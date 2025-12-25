#!/usr/bin/env bb
;; newies ↔ mlxies handoff demonstration
;; Shows how local and remote codex can work together

(require '[babashka.fs :as fs]
         '[clojure.string :as str]
         '[babashka.process :as p])

(def HANDOFF-DIR (fs/expand-home "~/.topos/handoffs"))

(defn timestamp []
  (str (java.time.Instant/now)))

(defn create-handoff! [from to context]
  (fs/create-dirs HANDOFF-DIR)
  (let [id (str (java.util.UUID/randomUUID))
        handoff {:id id
                 :from from
                 :to to
                 :context context
                 :created (timestamp)}
        path (fs/path HANDOFF-DIR (str id ".edn"))]
    (spit (str path) (pr-str handoff))
    (println (format "📤 Handoff created: %s → %s" from to))
    (println (format "   ID: %s" id))
    handoff))

(defn list-handoffs []
  (when (fs/exists? HANDOFF-DIR)
    (->> (fs/list-dir HANDOFF-DIR)
         (filter #(str/ends-with? (str %) ".edn"))
         (map #(read-string (slurp (str %))))
         (sort-by :created)
         reverse)))

(defn show-interaction []
  (println "
╔═══════════════════════════════════════════════════════════════╗
║   newies ↔ mlxies: Remote/Local Codex Interaction             ║
╚═══════════════════════════════════════════════════════════════╝

┌─────────────────────────────────────────────────────────────────┐
│  NEWIES (Remote OpenAI)           MLXIES (Local MLX)            │
│  ☁️  Cloud-powered                🧠 On-device                   │
│  • Full GPT-5.2-codex             • Qwen2.5-7B-Instruct          │
│  • High capability                • Fast, private                │
│  • Requires API key               • Requires MLX server          │
└─────────────────────────────────────────────────────────────────┘

                    HANDOFF PATTERNS
                    ════════════════

  1. OFFLOAD (newies → mlxies)
     ┌──────────┐         ┌──────────┐
     │  newies  │ ──────▶ │  mlxies  │
     │ (complex │         │ (iterate │
     │  plan)   │         │  locally)│
     └──────────┘         └──────────┘
     Use remote for planning, local for execution loops

  2. ESCALATE (mlxies → newies)  
     ┌──────────┐         ┌──────────┐
     │  mlxies  │ ──────▶ │  newies  │
     │ (hit     │         │ (handle  │
     │  limit)  │         │  complex)│
     └──────────┘         └──────────┘
     Local handles routine, escalates when stuck

  3. PARALLEL (both)
     ┌──────────┐
     │  newies  │──┐
     └──────────┘  │     ┌──────────┐
                   ├────▶│  merge   │
     ┌──────────┐  │     └──────────┘
     │  mlxies  │──┘
     └──────────┘
     Both work on different aspects, merge results

                    USAGE
                    ═════

  # Start MLX server (terminal 1)
  just mlx-server

  # Run local codex (terminal 2)  
  ./scripts/mlxies \"implement the feature\"

  # Run remote codex (terminal 3)
  ./scripts/newies \"review and improve\"

  # Create handoff context
  ./scripts/newies_mlxies_handoff.bb handoff newies mlxies \"context here\"

  # List handoffs
  ./scripts/newies_mlxies_handoff.bb list
"))

(defn -main [& args]
  (case (first args)
    "handoff" (create-handoff! (second args) (nth args 2) (nth args 3 ""))
    "list" (do
             (println "Recent handoffs:")
             (doseq [h (take 5 (list-handoffs))]
               (println (format "  %s: %s → %s (%s)" 
                                (subs (:id h) 0 8) 
                                (:from h) 
                                (:to h)
                                (:created h)))))
    "show" (show-interaction)
    ;; default
    (show-interaction)))

(apply -main *command-line-args*)
