#!/usr/bin/env bb
;; Energy Monitor - Correlates trit zones with power consumption
;; Skills: gay-mcp, duckdb-temporal-versioning, unworld

(require '[babashka.process :as p]
         '[clojure.string :as str])

;; ============================================================
;; TRIT DERIVATION (from trit_stream_derive.clj)
;; ============================================================

(defn derive-color [seed step]
  (let [h (mod (+ (* seed 31) (* step 17)) 360)]
    (cond
      (or (< h 60) (>= h 300)) {:trit +1 :name "PLUS" :hue h}
      (< h 180)                 {:trit 0  :name "ERGODIC" :hue h}
      :else                     {:trit -1 :name "MINUS" :hue h})))

;; Agent definitions (avoiding proper nouns)
(def agents
  [{:pid 92720  :name "plus-stream-1" :role :generator}
   {:pid 97274  :name "plus-stream-2" :role :generator}
   {:pid 93268  :name "worker-1"      :role :worker}
   {:pid 5563   :name "worker-2"      :role :worker}
   {:pid 46516  :name "worker-3"      :role :worker}
   {:pid 68569  :name "worker-4"      :role :worker}
   {:pid 51893  :name "worker-5"      :role :worker}
   {:pid 92289  :name "observer"      :role :observer}
   {:pid 94729  :name "actor"         :role :actor}])

;; ============================================================
;; SYSTEM POWER MEASUREMENT
;; ============================================================

(defn get-battery-power []
  "Get instantaneous battery power in mW from IOKit"
  (try
    (let [result (p/shell {:out :string :err :string} "ioreg -r -c AppleSmartBattery")
          output (:out result)
          ;; Parse BatteryPower field
          match (re-find #"\"BatteryPower\" = (\d+)" output)]
      (if match
        (parse-long (second match))
        ;; Fallback: estimate from voltage × amperage
        (let [v-match (re-find #"\"Voltage\" = (\d+)" output)
              a-match (re-find #"\"Amperage\" = (\d+)" output)]
          (if (and v-match a-match)
            (let [v (parse-long (second v-match))
                  a (parse-long (second a-match))]
              ;; Handle signed 64-bit for negative amperage (discharging)
              (if (> a (bit-shift-left 1 62))
                (let [signed-a (- a (bit-shift-left 1 64))]
                  (abs (int (/ (* v signed-a) 1000))))
                (int (/ (* v a) 1000))))
            25000)))) ;; Fallback to 25W estimate
    (catch Exception e
      (println "Battery power error:" (.getMessage e))
      25000)))

(defn get-process-stats []
  "Get CPU% and elapsed time for each agent PID"
  (try
    (let [pids (str/join "," (map :pid agents))
          cmd (format "ps -p %s -o pid,pcpu,etime" pids)
          result (p/shell {:out :string :continue true :err :string} cmd)
          output (:out result)
          lines (rest (str/split-lines output))] ;; Skip header
      (->> lines
           (map str/trim)
           (filter #(not (str/blank? %)))
           (map (fn [line]
                  (let [[pid cpu etime] (str/split (str/trim line) #"\s+")]
                    {:pid (parse-long pid)
                     :cpu (parse-double cpu)
                     :etime etime})))
           (into {}
                 (map (fn [m] [(:pid m) m])))))
    (catch Exception e
      (println "Process stats error:" (.getMessage e))
      {})))

(defn parse-etime [etime]
  "Parse elapsed time string to minutes (handles HH:MM:SS and MM:SS)"
  (let [parts (str/split etime #":")]
    (case (count parts)
      3 (let [[h m s] (map parse-long parts)]
          (+ (* h 60) m (/ s 60.0)))
      2 (let [[m s] (map parse-long parts)]
          (+ m (/ s 60.0)))
      0)))

;; ============================================================
;; ENERGY CALCULATION
;; ============================================================

(defn calculate-energy [agents process-stats system-power-mw]
  "Calculate energy consumption per agent based on CPU share"
  (let [total-cpu (->> process-stats vals (map :cpu) (reduce + 0.0))]
    (for [{:keys [pid name role] :as agent} agents]
      (let [stats (get process-stats pid)
            cpu (or (:cpu stats) 0.0)
            runtime-min (if stats (parse-etime (:etime stats)) 0.0)
            power-share (if (pos? total-cpu) (/ cpu total-cpu) 0.0)
            power-mw (* system-power-mw power-share)
            energy-mwh (* power-mw (/ runtime-min 60))
            energy-j (* energy-mwh 3.6)
            trit (:trit (derive-color pid 0))]  ;; Genesis trit
        {:pid pid
         :name name
         :role role
         :trit trit
         :zone (case trit 1 "PLUS" 0 "ERGODIC" -1 "MINUS")
         :running (boolean stats)
         :cpu cpu
         :runtime-min runtime-min
         :power-mw power-mw
         :energy-j energy-j}))))

;; ============================================================
;; GF(3) ANALYSIS
;; ============================================================

(defn gf3-analysis [measurements]
  "Analyze GF(3) conservation by energy-weighted trits"
  (let [by-zone (group-by :zone measurements)
        zone-energy (into {}
                      (for [[zone agents] by-zone]
                        [zone (reduce + (map :energy-j agents))]))
        total-energy (reduce + (vals zone-energy))
        ;; Weight trits by energy contribution
        weighted-sum (reduce +
                       (for [{:keys [trit energy-j]} measurements]
                         (* trit (if (pos? total-energy)
                                   (/ energy-j total-energy)
                                   0))))]
    {:zone-energy zone-energy
     :total-energy-j total-energy
     :weighted-trit-sum weighted-sum
     :gf3-balanced (< (abs weighted-sum) 0.5)}))

;; ============================================================
;; REAFFERENCE CORRELATION
;; ============================================================

(defn reafference-energy-correlation [measurements step]
  "Check if observer and actor share trit zone at given step"
  (let [obs-trit (:trit (derive-color 92289 step))
        act-trit (:trit (derive-color 94729 step))
        reafference? (= obs-trit act-trit)
        obs-energy (or (:energy-j (first (filter #(= (:pid %) 92289) measurements))) 0)
        act-energy (or (:energy-j (first (filter #(= (:pid %) 94729) measurements))) 0)]
    {:step step
     :observer-trit obs-trit
     :actor-trit act-trit
     :reafference reafference?
     :observer-energy-j obs-energy
     :actor-energy-j act-energy
     :energy-ratio (if (pos? act-energy) (/ obs-energy act-energy) 0)}))

;; ============================================================
;; DUCKDB SQL GENERATION
;; ============================================================

(defn generate-insert-sql [measurements timestamp]
  "Generate SQL INSERT statements for energy_measurements"
  ;; Agent IDs are 0-based to match DuckDB agents table
  (let [agent-ids {"plus-stream-1" 0 "plus-stream-2" 1 "worker-1" 2 "worker-2" 3
                   "worker-3" 4 "worker-4" 5 "worker-5" 6 "observer" 7 "actor" 8}]
    (str/join "\n"
      (for [{:keys [name cpu power-mw energy-j runtime-min]} measurements
            :let [agent-id (get agent-ids name)]]
        (format "INSERT INTO energy_measurements (timestamp, agent_id, cpu_percent, power_mw, energy_j, runtime_min) VALUES ('%s', %d, %.2f, %.2f, %.2f, %.2f);"
                timestamp agent-id cpu power-mw energy-j runtime-min)))))

;; ============================================================
;; MAIN REPORT
;; ============================================================

(defn run-measurement []
  (let [system-power-mw (get-battery-power)
        process-stats (get-process-stats)
        measurements (calculate-energy agents process-stats system-power-mw)
        gf3 (gf3-analysis measurements)
        timestamp (java.time.LocalDateTime/now)]

    (println "╔══════════════════════════════════════════════════════════════╗")
    (println "║              ENERGY × TRIT ZONE CORRELATION                  ║")
    (println "║  Skills: gay-mcp · duckdb-temporal-versioning · unworld      ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println)
    (println (format "Timestamp: %s" timestamp))
    (println (format "System Power: %.1f W" (/ system-power-mw 1000.0)))
    (println)

    (println "┌─────────────────────────────────────────────────────────────────┐")
    (println "│ Agent          │ Zone    │ CPU%  │ Power(W) │ Energy(J) │ Run? │")
    (println "├─────────────────────────────────────────────────────────────────┤")
    (doseq [{:keys [name zone cpu power-mw energy-j running]} measurements]
      (println (format "│ %-14s │ %-7s │ %5.1f │ %8.2f │ %9.0f │ %-4s │"
                       name zone cpu (/ power-mw 1000) energy-j (if running "yes" "no"))))
    (println "└─────────────────────────────────────────────────────────────────┘")
    (println)

    (println "╔══════════════════════════════════════════════════════════════╗")
    (println "║              GF(3) ENERGY CONSERVATION                       ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println (format "PLUS (+1):    %,.0f J" (get (:zone-energy gf3) "PLUS" 0)))
    (println (format "ERGODIC (0):  %,.0f J" (get (:zone-energy gf3) "ERGODIC" 0)))
    (println (format "MINUS (-1):   %,.0f J" (get (:zone-energy gf3) "MINUS" 0)))
    (println (format "Total:        %,.0f J (%.1f Wh)"
                     (:total-energy-j gf3)
                     (/ (:total-energy-j gf3) 3600)))
    (println (format "Weighted Σ:   %.3f" (double (:weighted-trit-sum gf3))))
    (println (format "Balanced?:    %s" (if (:gf3-balanced gf3) "✓ YES" "✗ NO")))
    (println)

    (println "╔══════════════════════════════════════════════════════════════╗")
    (println "║              REAFFERENCE × ENERGY CORRELATION                ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println "Step │ Obs │ Act │ Perception  │ Obs Energy │ Ratio")
    (println "─────┼─────┼─────┼─────────────┼────────────┼──────")
    (doseq [step (range 5)]
      (let [r (reafference-energy-correlation measurements step)]
        (println (format "  %d  │  %+d │  %+d │ %-11s │ %8.0f J │ %.3f"
                         step (:observer-trit r) (:actor-trit r)
                         (if (:reafference r) "REAFFERENCE" "exafference")
                         (double (:observer-energy-j r)) (double (:energy-ratio r))))))
    (println)

    (println "╔══════════════════════════════════════════════════════════════╗")
    (println "║              DUCKDB INSERT SQL                               ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println (generate-insert-sql measurements (str timestamp)))

    {:measurements measurements
     :gf3 gf3
     :timestamp timestamp}))

;; Run if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (run-measurement))
