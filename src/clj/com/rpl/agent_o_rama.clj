(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.impl :as i])
  (:import [com.rpl.agentorama AgentsTopology AgentGraph]
           [com.rpl.rama PState$Schema]))

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
        (i/define-agents!
          stream-topology
          (mapv i/agent-graph-state @agents-vol))
        ))))

(defn underlying-stream-topology [^AgentTopology at]
  (.getStreamTopology at))

(defn define-agents! [^AgentTopology at]
  (.define at))

;; TODO: all the declare methods

(defn node* [^AgentGraph agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node! agent-graph
    (i/->Node name (i/normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro node [agent-graph name output-nodes-spec & fn-body]
  `(node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-start-node* [^AgentGraph agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node!
    agent-graph
    (i/->AggStartNode name (i/normalize-output-nodes outputNodesSpec) node-fn)))

(defmacro agg-start-node [agent-graph name output-nodes-spec & fn-body]
  `(agg-start-node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defmacro agg-node [agent-graph name output-nodes-spec & body]
  `(i/agg-node* ~agent-graph ~name ~output-nodes-spec (i/agg-node-object ~@body)))

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
