(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.impl :as i]
            [loom.graph :as graph])
  (:import [com.rpl.agentorama AgentsTopology AgentGraph]
           [com.rpl.rama PState$Schema]))

(defn- normalize-output-nodes [spec]
  (cond (string? spec) [spec]
        (collection? spec) (set spec)
        :else (throw (ex-info "Invalid output nodes spec"
                              {:spec spec :class (class spec)}))))

(defmacro reify-AgentGraph [& body]
  `(reify ~'AgentGraph
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (i/internal-add-node!
              this#
              ~name-sym
              (i/->Node
                (normalize-output-nodes outputNodesSpec#)
                (h/convert-void-jfn jfn#)))
            )))
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (i/internal-add-node!
              this#
              ~name-sym
              (i/->AggStartNode
                (normalize-output-nodes outputNodesSpec#)
                (j/convert-void-jfn jfn#)))
            )))
    ~@body
    ))

(defn- mk-agent-graph []
  (let [nodes-vol (volatile! {})
        start-node-vol (volatile! nil)]
    (reify-AgentGraph
      (aggNode [this name outputNodesSpec aggNode]
        (i/internal-add-node!
          this#
          name
          (i/->AggNode
            (normalize-output-nodes outputNodesSpec)
            addNode)))
      i/AgentGraphInternal
      (i/internal-add-node! [this name node]
        (when (or (nil? name) (= "" name))
          (throw (ex-info "Node name cannot be nil or empty string" {:name name})))
        (when (contains? @nodes-vol name)
          (throw (ex-info "Node already exists" {:name name})))
        (when (nil? @start-node-vol)
          (vreset! start-node-vol name))
        (vswap! nodes-vol assoc name node)
        this)
      (agent-graph-state [this]
        {:nodes @nodes-vol
         :start-node @start-node-vol})
      )))

(defn agents-topology [name setup topologies]
  (let [stream-topology (stream-topology topologies (str "__agents-topology-" name))
        defined?-vol (volatile! false)
        agents-vol (volatile! {})]
    (reify AgentsTopology
      (newAgent [this name]
        (when (contains? @agents-vol name)
          (throw (ex-info "Agent already exists" {:name name})))
        (let [ret (mk-agent-graph)]
          (vswap! agents-vol assoc name ret)
          ret ))
      (getStreamTopology [this] stream-topology)

      ;; TODO: need methods for getting mirror agents, and will also need methods for invoking mirror agents
      ;;    - should invoking a mirror agent be a node, or should it just be an invoke?
      ;;      - feels like an invoke

      (declareKeyValueStore [this name key-class val-class]
        (declare-pstate* stream-topology (symbol name) {key-class val-class}))
      (declareDocumentStore [this name key-class key-val-classes]
        (when-not (-> key-val-classes count even?)
          (throw (ex-info "Document store must be given even number of key/val classes"
                          {:count (count key-val-classes)})))
        (declare-pstate*
          stream-topology
          (symbol name)
          {key-class (fixed-keys-schema (into {} (partition 2 key-val-classes)))}))
      (declarePState [this name ^Class schema]
        (declare-pstate* stream-topology (symbol name) schema))
      (declarePState [this name ^PState$Schema schema]
        (.pstate stream-topology name schema))
      (declareAgentObject [this name o]
        (declare-object* setup (symbol name) o))
      (define [this]
        (when @defined?-vol
          (throw (ex-info "Agents topology already defined" {})))
        (vreset defined?-vol true)
        ;; TODO: <<<>>>> implement
        ;;  - make loom graph for each agent
        ;;  - verify valid agg subgraphs (no edges outside of graph in internal nodes)
        ;;  - create depots / stream topology impls
        ;;    - depot per agent
        ;;    - task global for out-of-band events
        ;;    - tick dpeot per agent
        ;;      - check active nodes for retries
        ;;    - source subscription
        ))))

(defn underlying-stream-topology [^AgentTopology at]
  (.getStreamTopology at))

(defn define-agents! [^AgentTopology at]
  (.define at))

;; TODO: all the declare methods

(defn node* [^AgentGraph agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node! agent-graph
    (i/->Node name (normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro node [agent-graph name output-nodes-spec & fn-body]
  `(node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-start-node* [^AgentGraph agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node! agent-graph
    (i/->AggStartNode name (normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro agg-start-node [agent-graph name output-nodes-spec & fn-body]
  `(agg-start-node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn- agg-node* [^AgentGraph agent-graph name output-nodes-spec agg-node-impl]
  (i/internal-add-node! agent-graph
    (i/->AggNode name (normalize-output-nodes outputNodesSpec) agg-node-impl)))

(defmacro agg-node [agent-graph name output-nodes-spec & body]
  `(agg-node* ~agent-graph ~name ~output-nodes-spec (i/agg-node-object ~@body)))

(defn- parse-map-options
  [[arg1 & rest-args :as args]]
  (if (map? arg1) [arg1 rest-args] [{} args]))

(defmacro defagentmodule [sym & args]
  (let [[options [[agent-topology-sym] & body]] (parse-map-options args)]
    `(defmodule ~sym ~options
       [setup# topologies#]
       (let [~agent-topology-sym (agents-topology "core" setup# topologies#)]
         ~@body
         (define-agents! ~agent-topology-sym)
         ))))
