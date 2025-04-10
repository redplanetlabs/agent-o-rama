(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [loom.graph :as graph])
  (:import [com.rpl.agentorama AgentsTopology]
           [com.rpl.rama PState$Schema]))

(defn agents-topology [name setup topologies]
  (let [stream-topology (stream-topology topologies (str "__agents-topology-" name))
        defined?-vol (volatile! false)]
    (reify AgentsTopology
      (newAgent [this name]
        ;; TODO: return AgentBuilder
        )
      (getStreamTopology [this]
        stream-topology)

      ;; TODO: need methods for getting mirror agents, and will also need methods for invoking mirror agents

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
      (declareObject [this name o]
        (declare-object* setup (symbol name) o))
      (define [this]
        (when @defined?-vol
          (throw (ex-info "Agents topology already defined" {})))
        (vreset defined?-vol true)
        ))))

(defn define-agents! [^AgentTopology at]
  (.define at))

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
