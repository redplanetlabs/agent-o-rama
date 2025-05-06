(ns com.rpl.agent-o-rama.impl
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require [clojure.set :as set]
            [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.types :as aor-types]
            [com.rpl.rama.ops :as ops]
            [loom.attr :as lattr]
            [loom.graph :as graph])
  (:import [com.rpl.agentorama
             AgentGraph
             AgentNode
             AsyncResult
             MultiAgg
             MultiAgg$Impl]
           [com.rpl.agentorama.impl BuiltInAgg]
           [com.rpl.agentorama.ops RamaVoidFunction3]
           [com.rpl.agent_o_rama.types
             AgentNodeEmit
             AgentResult
             AggInput
             AsyncOpInfo
             Node
             NodeAgg
             NodeAggStart]
           [com.rpl.rama.helpers TopologyUtils]
           [com.rpl.rama.ops RamaAccumulatorAgg RamaCombinerAgg]
           [java.util Function Map UUID]))

(defprotocol AgentGraphInternal
  (internal-add-init! [this afn])
  (internal-add-node! [this name output-nodes-spec node])
  (agent-graph-state [this]))

(defprotocol MultiAggInternal
  (internal-add-handler! [this name afn])
  (multi-agg-state [this]))

(defmacro reify-MultiAgg [& body]
  `(reify ~'MultiAgg$Impl
    ~@(for [i (range 0 (- h/MAX-ARITY 1))]
        (let [name-sym (h/type-hinted String 'name#)
              jfn-sym (h/type-hinted (h/rama-function-class (+ i 1)) 'jfn#)
              on-sym (h/type-hinted MultiAgg$Impl 'on)]
          `(~on-sym [this# ~name-sym ~jfn-sym]
            (internal-add-handler!
              this#
              ~name-sym
              (h/convert-jfn ~jfn-sym))
            )))
    ~@body
    ))

(defn mk-multi-agg []
  (let [on-vol (volatile! {})
        init-vol (volatile! nil)]
    (reify-MultiAgg
      MultiAggInternal
      (internal-add-init! [this afn]
        (when (some? @init-vol)
          (throw (ex-info "MultiAgg already has init function specified" {})))
        (vreset! init-vol afn)
        this)
      (internal-add-handler! [this name afn]
        (when (contains? @on-vol name)
          (throw (ex-info "MultiAgg already has handler for given name" {:name name})))
        (vswap! on-vol assoc name afn)
        this)
      (multi-agg-state [this]
        {:init-fn (or @init-vol (fn [] nil))
         :on-handlers @on-vol
         }))
      ))

(defmacro reify-AgentGraph [& body]
  `(reify ~'AgentGraph
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (h/type-hinted String 'name#)
              osym (h/type-hinted Object 'outputNodesSpec#)
              jfn-sym (h/type-hinted (h/rama-void-function-class i) 'jfn#)
              node-sym (h/type-hinted AgentGraph 'node)]
          `(~node-sym [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
              this#
              ~name-sym
              ~osym
              (aot-types/->Node (h/convert-void-jfn ~jfn-sym)))
            )))
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (h/type-hinted String 'name#)
              osym (h/type-hinted Object 'outputNodesSpec#)
              jfn-sym (h/type-hinted (h/rama-void-function-class i) 'jfn#)
              agg-start-node-sym (h/type-hinted AgentGraph 'aggStartNode)]
          `(~agg-start-node-sym [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
              this#
              ~name-sym
              ~osym
              (aot-types/->NodeAggStart (h/convert-void-jfn ~jfn-sym) nil))
            )))
    ~@body
    ))

(defn- normalize-output-nodes [spec]
  (cond (string? spec) [spec]
        (coll? spec) (set spec)
        (nil? spec) #{}
        :else (throw (ex-info "Invalid output nodes spec"
                              {:spec spec :class (class spec)}))))

(defn internal-add-agg-node!
  [this name outputNodesSpec agg afn]
  (if (instance? MultiAgg$Impl agg)
    (let [{:keys [init-fn on-handlers]} (multi-agg-state agg)
          update-fn (fn [state dispatch-name & args]
                       (when-not (contains? on-handlers dispatch-name)
                         (throw (ex-info "Invalid dispatch name for MultiAgg"
                                         {:valid-names (keys on-handlers)
                                          :name dispatch-name})))
                       (apply (get on-handlers dispatch-name) args))]
      (internal-add-node!
        this
        name
        outputNodesSpec
        (aot-types/->NodeAgg init-fn update-fn afn)))
    (let [agg (if (instance? BuiltInAgg agg)
                (.agg ^BuiltInAgg agg)
                agg)
          init-fn (ops/agg->init-fn agg)
          update-fn (ops/agg->update-fn agg)]
    (internal-add-node!
      this
      name
      outputNodesSpec
      (aot-types/->NodeAgg init-fn update-fn afn)))))

(defn internal-add-agg-node-java!
  [this name outputNodesSpec agg jfn]
  (internal-add-agg-node!
    this
    name
    outputNodesSpec
    agg
    (h/convert-void-jfn jfn)))

(defn mk-agent-graph []
  (let [nodes-vol (volatile! {})
        start-node-vol (volatile! nil)]
    (reify-AgentGraph
      (aggNode ^AgentGraph [this ^String name ^Object outputNodesSpec ^RamaAccumulatorAgg agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
          this
          name
          outputNodesSpec
          agg
          impl))
      (aggNode ^AgentGraph [this ^String name ^Object outputNodesSpec ^RamaCombinerAgg agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
          this
          name
          outputNodesSpec
          agg
          impl))
      (aggNode ^AgentGraph [this ^String name ^Object outputNodesSpec ^MultiAgg$Impl agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
          this
          name
          outputNodesSpec
          agg
          impl))
      (aggNode ^AgentGraph [this ^String name ^Object outputNodesSpec ^BuiltInAgg agg ^RamaVoidFunction3 impl]
        (internal-add-agg-node-java!
          this
          name
          outputNodesSpec
          agg
          impl))
      AgentGraphInternal
      (internal-add-node! [this name output-nodes-spec node-obj]
        (when (or (nil? name) (= "" name))
          (throw (ex-info "Node name cannot be nil or empty string" {:name name})))
        (when (contains? @nodes-vol name)
          (throw (ex-info "Node already exists" {:name name})))
        (when (nil? @start-node-vol)
          (vreset! start-node-vol name))
        (vswap! nodes-vol
                assoc
                name
                {:node-obj node-obj
                 :output-nodes (normalize-output-nodes output-nodes-spec)})
        this)
      (agent-graph-state [this]
        {:nodes @nodes-vol
         :start-node @start-node-vol})
      )))

(defn- nodes->graph [nodes]
  (reduce-kv
    (fn [graph name {:keys [node-obj output-nodes]}]
      (reduce
        (fn [graph output]
          (graph/add-edges graph [name output]))
        (-> graph
            (graph/add-nodes name)
            (lattr/add-attr name :node-obj node-obj))
        output-nodes))
    (graph/digraph)
    nodes))

(defn- annotate-aggs [graph node traversed agg-stack]
  (let [curr-agg (peek agg-stack)
        node-obj (lattr/attr graph node :node-obj)
        next-traversed (conj traversed node)]
    (cond
      (contains? traversed node)
      (if (not= (lattr/attr graph node :agg) curr-agg)
        (throw (ex-info "Invalid loop to different agg context"
                        {:agg1 curr-agg
                         :agg2 (lattr/attr graph node :agg)}))
        graph)

      (instance? Node node-obj)
      (reduce
        (fn [graph output-node]
          (annotate-aggs
            graph
            output-node
            next-traversed
            agg-stack
            ))
        (lattr/add-attr graph node :agg curr-agg)
        (graph/successors graph node))

      (instance? NodeAggStart node-obj)
      (let [new-agg-stack (conj agg-stack node)]
        (reduce
          (fn [graph output-node]
            (annotate-aggs
              graph
              output-node
              next-traversed
              new-agg-stack
              ))
          (lattr/add-attr graph node :agg curr-agg)
          (graph/successors graph node)))

      (instance? NodeAgg node-obj)
      (do
        (when (nil? curr-agg)
          (throw (ex-info "Reached AggNode outside of agg context" {:name node})))
        (let [new-agg-stack (pop agg-stack)
              start-node-obj (lattr/attr graph curr-agg :node-obj)]
          (if (some? (:agg-node-name start-node-obj))
            (throw (ex-info "Only one AggNode can be reached per aggregation context"
                            {:curr-agg curr-agg :other-agg node})))
          (reduce
            (fn [graph output-node]
              (annotate-aggs
                graph
                output-node
                next-traversed
                new-agg-stack
                ))
            (-> graph
                (lattr/add-attr node :agg curr-agg)
                (lattr/add-attr curr-agg :node-obj (assoc start-node-obj :agg-node-name node)))
            (graph/successors graph node))))

      :else
      (throw (ex-info "Unreachable" {})))
    ))

(defn resolve-agent-graph [agent-graph]
  (let [{:keys [nodes start-node]} (agent-graph-state agent-graph)
        graph (nodes->graph nodes)
        agg-graph (annotate-aggs graph start-node #{} [])]
    (aor-types/->valid-AgentGraph
      (reduce
        (fn [m node]
          (let [output-nodes (graph/successors agg-graph node)]
            (assoc m
                   node
                   (aor-types/->valid-AgentNode
                     (lattr/attr graph node :node-obj)
                     (set output-nodes)
                     (lattr/attr graph node :agg)))
            ))
        {}
        (graph/nodes agg-graph))
      start-node
      (str (UUID/randomUUID)))))

(defn get-invoke-args [data]
  ;; accepting Maps allows for REST API invokes, with limitation of allowed
  ;; argument types being those representable by JSON
  (if (instance? Map data)
    (get data "args")
    (:args data)))

(defdepotpartitioner agent-streaming-depot-partitioner
  [{:keys [agent-task-id]} num-partitions]
  agent-task-id)

(deframaop gen-id [$$id]
  (local-select> STAY $$id :> *ret)
  (local-transform> (term inc) $$id :> *ret)
  (:> *ret))

(defn- agent-graph-task-global-name [agent-name]
  (str "*_agent-graph-" agent-name))

(defn- agent-node-task-global-name [agent-name]
  (str "$$_agent-node-" agent-name))

(defn- graph-history-task-global-name [agent-name]
  (str "$$_agent-graph-history-" agent-name))

(defn- graph->historical-graph-info [graph]
  (aor-types/->valid-HistoricalAgentGraphInfo
    (transform
      [MAP-VALS
       (view
         (fn [{:keys [node output-nodes agg-context]}]
           (aor-types/->valid-HistoricalAgentNodeInfo
             (aor-types/node->type-kw node)
             output-nodes
             agg-context
             )))]
      (:node-map graph))
    (:start-node graph)
    (:uuid graph)))

(deframaop fetch-graph-version [*agent-name]
  (<<with-substitutions
    [*graph (declared-object-task-global (agent-graph-task-global-name *agent-name))
     $$graph-history (this-module-pobject-task-global (graph-history-task-global-name *agent-name))]
    (get *graph :uuid :> *curr-uuid)
    (local-select> LAST $$graph-history :> [*version {:keys [*uuid]}])
    (<<if (= *uuid *curr-uuid)
      (:> *version)
     (else>)
      (ops/current-task-id :> *task-id)
      (|global)
      (local-select> LAST $$graph-history :> [*version {:keys [*uuid]}])
      (<<if (= *uuid *curr-uuid)
        (identity *version :> *found-version)
       (else>)
        (inc (or> *version -1) :> *found-version)
        (local-transform> [(keypath *found-version) (termval (graph->graph-info *graph))]
          $$graph-history))
      (|direct *task-id)
      (local-transform> [(keypath *found-version) (termval (graph->graph-info *graph))]
        $$graph-history)
      (:> *found-version)
      )))

(defprotocol AgentNodeInternal
  (agent-node-state [this]))

(defn mk-agent-node [agent-graph graph-task-id]
  (let [task-id (ops/current-task-id)
        result-vol (volatile! nil)
        emits-vol (volatile! [])
        async-ops-vol (volatile! [])
        completable-futures (java.util.IdentityHashMap.)
        start-time-millis (TopologyUtils/currentTimeMillis)]
    (reify AgentNode
      (emit [this node args]
        (when (some? @result-vol)
          (throw (ex-info "Cannot emit with result already specified" {:current-result @result-vol})))
        (let [args (mapv
                    (fn [arg]
                      (cond
                        (instance? AsyncResult arg)
                        arg

                        (instance? CompletableFuture arg)
                        (do
                          (when-not (.containsKey completable-futures arg)
                            (let [i (count @async-ops-vol)]
                              (.put completable-futures arg i)
                              (vswap! async-ops-vol conj (aor-types/->static-map->valid-AsyncOpInfo {}))
                              (vswap! emits-vol conj
                                (.thenApply
                                  ^CompletableFuture arg
                                  (reify Function
                                    (apply [_ v]
                                      [i start-time-millis (h/current-time-millis) v]))))))
                          (.thenApply
                            ^CompletableFuture arg
                            (reify Function
                              (apply [_ v]
                                [(.get completable-futures arg) v]))))

                        :else
                        (aor-types/->valid-AgentNodeArg arg nil)))
                    args))]
          (vswap! emits-vol conj
            (aor-types/->valid-AgentNodeEmit
              nil
              (if (selected-any? [:node-map (keypath node) :node #(instance? Node %)] agent-graph)
                task-id
                graph-task-id)
              node
              args
              )))
      (emitParallel [this node args]
        (when (some? @result-vol)
          (throw (ex-info "Cannot emit with result already specified" {:current-result @result-vol})))
        ;; TODO: <<<<>>>>
        ;;  - need the task global with shuffled task IDs
        ;;  - ideally have the thread->tasks mapping
        ;;  - validate that this is not going to an agg node or agg start node
        ;;  - error if using this to go to start agg or agg node
        (assert false)
        )
      (result [this arg]
        (when (some? @result-vol)
          (throw (ex-info "Cannot have multiple results" {:current-result @result-vol})))
        (when-not (empty? @emits-vol)
          (throw (ex-info "Cannot both emit and result" {})))
        (vreset! result-vol (aor-types/->valid-AgentResult arg)))
      (getObject [this name]
        ;; TODO: <<<<>>>>
        ;;  - how would this fetch mirrors?
        ;;    - probably need a getPState/getStore API that takes in module name as input
        ;;    - though "declareStore" API can put mapping into a task global...
        )
      AgentNodeInternal
      (agent-node-state [this]
        {:emits @emits-vol
         :result @result-vol
         :async-ops @async-ops-vol}))))

(defn- immediate-async-resolve? [arg]
  (or (instance? CompletableFuture arg)
      (aor-types/AsyncResultPStateQuery? arg)))

(deframaop handle-async-emits [*parent-invoke-id *async-ops *emits]
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
        (case> (aor-types/AgentPStateTransform? *emit))
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

        (case> (aor-types/AgentPStateSelect? *emit))
        ;; TODO: <<<<>>>> need to propagate failures back as the result
        ;;  - need to expose try/catch somehow...
        (identity *emit :> {:keys [*module-name *pstate-name *path *async-op-index]})
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
                 {"type" "pstate-select"
                  "module-name" *module-name
                  "name" *pstate-name
                  "result" *res})))
            *async-ops)
          (next *emits))

        (case> (instance> CompletableFuture *emit))
        ;; TODO: <<<>>>> propagate failures
        (completable-future> *emit :> [*async-op-index *start-millis *finish-millis *v])
        (continue>
          *res
          (h/clj-transform
            (path>
             (nthpath *async-op-index)
             (termval
               (aor-types/->valid-AsyncOpInfo
                 *start-millis
                 *finish-millis
                 {"type" "completable-future"
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
          *emits :> *asyncs)
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
              (select> [(nthpath *async-op-index) :info (keypath "result")] *async-ops :> *res)

              (default> :unify false)
              (throw! (ex-info "Unknown async type" {:class (class *v)})))
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
  (<<if (> (count *emits) 0)
    (h/gen-subsequent-invoke-ids (count *emits) *parent-invoke-id :> *invoke-ids)
    (<<semifn% %mapper
      (:< *agent-node-emit *invoke-id)
      (:> (assoc *agent-node-emit :invoke-id *invoke-id)))
    (mapv %mapper *emits *invoke-ids :> *emits)
   (else>)
    (identity *emits :> *emits))
  (:> *async-ops *emits))

(defn- emits-finished? [emits]
  (not
    (selected-any?
      [ALL :args ALL #(not (aor-types/AgentNodeArg? %))]
      emits)))

(defn- node-type [graph node]
  (select-any [:node-map (keypath *next-node) :node (view h/node->type-kw)]
    graph))

(deframafn handle-node-invoke [*name *graph-task-id *graph-id *node-fn *invoke-id *next-node *args *agg-invoke-id]
  (<<with-substitutions [$$nodes (this-module-pobject-task-global (agent-node-task-global-name *name))
                        *agent-graph (declared-object-task-global (agent-graph-task-global-name *name))]
    (mk-agent-node *agent-graph *graph-task-id :> *agent-node)
    (h/current-time-millis :> *start-time-millis)
    ;; TODO: <<<<>>>> should be done in a try/catch with exceptions causing non-retryable failure
    (apply *node-fn *agent-node *args :> *node-fn-res)
    (agent-node-state *agent-node :> {:keys [*async-ops *emits *result]})
    ;; TODO: <<<<>>>> error if not in agg context and #emits != 1
    (handle-async-emits *invoke-id *async-ops *emits :> *async-ops *emits)
    (local-transform>
      [(keypath *invoke-id)
       (termval {:graph-id *graph-id
                 :graph-task-id *graph-task-id
                 :node *next-node
                 :async-ops *async-ops
                 :start-time-millis *start-time-millis
                 :input *args
                 :emits *emits
                 :result *result
                 :agg-invoke-id *agg-invoke-id
                })]
        $$nodes)
    (:> {:start-time-millis *start-time-millis
         :node-fn-res *node-fn-res
         :emits *emits
         :result *result})))

(deframaop send-emits> [*agent-name *graph-task-id *invoke-id *agg-invoke-id *emits]
  (anchor> <root>)
  (ops/explode *emits :> {:keys [*invoke-id *target-task-id *node-name *args]})
  (mapv :val *args :> *unwrapped-args)
  (|direct *target-task-id)
  (aor-types/->valid-NodeOp *invoke-id *node-name *unwrapped-args *agg-invoke-id :> *op)
  (anchor> <regular-emit>)

  (hook> <root>)
  (filter> (and> (some? *agg-invoke-id) (empty? *emits)))
  (|direct *graph-task-id)
  (aor-types/->valid-AggAckOp *agg-invoke-id *invoke-id :> *op)
  (anchor> <agg-ack-emit>)

  (unify> <regular-emit> <agg-ack-emit>)
  (:> *op))

(defn task-id-key-partitioner
  [num-partitions task-id]
  task-id)

(defn- define-agent! [setup stream-topology name agent-graph]
  (let [graph (resolve-agent-graph agent-graph)
        agent-depot-sym (symbol (str "*_agent-depot-" name))
        agent-streaming-depot-sym (symbol (str "*_agent-streaming-depot-" name))
        agent-graph-sym (symbol (agent-graph-task-global-name name))
        agent-node-pstate-sym (symbol (agent-node-task-global-name name))
        agent-invoke-pstate-sym (symbol (str "$$_agent-invoke-" name))
        agent-streaming-results-pstate-sym (symbol (str "$$_agent-streaming-" name))
        agent-graph-history-pstate-sym (symbol (graph-history-task-global-name name))
        agent-id-gen-pstate-sym (symbol (str "$$_agent-id-gen-" name))
        ]
    (declare-depot* setup agent-depot-sym :random)
    (declare-depot* setup agent-streaming-depot-sym agent-streaming-depot-partitioner)

    (declare-object* setup agent-graph-sym graph)

    ;; TODO: <<<<>>>>
    ;; - and ordered IDs is perfect for GC!
    ;;    - especially since they're sequential, so know exactly how many are in there by looking at min and max
    (declare-pstate*
      stream-topology
      agent-invoke-pstate-sym
      {Long
        (fixed-keys-schema
          {:root-invoke-id Long
           :invoke-args [Object]
           :graph-version Long
           :result AgentResult})})
    (declare-pstate*
      stream-topology
      agent-streaming-results-pstate-sym
      {Long ; agent ID
        (map-schema
          String ; node name
          {String ; async invoke name
            (map-schema
              Long ; invoke-id
              (list-schema Object {:subindex? true})
              {:subindex? true})}
          {:subindex? true})})
    (declare-pstate*
      stream-topology
      agent-node-pstate-sym
      {Long ; invoke-id
        (fixed-keys-schema
          {:graph-id Long
           :graph-task-id Long
           :node String
           :async-ops [AsyncOpInfo]
           :emits [AgentNodeEmit]
           :result AgentResult
           :start-time-millis Long
           :finish-time-millis Long
           :agg-invoke-id Long

           ;; regular node state
           :input [Object]

           ;; agg state
           :agg-inputs (vector-schema AggInput {:subindex? true})
           :agg-start-res Object
           :agg-state Object
           :agg-ack-val Long

           ;; TODO: <<<<>>>>
           ;;   - what other stats does langsmith track?
           })})
    (declare-pstate*
      stream-topology
      agent-graph-history-pstate-sym
      {Long HistoricalAgentGraphInfo}
      {:key-partitioner task-id-key-partitioner})
    (declare-pstate*
      stream-topology
      agent-id-gen-pstate-sym
      Long
      {:initial-value 0})

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
                     :invoke-args *args
                     :graph-version *version})]
          agent-invoke-pstate-sym)
        (aor-types/->valid-NodeOp *invoke-id (get agent-graph-sym :start-node) *args nil :> *op)

        (case> (aor-types/AsyncFutureResult? *data))
        (identity *data :> {:keys [*invoke-id *async-op-index *result *start-time-millis *finish-time-millis *info]})
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
        (local-select>
          [(keypath *invoke-id)
           (subselect (multi-path :graph-id :graph-task-id :emits :node :agg-invoke-id :parent-agg-invoke-id))]
          agent-node-pstate-sym :> [*graph-id *graph-task-id *emits *node *agg-invoke-id *parent-agg-invoke-id])
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
        (send-emits> name *graph-task-id *invoke-id *agg-invoke-id *emits :> *op)

        (default> :unify false)
        (throw! (ex-info "Unrecognized data type" {:class (class *data)})))

      ;; requires *graph-id, *graph-task-id, *op to be in scope
      (loop<- [*op *op]
        (<<if (aor-types/NodeOp? *op)
          (get *op :next-node :> *next-node)
          (select> [:node-map (keypath *next-node) :node]
            agent-graph-sym :> *op-obj)
         (else>)
          (identity *op :> *op-obj))

        (<<subsource *op-obj
          (case> Node :> {:keys [*node-fn]})
          (identity *op :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
          (handle-node-invoke
            name *graph-task-id *graph-id *node-fn *invoke-id *next-node *args *new-agg-invoke-id
            :> {:keys [*emits *result]})

          (case> NodeAggStart :> {:keys [*node-fn *agg-node-name]})
          (identity *op :> {:keys [*invoke-id *next-node *args *agg-invoke-id]})
          (h/random-long :> *new-agg-invoke-id)
          (handle-node-invoke
            name *graph-task-id *graph-id *node-fn *invoke-id *next-node *args *new-agg-invoke-id
            :> {:keys [*start-time-millis *node-fn-res *emits *result]})
          ;; TODO: <<<<>>>> run init fn of the agg
          ;;    - should convert everything to internal accumulator
          ;;    - what about converting things like Agg.sum()
          ;;       - extract from AggImpl type
          ;;    - would be nice to use to-streaming-accumulator internal function...
          (local-transform>
            [(keypath *new-agg-invoke-id)
             (termval {:graph-id *graph-id
                       :graph-task-id *graph-task-id
                       :node *agg-node-name
                       :start-time-millis *start-time-millis
                       :agg-invoke-id *agg-invoke-id
                       :agg-inputs []
                       :agg-start-res *node-fn-res
                       :agg-ack-val *invoke-id
                      })]
              agent-node-pstate-sym)

          (case> NodeAgg :> {:keys [*agg-node]})
          (assert! (some? *agg-invoke-id))
          ;; TODO: <<<<>>>>
          ;;  - this processes emits incrementally as it goes
          ;;  - what about 'finished' agg method for early finish
          ;;    - need a sub-interface for this
          ;;  - seems like this should also implicitly have an AggAckOp for this invoke ID, as its terminal on this node

          (case> AggAckOp :> {:keys [*agg-invoke-id *ack-val]})
          ;: TODO: <<<<>>>>
          ;;  - apply ack val
          ;;  - if zero:
          ;;     - run completion function
          ;;     - handle-async-emits and/or completed emits
          ;;     - seems like that should be in continuation
          (identity nil :> *result)
          (identity nil :> *invoke-id)
          (identity nil :> *emits)
          (identity nil :> *agg-invoke-id)
          )
      ;; AgentNode implementation makes it impossible for there to be both emits and result
      (<<if (some? *result)
        (<<atomic
          (|direct *graph-task-id)
          (local-transform>
            [(keypath *graph-id)
             :result
             (termval *result)]
            agent-invoke-pstate-sym)))
      (<<if (emits-finished? *emits)
        (local-transform>
          [(keypath *new-agg-invoke-id)
           :finish-time-millis
           (termval (h/current-time-millis)]
            agent-node-pstate-sym))
        (send-emits> *graph-task-id *invoke-id *agg-invoke-id *emits :> *op)
        (continue> *op)))



      (source> agent-streaming-depot-sym
        :> {:keys [*agent-id
                   *node
                   *invoke-id
                   *emit-index
                   *arg-index
                   *streaming-index
                   *value]})
      (<<ramafn %correct-index? [*l]
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
    ))

(defn define-agents! [setup stream-topology agent-graphs]
  (doseq [[name agent-graph] agent-graphs]
    (define-agent! setup stream-topology name agent-graph)))
