#!/usr/bin/env bb
;; Sheaf cohomology verification for skill overlaps
;; Checks cocycle condition: local sections glue into global sections

(require '[clojure.set :as set]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def SKILLS-ROOT (io/file (System/getProperty "user.home") ".claude" "skills"))

(defn skill-files [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)]
    (->> (file-seq skill-dir)
         (filter #(.isFile %))
         (remove #(str/starts-with? (.getName %) "."))
         count)))

(defn skill-loc [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)
        files (->> (file-seq skill-dir)
                   (filter #(.isFile %))
                   (filter #(re-matches #".*\.(clj|py|js|ts|md)$" (.getName %))))]
    (reduce + 0 (for [f files]
                  (try (count (str/split-lines (slurp f)))
                       (catch Exception _ 0))))))

(defn top-skills [n]
  (->> (.listFiles SKILLS-ROOT)
       (filter #(.isDirectory %))
       (filter #(.exists (io/file % "SKILL.md")))
       (map #(.getName %))
       (map (fn [s] {:name s
                     :files (skill-files s)
                     :loc (skill-loc s)
                     :fitness (* (skill-files s) (Math/log (max 1 (skill-loc s))))}))
       (sort-by :fitness >)
       (take n)))

(defn extract-concepts [skill-name]
  (let [content (slurp (io/file SKILLS-ROOT skill-name "SKILL.md"))]
    (->> (str/split content #"\s+")
         (filter #(> (count %) 6))
         (map str/lower-case)
         (remove #(re-matches #"^[#\-\*\|\`\{\}\[\]]+$" %))
         frequencies
         (sort-by val >)
         (take 15)
         (map first)
         set)))

(defn jaccard [a b]
  (let [i (count (set/intersection a b))
        u (count (set/union a b))]
    (if (zero? u) 0.0 (double (/ i u)))))

(defn verify-sheaf [skills]
  (let [n (count skills)
        concepts (mapv #(extract-concepts (:name %)) skills)
        overlaps (for [i (range n)
                       j (range (inc i) n)]
                   {:i i :j j
                    :pair [(:name (nth skills i)) (:name (nth skills j))]
                    :overlap (jaccard (nth concepts i) (nth concepts j))})
        global (apply set/intersection concepts)
        obstructions (filter #(< (:overlap %) 0.05) overlaps)
        avg-overlap (if (empty? overlaps) 0 
                        (/ (reduce + (map :overlap overlaps)) (count overlaps)))]
    {:valid? (< (count obstructions) (/ n 2))
     :H0 (count global)
     :H1 (count obstructions)
     :avg-overlap avg-overlap
     :global global
     :overlaps overlaps
     :obstructions obstructions}))

(defn -main [& args]
  (let [n (or (some-> (first args) parse-long) 4)
        skills (top-skills n)
        {:keys [valid? H0 H1 avg-overlap global overlaps obstructions]} (verify-sheaf skills)]
    
    (println "╔═══════════════════════════════════════════╗")
    (println "║   Sheaf Cohomology: Skill Gluing Check    ║")
    (println "╚═══════════════════════════════════════════╝")
    (println)
    (println "Skills (top" n "by complexity):")
    (doseq [s skills]
      (println (format "  • %-30s %3d files, %5d LOC" (:name s) (:files s) (:loc s))))
    
    (println)
    (println "Pairwise overlaps (Jaccard similarity):")
    (doseq [{:keys [pair overlap]} overlaps]
      (let [bar (apply str (repeat (int (* overlap 20)) "█"))]
        (println (format "  %-25s ∩ %-25s = %.2f %s" 
                         (first pair) (second pair) overlap bar))))
    
    (println)
    (println "─────────────────────────────────────────────")
    (println (format "H⁰ (global sections):  %d" H0))
    (println (format "H¹ (obstructions):     %d" H1))
    (println (format "Avg overlap:           %.3f" avg-overlap))
    
    (when (seq global)
      (println)
      (println "Global sections (concepts in ALL skills):")
      (println " " (str/join ", " (take 10 global))))
    
    (when (seq obstructions)
      (println)
      (println "⚠ Weak gluing pairs (H¹ obstructions):")
      (doseq [{:keys [pair]} obstructions]
        (println "  " pair)))
    
    (println)
    (if valid?
      (println "✓ Cocycle condition SATISFIED - skills glue coherently")
      (println "✗ Cohomology obstruction detected - gluing may be inconsistent"))))

(apply -main *command-line-args*)
