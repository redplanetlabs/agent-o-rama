(ns com.rpl.agent-o-rama.impl.agent-node
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.langchain4j-trace :as lc4j-trace]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentClient
    AgentFailedException
    AgentNode
    AgentObjectFetcher
    HumanInputRequest
    IUnderlying
    StreamingRecorder]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    AgentNodeExecutorTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    Node]
   [dev.langchain4j.model.chat
    ChatModel
    StreamingChatModel]
   [dev.langchain4j.data.embedding
    Embedding]
   [dev.langchain4j.data.message
    ChatMessage]
   [dev.langchain4j.model.chat.request
    ChatRequest]
   [dev.langchain4j.model.chat.response
    ChatResponse
    StreamingChatResponseHandler]
   [dev.langchain4j.store.embedding
    EmbeddingMatch
    EmbeddingStore]
   [dev.langchain4j.store.embedding.filter
    Filter]
   [java.io
    Closeable]
   [java.util
    UUID]
   [java.util.concurrent
    CompletableFuture]))

(defn next-task-id
  [task-id-vol ^com.rpl.rama.ModuleInstanceInfo module-instance-info]
  (when (empty? @task-id-vol)
    (vreset! task-id-vol
             (-> (.getNumTasks module-instance-info)
                 range
                 shuffle
                 seq)))
  (let [ret (long (first @task-id-vol))]
    (vswap! task-id-vol next)
    ret))

(defprotocol AgentNodeInternal
  (agent-node-state [this])
  (release-acquired-objects! [this])
  (get-streaming-recorder [this])
  (get-declared-objects [this])
  (get-agent-invoke [this]))

(defprotocol StreamingRecorderInternal
  (waitFinish [this]))

(defn- verify-successful-cf!
  [^CompletableFuture cf]
  (try
    (.get cf)
    (catch Exception e
      (throw (h/ex-info "Streaming append failed" {} e)))))

;; these are for redef in tests
(defn identity-streaming-index [v] v)
(defn identity-retry-num [v] v)

(defn mk-streaming-recorder
  ^StreamingRecorder
  [agent-task-id agent-id node invoke-id retry-num streaming-depot]
  (let [index-vol (volatile! 0)
        outstanding-queue-vol (volatile! clojure.lang.PersistentQueue/EMPTY)]
    (reify
     StreamingRecorder
     (streamChunk [this chunk]
       ;; crucial to lock so that appends on this depot happen in order of
       ;; indexes
       (locking index-vol
         (let [streaming-index @index-vol
               _ (vswap! index-vol inc)
               cf (foreign-append-async!
                   streaming-depot
                   (aor-types/->valid-NodeStreamingResult
                    agent-task-id
                    agent-id
                    node
                    invoke-id
                    (identity-retry-num retry-num)
                    (identity-streaming-index streaming-index)
                    chunk))]
           (vswap! outstanding-queue-vol conj cf)
           (when (> (count @outstanding-queue-vol) 1000)
             (dotimes [_ 100]
               (let [cf (peek @outstanding-queue-vol)]
                 (vswap! outstanding-queue-vol pop)
                 (verify-successful-cf! cf)
               )))
         )))
     StreamingRecorderInternal
     (waitFinish [this]
       (when (> @index-vol 0)
         (vswap! outstanding-queue-vol
                 conj
                 (foreign-append-async!
                  streaming-depot
                  (aor-types/->valid-NodeStreamingResult
                   agent-task-id
                   agent-id
                   node
                   invoke-id
                   (identity-retry-num retry-num)
                   (identity-streaming-index @index-vol)
                   iclient/FINISHED-INVOKE))))
       (doseq [cf @outstanding-queue-vol]
         (verify-successful-cf! cf)))
    )))

(defn- no-async!
  []
  (throw (h/ex-info "Async API not implemented for subagents" {})))

(defn- no-stream!
  []
  (throw (h/ex-info "Streaming not implemented for subagents" {})))

(defn record-nested-op!-impl
  [^AgentNode agent-node nested-op-type start-time-millis finish-time-millis
   info-map]
  ;; can be nil when trying evaluators
  (when agent-node
    (.recordNestedOp agent-node
                     (aor-types/nested-op-type->java nested-op-type)
                     start-time-millis
                     finish-time-millis
                     info-map)))

(defmacro timed-agent-call
  [expr agent-node-sym agent-info-tuple [res-sym] info-map-expr]
  `(let [start-time-millis# (h/current-time-millis)
         ~res-sym ~expr
         finish-time-millis# (h/current-time-millis)
         [agent-module-name# agent-name#] ~agent-info-tuple]
     (record-nested-op!-impl
      ~agent-node-sym
      :agent-call
      start-time-millis#
      finish-time-millis#
      (assoc ~info-map-expr
       "agent-module-name" agent-module-name#
       "agent-name" agent-name#))
     ~res-sym
   ))

(defn mk-fetcher
  ^AgentObjectFetcher []
  (let [declared-objects-tg   (po/agent-declared-objects-task-global)
        acquired-objects-atom (atom [])]
    (reify
     AgentObjectFetcher
     (getAgentObject [this name]
       (let [ret (.getAgentObjectFromResource declared-objects-tg name)]
         (swap! acquired-objects-atom conj [name ret])
         ret
       ))
     AgentNodeInternal
     (release-acquired-objects! [this]
       (doseq [[name o] @acquired-objects-atom]
         (.releaseAgentObject declared-objects-tg name o)))
    )))

(defn gen-node-id
  []
  (h/random-uuid7))

(defn mk-agent-node
  [agent-name agent-graph agent-task-id agent-id execution-context curr-node invoke-id retry-num
   store-info ^RamaClientsTaskGlobal rama-clients]
  (let [task-id             (ops/current-task-id)
        result-vol          (volatile! nil)
        emits-vol           (volatile! [])
        nested-ops-vol      (volatile! [])
        task-ids-vol        (volatile! nil)
        emit-count-vol      (volatile! 0)
        valid-output-nodes  (-> agent-graph
                                :node-map
                                (get curr-node)
                                :output-nodes)

        ^com.rpl.rama.ModuleInstanceInfo module-instance-info
        (ops/module-instance-info)

        streaming-depot     (.getAgentStreamingDepot rama-clients agent-name)
        human-depot         (.getAgentHumanDepot rama-clients agent-name)
        streaming-recorder  (mk-streaming-recorder agent-task-id
                                                   agent-id
                                                   curr-node
                                                   invoke-id
                                                   retry-num
                                                   streaming-depot)

        declared-objects-tg (po/agent-declared-objects-task-global)

        fetcher             (mk-fetcher)

        ^AgentNodeExecutorTaskGlobal node-exec
        (po/agent-node-executor-task-global)
       ]
    (reify
     AgentNode
     (emit [this node args]
       (when (some? @result-vol)
         (throw (h/ex-info "Cannot emit with result already specified"
                           {:current-result @result-vol})))
       (when-not (contains? valid-output-nodes node)
         (throw (h/ex-info "Emitting to undeclared output node"
                           {:node node
                            :valid-output-nodes valid-output-nodes})))
       (let [emit-count (vswap! emit-count-vol inc)]
         (vswap!
          emits-vol
          conj
          (aor-types/->valid-AgentNodeEmit
           (gen-node-id)
           nil
           (if (selected-any? [:node-map (keypath node) :node
                               #(instance? Node %)]
                              agent-graph)
             (if (= emit-count 1)
               task-id
               (next-task-id task-ids-vol module-instance-info))
             agent-task-id)
           node
           (vec args)
          ))))
     (result [this arg]
       (when (some? @result-vol)
         (throw (h/ex-info "Cannot have multiple results"
                           {:current-result @result-vol})))
       (when-not (empty? @emits-vol)
         (throw (h/ex-info "Cannot both emit and result" {})))
       (vreset! result-vol (aor-types/->valid-AgentResult arg false)))
     (getMetadata [this]
       (:metadata execution-context))
     (getAgentObject [this name]
       (.getAgentObject fetcher name))
     (getStore [this name]
       (let [store-params
             (simpl/->valid-StoreParams
              name
              agent-name
              agent-task-id
              agent-id
              retry-num
              false
              (.getLocalPState rama-clients name)
              (.getPStateWriteDepot rama-clients)
              nested-ops-vol)]
         ;; TODO: not sure this is the right approach for mirrors
         (condp = (get (:store-info store-info) name)
           simpl/KV
           (simpl/mk-kv-store store-params)

           simpl/DOC
           (simpl/mk-doc-store store-params)

           nil
           (simpl/mk-pstate-store store-params)

           (throw (h/ex-info "Unknown store type"
                             {:name name
                              :type (get store-info name)}))
         )))
     (getAgentClient [agent-node name]
       (let [client (.getAgentClient declared-objects-tg name)

             agent-info-tuple
             (.getAgentInfo declared-objects-tg name)]
         (reify
          AgentClient
          (invoke [this args]
            (let [inv (.initiate this args)]
              (.result this inv)))
          (invokeAsync [this args]
            (no-async!))
          (initiate [this args]
            (timed-agent-call
             (.initiate client args)
             agent-node
             agent-info-tuple
             [res]
             {"op"     "initiate"
              "args"   (vec args) ; so it doesn't put a raw array in the trace
              "result" res}))
          (initiateAsync [this args]
            (no-async!))
          (fork [this invoke nodeInvokeIdToNewArgs]
            (let [inv (.initiateFork this invoke nodeInvokeIdToNewArgs)]
              (.result this inv)))
          (forkAsync [this invoke nodeInvokeIdToNewArgs]
            (no-async!))
          (initiateFork [this invoke nodeInvokeIdToNewArgs]
            (timed-agent-call
             (.initiateFork client invoke nodeInvokeIdToNewArgs)
             agent-node
             agent-info-tuple
             [res]
             {"op"           "initiateFork"
              "invoke"       invoke
              "new-args-map" nodeInvokeIdToNewArgs
              "result"       res}))
          (initiateForkAsync [this invoke invokeIdToNewArgs]
            (no-async!))
          (nextStep [this agent-invoke]
            (let [start-time-millis (h/current-time-millis)
                  ret               (.get ^CompletableFuture
                                          (aor-types/subagent-next-step-async client agent-invoke))
                  [stats res]       (if (instance? HumanInputRequest ret)
                                      [nil ret]
                                      [(:stats ret) (:result ret)])

                  finish-time-millis
                  (h/current-time-millis)
                  [agent-module-name agent-name]
                  agent-info-tuple
                  data-map
                  {"op"           "nextStep"
                   "agent-invoke" agent-invoke
                   "agent-module-name" agent-module-name
                   "agent-name"   agent-name}

                  data-map
                  (if stats (assoc data-map "stats" stats) data-map)
                  data-map
                  (if (instance? AgentFailedException res) data-map (assoc data-map "result" res))
                 ]
              (record-nested-op!-impl
               agent-node
               :agent-call
               start-time-millis
               finish-time-millis
               data-map)
              (if (instance? AgentFailedException res)
                (throw res))
              res
            ))
          (nextStepAsync [this agent-invoke]
            (no-async!))
          (result [this agent-invoke]
            (loop [step (.nextStep this agent-invoke)]
              (if (instance? HumanInputRequest step)
                (do
                  (.provideHumanInput
                   this
                   step
                   (.getHumanInput agent-node (:prompt step)))
                  (recur (.nextStep this agent-invoke)))
                (:result step))))
          (resultAsync [this agent-invoke]
            (no-async!))
          (stream [this agent-invoke node]
            (no-stream!))
          (stream [this agent-invoke node stream-callback]
            (no-stream!))
          (streamSpecific [this agent-invoke node node-invoke-id]
            (no-stream!))
          (streamSpecific
            [this agent-invoke node node-invoke-id stream-callback]
            (no-stream!))
          (streamAll [this agent-invoke node]
            (no-stream!))
          (streamAll [this agent-invoke node stream-all-callback]
            (no-stream!))
          (pendingHumanInputs [this agent-invoke]
            (timed-agent-call
             (.pendingHumanInputs client agent-invoke)
             agent-node
             agent-info-tuple
             [res]
             {"op"           "pendingHumanInputs"
              "agent-invoke" agent-invoke
              "result"       res}))
          (pendingHumanInputsAsync [this invoke]
            (no-async!))
          (provideHumanInput [this request response]
            (timed-agent-call
             (.provideHumanInput client request response)
             agent-node
             agent-info-tuple
             [res]
             {"op"       "provideHumanInput"
              "request"  request
              "response" response}))
          (provideHumanInputAsync [this request response]
            (no-async!))
          (close [this]
            (close! client))
          aor-types/AgentClientInternal
          (invoke-with-context-async-internal [this context args]
            (aor-types/invoke-with-context-async-internal client context args))
          (initiate-with-context-async-internal [this context args]
            (aor-types/initiate-with-context-async-internal client context args))
          (subagent-next-step-async [this agent-invoke]
            (aor-types/subagent-next-step-async client agent-invoke))
          aor-types/UnderlyingObjects
          (underlying-objects [this]
            (aor-types/underlying-objects client))
         )))
     (streamChunk [this chunk]
       (.streamChunk streaming-recorder chunk))
     (recordNestedOp [this type start-time-millis finish-time-millis info]
       (when (< finish-time-millis start-time-millis)
         (throw (h/ex-info "Finish time cannot be before start time"
                           {:start-time-millis  start-time-millis
                            :finish-time-millis finish-time-millis})))
       (when-not (every? string? (.keySet info))
         (throw (h/ex-info "Info map must contain string keys" {:info info})))
       (vswap! nested-ops-vol
               conj
               (aor-types/->NestedOpInfoImpl
                start-time-millis
                finish-time-millis
                (aor-types/nested-op-type->clj type)
                (into {} info))))
     (getHumanInput
       [this prompt]
       (let [start-time-millis (h/current-time-millis)
             request (aor-types/->valid-NodeHumanInputRequest
                      agent-task-id
                      agent-id
                      curr-node
                      task-id
                      invoke-id
                      prompt
                      (h/random-uuid-str))
             cf      (CompletableFuture.)
             _ (.putHumanFuture node-exec invoke-id request cf)
             _ (foreign-append! human-depot request :append-ack)
             ret     (.get cf)]
         (vswap! nested-ops-vol
                 conj
                 (aor-types/->NestedOpInfoImpl
                  start-time-millis
                  (h/current-time-millis)
                  :human-input
                  {"prompt" prompt
                   "result" ret}))
         ret
       ))
     AgentNodeInternal
     (get-declared-objects [this]
       declared-objects-tg)
     (get-streaming-recorder [this] streaming-recorder)
     (release-acquired-objects! [this]
       (release-acquired-objects! fetcher))
     (get-agent-invoke [this] (aor-types/->AgentInvokeImpl agent-task-id agent-id))
     (agent-node-state [this]
       {:emits      @emits-vol
        :result     @result-vol
        :nested-ops @nested-ops-vol
       }))))

(defn submit-virtual-task!
  [invoke-id afn]
  (let [^AgentNodeExecutorTaskGlobal node-exec
        (po/agent-node-executor-task-global)]
    (.submitTask node-exec invoke-id afn)))

(defn log-node-error
  [t msg data]
  (cljlogging/error t msg data))

(def AGENT-NODE-CONTEXT (ThreadLocal.))

(defn try-close!
  [obj]
  (when (instance? Closeable obj)
    (close! obj)))

(defn- record-model-call!
  [name agent-node ^ChatRequest request ^ChatResponse response start-time-millis
   first-token-time-millis]
  (record-nested-op!-impl
   agent-node
   :model-call
   start-time-millis
   (h/current-time-millis)
   (h/remove-empty-vals
    {"objectName"       name
     "modelName"        (.modelName response)
     "frequencyPenalty" (.frequencyPenalty request)
     "presencePenalty"  (.presencePenalty request)
     "stopSequences"    (into [] (.stopSequences request))
     "temperature"      (.temperature request)
     "topK"             (.topK request)
     "topP"             (.topP request)
     "input"            (lc4j-trace/messages->trace (.messages request))
     "response"         (h/safe-> response .aiMessage .text)
     "finishReason"     (lc4j-trace/finish-reason->trace
                         (.finishReason response))
     "inputTokenCount"  (h/safe-> response
                                  .tokenUsage
                                  .inputTokenCount)
     "outputTokenCount" (h/safe-> response
                                  .tokenUsage
                                  .outputTokenCount)
     "totalTokenCount"  (h/safe-> response
                                  .tokenUsage
                                  .totalTokenCount)
     "firstTokenTimeMillis" first-token-time-millis
    })))

(defn record-model-failure!
  [name agent-node ^ChatRequest request start-time-millis t]
  (record-nested-op!-impl
   agent-node
   :model-call
   start-time-millis
   (h/current-time-millis)
   {"objectName" name
    "input"      (lc4j-trace/messages->trace (.messages request))
    "failure"    (h/throwable->str t)}))

(defn- instrument-chat!
  [name request response-fn]
  (let [^AgentNode agent-node (h/thread-local-get AGENT-NODE-CONTEXT)
        start-time-millis     (h/current-time-millis)]
    (try
      (let [response (response-fn)]
        (record-model-call! name agent-node request response start-time-millis nil)
        response)
      (catch Throwable t
        (record-model-failure! name agent-node request start-time-millis t)
        (throw t)))))

(defn- instrument-streaming-chat!
  [name ^ChatRequest request initiate-fn]
  (let [^AgentNode agent-node (h/thread-local-get AGENT-NODE-CONTEXT)
        start-time-millis     (h/current-time-millis)]
    (try
      (let [cf (CompletableFuture.)
            first-token-time-millis (atom nil)
            update-token-time!
            (fn [] (swap! first-token-time-millis (fn [v] (or v (h/current-time-millis)))))
            _ (initiate-fn
               (reify
                StreamingChatResponseHandler
                (onPartialResponse [this partial]
                  (update-token-time!)
                  (.streamChunk agent-node partial))
                (onCompleteResponse [this response]
                  (update-token-time!)
                  (.complete cf response))
                (onError [this t]
                  (.completeExceptionally
                   cf
                   (h/ex-info "Streaming failed" {:name name} t)))))
            response (.get cf)]
        (record-model-call! name
                            agent-node
                            request
                            response
                            start-time-millis
                            @first-token-time-millis)
        response)
      (catch Throwable t
        (record-model-failure! name agent-node request start-time-millis t)
        (throw t))
    )))

(defmacro with-traced
  [expr object-name nested-op-type [res-sym] & body]
  `(let [agent-node#        (h/thread-local-get AGENT-NODE-CONTEXT)
         start-time-millis# (h/current-time-millis)
         ~res-sym           ~expr
         info-map#          (do ~@body)
        ]
     (record-nested-op!-impl
      agent-node#
      ~nested-op-type
      start-time-millis#
      (h/current-time-millis)
      (assoc info-map# "objectName" ~object-name))
     ~res-sym
   ))

(defn wrap-agent-object
  [name obj]
  (cond
    (instance? ChatModel obj)
    (let [^ChatModel obj obj]
      (reify
       ChatModel
       ;; - each provider overrides one of the following two methods and uses
       ;; default impls for the rest of the "chat" methods
       (^ChatResponse chat [this ^ChatRequest chatRequest]
         (instrument-chat! name chatRequest #(.chat obj chatRequest)))
       (^ChatResponse doChat [this ^ChatRequest chatRequest]
         (instrument-chat! name chatRequest #(.doChat obj chatRequest)))
       (defaultRequestParameters [this] (.defaultRequestParameters obj))
       (listeners [this] (.listeners obj))
       (provider [this] (.provider obj))
       (supportedCapabilities [this] (.supportedCapabilities obj))

       IUnderlying
       (getUnderlying [this] obj)

       Closeable
       (close [this] (try-close! obj))))


    (instance? StreamingChatModel obj)
    (let [^StreamingChatModel obj obj]
      (reify
       ChatModel
       ;; - same as with ChatModel impls, some StreamingChatModel impls
       ;; implement chat(ChatRequest, StreamingChatResponseHandler) and others
       ;; implement doChat(ChatRequest, StreamingChatResponseHandler)
       ;; - so here only need to implement these entry points and forward to
       ;; corresponding method on StreamingChatModel
       (^ChatResponse chat [this ^ChatRequest chatRequest]
         (instrument-streaming-chat!
          name
          chatRequest
          #(.chat obj chatRequest ^StreamingChatResponseHandler %)))
       (^ChatResponse doChat [this ^ChatRequest chatRequest]
         (instrument-streaming-chat!
          name
          chatRequest
          #(.doChat obj chatRequest ^StreamingChatResponseHandler %)))
       (defaultRequestParameters [this] (.defaultRequestParameters obj))
       (listeners [this] (.listeners obj))
       (provider [this] (.provider obj))
       (supportedCapabilities [this] (.supportedCapabilities obj))

       IUnderlying
       (getUnderlying [this] obj)

       Closeable
       (close [this] (try-close! obj))))

    (instance? EmbeddingStore obj)
    (let [^EmbeddingStore obj obj]
      (reify
       EmbeddingStore
       (add [this embedding]
         (with-traced
          (.add obj embedding)
          name
          :db-write
          [res]
          {"op" "add"
           "id" res
          }))
       (^String add [this ^Embedding embedding ^Object embedded]
         (with-traced
          (.add obj embedding embedded)
          name
          :db-write
          [res]
          {"op" "add"
           "id" res
          }))
       (^void add [this ^String id ^Embedding embedding]
         (with-traced
          (.add obj id embedding)
          name
          :db-write
          [res]
          {"op" "add"
           "id" id
          }))
       (addAll [this embeddings]
         (with-traced
          (.addAll obj embeddings)
          name
          :db-write
          [res]
          {"op"  "addAll"
           "ids" res
          }))
       (addAll [this embeddings embeddeds]
         (with-traced
          (.addAll obj embeddings embeddeds)
          name
          :db-write
          [res]
          {"op"  "addAll"
           "ids" res
          }))
       (addAll [this ids embeddings embeddeds]
         (with-traced
          (.addAll obj ids embeddings embeddeds)
          name
          :db-write
          [res]
          {"op"  "addAll"
           "ids" ids
          }))
       (generateIds [this n]
         (.generateIds obj n))
       (remove [this id]
         (with-traced
          (.remove obj id)
          name
          :db-write
          [res]
          {"op" "remove"
           "id" id
          }))
       (removeAll [this]
         (with-traced
          (.removeAll obj)
          name
          :db-write
          [res]
          {"op" "removeAll"
          }))
       (^void removeAll [this ^Filter filter]
         (with-traced
          (.removeAll obj filter)
          name
          :db-write
          [res]
          {"op"     "removeAll"
           "filter" (str filter)
          }))
       (^void removeAll [this ^java.util.Collection ids]
         (with-traced
          (.removeAll obj ids)
          name
          :db-write
          [res]
          {"op"  "removeAll"
           "ids" ids
          }))
       (search [this request]
         (with-traced
          (.search obj request)
          name
          :db-read
          [res]
          {"op"      "search"
           "request" {"filter"     (str (.filter request))
                      "maxResults" (.maxResults request)
                      "minScore"   (.minScore request)}
           "matches" (mapv
                      (fn [^EmbeddingMatch match]
                        {"id"    (.embeddingId match)
                         "score" (.score match)})
                      (.matches res))
          }))

       IUnderlying
       (getUnderlying [this] obj)

       Closeable
       (close [this] (try-close! obj))))

    :else
    obj))

(defn node-event
  [agent-name task-id invoke-id retry-num node-name node-fn
   ^AgentNode agent-node args ^RamaClientsTaskGlobal rama-clients
   fork-context acquire-timeout-millis]
  (fn []
    (let [depot (.getAgentDepot rama-clients agent-name)]
      (try
        (h/thread-local-set! AGENT-NODE-CONTEXT agent-node)
        (h/thread-local-set!
         AgentDeclaredObjectsTaskGlobal/ACQUIRE_TIMEOUT_MILLIS
         acquire-timeout-millis)
        (let [res (apply node-fn agent-node args)
              {:keys [emits result nested-ops]} (agent-node-state agent-node)]
          (-> agent-node
              get-streaming-recorder
              waitFinish)
          (foreign-append!
           depot
           (aor-types/->valid-NodeComplete
            task-id
            invoke-id
            retry-num
            res
            emits
            result
            nested-ops
            (h/current-time-millis)
            fork-context)
           :append-ack))
        (catch Throwable t
          (log-node-error t
                          "Error during agent node execution"
                          {:node      node-name
                           :invoke-id invoke-id})
          (foreign-append!
           depot
           (aor-types/->valid-NodeFailure
            task-id
            invoke-id
            retry-num
            (h/throwable->str t)
            (-> agent-node
                agent-node-state
                :nested-ops))
           :append-ack)
          (throw t))
        (finally
          (release-acquired-objects! agent-node)))
    )))

(deframafn read-config
  [*agent-name *config]
  (<<with-substitutions
   [$$config (po/agent-config-task-global *agent-name)]
   (local-select> STAY $$config :> *config-map)
   (:> (aor-types/get-config *config-map *config))))

(deframafn read-global-config
  [*config]
  (<<with-substitutions
   [$$global-config (po/agent-global-config-task-global)]
   (local-select> STAY $$global-config :> *config-map)
   (:> (aor-types/get-config *config-map *config))))

(deframaop handle-node-invoke
  [*agent-name *agent-task-id *agent-id *execution-context *node-fn *invoke-id *retry-num
   *next-node *args *agg-invoke-id *fork-context]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)
    *store-info (po/agent-store-info-task-global)
    *rama-clients (po/agents-clients-task-global)]
   (mk-agent-node *agent-name
                  *agent-graph
                  *agent-task-id
                  *agent-id
                  *execution-context
                  *next-node
                  *invoke-id
                  *retry-num
                  *store-info
                  *rama-clients
                  :> *agent-node)

   (h/current-time-millis :> *start-time-millis)
   (ops/current-task-id :> *task-id)
   ;; - merge instead of overwrite since agg nodes run completion function on
   ;; already existing node
   ;; - in retries, this is mostly redundant except for update of
   ;; start-time-millis
   (<<ramafn %merger
     [*m]
     (:> (reduce-kv
          assoc
          *m
          {:agent-id          *agent-id
           :agent-task-id     *agent-task-id
           :node              *next-node
           :metadata          (get *execution-context :metadata)
           :source            (get *execution-context :source)
           :start-time-millis *start-time-millis
           :input             *args
           :agg-invoke-id     *agg-invoke-id
          })))
   (local-transform> [(keypath *invoke-id) (term %merger)] $$nodes)
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *task-id)
   (read-config *agent-name
                aor-types/ACQUIRE-OBJECT-TIMEOUT-MILLIS-CONFIG
                :> *acquire-timeout-millis)
   (submit-virtual-task!
    *invoke-id
    (node-event *agent-name
                *task-id
                *invoke-id
                *retry-num
                *next-node
                *node-fn
                *agent-node
                *args
                *rama-clients
                *fork-context
                *acquire-timeout-millis))
   (:>)))

(defn- invoke-or-error
  [afn info]
  (try
    (afn)
    (catch Throwable t
      (log-node-error t "Error invoking function" {:info info})
      {::error t})))

(defn hook:appended-agent-failure [agent-task-id agent-id retry-num])

(deframaop invoke-on-task-thread
  [*agent-name *agent-task-id *agent-id *node *invoke-id *retry-num *afn *info]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    *failure-depot (po/agent-failures-depot-task-global *agent-name)]
   (invoke-or-error *afn *info :> *res)
   (<<if (and> (map? *res) (contains? *res ::error))
     (h/throwable->str (get *res ::error) :> *s)
     (|direct *agent-task-id)
     (local-transform>
      [(must *agent-id)
       :exception-summaries
       AFTER-ELEM
       (termval (aor-types/->ExceptionSummary *s *node *invoke-id))]
      $$root)
     (depot-partition-append!
      *failure-depot
      (aor-types/->valid-AgentFailure *agent-task-id *agent-id *retry-num)
      :append-ack)
     (hook:appended-agent-failure *agent-task-id
                                  *agent-id
                                  *retry-num)
    (else>)
     (:> *res)
   )))
