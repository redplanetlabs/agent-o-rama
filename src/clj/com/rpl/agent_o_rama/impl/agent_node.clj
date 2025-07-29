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
    AgentNode
    IUnderlying
    NestedOpType
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
   [java.util.concurrent
    CompletableFuture]))

(defn next-task-thread-id
  [task-thread-id-vol ^com.rpl.rama.ModuleInstanceInfo module-instance-info]
  (when (empty? @task-thread-id-vol)
    (vreset! task-thread-id-vol
             (-> (.getTaskThreadIds module-instance-info)
                 shuffle
                 seq)))
  (let [ret (long (first @task-thread-id-vol))]
    (vswap! task-thread-id-vol next)
    ret))

(defprotocol AgentNodeInternal
  (agent-node-state [this])
  (release-acquired-objects! [this])
  (get-streaming-recorder [this]))

(defprotocol StreamingRecorderInternal
  (waitFinish [this]))

(defn- verify-successful-cf!
  [^CompletableFuture cf]
  (.get cf)
  (when (.isCompletedExceptionally cf)
    (throw (h/ex-info "Streaming append failed" {} (.get cf)))))

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

(def NESTED-OP-TYPE-CLJ
  {:store-read   NestedOpType/STORE_READ
   :store-write  NestedOpType/STORE_WRITE
   :db-read      NestedOpType/DB_READ
   :db-write     NestedOpType/DB_WRITE
   :model-call   NestedOpType/MODEL_CALL
   :agent-invoke NestedOpType/AGENT_INVOKE
   :other        NestedOpType/OTHER
  })

(def NESTED-OP-TYPE-JAVA
  (into {} (for [[k v] NESTED-OP-TYPE-CLJ] [v k])))

(defn nested-op-type->clj
  [v]
  (if-let [res (get NESTED-OP-TYPE-JAVA v)]
    res
    (throw (h/ex-info "Unknown nested op type" {:val v :type (class v)}))))

(defn nested-op-type->java
  [v]
  (if-let [res (get NESTED-OP-TYPE-CLJ v)]
    res
    (throw (h/ex-info "Unknown nested op type" {:val v :type (class v)}))))

(defn mk-agent-node
  [agent-name agent-graph agent-task-id agent-id curr-node invoke-id retry-num
   store-info ^RamaClientsTaskGlobal rama-clients]
  (let [task-id               (ops/current-task-id)
        result-vol            (volatile! nil)
        emits-vol             (volatile! [])
        nested-ops-vol        (volatile! [])
        task-thread-ids-vol   (volatile! nil)
        emit-count-vol        (volatile! 0)
        valid-output-nodes    (-> agent-graph
                                  :node-map
                                  (get curr-node)
                                  :output-nodes)

        ^com.rpl.rama.ModuleInstanceInfo module-instance-info
        (ops/module-instance-info)

        this-module-name      (.getModuleName module-instance-info)
        random-source         (ops/current-random-source)
        streaming-depot       (.getAgentStreamingDepot rama-clients agent-name)
        streaming-recorder    (mk-streaming-recorder agent-task-id
                                                     agent-id
                                                     curr-node
                                                     invoke-id
                                                     retry-num
                                                     streaming-depot)

        declared-objects-tg   (po/agent-declared-objects-task-global)
        acquired-objects-atom (atom [])
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
           (h/random-long random-source)
           nil
           (if (selected-any? [:node-map (keypath node) :node
                               #(instance? Node %)]
                              agent-graph)
             (if (= emit-count 1)
               task-id
               (next-task-thread-id task-thread-ids-vol module-instance-info))
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
     (getAgentObject [this name]
       (let [ret (.getAgentObjectFromResource declared-objects-tg name)]
         (swap! acquired-objects-atom conj [name ret])
         ret
       ))
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
     (streamChunk [this chunk]
       (.streamChunk streaming-recorder chunk))
     (recordNestedOp [this type start-time-millis finish-time-millis info]
       (when (< finish-time-millis start-time-millis)
         (throw (h/ex-info "Finish time cannot be before start time"
                           {:start-time-millis  start-time-millis
                            :finish-time-millis finish-time-millis})))
       (vswap! nested-ops-vol
               conj
               (aor-types/->NestedOpInfo
                start-time-millis
                finish-time-millis
                (nested-op-type->clj type)
                info)))
     AgentNodeInternal
     (get-streaming-recorder [this] streaming-recorder)
     (release-acquired-objects! [this]
       (doseq [[name o] @acquired-objects-atom]
         (.releaseAgentObject declared-objects-tg name o)))
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

(defn record-nested-op!-impl
  [^AgentNode agent-node nested-op-type start-time-millis finish-time-millis
   info-map]
  (.recordNestedOp agent-node
                   (nested-op-type->java nested-op-type)
                   start-time-millis
                   finish-time-millis
                   info-map))

(defn try-close!
  [obj]
  (when (instance? Closeable obj)
    (close! obj)))

(defn- record-model-call!
  [name agent-node ^ChatRequest request ^ChatResponse response
   start-time-millis]
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
    })))

(defn- instrument-chat!
  [name request response-fn]
  (let [^AgentNode agent-node (h/thread-local-get AGENT-NODE-CONTEXT)
        start-time-millis (h/current-time-millis)
        response (response-fn)]
    (record-model-call! name agent-node request response start-time-millis)
    response
  ))

(defn- instrument-streaming-chat!
  [name ^ChatRequest request initiate-fn]
  (let [^AgentNode agent-node (h/thread-local-get AGENT-NODE-CONTEXT)
        cf (CompletableFuture.)
        start-time-millis (h/current-time-millis)
        _ (initiate-fn
           (reify
            StreamingChatResponseHandler
            (onPartialResponse [this partial]
              (.streamChunk agent-node partial))
            (onCompleteResponse [this response]
              (.complete cf response))
            (onError [this t]
              (.completeExceptionally
               cf
               (h/ex-info "Streaming failed" {:name name} t)))))
        response (.get cf)]
    (record-model-call! name agent-node request response start-time-millis)
    response
  ))

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
          {"op"        "add"
           "embedding" (.vector embedding)
           "id"        res
          }))
       (^String add [this ^Embedding embedding ^Object embedded]
         (with-traced
          (.add obj embedding embedded)
          name
          :db-write
          [res]
          {"op"        "add"
           "embedding" (.vector embedding)
           "embedded"  (str embedded)
           "id"        res
          }))
       (^void add [this ^String id ^Embedding embedding]
         (with-traced
          (.add obj id embedding)
          name
          :db-write
          [res]
          {"op"        "add"
           "embedding" (.vector embedding)
           "id"        id
          }))
       (addAll [this embeddings]
         (with-traced
          (.addAll obj embeddings)
          name
          :db-write
          [res]
          {"op"         "addAll"
           "embeddings" (mapv #(.vector ^Embedding %) embeddings)
           "ids"        res
          }))
       (addAll [this embeddings embeddeds]
         (with-traced
          (.addAll obj embeddings embeddeds)
          name
          :db-write
          [res]
          {"op"         "addAll"
           "embeddings" (mapv #(.vector ^Embedding %) embeddings)
           "embeddeds"  (mapv str embeddeds)
           "ids"        res
          }))
       (addAll [this ids embeddings embeddeds]
         (with-traced
          (.addAll obj ids embeddings embeddeds)
          name
          :db-write
          [res]
          {"op"         "addAll"
           "embeddings" (mapv #(.vector ^Embedding %) embeddings)
           "embeddeds"  (mapv str embeddeds)
           "ids"        ids
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
           "request" {"filter"         (str (.filter request))
                      "maxResults"     (.maxResults request)
                      "minScore"       (.minScore request)
                      "queryEmbedding" (.vector (.queryEmbedding request))}
           "matches" (mapv
                      (fn [^EmbeddingMatch match]
                        {"embedded"  (str (.embedded match))
                         "embedding" (.vector (.embedding match))
                         "id"        (.embeddingId match)
                         "score"     (.score match)})
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
    (let [depot (.getAgentDepot rama-clients agent-name)
          res   (try
                  (h/thread-local-set! AGENT-NODE-CONTEXT agent-node)
                  (h/thread-local-set!
                   AgentDeclaredObjectsTaskGlobal/ACQUIRE_TIMEOUT_MILLIS
                   acquire-timeout-millis)
                  (h/returning (apply node-fn agent-node args)
                    (-> agent-node
                        get-streaming-recorder
                        waitFinish))
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
                      retry-num)
                     :append-ack)
                    (throw t))
                  (finally
                    (release-acquired-objects! agent-node)))
          {:keys [emits result nested-ops]} (agent-node-state agent-node)]
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
       :append-ack)
    )))

(deframafn read-config
  [*agent-name *config]
  (<<with-substitutions
   [$$config (po/agent-config-task-global *agent-name)]
   (local-select> STAY $$config :> *config-map)
   (:> (aor-types/get-config *config-map *config))))

(deframaop handle-node-invoke
  [*agent-name *agent-task-id *agent-id *node-fn *invoke-id *retry-num
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
          {:agent-id      *agent-id
           :agent-task-id *agent-task-id
           :node          *next-node
           :start-time-millis *start-time-millis
           :input         *args
           :agg-invoke-id *agg-invoke-id
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
      ::error)))

(defn hook:appended-agent-failure [agent-task-id agent-id retry-num])

(deframaop invoke-on-task-thread
  [*agent-name *agent-task-id *agent-id *retry-num *afn *info]
  (<<with-substitutions
   [*failure-depot (po/agent-failures-depot-task-global *agent-name)]
   (invoke-or-error *afn *info :> *res)
   (<<if (= *res ::error)
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
