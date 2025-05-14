(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.impl.core :as i]
            [com.rpl.agent-o-rama.impl.helpers :as h]
            [com.rpl.agent-o-rama.impl.store-impl :as simpl]
            [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import [com.rpl.agentorama
             AgentClient
             AgentGraph
             AgentManager
             AgentNode
             AgentsTopology
             MultiAgg$Impl]
           [com.rpl.rama PState$Declaration PState$Schema]
           [com.rpl.rama.ops RamaAccumulatorAgg RamaCombinerAgg]))

(defn agents-topology [setup topologies]
  (let [stream-topology (stream-topology topologies "_agents-topology")
        defined?-vol (volatile! false)
        agents-vol (volatile! {})
        store-info-vol (volatile! {})]
    (reify AgentsTopology
      (newAgent [this name]
        (when (contains? @agents-vol name)
          (throw (h/ex-info "Agent already exists" {:name name})))
        (let [ret (i/mk-agent-graph)]
          (vswap! agents-vol assoc name ret)
          ret ))
      (getStreamTopology [this] stream-topology)

      ;; TODO: need methods for getting mirror agents, and will also need methods for invoking mirror agents
      ;;    - should invoking a mirror agent be a node, or should it just be an invoke?
      ;;      - feels like an invoke

      (declareKeyValueStore [this name key-class val-class]
        (simpl/declare-store* stream-topology store-info-vol name simpl/KV {key-class val-class}))
      (declareDocumentStore [this name key-class key-val-classes]
        (when-not (-> key-val-classes count even?)
          (throw (h/ex-info "Document store must be given even number of key/val classes"
                          {:count (count key-val-classes)})))
        (simpl/declare-store*
          stream-topology
          store-info-vol
          name
          simpl/DOC
          {key-class (fixed-keys-schema (into {} (partition 2 key-val-classes)))}))
      (^PState$Declaration declarePState [this ^String name ^Class schema]
        (declare-pstate* stream-topology (symbol name) schema))
      (^PState$Declaration declarePState [this ^String name ^PState$Schema schema]
        (.pstate stream-topology name schema))
      (declareAgentObject [this name o]
        (declare-object* setup (symbol name) o))
      (define [this]
        (when @defined?-vol
          (throw (h/ex-info "Agents topology already defined" {})))
        (vreset! defined?-vol true)
        (i/define-agents!
          setup
          stream-topology
          @agents-vol
          @store-info-vol)
        ))))

(defn underlying-stream-topology [^AgentsTopology at]
  (.getStreamTopology at))

(defn define-agents! [^AgentsTopology at]
  (.define at))

;; TODO: all the declare methods

(defn new-agent [^AgentsTopology agents-topology name]
  (.newAgent agents-topology name))

(defn node [agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node!
    agent-graph
    name
    output-nodes-spec
    (aor-types/->Node node-fn)))

(defn agg-start-node [agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node!
    agent-graph
    name
    output-nodes-spec
    (aor-types/->NodeAggStart node-fn nil)))

(defn agg-node [agent-graph name output-nodes-spec agg node-fn]
  (i/internal-add-agg-node!
    agent-graph
    name
    output-nodes-spec
    agg
    node-fn))

(defmacro multi-agg [& body]
  (let [ret-sym (gensym "ret")]
    `(let [~ret-sym (i/mk-multi-agg)]
      ~@(for [form body]
          (condp = (first form)
            'init
            (let [[_ bindings & body] form]
              (when-not (= [] bindings)
                (throw (h/ex-info "Invalid binding vector for MultiAgg init"
                                {:bindings bindings :required []})))
              `(i/internal-add-init! ~ret-sym (fn [] ~@body)))

            'on
            (let [[_ name & body] form]
              `(i/internal-add-handler! ~ret-sym ~name (fn ~@body)))
            (throw (h/ex-info "Invalid MultiAgg method" {:method (first form)}))
            ))
       ~ret-sym
       )))

(defn emit! [^AgentNode agent-node node & args]
  (.emit agent-node node (into-array Object args)))

(defn result! [^AgentNode agent-node val]
  (.result agent-node val))

(defn get-store [^AgentNode agent-node name]
  (.getStore agent-node name))

(defn- parse-map-options
  [[arg1 & rest-args :as args]]
  (if (map? arg1) [arg1 rest-args] [{} args]))

(defmacro agentmodule [& args]
  (let [[options [[agent-topology-sym] & body]] (parse-map-options args)]
    `(module ~options
       [setup# topologies#]
       (let [~agent-topology-sym (agents-topology setup# topologies#)]
         ~@body
         (define-agents! ~agent-topology-sym)
         ))))

(defmacro defagentmodule
  [sym & args]
  (let [[options args] (parse-map-options args)
        name-default   (str sym)]
    `(def ~sym (agentmodule ~(merge {:module-name name-default} options) ~@args))))

(defn agent-manager [cluster]
  ;; TODO: <<<<>>>>
  )

(defn agent-client [manager module-name agent-name]
  (let []
    ;; TODO: <<<<>>>>
    (reify AgentClient
      (invoke [this args]
        ;; TODO: <<<<>>>>
        )
      (invokeAsync [this arg]
        ;; TODO: <<<<>>>>
        )
      (initiate [this args]
        ;; TODO: <<<<>>>>
        )
      (initiateAsync [this args]
        ;; TODO: <<<<>>>>
        )
      (agentResult [this agent-invoke]
        ;; TODO: <<<<>>>>
        )
      (agentResultAsync [this agent-invoke]
        ;; TODO: <<<<>>>>
        )
      (stream [this agent-invoke node async-invoke-name]
        ;; TODO: <<<<>>>>
        )
      (stream [this agent-invoke node async-invoke-name callback-void-jfn]
        ;; TODO: <<<<>>>>
        )
      (streamInstance [this agent-invoke node async-invoke-name node-invoke-id]
        ;; TODO: <<<<>>>>
        )
      (streamInstance [this agent-invoke node async-invoke-name node-invoke-id callback-void-jfn]
        ;; TODO: <<<<>>>>
        )
      (close [this]
        ;; TODO: <<<<>>>>
        ))))

(defn module-agents [cluster module-name]
  ;; TODO: <<<<>>>> should use AgentManager for this so query topology client can be created already...
  )
;; TODO: <<<<>>>> need to define agent client
;;    - gets depot/PStates/query topologies
;;    - should wrap a Java client
;;      - AgentManager.open(...) // same as clustermanager.open
;;      - new AgentManager(ETLManagerBase)

;; TODO: <<<<>>>>
;;  - need test namespace with ability to define and run agent graphs outside of modules
;;    - make a "LocalAgentsTopology" with methods to run it
;;    - getStreamTopology will throw exception
