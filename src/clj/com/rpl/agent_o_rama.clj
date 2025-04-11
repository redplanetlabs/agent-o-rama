(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.helpers :as h]
            [loom.graph :as graph])
  (:import [com.rpl.agentorama AgentsTopology AgentGraph]
           [com.rpl.rama PState$Schema]))

(defrecord Node [name output-nodes node-fn])
(defrecord AggStartNode [name output-nodes node-fn])
(defrecord AggNode [name output-nodes agg-node])

(defn- normalize-output-nodes [spec]
  (cond (string? spec) [spec]
        (collection? spec) (set spec)
        :else (throw (ex-info "Invalid output nodes spec"
                              {:spec spec :class (class spec)}))
        ))

(defprotocol AgentGraphInternal
  (internal-add-node! [this name node])
  (agent-graph-state [this]))

(defmacro reify-AgentGraph [& body]
  `(reify AgentGraph
    ~@(for [i (range 1 8)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
              this#
              ~name-sym
              (->Node
                (normalize-output-nodes outputNodesSpec#)
                (h/convert-void-jfn jfn#)))
            )))
    ~@(for [i (range 1 8)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (internal-add-node!
              this#
              ~name-sym
              (->AggStartNode
                (normalize-output-nodes outputNodesSpec#)
                (j/convert-void-jfn jfn#)))
            )))
    ))

(defn- mk-agent-graph []
  (let [nodes-vol (volatile! {})]
    (reify-AgentGraph
      (aggNode [this name outputNodesSpec aggNode]
        (internal-add-node!
          this#
          name
          (->AggNode
            (normalize-output-nodes outputNodesSpec)
            addNode)))
      AgentGraphInternal
      (internal-add-node! [this name node]
        (when (contains? @nodes-vol name)
          (throw (ex-info "Node already exists" {:name name})))
        (vswap! nodes-vol assoc name node)
        this)
      (agent-graph-state [this]
        {:nodes @nodes-vol})
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
  (internal-add-node! agent-graph
    (->Node name (normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro node [agent-graph name output-nodes-spec & fn-body]
  `(node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-start-node* [^AgentGraph agent-graph name output-nodes-spec node-fn]
  (internal-add-node! agent-graph
    (->AggStartNode name (normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro agg-start-node [agent-graph name output-nodes-spec & fn-body]
  `(agg-start-node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-node* [^AgentGraph agent-graph name output-nodes-spec agg-node-impl]
  (internal-add-node! agent-graph
    (->AggNode name (normalize-output-nodes outputNodesSpec) agg-node-impl)))

(defmacro agg-node-object [& body]
  ;; TODO: <<<<>>>>
  ;;  - sequence of forms beginning with "on', "on-any", or "on-complete"
  ;;  - last one must be on-complete
  ;;  - AggNode needs static versions of all its methods that then switch into Impl
  ;;    - this code should invoke internal versions of those methods to build up the internal state
  ;;    - should either be many on and one on-complete, or one on-any and one on-complete
  )

(defmacro agg-node [agent-graph name output-nodes-spec & body]
  (agg-node* ~agent-graph ~name ~output-nodes-spec (agg-node-object ~@body)))


;; TODO: define agg-node macro that allows for many "on" declarations, one "on-any", and one "on-complete"
; (agg-node "agg" "report"
;   (on "result" [node agg-state llm-result]
;     (conj agg-state llm-result))
;   (on-complete [node agg-state]
;     (agent-result! node "report" (invoke-model "*model" ["prompt" agg-state]))))

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
