(ns com.rpl.agent-o-rama.impl
  (:require [clojure.set :as set]
            [com.rpl.agent-o-rama.helpers :as h]
            [loom.graph :as graph])
  (:import [com.rpl.agentorama AgentGraph AggNode AggNode$Impl]))

(defrecord Node [output-nodes node-fn])
(defrecord NodeAggStart [output-nodes node-fn])
(defrecord NodeAgg [output-nodes agg-node])

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
              jfn-sym (h/type-hinted (h/rama-void-function-class (inc i)) 'jfn#)
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
              jfn-sym (h/type-hinted (h/rama-void-function-class (inc i)) 'jfn#)
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
      (internal-add-node! [this name output-nodes-spec node]
        (when (or (nil? name) (= "" name))
          (throw (ex-info "Node name cannot be nil or empty string" {:name name})))
        (when (contains? @nodes-vol name)
          (throw (ex-info "Node already exists" {:name name})))
        (when (nil? @start-node-vol)
          (vreset! start-node-vol name))
        (vswap! nodes-vol
                assoc
                name
                {:node node
                 :output-nodes (normalize-output-nodes output-nodes-spec)})
        this)
      (agent-graph-state [this]
        {:nodes @nodes-vol
         :start-node @start-node-vol})
      )))

(defn nodes->graph [nodes]

  )

(defn- define-agent! [stream-topology {:keys [nodes start-node]}]
  (let [graph (nodes->graph nodes)]


  ;; TODO: <<<<>>>> implement
  ;;  - make loom graph for each agent
  ;;  - verify valid agg subgraphs (no edges outside of graph in internal nodes)
  ;;  - create depots / stream topology impls
  ;;    - depot per agent
  ;;    - task global for out-of-band events
  ;;    - tick dpeot per agent
  ;;      - check active nodes for retries
  ;;    - source subscription
    ))

(defn define-agents! [stream-topology agent-infos]
  (doseq [agent-info agent-infos]
    (define-agent! stream-topology agent-info)))
