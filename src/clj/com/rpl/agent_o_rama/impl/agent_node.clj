(ns com.rpl.agent-o-rama.impl.agent-node
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentNode
    StreamingRecorder]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    Node]
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

(defn mk-agent-node
  [agent-name agent-graph agent-task-id agent-id curr-node invoke-id retry-num
   store-info ^RamaClientsTaskGlobal rama-clients]
  (let [task-id             (ops/current-task-id)
        result-vol          (volatile! nil)
        emits-vol           (volatile! [])
        nested-ops-vol      (volatile! [])
        task-thread-ids-vol (volatile! nil)
        emit-count-vol      (volatile! 0)
        valid-output-nodes  (-> agent-graph
                                :node-map
                                (get curr-node)
                                :output-nodes)

        ^com.rpl.rama.ModuleInstanceInfo module-instance-info
        (ops/module-instance-info)

        this-module-name    (.getModuleName module-instance-info)
        random-source       (ops/current-random-source)
        streaming-depot     (.getAgentStreamingDepot rama-clients agent-name)
        streaming-recorder  (mk-streaming-recorder agent-task-id
                                                   agent-id
                                                   curr-node
                                                   invoke-id
                                                   retry-num
                                                   streaming-depot)
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
                     ;; TODO
     )
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
     AgentNodeInternal
     (get-streaming-recorder [this] streaming-recorder)
     (agent-node-state [this]
       {:emits      @emits-vol
        :result     @result-vol
        :nested-ops @nested-ops-vol}))))


(defn submit-virtual-task!
  [invoke-id afn]
  (let [^AgentNodeExecutorTaskGlobal node-exec
        (po/agent-node-executor-task-global)]
    (.submitTask node-exec invoke-id afn)))

(defn log-node-error
  [t msg data]
  (cljlogging/error t msg data))

(defn node-event
  [agent-name task-id invoke-id retry-num node-name node-fn
   ^AgentNode agent-node args ^RamaClientsTaskGlobal rama-clients
   fork-context]
  (fn []
    (let [depot (.getAgentDepot rama-clients agent-name)
          res   (try
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
                    (throw t)
                  ))
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
                *fork-context))
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
