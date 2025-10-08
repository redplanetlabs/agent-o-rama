(ns com.rpl.agent-o-rama.impl.clojure
  (:require
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agentorama
    AddDatasetExampleOptions
    AgentClient
    AgentInvoke
    AgentManager
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

(defn agent-initiate-with-context-async
  ^CompletableFuture [agent-client context & args]
  (aor-types/initiate-with-context-async-internal agent-client context (into [] args)))

(defn agent-initiate-with-context
  ^AgentInvoke [agent-client context & args]
  (.get ^CompletableFuture (apply agent-initiate-with-context-async agent-client context args)))

(defn agent-result
  [^AgentClient agent-client agent-invoke]
  (.result agent-client agent-invoke))

(defn agent-result-async
  ^CompletableFuture [^AgentClient agent-client agent-invoke]
  (.resultAsync agent-client agent-invoke))

(defn add-dataset-example-async!
  (^CompletableFuture [manager dataset-id input]
   (add-dataset-example-async! manager dataset-id input nil))
  (^CompletableFuture [^AgentManager manager dataset-id input options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec
                         :reference-output h/any-spec
                         :tags     h/any-spec})
   (let [joptions (AddDatasetExampleOptions.)]
     (set! (.snapshotName joptions) (:snapshot options))
     (set! (.referenceOutput joptions) (:reference-output options))
     (set! (.tags joptions) (:tags options))
     (.addDatasetExampleAsync manager
                              dataset-id
                              input
                              joptions))))

(defn add-dataset-example!
  ([manager dataset-id input]
   (.get (add-dataset-example-async! manager dataset-id input)))
  ([^AgentManager manager dataset-id input options]
   (.get (add-dataset-example-async! manager dataset-id input options))))
