(ns com.rpl.agent-o-rama.impl.core
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentNode
    FinishedAgg
    StreamingRecorder]
   [com.rpl.agentorama.impl
    RamaClientsTaskGlobal
    AgentNodeExecutorTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AggAckOp
    Node
    NodeAgg
    NodeAggStart]
   [java.util.concurrent
    CompletableFuture]))

;; for agent-o-rama namespace
(defn hook:agent-result-proxy [proxy])

(defdepotpartitioner agent-streaming-depot-partitioner
  [{:keys [agent-task-id]} num-partitions]
  agent-task-id)

(defdepotpartitioner agent-depot-partitioner
  [data num-partitions]
  (if (or (aor-types/NodeComplete? data)
          (aor-types/NodeFailure? data))
    (:task-id data)
    (rand-int num-partitions)))

(defn submit-virtual-task!
  [invoke-id afn]
  (let [^AgentNodeExecutorTaskGlobal node-exec
        (po/agent-node-executor-task-global)]
    (.submitTask node-exec invoke-id afn)))

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
                     ;; TODO: <<<<>>>>
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
         ;; TODO: <<<<>>>> not sure this is the right approach for mirrors
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

(defn log-node-error
  [t msg data]
  (cljlogging/error t msg data))

(defn node-event
  [agent-name task-id invoke-id retry-num node-name node-fn
   ^AgentNode agent-node args ^RamaClientsTaskGlobal rama-clients
   invoke-id->new-args]
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
                      retry-num
                      (h/current-time-millis))
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
        invoke-id->new-args)
       :append-ack)
    )))

(deframaop handle-node-invoke
  [*agent-name *agent-task-id *agent-id *node-fn *invoke-id *retry-num
   *next-node *args *agg-invoke-id *invoke-id->new-args]
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
   ;; merge instead of overwrite since agg nodes run completion function on
   ;; already existing node
   (<<ramafn %merger
     [*m]
     (:> (reduce-kv h/assoc-if-void
                    *m
                    {:agent-id      *agent-id
                     :agent-task-id *agent-task-id
                     :node          *next-node
                     :start-time-millis *start-time-millis
                     :input         *args
                     :agg-invoke-id *agg-invoke-id
                    })))
   (local-transform> [(keypath *invoke-id) (term %merger)] $$nodes)
   (at/|aor [*agent-name *agent-task-id *agent-id *retry-num] |direct *task-id)
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
                *invoke-id->new-args))
   (:> {:start-time-millis *start-time-millis})))

(defn- node-type
  [graph node]
  (select-any [:node-map (keypath node) :node (view aor-types/node->type-kw)]
              graph))

(defn task-id-key-partitioner
  [num-partitions task-id]
  task-id)

(defn extract-agg-result
  [res]
  (cond
    (reduced? res)
    {:new-agg-state @res
     :finished?     true}

    (instance? FinishedAgg res)
    {:new-agg-state (.getValue ^FinishedAgg res)
     :finished?     true}

    :else
    {:new-agg-state res
     :finished?     false}))

(deframaop complete-agg!
  [*agent-name *invoke-id *retry-num]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node *agg-ack-val
                             *agg-state *agg-start-res *agg-invoke-id]})
   ;; since agg state is colocated with root of graph invoke
   (local-select> [(keypath *agent-id) :fork-of :invoke-id->new-args]
                  $$root
                  :> *invoke-id->new-args)
   (local-transform>
    [(keypath *invoke-id) :agg-finished? (termval true)]
    $$nodes)
   (at/get-node-obj *agent-graph *node :> {:keys [*node-fn]})
   (vector *agg-state *agg-start-res :> *args)
   (handle-node-invoke *agent-name
                       *agent-task-id
                       *agent-id
                       *node-fn
                       *invoke-id
                       *retry-num
                       *node
                       *args
                       *agg-invoke-id
                       *invoke-id->new-args)
   (:>)))

(deframaop ack-agg!
  [*agent-name *invoke-id *retry-num *ack-val]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> [(keypath *invoke-id) :agg-ack-val] $$nodes :> *agg-ack-val)
   (bit-xor *ack-val *agg-ack-val :> *new-ack-val)
   (local-transform>
    [(keypath *invoke-id) :agg-ack-val (termval *new-ack-val)]
    $$nodes)
   (filter> (= 0 *new-ack-val))
   (complete-agg! *agent-name *invoke-id *retry-num)
   (:>)))


(defn invoke-or-error
  [afn info]
  (try
    (afn)
    (catch Throwable t
      (log-node-error t "Error invoking function" {:info info})
      ::error)))

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
     (at/hook:appended-agent-failure *agent-task-id
                                     *agent-id
                                     *retry-num)
    (else>)
     (:> *res)
   )))

(defn- define-agent!
  [agent-name setup topologies stream-topology mb-topology agent-graph]
  (let [agent-depot-sym           (symbol (po/agent-depot-name agent-name))
        agent-streaming-depot-sym (symbol (po/agent-streaming-depot-name
                                           agent-name))
        agent-config-depot-sym    (symbol (po/agent-config-depot-name
                                           agent-name))

        agent-graph-sym           (symbol (po/agent-graph-task-global-name
                                           agent-name))
        agent-node-pstate-sym     (symbol (po/agent-node-task-global-name
                                           agent-name))]
    (declare-depot* setup agent-depot-sym agent-depot-partitioner)
    (declare-depot* setup
                    agent-streaming-depot-sym
                    agent-streaming-depot-partitioner)
    (declare-depot* setup
                    agent-config-depot-sym
                    :random
                    {:global? true})

    (declare-object* setup
                     agent-graph-sym
                     (graph/resolve-agent-graph agent-graph))

    ;; TODO: <<<<>>>>
    ;; - and ordered IDs is perfect for GC!
    ;;    - especially since they're sequential, so know exactly how many are in
    ;;    there by looking at min and max
    (declare-pstate*
     stream-topology
     (symbol (po/agent-root-task-global-name agent-name))
     po/AGENT-INVOKE-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-active-invokes-task-global-name agent-name))
     po/AGENT-ACTIVE-INVOKES-PSTATE-SCHEMA)
    (declare-pstate*
     stream-topology
     (symbol (po/agent-gc-invokes-task-global-name agent-name))
     po/AGENT-GC-ROOT-INVOKES-PSTATE-SCHEMA)
    (declare-pstate*
     stream-topology
     (symbol (po/agent-streaming-results-task-global-name agent-name))
     po/AGENT-STREAMING-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     agent-node-pstate-sym
     po/AGENT-NODE-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/graph-history-task-global-name agent-name))
     po/GRAPH-HISTORY-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-id-gen-task-global-name agent-name))
     Long
     {:initial-value 0})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-config-task-global-name agent-name))
     po/AGENT-CONFIG-PSTATE-SCHEMA)

    (if retries/SUBSTITUTE-TICK-DEPOT
      (declare-depot* setup
                      (symbol (po/agent-check-tick-depot-name agent-name))
                      :random
                      {:global? true})
      (declare-tick-depot* setup
                           (symbol (po/agent-check-tick-depot-name agent-name))
                           retries/DEFAULT-CHECKER-TICK-MILLIS))
    (declare-depot* setup
                    (symbol (po/agent-failures-depot-name agent-name))
                    :random)

    (declare-pstate*
     mb-topology
     (symbol (po/agent-valid-invokes-task-global-name agent-name))
     po/AGENT-VALID-INVOKES-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     mb-topology
     (symbol (po/pending-retries-task-global-name agent-name))
     po/PENDING-RETRIES-PSTATE-SCHEMA)

    (retries/declare-check-impl mb-topology agent-name)
    (queries/declare-tracing-query-topology topologies agent-name)

    (<<sources stream-topology
     (source> agent-config-depot-sym {:retry-mode :all-after} :> *data)
      (at/handle-config agent-name *data)

     (source> agent-streaming-depot-sym {:retry-mode :all-after} :> *data)
      (at/handle-streaming agent-name *data)

      ;; TODO: <<<<<>>>> add case here for GC
      ;; - each iteration delete node and write to PState the next ones to
      ;; delete and where – can probably be same PState as one used by retry

     (source> agent-depot-sym {:retry-mode :none} :> *data)
      (at/intake-agent-depot agent-name
                             *data
                             :> *agent-task-id *agent-id *retry-num *op)

      (<<if (aor-types/NodeOp? *op)
        (at/get-node-obj agent-graph-sym (get *op :next-node) :> *op-obj)
       (else>)
        (identity *op :> *op-obj))

      ;; TODO: <<<<>>>> if this is a retry, need to clear streaming results if
      ;; the node didn't finish
      ;;  - which involves partitioning back to agent-task-id
      ;;  - but streaming results are accumulation across ALL node invokes
      ;;    - some of which may have succeeded, some of which may have been
      ;;    partial, and some of which may not have started
      ;;      - so would need to iterate through streaming results and filter
      ;;      out if it failed, and reset index to 0
      ;;  - maybe streaming should work like this:
      ;;     - (fn [invoke-id->chunks invoke-id->new-chunks
      ;;     reset-invoke-id-set complete?])
      ;;    - this is "streamAll"
      ;;    - "stream" ONLY does the first invoke ID and ignores the rest
      ;;      (fn [chunks new-chunks reset? complete?])
      ;;    - maybe stream could take a "join" function that would present
      ;;    results as a single list in the case of LLMs
      ;;  - so here, it just needs to reset the streaming index for this invoke
      ;;  ID
      ;;  - don't need to reset here...
      ;;    - if it's 0, then let it go through no matter what
      ;;      - and client treats this as a reset

      (<<subsource *op-obj
       (case> Node :> {:keys [*node-fn]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id
                             *invoke-id->new-args]})
        (handle-node-invoke
         agent-name
         *agent-task-id
         *agent-id
         *node-fn
         *invoke-id
         *retry-num
         *next-node
         *args
         *agg-invoke-id
         *invoke-id->new-args)

       (case> NodeAggStart :> {:keys [*node-fn *agg-node-name]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id
                             *invoke-id->new-args]})
        (h/random-long :> *new-agg-invoke-id)
        (local-transform>
         [(keypath *invoke-id) :started-agg? (termval true)]
         agent-node-pstate-sym)
        (at/get-node-obj agent-graph-sym *agg-node-name :> {:keys [*init-fn]})
        (invoke-on-task-thread agent-name
                               *agent-task-id
                               *agent-id
                               *retry-num
                               *init-fn
                               :agg-init
                               :> *init-agg-state)
        (local-transform>
         [(keypath *new-agg-invoke-id)
          (termval {:agent-id            *agent-id
                    :agent-task-id       *agent-task-id
                    :node                *agg-node-name
                    :start-time-millis   (h/current-time-millis)
                    :agg-invoke-id       *agg-invoke-id
                    :agg-inputs          []
                    :agg-state           *init-agg-state
                    :agg-ack-val         *invoke-id
                    :agg-start-invoke-id *invoke-id
                   })]
         agent-node-pstate-sym)
        (handle-node-invoke
         agent-name
         *agent-task-id
         *agent-id
         *node-fn
         *invoke-id
         *retry-num
         *next-node
         *args
         *new-agg-invoke-id
         *invoke-id->new-args)


       (case> NodeAgg :> {:keys [*update-fn]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
        (assert! (some? *agg-invoke-id))
        (local-select> (keypath *agg-invoke-id)
                       agent-node-pstate-sym
                       :> {*agg-state           :agg-state
                           *parent-agg-invoke-id :agg-invoke-id
                           *agg-start-invoke-id :agg-start-invoke-id
                           *agg-finished?       :agg-finished?
                          })
        (local-transform> [(keypath *invoke-id)
                           :invoked-agg-invoke-id
                           (termval *agg-invoke-id)]
                          agent-node-pstate-sym)
        (filter> (not *agg-finished?))
        (<<ramafn %update-fn
          []
          (:> (apply *update-fn *agg-state *args)))
        (invoke-on-task-thread agent-name
                               *agent-task-id
                               *agent-id
                               *retry-num
                               %update-fn
                               :agg-update
                               :> *res)
        (extract-agg-result *res :> {:keys [*new-agg-state *finished?]})

        (local-transform>
         [(keypath *agg-invoke-id)
          (multi-path [:agg-state (termval *new-agg-state)]
                      [:agg-inputs AFTER-ELEM
                       (termval (aor-types/->valid-AggInput *invoke-id
                                                            *args))])]
         agent-node-pstate-sym)

        (<<if *finished?
          (complete-agg! agent-name *agg-invoke-id *retry-num)
         (else>)
          (ack-agg! agent-name *agg-invoke-id *retry-num *invoke-id))

       (case> AggAckOp :> {:keys [*agg-invoke-id *ack-val]})
        (ack-agg! agent-name *agg-invoke-id *retry-num *ack-val)
      )
    )))

(deframafn do-transform!*
  [*path $$p]
  (local-transform> *path $$p)
  (:> {:type :success}))

(defn do-transform!
  [path pstate]
  (try
    (do-transform!* path pstate)
    (catch Exception e
      {:type      :failure
       :exception e}
    )))

(defn define-agents!
  [setup topologies stream-topology mb-topology agent-graphs store-info]
  (declare-object* setup
                   (symbol (po/agents-store-info-name))
                   (aor-types/->valid-StoreInfo store-info {}))
  (declare-object* setup
                   (symbol (po/agents-clients-name))
                   (RamaClientsTaskGlobal.
                    (-> agent-graphs
                        keys
                        vec)
                    []))
  (declare-object* setup
                   (symbol (po/agent-node-executor-name))
                   (AgentNodeExecutorTaskGlobal.))

  (let [pstate-write-depot-sym (symbol (po/agent-pstate-write-depot-name))]
    (declare-depot* setup pstate-write-depot-sym (hash-by :key))
    (<<sources stream-topology
     (source> pstate-write-depot-sym
               {:retry-mode :none}
              :> {:keys [*pstate-name *path *agent-name *agent-task-id
                          *agent-id *retry-num]})
      (<<if (at/valid-retry-num? *agent-name
                                 *agent-task-id
                                 *agent-id
                                 *retry-num)
        (this-module-pobject-task-global *pstate-name :> $$p)
        (do-transform! *path $$p :> *ret)
        (ack-return> *ret)
       (else>)
        (ack-return> {:type      :failure
                      :exception (h/ex-info "Agent invoke has been retried"
                                            {})})
      )))
  (queries/declare-agent-get-names-query-topology topologies
                                                  (-> agent-graphs
                                                      keys
                                                      set))
  (doseq [[agent-name agent-graph] agent-graphs]
    (define-agent! agent-name
                   setup
                   topologies
                   stream-topology
                   mb-topology
                   agent-graph)))
