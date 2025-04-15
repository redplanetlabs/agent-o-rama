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
  (:import [com.rpl.agentorama AgentGraph AggNode AggNode$Impl]
           [com.rpl.agent_o_rama.types AgentResult]
           [java.util Map]))

(defrecord Node [node-fn])
(defrecord NodeAggStart [node-fn])
(defrecord NodeAgg [agg-node])

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
    (->NodeAgg agg-node-impl)))

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
              (->Node (h/convert-void-jfn ~jfn-sym)))
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
              (->NodeAggStart (h/convert-void-jfn ~jfn-sym)))
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
          (->NodeAgg aggNode)))
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
    (with-meta agg-graph {:start-node start-node})))

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

(defn random-long []
  (.nextLong ^java.util.Random (d/current-random-source)))

;; TODO: <<<<<>>>> define records for invokes
;;    - input should be plain map so it can be used from REST API


(defn- define-agent! [setup stream-topology name agent-graph]
  (let [graph (resolve-agent-graph agent-graph)
        agent-depot-sym (symbol (str "*_agent-depot-" name))
        agent-node-pstate-sym (symbol (str "$$_agent-node-" name))
        agent-pending-nodes-pstate-sym (symbol (str "$$_agent-pending-nodes-" name))
        agent-invoke-pstate-sym (symbol (str "$$_agent-invoke-" name))
        agent-graph-history-pstate-sym (symbol (str "$$_agent-graph-history-" name))
        ]
    (declare-depot* setup agent-depot-sym agent-depot-partitioner)

    ;; TODO: <<<<>>>> should generalize this to also include the root invoke ID so can trace from there
    ;; - and ordered IDs is perfect for GC!
    ;;    - especially since they're sequential, so know exactly how many are in there by looking at min and max
    ;;    - can materialize invoke args here, which is helpful for searching instead of having to query invokes PState repeatedly
    (declare-pstate*
      stream-topology
      agent-invoke-pstate-sym
      {Long
        (fixed-keys-schema
          {:root-invoke-id Long
           :invoke-args [Object]
           ;; TODO: <<<<<>>>> translate agent graph in task global into PState (without functions)
           ;;   - capture args from the functions if not already in there
           ;;   - this isn't central though...
           ;;     - how to just see a listing of all of them?
           ;;       - could be duplicated, and if not there go to task 0 to generate/fetch it, then bring it here and write it
           ;;   - how to actually distinguish graph versions?
           ;;     - put into the task global a random UUID?
           :graph-version Long
           :result AgentResult})})
    (declare-pstate*
      stream-topology
      agent-node-pstate-sym
      {Long
        (fixed-keys-schema
          {:graph-id Long
           :graph-task-id Long
           :expected-node-type clojure.lang.Keyword ; :node, :agg-start-node, :agg-node
           :node String
           :args [Object]
           :retry-count Long
           :emits [AgentNodeEmit]
           :created-time-millis Long
           :finished-time-millis Long
           ;; TODO: <<<<>>>> also need stats for token count
           ;;   - could be multiple LLM calls, so need token count per
           ;;   - what other stats does langsmith track?
           })})
    (declare-pstate*
      stream-topology
      agent-pending-nodes-pstate-sym
      {Long Object})
    (declare-pstate*
      stream-topology
      agent-graph-history-pstate-sym
      {Long AgentGraphInfo})

    (<<sources stream-topology
      (source> agent-depot-sym :> *data)
      (<<cond
        (case> (or> (aor-types/AgentInvoke? *data) (instance? Map *data)))
        (get-invoke-args *data :> *args)
        (ops/current-task-id :> *graph-task-id)
        ;; TODO: use ID PState and longs (no rollover needed)
        (...gen-id :> *id)

        (random-long :> *invoke-id)

        ;; TODO:
        ;;  - initialize agent-invoke into agent-invoke-pstate-sym
        ;;  - initialize node invoke
        ;;    - should be same code as that which handles emits

        (case> (aor-types/AsyncFutureResult? *data))
        ;; TODO: <<<<>>>
        ;;   - look up the node invoke
        ;;   - if success:
        ;;     - update node PState
        ;;     - if all AsyncFuture filled in, process it as an emit, unified with above code
        ;;    - and should be part of a loop
        ;;  - if failure, bump retries and try it again

        (case> (aor-types/ContinueExecution? *data))
        ;; TODO: <<<<>>>>
        ;;  - this is appended from tick depot
        ;;  - should verify it still needs execution
        )

        ;; TODO: <<<<<>>>>
        ;; - what about module update mid agent execution?
        ;;    - it could be changing graph structure, changing functions
        ;;       - those executions should fail? or only fail if graph structure fundamentally changes?
        ;;        - the latter is better
        ;;    - this means nodes need to keep the UUID of execution
        ;;      - but history might not be there anymore
        ;;      - is it enough to store UUID + the node type?

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
