#!/usr/bin/env bb
;; Sheaf cohomology for a specific group of related skills

(require '[clojure.set :as set]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def SKILLS-ROOT (io/file (System/getProperty "user.home") ".claude" "skills"))

(defn stem [word]
  ;; Simple suffix stripping for conceptual matching
  (-> word
      (str/replace #"(tion|sion|ness|ment|ity|ing|ed|er|ly|al|ous|ive)$" "")))

(defn extract-concepts [skill-name]
  (let [content (slurp (io/file SKILLS-ROOT skill-name "SKILL.md"))]
    (->> (str/split content #"\s+")
         (filter #(> (count %) 5))
         (map str/lower-case)
         (remove #(re-matches #"^[#\-\*\|\`\{\}\[\]<>()]+.*" %))
         (remove #(re-find #"^\d|https?:|file:" %))
         (map stem)
         frequencies
         (sort-by val >)
         (take 25)
         (map first)
         set)))

(defn jaccard [a b]
  (let [i (count (set/intersection a b))
        u (count (set/union a b))]
    (if (zero? u) 0.0 (double (/ i u)))))

(defn verify-sheaf [skill-names]
  (let [n (count skill-names)
        concepts (mapv extract-concepts skill-names)
        overlaps (for [i (range n)
                       j (range (inc i) n)]
                   {:pair [(nth skill-names i) (nth skill-names j)]
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
     :overlaps overlaps}))

(def GROUPS
  {"category" ["discopy" "acsets" "sheaf-cohomology" "operad-compose" "topos-generate"]
   "agent" ["self-evolving-agent" "alife" "godel-machine" "curiosity-driven"]
   "clojure" ["babashka" "cider-clojure" "rama-gay-clojure" "clj-kondo-3color"]})

(defn -main [& args]
  (let [group-name (or (first args) "category")
        skills (get GROUPS group-name (str/split group-name #","))]
    
    (println "╔═══════════════════════════════════════════╗")
    (println "║   Sheaf Cohomology: Skill Group Check     ║")
    (println "╚═══════════════════════════════════════════╝")
    (println)
    (println "Group:" group-name)
    (println "Skills:" (str/join ", " skills))
    (println)
    
    (let [{:keys [valid? H0 H1 avg-overlap global overlaps]} (verify-sheaf skills)]
      (println "Pairwise overlaps:")
      (doseq [{:keys [pair overlap]} (sort-by :overlap > overlaps)]
        (let [bar (apply str (repeat (int (* overlap 30)) "█"))]
          (println (format "  %-20s ∩ %-20s = %.2f %s" 
                           (first pair) (second pair) overlap bar))))
      
      (println)
      (println "─────────────────────────────────────────────")
      (println (format "H⁰ (global sections):  %d" H0))
      (println (format "H¹ (obstructions):     %d" H1))
      (println (format "Avg overlap:           %.3f" avg-overlap))
      
      (when (seq global)
        (println)
        (println "Global sections:")
        (println " " (str/join ", " (take 15 global))))
      
      (println)
      (if valid?
        (println "✓ Cocycle condition SATISFIED")
        (println "✗ Cohomology obstruction detected")))))

(apply -main *command-line-args*)
