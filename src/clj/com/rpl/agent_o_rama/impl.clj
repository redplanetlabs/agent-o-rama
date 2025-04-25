(ns com.rpl.agent-o-rama.impl
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require [clojure.set :as set]
            [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.types :as aor-types]
            [com.rpl.rama.ops :as ops]
            [loom.attr :as lattr]
            [loom.graph :as graph]
            ;; TODO: <<<<>>>> expose current-random-source in public API and use that
            [rpl.rama.distributed.core :as d])
  (:import [com.rpl.agentorama AgentGraph AgentNode AggNode AggNode$Impl AsyncResult]
           [com.rpl.agent_o_rama.types
             AgentNodeEmit
             AgentResult
             AsyncOpInfo
             Node
             NodeAgg
             NodeAggStart]
           [com.rpl.rama.helpers TopologyUtils]
           [java.util Function Map UUID]))

(defprotocol AgentGraphInternal
  (internal-add-node! [this name output-nodes-spec node])
  (agent-graph-state [this]))

(defprotocol AggNodeInternal
  (internal-add-handler! [this name afn])
  (internal-add-any-handler! [this afn])
  (internal-add-complete-handler! [this afn])
  (agg-node-state [this]))

(defmacro reify-AggNode [& body]
  `(reify ~'AggNode$Impl
    ~@(for [i (range 0 (- h/MAX-ARITY 2))]
        (let [name-sym (h/type-hinted String 'name#)
              jfn-sym (h/type-hinted (h/rama-function-class (+ i 2)) 'jfn#)
              on-sym (h/type-hinted AggNode$Impl 'on)]
          `(~on-sym [this# ~name-sym ~jfn-sym]
            (internal-add-handler!
              this#
              ~name-sym
              (h/convert-jfn ~jfn-sym))
            )))
    ~@body
    ))

(defn mk-agg-node []
  (let [on-vol (volatile! {})
        on-any-vol (volatile! nil)
        on-complete-vol (volatile! nil)]
    (reify-AggNode
      (onAny [this jfn]
        (internal-add-any-handler! this (h/convert-jfn jfn)))
      (onComplete [this jfn]
        (internal-add-complete-handler! this (h/convert-void-jfn jfn)))
      AggNodeInternal
      (internal-add-handler! [this name afn]
        (when (some? @on-any-vol)
          (throw (ex-info "Agg node may not have both 'on' and 'onAny' handlers" {})))
        (when (contains? @on-vol name)
          (throw (ex-info "Agg node already has handler for given name" {:name name})))
        (vswap! on-vol assoc name afn)
        this)
      (internal-add-any-handler! [this afn]
        (when (some? @on-any-vol)
          (throw (ex-info "Agg node can only have one onAny handler" {})))
        (when-not (empty? @on-vol)
          (throw (ex-info "Agg node may not have both 'on' and 'onAny' handlers" {})))
        (vreset! on-any-vol afn)
        this )
      (internal-add-complete-handler! [this afn]
        (when (some? @on-complete-vol)
          (throw (ex-info "Agg node can only have one onComplete handler" {})))
        (vreset! on-complete-vol afn)
        this )
      (agg-node-state [this]
        {:on-handlers @on-vol
         :on-any-handler @on-any-vol
         :on-complete-handler @on-complete-vol
         }))
      ))

(defmacro agg-node-object [& body]
  (let [ret-sym (gensym "ret")]
    `(let [~ret-sym (mk-agg-node)]
      ~@(for [form body]
          (condp = (first form)
            'on
            (let [[_ name & body] form]
              `(internal-add-handler! ~ret-sym ~name (fn ~@body)))

            'on-any
            (let [[_ & body] form]
              `(internal-add-any-handler! ~ret-sym (fn ~@body)))

            'on-complete
            (let [[_ & body] form]
              `(internal-add-complete-handler! ~ret-sym (fn ~@body)))

            (throw (ex-info "Invalid agg node method" {:method (first form)}))
            ))
       ~ret-sym
       )))

(defn agg-node* [^AgentGraph agent-graph name output-nodes-spec agg-node-impl]
  (internal-add-node!
    agent-graph
    name
    output-nodes-spec
    (aot-types/->NodeAgg agg-node-impl)))

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
              (aot-types/->NodeAggStart (h/convert-void-jfn ~jfn-sym)))
            )))
    ~@body
    ))

(defn- normalize-output-nodes [spec]
  (cond (string? spec) [spec]
        (coll? spec) (set spec)
        (nil? spec) #{}
        :else (throw (ex-info "Invalid output nodes spec"
                              {:spec spec :class (class spec)}))))

(defn mk-agent-graph []
  (let [nodes-vol (volatile! {})
        start-node-vol (volatile! nil)]
    (reify-AgentGraph
      (aggNode [this name outputNodesSpec aggNode]
        (internal-add-node!
          this
          name
          outputNodesSpec
          (aot-types/->NodeAgg aggNode)))
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

;; TODO: <<<<>>>>
;;  - don't allow looping back to start agg node from within agg context (but OK from agg node)
;;    - need test for looping back from agg node
;;  - start agg node needs to know the agg node
(defn- annotate-aggs [graph node traversed agg-stack]
  (let [curr-agg (peek agg-stack)
        node-obj (lattr/attr graph node :node-obj)
        next-traversed (conj traversed node)]
    (cond
      (contains? traversed node-obj)
      ;; first case allows agg subgraph to loop back to start node of aggregation
      (if (and (not= node curr-agg)
               (not= (lattr/attr graph node :agg) curr-agg))
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
        (let [new-agg-stack (pop agg-stack)]
          (reduce
            (fn [graph output-node]
              (annotate-aggs
                graph
                output-node
                next-traversed
                new-agg-stack
                ))
            (lattr/add-attr graph node :agg curr-agg)
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

(defdepotpartitioner agent-depot-partitioner
  [data num-partitions]
  ;; TODO: <<<<>>>>
  )

(defdepotpartitioner agent-streaming-depot-partitioner
  [{:keys [agent-task-id]} num-partitions]
  agent-task-id)


(defn random-long []
  (.nextLong ^java.util.Random (d/current-random-source)))

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

(defn mk-agent-node []
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
              (random-long)
              task-id
              node
              args
              )))
      (emitParallel [this node args]
        (when (some? @result-vol)
          (throw (ex-info "Cannot emit with result already specified" {:current-result @result-vol})))
        ;; TODO: <<<<>>>>
        ;;  - need the task global with shuffled task IDs
        ;;  - ideally have the thread->tasks mapping
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

(deframaop handle-async-emits [*async-ops *emits]
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
  (:> *async-ops *emits))

(defn- define-agent! [setup stream-topology name agent-graph]
  (let [graph (resolve-agent-graph agent-graph)
        agent-depot-sym (symbol (str "*_agent-depot-" name))
        agent-streaming-depot-sym (symbol (str "*_agent-streaming-depot-" name))
        agent-graph-sym (symbol (agent-graph-task-global-name name))
        agent-node-pstate-sym (symbol (agent-node-task-global-name name))
        agent-pending-nodes-pstate-sym (symbol (str "$$_agent-pending-nodes-" name))
        agent-invoke-pstate-sym (symbol (str "$$_agent-invoke-" name))
        agent-streaming-results-pstate-sym (symbol (str "$$_agent-streaming-" name))
        agent-graph-history-pstate-sym (symbol (graph-history-task-global-name name))
        agent-id-gen-pstate-sym (symbol (str "$$_agent-id-gen-" name))
        ]
    (declare-depot* setup agent-depot-sym agent-depot-partitioner)
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
           ;; TODO: <<<<>>>> it should be able to be a redirect/retry
           ;; - simpler would be if retry just continues it..., and then don't need redirect
           ;; - maybe fork with same invoke ID has that behavior
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

           ;; regular node state
           :input [Object]
           :agg-invoke-id Long

           ;; agg state
           :agg-inputs (map-schema Long [Object] {:subindex? true}) ; invoke ID -> args
           :parent-agg-invoke-id Long
           :agg-state Object
           :agg-ack-val Long

           ;; TODO: <<<<>>>>
           ;;   - what other stats does langsmith track?
           })})
    (declare-pstate*
      stream-topology
      agent-pending-nodes-pstate-sym
      {Long Object})
    ;; TODO: <<<<>>>> need custom key partitioner on this so can fetch from foreign client from task 0
    (declare-pstate*
      stream-topology
      agent-graph-history-pstate-sym
      {Long HistoricalAgentGraphInfo})
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
        (random-long :> *invoke-id)
        (fetch-graph-version name :> *version)
        (local-transform>
          [(keypath *graph-id)
           (termval {:root-invoke-id *invoke-id
                     :invoke-args *args
                     :graph-version *version})]
          agent-invoke-pstate-sym)
        (get agent-graph-sym :start-node :> *next-node)
        (identity nil :> *node-invoke-source)
        (identity [] :> *agg-context)
        (anchor> <first-node-invoke>)

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
        ;; TODO: <<<<>>>
        ;;   - look up the node invoke
        ;;   - if success:
        ;;     - update node PState
        ;;     - if all AsyncFuture filled in, process it as an emit, unified with above code
        ;;    - and should be part of a loop
        ;;    - this part unifies with the output portion of a node... which is inside the loop below
        ;;      - maybe this can be factored into a helper function, and then this unifies with the loop?
        ;;  - if failure, bump retries and try it again
        <async-node-finished>
        )

      ;; requires *graph-id, *graph-task-id, *invoke-id, *next-node, *args, *agg-invoke-id to be in scope
      (unify> <first-node-invoke> <async-node-finished>)
      (loop<- [*invoke-id *invoke-id
               *next-node *next-node
               *args *args
               *agg-invoke-id *agg-invoke-id]
        (select> [:node-map (keypath *next-node) :node (view h/node->type-kw)]
          agent-graph-sym :> *expected-node-type)
        (<<if (contains? #{aor-types/AGG-START-NODE-KW aor-types/AGG-NODE-KW} *expected-node-type)
          (|direct *graph-task-id))
        (select> [:node-map (keypath *next-node) :node]
          agent-graph-sym :> *node-obj)
        (<<subsource *node-obj
          (case> Node :> {:keys [*node-fn]})
          (mk-agent-node :> *agent-node)
          (h/current-time-millis :> *start-time-millis)
          (apply *node-fn *agent-node *args)
          (agent-node-state *agent-node :> {:keys [*async-ops *emits *result]})
          (handle-async-emits *async-ops *emits :> *async-ops *emits)
          ;; TODO: <<<<>>>>
          (...emits-finished? *emits :> *emits-finished?)
          (<<if *emits-finished?
            (h/current-time-millis :> *finish-time-millis)
           (else>)
            (identity nil :> *finish-time-millis))
          (local-transform>
            [(keypath *invoke-id)
             (termval {:graph-id *graph-id
                       :graph-task-id *graph-task-id
                       :node *next-node
                       :async-ops *async-ops
                       :start-time-millis *start-time-millis
                       :finish-time-millis *finish-time-millis
                       :input *args
                       :emits *emits
                       :result *result
                       :agg-invoke-id *agg-invoke-id
                      })]
              agent-node-pstate-sym)
          (<<if (some? *result)
            (<<atomic
              (|direct *graph-task-id)
              (local-transform>
                [(keypath *graph-id)
                 :result
                 (termval *result)
                 ]
                agent-invoke-pstate-sym)
              ))
          (<<if *emits-finished?
            (ops/explode *emits :> {:keys [*invoke-id *target-task-id *node-name *args]})
            (mapv :val *args :> *unwrapped-args)
            (|direct *target-task-id)
            (continue> *invoke-id *node-name *unwrapped-args *agg-invoke-id))
          ;; TODO: <<<<>>>> emit code should be shared for all branches?
          ;; TODO: <<<<>>>> need to propagate acking



          (case> NodeAggStart :> {:keys [*node-fn]})
          ;; TODO: <<<<<>>>> guaranteed to be new agg context
          ;;  - needs to capture the current agg invoke-id into parent-agg-invoke-id

          (case> NodeAgg :> {:keys [*agg-node]})
          (assert! (some? *agg-invoke-id))

          )



        ;; TODO: <<<<>>>>
        ;;  - normal node and start agg node check if it's initialized or not
        ;;  - agg node checks if its invoke ID is in :agg-inputs an then skips the whole thing
        ;;      - but what about its async emits?
        ;;      - if task resets, need to retry ALL inputs
        ;;  - either way, execute ack-invoke
        ;;  - execution then differs per type:
        ;;    - normal node and start agg node initialize similar but not the same
        ;;      - start agg node initializes the agg fields
        ;;    - normal node collects emits and writes them in
        ;;    - start agg node returns the initial agg state and then writes it in (along with emits)
        ;;    - agg node executes and writes new agg state plus the new emit plus its invoke ID + args






        ;; TODO: <<<<>>>>
        ;;  - verify the expected-node-type – need this in the closure, or just fetch it here?
        ;;  - fetch the node function
        ;;  - if agg start node, partition back to graph-task-id
        ;;  - invoke the node with the args in a try/catch
        ;;    - on failure, increment retry count, do |direct partition, and try again
        ;;    - AgentNode reify captures all emits
        ;;  - agg start node produces init for the agg state
        ;;  - if any AsyncFuture results, then stop execution on this branch
        ;;  - otherwise, if this has an agg context need to go to the agg task-id + invoke-id
        ;;     - TODO: how to track that info
        ;;     - TODO: what if nothing reaches the agg? how does it invoke it?
        ;;        - what connects the agg start node to the agg node... they all just point to the agg start node



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
    ;;  - create depots / stream topology impls
    ;;    - depot per agent for recieving inputs
    ;;      - client append is UUID + args
    ;;      - client should query for number of args
    ;;    - depot for receiving continuations
    ;;    - PState for storing inputs/outputs of nodes and agg info
    ;;    - tick depot for checking active nodes and retrying/continuing if necessary
    ;;    - task global for the actual graph structure so can look up node -> node-obj and agg label
    ;;    - task global for out-of-band events
    ;;    - tick depot per agent
    ;;      - check active nodes for retries
    ;;    - source subscription

    ;; TODO: <<<<>>>
    ;;   - if node emits multiple times and is not in aggregation context, isn't that a problem?
    ;;      - same with not emitting at all
    ;;   - these should be runtime errors?
    ;;      - easy to track if in agg context or not


    ;; TODO: <<<<>>>>
    ;; FLOW:
    ;;  - input depot receives args for invoke
    ;;    - need to be able to partition by user ID so can colocate with user data?
    ;;  - create state tracking this agent invoke
    ;;    - assign an ID (task ID + UUID)
    ;;    - need to know agg context and ID for it
    ;;      - agg context is the AggStartNode name + the invoke ID + task ID where it was initiated
    ;;  - create ID for the "emit" (which is the invoke in this case)
    ;;  - write emit ID -> node name + args into state
    ;;      - a state could be entered multiple times at same time, so need an identifier for the "active invoke"
    ;;  - invoke node on that task
    ;;    - on exception, update state with failure count
    ;;    - on success, update state that the node is "done" and write targets nodes + invokes to them
    ;;      - also need to write implicit output to agg node
    ;;    - if any outputs are AsyncFuture:
    ;;      - check type
    ;;      - if PState query or write, then do it immediately
    ;;          - error should be immediate failure of agent
    ;;          - if "slow external", then update state with what it's waiting for in the in-memory task global along with its ID
    ;;      - asyncfuture execution either deliver result, or it delivers failure
    ;;        - failure here will be a depot append that increments failure count and tries again
    ;;          - the failure append include the input to retry
    ;;        - race condition with tick that checks if its empty or not...
    ;;          - could fix this race by keeping in-mem info on whether it's active or not
    ;;        - success updates state with value and that it's no longer pending
    ;;          - when all of them are successful, can send to the next task
    ;;          - sending to next task:
    ;;            - update state on that task with the invokes
    ;;            - sends message back to clear the state so it doesn't retry
    ;;          - seems like input depot and continuation depot can just be the same...
    ;;            - depot partitioniner for continuations can contain task ID
    ;;    - TODO: what about aggs?
    ;;      - if in agg context, need to send invoke IDs of emits to the origin
    ;;        - and need confirmation it was sent and came back
    ;;          - this needs to be paired with ack of its own invoke ID
    ;;
    ))

(defn define-agents! [setup stream-topology agent-graphs]
  (doseq [[name agent-graph] agent-graphs]
    (define-agent! setup stream-topology name agent-graph)))
