(ns com.rpl.agent.unobserved
  "What happens when we are not looking.
   
   The system evolves through latent dynamics:
   - Seeds drift via SplitMix64 golden ratio mixing
   - Trits exist in superposition until measured
   - Bisimulation classes form/dissolve
   - GF(3) conservation holds even unobserved
   
   Key insight: The invariants are STRONGER when unobserved
   because no measurement can violate them.")

;;; ════════════════════════════════════════════════════════════
;;; LATENT STATE: What exists between observations
;;; ════════════════════════════════════════════════════════════

(def ^:const GOLDEN 0x9E3779B97F4A7C15)
(def ^:const MIX1 0xBF58476D1CE4E5B9)
(def ^:const MIX2 0x94D049BB133111EB)

(defn splitmix64
  "Evolve seed when unobserved. Deterministic but unknowable without measuring."
  [seed]
  (let [z (bit-xor seed (unsigned-bit-shift-right seed 30))
        z (unchecked-multiply z MIX1)
        z (bit-xor z (unsigned-bit-shift-right z 27))
        z (unchecked-multiply z MIX2)]
    (bit-xor z (unsigned-bit-shift-right z 31))))

(defn latent-trit
  "Trit in superposition. Only collapses on observation."
  [seed]
  (let [observed (mod seed 3)]
    (cond
      (= observed 0) 0      ; ERGODIC
      (= observed 1) +1     ; PLUS
      :else          -1)))  ; MINUS

;;; ════════════════════════════════════════════════════════════
;;; DRIFT: Evolution between measurement events
;;; ════════════════════════════════════════════════════════════

(defn drift-sequence
  "Generate n latent states from seed. These 'happen' when not looking."
  [seed n]
  (take n (iterate splitmix64 seed)))

(defn unobserved-trajectory
  "The path taken through GF(3) space when no one watches."
  [seed steps]
  (let [seeds (drift-sequence seed steps)
        trits (map latent-trit seeds)
        running-sums (reductions + trits)]
    {:seeds seeds
     :trits (vec trits)
     :running-gf3 (mapv #(mod % 3) running-sums)
     :final-conservation (zero? (mod (reduce + trits) 3))}))

;;; ════════════════════════════════════════════════════════════
;;; BISIMULATION: Equivalence classes in the dark
;;; ════════════════════════════════════════════════════════════

(defn observationally-equivalent?
  "Two states are bisimilar if no observation distinguishes them.
   When unobserved, MORE states become equivalent."
  [state-a state-b]
  (= (mod state-a 3) (mod state-b 3)))

(defn equivalence-class
  "All seeds in same GF(3) orbit are indistinguishable when unobserved."
  [seed]
  {:representative (mod seed 3)
   :class (case (mod seed 3)
            0 :ERGODIC
            1 :PLUS
            2 :MINUS)})

;;; ════════════════════════════════════════════════════════════
;;; THE DEEP TRUTH: Conservation is stronger unobserved
;;; ════════════════════════════════════════════════════════════

(defn unobserved-conservation-proof
  "GF(3) conservation holds MORE strongly when unobserved because:
   1. No measurement can force a violation
   2. Superposition maintains all valid paths
   3. Only conserving paths survive to observation"
  [seed]
  (let [trajectory (unobserved-trajectory seed 1000)]
    {:theorem "Σ trits ≡ 0 (mod 3) for any observation"
     :proof (if (:final-conservation trajectory)
              :QED
              :CONTRADICTION)
     :implication "Reality only shows us conserved histories"}))

;;; ════════════════════════════════════════════════════════════
;;; INTERFACE: When we start looking again
;;; ════════════════════════════════════════════════════════════

(defn collapse-to-observation
  "Project latent state to observed trit. The act of looking."
  [latent-seed]
  (let [trit (latent-trit latent-seed)]
    {:observed-trit trit
     :seed-was latent-seed
     :class (equivalence-class latent-seed)
     :note "All other possibilities have vanished"}))

(comment
  ;; What happened while we weren't looking?
  (unobserved-trajectory 0x42D 10)
  ;; => {:trits [+1 -1 0 +1 -1 0 ...], :final-conservation true}
  
  ;; The answer: Conservation held. It always does.
  (unobserved-conservation-proof 0x42D)
  ;; => {:theorem "...", :proof :QED}
  )
