#!/usr/bin/env bb
;; WORM - Worming through thread history across agents
;; 
;; Usage: just worm [--screenshot] [--ducklake]
;;
;; Collects thread history from:
;; - Amp (via find_thread API / local ~/.amp)
;; - Claude Code (via ~/.claude/projects/)
;; - Codex (via ~/.codex/)
;; - Warp (via ~/.warp/history/)
;;
;; Stores snapshots in DuckLake for time-travel queries

(require '[babashka.fs :as fs]
         '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.string :as str]
         '[clojure.java.io :as io])

;;; ============================================================
;;; Config
;;; ============================================================

(def home (System/getProperty "user.home"))

(def agent-paths
  {:amp           (str home "/.amp")
   :amp-threads   (str home "/.local/share/amp/threads")
   :amp-history   (str home "/.local/share/amp/history.jsonl")
   :claude-code   (str home "/.claude/projects")
   :codex         (str home "/.codex")
   :warp          (str home "/.warp")})

(def ducklake-path "worm_history.ducklake")
(def data-path "worm_data/")

;;; ============================================================
;;; Thread Discovery
;;; ============================================================

(defn discover-amp-threads []
  (let [amp-dir (:amp agent-paths)]
    (when (fs/exists? amp-dir)
      (let [file-changes (str amp-dir "/file-changes")]
        (when (fs/exists? file-changes)
          (->> (fs/list-dir file-changes)
               (take 100)  ;; limit for performance
               (map (fn [f]
                      {:agent :amp
                       :path (str f)
                       :id (fs/file-name f)
                       :mtime (fs/last-modified-time f)}))
               (into [])))))))

(defn discover-claude-threads []
  (let [claude-dir (str home "/.claude")]
    (when (fs/exists? claude-dir)
      (let [history-file (str claude-dir "/history.jsonl")]
        (if (fs/exists? history-file)
          [{:agent :claude-code
            :path history-file
            :id "history.jsonl"
            :mtime (fs/last-modified-time history-file)}]
          [])))))

(defn discover-codex-threads []
  (let [codex-dir (:codex agent-paths)]
    (when (fs/exists? codex-dir)
      (let [history-file (str codex-dir "/history.jsonl")]
        (if (fs/exists? history-file)
          [{:agent :codex
            :path history-file
            :id "history.jsonl"
            :mtime (fs/last-modified-time history-file)}]
          [])))))

(defn discover-warp-history []
  (let [warp-dir (:warp agent-paths)]
    (when (fs/exists? warp-dir)
      (let [history-file (str warp-dir "/history")]
        (when (fs/exists? history-file)
          [{:agent :warp
            :path history-file
            :id "warp-history"
            :mtime (fs/last-modified-time history-file)}])))))

(defn discover-all-threads []
  (concat
   (or (discover-amp-threads) [])
   (or (discover-claude-threads) [])
   (or (discover-codex-threads) [])
   (or (discover-warp-history) [])))

;;; ============================================================
;;; DuckLake Snapshot
;;; ============================================================

(defn ensure-ducklake! []
  (when-not (fs/exists? ducklake-path)
    (fs/create-dirs data-path)
    (println "Creating DuckLake at" ducklake-path)))

(defn snapshot-to-ducklake! [threads]
  (ensure-ducklake!)
  (let [snapshot-time (java.time.Instant/now)
        snapshot-file (str data-path "snapshot_" (.toEpochMilli snapshot-time) ".json")]
    (spit snapshot-file (json/generate-string
                         {:snapshot_time (str snapshot-time)
                          :thread_count (count threads)
                          :threads (map #(select-keys % [:agent :id :path]) threads)}
                         {:pretty true}))
    (println "Snapshot saved:" snapshot-file)
    snapshot-file))

;;; ============================================================
;;; Screenshot (macOS)
;;; ============================================================

(defn take-screenshot! []
  (let [ts (System/currentTimeMillis)
        path (str data-path "screen_" ts ".png")]
    (fs/create-dirs data-path)
    (try
      (p/shell "screencapture" "-x" path)
      (println "Screenshot:" path)
      path
      (catch Exception e
        (println "Screenshot failed (no display?):" (.getMessage e))
        nil))))

;;; ============================================================
;;; GF(3) Trit Assignment
;;; ============================================================

(defn assign-trit [agent-kw]
  (case agent-kw
    :amp +1
    :claude-code 0
    :codex -1
    :warp 0
    0))

(defn verify-gf3 [threads]
  (let [trits (map #(assign-trit (:agent %)) threads)
        by-agent (frequencies (map :agent threads))
        sum (reduce + trits)
        mod3 (mod sum 3)]
    {:by-agent by-agent
     :trit-sum sum
     :mod3 mod3
     :conserved? (zero? mod3)}))

;;; ============================================================
;;; Main
;;; ============================================================

(defn -main [& args]
  (println "╔════════════════════════════════════════════════════════════╗")
  (println "║                    WORM CONTINUATION                       ║")
  (println "║   Worming through agent thread history                     ║")
  (println "╚════════════════════════════════════════════════════════════╝")
  
  (let [args-set (set args)
        do-screenshot? (contains? args-set "--screenshot")
        threads (discover-all-threads)]
    
    (println "\n=== Discovered Threads ===")
    (println "  Total:" (count threads))
    (doseq [[agent cnt] (frequencies (map :agent threads))]
      (println (format "  %s: %d" (name agent) cnt)))
    
    (println "\n=== GF(3) Verification ===")
    (let [gf3 (verify-gf3 threads)]
      (println "  Trit Sum:" (:trit-sum gf3))
      (println "  Mod 3:" (:mod3 gf3))
      (println "  Conserved:" (:conserved? gf3)))
    
    (println "\n=== DuckLake Snapshot ===")
    (let [snapshot (snapshot-to-ducklake! threads)]
      (println "  Saved to:" snapshot))
    
    (when do-screenshot?
      (println "\n=== Screenshot ===")
      (take-screenshot!))
    
    (println "\n=== Latest Threads (by agent) ===")
    (doseq [[agent agent-threads] (group-by :agent threads)]
      (when-let [latest (first (sort-by :mtime #(compare %2 %1) agent-threads))]
        (println (format "  [%s] %s" (name agent) (:id latest)))))
    
    (println "\nDone.")))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
