(ns dev.metrics-setup
  "REPL-friendly metrics data generation for UI development.
  
  Usage:
    (require '[dev.metrics-setup :as ms])
    (def ipc (ms/setup-metrics-env))
    ;; Now visit http://localhost:1974 to see the populated analytics UI
    ;; When done:
    (com.rpl.agent-o-rama/stop-ui)
    (.close ipc)"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
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

(defn advancer-pred
  "Helper to advance simulated time and return true (for use in pstate predicates)"
  [amt]
  (fn [_]
    (TopologyUtils/advanceSimTime amt)
    true))

(defn minute-millis [i]
  (* i 1000 po/MINUTE-GRANULARITY))

(defn hour-millis [i]
  (* i 1000 po/HOUR-GRANULARITY))

(defn day-millis [i]
  (* i 1000 po/DAY-GRANULARITY))

;; =============================================================================
;; Module Definition
;; =============================================================================

(defn create-metrics-gen-module
  "Creates an agent module specifically designed to generate varied metrics data."
  []
  (aor/agentmodule
   [topology]

   ;; Declare evaluator builder for generating eval metrics
   (aor/declare-evaluator-builder
    topology
    "my-eval"
    ""
    (fn [params]
      (fn [fetcher input ref-output output]
        {"score-a" (count (first input))
         "score-b" (+ (count output) 0.5)})))

   ;; Declare mock model
   (aor/declare-agent-object-builder
    topology
    "my-model"
    (fn [setup] (->MockChatModel)))

   ;; Declare mock embedding store
   (aor/declare-agent-object-builder
    topology
    "emb"
    (fn [setup] (MockEmbeddingStore.)))

   ;; Declare dummy PState for store operations
   (aor/declare-pstate-store
    topology
    "$$p"
    Object)

   ;; Define the metrics generation agent
   (-> topology
       (aor/new-agent "MetricsGenAgent")
       (aor/node
        "start"
        "process"
        (fn [agent-node input flags]
          (TopologyUtils/advanceSimTime 3)
          (let [p (aor/get-store agent-node "$$p")]
            (when (contains? flags :store-write)
              (store/pstate-transform! [(advancer-pred 12) (termval "a")]
                                       p
                                       :a))
            (when (contains? flags :model)
              (lc4j/basic-chat (aor/get-agent-object agent-node "my-model") ".")
              (lc4j/basic-chat (aor/get-agent-object agent-node "my-model") input))
            (aor/emit! agent-node "process" (str input "!") flags))))
       (aor/node
        "process"
        nil
        (fn [agent-node input flags]
          (let [^EmbeddingStore es (aor/get-agent-object agent-node "emb")
                p (aor/get-store agent-node "$$p")]
            (when (contains? flags :db-write)
              (.add es (tc/embedding 1.0 2.0)))
            (when (contains? flags :db-read)
              (.search es
                       (EmbeddingSearchRequest. (tc/embedding 0.1 0.3)
                                                (int 5)
                                                0.75
                                                (IsEqualTo. "b" 2))))
            (when (contains? flags :db-write)
              (.add es (tc/embedding 1.0 2.0)))
            (when (contains? flags :store-read)
              (store/pstate-select-one [:a (advancer-pred 14)] p))
            (if (= input "fail!")
              (throw (ex-info "fail" {}))
              (aor/result! agent-node (str input "?")))))))))

;; =============================================================================
;; Main Setup Function
;; =============================================================================

(defn setup-metrics-env
  "Sets up a complete development environment with rich analytics data.
  
  Returns the IPC handle for continued interaction.
  
  This function will:
  1. Start simulated time
  2. Create an IPC and launch the metrics generation module
  3. Start the UI on port 1974
  4. Generate varied metrics data across different time buckets
  5. Return the IPC handle
  
  Example usage:
    (def ipc (setup-metrics-env))
    ;; Visit http://localhost:1974
    ;; When done:
    (aor/stop-ui)
    (.close ipc)"
  []
  (println "🚀 Starting metrics environment setup...")

  ;; Set up dynamic redefinitions for controlled test environment
  (alter-var-root #'*ticks* (constantly (atom 0)))

  (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! *ticks* inc))

                anode/gen-node-id
                (fn [& args]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))

                anode/log-node-error (fn [& args])

                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))

                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))

                at/gen-new-agent-id
                (fn [agent-name]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))]

    (let [ipc (rtest/create-ipc)
          _ (TopologyUtils/startSimTime)
          ;; Advance simulated time to current time minus 30 minutes
          ;; UI queries for last 60 minutes, so all our data will be in that window
          now (System/currentTimeMillis)
          thirty-mins-ago (- now (* 30 60 1000))
          _ (TopologyUtils/advanceSimTime thirty-mins-ago)
          _ (println "✓ IPC created and simulated time started at" thirty-mins-ago)
          _ (println "  Current bucket:" (long (/ now 60000))
                     "| Data starts at bucket:" (long (/ thirty-mins-ago 60000)))

          module (create-metrics-gen-module)
          _ (println "✓ Module created")

          _ (rtest/launch-module! ipc module {:tasks 2 :threads 2})
          _ (println "✓ Module launched")

          module-name (get-module-name module)
          agent-manager (aor/agent-manager ipc module-name)
          _ (println "✓ Agent manager created")

          global-actions-depot (:global-actions-depot
                                (aor-types/underlying-objects agent-manager))
          agent-client (aor/agent-client agent-manager "MetricsGenAgent")
          ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name))

          ;; Helper to cycle analytics
          cycle! (fn []
                   (reset! *ticks* 0)
                   (foreign-append! ana-depot nil)
                   ;; Wait for analytics to process
                   (Thread/sleep 500)
                   (rtest/pause-microbatch-topology! ipc
                                                     module-name
                                                     aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
                   (rtest/resume-microbatch-topology! ipc
                                                      module-name
                                                      aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))]

      (println "✓ Agent client and analytics depot configured")

      ;; Create evaluators
      (aor/create-evaluator! agent-manager
                             "concise5"
                             "aor/conciseness"
                             {"threshold" "5"}
                             "")
      (aor/create-evaluator! agent-manager
                             "eval1"
                             "my-eval"
                             {}
                             "")
      (println "✓ Evaluators created")

      ;; Add rules
      (ana/add-rule! global-actions-depot
                     "rule1"
                     "MetricsGenAgent"
                     {:action-name "aor/eval"
                      :action-params {"name" "concise5"}
                      :filter (aor-types/->AndFilter [])
                      :sampling-rate 1.0
                      :start-time-millis 0
                      :status-filter :success})
      (ana/add-rule! global-actions-depot
                     "rule2"
                     "MetricsGenAgent"
                     {:action-name "aor/eval"
                      :action-params {"name" "eval1"}
                      :filter (aor-types/->AndFilter [])
                      :sampling-rate 1.0
                      :start-time-millis 0
                      :status-filter :success})
      (println "✓ Rules added")

      ;; ========================================================================
      ;; MINUTE 0 - First batch of invocations
      ;; ========================================================================
      (println "\n📊 Generating data for minute 0...")
      (aor/agent-invoke-with-context agent-client
                                     {:metadata {"user-id" "alice" "region" "us-west"}}
                                     "ab"
                                     #{:model :store-read :store-write :db-read :db-write})
      (aor/agent-invoke-with-context agent-client
                                     {:metadata {"user-id" "bob" "region" "us-east"}}
                                     "..."
                                     #{:model :store-read :db-read})
      (try
        (aor/agent-invoke-with-context agent-client
                                       {:metadata {"user-id" "charlie" "region" "eu-west"}}
                                       "fail"
                                       #{})
        (catch Exception _e
          (println "  (Expected failure for charlie)")))

      (cycle!)
      (cycle!)
      (println "✓ Minute 0 data generated")

      ;; ========================================================================
      ;; MINUTE 1 - More varied data
      ;; ========================================================================
      (TopologyUtils/advanceSimTime (minute-millis 1))
      (println "\n📊 Generating data for minute 1...")
      (aor/agent-invoke agent-client "abc" #{:store-write :db-read :db-write})
      (aor/agent-invoke-with-context agent-client
                                     {:metadata {"user-id" "dave" "region" "ap-south"}}
                                     "eeeee"
                                     #{:model})

      (cycle!)
      (cycle!)
      (println "✓ Minute 1 data generated")

      ;; ========================================================================
      ;; MINUTE 2 - Model failure
      ;; ========================================================================
      (TopologyUtils/advanceSimTime (minute-millis 1))
      (println "\n📊 Generating data for minute 2...")
      (try
        (aor/agent-invoke agent-client "fail-model" #{:model})
        (catch Exception _e
          (println "  (Expected model failure)")))

      (cycle!)
      (cycle!)
      (println "✓ Minute 2 data generated")

      ;; ========================================================================
      ;; MINUTE 3 - Lots of varied metadata
      ;; ========================================================================
      (TopologyUtils/advanceSimTime (minute-millis 1))
      (println "\n📊 Generating data for minute 3...")
      (doseq [i (range 15)]
        (let [s (apply str (repeat i "."))]
          (aor/agent-invoke-with-context agent-client
                                         {:metadata {"iteration" (str i)
                                                     "batch" "A"}}
                                         s
                                         #{})))

      (cycle!)
      (cycle!)
      (println "✓ Minute 3 data generated (15 invocations with varied metadata)")

      ;; ========================================================================
      ;; Continue generating more recent minute-level data
      ;; ========================================================================
      (println "\n📊 Generating more recent data across multiple minutes...")
      
      ;; Generate data across minutes 4-20 with varying patterns
      (doseq [minute-offset (range 4 21)]
        (TopologyUtils/advanceSimTime (minute-millis 1))
        (when (zero? (mod minute-offset 3))
          (println "  ✓ Generated data through minute" minute-offset))
        
        ;; Vary the operations based on minute number
        (cond
          (zero? (mod minute-offset 5))
          ;; Every 5th minute: model calls with metadata
          (dotimes [_ 2]
            (aor/agent-invoke-with-context agent-client
                                           {:metadata {"user-id" (str "user-" minute-offset)
                                                       "region" (rand-nth ["us-west" "us-east" "eu" "apac"])}}
                                           "test"
                                           #{:model}))
          
          (zero? (mod minute-offset 3))
          ;; Every 3rd minute: store operations
          (aor/agent-invoke agent-client "data" #{:store-write :store-read})
          
          :else
          ;; Other minutes: simple successful calls
          (aor/agent-invoke agent-client "." #{})))
      
      (cycle!)
      (cycle!)
      (println "✓ Generated 20 minutes of varied data")

      ;; Final analytics cycle
      (cycle!)

      (let [final-time (h/current-time-millis)
            start-bucket (long (/ thirty-mins-ago 60000))
            end-bucket (long (/ final-time 60000))]
        (println "\n✅ Setup complete!")
        (println "   Visit http://localhost:1974 to view the analytics UI")
        (println "   The agent is: MetricsGenAgent")
        (println "   Data generated in buckets:" start-bucket "to" end-bucket 
                 "(" (- end-bucket start-bucket) "minute buckets)")
        (println "   Use the returned IPC handle to continue interacting with the system")
        (println "   When done, call (aor/stop-ui) and (.close ipc)\n"))

      ;; Return the IPC handle
      ipc)))
