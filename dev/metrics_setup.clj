(ns dev.metrics-setup
  "REPL-friendly metrics data generation for UI development.
  
  This namespace provides a single function, `setup-metrics-env`, which creates
  a realistic, multi-dimensional, and time-aware dataset for testing and
  developing the analytics UI. It simulates a long-running production environment
  with varied agent behaviors.

  Usage:
    (require '[dev.metrics-setup :as ms])
    ;; This will take a moment to generate all the data.
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
   [com.rpl.test-common :as tc])
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
   [dev.langchain4j.store.embedding.filter.comparison IsEqualTo]
   [java.util.concurrent CompletableFuture]))

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

   ;; Evaluator builders for testing evaluator charts
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

   ;; Action builder to confirm rules are firing
   (aor/declare-action-builder
    topology
    "logging-action"
    "A simple action that logs its execution"
    (fn [params]
      (fn [fetcher input output run-info]
        {"rule-fired" (:rule-name run-info)})))

   ;; Declare mock objects for agent to use
   (aor/declare-agent-object-builder topology "my-model" (fn [_] (->MockChatModel)))
   (aor/declare-agent-object-builder topology "emb" (fn [_] (MockEmbeddingStore.)))
   (aor/declare-pstate-store topology "$$p" Object)

   ;; The agent itself, configurable via input params to generate varied runs
   (-> topology
       (aor/new-agent "MetricsGenAgent")
       (aor/node
        "start"
        "process"
        (fn [agent-node {:keys [flags delay-ms should-fail?] :as params}]
          (when delay-ms (TopologyUtils/advanceSimTime delay-ms))

          (when (contains? flags :model)
            (lc4j/basic-chat (aor/get-agent-object agent-node "my-model") "test-prompt"))
          (aor/emit! agent-node "process" params)))
       (aor/node
        "process"
        nil
        (fn [agent-node {:keys [flags input] :as params}]
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

            (if (:should-fail? params)
              (throw (ex-info "Intentional test failure" {}))
              (aor/result! agent-node (str "Success: " input)))))))))
  

;; =============================================================================
;; Main Setup Function
;; =============================================================================

(defn setup-metrics-env
  "Sets up a rich development environment with varied analytics data.
   Returns the IPC handle."
  []
  (println "🚀 Starting metrics environment setup...")

  (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                i/hook:analytics-tick (fn [& args] (swap! *ticks* inc))
                anode/gen-node-id (fn [& args] (h/random-uuid7-at-timestamp (h/current-time-millis)))
                anode/log-node-error (fn [& args])
                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))
                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))
                at/gen-new-agent-id (fn [_] (h/random-uuid7-at-timestamp (h/current-time-millis)))]

    (let [ipc (rtest/create-ipc)
          _ (TopologyUtils/startSimTime)
          ;; Start simulation 35 days ago to have a rich history
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
                   (rtest/pause-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
                   (rtest/resume-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))]

      (println "✓ Agent manager and analytics depot configured.")

      ;; Create evaluators and rules
      (aor/create-evaluator! agent-manager "numeric-eval" "numeric-score" {} "")
      (aor/create-evaluator! agent-manager "concise-eval" "conciseness" {} "")
      (ana/add-rule! global-actions-depot "numeric-rule" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "numeric-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (ana/add-rule! global-actions-depot "concise-rule" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "concise-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (println "✓ Evaluators and rules created.")

      (println "\n📊 Generating historical and recent data...")
      (let [;; Metadata profiles for segmentation
            profiles [{:metadata {"user-tier" "free", "region" "us-west"}}
                      {:metadata {"user-tier" "premium", "region" "us-west", "ab-test-group" "v1"}}
                      {:metadata {"user-tier" "premium", "region" "eu-central", "ab-test-group" "v2"}}
                      {:metadata {"user-tier" "enterprise", "region" "apac", "ab-test-group" "v1"}}]

            ;; Scaled-down declarative plan for data generation
            generation-plan [{:duration-units :days, :duration 34, :invokes-per-unit 1}
                             {:duration-units :hours, :duration 23, :invokes-per-unit 2}
                             {:duration-units :minutes, :duration 59, :invokes-per-unit 5}]
            
            initiation-futures (atom [])]

        (doseq [{:keys [duration-units duration invokes-per-unit]} generation-plan]
          (let [time-advancer (case duration-units
                                :days #(TopologyUtils/advanceSimTime (day-millis %))
                                :hours #(TopologyUtils/advanceSimTime (hour-millis %))
                                :minutes #(TopologyUtils/advanceSimTime (minute-millis %)))]
            (dotimes [_ duration]
              (time-advancer 1)
              (dotimes [i invokes-per-unit]
                (let [profile (rand-nth profiles)
                      params {:input (str "run-" i)
                              :flags (cond-> #{}
                                       (> (rand) 0.5) (conj :model)
                                       (> (rand) 0.8) (conj :store-write)
                                       (> (rand) 0.9) (conj :db-read))
                              :delay-ms (+ 20 (rand-int 200))
                              :should-fail? (< (rand) 0.1)}]
                  (swap! initiation-futures conj
                         (aor/agent-initiate-with-context-async agent-client profile params)))))))

        ;; Wait for all agent INITIATIONS to complete
        (println "  Waiting for" (count @initiation-futures) "agent initiations to complete...")
        (let [agent-invokes (mapv deref @initiation-futures)]
          (println "  All agents initiated. Now waiting for executions to finish...")

          ;; Wait for all agent EXECUTIONS to complete
          (let [result-futures (mapv #(aor/agent-result-async agent-client %) agent-invokes)]
            (.get (CompletableFuture/allOf (into-array CompletableFuture result-futures)))
            (println "  All agent executions complete."))))
        
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
        (println "   Charts for evaluators 'numeric-eval' and 'concise-eval' are also available.")
        (println "\n   Return value is the IPC handle. Call (.close ipc) when done."))
      ipc))