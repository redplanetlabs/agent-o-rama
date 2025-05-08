(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.impl :as i]
            [com.rpl.agent-o-rama.types :as aor-types])
  (:import [com.rpl.agentorama AgentsTopology AgentGraph MultiAgg$Impl]
           [com.rpl.rama PState$Declaration PState$Schema]
           [com.rpl.rama.ops RamaAccumulatorAgg RamaCombinerAgg]))

(defn agents-topology [name setup topologies]
  (let [stream-topology (stream-topology topologies (str "_agents-topology-" name))
        defined?-vol (volatile! false)
        agents-vol (volatile! {})]
    (reify AgentsTopology
      (newAgent [this name]
        (when (contains? @agents-vol name)
          (throw (ex-info "Agent already exists" {:name name})))
        (let [ret (i/mk-agent-graph)]
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
      (^PState$Declaration declarePState [this ^String name ^Class schema]
        (declare-pstate* stream-topology (symbol name) schema))
      (^PState$Declaration declarePState [this ^String name ^PState$Schema schema]
        (.pstate stream-topology name schema))
      (declareAgentObject [this name o]
        (declare-object* setup (symbol name) o))
      (define [this]
        (when @defined?-vol
          (throw (ex-info "Agents topology already defined" {})))
        (vreset! defined?-vol true)
        (i/define-agents!
          setup
          stream-topology
          @agents-vol)
        ))))

(defn underlying-stream-topology [^AgentsTopology at]
  (.getStreamTopology at))

(defn define-agents! [^AgentsTopology at]
  (.define at))

;; TODO: all the declare methods

(defn new-agent [^AgentsTopology agents-topology name]
  (.newAgent agents-topology name))

(defn node* [agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node!
    agent-graph
    name
    output-nodes-spec
    (aor-types/->Node node-fn)))

(defmacro node [agent-graph name output-nodes-spec & fn-body]
  `(node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-start-node* [agent-graph name output-nodes-spec node-fn]
  (i/internal-add-node!
    agent-graph
    name
    output-nodes-spec
    (aor-types/->NodeAggStart node-fn nil)))

(defmacro agg-start-node [agent-graph name output-nodes-spec & fn-body]
  `(agg-start-node* ~agent-graph ~name ~output-nodes-spec (fn ~@fn-body)))

(defn agg-node* [agent-graph name output-nodes-spec agg node-fn]
  (i/internal-add-agg-node!
    agent-graph
    name
    output-nodes-spec
    agg
    node-fn))

(defmacro agg-node [agent-graph name output-nodes-spec agg & fn-body]
  `(agg-node* ~agent-graph ~name ~output-nodes-spec ~agg (fn ~@fn-body)))

(defmacro multi-agg [& body]
  (let [ret-sym (gensym "ret")]
    `(let [~ret-sym (i/mk-multi-agg)]
      ~@(for [form body]
          (condp = (first form)
            'init
            (let [[_ & body] form]
              `(i/internal-add-init! ~ret-sym (fn ~@body)))

            'on
            (let [[_ name & body] form]
              `(i/internal-add-handler! ~ret-sym ~name (fn ~@body)))
            (throw (ex-info "Invalid MultiAgg method" {:method (first form)}))
            ))
       ~ret-sym
       )))

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

;; TODO: <<<<>>>>
;;  - need test namespace with ability to define and run agent graphs outside of modules
;;    - make a "LocalAgentsTopology" with methods to run it
;;    - getStreamTopology will throw exception
