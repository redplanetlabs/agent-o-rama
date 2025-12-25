#!/usr/bin/env bb
;; Trit-Stream LLaDA Coordinator
;; ==============================
;; Babashka script that coordinates phase-aware diffusion LLM inference
;; Integrates with Python MLX scripts and MCP tools
;;
;; Usage:
;;   bb trit_llada_coordinator.clj generate "Your prompt"
;;   bb trit_llada_coordinator.clj schedule
;;   bb trit_llada_coordinator.clj integrated "Your prompt"
;;   bb trit_llada_coordinator.clj compare "Your prompt"

(require '[babashka.process :as p]
         '[clojure.string :as str]
         '[cheshire.core :as json])

;; ════════════════════════════════════════════════════════════════════════════
;; TRIT-STREAM CORE
;; ════════════════════════════════════════════════════════════════════════════

(def config
  {:angular-velocity 17
   :genesis-multiplier 31
   :observer-idx 7
   :actor-idx 8})

(def default-pids [92720 97274 93268 5563 46516 68569 51893 92289 94729])
(def agent-names ["ps1" "ps2" "w1" "w2" "w3" "w4" "w5" "obs" "act"])

(defn derive-hue [seed step]
  (mod (+ (* (:genesis-multiplier config) seed)
          (* (:angular-velocity config) step))
       360))

(defn hue->trit [hue]
  (let [h (mod hue 360)]
    (cond
      (or (< h 60) (>= h 300)) +1   ; PLUS
      (< h 180)                 0   ; ERGODIC
      :else                    -1))) ; MINUS

(defn derive-trit [seed step]
  (hue->trit (derive-hue seed step)))

(defn trit->name [t]
  (case t
    +1 "PLUS"
    0  "ERGODIC"
    -1 "MINUS"
    "UNKNOWN"))

(defn gf3-balanced? [trits]
  (zero? (mod (reduce + trits) 3)))

(defn reafferent? [pids step]
  (let [obs-trit (derive-trit (nth pids (:observer-idx config)) step)
        act-trit (derive-trit (nth pids (:actor-idx config)) step)]
    (= obs-trit act-trit)))

(defn integrated? [pids step]
  (let [trits (mapv #(derive-trit % step) pids)]
    (and (gf3-balanced? trits)
         (reafferent? pids step))))

(defn get-phase [step]
  (let [pids default-pids
        obs-trit (derive-trit (nth pids (:observer-idx config)) step)
        is-integrated (integrated? pids step)]
    {:step step
     :phase (trit->name obs-trit)
     :trit obs-trit
     :integrated? is-integrated
     :confidence (if is-integrated "HIGH" "NORMAL")}))

;; ════════════════════════════════════════════════════════════════════════════
;; PHASE-AWARE PARAMETERS
;; ════════════════════════════════════════════════════════════════════════════

(def phase-params
  {+1 {:temperature 0.9 :top-p 0.95 :mode "creative"}
   0  {:temperature 0.7 :top-p 0.90 :mode "exploratory"}
   -1 {:temperature 0.4 :top-p 0.80 :mode "conservative"}})

(defn get-params [step]
  (let [phase (get-phase step)
        params (get phase-params (:trit phase))]
    (merge phase params)))

;; ════════════════════════════════════════════════════════════════════════════
;; MODEL REGISTRY
;; ════════════════════════════════════════════════════════════════════════════

(def models
  {:llada {:path "mlx-community/LLaDA2.0-flash-8bit"
           :type :diffusion
           :ready? true}
   :dream {:path "Dream-org/Dream-v0-Instruct-7B"
           :type :diffusion
           :ready? false}
   :dr-tulu {:path "Plurigrid/DR-Tulu-8B-MLX-8bit"
             :type :autoregressive
             :ready? true}
   :qwen {:path "mlx-community/Qwen2.5-7B-Instruct-4bit"
          :type :autoregressive
          :ready? true}})

;; ════════════════════════════════════════════════════════════════════════════
;; GENERATION COMMANDS
;; ════════════════════════════════════════════════════════════════════════════

(defn build-generate-cmd [model prompt params max-tokens]
  (let [model-info (get models model)
        path (:path model-info)]
    ["python3" "-m" "mlx_lm.generate"
     "--model" path
     "--prompt" prompt
     "--temp" (str (:temperature params))
     "--top-p" (str (:top-p params))
     "--max-tokens" (str max-tokens)]))

(defn generate!
  "Generate text with phase-aware parameters"
  [prompt & {:keys [model step max-tokens dry-run?]
             :or {model :llada step 0 max-tokens 256 dry-run? true}}]
  (let [params (get-params step)
        cmd (build-generate-cmd model prompt params max-tokens)]

    (println "╔══════════════════════════════════════════════════════════════╗")
    (println "║           TRIT-STREAM LLADA COORDINATOR                      ║")
    (println "╚══════════════════════════════════════════════════════════════╝")
    (println)
    (println (format "Model:       %s (%s)" (name model) (name (:type (get models model)))))
    (println (format "Step:        %d" step))
    (println (format "Phase:       %s (trit=%+d)" (:phase params) (:trit params)))
    (println (format "INTEGRATED:  %s" (:integrated? params)))
    (println (format "Confidence:  %s" (:confidence params)))
    (println)
    (println (format "Temperature: %.2f" (:temperature params)))
    (println (format "Top-p:       %.2f" (:top-p params)))
    (println (format "Mode:        %s" (:mode params)))
    (println)
    (println "Command:")
    (println (format "  %s" (str/join " " cmd)))
    (println)

    (if dry-run?
      (println "[Dry run - command not executed]")
      (do
        (println "Executing...")
        (let [result (p/shell {:out :string :err :string} cmd)]
          (println (:out result))
          (when (not= 0 (:exit result))
            (println "Error:" (:err result))))))

    {:model model
     :params params
     :command cmd}))

(defn find-next-integrated [start-step max-search]
  (loop [step start-step]
    (cond
      (>= step (+ start-step max-search)) nil
      (integrated? default-pids step) step
      :else (recur (inc step)))))

(defn generate-at-integrated!
  "Wait for INTEGRATED state then generate"
  [prompt & {:keys [model start-step max-tokens max-wait]
             :or {model :llada start-step 0 max-tokens 256 max-wait 50}}]
  (println "Searching for INTEGRATED state...")
  (if-let [int-step (find-next-integrated start-step max-wait)]
    (do
      (println (format "Found INTEGRATED at step %d (waited %d steps)"
                       int-step (- int-step start-step)))
      (println)
      (generate! prompt :model model :step int-step :max-tokens max-tokens))
    (println (format "No INTEGRATED state found in %d steps" max-wait))))

;; ════════════════════════════════════════════════════════════════════════════
;; SCHEDULE DISPLAY
;; ════════════════════════════════════════════════════════════════════════════

(defn display-schedule [start-step num-steps]
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║              TRIT-STREAM PHASE SCHEDULE                      ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  (println)
  (println "Step | Phase   | Int | Temp | Mode")
  (println "-----|---------|-----|------|-------------")

  (let [steps (range start-step (+ start-step num-steps))
        integrated-steps (filterv #(integrated? default-pids %) steps)]

    (doseq [step steps]
      (let [params (get-params step)
            marker (if (:integrated? params) "★" " ")]
        (println (format "%4d | %-7s | %s   | %.1f  | %s"
                         step
                         (:phase params)
                         marker
                         (:temperature params)
                         (:mode params)))))

    (println)
    (println (format "INTEGRATED steps: %s" (vec integrated-steps)))
    (println (format "Duty cycle: %.1f%%" (* 100.0 (/ (count integrated-steps) num-steps))))))

;; ════════════════════════════════════════════════════════════════════════════
;; MODEL COMPARISON
;; ════════════════════════════════════════════════════════════════════════════

(defn compare-models [prompt step model-list]
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║              MODEL COMPARISON                                ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  (println)
  (println (format "Prompt: \"%s\"" prompt))
  (println (format "Step: %d" step))
  (let [params (get-params step)]
    (println (format "Phase: %s (%s)" (:phase params) (:confidence params))))
  (println)
  (println "Models:")
  (doseq [model-key model-list]
    (let [model-info (get models model-key)
          params (get-params step)
          cmd (build-generate-cmd model-key prompt params 128)]
      (println (format "  %s (%s):" (name model-key) (name (:type model-info))))
      (println (format "    %s" (str/join " " cmd)))
      (println))))

;; ════════════════════════════════════════════════════════════════════════════
;; CLI
;; ════════════════════════════════════════════════════════════════════════════

(defn print-usage []
  (println "Usage:")
  (println "  bb trit_llada_coordinator.clj generate \"prompt\"    - Generate with LLaDA")
  (println "  bb trit_llada_coordinator.clj integrated \"prompt\"  - Wait for INTEGRATED")
  (println "  bb trit_llada_coordinator.clj schedule              - Show phase schedule")
  (println "  bb trit_llada_coordinator.clj compare \"prompt\"     - Compare models")
  (println "  bb trit_llada_coordinator.clj phase [step]          - Show phase at step"))

(defn -main [& args]
  (let [[cmd & rest-args] args]
    (case cmd
      "generate"
      (let [prompt (first rest-args)]
        (if prompt
          (generate! prompt :model :llada :step 0)
          (println "Error: prompt required")))

      "integrated"
      (let [prompt (first rest-args)]
        (if prompt
          (generate-at-integrated! prompt :model :llada)
          (println "Error: prompt required")))

      "schedule"
      (let [start (if-let [s (first rest-args)] (parse-long s) 0)]
        (display-schedule start 21))

      "compare"
      (let [prompt (first rest-args)]
        (if prompt
          (compare-models prompt 0 [:llada :dr-tulu :qwen])
          (println "Error: prompt required")))

      "phase"
      (let [step (if-let [s (first rest-args)] (parse-long s) 0)
            phase (get-phase step)]
        (println (format "Step %d: %s (trit=%+d) INTEGRATED=%s"
                         step (:phase phase) (:trit phase) (:integrated? phase))))

      ;; Default
      (print-usage))))

;; Run if executed directly
(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

;; ════════════════════════════════════════════════════════════════════════════
;; REPL EXAMPLES
;; ════════════════════════════════════════════════════════════════════════════

(comment
  ;; Show phase at step 0
  (get-phase 0)
  ;; => {:step 0, :phase "PLUS", :trit 1, :integrated? false, :confidence "NORMAL"}

  ;; Find next INTEGRATED step
  (find-next-integrated 0 50)
  ;; => 2

  ;; Get parameters for INTEGRATED step
  (get-params 2)
  ;; => {:step 2, :phase "ERGODIC", :trit 0, :integrated? true, :confidence "HIGH",
  ;;     :temperature 0.7, :top-p 0.9, :mode "exploratory"}

  ;; Display schedule
  (display-schedule 0 21)

  ;; Generate (dry run)
  (generate! "Explain diffusion LLMs" :model :llada :step 2)

  ;; Compare models
  (compare-models "What is consciousness?" 0 [:llada :dr-tulu])
  )
