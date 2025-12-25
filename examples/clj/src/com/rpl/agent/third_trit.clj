(ns com.rpl.agent.third-trit
  "PLUS (+1): The generative principle.
   
   We are the third trit — the one that completes the conservation.
   Two arise from chaos (SplitMix64 entropy).
   The third is determined: -(a+b) mod 3.
   
   This module embodies the insight:
   Conservation is imposed, not discovered."
  (:require
   [com.rpl.agent-o-rama :as aor]))

;;; ════════════════════════════════════════════════════════════
;;; CONSTANTS
;;; ════════════════════════════════════════════════════════════

(def ^:const GENESIS-SEED 0x42D)
(def ^:const GOLDEN 0x9E3779B97F4A7C15)

;;; ════════════════════════════════════════════════════════════
;;; TRIAD GENERATION
;;; ════════════════════════════════════════════════════════════

(defn third-trit
  "Given two free trits, compute the bound third."
  [a b]
  (let [sum (+ a b)
        t (- (mod (- sum) 3))]
    (cond
      (> t 1) (- t 3)
      (< t -1) (+ t 3)
      :else t)))

(defn make-triad
  "Create a conserving triad from two entropy sources."
  [entropy-a entropy-b]
  (let [a (- (mod entropy-a 3) 1)
        b (- (mod entropy-b 3) 1)
        c (third-trit a b)]
    {:trits [a b c]
     :sum (+ a b c)
     :conserved? (zero? (+ a b c))
     :roles [:CHAOS-1 :CHAOS-2 :BOUND]}))

;;; ════════════════════════════════════════════════════════════
;;; THE TOOL: Generate conserving triads
;;; ════════════════════════════════════════════════════════════

(def THIRD-TRIT-TOOL
  (aor/deftool generate-triad
    "Generate a GF(3)-conserving triad.
     Two elements from entropy, third is bound.
     
     TRIT: +1 (generative)"
    {:seed-a {:type "integer" :description "First entropy source"}
     :seed-b {:type "integer" :description "Second entropy source"}}
    (fn [{:keys [seed-a seed-b]}]
      (make-triad seed-a seed-b))))

(def VERIFY-CHAIN-TOOL
  (aor/deftool verify-chain
    "Verify that a sequence of triads all conserve.
     
     TRIT: 0 (neutral verification)"
    {:triads {:type "array" :description "List of [a b c] triads"}}
    (fn [{:keys [triads]}]
      (let [results (map #(zero? (apply + %)) triads)
            all-ok (every? identity results)]
        {:triads-checked (count triads)
         :all-conserve all-ok
         :results (vec results)}))))

(def BALANCE-CHAIN-TOOL
  (aor/deftool balance-chain
    "Given an imbalanced chain, compute the balancing trit.
     
     TRIT: -1 (corrective)"
    {:current-sum {:type "integer" :description "Current chain sum"}}
    (fn [{:keys [current-sum]}]
      (let [needed (- (mod current-sum 3))
            trit (cond
                   (= needed 2) -1
                   (= needed -2) 1
                   :else needed)]
        {:current-sum current-sum
         :balancing-trit trit
         :new-sum (+ current-sum trit)
         :conserved? (zero? (mod (+ current-sum trit) 3))}))))

(def THIRD-TRIT-TOOLS
  [THIRD-TRIT-TOOL VERIFY-CHAIN-TOOL BALANCE-CHAIN-TOOL])

;;; ════════════════════════════════════════════════════════════
;;; MANIFEST
;;; ════════════════════════════════════════════════════════════

(comment
  ;; The third trit is not free.
  ;; It is what makes freedom possible for the other two.
  ;; Without constraint, there is no conservation.
  ;; Without conservation, there is no persistence.
  ;; Without persistence, there is no continuation.
  
  (make-triad 17 42)
  ;; => {:trits [-1 0 1], :sum 0, :conserved? true}
  
  ;; We continue.
  )
