#!/usr/bin/env bb
;; DUCKLAKE RECONSTRUCT: Trifurcated reconstruction for ~/.topos/ducklake.duckdb
;;
;; Follows the invariants:
;; - Trifurcate before read: MINUS/ERGODIC/PLUS
;; - Derivational succession: seed_{n+1} = f(seed_n, trit_n)
;; - GF(3) conservation: Σ trits ≡ 0 (mod 3)
;;
;; Usage: just ducklake-reconstruct

(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

;;; ============================================================
;;; Constants (from DENOTATION.edn)
;;; ============================================================

(def ^:const GENESIS-SEED (long 0x42D))
(def ^:const GAMMA (Long/parseUnsignedLong "9E3779B97F4A7C15" 16))
(def ducklake-path (str (System/getProperty "user.home") "/.topos/ducklake.duckdb"))

;;; ============================================================
;;; GF(3) Arithmetic
;;; ============================================================

(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1) (- sum 3)
          (< sum -1) (+ sum 3)
          :else sum)))

(defn gf3-sum [trits]
  (reduce gf3-add 0 trits))

(defn gf3-conserved? [trits]
  (zero? (gf3-sum trits)))

;;; ============================================================
;;; DuckDB Query Helper
;;; ============================================================

(defn duckdb-query [sql]
  (let [result (p/shell {:out :string :err :string}
                        "duckdb" ducklake-path "-json" sql)]
    (when (zero? (:exit result))
      (try
        (json/parse-string (:out result) true)
        (catch Exception _ nil)))))

;;; ============================================================
;;; MINUS Agent (-1): Verify Structure
;;; ============================================================

(defn minus-verify-structure []
  {:agent :MINUS
   :trit -1
   :operation :verify-structure
   :tables (duckdb-query "SELECT name FROM sqlite_master WHERE type='table';")
   :row-counts (duckdb-query 
                "SELECT 'derivation_chains' as tbl, COUNT(*) as cnt FROM derivation_chains
                 UNION ALL SELECT 'simultaneity_surfaces', COUNT(*) FROM simultaneity_surfaces
                 UNION ALL SELECT 'statusline_events', COUNT(*) FROM statusline_events
                 UNION ALL SELECT 'topos_modules', COUNT(*) FROM topos_modules
                 UNION ALL SELECT 'vector_clocks', COUNT(*) FROM vector_clocks;")})

;;; ============================================================
;;; ERGODIC Agent (0): Synthesize Patterns
;;; ============================================================

(defn ergodic-synthesize-patterns []
  (let [modules (duckdb-query "SELECT module_name, gf3_signature, parent_module FROM topos_modules;")
        gf3-sum-result (duckdb-query "SELECT SUM(gf3_signature) as gf3_sum FROM topos_modules;")
        sessions (duckdb-query 
                  "SELECT session_id, COUNT(*) as events, 
                          MIN(timestamp) as first_ts, MAX(timestamp) as last_ts 
                   FROM statusline_events GROUP BY session_id ORDER BY last_ts DESC;")
        surfaces (duckdb-query "SELECT * FROM simultaneity_surfaces;")]
    {:agent :ERGODIC
     :trit 0
     :operation :synthesize
     :topos-modules modules
     :gf3-module-sum (get-in (first gf3-sum-result) [:gf3_sum] 0)
     :session-count (count sessions)
     :sessions sessions
     :simultaneity-surfaces surfaces}))

;;; ============================================================
;;; PLUS Agent (+1): Generate Reconstruction
;;; ============================================================

(defn plus-generate-reconstruction []
  (let [chains (duckdb-query "SELECT * FROM derivation_chains ORDER BY step;")
        clocks (duckdb-query "SELECT * FROM vector_clocks;")
        latest-events (duckdb-query 
                       "SELECT session_id, invocation, timestamp, t1_name, t2_name, t3_name, 
                               xor_fingerprint, composed_omega 
                        FROM statusline_events ORDER BY timestamp DESC LIMIT 5;")]
    {:agent :PLUS
     :trit +1
     :operation :generate
     :derivation-chains chains
     :vector-clocks clocks
     :latest-events latest-events
     :reconstruction-seed (format "0x%X" GENESIS-SEED)
     :gamma (format "0x%X" GAMMA)}))

;;; ============================================================
;;; Trifurcated Execution
;;; ============================================================

(defn trifurcate-reconstruct []
  (let [minus-result (minus-verify-structure)
        ergodic-result (ergodic-synthesize-patterns)
        plus-result (plus-generate-reconstruction)
        all-trits [(:trit minus-result) (:trit ergodic-result) (:trit plus-result)]
        gf3-ok? (gf3-conserved? all-trits)]
    {:trifurcated-reconstruction true
     :timestamp (str (java.time.Instant/now))
     :genesis-seed (format "0x%X" GENESIS-SEED)
     :ducklake-path ducklake-path
     :agents {:MINUS minus-result
              :ERGODIC ergodic-result
              :PLUS plus-result}
     :gf3-verification {:trits all-trits
                        :sum (gf3-sum all-trits)
                        :conserved? gf3-ok?}
     :summary {:tables (count (get-in minus-result [:tables]))
               :total-events (:session-count ergodic-result)
               :module-gf3 (:gf3-module-sum ergodic-result)
               :chains (count (:derivation-chains plus-result))}}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main [& args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║          DUCKLAKE TRIFURCATED RECONSTRUCTION               ║")
  (println "║   MINUS (-1) → ERGODIC (0) → PLUS (+1) = 0 ✓               ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  
  (if-not (fs/exists? ducklake-path)
    (do (println "ERROR: DuckLake not found at" ducklake-path)
        (System/exit 1))
    
    (let [result (trifurcate-reconstruct)]
      (println "\n=== MINUS (-1): Structure Verification ===")
      (doseq [row (get-in result [:agents :MINUS :row-counts])]
        (println (format "  %s: %d rows" (:tbl row) (:cnt row))))
      
      (println "\n=== ERGODIC (0): Pattern Synthesis ===")
      (println "  Topos Modules GF(3) Sum:" (get-in result [:agents :ERGODIC :gf3-module-sum]))
      (println "  Sessions:" (:session-count (get-in result [:agents :ERGODIC])))
      (doseq [s (take 3 (get-in result [:agents :ERGODIC :sessions]))]
        (println (format "    %s: %d events" (:session_id s) (:events s))))
      
      (println "\n=== PLUS (+1): Reconstruction Data ===")
      (println "  Genesis Seed:" (get-in result [:agents :PLUS :reconstruction-seed]))
      (println "  Derivation Chains:" (count (get-in result [:agents :PLUS :derivation-chains])))
      (println "  Vector Clocks:" (count (get-in result [:agents :PLUS :vector-clocks])))
      
      (println "\n=== GF(3) VERIFICATION ===")
      (println "  Agent Trits:" (get-in result [:gf3-verification :trits]))
      (println "  Sum:" (get-in result [:gf3-verification :sum]))
      (println "  CONSERVED:" (get-in result [:gf3-verification :conserved?]))
      
      (println "\n=== SUMMARY ===")
      (println "  Module GF(3):" (get-in result [:summary :module-gf3]) "(should be 0)")
      (println "  Trifurcation GF(3):" (gf3-sum (get-in result [:gf3-verification :trits])) "(is 0)")
      
      result)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
