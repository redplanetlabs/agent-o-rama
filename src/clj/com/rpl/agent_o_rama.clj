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

(defmacro reify-AgentGraph [& body]
  `(reify AgentGraph
    ~@(for [i (range 1 8)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (vswap! ~'nodes-vol
              conj
              (->Node
                name#
                (normalize-output-nodes outputNodesSpec#)
                (h/convert-void-jfn jfn#)))
            this#
            )))
    ~@(for [i (range 1 8)]
        (let [name-sym (type-hinted String 'name#)
              osym (type-hinted Object 'outputNodesSpec#)
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'node [this# ~name-sym ~osym ~jfn-sym]
            (vswap! ~'nodes-vol
              conj
              (->AggStartNode
                name#
                (normalize-output-nodes outputNodesSpec#)
                (j/convert-void-jfn jfn#)))
            this#
            )))
    ))

(defn- mk-agent-graph [name]
  (let [nodes-vol (volatile! [])]
    (reify-AgentGraph
      (aggNode [this name outputNodesSpec aggNode]
        (vswap! nodes-vol
                conj
                (->AggNode name
                           (normalize-output-nodes outputNodesSpec)
                           aggNode))
        this
        ))))

(defn agents-topology [name setup topologies]
  (let [stream-topology (stream-topology topologies (str "__agents-topology-" name))
        defined?-vol (volatile! false)]
    (reify AgentsTopology
      (newAgent [this name]
        ;; TODO: return AgentGraph
        )
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
        ))))

(defn underlying-stream-topology [^AgentTopology at]
  (.getStreamTopology at))

(defn define-agents! [^AgentTopology at]
  (.define at))

;; TODO: all the declare methods

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

;; TODO:
;;  - should define API in Java and implement in Clojure
;;    - convert RamaFunction to clojure function
;;      - currently an internal method...
;;      - just pull it out and remove the INativeOperation handling
