(ns probe
  "Fast-fail capability probe - test everything in parallel"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]))

(defn probe-env []
  {:openai (boolean (System/getenv "OPENAI_API_KEY"))
   :tavily (boolean (System/getenv "TAVILY_API_KEY"))
   :anthropic (boolean (System/getenv "ANTHROPIC_API_KEY"))})

(defn probe-skills []
  (let [root (io/file (System/getProperty "user.home") ".claude" "skills")
        dirs (filter #(.isDirectory %) (.listFiles root))]
    {:count (count dirs)
     :top-3 (->> dirs
                 (map (fn [d] {:name (.getName d)
                               :files (count (filter #(.isFile %) (file-seq d)))}))
                 (sort-by :files >)
                 (take 3)
                 (mapv :name))}))

(defn probe-classes []
  (letfn [(has? [c] (try (Class/forName c) true (catch Exception _ false)))]
    {:rama (has? "com.rpl.rama.Depot")
     :langchain4j (has? "dev.langchain4j.model.openai.OpenAiChatModel")
     :tavily-client (has? "dev.langchain4j.web.search.tavily.TavilyWebSearchEngine")
     :jsonista (has? "jsonista.core")
     :httpkit (has? "org.httpkit.client")}))

(defn probe-files []
  (let [root (io/file "examples/clj/src/com/rpl/agent")]
    {:agent-files (count (filter #(str/ends-with? (.getName %) ".clj") 
                                 (.listFiles root)))
     :asi-agent (.exists (io/file root "asi_agent.clj"))}))

(defn run-probe []
  (println "╔═══════════════════════════════════════╗")
  (println "║      CAPABILITY PROBE (fast-fail)     ║")
  (println "╚═══════════════════════════════════════╝")
  (println)
  
  (let [env (probe-env)
        skills (probe-skills)
        classes (probe-classes)
        files (probe-files)]
    
    (println "ENV VARS:")
    (doseq [[k v] env]
      (println (format "  %-12s %s" (name k) (if v "✓" "✗"))))
    
    (println "\nSKILLS:")
    (println (format "  count        %d" (:count skills)))
    (println (format "  top-3        %s" (str/join ", " (:top-3 skills))))
    
    (println "\nCLASSPATH:")
    (doseq [[k v] classes]
      (println (format "  %-12s %s" (name k) (if v "✓" "✗"))))
    
    (println "\nFILES:")
    (doseq [[k v] files]
      (println (format "  %-12s %s" (name k) v)))
    
    (println "\n─────────────────────────────────────────")
    (let [ready? (and (:openai env) (:rama classes) (:langchain4j classes))]
      (println (if ready? 
                 "STATUS: READY TO RUN"
                 "STATUS: MISSING REQUIREMENTS"))
      (when-not (:openai env)
        (println "  → Set OPENAI_API_KEY"))
      (when-not (:rama classes)
        (println "  → Run with lein (not clj)"))
      ready?)))

(defn -main [& _]
  (run-probe))
