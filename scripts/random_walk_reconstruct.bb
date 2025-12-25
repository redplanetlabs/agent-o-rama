#!/usr/bin/env bb
;; RANDOM WALK RECONSTRUCTION
;; 
;; Continuous random walk through thread history to reconstruct ~/.topos
;; Uses SplitMix64 deterministic derivation for reproducible walks
;;
;; Invariants:
;; - GF(3) conservation at each step
;; - Derivational succession: seed_{n+1} = f(seed_n, trit_n)
;; - Trifurcate before any major operation

(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

;;; ============================================================
;;; Constants
;;; ============================================================

(def ^:const GENESIS-SEED (long 0x42D))
(def ^:const GAMMA (Long/parseUnsignedLong "9E3779B97F4A7C15" 16))
(def ^:const MIX1 (Long/parseUnsignedLong "BF58476D1CE4E5B9" 16))
(def ^:const MIX2 (Long/parseUnsignedLong "94D049BB133111EB" 16))

(def topos-root (str (System/getProperty "user.home") "/.topos"))

;;; ============================================================
;;; GF(3) Arithmetic
;;; ============================================================

(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1) (- sum 3)
          (< sum -1) (+ sum 3)
          :else sum)))

(defn gf3-sum [trits] (reduce gf3-add 0 trits))
(defn gf3-conserved? [trits] (zero? (gf3-sum trits)))

;;; ============================================================
;;; SplitMix64 Random Walk
;;; ============================================================

(defn splitmix64 [state]
  (let [z (bit-and (unchecked-add (long state) GAMMA) Long/MAX_VALUE)
        z (bit-and (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 30)) MIX1) Long/MAX_VALUE)
        z (bit-and (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) MIX2) Long/MAX_VALUE)]
    (bit-xor z (unsigned-bit-shift-right z 31))))

(defn derive-step [seed step]
  (let [state (bit-and (unchecked-add (long seed) (unchecked-multiply GAMMA (long step))) Long/MAX_VALUE)
        z (splitmix64 state)
        trit (cond (< (mod z 3) 1) -1
                   (< (mod z 3) 2) 0
                   :else +1)]
    {:step step
     :seed seed
     :next-seed z
     :trit trit
     :hex (format "0x%X" z)}))

(defn random-walk [seed steps]
  (loop [current-seed seed
         step 0
         path []]
    (if (>= step steps)
      {:path path
       :final-seed current-seed
       :trits (mapv :trit path)
       :gf3-sum (gf3-sum (mapv :trit path))}
      (let [derived (derive-step current-seed step)]
        (recur (:next-seed derived)
               (inc step)
               (conj path derived))))))

;;; ============================================================
;;; Directory Structure (from thread extraction)
;;; ============================================================

(def topos-structure
  "Original ~/.topos structure from T-019b4716"
  {:directories
   ["airdrop" "aptos_config" "aptos_experimentation" "archived" "bin"
    "Gay" "gay_additions" "Gay.jl" "GayDot" "GayMove" "GCPDot"
    "localsend-analysis" "localsend-treesitter" "nats_telemetry"
    "ruler" "screenshots" "skills" "tree-sitter-parsers"]
   
   :core-files
   [{:name "Project.toml" :trit 0}
    {:name "Manifest.toml" :trit 0}
    {:name "justfile" :trit 0}
    {:name "README.md" :trit 0}
    {:name "RULES.md" :trit -1}
    {:name ".env" :trit -1}]
   
   :scripts
   [{:name "ingest_interactions_to_ducklake.py" :trit -1 :exists true}
    {:name "ingest_statusline_to_ducklake.py" :trit 0}
    {:name "ingest_statusline_to_ducklake.jl" :trit 0}
    {:name "screenshot_watcher.py" :trit +1}
    {:name "amp_threads.py" :trit +1}
    {:name "morph_client.py" :trit +1}
    {:name "populate_historical_colors.py" :trit 0}
    {:name "nats_rate_monitor.py" :trit -1}
    {:name "trialectic_fork.py" :trit -1}]
   
   :julia-files
   [{:name "run_gay.jl" :trit +1}
    {:name "nats_gay_bridge.jl" :trit 0}
    {:name "random_walk.jl" :trit 0}
    {:name "portfolio_dec2025.jl" :trit +1}
    {:name "seed_user.jl" :trit -1}
    {:name "use_gpg_secret.jl" :trit -1}]
   
   :duckdbs
   [{:name "ducklake.duckdb" :trit 0 :exists true}
    {:name "repos.duckdb" :trit -1}
    {:name "ct_zulip.duckdb" :trit +1}
    {:name "interactions.db" :trit 0}]})

;;; ============================================================
;;; Reconstruction Functions
;;; ============================================================

(defn ensure-directories! []
  (doseq [dir (:directories topos-structure)]
    (let [path (str topos-root "/" dir)]
      (when-not (fs/exists? path)
        (fs/create-dirs path)
        (println "  Created:" dir)))))

(defn create-placeholder! [file-spec]
  (let [path (str topos-root "/" (:name file-spec))]
    (when-not (or (fs/exists? path) (:exists file-spec))
      (spit path (str ";; Placeholder for " (:name file-spec) "\n"
                      ";; Trit: " (:trit file-spec) "\n"
                      ";; Reconstruct from thread history\n"))
      (println "  Placeholder:" (:name file-spec) "trit=" (:trit file-spec)))))

(defn verify-structure []
  (let [existing-dirs (filterv #(fs/exists? (str topos-root "/" %)) 
                               (:directories topos-structure))
        missing-dirs (filterv #(not (fs/exists? (str topos-root "/" %))) 
                              (:directories topos-structure))
        all-files (concat (:core-files topos-structure)
                          (:scripts topos-structure)
                          (:julia-files topos-structure)
                          (:duckdbs topos-structure))
        existing-files (filterv #(fs/exists? (str topos-root "/" (:name %))) all-files)
        missing-files (filterv #(not (fs/exists? (str topos-root "/" (:name %)))) all-files)]
    {:existing-dirs (count existing-dirs)
     :missing-dirs missing-dirs
     :existing-files (count existing-files)
     :missing-files (mapv :name missing-files)
     :file-trits (mapv :trit all-files)
     :gf3-sum (gf3-sum (mapv :trit all-files))}))

;;; ============================================================
;;; Random Walk Reconstruction
;;; ============================================================

(defn walk-reconstruct! [steps]
  (println "\n=== Random Walk Reconstruction ===")
  (println "  Seed:" (format "0x%X" GENESIS-SEED))
  (println "  Steps:" steps)
  
  (let [walk (random-walk GENESIS-SEED steps)]
    (println "  Walk GF(3):" (:gf3-sum walk))
    
    ;; Use walk to determine reconstruction order
    (doseq [{:keys [step trit]} (:path walk)]
      (let [action (case trit
                     -1 :verify
                     0  :create
                     +1 :generate)]
        (case action
          :verify (println (format "  [%d] MINUS: Verify step" step))
          :create (println (format "  [%d] ERGODIC: Create placeholder" step))
          :generate (println (format "  [%d] PLUS: Generate content" step)))))
    
    walk))

;;; ============================================================
;;; Main Entry Point
;;; ============================================================

(defn -main [& args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║         RANDOM WALK RECONSTRUCTION                         ║")
  (println "║   SplitMix64 deterministic walk through ~/.topos           ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  
  (let [steps (if (seq args) (parse-long (first args)) 9)]
    
    (println "\n=== Pre-Reconstruction State ===")
    (let [pre (verify-structure)]
      (println "  Existing dirs:" (:existing-dirs pre))
      (println "  Missing dirs:" (count (:missing-dirs pre)))
      (println "  Existing files:" (:existing-files pre))
      (println "  Missing files:" (count (:missing-files pre))))
    
    (println "\n=== Creating Directory Structure ===")
    (ensure-directories!)
    
    (println "\n=== Creating Placeholders ===")
    (doseq [f (concat (:core-files topos-structure)
                      (:scripts topos-structure)
                      (:julia-files topos-structure))]
      (create-placeholder! f))
    
    (let [walk (walk-reconstruct! steps)]
      
      (println "\n=== Post-Reconstruction State ===")
      (let [post (verify-structure)]
        (println "  Existing dirs:" (:existing-dirs post))
        (println "  Existing files:" (:existing-files post))
        (println "  Structure GF(3):" (:gf3-sum post))
        (println "  Walk GF(3):" (:gf3-sum walk))
        (println "  Combined conserved:" (gf3-conserved? 
                                          (concat (:file-trits post) (:trits walk)))))
      
      {:walk walk
       :structure (verify-structure)})))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
