(ns com.rpl.agent-o-rama.impl.clojure
  (:require
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agentorama
    AgentClient
    AgentInvoke
    AgentNode
    AgentTopology]
   [java.util.concurrent
    CompletableFuture]))

(defn new-agent
  [^AgentTopology agent-topology name]
  (.newAgent agent-topology name))

(defn node
  [agent-graph name output-nodes-spec node-fn]
  (graph/internal-add-node!
   agent-graph
   name
   output-nodes-spec
   (aor-types/->Node node-fn)))

(defn agg-start-node
  [agent-graph name output-nodes-spec node-fn]
  (graph/internal-add-node!
   agent-graph
   name
   output-nodes-spec
   (aor-types/->NodeAggStart node-fn nil)))

(defn agg-node
  [agent-graph name output-nodes-spec agg node-fn]
  (graph/internal-add-agg-node!
   agent-graph
   name
   output-nodes-spec
   agg
   node-fn))

(defn emit!
  [^AgentNode agent-node node & args]
  (.emit agent-node node (into-array Object args)))

(defn result!
  [^AgentNode agent-node val]
  (.result agent-node val))

(defn agent-initiate
  ^AgentInvoke [^AgentClient agent-client & args]
  (.initiate agent-client (into-array Object args)))

(defn agent-initiate-async
  ^CompletableFuture [^AgentClient agent-client & args]
  (.initiateAsync agent-client (into-array Object args)))

(defn agent-result
  [^AgentClient agent-client agent-invoke]
  (.result agent-client agent-invoke))

(defn agent-result-async
  ^CompletableFuture [^AgentClient agent-client agent-invoke]
  (.resultAsync agent-client agent-invoke))
