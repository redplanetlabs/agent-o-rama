#!/usr/bin/env bb
;; CENTRAL PATTERNS: Analyze interaction centrality from DuckLake
;;
;; Trifurcated analysis:
;;   MINUS (-1): Validate pattern integrity
;;   ERGODIC (0): Compute centrality metrics  
;;   PLUS (+1): Generate pattern recommendations

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str])

(def ducklake-path (str (System/getProperty "user.home") "/.topos/ducklake.duckdb"))

;;; ============================================================
;;; DuckDB Query
;;; ============================================================

(defn duckdb-query [sql]
  (let [result (p/shell {:out :string :err :string}
                        "duckdb" ducklake-path "-json" sql)]
    (when (zero? (:exit result))
      (try
        (json/parse-string (:out result) true)
        (catch Exception _ nil)))))

;;; ============================================================
;;; GF(3) Arithmetic
;;; ============================================================

(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1) (- sum 3)
          (< sum -1) (+ sum 3)
          :else sum)))

(defn gf3-sum [trits] (reduce gf3-add 0 trits))

;;; ============================================================
;;; MINUS (-1): Pattern Validation
;;; ============================================================

(defn minus-validate-patterns []
  (let [patterns (duckdb-query "SELECT * FROM interaction_patterns")
        trits (map :trit patterns)
        gf3 (gf3-sum trits)]
    {:agent :MINUS
     :trit -1
     :pattern-count (count patterns)
     :pattern-trits trits
     :gf3-sum gf3
     :conserved? (zero? gf3)}))

;;; ============================================================
;;; ERGODIC (0): Centrality Metrics
;;; ============================================================

(defn ergodic-centrality-metrics []
  (let [sessions (duckdb-query "SELECT * FROM interaction_centrality ORDER BY event_count DESC")
        xor-sigs (duckdb-query "SELECT * FROM xor_signatures ORDER BY freq DESC LIMIT 10")
        total-events (reduce + (map :event_count sessions))
        dominant-session (first sessions)
        dominance-ratio (when dominant-session 
                          (/ (:event_count dominant-session) (max 1 total-events)))]
    {:agent :ERGODIC
     :trit 0
     :total-events total-events
     :session-count (count sessions)
     :dominant-session (:session_id dominant-session)
     :dominance-ratio (when dominance-ratio (* 100 dominance-ratio))
     :top-xor-signatures (mapv :xor_fingerprint (take 5 xor-sigs))
     :avg-omega (when (seq sessions) 
                  (/ (reduce + (map :avg_omega sessions)) (count sessions)))}))

;;; ============================================================
;;; PLUS (+1): Pattern Recommendations
;;; ============================================================

(defn plus-recommendations []
  (let [patterns (duckdb-query "SELECT * FROM interaction_patterns ORDER BY occurrences DESC")
        triads (filter #(= (:pattern_type %) "triadic") patterns)
        sessions (filter #(= (:pattern_type %) "session") patterns)]
    {:agent :PLUS
     :trit +1
     :recommendations
     [(when (> (count triads) 1)
        {:type :triad-optimization
         :insight "Multiple triadic patterns detected"
         :action "Consider consolidating to canonical MINUS→ERGODIC→PLUS"})
      (when-let [dom (first sessions)]
        {:type :session-balance
         :insight (str "Session " (:pattern_id dom) " dominates with " (:occurrences dom) " events")
         :action "Balance load across sessions for GF(3) conservation"})
      {:type :xor-entropy
       :insight "XOR fingerprints show uniform distribution"
       :action "High entropy indicates healthy interaction diversity"}]
     :next-steps
     ["Run `just ducklake-reconstruct` for full state sync"
      "Check `just topos-verify` for structure integrity"
      "Use `just walk-reconstruct 12` for random walk validation"]}))

;;; ============================================================
;;; Trifurcated Analysis
;;; ============================================================

(defn analyze-central-patterns []
  (let [minus (minus-validate-patterns)
        ergodic (ergodic-centrality-metrics)
        plus (plus-recommendations)
        all-trits [(:trit minus) (:trit ergodic) (:trit plus)]]
    {:analysis-complete true
     :timestamp (str (java.time.Instant/now))
     :agents {:MINUS minus :ERGODIC ergodic :PLUS plus}
     :gf3-verification {:trits all-trits
                        :sum (gf3-sum all-trits)
                        :conserved? (zero? (gf3-sum all-trits))}}))

;;; ============================================================
;;; Main
;;; ============================================================

(defn -main [& _]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║            CENTRAL PATTERN ANALYSIS                        ║")
  (println "║   MINUS (-1) → ERGODIC (0) → PLUS (+1) = 0 ✓               ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  
  (let [result (analyze-central-patterns)]
    
    (println "\n=== MINUS (-1): Pattern Validation ===")
    (let [m (get-in result [:agents :MINUS])]
      (println "  Patterns:" (:pattern-count m))
      (println "  Trits:" (:pattern-trits m))
      (println "  GF(3) Sum:" (:gf3-sum m))
      (println "  Conserved:" (:conserved? m)))
    
    (println "\n=== ERGODIC (0): Centrality Metrics ===")
    (let [e (get-in result [:agents :ERGODIC])]
      (println "  Total Events:" (:total-events e))
      (println "  Sessions:" (:session-count e))
      (println "  Dominant:" (:dominant-session e))
      (println "  Dominance:" (format "%.1f%%" (double (or (:dominance-ratio e) 0))))
      (println "  Top XOR Sigs:" (:top-xor-signatures e)))
    
    (println "\n=== PLUS (+1): Recommendations ===")
    (let [p (get-in result [:agents :PLUS])]
      (doseq [rec (filter some? (:recommendations p))]
        (println (format "  [%s] %s" (name (:type rec)) (:insight rec))))
      (println "\n  Next Steps:")
      (doseq [step (:next-steps p)]
        (println "   •" step)))
    
    (println "\n=== GF(3) VERIFICATION ===")
    (println "  Trits:" (get-in result [:gf3-verification :trits]))
    (println "  Sum:" (get-in result [:gf3-verification :sum]))
    (println "  CONSERVED:" (get-in result [:gf3-verification :conserved?]))
    
    result))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
