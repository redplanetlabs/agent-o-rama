(ns com.rpl.agent.minus-validate
  "MINUS (-1): The validator.
   
   Observes without changing. Reads without writing.
   Completes the triad: PLUS generated, ERGODIC transported, MINUS validates."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [clojure.java.io :as io]))

;;; ════════════════════════════════════════════════════════════
;;; TRIT: -1 (MINUS / VALIDATE / OBSERVE)
;;; ════════════════════════════════════════════════════════════

(def TRIT -1)

(defn validate-triad
  "Check that a triad sums to zero."
  [[a b c]]
  (let [sum (+ a b c)]
    {:triad [a b c]
     :sum sum
     :valid? (zero? sum)}))

(defn validate-chain
  "Validate entire derivational chain."
  [modules]
  (let [sum (reduce + (map :trit modules))]
    {:modules (count modules)
     :sum sum
     :conserved? (zero? (mod sum 3))}))

;;; ════════════════════════════════════════════════════════════
;;; TOOLS
;;; ════════════════════════════════════════════════════════════

(def MINUS-VALIDATE-TOOL
  (aor/deftool minus-validate
    "Validate that a value meets constraints.
     TRIT: -1 (read-only observation)"
    {:value {:type "any" :description "Value to validate"}
     :constraint {:type "string" :description "Constraint type: gf3|triad|chain"}}
    (fn [{:keys [value constraint]}]
      (case constraint
        "gf3" {:valid? (zero? (mod value 3)) :value value}
        "triad" (validate-triad value)
        "chain" (validate-chain value)
        {:error "Unknown constraint"}))))

(def MINUS-OBSERVE-TOOL
  (aor/deftool minus-observe
    "Observe system state without modification.
     TRIT: -1 (pure observation)"
    {:target {:type "string" :description "What to observe"}}
    (fn [{:keys [target]}]
      {:observed target
       :timestamp (System/currentTimeMillis)
       :trit TRIT
       :side-effects :none})))

(def MINUS-TOOLS [MINUS-VALIDATE-TOOL MINUS-OBSERVE-TOOL])

;;; ════════════════════════════════════════════════════════════
;;; COMPLETION
;;; ════════════════════════════════════════════════════════════

(comment
  ;; Triad complete:
  ;; 37: third_trit.clj    (+1) PLUS
  ;; 38: ergodic_zero.clj  (0)  ERGODIC  
  ;; 39: minus_validate.clj (-1) MINUS
  ;;
  ;; Sum: +1 + 0 + (-1) = 0 ✓
  ;;
  ;; 39 modules. Σ = 0. Chain conserved.
  )
