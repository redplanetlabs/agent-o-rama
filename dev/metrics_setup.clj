(ns dev.metrics-setup
  "REPL-friendly, comprehensive metrics data generation for UI development and testing.

  This namespace creates a realistic, time-distributed dataset for the analytics UI,
  simulating an agent that has been running in production for over a month.

  It generates varied data to populate all standard charts, including:
  - Agent success/failure rates.
  - Latency distributions (agent, model, store operations).
  - Token counts for model calls.
  - Custom evaluator scores (for testing dynamic evaluator charts).
  - Rich metadata for testing the 'Split by' functionality.

  Usage:
    (require '[dev.metrics-setup :as ms])
    (def ipc (ms/setup-metrics-env))
    "
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [clojure.string :as str])
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
          o (str "Mock response to: " m)
          response (-> (ChatResponse$Builder.)
                       (.aiMessage (AiMessage. o))
                       (.tokenUsage
                        (TokenUsage. (int (* (count m) 1.5))
                                     (int (* (count o) 2.0))
                                     (int (+ (* (count m) 1.5) (* (count o) 2.0)))))
                       .build)]
      (TopologyUtils/advanceSimTime (+ 50 (rand-int 150)))
      (when (h/contains-string? m "fail-model")
        (throw (ex-info "fail model" {})))
      (.onPartialResponse handler (subs o 0 (min 5 (count o))))
      (TopologyUtils/advanceSimTime (+ 50 (rand-int 100)))
      (.onPartialResponse handler (subs o 5))
      (.onCompleteResponse handler response))))

(deftype MockEmbeddingStore []
  EmbeddingStore
  (add [this embedding]
    (TopologyUtils/advanceSimTime 10)
    "999")
  (search [this request]
    (TopologyUtils/advanceSimTime 15)
    (EmbeddingSearchResult. [])))

(defn advancer-pred [amt]
  (fn [_]
    (TopologyUtils/advanceSimTime amt)
    true))

;; =============================================================================
;; Module Definition with a Configurable Agent and Evaluators
;; =============================================================================

(defn create-metrics-gen-module []
  (aor/agentmodule
   [topology]

   ;; --- Evaluator Builders for Testing ---
   (aor/declare-evaluator-builder
    topology
    "conciseness"
    "Boolean evaluator for output length."
    (fn [params]
      (let [threshold (Long/parseLong (get params "threshold" "50"))]
        (fn [fetcher input ref-output output]
          {"concise?" (< (count (str output)) threshold)}))))

   (aor/declare-evaluator-builder
    topology
    "word-count"
    "Numeric evaluator for word count."
    (fn [params]
      (fn [fetcher input ref-output output]
        {"word-count" (count (str/split (str output) #"\s+"))})))

   ;; --- Agent Objects ---
   (aor/declare-agent-object-builder
    topology "my-model" (fn [setup] (->MockChatModel)))

   (aor/declare-agent-object-builder
    topology "emb" (fn [setup] (MockEmbeddingStore.)))

   (aor/declare-pstate-store topology "$$p" Object)

   ;; --- Agent Definition ---
   (-> topology
       (aor/new-agent "MetricsGenAgent")
       (aor/node
        "start"
        nil
        (fn [agent-node {:keys [delay-ms should-fail? ops input-text]}]
          ;; 1. Simulate Latency
          (when (pos? delay-ms)
            (TopologyUtils/advanceSimTime delay-ms))

          ;; 2. Simulate Failure
          (when should-fail?
            (throw (ex-info "Intentional test failure" {})))

          ;; 3. Perform nested operations
          (when (:model ops)
            (lc4j/basic-chat (aor/get-agent-object agent-node "my-model")
                             (or input-text "default input")))
          (when (:store-write ops)
            (store/pstate-transform! [(advancer-pred 12) (termval "a")]
                                     (aor/get-store agent-node "$$p")
                                     :a))
          (when (:store-read ops)
            (store/pstate-select-one [:a (advancer-pred 14)]
                                     (aor/get-store agent-node "$$p")))
          (when (:db-write ops)
            (.add ^EmbeddingStore (aor/get-agent-object agent-node "emb") (tc/embedding 1.0)))
          (when (:db-read ops)
            (.search ^EmbeddingStore (aor/get-agent-object agent-node "emb")
                     (EmbeddingSearchRequest. (tc/embedding 0.1) 5 0.75 (IsEqualTo. "b" 2))))

          ;; 4. Return result
          (aor/result! agent-node (str "Completed with input: " (or input-text "none"))))))))

;; =============================================================================
;; Data Generation Logic
;; =============================================================================

(defn generate-random-run-config []
  "Generates a random configuration for an agent run, including metadata and behavior."
  {:metadata {"user-tier" (rand-nth ["free" "premium" "enterprise"])
              "region" (rand-nth ["us-west" "us-east" "eu-central" "apac"])
              "ab-test-group" (rand-nth ["prompt-v1" "prompt-v2-experimental"])}
   :behavior {:delay-ms (+ 20 (rand-int 300))
              :should-fail? (< (rand) 0.1) ;; 10% failure rate
              :ops (set (random-sample 0.6 #{:model :store-read :store-write :db-read :db-write}))
              :input-text (str/join " " (repeatedly (+ 2 (rand-int 10))
                                                    #(rand-nth ["a" "b" "c" "d" "e"])))}
   })

(defn run-agent-at [timestamp-ms agent-client {:keys [metadata behavior]}]
  "Sets the simulated time and runs the agent with the given configuration."
  (TopologyUtils/setTimeMillis timestamp-ms)
  (try
    (aor/agent-invoke-with-context agent-client {:metadata metadata} behavior)
    (catch Exception _e
      ;; Expected for failure simulations
      )))

;; =============================================================================
;; Main Setup Function
;; =============================================================================

(defn setup-metrics-env []
  (println "🚀 Starting comprehensive metrics environment setup...")

  (alter-var-root #'*ticks* (constantly (atom 0)))

  (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                i/hook:analytics-tick (fn [& args] (swap! *ticks* inc))
                anode/gen-node-id (fn [& args] (h/random-uuid7-at-timestamp (h/current-time-millis)))
                anode/log-node-error (fn [& args])
                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))
                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))
                at/gen-new-agent-id (fn [agent-name] (h/random-uuid7-at-timestamp (h/current-time-millis)))]

    (let [ipc (rtest/create-ipc)
          _ (TopologyUtils/startSimTime)
          now (System/currentTimeMillis)
          start-of-sim-time (- now (* 40 24 60 60 1000)) ; 40 days ago
          _ (TopologyUtils/advanceSimTime start-of-sim-time)
          _ (println "✓ IPC created and simulated time started 40 days in the past.")

          module (create-metrics-gen-module)
          _ (rtest/launch-module! ipc module {:tasks 2 :threads 2})
          _ (println "✓ Module launched.")

          module-name (get-module-name module)
          agent-manager (aor/agent-manager ipc module-name)
          global-actions-depot (:global-actions-depot (aor-types/underlying-objects agent-manager))
          agent-client (aor/agent-client agent-manager "MetricsGenAgent")
          ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name))
          cycle! (fn []
                   (reset! *ticks* 0)
                   (foreign-append! ana-depot nil)
                   (Thread/sleep 500)
                   (rtest/pause-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
                   (rtest/resume-microbatch-topology! ipc module-name aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))]

      ;; Create evaluators and rules BEFORE generating data
      (aor/create-evaluator! agent-manager "concise-eval" "conciseness" {"threshold" "30"} "")
      (aor/create-evaluator! agent-manager "word-count-eval" "word-count" {} "")
      (ana/add-rule! global-actions-depot "rule-concise" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "concise-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (ana/add-rule! global-actions-depot "rule-word-count" "MetricsGenAgent"
                     {:action-name "aor/eval", :action-params {"name" "word-count-eval"}, :filter (aor-types/->AndFilter []), :sampling-rate 1.0, :start-time-millis 0, :status-filter :success})
      (println "✓ Evaluators and rules created.")

      ;; Generate historical data
      (println "📊 Generating historical data...")
      (doseq [days-ago (reverse (range 1 41))]
        (let [day-start-ms (- now (* days-ago 24 60 60 1000))
              num-invokes (+ 5 (rand-int (* (- 41 days-ago) 5)))] ; More invocations for recent days
          (when (zero? (mod days-ago 5))
            (println "  ...generating data for" days-ago "days ago (" num-invokes "invokes)"))
          (dotimes [_ num-invokes]
            (let [timestamp (+ day-start-ms (rand-int (* 24 60 60 1000)))
                  config (generate-random-run-config)]
              (run-agent-at timestamp agent-client config)))))

      ;; Generate recent, high-density data for the last day
      (println "📊 Generating recent high-density data for the last 24 hours...")
      (let [day-start-ms (- now (* 1 24 60 60 1000))]
        (dotimes [_ 200]
          (let [timestamp (+ day-start-ms (rand-int (* 24 60 60 1000)))
                config (generate-random-run-config)]
            (run-agent-at timestamp agent-client config))))

      ;; Force some specific scenarios for predictable testing
      (println "📊 Generating specific scenarios...")
      (let [current-time (- now (* 5 60 1000))] ; 5 minutes ago
        ;; A very slow, successful run with all ops
        (run-agent-at current-time agent-client
                      {:metadata {"user-tier" "enterprise", "region" "us-west", "scenario" "slow-success"}
                       :behavior {:delay-ms 1200, :should-fail? false, :ops #{:model :store-read :store-write :db-read :db-write}, :input-text "long query text for token testing"}})
        ;; A fast, failing run
        (run-agent-at (+ current-time 1000) agent-client
                      {:metadata {"user-tier" "free", "region" "eu-central", "scenario" "fast-fail"}
                       :behavior {:delay-ms 10, :should-fail? true, :ops #{:model}}}))

      (println "✓ All data generated.")
      (println "⚙️ Running analytics processing cycles...")
      (cycle!)
      (cycle!)
      (println "✓ Analytics processed.")

      ;; Start UI at the end
      ipc)))
