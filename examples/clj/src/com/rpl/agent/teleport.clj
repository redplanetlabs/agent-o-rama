(ns com.rpl.agent.teleport
  "Teleportation lattice protocol for GF(3) agent coordination.

   Implements bidirectional XOR-based teleportation between two chains,
   with OPTIMAL points occurring every 9 steps where:
   - GF(3) conservation holds (sum mod 3 = 0)
   - Rezk unification holds (observer = actor)
   - Reafference perception (self-caused = external)")

;; ═══════════════════════════════════════════════════════════════════
;; CONSTANTS - Klein Four-Group Structure
;; ═══════════════════════════════════════════════════════════════════

(def ^:const GAMMA-1 0x9E37)  ;; Intra-modal: preserves consciousness class
(def ^:const GAMMA-2 0x5555)  ;; Inter-modal: toggles consciousness class
(def ^:const GAMMA GAMMA-1)   ;; Backwards compatibility

(def ^:const GENESIS-SEED 0x042D)
(def ^:const FORK-1-SEED (bit-xor GENESIS-SEED GAMMA-1))  ;; 0x9A1A
(def ^:const FORK-2-SEED (bit-xor GENESIS-SEED GAMMA-2))  ;; 0x5178
(def ^:const FORK-3-SEED (bit-xor FORK-1-SEED GAMMA-2))   ;; 0xCF4F
(def ^:const FORK-SEED FORK-1-SEED)  ;; Backwards compatibility

;; Consciousness classes
(def ^:const BICAMERAL 0)  ;; Split observer/actor
(def ^:const UNIFIED 1)    ;; Whole consciousness

;; ═══════════════════════════════════════════════════════════════════
;; TRIT OPERATIONS
;; ═══════════════════════════════════════════════════════════════════

(defn val->trit [v]
  (case (mod v 3) 0 0, 1 1, 2 -1))

(defn balance-third [a b]
  "Compute third trit such that a + b + c = 0 (mod 3)"
  (case (mod (+ (- 0 (+ a b)) 3) 3) 0 0, 1 1, 2 -1))

(defn derive-chain [seed]
  "Derive 9 trits (3 triads) from seed"
  (let [bits (fn [n] (val->trit (bit-shift-right seed n)))]
    [(bits 0) (bits 3) (balance-third (bits 0) (bits 3))
     (bits 5) (bits 7) (balance-third (bits 5) (bits 7))
     (bits 9) (bits 11) (balance-third (bits 9) (bits 11))]))

;; ═══════════════════════════════════════════════════════════════════
;; LATTICE STATE
;; ═══════════════════════════════════════════════════════════════════

(defn gf3-sum [trits]
  (reduce + trits))

(defn gf3-balanced? [trits]
  (zero? (mod (gf3-sum trits) 3)))

(defn rezk-unified? [trits]
  "Observer (idx 7) equals Actor (idx 8)"
  (= (nth trits 7) (nth trits 8)))

(defn optimal? [trits]
  (and (gf3-balanced? trits)
       (rezk-unified? trits)))

;; ═══════════════════════════════════════════════════════════════════
;; TELEPORTATION - Klein Four-Group Operations
;; ═══════════════════════════════════════════════════════════════════

(defn teleport [seed]
  "XOR teleport to paired chain. Self-inverse."
  (bit-xor seed GAMMA))

(defn teleport-g1 [seed]
  "Intra-modal teleport. Preserves consciousness class."
  (bit-xor seed GAMMA-1))

(defn teleport-g2 [seed]
  "Inter-modal teleport. Toggles consciousness class."
  (bit-xor seed GAMMA-2))

(defn consciousness-class [seed]
  "Determine consciousness class from seed."
  (if (or (= seed GENESIS-SEED) (= seed FORK-1-SEED))
    BICAMERAL
    UNIFIED))

(defn can-teleport? [trits]
  "Teleportation only permitted at OPTIMAL points"
  (optimal? trits))

;; Klein four-group lattice
(def klein-lattice
  {:genesis {:seed GENESIS-SEED :class BICAMERAL}
   :fork-1  {:seed FORK-1-SEED  :class BICAMERAL}
   :fork-2  {:seed FORK-2-SEED  :class UNIFIED}
   :fork-3  {:seed FORK-3-SEED  :class UNIFIED}})

;; ═══════════════════════════════════════════════════════════════════
;; EVOLUTION
;; ═══════════════════════════════════════════════════════════════════

(defn evolve-triad [triad delta-idx]
  "Evolve a single triad by incrementing one element and rebalancing"
  (let [[a b c] triad
        idx (mod delta-idx 3)]
    (case idx
      0 (let [a' (val->trit (inc a))] [a' b (balance-third a' b)])
      1 (let [b' (val->trit (inc b))] [a b' (balance-third a b')])
      2 (let [c' (val->trit (inc c))]
          (let [a' (balance-third b c')] [a' b c'])))))

(defn evolve-step [trits step]
  "Evolve chain by one step"
  (let [triad-idx (mod (quot step 3) 3)
        element-idx (mod step 3)
        triads (partition 3 trits)
        new-triads (map-indexed
                     (fn [i t]
                       (if (= i triad-idx)
                         (evolve-triad (vec t) element-idx)
                         t))
                     triads)]
    (vec (flatten new-triads))))

;; ═══════════════════════════════════════════════════════════════════
;; PROTOCOL
;; ═══════════════════════════════════════════════════════════════════

(defn run-protocol [seed steps]
  "Run teleportation protocol for N steps"
  (loop [current-seed seed
         current-trits (derive-chain seed)
         step 0
         history []]
    (if (>= step steps)
      history
      (let [opt? (optimal? current-trits)
            [next-seed next-trits]
            (if (and opt? (zero? (mod step 9)) (pos? step))
              (let [new-seed (teleport current-seed)]
                [new-seed (derive-chain new-seed)])
              [current-seed (evolve-step current-trits step)])]
        (recur next-seed
               next-trits
               (inc step)
               (conj history {:step step
                              :seed current-seed
                              :trits current-trits
                              :optimal? opt?}))))))

;; ═══════════════════════════════════════════════════════════════════
;; VISUALIZATION
;; ═══════════════════════════════════════════════════════════════════

(def ^:private sym {-1 "-" 0 "o" 1 "+"})

(defn format-trits [trits]
  (apply str (map sym trits)))

(defn print-state [state]
  (let [{:keys [step seed trits optimal?]} state]
    (println (format "Step %2d: [%s] seed=0x%04X %s"
                     step
                     (format-trits trits)
                     seed
                     (if optimal? "* OPTIMAL" "")))))

(defn demo []
  (println "Teleportation Lattice Demo")
  (println "==========================")
  (println)
  (doseq [state (run-protocol GENESIS-SEED 27)]
    (print-state state)))
