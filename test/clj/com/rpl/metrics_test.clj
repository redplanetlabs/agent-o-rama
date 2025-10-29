(ns com.rpl.metrics-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.metrics :as metrics]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.rama.helpers
    TopologyUtils]
   [dev.langchain4j.data.message
    AiMessage
    UserMessage]
   [dev.langchain4j.model.chat
    StreamingChatModel]
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]
   [dev.langchain4j.model.output
    TokenUsage]
   [dev.langchain4j.store.embedding
    EmbeddingSearchRequest
    EmbeddingSearchResult
    EmbeddingStore]
   [dev.langchain4j.store.embedding.filter.comparison
    IsEqualTo]))

(def TICKS)

(defrecord MockChatModel []
  StreamingChatModel
  (doChat [this request handler]
    (let [^UserMessage um (-> request
                              .messages
                              last)
          m        (.singleText um)
          o        (str m "***")
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
      (.onCompleteResponse handler response)
    )))

(deftype MockEmbeddingStore []
  EmbeddingStore
  (add [this embedding]
    (TopologyUtils/advanceSimTime 10)
    "999")
  (search [this request]
    (TopologyUtils/advanceSimTime 15)
    (EmbeddingSearchResult. [])))

(defn advancer-pred
  [amt]
  (fn [_]
    (TopologyUtils/advanceSimTime amt)
    true
  ))

(defn minute-millis
  [i]
  (* i 1000 po/MINUTE-GRANULARITY))

(defn hour-millis
  [i]
  (* i 1000 po/HOUR-GRANULARITY))

(defn day-millis
  [i]
  (* i 1000 po/DAY-GRANULARITY))

(defn thirty-day-millis
  [i]
  (* i 1000 po/THIRTY-DAY-GRANULARITY))

(deftest basic-metrics-test
  (with-redefs [TICKS (atom 0)
                i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))

                aor-types/get-config (max-retries-override 0)

                anode/gen-node-id
                (fn [& args]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))

                anode/log-node-error (fn [& args])

                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))

                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))

                at/gen-new-agent-id
                (fn [agent-name]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-evaluator-builder
           topology
           "my-eval"
           ""
           (fn [params]
             (fn [fetcher input ref-output output]
               {"score-a" (count (first input))
                "score-b" (+ (count output) 0.5)
                "score-c" (if (<= (count (first input)) 3) "small" "large")}
             )))
          (aor/declare-agent-object-builder
           topology
           "my-model"
           (fn [setup] (->MockChatModel)))
          (aor/declare-agent-object-builder
           topology
           "emb"
           (fn [setup] (MockEmbeddingStore.)))
          (aor/declare-pstate-store
           topology
           "$$p"
           Object)
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               "a"
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
                   (aor/emit! agent-node "a" (str input "!") flags))))
              (aor/node
               "a"
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
                     (aor/result! agent-node (str input "?"))))))
          )))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
       (bind telemetry (:telemetry-pstate (aor-types/underlying-objects foo)))

       (bind cycle!
         (fn []
           (reset! TICKS 0)
           (foreign-append! ana-depot nil)
           (is (condition-attained? (> @TICKS 0)))
           (rtest/pause-microbatch-topology! ipc
                                             module-name
                                             aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
           (rtest/resume-microbatch-topology! ipc
                                              module-name
                                              aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)))


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

       (ana/add-rule! global-actions-depot
                      "rule1"
                      "foo"
                      {:action-name       "aor/eval"
                       :action-params     {"name" "concise5"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })
       (ana/add-rule! global-actions-depot
                      "rule2"
                      "foo"
                      {:action-name       "aor/eval"
                       :action-params     {"name" "eval1"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })

       (TopologyUtils/advanceSimTime 1000)

       (is
        (= "ab!?"
           (aor/agent-invoke-with-context foo
                                          {:metadata {"m1" "a"}}
                                          "ab"
                                          #{:model :store-read :store-write :db-read :db-write})))
       (is (= "...!?"
              (aor/agent-invoke-with-context foo
                                             {:metadata {"m1" "a" "m2" 1}}
                                             "..."
                                             #{:model :store-read :db-read})))
       (is (thrown? Exception
                    (aor/agent-invoke-with-context foo {:metadata {"m1" "b"}} "fail" #{})))

       (TopologyUtils/advanceSimTime (minute-millis 1))

       (is (= "abc!?"
              (aor/agent-invoke foo "abc" #{:store-write :db-read :db-write})))
       (is (= "eeeee!?"
              (aor/agent-invoke-with-context foo {:metadata {"m2" 2}} "eeeee" #{:model})))

       (TopologyUtils/advanceSimTime (minute-millis 1))
       (is (thrown? Exception (aor/agent-invoke foo "fail-model" #{:model})))


       (cycle!)
       (cycle!)

       (bind fetch-day
         (fn [metric-id metadata-key]
           (ana/select-telemetry telemetry
                                 "foo"
                                 po/MINUTE-GRANULARITY
                                 metric-id
                                 0
                                 (day-millis 1)
                                 [:count :rest-sum]
                                 metadata-key)))


       ;; check agent success rate
       (testing "agent success rate"
         (is (= {0 {"_aor/default" {:count 3 :rest-sum 2}}
                 1 {"_aor/default" {:count 2 :rest-sum 2}}
                 2 {"_aor/default" {:count 1 :rest-sum 0}}}
                (fetch-day [:agent :success-rate] nil)))
         (is (= {0
                 {"run-success" {"_aor/default" {:count 2 :rest-sum 2}}
                  "run-failure" {"_aor/default" {:count 1 :rest-sum 0}}}
                 1 {"run-success" {"_aor/default" {:count 2 :rest-sum 2}}}
                 2 {"run-failure" {"_aor/default" {:count 1 :rest-sum 0}}}}
                (fetch-day [:agent :success-rate] "aor/status")))
         (is (= {0
                 {"a" {"_aor/default" {:count 2 :rest-sum 2}}
                  "b" {"_aor/default" {:count 1 :rest-sum 0}}}}
                (fetch-day [:agent :success-rate] "m1")))
         (is (= {0 {1 {"_aor/default" {:count 1 :rest-sum 1}}}
                 1 {2 {"_aor/default" {:count 1 :rest-sum 1}}}}
                (fetch-day [:agent :success-rate] "m2"))))

       ;; check agent latency
       (testing "agent latency"
         (is (= {0 {"_aor/default" {:count 3 :rest-sum 1099}}
                 1 {"_aor/default" {:count 2 :rest-sum 553}}
                 2 {"_aor/default" {:count 1 :rest-sum 403}}}
                (fetch-day [:agent :latency] nil)))
         (is (= {0
                 {"run-success" {"_aor/default" {:count 2 :rest-sum 1096}}
                  "run-failure" {"_aor/default" {:count 1 :rest-sum 3}}}
                 1 {"run-success" {"_aor/default" {:count 2 :rest-sum 553}}}
                 2 {"run-failure" {"_aor/default" {:count 1 :rest-sum 403}}}}
                (fetch-day [:agent :latency] "aor/status")))
         (is (= {0
                 {"a" {"_aor/default" {:count 2 :rest-sum 1096}}
                  "b" {"_aor/default" {:count 1 :rest-sum 3}}}}
                (fetch-day [:agent :latency] "m1")))
         (is (= {0 {1 {"_aor/default" {:count 1 :rest-sum 532}}}
                 1 {2 {"_aor/default" {:count 1 :rest-sum 503}}}}
                (fetch-day [:agent :latency] "m2"))))

       (testing "agent model call count"
         (is (= {0 {"_aor/default" {:count 3 :rest-sum 4}}
                 1 {"_aor/default" {:count 2 :rest-sum 2}}
                 ;; - the model calls are not in trace analytics because the node never succeeded:
                 ;;   - if it retries and succeeds, all its stats would be sent back on ack
                 ;;     - but since it failed and never retried, it never gets sent
                 ;;     - can't send on failure because it would get sent again on retry success
                 2 {"_aor/default" {:count 1 :rest-sum 0}}}
                (fetch-day [:agent :model-call-count] nil)))
         (is (= {0
                 {"run-success" {"_aor/default" {:count 2 :rest-sum 4}}
                  "run-failure" {"_aor/default" {:count 1 :rest-sum 0}}}
                 1 {"run-success" {"_aor/default" {:count 2 :rest-sum 2}}}
                 2 {"run-failure" {"_aor/default" {:count 1 :rest-sum 0}}}}
                (fetch-day [:agent :model-call-count] "aor/status")))
         (is (= {0
                 {"a" {"_aor/default" {:count 2 :rest-sum 4}}
                  "b" {"_aor/default" {:count 1 :rest-sum 0}}}}
                (fetch-day [:agent :model-call-count] "m1")))
         (is (= {0 {1 {"_aor/default" {:count 1 :rest-sum 2}}}
                 1 {2 {"_aor/default" {:count 1 :rest-sum 2}}}}
                (fetch-day [:agent :model-call-count] "m2"))))

       (testing "token counts"
         (is (= {0
                 {"input"  {:count 3 :rest-sum 7}
                  "output" {:count 3 :rest-sum 19}
                  "total"  {:count 3 :rest-sum 34}}
                 1
                 {"input"  {:count 2 :rest-sum 6}
                  "output" {:count 2 :rest-sum 12}
                  "total"  {:count 2 :rest-sum 22}}
                 2
                 {"input"  {:count 1 :rest-sum 0}
                  "output" {:count 1 :rest-sum 0}
                  "total"  {:count 1 :rest-sum 0}}}
                (fetch-day [:agent :token-counts] nil))))


       (testing "model success rate"
         (is (= {0
                 {"success" {:count 6 :rest-sum 4}
                  "failure" {:count 6 :rest-sum 0}}
                 1
                 {"success" {:count 4 :rest-sum 2}
                  "failure" {:count 4 :rest-sum 0}}
                 2
                 {"success" {:count 1 :rest-sum 1}
                  "failure" {:count 1 :rest-sum 1}}}
                (fetch-day [:agent :model-success-rate] nil))))

       (testing "model latency"
         (is (= {0 {"_aor/default" {:count 4 :rest-sum 1000}}
                 1 {"_aor/default" {:count 2 :rest-sum 500}}
                 ;; this includes the 150ms latency for the model failure
                 2 {"_aor/default" {:count 2 :rest-sum 400}}}
                (fetch-day [:agent :model-latency] nil))))

       (testing "store read latency"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 28}}}
                (fetch-day [:agent :store-read-latency] nil))))

       (testing "store write latency"
         (is (= {0 {"_aor/default" {:count 1 :rest-sum 12}}
                 1 {"_aor/default" {:count 1 :rest-sum 12}}}
                (fetch-day [:agent :store-write-latency] nil))))

       (testing "db read latency"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 30}}
                 1 {"_aor/default" {:count 1 :rest-sum 15}}}
                (fetch-day [:agent :db-read-latency] nil))))

       (testing "db write latency"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 20}}
                 1 {"_aor/default" {:count 2 :rest-sum 20}}}
                (fetch-day [:agent :db-write-latency] nil))))

       ;; this is non-deterministic, since next model / node keep running while streaming is
       ;; processing, so checks here are against a lower bound
       (bind res (fetch-day [:agent :first-token-time] nil))
       (testing "agent first token time"
         (is (= [0 1 2] (keys res)))
         (is (= [2 1 1] (select [MAP-VALS MAP-VALS :count] res)))
         (is (>= (select-any [0 "_aor/default" :rest-sum] res) (* 2 153)))
         (is (>= (select-any [1 "_aor/default" :rest-sum] res) 153))
         (is (>= (select-any [2 "_aor/default" :rest-sum] res) 153)))

       (testing "model first token time"
         (is (= {0 {"_aor/default" {:count 4 :rest-sum 600}}
                 1 {"_aor/default" {:count 2 :rest-sum 300}}
                 2 {"_aor/default" {:count 1 :rest-sum 150}}}
                (fetch-day [:agent :model-first-token-time] nil))))

       (testing "node latencies"
         (is (= {0 {"start" {:count 3 :rest-sum 1021} "a" {:count 2 :rest-sum 78}}
                 1 {"start" {:count 2 :rest-sum 518} "a" {:count 2 :rest-sum 35}}}
                (fetch-day [:agent :node-latencies] nil))))

       (testing "concise? eval"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 2}}
                 1 {"_aor/default" {:count 2 :rest-sum 1}}}
                (fetch-day [:eval :rule1 :concise?] nil))))

       (testing "score-a eval"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 5}}
                 1 {"_aor/default" {:count 2 :rest-sum 8}}}
                (fetch-day [:eval :rule2 :score-a] nil))))

       (testing "score-b eval"
         (is (= {0 {"_aor/default" {:count 2 :rest-sum 10.0}}
                 1 {"_aor/default" {:count 2 :rest-sum 13.0}}}
                (fetch-day [:eval :rule2 :score-b] nil))))

       (is (= {0 {"small" {:count 2 :rest-sum 2}}
               1 {"small" {:count 1 :rest-sum 1}
                  "large" {:count 1 :rest-sum 1}}}
              (fetch-day [:eval :rule2 :score-c] nil)))

       ;; verify all-agent-metrics topology
       (bind all-agent-metrics (:all-agent-metrics-query (aor-types/underlying-objects foo)))
       (bind res (foreign-invoke-query all-agent-metrics))
       (is (= res
              (-> metrics/ALL-METRICS
                  keys
                  set
                  (conj [:eval :rule1 :concise?])
                  (conj [:eval :rule2 :score-a])
                  (conj [:eval :rule2 :score-b])
                  (conj [:eval :rule2 :score-c])
              )))

       (TopologyUtils/advanceSimTime (minute-millis 1))

       (doseq [i (range 20)]
         (let [s (apply str (repeat i "."))]
           (is
            (= (str s "!?")
               (aor/agent-invoke-with-context foo
                                              {:metadata {"m3" (str i)}}
                                              s
                                              #{})))))

       (cycle!)
       (cycle!)

       ;; verify only first 5 metadata values are captured in a bucket
       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               (minute-millis 3)
                               (hour-millis 1)
                               [:count :rest-sum]
                               "m3"))
       (is (= 5 (count (get res 3))))
       (is (= [1 1 1 1 1] (select [MAP-VALS MAP-VALS MAP-VALS :count] res)))
       (is (= [1 1 1 1 1] (select [MAP-VALS MAP-VALS MAP-VALS :rest-sum] res)))


       ;; verify all the other ways to query for metrics
       (bind res
         (select-any [3 "_aor/default"]
                     (ana/select-telemetry telemetry
                                           "foo"
                                           po/MINUTE-GRANULARITY
                                           [:eval :rule2 :score-a]
                                           (* 3 1000 po/MINUTE-GRANULARITY)
                                           (* 1000 po/HOUR-GRANULARITY)
                                           [:mean :min :max :latest 0.25 0.5 0.75]
                                           nil)))
       (is (= 9.5 (:mean res)))
       (is (= 0.0 (:min res)))
       (is (= 19.0 (:max res)))
       (is (< (get res 0.25) (get res 0.5) (get res 0.75)))
       (is (number? (:latest res)))


       (TopologyUtils/advanceSimTime (hour-millis 1))
       (dotimes [_ 3]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (TopologyUtils/advanceSimTime (hour-millis 1))
       (dotimes [_ 2]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (cycle!)
       (cycle!)
       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/HOUR-GRANULARITY
                               [:agent :success-rate]
                               (hour-millis 1)
                               (hour-millis 3)
                               [:count]
                               nil))
       (is (= res {1 {"_aor/default" {:count 3}} 2 {"_aor/default" {:count 2}}}))

       (TopologyUtils/advanceSimTime (day-millis 1))
       (dotimes [_ 2]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (TopologyUtils/advanceSimTime (day-millis 1))
       (dotimes [_ 3]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (cycle!)
       (cycle!)
       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/DAY-GRANULARITY
                               [:agent :success-rate]
                               (day-millis 1)
                               (day-millis 3)
                               [:count]
                               nil))
       (is (= res {1 {"_aor/default" {:count 2}} 2 {"_aor/default" {:count 3}}}))

       (TopologyUtils/advanceSimTime (thirty-day-millis 1))
       (dotimes [_ 1]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (TopologyUtils/advanceSimTime (thirty-day-millis 1))
       (dotimes [_ 2]
         (is (= ".!?"
                (aor/agent-invoke foo "." #{}))))
       (cycle!)
       (cycle!)
       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/THIRTY-DAY-GRANULARITY
                               [:agent :success-rate]
                               (thirty-day-millis 1)
                               (thirty-day-millis 3)
                               [:count]
                               nil))
       (is (= res {1 {"_aor/default" {:count 1}} 2 {"_aor/default" {:count 2}}}))
      ))))

(deftest metrics-coordination-test
  (with-redefs [TICKS (atom 0)
                i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))

                anode/gen-node-id
                (fn [& args]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))

                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))

                ana/node-stall-time (fn [] (+ (h/current-time-millis) 60000))

                at/gen-new-agent-id
                (fn [agent-name]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node input]
                 (aor/result! agent-node (str input "!!!"))
               )))
         ))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind pstate-write-depot (foreign-depot ipc module-name (po/agent-pstate-write-depot-name)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind foo-root (:root-pstate (aor-types/underlying-objects foo)))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
       (bind telemetry (:telemetry-pstate (aor-types/underlying-objects foo)))
       (bind cursors
         (foreign-pstate ipc module-name (po/agent-metric-cursors-task-global-name "foo")))

       (bind cycle!
         (fn []
           (reset! TICKS 0)
           (foreign-append! ana-depot nil)
           (is (condition-attained? (> @TICKS 0)))
           (rtest/pause-microbatch-topology! ipc
                                             module-name
                                             aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
           (rtest/resume-microbatch-topology! ipc
                                              module-name
                                              aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)))


       (foreign-append! global-actions-depot
                        (aor-types/change-analytics-scan-amount-per-target-per-task 2))


       (aor/create-evaluator! agent-manager
                              "concise5"
                              "aor/conciseness"
                              {"threshold" "5"}
                              "")

       (bind feedback-invs-vol (volatile! []))

       (TopologyUtils/advanceSimTime 1000)

       ;; some feedback is written manually for future rule to verify metrics only begin at start
       ;; time of that rule (these should be skipped)
       (binding [aor-types/FORCED-AGENT-TASK-ID 0]
         (let [inv (aor/agent-initiate foo "a")]
           (vswap! feedback-invs-vol conj inv)
           (is (= "a!!!" (aor/agent-result foo inv)))
           (simpl/do-pstate-write!
            pstate-write-depot
            nil
            (po/agent-root-task-global-name "foo")
            (path (keypath (:agent-invoke-id inv))
                  (fb/add-feedback-path {"concise?" true}
                                        (aor-types/->valid-EvalSourceImpl
                                         "concise5"
                                         inv
                                         (aor-types/->valid-ActionSourceImpl "foo" "rule1"))))
            (aor-types/->DirectTaskId (:task-id inv)))
           (is (= "abcd!!!" (aor/agent-invoke foo "abcd")))))
       (binding [aor-types/FORCED-AGENT-TASK-ID 1]
         (let [inv (aor/agent-initiate foo "...")]
           (vswap! feedback-invs-vol conj inv)
           (is (= "...!!!" (aor/agent-result foo inv)))
           (simpl/do-pstate-write!
            pstate-write-depot
            nil
            (po/agent-root-task-global-name "foo")
            (path (keypath (:agent-invoke-id inv))
                  (fb/add-feedback-path {"concise?" false}
                                        (aor-types/->valid-EvalSourceImpl
                                         "concise5"
                                         inv
                                         (aor-types/->valid-ActionSourceImpl "foo" "rule1"))))
            (aor-types/->DirectTaskId (:task-id inv)))
         ))

       ;; sanity check
       (doseq [inv @feedback-invs-vol]
         (is (not (empty? (foreign-select-one [(keypath (:agent-invoke-id inv)) :feedback :results]
                                              foo-root
                                              {:pkey (:task-id inv)})))))


       (TopologyUtils/advanceSimTime 60000)
       (ana/add-rule! global-actions-depot
                      "rule1"
                      "foo"
                      {:node-name         "start"
                       :action-name       "aor/eval"
                       :action-params     {"name" "concise5"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 20000
                       :status-filter     :success
                      })

       (binding [aor-types/FORCED-AGENT-TASK-ID 0]
         (is (= "....!!!" (aor/agent-invoke foo "....")))
         (is (= ".!!!" (aor/agent-invoke foo ".")))
         (is (= "..!!!" (aor/agent-invoke foo "..")))
         (is (= "z!!!" (aor/agent-invoke foo "z"))))
       (binding [aor-types/FORCED-AGENT-TASK-ID 1]
         (is (= "aa!!!" (aor/agent-invoke foo "aa")))
         (is (= "x!!!" (aor/agent-invoke foo "x"))))

       (cycle!)

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:eval :rule1 :concise?]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       ;; first cycle was to apply rule
       (is (= {} res))

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       ;; - 2 from task 0 are in bucket 0
       ;;  - 1 from task 1 is in bucket 0
       ;;  - 1 from task 1 is in bucket 1
       (is (= res {0 {"_aor/default" {:count 3}} 1 {"_aor/default" {:count 1}}}))

       (cycle!)

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:eval :rule1 :concise?]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       ;; skipped everything from bucket 0
       (is (= res {1 {"_aor/default" {:count 4}}}))

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       ;; last one from task 0 and 2 more from task 1
       (is (= res {0 {"_aor/default" {:count 3}} 1 {"_aor/default" {:count 4}}}))


       (cycle!)


       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:eval :rule1 :concise?]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       (is (= res {1 {"_aor/default" {:count 6}}}))

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       (is (= res {0 {"_aor/default" {:count 3}} 1 {"_aor/default" {:count 6}}}))

       (cycle!)

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:eval :rule1 :concise?]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       (is (= res {1 {"_aor/default" {:count 6}}}))

       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               0
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count]
                               nil))
       (is (= res {0 {"_aor/default" {:count 3}} 1 {"_aor/default" {:count 6}}}))

       ;; verify associated cursors for a deleted rule get deleted on the next cycle
       (dotimes [i 2]
         (is (= #{[:root] [:nodes] [:eval :rule1]})
             (set (foreign-select MAP-KEYS cursors {:pkey i}))))
       (ana/delete-rule! global-actions-depot "foo" "rule1")
       (cycle!)
       (dotimes [i 2]
         (is (= #{[:root] [:nodes]})
             (set (foreign-select MAP-KEYS cursors {:pkey i}))))
      ))))
