#!/usr/bin/env bb
;; Gay Tidar Agent: Triadic GF(3) orchestration with deterministic coloring
;; 
;; Architecture:
;; ┌─────────────────────────────────────────────────────────────────────────────┐
;; │                         GAY TIDAR ORCHESTRATOR                              │
;; │  Three parallel streams with GF(3) conservation: Σ trits ≡ 0 (mod 3)       │
;; └─────────────────────────────────────────────────────────────────────────────┘
;;        │                    │                    │
;;        ▼                    ▼                    ▼
;; ┌────────────┐       ┌────────────┐       ┌────────────┐
;; │   MINUS    │       │  ERGODIC   │       │    PLUS    │
;; │  trit: -1  │       │  trit: 0   │       │  trit: +1  │
;; │  #2626D8   │       │  #26D826   │       │  #D82626   │
;; │ validator  │       │ coordinator│       │ generator  │
;; └────────────┘       └────────────┘       └────────────┘
;;        │                    │                    │
;;        └────────────────────┼────────────────────┘
;;                             ▼
;;                    ┌────────────────┐
;;                    │ SHEAF GLUING   │
;;                    │ H¹ obstruction │
;;                    └────────────────┘

(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[babashka.http-client :as http]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[cheshire.core :as json])

;;; ============================================================================
;;; SplitMix64 Deterministic RNG (Gay.jl compatible)
;;; ============================================================================

;; Use unchecked-long for unsigned 64-bit constants
(def GOLDEN (unchecked-long 0x9E3779B97F4A7C15))

(defn splitmix64 [x]
  (let [z (unchecked-add (unchecked-long x) GOLDEN)
        z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 30)) 
                              (unchecked-long 0xBF58476D1CE4E5B9))
        z (unchecked-multiply (bit-xor z (unsigned-bit-shift-right z 27)) 
                              (unchecked-long 0x94D049BB133111EB))]
    (bit-xor z (unsigned-bit-shift-right z 31))))

(defn seed->trit [seed index]
  (let [h (splitmix64 (bit-xor (unchecked-long seed) (unchecked-long index)))]
    (- (mod (Math/abs h) 3) 1)))  ; {-1, 0, +1}

(defn seed->color [seed]
  (let [h (splitmix64 (unchecked-long seed))
        r (bit-and (unsigned-bit-shift-right h 16) 0xFF)
        g (bit-and (unsigned-bit-shift-right h 8) 0xFF)
        b (bit-and h 0xFF)]
    (format "#%02X%02X%02X" r g b)))

;;; ============================================================================
;;; Triadic Stream Definitions
;;; ============================================================================

(def TRIAD-STREAMS
  {:minus   {:trit -1 
             :color "#2626D8"
             :role :validator
             :prompt "You are a VALIDATOR agent (trit -1). Analyze for correctness, edge cases, and potential issues. Be critical and thorough."
             :port 8080}
   
   :ergodic {:trit 0
             :color "#26D826" 
             :role :coordinator
             :prompt "You are a COORDINATOR agent (trit 0). Synthesize information, find patterns, and maintain coherence across streams."
             :port 8081}
   
   :plus    {:trit +1
             :color "#D82626"
             :role :generator
             :prompt "You are a GENERATOR agent (trit +1). Create new solutions, generate code, and produce actionable output."
             :port 8082}})

;;; ============================================================================
;;; MLX API Interface
;;; ============================================================================

(def MLX-BASE-URL "http://localhost")

(defn mlx-url [port]
  (str MLX-BASE-URL ":" port "/v1"))

(defn check-mlx-available [port]
  (try
    (let [resp (http/get (str (mlx-url port) "/models") {:timeout 2000})]
      (= 200 (:status resp)))
    (catch Exception _ false)))

(defn chat-completion [port messages & {:keys [max-tokens] :or {max-tokens 2000}}]
  (let [url (str (mlx-url port) "/chat/completions")
        body {:model "default"
              :messages messages
              :max_tokens max-tokens}]
    (try
      (let [resp (http/post url
                            {:headers {"Content-Type" "application/json"}
                             :body (json/generate-string body)
                             :timeout 120000})]
        (-> resp :body (json/parse-string true) :choices first :message :content))
      (catch Exception e
        (str "Error: " (.getMessage e))))))

;;; ============================================================================
;;; Triadic Agent Execution
;;; ============================================================================

(defn run-stream [stream-key task seed]
  (let [stream (get TRIAD-STREAMS stream-key)
        port (:port stream)
        trit (:trit stream)
        color (:color stream)
        indexed-seed (bit-xor seed (hash stream-key))]
    
    (println (format "  [%s] %s (trit=%+d, color=%s)..." 
                     (name stream-key) 
                     (name (:role stream))
                     trit
                     color))
    
    (if (check-mlx-available port)
      (let [messages [{:role "system" :content (:prompt stream)}
                      {:role "user" :content (str "Task: " task 
                                                   "\n\nSeed: " (format "0x%X" indexed-seed)
                                                   "\nTrit: " trit)}]
            result (chat-completion port messages)]
        {:stream stream-key
         :trit trit
         :color color
         :seed indexed-seed
         :result result})
      {:stream stream-key
       :trit trit
       :color color
       :seed indexed-seed
       :result (str "MLX not available on port " port)})))

;;; ============================================================================
;;; Sheaf Cohomology Verification
;;; ============================================================================

(defn extract-concepts [text]
  (->> (str/split (or text "") #"\s+")
       (filter #(> (count %) 5))
       (map str/lower-case)
       (remove #(re-find #"^[#\-\*\|`{}\[\]<>()]" %))
       frequencies
       (sort-by val >)
       (take 20)
       (map first)
       set))

(defn jaccard [a b]
  (let [i (count (set/intersection a b))
        u (count (set/union a b))]
    (if (zero? u) 0.0 (double (/ i u)))))

(defn verify-sheaf-gluing [stream-results]
  (let [concepts (mapv #(extract-concepts (:result %)) stream-results)
        trits (mapv :trit stream-results)
        trit-sum (reduce + trits)
        
        ;; Pairwise overlaps
        overlaps (for [i (range 3) j (range (inc i) 3)]
                   {:pair [(-> (nth stream-results i) :stream)
                           (-> (nth stream-results j) :stream)]
                    :overlap (jaccard (nth concepts i) (nth concepts j))})
        
        ;; Global sections (concepts in all three)
        global (apply set/intersection concepts)
        
        ;; H¹ obstructions (weak gluing)
        obstructions (filter #(< (:overlap %) 0.05) overlaps)]
    
    {:gf3-conserved? (zero? (mod trit-sum 3))
     :trit-sum trit-sum
     :H0 (count global)
     :H1 (count obstructions)
     :global-sections global
     :overlaps overlaps
     :valid? (and (zero? (mod trit-sum 3))
                  (< (count obstructions) 2))}))

;;; ============================================================================
;;; Gay Tidar Orchestrator
;;; ============================================================================

(defn orchestrate-tidar [task & {:keys [seed parallel?] 
                                  :or {seed 0x42D parallel? true}}]
  (println "\n╔═══════════════════════════════════════════════════════════════╗")
  (println "║        GAY TIDAR AGENT - GF(3) Triadic Orchestration          ║")
  (println "╚═══════════════════════════════════════════════════════════════╝")
  (println (format "\nTask: %s" task))
  (println (format "Seed: 0x%X" seed))
  (println (format "Color: %s" (seed->color seed)))
  
  ;; Check availability
  (println "\n=== Stream Availability ===")
  (doseq [[k v] TRIAD-STREAMS]
    (let [avail? (check-mlx-available (:port v))]
      (println (format "  %s :%d %s %s" 
                       (name k) 
                       (:port v)
                       (:color v)
                       (if avail? "✓" "✗")))))
  
  (println "\n=== Triadic Execution ===")
  
  ;; Run three streams
  (let [results (if parallel?
                  (let [futures (mapv #(future (run-stream % task seed)) 
                                      [:minus :ergodic :plus])]
                    (mapv deref futures))
                  (mapv #(run-stream % task seed) [:minus :ergodic :plus]))
        
        ;; Verify sheaf gluing
        verification (verify-sheaf-gluing results)]
    
    (println "\n=== GF(3) Conservation ===")
    (println (format "Σ trits = %d" (:trit-sum verification)))
    (println (format "GF(3) conserved: %s" 
                     (if (:gf3-conserved? verification) "✓" "✗")))
    
    (println "\n=== Sheaf Cohomology ===")
    (println (format "H⁰ (global sections): %d" (:H0 verification)))
    (println (format "H¹ (obstructions): %d" (:H1 verification)))
    (when (seq (:global-sections verification))
      (println (format "Shared concepts: %s" 
                       (str/join ", " (take 5 (:global-sections verification))))))
    
    (println "\n=== Overlaps ===")
    (doseq [{:keys [pair overlap]} (:overlaps verification)]
      (println (format "  %s ↔ %s: %.2f" 
                       (name (first pair)) 
                       (name (second pair)) 
                       overlap)))
    
    (println (if (:valid? verification)
               "\n✓ Triadic gluing successful"
               "\n⚠ Cohomology obstruction detected"))
    
    {:task task
     :seed seed
     :results results
     :verification verification}))

;;; ============================================================================
;;; CLI
;;; ============================================================================

(defn show-architecture []
  (println "
╔═══════════════════════════════════════════════════════════════════════════════╗
║                      GAY TIDAR ARCHITECTURE                                    ║
║            Deterministic Coloring + GF(3) Conservation                         ║
╚═══════════════════════════════════════════════════════════════════════════════╝

SplitMix64 Seed (0x42D)
        │
        ├──────────────────┬──────────────────┐
        ▼                  ▼                  ▼
   ┌─────────┐        ┌─────────┐        ┌─────────┐
   │  MINUS  │        │ ERGODIC │        │  PLUS   │
   │ trit=-1 │        │ trit=0  │        │ trit=+1 │
   │ #2626D8 │        │ #26D826 │        │ #D82626 │
   │ :8080   │        │ :8081   │        │ :8082   │
   │validate │        │coordinate│       │generate │
   └────┬────┘        └────┬────┘        └────┬────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           ▼
                  ┌────────────────┐
                  │  SHEAF GLUING  │
                  │                │
                  │  H⁰ = global   │
                  │  H¹ = obstruct │
                  │                │
                  │ Σ trits ≡ 0    │
                  │   (mod 3)      │
                  └────────────────┘

INVARIANT: GF(3) conservation across all operations
"))

(defn -main [& args]
  (let [cmd (first args)]
    (case cmd
      "arch" (show-architecture)
      
      "check" (do
                (println "Checking triadic stream availability...")
                (doseq [[k v] TRIAD-STREAMS]
                  (let [avail? (check-mlx-available (:port v))]
                    (println (format "  %s :%d %s (trit=%+d) %s"
                                     (name k)
                                     (:port v)
                                     (:color v)
                                     (:trit v)
                                     (if avail? "✓" "✗"))))))
      
      "run" (let [task (or (second args) "Analyze the agent-o-rama architecture")
                  seed (if-let [s (nth args 2 nil)]
                         (Long/parseLong s 16)
                         0x42D)]
              (orchestrate-tidar task :seed seed))
      
      "color" (let [seed (if-let [s (second args)]
                           (Long/parseLong s 16)
                           0x42D)]
                (println (format "Seed: 0x%X" seed))
                (println (format "Color: %s" (seed->color seed)))
                (doseq [i (range 9)]
                  (println (format "  [%d] trit=%+d" i (seed->trit seed i)))))
      
      ;; Default
      (do
        (println "Usage: gay_tidar_agent.bb <command>")
        (println "")
        (println "Commands:")
        (println "  arch        Show architecture diagram")
        (println "  check       Check triadic stream availability")
        (println "  run <task> [seed]  Run triadic orchestration")
        (println "  color [seed]       Show color and trits for seed")
        (println "")
        (println "Start MLX servers on ports 8080, 8081, 8082:")
        (println "  Terminal 1: just mlx-coder              # MINUS :8080")
        (println "  Terminal 2: MLX_PORT=8081 just mlx-server  # ERGODIC :8081")
        (println "  Terminal 3: MLX_PORT=8082 just mlx-medium  # PLUS :8082")))))

(apply -main *command-line-args*)
