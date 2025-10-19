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
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
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
                "score-b" (+ (count output) 0.5)}
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
       (time
        (rtest/launch-module! ipc module {:tasks 2 :threads 2}))
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
                                             {:metadata {"m1" "a" "m2" "A"}}
                                             "..."
                                             #{:model :store-read :db-read})))
       (is (thrown? Exception
                    (aor/agent-invoke-with-context foo {:metadata {"m1" "b"}} "fail" #{})))

       (TopologyUtils/advanceSimTime 60000)

       (is (= "abc!?"
              (aor/agent-invoke foo "abc" #{:store-write :db-read :db-write})))
       (is (= "eeeee!?"
              (aor/agent-invoke-with-context foo {:metadata {"m2" "B"}} "eeeee" #{:model})))

       (TopologyUtils/advanceSimTime 60000)
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
                                 (* 1000 po/DAY-GRANULARITY)
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
         (is (= {0 {"A" {"_aor/default" {:count 1 :rest-sum 1}}}
                 1 {"B" {"_aor/default" {:count 1 :rest-sum 1}}}}
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
         (is (= {0 {"A" {"_aor/default" {:count 1 :rest-sum 532}}}
                 1 {"B" {"_aor/default" {:count 1 :rest-sum 503}}}}
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
         (is (= {0 {"A" {"_aor/default" {:count 1 :rest-sum 2}}}
                 1 {"B" {"_aor/default" {:count 1 :rest-sum 2}}}}
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


       (TopologyUtils/advanceSimTime 60000)

       (doseq [i (range 20)]
         (is
          (= "a!?"
             (aor/agent-invoke-with-context foo
                                            {:metadata {"m3" (str i)}}
                                            "a"
                                            #{}))))

       (cycle!)


       (bind res
         (ana/select-telemetry telemetry
                               "foo"
                               po/MINUTE-GRANULARITY
                               [:agent :success-rate]
                               (* 3 1000 po/MINUTE-GRANULARITY)
                               (* 1000 po/HOUR-GRANULARITY)
                               [:count :rest-sum]
                               "m3"))
       (is (= 5 (count (get res 3))))
       (is (= [1 1 1 1 1] (select [MAP-VALS MAP-VALS MAP-VALS :count] res)))
       (is (= [1 1 1 1 1] (select [MAP-VALS MAP-VALS MAP-VALS :rest-sum] res)))

       ;; TODO: <<<<>>>>
       ;;  - check every metric type
       ;;     - :mean, :min, :max, :latest, <number quantile>
       ;;  - check all granularities (hour, day, 30-day)
       ;;  - get all agent metrics query topology
      ))))
