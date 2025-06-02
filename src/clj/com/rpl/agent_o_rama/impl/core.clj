(ns com.rpl.agent-o-rama.impl.core
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentNode
    FinishedAgg]
   [com.rpl.agentorama.impl
    RamaClientsTaskGlobal
    VirtualThreadsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AggAckOp
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.rama.helpers
    TopologyUtils]
   [java.util
    Map]
   [java.util.concurrent
    CompletableFuture]
   [java.util.function
    Function]))

(defn get-invoke-args
  [data]
  (if (aor-types/AgentInvoke? data)
    (:args data)
    ;; accepting Maps allows for REST API invokes, with limitation of allowed
    ;; argument types being those representable by JSON
    (get data "args")))

(defdepotpartitioner agent-streaming-depot-partitioner
  [{:keys [agent-task-id]} num-partitions]
  agent-task-id)

(defdepotpartitioner agent-depot-partitioner
  [data num-partitions]
  (if (aor-types/NodeComplete? data)
    (:task-id data)
    (rand-int num-partitions)))

(deframaop gen-id
  [$$id]
  (local-select> STAY $$id :> *ret)
  (local-transform> (term inc) $$id)
  (:> *ret))

(defn submit-virtual-task!
  [afn]
  (let [^VirtualThreadsTaskGlobal virtual-exec
        (declared-object-task-global (po/agents-virtual-threads-name))]
    (.submitTask virtual-exec afn)))

(defn fetch-graph
  [agent-name]
  (declared-object-task-global (po/agent-graph-task-global-name agent-name)))

(defn hook:finding-graph-version [starting-task-id])

(deframaop fetch-graph-version
  [*agent-name]
  (<<with-substitutions
   [*graph (fetch-graph *agent-name)
    $$graph-history
    (this-module-pobject-task-global (po/graph-history-task-global-name
                                      *agent-name))]
   (get *graph :uuid :> *curr-uuid)
   (local-select> (view last) $$graph-history :> [*version {:keys [*uuid]}])
   (<<if (= *uuid *curr-uuid)
     (:> *version)
    (else>)
     (ops/current-task-id :> *task-id)
     (|global)
     (hook:finding-graph-version *task-id)
     (local-select> (view last) $$graph-history :> [*version {:keys [*uuid]}])
     (<<if (= *uuid *curr-uuid)
       (identity *version :> *found-version)
      (else>)
       (inc (or> *version -1) :> *found-version)
       (local-transform> [(keypath *found-version)
                          (termval (graph/graph->historical-graph-info *graph))]
                         $$graph-history))
     (|direct *task-id)
     (local-transform> [(keypath *found-version)
                        (termval (graph/graph->historical-graph-info *graph))]
                       $$graph-history)
     (:> *found-version)
   )))

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
  (agent-node-state [this]))

(defn mk-agent-node
  [agent-graph graph-task-id curr-node store-info
   ^RamaClientsTaskGlobal rama-clients]
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
        random-source       (ops/current-random-source)]
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
             graph-task-id)
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
              false
              (.getLocalPState rama-clients name)
              (.getPStateWriteDepot rama-clients))]
         (condp = (get store-info name)
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
     AgentNodeInternal
     (agent-node-state [this]
       {:emits      @emits-vol
        :result     @result-vol
        :nested-ops @nested-ops-vol}))))

(defn- node-type
  [graph node]
  (select-any [:node-map (keypath node) :node (view aor-types/node->type-kw)]
              graph))

(defn node-event
  [agent-name task-id invoke-id node-fn agent-node args
   ^RamaClientsTaskGlobal rama-clients]
  (fn []
    (let [res   (try
                  (apply node-fn agent-node args)
                  (catch Throwable t
                    (cljlogging/error t
                                      "Error during agent node execution"
                                      {:node      agent-node
                                       :invoke-id invoke-id})
                    ;; TODO: <<<<>>>> handle errors properly
                    (throw t)
                  ))
          {:keys [emits result nested-ops]} (agent-node-state agent-node)
          depot (.getAgentDepot rama-clients agent-name)]
      (foreign-append!
       depot
       (aor-types/->valid-NodeComplete
        task-id
        invoke-id
        res
        emits
        result
        nested-ops
        (h/current-time-millis))
       :append-ack)
    )))

(defn- assoc-if-void
  [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

(deframaop handle-node-invoke
  [*name *graph-task-id *graph-id *node-fn *invoke-id *next-node *args
   *agg-invoke-id]
  (<<with-substitutions
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name *name))
    *agent-graph (fetch-graph *name)
    *store-info (declared-object-task-global (po/agents-store-info-name))
    *rama-clients (declared-object-task-global (po/agents-clients-name))]
   (mk-agent-node *agent-graph
                  *graph-task-id
                  *next-node
                  *store-info
                  *rama-clients
                  :> *agent-node)

   (h/current-time-millis :> *start-time-millis)
   (ops/current-task-id :> *task-id)
   ;; merge instead of overwrite since agg nodes run completion function on
   ;; already existing node
   (<<ramafn %merger
     [*m]
     (:> (reduce-kv assoc-if-void
                    *m
                    {:graph-id      *graph-id
                     :graph-task-id *graph-task-id
                     :node          *next-node
                     :start-time-millis *start-time-millis
                     :input         *args
                     :agg-invoke-id *agg-invoke-id
                    })))
   (local-transform> [(keypath *invoke-id) (term %merger)] $$nodes)
   (|direct *task-id)
   (submit-virtual-task!
    (node-event *name
                *task-id
                *invoke-id
                *node-fn
                *agent-node
                *args
                *rama-clients))


   (:> {:start-time-millis *start-time-millis})))

(deframaop send-emits>
  [*agent-name *graph-task-id *graph-id *invoke-id *agg-invoke-id *emits]
  (<<with-substitutions
   [$$root
    (this-module-pobject-task-global (po/agent-invoke-task-global-name
                                      *agent-name))]
   (anchor> <root>)
   (ops/explode *emits
                :> {:keys [*invoke-id *target-task-id *node-name *args]})
   (|direct *target-task-id)
   (aor-types/->valid-NodeOp *invoke-id
                             *node-name
                             *args
                             *agg-invoke-id
                             :> *op)
   (anchor> <regular-emit>)

   (hook> <root>)
   (mapv :invoke-id *emits :> *next-invoke-ids)
   (reduce bit-xor *invoke-id *next-invoke-ids :> *ack-val)
   (|direct *graph-task-id)

   (<<if (some? *agg-invoke-id)
     (aor-types/->valid-AggAckOp *agg-invoke-id *ack-val :> *op)
     (anchor> <agg-ack-emit>)
    (else>)
     (<<ramafn %update-ack-val
       [*v]
       (:> (bit-xor *v *ack-val)))
     (local-transform>
      [(keypath *graph-id)
       :ack-val
       (term %update-ack-val)]
      $$root)
     (local-select> (keypath *graph-id)
                    $$root
                    :> {*root-ack-val :ack-val *result :result})
     (<<if (and> (nil? *result) (= 0 *root-ack-val))
       (local-transform>
        [(keypath *graph-id) :result
         (termval (aor-types/->AgentResult "Agent completed without result"
                                           true))]
        $$root))
   )

   (unify> <regular-emit> <agg-ack-emit>)
   (:> *op)))

(defn task-id-key-partitioner
  [num-partitions task-id]
  task-id)

(defn get-node-obj
  [agent-graph node]
  (select-any [:node-map (keypath node) :node]
              agent-graph))

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
  [*name *invoke-id]
  (<<with-substitutions
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name *name))
    *agent-graph (fetch-graph *name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*graph-task-id *graph-id *node *agg-ack-val
                             *agg-state *agg-start-res *agg-invoke-id]})
   (local-transform>
    [(keypath *invoke-id) :agg-finished? (termval true)]
    $$nodes)
   (get-node-obj *agent-graph *node :> {:keys [*node-fn]})
   (vector *agg-state *agg-start-res :> *args)
   (handle-node-invoke *name
                       *graph-task-id
                       *graph-id
                       *node-fn
                       *invoke-id
                       *node
                       *args
                       *agg-invoke-id)
   (:>)))

(deframaop ack-agg!
  [*name *invoke-id *ack-val]
  (<<with-substitutions
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name *name))
    *agent-graph (fetch-graph *name)]
   (local-select> [(keypath *invoke-id) :agg-ack-val] $$nodes :> *agg-ack-val)
   (bit-xor *ack-val *agg-ack-val :> *new-ack-val)
   (local-transform>
    [(keypath *invoke-id) :agg-ack-val (termval *new-ack-val)]
    $$nodes)
   (filter> (= 0 *new-ack-val))
   (complete-agg! *name *invoke-id)
   (:>)))

(defn- define-agent!
  [setup topologies stream-topology name agent-graph]
  (let [graph (graph/resolve-agent-graph agent-graph)
        agent-depot-sym (symbol (po/agent-depot-name name))
        agent-streaming-depot-sym (symbol (str "*_agent-streaming-depot-" name))
        agent-graph-sym (symbol (po/agent-graph-task-global-name name))
        agent-node-pstate-sym (symbol (po/agent-node-task-global-name name))
        agent-invoke-pstate-sym (symbol (po/agent-invoke-task-global-name name))
        agent-streaming-results-pstate-sym
        (symbol (po/agent-streaming-results-task-global-name name))
        agent-graph-history-pstate-sym (symbol
                                        (po/graph-history-task-global-name
                                         name))
        agent-id-gen-pstate-sym (symbol (str "$$_agent-id-gen-" name))
       ]
    (declare-depot* setup agent-depot-sym agent-depot-partitioner)
    (declare-depot* setup
                    agent-streaming-depot-sym
                    agent-streaming-depot-partitioner)

    (declare-object* setup agent-graph-sym graph)

    ;; TODO: <<<<>>>>
    ;; - and ordered IDs is perfect for GC!
    ;;    - especially since they're sequential, so know exactly how many are in
    ;;    there by looking at min and max
    (declare-pstate*
     stream-topology
     agent-invoke-pstate-sym
     po/AGENT-INVOKE-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     agent-streaming-results-pstate-sym
     po/AGENT-STREAMING-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     agent-node-pstate-sym
     po/AGENT-NODE-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     agent-graph-history-pstate-sym
     po/GRAPH-HISTORY-PSTATE-SCHEMA
     {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     agent-id-gen-pstate-sym
     Long
     {:initial-value 0})

    (queries/declare-tracing-query-topology topologies name)

    (<<sources stream-topology
     (source> agent-depot-sym {:retry-mode :none} :> *data)
      (<<cond
       (case> (or> (aor-types/AgentInvoke? *data)
                   (and> (instance? Map *data)
                         (not (record? *data)))))
        (get-invoke-args *data :> *args)
        (ops/current-task-id :> *graph-task-id)
        (gen-id agent-id-gen-pstate-sym :> *graph-id)
        (ack-return> [*graph-task-id *graph-id])
        (h/random-long :> *invoke-id)
        (fetch-graph-version name :> *version)
        (local-transform>
         [(keypath *graph-id)
          (termval {:root-invoke-id *invoke-id
                    :invoke-args    *args
                    :graph-version  *version
                    :ack-val        *invoke-id})]
         agent-invoke-pstate-sym)
        (aor-types/->valid-NodeOp *invoke-id
                                  (get agent-graph-sym :start-node)
                                  *args
                                  nil
                                  :> *op)

       (case> (aor-types/NodeComplete? *data))
        (identity *data
                  :> {:keys [*invoke-id
                             *node-fn-res
                             *emits
                             *result
                             *nested-ops
                             *finish-time-millis]})
        (<<ramafn %merger
          [*m]
          (:> (reduce-kv assoc
                         *m
                         {:emits      *emits
                          :result     *result
                          :nested-ops *nested-ops
                          :finish-time-millis *finish-time-millis})))
        (local-transform> [(keypath *invoke-id) (term %merger)]
                          agent-node-pstate-sym)
        (local-select> (keypath *invoke-id)
                       agent-node-pstate-sym
                       :> {:keys [*graph-task-id *graph-id *node
                                  *agg-invoke-id]})
        (get-node-obj agent-graph-sym *node :> *node-obj)

        (<<subsource *node-obj
         (case> Node)
          (identity *invoke-id :> *invoke-id)

         (case> NodeAggStart)
          ;; TODO: <<<<>>>> it's possible this initialization didn't happen
          (local-transform> [(keypath *agg-invoke-id)
                             :agg-start-res
                             (termval *node-fn-res)]
                            agent-node-pstate-sym)
          (identity *invoke-id :> *invoke-id)


         (case> NodeAgg)
          (local-select> (keypath *invoke-id)
                         agent-node-pstate-sym
                         :> {*invoke-id :agg-start-invoke-id})

        )

        ;; AgentNode implementation makes it impossible for there to be both
        ;; emits and result
        (<<if (some? *result)
          (|direct *graph-task-id)
          (local-transform>
           [(keypath *graph-id)
            :result
            ;; TODO: <<<<<>>>>> what about case of a retry?
            ;;  - what about case where it errors on one branch but has a result
            ;;  in the other branch?
            ;;  - seems like need "execution ID" so that it can only be
            ;;  overridden on a fresh retry
            nil?
            (termval *result)]
           agent-invoke-pstate-sym))
        (send-emits> name
                     *graph-task-id
                     *graph-id
                     *invoke-id
                     *agg-invoke-id
                     *emits
                     :> *op)

       (default> :unify false)
        (throw! (h/ex-info "Unrecognized data type" {:class (class *data)})))

      ;; requires *graph-id, *graph-task-id, *op to be in scope
      (<<if (aor-types/NodeOp? *op)
        (get *op :next-node :> *next-node)
        (get-node-obj agent-graph-sym *next-node :> *op-obj)
       (else>)
        (identity *op :> *op-obj))

      (<<subsource *op-obj
       (case> Node :> {:keys [*node-fn]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
        (handle-node-invoke
         name
         *graph-task-id
         *graph-id
         *node-fn
         *invoke-id
         *next-node
         *args
         *agg-invoke-id)

       (case> NodeAggStart :> {:keys [*node-fn *agg-node-name]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
        (h/random-long :> *new-agg-invoke-id)
        (handle-node-invoke
         name
         *graph-task-id
         *graph-id
         *node-fn
         *invoke-id
         *next-node
         *args
         *new-agg-invoke-id
         :> {:keys [*start-time-millis]})
        (get-node-obj agent-graph-sym *agg-node-name :> {:keys [*init-fn]})
        ;; TODO: <<<<<>>>>> propagate errors
        (h/invoke *init-fn :> *init-agg-state)
        (local-transform>
         [(keypath *invoke-id) :started-agg? (termval true)]
         agent-node-pstate-sym)
        (local-transform>
         [(keypath *new-agg-invoke-id)
          (termval {:graph-id            *graph-id
                    :graph-task-id       *graph-task-id
                    :node                *agg-node-name
                    :start-time-millis   *start-time-millis
                    :agg-invoke-id       *agg-invoke-id
                    :agg-inputs          []
                    :agg-state           *init-agg-state
                    :agg-ack-val         *invoke-id
                    :agg-start-invoke-id *invoke-id
                   })]
         agent-node-pstate-sym)


       (case> NodeAgg :> {:keys [*update-fn]})
        (identity *op
                  :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
        (assert! (some? *agg-invoke-id))
        (local-select> (keypath *agg-invoke-id)
                       agent-node-pstate-sym
                       :> {*agg-state :agg-state
                           *parent-agg-invoke-id :agg-invoke-id
                           *agg-start-invoke-id :agg-start-invoke-id
                          })
        ;; TODO: <<<<>>>> catch exceptions and propagate failure
        (apply *update-fn *agg-state *args :> *res)
        (extract-agg-result *res :> {:keys [*new-agg-state *finished?]})

        (local-transform>
         [(keypath *agg-invoke-id)
          (multi-path [:agg-state (termval *new-agg-state)]
                      [:agg-inputs AFTER-ELEM
                       (termval (aor-types/->valid-AggInput *invoke-id
                                                            *args))])]
         agent-node-pstate-sym)
        (local-transform> [(keypath *invoke-id)
                           :invoked-agg-invoke-id
                           (termval *agg-invoke-id)]
                          agent-node-pstate-sym)

        (<<if *finished?
          (complete-agg! name *agg-invoke-id)
         (else>)
          (ack-agg! name *agg-invoke-id *invoke-id))

       (case> AggAckOp :> {:keys [*agg-invoke-id *ack-val]})
        (ack-agg! name *agg-invoke-id *ack-val)
      )


     (source> agent-streaming-depot-sym
              :> {:keys [*agent-id
                          *node
                          *invoke-id
                          *streaming-index
                          *value]})
      ;; this ensures idempotence
      (<<ramafn %correct-index?
        [*l]
        (:> (= (count *l) *streaming-index)))
      (local-transform>
       [(keypath *agent-id *node *invoke-id)
        (pred %correct-index?)
        AFTER-ELEM
        (termval *value)]
       agent-streaming-results-pstate-sym)
    )


    ;; TODO: <<<<>>>> implement
    ;;  - need abstraction for human in the loop
    ;;    - need depot for this too
    ;;  - client should query for number of args
    ;;  - need ability to set breakpoints, which is implicit human in the loop?
  ))

(defn define-agents!
  [setup topologies stream-topology agent-graphs store-info]
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
                   (symbol (po/agents-virtual-threads-name))
                   (VirtualThreadsTaskGlobal.))

  (let [pstate-write-depot-sym (symbol (po/agent-pstate-write-depot-name))]
    (declare-depot* setup pstate-write-depot-sym (hash-by :key))
    (<<sources stream-topology
     (source> pstate-write-depot-sym
               {:retry-mode :none}
              :> {:keys [*pstate-name *path]})
      (this-module-pobject-task-global *pstate-name :> $$p)
      (local-transform> *path $$p)
    ))
  (doseq [[name agent-graph] agent-graphs]
    (define-agent! setup topologies stream-topology name agent-graph)))
