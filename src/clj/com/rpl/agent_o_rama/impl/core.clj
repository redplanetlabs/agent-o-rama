(ns com.rpl.agent-o-rama.impl.core
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
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
    AsyncResult
    FinishedAgg]
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

(deframaop gen-id
  [$$id]
  (local-select> STAY $$id :> *ret)
  (local-transform> (term inc) $$id)
  (:> *ret))


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
  [task-thread-id-vol]
  (when (empty? @task-thread-id-vol)
    (let [^com.rpl.rama.ModuleInstanceInfo info (ops/module-instance-info)]
      (vreset! task-thread-id-vol
               (-> (.getTaskThreadIds info)
                   shuffle
                   seq))))
  (let [ret (first @task-thread-id-vol)]
    (vswap! task-thread-id-vol next)
    ret))

(defprotocol AgentNodeInternal
  (agent-node-state [this]))

(defn mk-agent-node
  [agent-graph graph-task-id curr-node store-info]
  (let [task-id             (ops/current-task-id)
        result-vol          (volatile! nil)
        emits-vol           (volatile! [])
        async-ops-vol       (volatile! [])
        completable-futures (java.util.IdentityHashMap.)
        start-time-millis   (TopologyUtils/currentTimeMillis)
        task-thread-ids-vol (volatile! nil)
        emit-count-vol      (volatile! 0)
        valid-output-nodes  (-> agent-graph
                                :node-map
                                (get curr-node)
                                :output-nodes)
        ^com.rpl.rama.ModuleInstanceInfo info (ops/module-instance-info)
        this-module-name    (.getModuleName info)]
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
       (let [args
             (mapv
              (fn [arg]
                (cond
                  (instance? AsyncResult arg)
                  arg

                  (instance? CompletableFuture arg)
                  (do
                    (when-not (.containsKey completable-futures arg)
                      (let [i (count @async-ops-vol)]
                        (.put completable-futures arg i)
                        (vswap! async-ops-vol
                                conj
                                (aor-types/->valid-AsyncOpInfo nil nil nil))
                        (vswap! emits-vol
                                conj
                                (.thenApply
                                 ^CompletableFuture arg
                                 (reify
                                  Function
                                  (apply [_ v]
                                    [i start-time-millis (h/current-time-millis)
                                     v]))))))
                    (.thenApply
                     ^CompletableFuture arg
                     (reify
                      Function
                      (apply [_ v]
                        [(.get completable-futures arg) v]))))

                  :else
                  (aor-types/->valid-AgentNodeArg arg nil)))
              args)
             emit-count (vswap! emit-count-vol inc)]
         (vswap! emits-vol
                 conj
                 (aor-types/->valid-AgentNodeEmit
                  (h/random-long)
                  (if (selected-any? [:node-map (keypath node) :node
                                      #(instance? Node %)]
                                     agent-graph)
                    (if (= emit-count 1)
                      task-id
                      (next-task-thread-id task-thread-ids-vol))
                    graph-task-id)
                  node
                  args
                 ))))
     (result [this arg]
       (when (some? @result-vol)
         (throw (h/ex-info "Cannot have multiple results"
                           {:current-result @result-vol})))
       (when-not (empty? @emits-vol)
         (throw (h/ex-info "Cannot both emit and result" {})))
       (vreset! result-vol (aor-types/->valid-AgentResult arg)))
     (getAgentObject [this name]
                     ;; TODO: <<<<>>>>
     )
     (getStore [this name]
       (let [store-params
             (simpl/->valid-StoreParams this-module-name
                                        this-module-name
                                        name
                                        emits-vol
                                        async-ops-vol)]
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
       {:emits     @emits-vol
        :result    @result-vol
        :async-ops @async-ops-vol}))))

(defn- immediate-async-resolve?
  [arg]
  (or (instance? CompletableFuture arg)
      (aor-types/AsyncResultPStateQuery? arg)))

(deframaop handle-async-emits
  [*parent-invoke-id *async-ops *emits]
  (ops/current-task-id :> *node-task-id)
  (loop<- [*res []
           *async-ops *async-ops
           *emits (seq *emits)
           :> *async-ops *emits]
    (<<if (nil? *emits)
      (:> *async-ops *res)
     (else>)
      (first *emits :> *emit)
      (<<cond
       (case> (aor-types/AsyncPStateTransform? *emit))
        ;; TODO: <<<<>>>> need to propagate failures back as the result
        ;;  - need to expose try/catch somehow...
        (identity *emit :> {:keys [*pstate-name *path *async-op-index]})
        (this-module-pobject-task-global *pstate-name :> $$p)
        (h/current-time-millis :> *start-time-millis)
        (|path$$ $$p *path)
        (this-module-pobject-task-global *pstate-name :> $$p)
        (local-transform> *path $$p)
        (h/current-time-millis :> *finish-time-millis)
        (|direct *node-task-id)
        (continue>
         *res
         (h/clj-transform
          (path>
           (nthpath *async-op-index)
           (termval
            (aor-types/->valid-AsyncOpInfo
             *start-time-millis
             *finish-time-millis
             {"type" "pstate-transform"
              "name" *pstate-name})))
          *async-ops)
         (next *emits))

       (case> (aor-types/AsyncPStateQuery? *emit))
        ;; TODO: <<<<>>>> need to propagate failures back as the result
        ;;  - need to expose try/catch somehow...
        (identity *emit
                  :> {:keys [*module-name *pstate-name *path
                             *async-op-index]})
        (pobject-task-global *module-name *pstate-name :> $$p)
        (h/current-time-millis :> *start-time-millis)
        (|path$$ $$p *path)
        (pobject-task-global *module-name *pstate-name :> $$p)
        (local-select> *path $$p :> *res)
        (h/current-time-millis :> *finish-time-millis)
        (|direct *node-task-id)
        (continue>
         *res
         (h/clj-transform
          (path>
           (nthpath *async-op-index)
           (termval
            (aor-types/->valid-AsyncOpInfo
             *start-time-millis
             *finish-time-millis
             {"type"        "pstate-select"
              "module-name" *module-name
              "name"        *pstate-name
              "result"      *res})))
          *async-ops)
         (next *emits))

       (case> (instance? CompletableFuture *emit))
        ;; TODO: <<<>>>> propagate failures
        (completable-future> *emit
                             :> [*async-op-index *start-millis *finish-millis
                                 *v])
        (continue>
         *res
         (h/clj-transform
          (path>
           (nthpath *async-op-index)
           (termval
            (aor-types/->valid-AsyncOpInfo
             *start-millis
             *finish-millis
             {"type"   "completable-future"
              "result" *v})))
          *async-ops)
         (next *emits))

       (default>)
        (select>
          (subselect
           :args
           INDEXED-VALS
           (collect-one FIRST)
           LAST
           immediate-async-resolve?)
          *emits
          :> *asyncs)
        (loop<- [*emit *emit
                 *asyncs (seq *asyncs)
                 :> *emit]
          (<<if (nil? *asyncs)
            (:> *emit)
           (else>)
            (first *asyncs :> [*arg-index *v])
            (<<cond
             (case> (instance? CompletableFuture *v))
              ;; failure is already handled before
              (completable-future> *v :> [*async-op-index *res])

             (case> (aor-types/AsyncResultPStateQuery? *v))
              (get *v :async-op-index :> *async-op-index)
              (select> [(nthpath *async-op-index) :info (keypath "result")]
                *async-ops
                :> *res)

             (default> :unify false)
              (throw! (h/ex-info "Unknown async type" {:class (class *v)})))
            (h/clj-transform
             (path>
              (nthpath *arg-index)
              (termval
               (aor-types/->valid-AgentNodeArg
                *res
                *async-op-index)))
             *emit
             :> *new-emit)
            (continue> *new-emit (next *asyncs))
          ))
        (continue> (conj *res *emit) *async-ops (next *emits))
      )))
  (:> *async-ops *emits))

(defn- emits-finished?
  [emits]
  (not
   (selected-any?
    [ALL :args ALL #(not (aor-types/AgentNodeArg? %))]
    emits)))

(defn- node-type
  [graph node]
  (select-any [:node-map (keypath node) :node (view aor-types/node->type-kw)]
              graph))

(deframafn handle-node-invoke
  [*name *graph-task-id *graph-id *node-fn *invoke-id *next-node *args
   *agg-invoke-id]
  (<<with-substitutions
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name *name))
    *agent-graph (fetch-graph *name)
    *store-info (declared-object-task-global (po/agents-store-info-name))]
   (mk-agent-node *agent-graph
                  *graph-task-id
                  *next-node
                  *store-info
                  :> *agent-node)
   (h/current-time-millis :> *start-time-millis)
   ;; TODO: <<<<>>>> should be done in a try/catch with exceptions causing
   ;; non-retryable failure
   (apply *node-fn *agent-node *args :> *node-fn-res)
   (agent-node-state *agent-node :> {:keys [*async-ops *emits *result]})
   (handle-async-emits *invoke-id *async-ops *emits :> *async-ops *emits)
   (local-transform>
    [(keypath *invoke-id)
     (termval {:graph-id          *graph-id
               :graph-task-id     *graph-task-id
               :node              *next-node
               :async-ops         *async-ops
               :start-time-millis *start-time-millis
               :input             *args
               :emits             *emits
               :result            *result
               :agg-invoke-id     *agg-invoke-id
              })]
    $$nodes)
   (:> {:start-time-millis *start-time-millis
        :node-fn-res *node-fn-res
        :emits       *emits
        :result      *result})))

(deframaop send-emits>
  [*agent-name *graph-task-id *invoke-id *agg-invoke-id *emits]
  (anchor> <root>)
  (ops/explode *emits
               :> {:keys [*invoke-id *target-task-id *node-name *args]})
  (mapv :val *args :> *unwrapped-args)
  (|direct *target-task-id)
  (aor-types/->valid-NodeOp *invoke-id
                            *node-name
                            *unwrapped-args
                            *agg-invoke-id
                            :> *op)
  (anchor> <regular-emit>)

  (hook> <root>)
  (filter> (some? *agg-invoke-id))
  (mapv :invoke-id *emits :> *next-invoke-ids)
  (reduce bit-xor *invoke-id *next-invoke-ids :> *ack-val)
  (|direct *graph-task-id)
  (aor-types/->valid-AggAckOp *agg-invoke-id *ack-val :> *op)
  (anchor> <agg-ack-emit>)

  (unify> <regular-emit> <agg-ack-emit>)
  (:> *op))

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
    {:new-agg-state (.value ^FinishedAgg res)
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
                       *agg-invoke-id
                       :> {:keys [*emits *result]})
   (:> *emits *result)))

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
   (complete-agg! *name *invoke-id :> *emits *result)
   (:> *emits *result)))

(defn- define-agent!
  [setup topologies stream-topology name agent-graph]
  (let [graph (graph/resolve-agent-graph agent-graph)
        agent-depot-sym (symbol (po/agent-depot-task-global-name name))
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
    (declare-depot* setup agent-depot-sym :random)
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
       (case> (or> (aor-types/AgentInvoke? *data) (instance? Map *data)))
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
                    :graph-version  *version})]
         agent-invoke-pstate-sym)
        (aor-types/->valid-NodeOp *invoke-id
                                  (get agent-graph-sym :start-node)
                                  *args
                                  nil
                                  :> *op)

       (case> (aor-types/AsyncFutureResult? *data))
        (identity *data
                  :> {:keys [*invoke-id *async-op-index *result
                             *start-time-millis *finish-time-millis *info]})
        (local-transform>
         [(keypath *invoke-id)
          (multi-path
           [:async-ops
            (nthpath *async-op-index)
            (termval
             (aor-types/->valid-AsyncOpInfo
              *start-time-millis
              *finish-time-millis
              *info))]
           [:emits
            ALL
            :args
            ALL
            aor-types/AsyncResultOutOfBand?
            (selected? :async-op-index (pred= *async-op-index))
            (termval
             (aor-types/->valid-AgentNodeArg
              *result
              *async-op-index
             ))])]
         agent-node-pstate-sym)
        (local-select> (keypath *invoke-id)
                       agent-node-pstate-sym
                       :> {:keys [*graph-id *graph-task-id *emits *node
                                  *agg-invoke-id *parent-agg-invoke-id]})
        (filter> (emits-finished? *emits))
        (local-transform>
         [(keypath *invoke-id)
          :finish-time-millis
          (termval *finish-time-millis)]
         agent-node-pstate-sym)
        (node-type agent-graph-sym *node :> *node-type)
        (ifexpr (= *node-type aor-types/AGG-NODE-KW)
          *parent-agg-invoke-id
          *agg-invoke-id
          :> *agg-invoke-id)
        (send-emits> name
                     *graph-task-id
                     *invoke-id
                     *agg-invoke-id
                     *emits
                     :> *op)

       (default> :unify false)
        (throw! (h/ex-info "Unrecognized data type" {:class (class *data)})))

      ;; requires *graph-id, *graph-task-id, *op to be in scope
      (loop<- [*op *op]
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
           *agg-invoke-id
           :> {:keys [*emits *result]})

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
           :> {:keys [*start-time-millis *node-fn-res *emits *result]})
          (get-node-obj agent-graph-sym *agg-node-name :> {:keys [*init-fn]})
          ;; TODO: <<<<<>>>>> propagate errors
          (h/invoke *init-fn :> *init-agg-state)
          (local-transform>
           [(keypath *new-agg-invoke-id)
            (termval {:graph-id            *graph-id
                      :graph-task-id       *graph-task-id
                      :node                *agg-node-name
                      :start-time-millis   *start-time-millis
                      :agg-invoke-id       *agg-invoke-id
                      :agg-inputs          []
                      :agg-state           *init-agg-state
                      :agg-start-res       *node-fn-res
                      :agg-ack-val         *invoke-id
                      :agg-start-invoke-id *invoke-id
                     })]
           agent-node-pstate-sym)
          (identity *new-agg-invoke-id :> *agg-invoke-id)

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

          (<<if *finished?
            (complete-agg! name *agg-invoke-id :> *emits *result)
           (else>)
            (ack-agg! name *agg-invoke-id *invoke-id :> *emits *result))
          (identity *agg-start-invoke-id :> *invoke-id)
          (identity *parent-agg-invoke-id :> *agg-invoke-id)


         (case> AggAckOp :> {:keys [*agg-invoke-id *ack-val]})
          (ack-agg! name *agg-invoke-id *ack-val :> *emits *result)
          (local-select> (keypath *agg-invoke-id)
                         agent-node-pstate-sym
                         :> {*agg-invoke-id :agg-invoke-id
                             *invoke-id     :agg-start-invoke-id})
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
        (<<if (emits-finished? *emits)
          (local-transform>
           [(keypath *invoke-id)
            :finish-time-millis
            (termval (h/current-time-millis))]
           agent-node-pstate-sym)
          (send-emits> name
                       *graph-task-id
                       *invoke-id
                       *agg-invoke-id
                       *emits
                       :> *op)
          (continue> *op)))


     (source> agent-streaming-depot-sym
              :> {:keys [*agent-id
                          *node
                          *invoke-id
                          *emit-index
                          *arg-index
                          *streaming-index
                          *value]})
      (<<ramafn %correct-index?
        [*l]
        (:> (= (count *l) *streaming-index)))
      (local-transform>
       [(keypath *agent-id *node *invoke-id *emit-index *arg-index)
        (pred %correct-index?)
        AFTER-ELEM
        (termval *value)]
       agent-streaming-results-pstate-sym)
    )


    ;; TODO: <<<<>>>> implement
    ;;  - need abstraction for human in the loop
    ;;    - need depot for this too
    ;;  - client should query for number of args
    ;;  - task global for out-of-band events
    ;;  - need ability to set breakpoints, which is implicit human in the loop?
  ))

(defn define-agents!
  [setup topologies stream-topology agent-graphs store-info]
  (declare-object* setup
                   (symbol (po/agents-store-info-name))
                   (aor-types/->valid-StoreInfo store-info))
  (doseq [[name agent-graph] agent-graphs]
    (define-agent! setup topologies stream-topology name agent-graph)))
