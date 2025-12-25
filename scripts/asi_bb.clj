#!/usr/bin/env bb
;; ASI Agent - Babashka version (no Rama deps)
;; Calls Anthropic API directly via httpkit

(require '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[clojure.set :as set]
         '[babashka.http-client :as http]
         '[cheshire.core :as json])

(def SKILLS-ROOT (io/file (System/getProperty "user.home") ".claude" "skills"))
(def ANTHROPIC-API "https://api.anthropic.com/v1/messages")

;;; ============================================================================
;;; Skill Discovery
;;; ============================================================================

(defn list-skills []
  (->> (.listFiles SKILLS-ROOT)
       (filter #(.isDirectory %))
       (filter #(.exists (io/file % "SKILL.md")))
       (mapv #(.getName %))
       sort))

(defn read-skill [skill-name]
  (let [skill-file (io/file SKILLS-ROOT skill-name "SKILL.md")]
    (when (.exists skill-file)
      (slurp skill-file))))

(defn skill-complexity [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)
        files (->> (file-seq skill-dir)
                   (filter #(.isFile %))
                   (remove #(str/starts-with? (.getName %) ".")))]
    {:files (count files)
     :loc (reduce + 0 (for [f files
                            :when (re-matches #".*\.(clj|py|js|ts|md)$" (.getName f))]
                        (try (count (str/split-lines (slurp f)))
                             (catch Exception _ 0))))}))

(defn load-skill [skill-name]
  (when-let [content (read-skill skill-name)]
    (let [{:keys [files loc]} (skill-complexity skill-name)]
      {:name skill-name
       :content content
       :files files
       :loc loc
       :fitness (* files (Math/log (max 1 loc)))})))

(defn top-skills [n]
  (->> (list-skills)
       (map load-skill)
       (filter some?)
       (sort-by :fitness >)
       (take n)))

;;; ============================================================================
;;; Sheaf Cohomology Verification
;;; ============================================================================

(defn extract-concepts [text]
  (->> (str/split text #"\s+")
       (filter #(> (count %) 5))
       (map str/lower-case)
       (remove #(re-find #"^[#\-\*\|`{}\[\]<>()]" %))
       (remove #(re-find #"^\d|https?:|file:" %))
       frequencies
       (sort-by val >)
       (take 20)
       (map first)
       set))

(defn jaccard [a b]
  (let [i (count (set/intersection a b))
        u (count (set/union a b))]
    (if (zero? u) 0.0 (double (/ i u)))))

(defn verify-cocycle [analyses]
  (let [n (count analyses)
        concepts (mapv #(extract-concepts (:analysis %)) analyses)
        overlaps (for [i (range n) j (range (inc i) n)]
                   {:pair [(:skill (nth analyses i)) (:skill (nth analyses j))]
                    :overlap (jaccard (nth concepts i) (nth concepts j))})
        global (apply set/intersection concepts)
        obstructions (filter #(< (:overlap %) 0.05) overlaps)]
    {:valid? (< (count obstructions) (/ n 2))
     :H0 (count global)
     :H1 (count obstructions)
     :avg (if (empty? overlaps) 0 (/ (reduce + (map :overlap overlaps)) (count overlaps)))
     :global global}))

;;; ============================================================================
;;; Anthropic API
;;; ============================================================================

(defn anthropic-chat [messages & {:keys [system max-tokens model]
                                   :or {max-tokens 2000
                                        model "claude-sonnet-4-20250514"}}]
  (let [api-key (System/getenv "ANTHROPIC_API_KEY")
        _ (when-not api-key (throw (ex-info "ANTHROPIC_API_KEY not set" {})))
        body {:model model
              :max_tokens max-tokens
              :messages messages}
        body (if system (assoc body :system system) body)
        resp (http/post ANTHROPIC-API
                        {:headers {"x-api-key" api-key
                                   "anthropic-version" "2023-06-01"
                                   "content-type" "application/json"}
                         :body (json/generate-string body)})]
    (-> resp :body (json/parse-string true) :content first :text)))

;;; ============================================================================
;;; Analyst Prompts
;;; ============================================================================

(def ANALYST-PROMPT
  "You are a skill-analyst persona based on: %s

SKILL CONTENT (excerpt):
%s

Analyze the following topic through the lens of this skill.
Focus on: patterns, implementations, integration points.
Be concise but specific. Return actionable insights.")

(def COMPOSER-PROMPT
  "You are a skill composer. Given research from multiple skill-analysts,
synthesize a NEW skill that combines their insights.

Return JSON with:
{
  \"name\": \"new-skill-name\",
  \"description\": \"One-line description\",
  \"parents\": [\"skill1\", \"skill2\"],
  \"capabilities\": [\"cap1\", \"cap2\", \"cap3\"],
  \"implementation_notes\": \"How to implement this skill\"
}

Be creative but practical. The skill should be genuinely useful.")

;;; ============================================================================
;;; ASI Agent Core
;;; ============================================================================

(defn research-skill [skill topic]
  (println (str "  → " (:name skill) " researching..."))
  (let [excerpt (subs (:content skill) 0 (min 2000 (count (:content skill))))
        prompt (format ANALYST-PROMPT (:name skill) excerpt)
        response (anthropic-chat [{:role "user" :content (str "Topic: " topic)}]
                                 :system prompt
                                 :max-tokens 1000)]
    (println (str "  ✓ " (:name skill) " done"))
    {:skill (:name skill)
     :analysis response}))

(defn compose-skill [research-results topic]
  (let [summary (str/join "\n\n" 
                          (for [{:keys [skill analysis]} research-results]
                            (str "=== " skill " ===\n" analysis)))]
    (println "\n→ Composing new skill...")
    (let [response (anthropic-chat [{:role "user" 
                                     :content (str "Topic: " topic 
                                                   "\n\nResearch:\n" summary
                                                   "\n\nReturn JSON only.")}]
                                   :system COMPOSER-PROMPT
                                   :max-tokens 1500)]
      (try
        (json/parse-string response true)
        (catch Exception e
          (println "Warning: Could not parse JSON, extracting...")
          (when-let [match (re-find #"\{[^}]+\}" response)]
            (json/parse-string match true)))))))

(defn self-host! [composed-skill]
  (let [skill-name (:name composed-skill)
        skill-dir (io/file SKILLS-ROOT skill-name)
        skill-md (io/file skill-dir "SKILL.md")]
    (if (.exists skill-dir)
      {:exists true :name skill-name}
      (do
        (.mkdirs skill-dir)
        (spit skill-md
              (str "---\n"
                   "name: " skill-name "\n"
                   "description: " (:description composed-skill) "\n"
                   "parents: " (str/join ", " (:parents composed-skill)) "\n"
                   "generated: " (java.time.Instant/now) "\n"
                   "---\n\n"
                   "# " skill-name "\n\n"
                   (:description composed-skill) "\n\n"
                   "## Capabilities\n\n"
                   (str/join "\n" (map #(str "- " %) (:capabilities composed-skill))) "\n\n"
                   "## Implementation\n\n"
                   (:implementation_notes composed-skill) "\n\n"
                   "## Parents\n\n"
                   (str/join "\n" (map #(str "- " %) (:parents composed-skill))) "\n"))
        (println (str "✓ Created: " (.getAbsolutePath skill-md)))
        {:created true :name skill-name :path (.getAbsolutePath skill-md)}))))

(defn run-agent [topic & {:keys [n-skills evolve? parallel?] :or {n-skills 4 evolve? true parallel? true}}]
  (println "\n╔═══════════════════════════════════════════╗")
  (println "║   ASI Agent (Babashka) - Sheaf Gluing     ║")
  (println "╚═══════════════════════════════════════════╝")
  (println "\nTopic:" topic)
  
  (let [skills (top-skills n-skills)]
    (println "\nLoaded" (count skills) "skill-analysts:")
    (doseq [s skills]
      (println (str "  • " (:name s) " (" (:files s) " files, " (:loc s) " LOC)")))
    
    (println (str "\n=== Research Phase " (if parallel? "(parallel)" "(sequential)") " ==="))
    (let [research (if parallel?
                     ;; Parallel: launch all futures, then deref
                     (let [futures (mapv #(future (research-skill % topic)) skills)]
                       (mapv deref futures))
                     ;; Sequential fallback
                     (mapv #(research-skill % topic) skills))
          
          ;; Sheaf verification
          {:keys [valid? H0 H1 avg global]} (verify-cocycle research)]
      
      (println "\n=== Sheaf Cohomology ===")
      (println (str "H⁰ (global sections): " H0))
      (println (str "H¹ (obstructions): " H1))
      (println (format "Avg overlap: %.3f" avg))
      (when (seq global)
        (println "Global:" (str/join ", " (take 5 global))))
      (println (if valid? 
                 "✓ Cocycle condition satisfied" 
                 "⚠ Cohomology obstruction (proceeding anyway)"))
      
      ;; Compose
      (let [composed (compose-skill research topic)]
        (println "\n=== Composed Skill ===")
        (println "Name:" (:name composed))
        (println "Description:" (:description composed))
        (println "Parents:" (str/join ", " (:parents composed)))
        
        ;; Self-host
        (when evolve?
          (println "\n=== Self-Hosting ===")
          (let [result (self-host! composed)]
            (if (:created result)
              (println "New skill created!")
              (println "Skill already exists:" (:name result)))))
        
        {:composed composed :research research :cohomology {:H0 H0 :H1 H1}}))))

;;; ============================================================================
;;; CLI
;;; ============================================================================

(defn -main [& args]
  (let [topic (or (first args) "distributed coordination")
        n-skills (or (some-> (second args) parse-long) 4)
        evolve? (not (some #{"--dry-run"} args))
        parallel? (not (some #{"--sequential" "--seq"} args))]
    (run-agent topic :n-skills n-skills :evolve? evolve? :parallel? parallel?)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
