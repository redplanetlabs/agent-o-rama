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
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.metrics :as metrics]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
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

       (testing "node latencies at hour granularity"
         (let [fetch-hour (fn [metric-id]
                            (ana/select-telemetry telemetry
                                                  "foo"
                                                  po/HOUR-GRANULARITY
                                                  metric-id
                                                  0
                                                  (day-millis 1)
                                                  [:count :rest-sum :mean]
                                                  nil))
               hour-node-data (fetch-hour [:agent :node-latencies])
               hour-success-data (fetch-hour [:agent :success-rate])]
           (is (seq hour-success-data)
               "Success rate should have data at hour granularity (control)")
           (is (seq hour-node-data)
               "Node latencies should have data at hour granularity")))

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
                                        nil
                                        (h/current-time-millis)
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
                                        nil
                                        (h/current-time-millis)
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


(defn node-invoke-by-name
  [trace n]
  (let [[id task-id]
        (select-one!
         [ALL (selected? LAST :node (pred= n)) (subselect (multi-path FIRST [LAST :node-task-id]))]
         trace)]
    (aor-types/->valid-NodeInvokeImpl task-id id)))

(deftest human-metrics-test
  (with-open [ipc (rtest/create-ipc)]
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
               (aor/result! agent-node (str input "!"))
             )))
        (-> topology
            (aor/new-agent "bar")
            (aor/node
             "start"
             "end"
             (fn [agent-node input]
               (aor/emit! agent-node "end" (str input "?"))))
            (aor/node
             "end"
             nil
             (fn [agent-node input]
               (aor/result! agent-node input)
             )))
       ))
     (rtest/launch-module! ipc module {:tasks 2 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind global-actions-depot
       (:global-actions-depot (aor-types/underlying-objects agent-manager)))
     (bind search-human-metrics
       (:search-human-metrics-query (aor-types/underlying-objects agent-manager)))
     (bind search-queues
       (:search-human-feedback-queues-query (aor-types/underlying-objects agent-manager)))
     (bind queue-info
       (:human-feedback-queue-info-query (aor-types/underlying-objects agent-manager)))
     (bind queue-page
       (:human-feedback-queue-page-query (aor-types/underlying-objects agent-manager)))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind foo-root (:root-pstate (aor-types/underlying-objects foo)))
     (bind foo-nodes
       (foreign-pstate ipc
                       module-name
                       (po/agent-node-task-global-name "foo")))
     (bind foo-traces (:tracing-query (aor-types/underlying-objects foo)))
     (bind bar (aor/agent-client agent-manager "bar"))
     (bind bar-root (:root-pstate (aor-types/underlying-objects bar)))
     (bind bar-nodes
       (foreign-pstate ipc
                       module-name
                       (po/agent-node-task-global-name "bar")))
     (bind bar-traces (:tracing-query (aor-types/underlying-objects bar)))

     (bind get-trace
       (fn [traces-query root {:keys [task-id agent-invoke-id]}]
         (let [root-invoke-id
               (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                                   root
                                   {:pkey task-id})]
           (:invokes-map (foreign-invoke-query traces-query
                                               task-id
                                               [[task-id root-invoke-id]]
                                               10000)))))

     (bind foo-inv1
       (aor/agent-initiate foo "a"))
     (is (= "a!" (aor/agent-result foo foo-inv1)))
     (bind foo-inv2
       (aor/agent-initiate foo "b"))
     (is (= "b!" (aor/agent-result foo foo-inv2)))
     (bind bar-inv1
       (aor/agent-initiate bar "a"))
     (is (= "a?" (aor/agent-result bar bar-inv1)))
     (bind bar-inv2
       (aor/agent-initiate bar "b"))
     (is (= "b?" (aor/agent-result bar bar-inv2)))

     (bind foo-trace1 (get-trace foo-traces foo-root foo-inv1))
     (bind foo-trace2 (get-trace foo-traces foo-root foo-inv2))
     (bind bar-trace1 (get-trace bar-traces bar-root bar-inv1))
     (bind bar-trace2 (get-trace bar-traces bar-root bar-inv2))

     (aor/create-categorical-human-metric! agent-manager
                                           "c1"
                                           "c1 metric"
                                           #{"option1" "option2" "option3"})
     (aor/create-categorical-human-metric! agent-manager
                                           "c2"
                                           "c2 metric"
                                           #{"option1" "o2" "o3"})
     (aor/create-numeric-human-metric! agent-manager
                                       "n1"
                                       "n1 metric"
                                       1
                                       10)
     (aor/create-numeric-human-metric! agent-manager
                                       "n2"
                                       "n2 metric"
                                       0
                                       5)
     (is (thrown? Exception
                  (aor/create-categorical-human-metric! agent-manager
                                                        "q1"
                                                        "q1 metric"
                                                        #{})))
     (is (thrown? Exception
                  (aor/create-numeric-human-metric! agent-manager
                                                    "q1"
                                                    "q1 metric"
                                                    10
                                                    9)))
     (is (thrown? Exception
                  (aor/create-numeric-human-metric! agent-manager
                                                    "c1"
                                                    ""
                                                    1
                                                    10)))
     (aor/create-categorical-human-metric! agent-manager
                                           "q1"
                                           "q1 metric"
                                           #{"a"})


     (bind res (foreign-invoke-query search-human-metrics {} 2 nil))
     (is (= (:items res)
            [{:name        "c1"
              :description "c1 metric"
              :metric      (aor-types/->HumanCategoryMetric #{"option1" "option2" "option3"})}
             {:name        "c2"
              :description "c2 metric"
              :metric      (aor-types/->HumanCategoryMetric #{"option1" "o2" "o3"})}
            ]))
     (bind res (foreign-invoke-query search-human-metrics {} 2 (:pagination-params res)))
     (is (= (:items res)
            [{:name        "n1"
              :description "n1 metric"
              :metric      (aor-types/->HumanNumericMetric 1 10)}
             {:name        "n2"
              :description "n2 metric"
              :metric      (aor-types/->HumanNumericMetric 0 5)}
            ]))
     (bind res (foreign-invoke-query search-human-metrics {} 2 (:pagination-params res)))
     (is (= (:items res)
            [{:name        "q1"
              :description "q1 metric"
              :metric      (aor-types/->HumanCategoryMetric #{"a"})}
            ]))
     (is (nil? (:pagination-params res)))


     (bind res (foreign-invoke-query search-human-metrics {:search-string "2"} 2 nil))
     (is (= (:items res)
            [{:name        "c2"
              :description "c2 metric"
              :metric      (aor-types/->HumanCategoryMetric #{"option1" "o2" "o3"})}
             {:name        "n2"
              :description "n2 metric"
              :metric      (aor-types/->HumanNumericMetric 0 5)}
            ]))


     (aor/remove-human-metric! agent-manager "c2")
     (bind res (foreign-invoke-query search-human-metrics {:search-string "2"} 2 nil))
     (is (= (:items res)
            [{:name        "n2"
              :description "n2 metric"
              :metric      (aor-types/->HumanNumericMetric 0 5)}
            ]))

     (bind rubric
       (fn [human-metric required?]
         (aor-types/->valid-HumanFeedbackQueueRubric human-metric required?)))

     (evals/create-human-feedback-queue! global-actions-depot
                                         "q1"
                                         "q1 q"
                                         [(rubric "c1" true)
                                          (rubric "n1" false)])
     (is (thrown? Exception
                  (evals/create-human-feedback-queue! global-actions-depot
                                                      "qq2"
                                                      "q2 q"
                                                      [])))
     (is (thrown? Exception
                  (evals/create-human-feedback-queue! global-actions-depot
                                                      "q1"
                                                      "q1 qq"
                                                      [(rubric "c1" false)])))

     (evals/create-human-feedback-queue! global-actions-depot
                                         "q2qq"
                                         "q2 q"
                                         [(rubric "n1" false)])

     (evals/create-human-feedback-queue! global-actions-depot
                                         "q3"
                                         "q3 q"
                                         [(rubric "n1" true)])
     (evals/create-human-feedback-queue! global-actions-depot
                                         "q4qq"
                                         "q4 q"
                                         [(rubric "n2" true)])


     (bind res (foreign-invoke-query search-queues {} 2 nil))
     (is (= (:items res)
            [{:name        "q1"
              :description "q1 q"
              :rubrics     [(rubric "c1" true)
                            (rubric "n1" false)]}
             {:name        "q2qq"
              :description "q2 q"
              :rubrics     [(rubric "n1" false)]}
            ]))
     (bind res (foreign-invoke-query search-queues {} 2 (:pagination-params res)))
     (is (= (:items res)
            [{:name        "q3"
              :description "q3 q"
              :rubrics     [(rubric "n1" true)]}
             {:name        "q4qq"
              :description "q4 q"
              :rubrics     [(rubric "n2" true)]}
            ]))
     (bind res (foreign-invoke-query search-queues {} 2 (:pagination-params res)))
     (is (empty? (:items res)))
     (is (nil? (:pagination-params res)))

     (bind res (foreign-invoke-query search-queues {:search-string "qq"} 2 nil))
     (is (= (:items res)
            [{:name        "q2qq"
              :description "q2 q"
              :rubrics     [(rubric "n1" false)]}
             {:name        "q4qq"
              :description "q4 q"
              :rubrics     [(rubric "n2" true)]}
            ]))

     (evals/update-human-feedback-queue! global-actions-depot
                                         "q1"
                                         "aq"
                                         [(rubric "c1" false)
                                          (rubric "notmetric1" true)
                                          (rubric "n2" true)
                                          (rubric "notmetric2" false)])
     ;; verify this is a no-op
     (evals/update-human-feedback-queue! global-actions-depot
                                         "a"
                                         "aq"
                                         [(rubric "c1" false)])
     (bind res (foreign-invoke-query search-queues {} 1 nil))
     (is (= (:items res)
            [{:name        "q1"
              :description "aq"
              :rubrics     [(rubric "c1" false)
                            (rubric "notmetric1" true)
                            (rubric "n2" true)
                            (rubric "notmetric2" false)]}
            ]))

     (evals/remove-human-feedback-queue! global-actions-depot "q3")
     ;; verify this is a no-op
     (evals/remove-human-feedback-queue! global-actions-depot "a")
     (bind res (foreign-invoke-query search-queues {} 4 nil))
     (is (= ["q1" "q2qq" "q4qq"]
            (->> res
                 :items
                 (mapv :name))))
     (is (nil? (:pagination-params res)))


     (bind res (foreign-invoke-query queue-info "q1"))

     (is (= res
            {:description "aq"
             :rubrics     [{:description "c1 metric"
                            :name        "c1"
                            :metric      (aor-types/->HumanCategoryMetric #{"option1" "option3"
                                                                            "option2"})
                            :required    false}
                           {:description "n2 metric"
                            :name        "n2"
                            :metric      (aor-types/->HumanNumericMetric 0 5)
                            :required    true}]}))

     (bind agent-target
       (fn [agent-name agent-invoke]
         (aor-types/->valid-FeedbackTarget agent-name agent-invoke nil)))
     (bind node-target
       (fn [agent-name agent-invoke node-invoke]
         (aor-types/->valid-FeedbackTarget agent-name agent-invoke node-invoke)))

     (bind target0 (agent-target "foo" foo-inv1))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target0
      "comment 1")
     (bind target1 (agent-target "bar" bar-inv2))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target1
      "comment 2")
     (bind target2 (agent-target "notagent" bar-inv2))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target2
      "comment 3")
     (bind target3 (node-target "notagent" bar-inv2 (node-invoke-by-name bar-trace2 "start")))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target3
      "comment 4")
     (bind target4 (node-target "bar" bar-inv2 (node-invoke-by-name bar-trace2 "start")))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target4
      "comment 5")
     (bind target5 (node-target "bar" bar-inv2 (node-invoke-by-name bar-trace2 "end")))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target5
      "comment 6")
     (bind target6 (agent-target "foo" (assoc foo-inv2 :agent-invoke-id (h/random-uuid7))))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target6
      "comment 7")
     (bind target7
       (node-target "foo"
                    foo-inv2
                    (assoc (node-invoke-by-name foo-trace2 "start")
                     :node-invoke-id (h/random-uuid7))))
     (evals/add-human-feedback-request!
      global-actions-depot
      "q1"
      target7
      "comment 8")


     (bind no-ids
       (fn [l]
         (setval [:items ALL (must :id)] :* l)))

     (bind res (no-ids (foreign-invoke-query queue-page "q1" 3 nil)))
     (is (= (:items res)
            [{:output  (aor-types/->AgentResult "a!" false)
              :id      :*
              :comment "comment 1"
              :input   ["a"]
              :target  target0}
             {:output  (aor-types/->AgentResult "b?" false)
              :id      :*
              :comment "comment 2"
              :input   ["b"]
              :target  target1}
             {:output  queries/TARGET-DOES-NOT-EXIST
              :id      :*
              :comment "comment 3"
              :input   queries/TARGET-DOES-NOT-EXIST
              :target  target2}
            ]))
     (bind res (no-ids (foreign-invoke-query queue-page "q1" 3 (:pagination-params res))))
     (is (= (:items res)
            [{:output  queries/TARGET-DOES-NOT-EXIST
              :id      :*
              :comment "comment 4"
              :input   queries/TARGET-DOES-NOT-EXIST
              :target  target3}
             {:output  [{"node" "end" "args" ["b?"]}]
              :id      :*
              :comment "comment 5"
              :input   ["b"]
              :target  target4}
             {:output  "b?"
              :id      :*
              :comment "comment 6"
              :input   ["b?"]
              :target  target5}
            ]))

     (bind res (no-ids (foreign-invoke-query queue-page "q1" 3 (:pagination-params res))))
     (is (= (:items res)
            [{:output  queries/TARGET-DOES-NOT-EXIST
              :id      :*
              :comment "comment 7"
              :input   queries/TARGET-DOES-NOT-EXIST
              :target  target6}
             {:output  queries/TARGET-DOES-NOT-EXIST
              :id      :*
              :comment "comment 8"
              :input   queries/TARGET-DOES-NOT-EXIST
              :target  target7}]))
     (is (nil? (:pagination-params res)))


     (bind all-ids-fn
       (fn []
         (select
          [:items ALL :id]
          (foreign-invoke-query queue-page "q1" 10 nil))))
     (bind all-ids (all-ids-fn))

     (bind cleaned-feedback
       (fn [{:keys [agent-name agent-invoke node-invoke]}]
         (let [p        (if (some? node-invoke)
                          (if (= agent-name "foo")
                            foo-nodes
                            bar-nodes)
                          (if (= agent-name "foo")
                            foo-root
                            bar-root))
               task-id  (if (some? node-invoke) (:task-id node-invoke) (:task-id agent-invoke))
               root-id  (if (some? node-invoke)
                          (:node-invoke-id node-invoke)
                          (:agent-invoke-id agent-invoke))
               clean-fn (fn [{:keys [created-at modified-at] :as fb}]
                          (let [delta (cond (> modified-at created-at) 1
                                            (< modified-at created-at) -1
                                            :else 0)]
                            (assoc (into {} fb)
                             :created-at 0
                             :modified-at delta)))]
           (foreign-select [(keypath root-id) :feedback :results ALL (view clean-fn)]
                           p
                           {:pkey task-id})
         )))


     (bind fid0
       (evals/resolve-human-feedback-queue-item!
        global-actions-depot
        "q1"
        (nth all-ids 0)
        target0
        "alice"
        {"score"    5
         "helpful?" true}
        "hcomment 0"))
     (is (= (all-ids-fn) (next all-ids)))
     (is (= (cleaned-feedback target0)
            [{:scores      {"score"    5
                            "helpful?" true}
              :comment     "hcomment 0"
              :source      (aor-types/->HumanSourceImpl "alice" fid0)
              :created-at  0
              :modified-at 0}]))



     (bind fid1
       (evals/resolve-human-feedback-queue-item!
        global-actions-depot
        "q1"
        (nth all-ids 4)
        target4
        "bob"
        {"score" 10
         "type"  "suggestion"}
        "hcomment 1"))
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 4) NONE)
                 next)))
     (is (= (cleaned-feedback target4)
            [{:scores      {"score" 10
                            "type"  "suggestion"}
              :comment     "hcomment 1"
              :source      (aor-types/->HumanSourceImpl "bob" fid1)
              :created-at  0
              :modified-at 0}]))

     ;; verify non-existent agent is no-op but still removes from queue
     (evals/resolve-human-feedback-queue-item!
      global-actions-depot
      "q1"
      (nth all-ids 2)
      target2
      "bob"
      {"score" 10}
      "hcomment 2")
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 4) NONE)
                 (setval (nthpath 2) NONE)
                 next)))

     ;; verify non-existent traces are no-ops but still removes from queue
     (evals/resolve-human-feedback-queue-item!
      global-actions-depot
      "q1"
      (nth all-ids 6)
      target6
      "bob"
      {"score" 10}
      "hcomment 3")
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 6) NONE)
                 (setval (nthpath 4) NONE)
                 (setval (nthpath 2) NONE)
                 next)))

     (evals/resolve-human-feedback-queue-item!
      global-actions-depot
      "q1"
      (nth all-ids 7)
      target7
      "bob"
      {"score" 10}
      "hcomment 3")
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 7) NONE)
                 (setval (nthpath 6) NONE)
                 (setval (nthpath 4) NONE)
                 (setval (nthpath 2) NONE)
                 next)))

     (evals/remove-human-feedback-queue-item! global-actions-depot "q1" (nth all-ids 1))
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 7) NONE)
                 (setval (nthpath 6) NONE)
                 (setval (nthpath 4) NONE)
                 (setval (nthpath 2) NONE)
                 (setval (nthpath 1) NONE)
                 next)))
     ;; verify idempotent
     (evals/remove-human-feedback-queue-item! global-actions-depot "q1" (nth all-ids 1))
     (is (= (all-ids-fn)
            (->> all-ids
                 (setval (nthpath 7) NONE)
                 (setval (nthpath 6) NONE)
                 (setval (nthpath 4) NONE)
                 (setval (nthpath 2) NONE)
                 (setval (nthpath 1) NONE)
                 next)))


     (bind fid2
       (evals/add-human-feedback! global-actions-depot
                                  target0
                                  "charlie"
                                  {"a" 1 "b" 2}
                                  "hcomment 4"))
     (bind fid3
       (evals/add-human-feedback! global-actions-depot
                                  target5
                                  "dan"
                                  {"c" 6}
                                  ""))
     (bind fid4
       (evals/add-human-feedback! global-actions-depot
                                  target5
                                  "emily"
                                  {}
                                  "hcomment 6"))


     (is (= (cleaned-feedback target0)
            [{:scores      {"score"    5
                            "helpful?" true}
              :comment     "hcomment 0"
              :source      (aor-types/->HumanSourceImpl "alice" fid0)
              :created-at  0
              :modified-at 0}
             {:scores      {"a" 1
                            "b" 2}
              :comment     "hcomment 4"
              :source      (aor-types/->HumanSourceImpl "charlie" fid2)
              :created-at  0
              :modified-at 0}
            ]))
     (is (= (cleaned-feedback target5)
            [{:scores      {"c" 6}
              :comment     ""
              :source      (aor-types/->HumanSourceImpl "dan" fid3)
              :created-at  0
              :modified-at 0}
             {:scores      {}
              :comment     "hcomment 6"
              :source      (aor-types/->HumanSourceImpl "emily" fid4)
              :created-at  0
              :modified-at 0}
            ]))

     (bind fid0-a
       (evals/edit-human-feedback!
        global-actions-depot
        target0
        fid0
        "alice2"
        {"score2" 6}
        "0-hcomment 0-0"))
     (is (not= fid0 fid0-a))
     (is (= (cleaned-feedback target0)
            [{:scores      {"score2" 6}
              :comment     "0-hcomment 0-0"
              :source      (aor-types/->HumanSourceImpl "alice2" fid0-a)
              :created-at  0
              :modified-at 1}
             {:scores      {"a" 1
                            "b" 2}
              :comment     "hcomment 4"
              :source      (aor-types/->HumanSourceImpl "charlie" fid2)
              :created-at  0
              :modified-at 0}
            ]))
     (evals/edit-human-feedback!
      global-actions-depot
      target0
      fid0
      "alice3"
      {"score3" 6}
      "aaa")
     ;; verify it needs the correct ID to change it, and otherwise is a no-op
     (is (= (cleaned-feedback target0)
            [{:scores      {"score2" 6}
              :comment     "0-hcomment 0-0"
              :source      (aor-types/->HumanSourceImpl "alice2" fid0-a)
              :created-at  0
              :modified-at 1}
             {:scores      {"a" 1
                            "b" 2}
              :comment     "hcomment 4"
              :source      (aor-types/->HumanSourceImpl "charlie" fid2)
              :created-at  0
              :modified-at 0}
            ]))

     (evals/delete-human-feedback!
      global-actions-depot
      target0
      fid0-a)
     (is (= (cleaned-feedback target0)
            [{:scores      {"a" 1
                            "b" 2}
              :comment     "hcomment 4"
              :source      (aor-types/->HumanSourceImpl "charlie" fid2)
              :created-at  0
              :modified-at 0}
            ]))
     ;; verify idempotent
     (evals/delete-human-feedback!
      global-actions-depot
      target0
      fid0-a)
     (is (= (cleaned-feedback target0)
            [{:scores      {"a" 1
                            "b" 2}
              :comment     "hcomment 4"
              :source      (aor-types/->HumanSourceImpl "charlie" fid2)
              :created-at  0
              :modified-at 0}
            ]))
    )))
