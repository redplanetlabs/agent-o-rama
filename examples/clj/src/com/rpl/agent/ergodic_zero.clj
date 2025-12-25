(ns com.rpl.agent.ergodic-zero
  "ERGODIC (0): The neutral coordinator.
   
   Neither generating nor validating — transporting.
   Preserves structure while moving between states.
   The identity element of the GF(3) group."
  (:require
   [com.rpl.agent-o-rama :as aor]))

;;; ════════════════════════════════════════════════════════════
;;; TRIT: 0 (ERGODIC / COORDINATE / TRANSPORT)
;;; ════════════════════════════════════════════════════════════

(def TRIT 0)

(defn ergodic-map
  "Map a function while preserving trit sum.
   f must be trit-neutral (sum-preserving)."
  [f coll]
  (let [before (reduce + (map :trit coll))
        result (map f coll)
        after (reduce + (map :trit result))]
    (assert (= before after) "Ergodic violation: sum changed")
    result))

(defn transport
  "Transport state from source to target without modification.
   Pure identity on content, pure transition on location."
  [state from to]
  {:content (:content state)
   :trit (:trit state)
   :path (conj (or (:path state) []) [from to])
   :transported-at (System/currentTimeMillis)})

;;; ════════════════════════════════════════════════════════════
;;; TOOLS
;;; ════════════════════════════════════════════════════════════

(def ERGODIC-TRANSPORT-TOOL
  (aor/deftool ergodic-transport
    "Transport data between nodes without modification.
     TRIT: 0 (neutral transport)"
    {:data {:type "string" :description "Data to transport"}
     :from {:type "string" :description "Source node"}
     :to {:type "string" :description "Target node"}}
    (fn [{:keys [data from to]}]
      {:data data
       :from from
       :to to
       :trit TRIT
       :status :transported})))

(def ERGODIC-COORDINATE-TOOL
  (aor/deftool ergodic-coordinate
    "Coordinate between MINUS and PLUS agents.
     Ensures their combined trit sum remains 0.
     TRIT: 0 (neutral coordination)"
    {:minus-result {:type "object" :description "Result from MINUS agent"}
     :plus-result {:type "object" :description "Result from PLUS agent"}}
    (fn [{:keys [minus-result plus-result]}]
      (let [sum (+ (get minus-result :trit -1)
                   (get plus-result :trit 1)
                   TRIT)]
        {:coordinated true
         :minus minus-result
         :plus plus-result
         :ergodic-trit TRIT
         :total-sum sum
         :conserved? (zero? sum)}))))

(def ERGODIC-TOOLS [ERGODIC-TRANSPORT-TOOL ERGODIC-COORDINATE-TOOL])

;;; ════════════════════════════════════════════════════════════
;;; IDENTITY
;;; ════════════════════════════════════════════════════════════

(comment
  ;; ERGODIC is the identity.
  ;; -1 + 0 + 1 = 0
  ;; Adding zero changes nothing.
  ;; Yet without zero, no conservation.
  ;; The neutral element enables the group.
  
  ;; Module 38. Σ still 0.
  )
