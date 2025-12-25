#!/usr/bin/env bb
;; HISTORY COMPOSE: Parse and merge all agent histories
;; Approach 3: Full JSONL parsing with message extraction

(require '[babashka.fs :as fs]
         '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

(def home (System/getProperty "user.home"))

(defn count-jsonl-lines [path]
  (when (fs/exists? path)
    (with-open [rdr (io/reader path)]
      (count (line-seq rdr)))))

(defn parse-jsonl-sample [path n]
  (when (fs/exists? path)
    (with-open [rdr (io/reader path)]
      (->> (line-seq rdr)
           (take n)
           (keep (fn [line]
                   (try (json/parse-string line true)
                        (catch Exception _ nil))))
           (into [])))))

(defn extract-amp-threads []
  (let [dir (str home "/.amp/file-changes")]
    (when (fs/exists? dir)
      (let [files (fs/list-dir dir)]
        {:agent :amp
         :path dir
         :count (count files)
         :sample (take 5 (map fs/file-name files))}))))

(defn extract-claude-history []
  (let [path (str home "/.claude/history.jsonl")]
    (when (fs/exists? path)
      (let [lines (count-jsonl-lines path)
            sample (parse-jsonl-sample path 3)]
        {:agent :claude
         :path path
         :count lines
         :sample-keys (when (first sample) (keys (first sample)))}))))

(defn extract-codex-history []
  (let [path (str home "/.codex/history.jsonl")]
    (when (fs/exists? path)
      (let [lines (count-jsonl-lines path)
            sample (parse-jsonl-sample path 3)]
        {:agent :codex
         :path path
         :count lines
         :sample-keys (when (first sample) (keys (first sample)))}))))

(defn compose-all []
  (let [agents [(extract-amp-threads)
                (extract-claude-history)
                (extract-codex-history)]
        valid (filter some? agents)
        total (reduce + (map :count valid))
        
        ;; GF(3): amp=+1, claude=0, codex=-1
        trit-map {:amp +1 :claude 0 :codex -1}
        weighted (map (fn [a] (* (:count a) (get trit-map (:agent a) 0))) valid)
        gf3-sum (reduce + weighted)]
    
    {:agents valid
     :total-entries total
     :gf3 {:weighted-sum gf3-sum
           :mod3 (mod gf3-sum 3)
           :conserved? (zero? (mod gf3-sum 3))}}))

(defn -main [& _]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║              HISTORY COMPOSE (JSONL Parse)                 ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  
  (let [result (compose-all)]
    (println "\n=== Agent History ===")
    (doseq [a (:agents result)]
      (println (format "  [%s] %d entries at %s"
                       (name (:agent a))
                       (:count a)
                       (:path a)))
      (when (:sample-keys a)
        (println "        Keys:" (str/join ", " (map name (:sample-keys a))))))
    
    (println "\n=== Totals ===")
    (println "  Total entries:" (:total-entries result))
    
    (println "\n=== GF(3) ===")
    (println "  Weighted sum:" (get-in result [:gf3 :weighted-sum]))
    (println "  Mod 3:" (get-in result [:gf3 :mod3]))
    (println "  Conserved:" (get-in result [:gf3 :conserved?]))
    
    result))

(when (= *file* (System/getProperty "babashka.file"))
  (-main))
