(ns dev.metrics-setup
  "REPL-friendly metrics data generation for UI development.
  
  This namespace provides a single function, `setup-metrics-env`, which creates
  a realistic, multi-dimensional, and time-aware dataset for testing and
  developing the analytics UI. It simulates a long-running production environment
  with varied agent behaviors.

  Usage:
    (require '[dev.metrics-setup :as ms])
    ;; This will take a moment to generate all the data, printing progress along the way.
    (def ipc (ms/setup-metrics-env))
    
    ;; Now visit http://localhost:1974 to see the populated analytics UI.
    ;; You can explore different time granularities and use the 'Split by'
    ;; feature with keys like 'user-tier', 'region', and 'ab-test-group'.
    
    ;; When done (optional, can just close the REPL):
    ;; (.close ipc)
    ;; Note: The UI server is not managed by this script, it is assumed to be
    ;; running from `lein repl`."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server])
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:import
   [com.rpl.rama.helpers TopologyUtils]
   [dev.langchain4j.data.message AiMessage UserMessage]
   [dev.langchain4j.model.chat StreamingChatModel]
   [dev.langchain4j.model.chat.response ChatResponse$Builder]
   [dev.langchain4j.model.output TokenUsage]
   [dev.langchain4j.store.embedding
    EmbeddingSearchRequest
    EmbeddingSearchResult
    EmbeddingStore]
   [dev.langchain4j.store.embedding.filter.comparison IsEqualTo]))

;; =============================================================================
;; Mock Objects & Helpers
;; =============================================================================

(def ^:dynamic *ticks* (atom 0))

(defrecord MockChatModel []
  StreamingChatModel
  (doChat [this request handler]
    (let [^UserMessage um (-> request .messages last)
          m (.singleText um)
          o (str m "***")
          response (-> (ChatResponse$Builder.)
                       (.aiMessage (AiMessage. o))
                       (.tokenUsage
                        (TokenUsage. (int (count m))
                                     (int (count o))
                                     (int (+ (count m) (count o) 2))))
                       .build)]
      (TopologyUtils/advanceSimTime 150)
      (when (h/contains-string? m "fail-model")
        (throw (ex-info "fail model" {})))
      (.onPartialResponse handler "abc ")
      (TopologyUtils/advanceSimTime 100)
      (.onPartialResponse handler "def")
      (.onCompleteResponse handler response))))

(deftype MockEmbeddingStore []
  EmbeddingStore
  (add [this embedding]
    (TopologyUtils/advanceSimTime 10)
    "999")
  (search [this request]
    (TopologyUtils/advanceSimTime 15)
    (EmbeddingSearchResult. [])))

(defn- advancer-pred [amt]
  (fn [_] (TopologyUtils/advanceSimTime amt) true))

(defn- minute-millis [i] (* i 1000 po/MINUTE-GRANULARITY))
(defn- hour-millis [i] (* i 1000 po/HOUR-GRANULARITY))
(defn- day-millis [i] (* i 1000 po/DAY-GRANULARITY))

;; =============================================================================
;; Module Definition
;; =============================================================================

(defn create-metrics-gen-module
  "Creates an agent module specifically designed to generate varied metrics data."
  []
  (aor/agentmodule
   [topology]
   (aor/declare-evaluator-builder
    topology "numeric-score" ""
    (fn [params]
      (fn [fetcher input ref-output output]
        {"score" (count (str output))})))

   (aor/declare-evaluator-builder
    topology "conciseness" ""
    (fn [params]
      (fn [fetcher input ref-output output]
        {"is-concise?" (< (count (str output)) 20)})))

   (aor/declare-evaluator-builder
    topology "output-classifier" ""
    (fn [params]
      (fn [fetcher input ref-output output]
        (let [output-str (str output)
              length (count output-str)]
          {"verbosity" (cond
                         (< length 14) "terse"
                         (< length 18) "concise"
                         (< length 25) "moderate"
                         :else "verbose")
           "sentiment" (cond
                         (re-find #"Success|success" output-str) "positive"
                         (re-find #"fail|error|Error" output-str) "negative"
                         :else "neutral")}))))

   (aor/declare-action-builder
    topology "logging-action" "A simple action that logs its execution"
    (fn [params]
      (fn [fetcher input output run-info]
        {"rule-fired" (:rule-name run-info)})))

   (aor/declare-agent-object-builder topology "my-model" (fn [_] (->MockChatModel)))
   (aor/declare-agent-object-builder topology "emb" (fn [_] (MockEmbeddingStore.)))
   (aor/declare-pstate-store topology "$$p" Object)

   (-> topology
       (aor/new-agent "MetricsGenAgent")
       (aor/node
        "start"
        "process"
        (fn [agent-node {:keys [flags delay-ms input] :as params}]
          (when delay-ms (TopologyUtils/advanceSimTime delay-ms))
          (when (contains? flags :model)
            (lc4j/basic-chat (aor/get-agent-object agent-node "my-model") input))
          (aor/emit! agent-node "process" params)))
       (aor/node
        "process"
        nil
        (fn [agent-node {:keys [flags input should-fail?] :as params}]
          (let [p (aor/get-store agent-node "$$p")
                ^EmbeddingStore es (aor/get-agent-object agent-node "emb")]
            (when (contains? flags :store-write)
              (store/pstate-transform! [(advancer-pred 10) (termval "value")] p :key))
            (when (contains? flags :store-read)
              (store/pstate-select-one [:key (advancer-pred 10)] p))
            (when (contains? flags :db-write)
              (.add es (tc/embedding 1.0 2.0)))
            (when (contains? flags :db-read)
              (.search es (EmbeddingSearchRequest. (tc/embedding 0.1 0.3) 5 0.75 (IsEqualTo. "b" 2))))

            (if should-fail?
              (throw (ex-info "Intentional test failure" {}))
              ;; Vary output text to create different sentiments and verbosity levels
              (let [rand-val (rand)
                    output (cond
                             (< rand-val 0.5) (str "Success: " input)              ; 14-16 chars = concise/positive
                             (< rand-val 0.7) (str "Completed task for " input)    ; 24 chars = moderate/neutral
                             (< rand-val 0.85) (str "Error handled for " input)    ; 23 chars = moderate/negative
                             (< rand-val 0.95) (str "Result: " input)              ; 13 chars = terse/neutral
                             :else (str "Successfully processed and validated " input))] ; 42+ chars = verbose/positive
                (aor/result! agent-node output)))))))))

;; =============================================================================
;; Main Setup Function
;; =============================================================================

(defn setup-metrics-env
  "Sets up a rich development environment with varied analytics data.
   Returns the IPC handle."
  [ipc]
  (println "🚀 Starting metrics environment setup...")

  (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                i/hook:analytics-tick (fn [& args] (swap! *ticks* inc))
                anode/gen-node-id (fn [& args] (h/random-uuid7-at-timestamp (h/current-time-millis)))
                anode/log-node-error (fn [& args])
                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))
                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))
                at/gen-new-agent-id (fn [_] (h/random-uuid7-at-timestamp (h/current-time-millis)))]

    (let [_ (TopologyUtils/startSimTime)
          now (System/currentTimeMillis)
          start-time (- now (* 35 24 60 60 1000))
          _ (TopologyUtils/advanceSimTime start-time)
          _ (println "✓ IPC created. Simulated time started 35 days in the past.")

          module (create-metrics-gen-module)
          _ (rtest/launch-module! ipc module {:tasks 2 :threads 2})
          _ (println "✓ Module launched.")

          module-name (get-module-name module)
          agent-manager (aor/agent-manager ipc module-name)
          agent-client (aor/agent-client agent-manager "MetricsGenAgent")
          global-actions-depot (:global-actions-depot (aor-types/underlying-objects agent-manager))
          ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name))
          cycle! (fn []
                   (reset! *ticks* 0)
                   (foreign-append! ana-depot nil)
                   (Thread/sleep 500) ; Wait for analytics to process
                   (def module-name module-name)
                   (rtest/pause-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
                   (rtest/resume-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))]

      (println "✓ Agent manager and analytics depot configured.")

      (aor/create-evaluator! agent-manager "numeric-eval" "numeric-score" {} "")
      (aor/create-evaluator! agent-manager "concise-eval" "conciseness" {} "")
      (aor/create-evaluator! agent-manager "classifier-eval" "output-classifier" {} "")
      (ana/add-rule! global-actions-depot "numeric-rule" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "numeric-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (ana/add-rule! global-actions-depot "concise-rule" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "concise-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (ana/add-rule! global-actions-depot "classifier-rule" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "classifier-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (println "✓ Evaluators and rules created.")

      (println "\n📊 Generating historical and recent data...")
      (let [;; Metadata profiles for segmentation
            profiles [{:metadata {"user-tier" "free", "region" "us-west"}}
                      {:metadata {"user-tier" "premium", "region" "us-west", "ab-test-group" "v1"}}
                      {:metadata {"user-tier" "premium", "region" "eu-central", "ab-test-group" "v2"}}
                      {:metadata {"user-tier" "enterprise", "region" "apac", "ab-test-group" "v1"}}]

            ;; Scaled-down declarative plan for faster setup
            generation-plan [{:label "day", :duration-units :days, :duration 34, :invokes-per-unit 0}
                             {:label "hour", :duration-units :hours, :duration 23, :invokes-per-unit 0}
                             {:label "minute", :duration-units :minutes, :duration 59, :invokes-per-unit 10}]]

        (doseq [{:keys [label duration-units duration invokes-per-unit]} generation-plan]
          (let [time-advancer (case duration-units
                                :days #(TopologyUtils/advanceSimTime (day-millis %))
                                :hours #(TopologyUtils/advanceSimTime (hour-millis %))
                                :minutes #(TopologyUtils/advanceSimTime (minute-millis %)))]
            (dotimes [i duration]
              (time-advancer 1)
              (print (str "\r  - Generating data for " label " " (inc i) "/" duration "..."))
              (flush)
              (dotimes [_ invokes-per-unit]
                (let [profile (rand-nth profiles)
                      ;; 15% of inputs will contain "fail-model" to trigger model failures
                      input-text (if (< (rand) 0.15)
                                   (str "fail-model run-" i)
                                   (str "run-" i))
                      params {:input input-text
                              :flags (cond-> #{}
                                       (> (rand) 0.5) (conj :model)
                                       (> (rand) 0.7) (conj :store-write)
                                       (> (rand) 0.7) (conj :store-read)
                                       (> (rand) 0.8) (conj :db-write)
                                       (> (rand) 0.8) (conj :db-read))
                              :delay-ms (+ 20 (rand-int 100)) ; Shorter delay
                              :should-fail? (< (rand) 0.1)}]
                  (try
                    (aor/agent-invoke-with-context agent-client profile params)
                    (catch Exception _e)))))))
        (println "\n  All agent invocations complete.")

        ;; Run analytics cycle multiple times to process all data
        (println "  Running analytics cycles to process metrics...")
        (dotimes [_ 3] (cycle!))
        (println "  Analytics processing complete."))

      (let [final-time (h/current-time-millis)
            start-bucket (long (/ start-time 60000))
            end-bucket (long (/ final-time 60000))]
        (println "\n✅ Setup complete!")
        (println "   UI is running. If you started with `lein repl`, it's likely at http://localhost:7888")
        (println "   (or the port you specified).")
        (println "   The agent is: MetricsGenAgent")
        (println "   Data spans from bucket" start-bucket "to" end-bucket
                 "(" (- end-bucket start-bucket) "minute buckets).")
        (println "   Use the 'Split by' dropdown with 'user-tier', 'region', or 'ab-test-group'.")
        (println "   Charts for evaluators 'numeric-eval', 'concise-eval', and 'classifier-eval' are also available.")
        (println "   The 'classifier-eval' returns categorical values:")
        (println "     - 'verbosity': terse (~13 chars), concise (14-17), moderate (18-24), verbose (25+)")
        (println "     - 'sentiment': positive (Success), negative (Error), neutral (other)")
        (println "   Perfect for testing categorical metrics with multiple categories!")
        (println "\n   Return value is the IPC handle. Call (.close ipc) when done."))
      ipc)))

(defn start-repl
  [ipc & {:keys [port build-id] :or {port 1974 build-id :frontend}}]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch build-id)
  (aor/start-ui ipc {:port port}))

(comment
  (def ipc (rtest/create-ipc))
  (setup-metrics-env ipc)
  (start-repl ipc)
  (def ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
  (foreign-append! ana-depot nil)
  (rtest/resume-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
  (rtest/pause-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))
