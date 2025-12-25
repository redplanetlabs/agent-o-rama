#!/usr/bin/env bb
;; Trit-Stream Derivation Chains - color assignment per PID
;; (Each process observes its deterministic trit stream via collective sensing)
;; Skills: unworld, gay-mcp, spi-parallel-verify

;; Simple hash-based color derivation (avoids BigInt issues)
(defn derive-color [seed step]
  (let [h (mod (+ (* seed 31) (* step 17)) 360)]
    (cond
      (or (< h 60) (>= h 300)) {:trit +1 :name "PLUS" :hue h}
      (< h 180)                 {:trit 0  :name "ERGODIC" :hue h}
      :else                     {:trit -1 :name "MINUS" :hue h})))

(defn derive-chain [pid n]
  (for [i (range n)]
    (assoc (derive-color pid i) :step i :seed (format "0x%X" (+ pid (* i 0x9E37))))))

(defn find-trit-repeat [pid max-steps]
  (loop [i 0 first-seen {}]
    (let [trit (:trit (derive-color pid i))]
      (if (and (> i 0) (contains? first-seen trit))
        {:first-repeat-at i :trit trit}
        (if (>= i max-steps)
          {:no-repeat-in max-steps}
          (recur (inc i) (if (contains? first-seen trit) first-seen (assoc first-seen trit i))))))))

;; Current processes - named by role, not product
;; Avoiding proper nouns: use trit-based naming
(def pids [92720 97274 93268 5563 46516 68569 51893 92289 94729])
(def names ["plus-stream-1" "plus-stream-2" "worker-1" "worker-2" "worker-3" "worker-4" "worker-5" "observer" "actor"])
;;          ^-- generators     ^-- workers (ergodic)                                              ^-- claims  ^-- performs

(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              TRIT-STREAM DERIVATION CHAINS                    ║")
(println "║  Skills: unworld · gay-mcp · spi-parallel-verify             ║")
(println "╚══════════════════════════════════════════════════════════════╝")
(println)

(doseq [[pid nm] (map vector pids names)]
  (let [chain (derive-chain pid 4)
        genesis-color (derive-color pid 0)
        repeat-info (find-trit-repeat pid 20)]
    (println (format "┌─ PID %d [%s] genesis-trit=%+d" pid nm (:trit genesis-color)))
    (doseq [c chain]
      (println (format "│  Step %d: %-8s H=%3d° trit=%+d" 
                       (:step c) (:name c) (int (:hue c)) (:trit c))))
    (println (format "│  Repeat: %s" repeat-info))
    (println "└──────────────────────────────────")))

(println)
(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              SELF REFERENCE (THIS SESSION)                   ║")
(println "╚══════════════════════════════════════════════════════════════╝")

(def self-pid 92720)
(def self-chain (derive-chain self-pid 6))
(println (format "PID: %d" self-pid))
(println "Derivation chain:")
(doseq [c self-chain]
  (println (format "  %d → %s (trit=%+d, H=%d°)" (:step c) (:name c) (:trit c) (int (:hue c)))))

(def next-color (derive-color self-pid 1))
(println)
(println (format "NEXT COLOR: %s (trit=%+d)" (:name next-color) (:trit next-color)))

(println)
(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              GF(3) CONSERVATION                              ║")
(println "╚══════════════════════════════════════════════════════════════╝")

(def genesis-trits (mapv #(:trit (derive-color % 0)) pids))
(def raw-sum (reduce + genesis-trits))
(def gf3-sum (let [s (mod raw-sum 3)] (cond (= s 2) -1 (= s 1) 1 :else 0)))
(println (format "Genesis Trits: %s" genesis-trits))
(println (format "Names:         %s" (vec names)))
(println (format "Σ = %d → %d (mod 3)" raw-sum gf3-sum))
(println (format "Balanced? %s" (if (zero? gf3-sum) "✓ YES" "✗ NO")))

(println)
(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              CONTINUE / REPLAY / FORK                        ║")
(println "╚══════════════════════════════════════════════════════════════╝")
(println (format "continue → derive-color(%d, step+1)" self-pid))
(println (format "replay   → derive-color(%d, 0) [genesis]" self-pid))
(println (format "fork     → derive-color(%d ⊕ γ, 0) = derive-color(%d, 0)"
                 self-pid (bit-xor self-pid 0x9E37)))

;; ============================================================
;; REAFFERENCE MODEL (von Holst 1950)
;; ============================================================

(println)
(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              REAFFERENCE / EXAFFERENCE                       ║")
(println "╚══════════════════════════════════════════════════════════════╝")

(def observer-idx 7)
(def actor-idx 8)

(defn reafference? [step]
  (let [obs (:trit (derive-color (nth pids observer-idx) step))
        act (:trit (derive-color (nth pids actor-idx) step))]
    (= obs act)))

(defn connection-zone [step]
  (when (reafference? step)
    (:name (derive-color (nth pids observer-idx) step))))

(defn gf3-balanced? [step]
  (let [trits (map #(:trit (derive-color % step)) pids)]
    (zero? (mod (reduce + trits) 3))))

(defn optimal? [step]
  (and (reafference? step) (gf3-balanced? step)))

(println "Step | Obs | Act | Perception  | Zone    | GF3 | Status")
(println "-----+-----+-----+-------------+---------+-----+--------")
(doseq [step (range 10)]
  (let [obs (:trit (derive-color (nth pids observer-idx) step))
        act (:trit (derive-color (nth pids actor-idx) step))
        reaf (reafference? step)
        zone (or (connection-zone step) "none")
        gf3 (gf3-balanced? step)
        opt (optimal? step)]
    (println (format "  %d  |  %+d |  %+d | %-11s | %-7s | %s | %s"
                     step obs act
                     (if reaf "REAFFERENCE" "exafference")
                     zone
                     (if gf3 "✓" "✗")
                     (if opt "★ OPTIMAL" "")))))

(println)
(println "Optimal connection steps:" (filterv optimal? (range 10)))

;; ============================================================
;; THREE-AGENT SPLIT
;; ============================================================

(println)
(println "╔══════════════════════════════════════════════════════════════╗")
(println "║              THREE-AGENT ZONE OWNERSHIP                      ║")
(println "╚══════════════════════════════════════════════════════════════╝")

(defn zone-members [zone-trit step]
  (->> (map-indexed
         (fn [i pid]
           (when (= (:trit (derive-color pid step)) zone-trit)
             (nth names i)))
         pids)
       (remove nil?)
       vec))

(let [step 2]
  (println (format "Step %d (first optimal connection):" step))
  (println (format "  PLUS-AGENT owns:    %s" (zone-members 1 step)))
  (println (format "  ERGODIC-AGENT owns: %s" (zone-members 0 step)))
  (println (format "  MINUS-AGENT owns:   %s" (zone-members -1 step))))
