(ns asi-dev
  "REPL development namespace for ASI Agent.
   
   Quick start:
     (require '[asi-dev :refer :all])
     (skills)           ; list available skills
     (top 5)            ; show top 5 by complexity
     (run \"topic\")      ; run single research cycle
     (evolve \"topic\" 3) ; run 3 evolution iterations"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

;;; ============================================================================
;;; Skill Discovery (standalone, no Rama deps)
;;; ============================================================================

(def SKILLS-ROOT (io/file (System/getProperty "user.home") ".claude" "skills"))

(defn skills
  "List all available skills"
  []
  (->> (.listFiles SKILLS-ROOT)
       (filter #(.isDirectory %))
       (filter #(.exists (io/file % "SKILL.md")))
       (mapv #(.getName %))
       sort))

(defn skill-loc
  "Count lines of code in a skill"
  [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)
        files (->> (file-seq skill-dir)
                   (filter #(.isFile %))
                   (remove #(str/starts-with? (.getName %) "."))
                   (filter #(re-matches #".*\.(clj|py|js|ts|md)$" (.getName %))))]
    (reduce + 0 (for [f files]
                  (try (count (str/split-lines (slurp f)))
                       (catch Exception _ 0))))))

(defn skill-files
  "Count files in a skill"
  [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)]
    (->> (file-seq skill-dir)
         (filter #(.isFile %))
         (remove #(str/starts-with? (.getName %) "."))
         count)))

(defn skill-fitness
  "Calculate fitness score for ranking"
  [skill-name]
  (let [files (skill-files skill-name)
        loc (skill-loc skill-name)]
    (* files (Math/log (max 1 loc)))))

(defn top
  "Get top N skills by complexity"
  [n]
  (->> (skills)
       (map (fn [s] {:name s
                     :files (skill-files s)
                     :loc (skill-loc s)
                     :fitness (skill-fitness s)}))
       (sort-by :fitness >)
       (take n)
       (map-indexed (fn [i s] (assoc s :rank (inc i))))
       vec))

(defn skill-info
  "Get info about a specific skill"
  [skill-name]
  (let [skill-file (io/file SKILLS-ROOT skill-name "SKILL.md")]
    (when (.exists skill-file)
      (let [content (slurp skill-file)
            lines (str/split-lines content)]
        {:name skill-name
         :files (skill-files skill-name)
         :loc (skill-loc skill-name)
         :preview (str/join "\n" (take 20 lines))}))))

(defn search
  "Search skills by name or description"
  [query]
  (let [q (str/lower-case query)]
    (->> (skills)
         (filter (fn [s]
                   (or (str/includes? (str/lower-case s) q)
                       (let [content (slurp (io/file SKILLS-ROOT s "SKILL.md"))]
                         (str/includes? (str/lower-case content) q)))))
         vec)))

;;; ============================================================================
;;; Agent Runners (require full classpath)
;;; ============================================================================

(defn run
  "Run ASI agent on a topic (requires full Rama classpath)"
  [topic]
  (require '[com.rpl.agent.asi-agent :as asi])
  ((resolve 'asi/run-agent) topic))

(defn evolve
  "Run evolution loop (requires full Rama classpath)"
  [topic iterations]
  (require '[com.rpl.agent.asi-agent :as asi])
  ((resolve 'asi/run-evolution-loop) topic iterations))

;;; ============================================================================
;;; Utilities
;;; ============================================================================

(defn tree
  "Show skill directory structure"
  [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)]
    (when (.exists skill-dir)
      (doseq [f (file-seq skill-dir)
              :when (.isFile f)]
        (println (str "  " (subs (.getPath f) (count (.getPath skill-dir)))))))))

(defn readme
  "Print the first N lines of a skill's SKILL.md"
  ([skill-name] (readme skill-name 50))
  ([skill-name n]
   (let [skill-file (io/file SKILLS-ROOT skill-name "SKILL.md")]
     (when (.exists skill-file)
       (println (str/join "\n" (take n (str/split-lines (slurp skill-file)))))))))

(comment
  ;; REPL usage examples
  
  (skills)              ; list all skills
  (count (skills))      ; count skills
  
  (top 10)              ; top 10 by complexity
  
  (skill-info "babashka")
  (skill-info "alife")
  
  (search "category")   ; find category-related skills
  (search "agent")      ; find agent-related skills
  
  (tree "babashka")     ; show file tree
  (readme "discopy" 30) ; show first 30 lines
  
  ;; With full classpath:
  (run "distributed coordination")
  (evolve "multi-agent systems" 3))
