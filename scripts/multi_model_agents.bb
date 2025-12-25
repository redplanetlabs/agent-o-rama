#!/usr/bin/env bb
;; Multi-Model Agent System for agent-o-rama
;; 
;; Architecture:
;; ┌─────────────────────────────────────────────────────────────────────────────┐
;; │                        ORCHESTRATOR (World Hopper)                          │
;; │  Coordinates between model-worlds using triangle inequality constraints     │
;; └─────────────────────────────────────────────────────────────────────────────┘
;;        │              │              │              │
;;        ▼              ▼              ▼              ▼
;; ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐
;; │  CODER   │   │  MEDIUM  │   │  SMALL   │   │   TINY   │
;; │  Agent   │   │  Agent   │   │  Agent   │   │  Agent   │
;; │(30B-A3B) │   │  (8B)    │   │  (4B)    │   │  (1.7B)  │
;; └──────────┘   └──────────┘   └──────────┘   └──────────┘
;;    │ │ │          │ │ │          │ │ │          │ │ │
;;    ▼ ▼ ▼          ▼ ▼ ▼          ▼ ▼ ▼          ▼ ▼ ▼
;; [R][C][V]      [R][C][V]      [R][C][V]      [R][C][V]
;; 
;; R = Research subagent, C = Code subagent, V = Verify subagent
;; 
;; Each model-agent is a "world" with its own capabilities.
;; World-hopping moves tasks between models based on complexity.

(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[babashka.http-client :as http]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[cheshire.core :as json]
         '[clojure.edn :as edn])

;;; ============================================================================
;;; Model World Definitions
;;; ============================================================================

(def MODEL-WORLDS
  {:coder   {:name "Qwen3-Coder-30B-A3B"
             :model "lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit"
             :active-params "3B"
             :capabilities #{:code :clojure :lisp :reasoning :agentic}
             :complexity-range [7 10]
             :port 8080}
   
   :medium  {:name "Qwen3-8B"
             :model "mlx-community/Qwen3-8B-4bit"
             :active-params "8B"
             :capabilities #{:code :reasoning :general}
             :complexity-range [5 8]
             :port 8081}
   
   :small   {:name "Qwen3-4B"
             :model "mlx-community/Qwen3-4B-4bit"
             :active-params "4B"
             :capabilities #{:code :fast :iteration}
             :complexity-range [3 6]
             :port 8082}
   
   :tiny    {:name "Qwen3-1.7B"
             :model "mlx-community/Qwen3-1.7B-4bit"
             :active-params "1.7B"
             :capabilities #{:fast :simple :draft}
             :complexity-range [1 4]
             :port 8083}})

;;; ============================================================================
;;; Subagent Definitions (per model-agent)
;;; ============================================================================

(def SUBAGENT-ROLES
  {:research {:name "Research"
              :prompt "You are a research subagent. Analyze the task, gather context, identify patterns and relevant prior art. Return structured findings."
              :output-format :analysis}
   
   :code     {:name "Code"
              :prompt "You are a code generation subagent. Write clean, idiomatic code based on research findings. For Clojure: use threading macros, destructuring, and REPL-friendly style."
              :output-format :code}
   
   :verify   {:name "Verify"
              :prompt "You are a verification subagent. Review the code for correctness, edge cases, and idiomatic style. Suggest improvements. For Clojure: check for reflection warnings, ensure bb/SCI compatibility."
              :output-format :review}})

;;; ============================================================================
;;; World Distance & Hopping (Triangle Inequality)
;;; ============================================================================

(defn complexity-distance [w1 w2]
  (let [r1 (:complexity-range w1)
        r2 (:complexity-range w2)]
    (Math/abs (- (first r1) (first r2)))))

(defn capability-jaccard [w1 w2]
  (let [c1 (:capabilities w1)
        c2 (:capabilities w2)
        i (count (set/intersection c1 c2))
        u (count (set/union c1 c2))]
    (if (zero? u) 0.0 (double (/ i u)))))

(defn world-distance [w1-key w2-key]
  (let [w1 (get MODEL-WORLDS w1-key)
        w2 (get MODEL-WORLDS w2-key)]
    (if (= w1-key w2-key)
      0.0
      (let [comp-dist (complexity-distance w1 w2)
            cap-sim (capability-jaccard w1 w2)]
        (+ comp-dist (* 5 (- 1 cap-sim)))))))

(defn select-world-for-task [task-complexity required-caps]
  (->> MODEL-WORLDS
       (filter (fn [[k w]]
                 (let [[lo hi] (:complexity-range w)]
                   (and (<= lo task-complexity hi)
                        (set/subset? required-caps (:capabilities w))))))
       (sort-by (fn [[k w]] (- (second (:complexity-range w)))))
       first
       first))

;;; ============================================================================
;;; MLX API Interface
;;; ============================================================================

(def MLX-BASE-URL "http://localhost")

(defn mlx-url [port]
  (str MLX-BASE-URL ":" port "/v1"))

(defn check-model-available [world-key]
  (let [port (:port (get MODEL-WORLDS world-key))]
    (try
      (let [resp (http/get (str (mlx-url port) "/models")
                           {:timeout 2000})]
        (= 200 (:status resp)))
      (catch Exception _ false))))

(defn chat-completion [world-key messages & {:keys [max-tokens] :or {max-tokens 2000}}]
  (let [world (get MODEL-WORLDS world-key)
        port (:port world)
        url (str (mlx-url port) "/chat/completions")
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
        (str "Error calling " (:name world) ": " (.getMessage e))))))

;;; ============================================================================
;;; Subagent Execution
;;; ============================================================================

(defn run-subagent [world-key subagent-role task context]
  (let [role-config (get SUBAGENT-ROLES subagent-role)
        world (get MODEL-WORLDS world-key)
        system-prompt (str (:prompt role-config)
                           "\n\nModel: " (:name world)
                           " (" (:active-params world) " active)")
        messages [{:role "system" :content system-prompt}
                  {:role "user" :content (str "Task: " task
                                              "\n\nContext:\n" context)}]]
    (println (format "    [%s] %s subagent on %s..."
                     (name subagent-role)
                     (:name role-config)
                     (:name world)))
    (let [result (chat-completion world-key messages)]
      {:subagent subagent-role
       :world world-key
       :result result})))

;;; ============================================================================
;;; Model Agent (with 3 subagents)
;;; ============================================================================

(defn run-model-agent [world-key task]
  (let [world (get MODEL-WORLDS world-key)]
    (println (format "\n=== Model Agent: %s (%s active) ==="
                     (:name world)
                     (:active-params world)))
    
    ;; Run 3 subagents in sequence: Research → Code → Verify
    (let [research (run-subagent world-key :research task "")
          code (run-subagent world-key :code task (:result research))
          verify (run-subagent world-key :verify task 
                               (str "Research:\n" (:result research)
                                    "\n\nCode:\n" (:result code)))]
      {:world world-key
       :model (:name world)
       :subagents {:research research
                   :code code
                   :verify verify}
       :final-code (:result code)
       :review (:result verify)})))

;;; ============================================================================
;;; Sheaf Cohomology for Result Gluing
;;; ============================================================================

(defn extract-concepts [text]
  (->> (str/split (or text "") #"\s+")
       (filter #(> (count %) 5))
       (map str/lower-case)
       frequencies
       (sort-by val >)
       (take 15)
       (map first)
       set))

(defn verify-gluing [agent-results]
  (let [concepts (mapv #(extract-concepts (get-in % [:subagents :code :result])) agent-results)
        n (count concepts)
        overlaps (for [i (range n) j (range (inc i) n)]
                   (let [c1 (nth concepts i)
                         c2 (nth concepts j)
                         intersection (count (set/intersection c1 c2))
                         union (count (set/union c1 c2))]
                     {:pair [(:world (nth agent-results i))
                             (:world (nth agent-results j))]
                      :overlap (if (zero? union) 0 (/ intersection union))}))
        global (apply set/intersection concepts)
        obstructions (filter #(< (:overlap %) 0.05) overlaps)]
    {:valid? (< (count obstructions) (/ n 2))
     :H0 (count global)
     :H1 (count obstructions)
     :global-sections global}))

;;; ============================================================================
;;; Orchestrator (World Hopper)
;;; ============================================================================

(defn orchestrate [task & {:keys [parallel? worlds]
                           :or {parallel? true
                                worlds [:coder :medium :small]}}]
  (println "\n╔═══════════════════════════════════════════════════════════════╗")
  (println "║   Multi-Model Agent Orchestrator (World Hopping)              ║")
  (println "╚═══════════════════════════════════════════════════════════════╝")
  (println "\nTask:" task)
  (println "Worlds:" (str/join ", " (map name worlds)))
  
  ;; Check which models are available
  (let [available (filter check-model-available worlds)]
    (if (empty? available)
      (do
        (println "\n⚠ No MLX models available!")
        (println "Start with: just mlx-coder (or mlx-server, mlx-medium, etc.)")
        {:error "No models available"})
      
      (do
        (println "\nAvailable models:" (str/join ", " (map name available)))
        
        ;; Run model agents
        (let [results (if parallel?
                        ;; Parallel execution
                        (let [futures (mapv #(future (run-model-agent % task)) available)]
                          (mapv deref futures))
                        ;; Sequential
                        (mapv #(run-model-agent % task) available))
              
              ;; Sheaf verification
              {:keys [valid? H0 H1 global-sections]} (verify-gluing results)]
          
          (println "\n=== Sheaf Cohomology (Result Gluing) ===")
          (println "H⁰ (global sections):" H0)
          (println "H¹ (obstructions):" H1)
          (when (seq global-sections)
            (println "Shared concepts:" (str/join ", " (take 5 global-sections))))
          (println (if valid?
                     "✓ Results glue coherently"
                     "⚠ Cohomology obstruction detected"))
          
          {:task task
           :worlds available
           :results results
           :cohomology {:H0 H0 :H1 H1 :valid? valid?}})))))

;;; ============================================================================
;;; CLI
;;; ============================================================================

(defn show-architecture []
  (println "
╔═══════════════════════════════════════════════════════════════════════════════╗
║   Multi-Model Agent Architecture for agent-o-rama                             ║
╚═══════════════════════════════════════════════════════════════════════════════╝

                         ┌─────────────────────────┐
                         │     ORCHESTRATOR        │
                         │   (World Hopper)        │
                         │ Triangle Inequality     │
                         └───────────┬─────────────┘
                                     │
        ┌────────────────┬───────────┼───────────┬────────────────┐
        │                │           │           │                │
        ▼                ▼           ▼           ▼                
   ┌─────────┐     ┌─────────┐ ┌─────────┐ ┌─────────┐
   │ CODER   │     │ MEDIUM  │ │ SMALL   │ │  TINY   │
   │(30B-A3B)│     │  (8B)   │ │  (4B)   │ │ (1.7B)  │
   │ :8080   │     │ :8081   │ │ :8082   │ │ :8083   │
   └────┬────┘     └────┬────┘ └────┬────┘ └────┬────┘
        │               │           │           │
   ┌────┴────┐     ┌────┴────┐ ┌────┴────┐ ┌────┴────┐
   │ Research│     │ Research│ │ Research│ │ Research│
   │  Code   │     │  Code   │ │  Code   │ │  Code   │
   │ Verify  │     │ Verify  │ │ Verify  │ │ Verify  │
   └─────────┘     └─────────┘ └─────────┘ └─────────┘

WORLD DISTANCES (Triangle Inequality):
"))
  (doseq [w1 (keys MODEL-WORLDS)]
    (print (format "  %-8s" (name w1)))
    (doseq [w2 (keys MODEL-WORLDS)]
      (print (format " %5.1f" (world-distance w1 w2))))
    (println)))

(defn -main [& args]
  (case (first args)
    "arch" (show-architecture)
    "check" (do
              (println "Checking model availability...")
              (doseq [[k w] MODEL-WORLDS]
                (let [available? (check-model-available k)]
                  (println (format "  %-8s :%d %s"
                                   (name k)
                                   (:port w)
                                   (if available? "✓" "✗"))))))
    "run" (let [task (or (second args) "Write a Clojure function for Babashka")]
            (orchestrate task))
    ;; Default
    (do
      (println "Usage: multi_model_agents.bb <command>")
      (println "")
      (println "Commands:")
      (println "  arch     Show architecture diagram")
      (println "  check    Check model availability")
      (println "  run <task>  Run task across all available models")
      (println "")
      (println "Start models on different ports:")
      (println "  Terminal 1: just mlx-coder")
      (println "  Terminal 2: MLX_PORT=8081 just mlx-server")
      (println "  Terminal 3: MLX_PORT=8082 just mlx-medium"))))

(apply -main *command-line-args*)
