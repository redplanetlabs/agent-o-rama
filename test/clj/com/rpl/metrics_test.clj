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
      (when (h/contains-string? m "fail-model")
        (throw (ex-info "fail model" {})))
      (TopologyUtils/advanceSimTime 150)
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
               {"score-a" (count input)
                "score-b" (count output)}
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
               (fn [agent-node input]
                 (TopologyUtils/advanceSimTime 3)
                 (let [p (aor/get-store agent-node "$$p")]
                   (store/pstate-transform! [(advancer-pred 12) (termval "a")]
                                            p
                                            :a)
                   (lc4j/basic-chat (aor/get-agent-object agent-node "my-model") input)
                   (aor/emit! agent-node "a" (str input "!")))))
              (aor/node
               "a"
               nil
               (fn [agent-node input]
                 (let [^EmbeddingStore es (aor/get-agent-object agent-node "emb")
                       p (aor/get-store agent-node "$$p")]
                   (.add es (tc/embedding 1.0 2.0))
                   (.search es
                            (EmbeddingSearchRequest. (tc/embedding 0.1 0.3)
                                                     (int 5)
                                                     0.75
                                                     (IsEqualTo. "b" 2)))
                   (.add es (tc/embedding 1.0 2.0))
                   (store/pstate-select-one [:a (advancer-pred 14)] p)
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

       (is (= "ab!?" (aor/agent-invoke foo "ab")))
       (is (= "...!?" (aor/agent-invoke foo "...")))
       (is (thrown? Exception (aor/agent-invoke foo "fail")))

       (TopologyUtils/advanceSimTime 60000)

       (is (= "abc!?" (aor/agent-invoke foo "abc")))
       (is (= "eeeee!?" (aor/agent-invoke foo "eeeee")))

       (cycle!)
       (cycle!)


       (doseq [metric-id [[:agent :success-rate]
                          [:agent :latency]
                          [:agent :model-call-count]
                          [:agent :token-counts]
                          [:agent :model-success-rate]
                          [:agent :model-latency]
                          [:agent :store-read-latency]
                          [:agent :store-write-latency]
                          [:agent :db-read-latency]
                          [:agent :db-write-latency]
                          [:agent :first-token-time]
                          [:agent :model-first-token-time]
                          [:eval :rule1 :concise?]
                          [:eval :rule2 :score-a]
                          [:eval :rule2 :score-b]
                         ]]
         (println "METRIC" metric-id)
         (clojure.pprint/pprint
          (ana/select-telemetry telemetry
                                "foo"
                                60
                                metric-id
                                0
                                (* 1000 60 60)
                                [:count :rest-sum]
                                nil))
         (println "\n")
         (clojure.pprint/pprint
          (ana/select-telemetry telemetry
                                "foo"
                                60
                                metric-id
                                0
                                (* 1000 60 60)
                                [:count :rest-sum]
                                "aor/status"))

         (println "----------------------------------\n\n")
       )

       ;; TODO: <<<<>>>>> model first token time not working


       ;; TODO: <<<<>>>>
       ;;  - agent needs mock chat model, streaming, and token counts
       ;;  - needs some model failures
       ;;  - needs store reads/writes
       ;;  - needs database reads/writes (mock embedding store)
       ;;  - need mixture of success and failures
       ;;  - some with metadata, some without
       ;;  - some metadata with high cardinality

      ))))
