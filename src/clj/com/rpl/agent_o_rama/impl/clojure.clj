(ns com.rpl.agent-o-rama.impl.clojure
  (:require
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agentorama
    AgentNode
    AgentsTopology]))

(defn new-agent
  [^AgentsTopology agents-topology name]
  (.newAgent agents-topology name))

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
