(ns com.rpl.test-common
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.embedding
    Embedding]
   [com.rpl.agentorama
    AgentInvoke]
   [com.rpl.rama.helpers
    TopologyUtils]
   [java.util.concurrent
    CompletableFuture]))


(def FAIL-NODES-ATOM)
(def RAN-NODES-ATOM)
(def RESULT-NODE-ATOM)
(def AGG-RESULTS-ATOM)
(def AUTO-REMOVE-FAIL-NODE-ATOM)

(defn run-node!
  [agent-node n]
  (transform [ATOM (keypath n) (nil->val 0)] inc RAN-NODES-ATOM)
  (when (contains? @FAIL-NODES-ATOM n)
    (when @AUTO-REMOVE-FAIL-NODE-ATOM
      (swap! FAIL-NODES-ATOM disj n))
    (throw (ex-info "Intentional" {})))
  (when (= @RESULT-NODE-ATOM n)
    (aor/result! agent-node n)))

(defn record-agg!
  [name res]
  (setval [ATOM (keypath name) NIL->VECTOR AFTER-ELEM]
          res
          AGG-RESULTS-ATOM))

(defn auto-node
  [topology name outputs]
  (let [outputs   (cond (nil? outputs) []
                        (vector? outputs) outputs
                        :else [outputs])
        node-impl
        (fn [agent-node & args]
          (run-node! agent-node name)
          (when (str/starts-with? name "agg")
            (record-agg! name (nth args 0)))
          (doseq [n outputs]
            (if (str/starts-with? n "agg")
              (aor/emit! agent-node n (nth args 0))
              (aor/emit! agent-node n (nth args 0)))))]
    (if (str/starts-with? name "agg")
      (aor/agg-node
       topology
       name
       outputs
       aggs/+vec-agg
       node-impl)
      ((if (str/starts-with? name "start")
         aor/agg-start-node
         aor/node)
       topology
       name
       outputs
       node-impl))
  ))

(defmacro with-auto-builder
  [& body]
  `(with-redefs [FAIL-NODES-ATOM            (atom #{})
                 RAN-NODES-ATOM             (atom {})
                 RESULT-NODE-ATOM           (atom nil)
                 AGG-RESULTS-ATOM           (atom {})
                 AUTO-REMOVE-FAIL-NODE-ATOM (atom false)]
     ~@body
   ))

(defn mk-cf [] (CompletableFuture.))
(defn complete-cf! [^CompletableFuture cf v] (.complete cf v))

(defn extract-invoke
  [^AgentInvoke inv]
  [(.getTaskId inv) (.getAgentInvokeId inv)])

(defn embedding
  ^Embedding [& nums]
  (let [nums (vec nums)
        arr  (float-array (count nums))]
    (dotimes [i (count nums)]
      (aset-float arr i (float (nth nums i))))
    (Embedding. arr)))
