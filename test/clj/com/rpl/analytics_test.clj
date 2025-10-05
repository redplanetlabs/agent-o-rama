(ns com.rpl.analytics-test
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
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [jsonista.core :as j]
   [meander.epsilon :as m]
   [org.httpkit.server :as server]
   [taoensso.nippy :as nippy])
  (:import
   [com.rpl.agentorama
    AgentFailedException]
   [com.rpl.aortest
    TestSnippets]
   [com.rpl.rama.helpers
    TopologyUtils]
   [dev.langchain4j.data.message
    AiMessage
    UserMessage]
   [dev.langchain4j.model.chat
    ChatModel]
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]
   [dev.langchain4j.model.output
    FinishReason
    TokenUsage]))

(defn ai-stats [& args] (apply aor-types/->AgentInvokeStatsImpl args))
(defn bai-stats [& args] (apply aor-types/->BasicAgentInvokeStatsImpl args))
(defn op-stats [& args] (apply aor-types/->OpStatsImpl args))
(defn nop-info [& args] (apply aor-types/->NestedOpInfoImpl args))
(defn sa-ref [& args] (apply aor-types/->AgentRefImpl args))
(defn sa-stats [& args] (apply aor-types/->SubagentInvokeStatsImpl args))

(deftest mk-node-stats-test
  (is (= (stats/mk-node-stats "a" 3 5 [])
         (ai-stats {} (bai-stats {} 0 0 0 {"a" (op-stats 1 2)}))))
  (is
   (=
    (stats/mk-node-stats
     "bb"
     3
     6
     [(nop-info 1 2 :other {})
      (nop-info 1 3 :tool-call {})
      (nop-info 1 6 :db-write {})
      (nop-info 1 2 :model-call {"outputTokenCount" 6})
      (nop-info 3 10 :other {})
      (nop-info 1 2 :model-call {"inputTokenCount" 3 "outputTokenCount" 4 "totalTokenCount" 20})
      (nop-info 1 11 :model-call {"inputTokenCount" 1 "outputTokenCount" 10 "totalTokenCount" 100})
      (nop-info 1 5 :db-write {})
     ])
    (ai-stats
     {}
     (bai-stats
      {:other      (op-stats 2 8)
       :tool-call  (op-stats 1 2)
       :db-write   (op-stats 2 9)
       :model-call (op-stats 3 12)}
      4
      20
      120
      {"bb" (op-stats 1 3)}))))

  (is
   (=
    (ai-stats
     {(sa-ref "M1" "A1")
      (sa-stats 4
                (bai-stats {:other    (op-stats 5 10)
                            :db-write (op-stats 3 7)}
                           12
                           15
                           18
                           {"abc" (op-stats 1020 1040)
                            "q"   (op-stats 1 2)}))

      (sa-ref "M1" "A2")
      (sa-stats 5
                (bai-stats {:other (op-stats 20 31)}
                           11
                           14
                           17
                           {"q"   (op-stats 2 4)
                            "abc" (op-stats 10 20)}))
     }
     (bai-stats
      {:agent-call (op-stats 6 25)}
      0
      0
      0
      {"abc" (op-stats 1 3)}))
    (stats/mk-node-stats
     "abc"
     3
     6
     [(nop-info 1
                5
                :agent-call
                {"agent-module-name" "M1"
                 "agent-name"        "A3"})
      (nop-info 1
                5
                :agent-call
                {"agent-module-name" "M1"
                 "agent-name" "A1"
                 "stats"      3})
      (nop-info 1
                6
                :agent-call
                {"agent-module-name" 1
                 "agent-name" 2
                 "stats"      3})
      (nop-info 1
                5
                :agent-call
                {"agent-module-name" "M1"
                 "agent-name" "A1"
                 "stats"
                 (ai-stats
                  {(sa-ref "M1" "A1")
                   (sa-stats 2
                             (bai-stats {:other (op-stats 3 4)}
                                        10
                                        11
                                        12
                                        {"abc" (op-stats 1000 1000)}))
                   (sa-ref "M1" "A2")
                   (sa-stats 3
                             (bai-stats {:other (op-stats 18 19)}
                                        5
                                        6
                                        7
                                        {"q" (op-stats 1 2)}))}
                  (bai-stats {:other    (op-stats 1 3)
                              :db-write (op-stats 3 7)}
                             1
                             2
                             3
                             {"abc" (op-stats 10 20)
                              "q"   (op-stats 1 2)}))})
      (nop-info 1
                5
                :agent-call
                {"agent-module-name" "M1"
                 "agent-name" "A1"
                 "stats"
                 (ai-stats
                  {(sa-ref "M1" "A2")
                   (sa-stats 1
                             (bai-stats {:other (op-stats 1 9)}
                                        5
                                        6
                                        7
                                        {"q" (op-stats 1 2)}))}
                  (bai-stats {:other (op-stats 1 3)}
                             1
                             2
                             3
                             {"abc" (op-stats 10 20)}))})
      (nop-info 1
                5
                :agent-call
                {"agent-module-name" "M1"
                 "agent-name" "A2"
                 "stats"
                 (ai-stats
                  {}
                  (bai-stats {:other (op-stats 1 3)}
                             1
                             2
                             3
                             {"abc" (op-stats 10 20)}))})
     ])))
)

(deftest aggregated-basic-stats-test
  (is (=
       (bai-stats
        {:agent-call (op-stats 6 25)
         :other      (op-stats 25 41)
         :db-write   (op-stats 3 7)}
        111
        163
        215
        {"abc" (op-stats 1031 1063)
         "q"   (op-stats 3 6)})
       (stats/aggregated-basic-stats
        (ai-stats
         {(sa-ref "M1" "A1")
          (sa-stats 4
                    (bai-stats {:other    (op-stats 5 10)
                                :db-write (op-stats 3 7)}
                               1
                               2
                               3
                               {"abc" (op-stats 1020 1040)
                                "q"   (op-stats 1 2)}))

          (sa-ref "M1" "A2")
          (sa-stats 5
                    (bai-stats {:other (op-stats 20 31)}
                               10
                               11
                               12
                               {"q"   (op-stats 2 4)
                                "abc" (op-stats 10 20)}))
         }
         (bai-stats
          {:agent-call (op-stats 6 25)}
          100
          150
          200
          {"abc" (op-stats 1 3)})))
      )))

(defrecord MockChatModel []
  ChatModel
  (doChat [this request]
    (let [^UserMessage m (-> request
                             .messages
                             last)
          c (count (.singleText m))]
      (-> (ChatResponse$Builder.)
          (.aiMessage (AiMessage. "!!!"))
          (.finishReason FinishReason/STOP)
          (.modelName "aor-model")
          (.tokenUsage (TokenUsage. (int c) (int (+ c 10)) (int (+ c 15))))
          .build))))

(deftest agent-trace-analytics-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-agent-object-builder
         topology
         "my-model"
         (fn [setup] (->MockChatModel)))
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             "node1"
             (fn [agent-node]
               (let [bar   (aor/agent-client agent-node "bar")
                     model (aor/get-agent-object agent-node "my-model")]
                 (lc4j/basic-chat model "..")
                 (lc4j/basic-chat model "...")
                 (aor/emit! agent-node
                            "node1"
                            (aor/agent-invoke bar)))))
            (aor/node
             "node1"
             nil
             (fn [agent-node v]
               (let [model (aor/get-agent-object agent-node "my-model")]
                 (aor/record-nested-op! agent-node :other 1 3 {})
                 (lc4j/basic-chat model "..........")
                 (aor/result! agent-node v)))
            ))
        (-> topology
            (aor/new-agent "bar")
            (aor/node
             "start"
             "q"
             (fn [agent-node]
               (aor/emit! agent-node "q")))
            (aor/node
             "q"
             nil
             (fn [agent-node]
               (let [model (aor/get-agent-object agent-node "my-model")]
                 (lc4j/basic-chat model ".")
                 (aor/result! agent-node :done)
               ))))
        (-> topology
            (aor/new-agent "fib")
            (aor/node
             "start"
             nil
             (fn [agent-node v]
               (let [fib (aor/agent-client agent-node "fib")]
                 (if (#{0 1} v)
                   (aor/result! agent-node 1)
                   (aor/result!
                    agent-node
                    (+ (aor/agent-invoke fib (- v 1))
                       (aor/agent-invoke fib (- v 2)))
                   ))
               ))))
       ))
     (rtest/launch-module! ipc module {:tasks 2 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind fib (aor/agent-client agent-manager "fib"))

     (bind foo-root
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind fib-root
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "fib")))

     (bind fetch-stats
       (fn [root inv]
         (foreign-select-one
          [(keypath (:agent-invoke-id inv)) :stats]
          root
          {:pkey (:task-id inv)})))


     (bind inv (aor/agent-initiate fib 4))
     (is (= 5 (aor/agent-result fib inv)))
     (is
      (trace-matches?
       (fetch-stats fib-root inv)
       {:subagent-stats
        {{:module-name !m1
          :agent-name  "fib"}
         {:count       8
          :basic-stats
          {:nested-op-stats    {:agent-call {:count 12 :total-time-millis ?t1}}
           :input-token-count  0
           :output-token-count 0
           :total-token-count  0
           :node-stats         {"start" {:count 8 :total-time-millis ?t2}}}}}
        :basic-stats
        {:nested-op-stats    {:agent-call {:count 4 :total-time-millis ?t3}}
         :input-token-count  0
         :output-token-count 0
         :total-token-count  0
         :node-stats         {"start" {:count 1 :total-time-millis ?t4}}}}
       (m/guard
        (= !m1 module-name))
      ))

     (bind inv (aor/agent-initiate foo))
     (is (= :done (aor/agent-result foo inv)))
     (is
      (trace-matches?
       (fetch-stats foo-root inv)
       {:subagent-stats
        {{:module-name !module-name
          :agent-name  "bar"}
         {:count       1
          :basic-stats
          {:nested-op-stats    {:model-call {:count 1 :total-time-millis ?t1}}
           :input-token-count  1
           :output-token-count 11
           :total-token-count  16
           :node-stats
           {"start" {:count 1 :total-time-millis ?t2}
            "q"     {:count 1 :total-time-millis ?t3}}}}}
        :basic-stats
        {:nested-op-stats
         {:model-call {:count 3 :total-time-millis ?t4}
          :agent-call {:count 2 :total-time-millis ?t5}
          :other      {:count 1 :total-time-millis 2}}
         :input-token-count  15
         :output-token-count 45
         :total-token-count  60
         :node-stats
         {"start" {:count 1 :total-time-millis ?t6}
          "node1" {:count 1 :total-time-millis ?t7}}}}
       (m/guard
        (= !module-name module-name))
      ))
    )))


(deftest comparator-spec-test
  (letlocals
   (bind spec (aor-types/->valid-ComparatorSpec := 6))
   (is (aor-types/comparator-spec-matches? spec 6))
   (is (not (aor-types/comparator-spec-matches? spec 7)))
   (is (not (aor-types/comparator-spec-matches? spec "6")))
   (bind spec (aor-types/->valid-ComparatorSpec := "aaa"))
   (is (aor-types/comparator-spec-matches? spec "aaa"))
   (is (not (aor-types/comparator-spec-matches? spec "abc")))

   (bind spec (aor-types/->valid-ComparatorSpec :not= 6))
   (is (not (aor-types/comparator-spec-matches? spec 6)))
   (is (aor-types/comparator-spec-matches? spec 7))
   (is (aor-types/comparator-spec-matches? spec "6"))

   (bind spec (aor-types/->valid-ComparatorSpec :< 10))
   (is (not (aor-types/comparator-spec-matches? spec 10)))
   (is (not (aor-types/comparator-spec-matches? spec 11)))
   (is (aor-types/comparator-spec-matches? spec 9))

   (bind spec (aor-types/->valid-ComparatorSpec :> 10))
   (is (not (aor-types/comparator-spec-matches? spec 10)))
   (is (not (aor-types/comparator-spec-matches? spec 9)))
   (is (aor-types/comparator-spec-matches? spec 11))

   (bind spec (aor-types/->valid-ComparatorSpec :<= 10))
   (is (aor-types/comparator-spec-matches? spec 10))
   (is (not (aor-types/comparator-spec-matches? spec 11)))
   (is (aor-types/comparator-spec-matches? spec 9))

   (bind spec (aor-types/->valid-ComparatorSpec :>= 10))
   (is (aor-types/comparator-spec-matches? spec 10))
   (is (not (aor-types/comparator-spec-matches? spec 9)))
   (is (aor-types/comparator-spec-matches? spec 11))
  ))

(deftest rule-filters-test
  ;; use actual PState schemas to ensure data matches what it would in full application
  (with-open [root  (rtest/create-test-pstate po/AGENT-ROOT-PSTATE-SCHEMA)
              nodes (rtest/create-test-pstate po/AGENT-NODE-PSTATE-SCHEMA)]
    (letlocals

     (bind id (h/random-uuid7))
     (bind tp-rule-filter-matches?
       (fn [pstate filter data]
         (rtest/test-pstate-transform [(keypath id) (termval data)] pstate)
         (aor-types/rule-filter-matches? filter
                                         (assoc (into {}
                                                      (rtest/test-pstate-select-one (keypath id)
                                                                                    pstate))
                                          :run-type (if (identical? pstate root) :agent :node)))
       ))


     (bind ai (aor-types/->valid-AgentInvokeImpl 0 (h/random-uuid7)))
     (bind filter
       (aor-types/->valid-FeedbackFilter "xyz" "abc" (aor-types/->valid-ComparatorSpec := 6)))

     (bind matching-source
       (aor-types/->valid-EvalSourceImpl
        "blah"
        ai
        (aor-types/->valid-ActionSourceImpl "aaa" "xyz")))

     (is (= #{"xyz"} (aor-types/dependency-rule-names filter)))

     (is (not
          (tp-rule-filter-matches?
           root
           filter
           {:feedback {:results [(aor-types/->FeedbackImpl {"abc" 6}
                                                           (aor-types/->AiSourceImpl)
                                                           0
                                                           0)]}})))
     (is (tp-rule-filter-matches?
          root
          filter
          {:feedback {:results [(aor-types/->valid-FeedbackImpl
                                 {"abc" 6}
                                 matching-source
                                 0
                                 0)]}}))
     (is (not
          (tp-rule-filter-matches?
           nodes
           filter
           {:feedback {:results [(aor-types/->FeedbackImpl {"def" 6}
                                                           matching-source
                                                           0
                                                           0)]}})))
     (is (not (tp-rule-filter-matches?
               nodes
               filter
               {:feedback {:results [(aor-types/->valid-FeedbackImpl
                                      {"abc" "6"}
                                      matching-source
                                      0
                                      0)]}})))

     (bind filter
       (aor-types/->valid-LatencyFilter (aor-types/->valid-ComparatorSpec :> 10)))
     (is (= #{} (aor-types/dependency-rule-names filter)))
     (is (not (tp-rule-filter-matches?
               root
               filter
               {:start-time-millis  10
                :finish-time-millis 20})))
     (is (tp-rule-filter-matches?
          root
          filter
          {:start-time-millis  10
           :finish-time-millis 21}))
     (is (tp-rule-filter-matches?
          nodes
          filter
          {:start-time-millis  10
           :finish-time-millis 21}))

     (bind filter (aor-types/->valid-ErrorFilter))
     (is (= #{} (aor-types/dependency-rule-names filter)))
     (is (not (tp-rule-filter-matches? root filter {})))
     (is (not (tp-rule-filter-matches? nodes filter {})))
     (is (not (tp-rule-filter-matches? root filter {:exception-summaries []})))
     (is
      (tp-rule-filter-matches?
       root
       filter
       {:exception-summaries [(aor-types/->ExceptionSummary "aaa" "bbb" (h/random-uuid7))]}))
     (is
      (not
       (tp-rule-filter-matches?
        nodes
        filter
        {:exceptions []})))
     (is
      (tp-rule-filter-matches?
       nodes
       filter
       {:exceptions ["abc"]}))


     (bind filter
       (aor-types/->valid-InputMatchFilter "$[0].a" #"abc"))
     (is (= #{} (aor-types/dependency-rule-names filter)))
     (is (not (tp-rule-filter-matches? root filter {:invoke-args [{"a" "aaa"} {"b" "abc"}]})))
     (is (tp-rule-filter-matches? root filter {:invoke-args [{"a" "qqqabcqqq"}]}))
     (is (not (tp-rule-filter-matches? nodes filter {:input [{"a" "aaa"}]})))
     (is (tp-rule-filter-matches? nodes filter {:input [{"a" "qqqabcqqq"}]}))

     (bind filter
       (aor-types/->valid-OutputMatchFilter "$[0].args[1]" #"abc"))
     (is (= #{} (aor-types/dependency-rule-names filter)))
     (is (not
          (tp-rule-filter-matches? root
                                   filter
                                   {:result (aor-types/->AgentResult [{"args" [1 "aaa"]}] false)})))
     (is (tp-rule-filter-matches? root
                                  filter
                                  {:result (aor-types/->AgentResult [{"args" [1 "1abc2"]}] false)}))
     (is (not
          (tp-rule-filter-matches? nodes
                                   filter
                                   {:result (aor-types/->AgentResult [{"args" [1 "aaa"]}] false)})))
     (is (tp-rule-filter-matches? nodes
                                  filter
                                  {:result (aor-types/->AgentResult [{"args" [1 "1abc2"]}] false)}))
     (is
      (not (tp-rule-filter-matches? nodes
                                    filter
                                    {:emits [(aor-types/->AgentNodeEmit id nil 0 "a" [0 "aaa"])]})))
     (is (tp-rule-filter-matches? nodes
                                  filter
                                  {:emits [(aor-types/->AgentNodeEmit id nil 0 "a" [0 "1abc2"])]}))



     (bind token-filter
       (fn [k v]
         (aor-types/->valid-TokenCountFilter k (aor-types/->valid-ComparatorSpec :> v))))
     (is (= #{} (aor-types/dependency-rule-names (token-filter :input 1))))

     (bind root-stats
       (ai-stats
        {(sa-ref "M1" "A1")
         (sa-stats 4
                   (bai-stats {:other    (op-stats 5 10)
                               :db-write (op-stats 3 7)}
                              1
                              2
                              3
                              {"abc" (op-stats 1020 1040)
                               "q"   (op-stats 1 2)}))

        }
        (bai-stats
         {:agent-call (op-stats 6 25)}
         10
         11
         12
         {"abc" (op-stats 1 3)})))

     (is (not (tp-rule-filter-matches? root
                                       (token-filter :input 11)
                                       {:stats root-stats})))
     (is (tp-rule-filter-matches? root
                                  (token-filter :input 10)
                                  {:stats root-stats}))
     (is (not (tp-rule-filter-matches? root
                                       (token-filter :output 13)
                                       {:stats root-stats})))
     (is (tp-rule-filter-matches? root
                                  (token-filter :output 12)
                                  {:stats root-stats}))
     (is (not (tp-rule-filter-matches? root
                                       (token-filter :total 15)
                                       {:stats root-stats})))
     (is (tp-rule-filter-matches? root
                                  (token-filter :total 14)
                                  {:stats root-stats}))


     (bind nested-ops
       [(aor-types/->NestedOpInfoImpl
         0
         0
         :other
         {"inputTokenCount"  1000
          "outputTokenCount" 1000
          "totalTokenCount"  1000})
        (aor-types/->NestedOpInfoImpl
         0
         0
         :model-call
         {"inputTokenCount" 1
          "totalTokenCount" 3})
        (aor-types/->NestedOpInfoImpl
         0
         0
         :model-call
         {"inputTokenCount"  10
          "outputTokenCount" 11
          "totalTokenCount"  12})
        (aor-types/->NestedOpInfoImpl
         0
         0
         :model-call
         {"outputTokenCount" 101})])

     (is (not (tp-rule-filter-matches? nodes
                                       (token-filter :input 11)
                                       {:nested-ops nested-ops})))
     (is (tp-rule-filter-matches? nodes
                                  (token-filter :input 10)
                                  {:nested-ops nested-ops}))
     (is (not (tp-rule-filter-matches? nodes
                                       (token-filter :output 112)
                                       {:nested-ops nested-ops})))
     (is (tp-rule-filter-matches? nodes
                                  (token-filter :output 111)
                                  {:nested-ops nested-ops}))
     (is (not (tp-rule-filter-matches? nodes
                                       (token-filter :total 15)
                                       {:nested-ops nested-ops})))
     (is (tp-rule-filter-matches? nodes
                                  (token-filter :total 14)
                                  {:nested-ops nested-ops}))

     (bind filter (aor-types/->valid-AndFilter []))
     (is (tp-rule-filter-matches? root filter {}))
     (bind filter
       (aor-types/->valid-AndFilter
        [(aor-types/->valid-LatencyFilter (aor-types/->ComparatorSpec :> 10))
         (aor-types/->valid-LatencyFilter (aor-types/->ComparatorSpec :< 20))]))
     (is (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 111}))
     (is (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 118}))
     (is (not
          (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 110})))
     (is (not
          (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 120})))

     (bind filter
       (aor-types/->valid-AndFilter
        [(aor-types/->valid-FeedbackFilter "xyz" "a" (aor-types/->ComparatorSpec :> 10))
         (aor-types/->valid-FeedbackFilter "xyz" "b" (aor-types/->ComparatorSpec :> 10))
         (aor-types/->valid-FeedbackFilter "cba" "a" (aor-types/->ComparatorSpec :> 10))]))
     (is (= #{"xyz" "cba"} (aor-types/dependency-rule-names filter)))

     (bind filter (aor-types/->valid-OrFilter []))
     (is (not (tp-rule-filter-matches? root filter {})))
     (bind filter
       (aor-types/->valid-OrFilter
        [(aor-types/->valid-LatencyFilter (aor-types/->ComparatorSpec :< 10))
         (aor-types/->valid-LatencyFilter (aor-types/->ComparatorSpec :> 20))]))
     (is (not
          (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 111})))
     (is (not
          (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 118})))
     (is (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 101}))
     (is (tp-rule-filter-matches? root filter {:start-time-millis 100 :finish-time-millis 125}))
     (bind filter
       (aor-types/->valid-OrFilter
        [(aor-types/->valid-FeedbackFilter "xyz" "a" (aor-types/->ComparatorSpec :> 10))
         (aor-types/->valid-FeedbackFilter "xyz" "b" (aor-types/->ComparatorSpec :> 10))
         (aor-types/->valid-FeedbackFilter "cba" "a" (aor-types/->ComparatorSpec :> 10))]))
     (is (= #{"xyz" "cba"} (aor-types/dependency-rule-names filter)))


     (bind filter
       (aor-types/->valid-NotFilter
        (aor-types/->valid-LatencyFilter (aor-types/->ComparatorSpec :> 10))))
     (is (tp-rule-filter-matches? root filter {:start-time-millis 10 :finish-time-millis 18}))
     (is (not
          (tp-rule-filter-matches? root filter {:start-time-millis 10 :finish-time-millis 100})))
     (bind filter
       (aor-types/->valid-NotFilter
        (aor-types/->valid-FeedbackFilter "xyz" "a" (aor-types/->ComparatorSpec :> 10))))
     (is (= #{"xyz"} (aor-types/dependency-rule-names filter)))
    )))

(defn expected-counts
  [m]
  (->> (for [[task-id agents]   m
             [agent-name rules] agents
             [rule-name count]  rules]
         [{:task-id    task-id
           :agent-name agent-name
           :rule-name  rule-name}
          count])
       (into {})))

(deftest to-action-queue-test
  (let [data   {0 {"A" {"A-R1" 1
                        "A-R2" 3}
                   "B" {"B-R1" 0
                        "B-R2" 1
                        "B-R3" 4}}
                1 {"A" {"A-R1" 2
                        "A-R2" 1}
                   "B" {"B-R1" 1
                        "B-R2" 2
                        "B-R3" 3}}
                2 {"A" {"A-R1" 3}
                   "B" {"B-R1" 3
                        "B-R2" 1
                        "B-R3" 9}}}
        out    (vec (ana/to-action-queue data))
        freqs  (frequencies out)
        expect (expected-counts data)]
    (is (= (into {} (remove (comp zero? val) expect))
           freqs))))

(deftest sample?-test
  (letlocals
   (bind counter (volatile! 0))
   (dotimes [_ 10000]
     (if (ana/sample? 0.5) (vswap! counter inc)))
   (is (< 4500 @counter 5500))
   (bind counter (volatile! 0))
   (dotimes [_ 10000]
     (if (ana/sample? 0.25) (vswap! counter inc)))
   (is (< 2000 @counter 3000))
  ))

(defn split-on
  [delim coll]
  (remove #(= [delim] %)
   (partition-by #(= % delim) coll)))

(def ACTIONS)
(def TICKS)

(deftest actions-test
  (let [sample-rates (atom [])
        sample-atom  (atom true)
        event-log    (atom [])]
    (with-redefs [ACTIONS (atom [])
                  TICKS (atom 0)
                  i/SUBSTITUTE-TICK-DEPOTS true

                  i/hook:analytics-tick
                  (fn [& args] (swap! TICKS inc))

                  ana/sample?
                  (fn [sampling-rate]
                    (swap! sample-rates conj sampling-rate)
                    @sample-atom)

                  ana/hook:run-action
                  (fn [run-info]
                    (swap! event-log conj (:rule-name run-info)))

                  ana/hook:analytics-loop-iter*
                  (fn [& args]
                    (swap! event-log conj :loop))

                  anode/gen-node-id
                  (fn [& args]
                    (h/random-uuid7-at-timestamp (h/current-time-millis)))

                  at/gen-new-agent-id
                  (fn [agent-name]
                    (if (#{"foo" "bar"} agent-name)
                      (do
                        (let [ret (h/random-uuid7-at-timestamp (h/current-time-millis))]
                          (TopologyUtils/advanceSimTime 10000)
                          ret
                        ))
                      (h/random-uuid7)))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (aor/declare-action-builder
             topology
             "action1"
             "does a thing"
             (fn [params]
               (fn [fetcher input output run-info]
                 (swap! ACTIONS conj
                   [:action1
                    input
                    output
                    (select-keys run-info
                                 [:action-name :agent-name :node-name :type :latency-millis])
                    (select [:feedback ALL :scores] run-info)
                    (mapv aor-types/NestedOpInfoImpl? (:nested-ops run-info))
                    (aor-types/AgentInvokeStatsImpl? (:agent-stats run-info))
                   ])
                 {"abc" "ccc"
                  "xyz" "..."}
               )))
            (aor/declare-action-builder
             topology
             "action2"
             "does a thing 2"
             (fn [params]
               (fn [fetcher input output run-info]
                 (swap! ACTIONS conj
                   [:action2 input output params])
                 {"abc" (str input "-" output)
                  "xyz" "zyx"}))
             {:params {"a1" {:description "param1" :default "1"}
                       "a2" {:description "param2"}}
              :limit-concurrency? true})
            (TestSnippets/declareActionBuilders topology)
            (-> topology
                (aor/new-agent "foo")
                (aor/node
                 "start"
                 ["node1" "as"]
                 (fn [agent-node input]
                   (if (= input "agg")
                     (aor/emit! agent-node "as")
                     (aor/emit! agent-node "node1" (str input "!")))))
                (aor/node
                 "node1"
                 nil
                 (fn [agent-node input]
                   (aor/result! agent-node (str input "?"))))
                (aor/agg-start-node
                 "as"
                 "agg"
                 (fn [agent-node]
                   (aor/emit! agent-node "agg" 1)
                   (aor/emit! agent-node "agg" 2)))
                (aor/agg-node
                 "agg"
                 nil
                 aggs/+sum
                 (fn [agent-node agg node-start-res]
                   (aor/result! agent-node (str agg "!")))))
            (-> topology
                (aor/new-agent "bar")
                (aor/node
                 "begin"
                 "n1"
                 (fn [agent-node input]
                   (if (map? input)
                     (aor/emit! agent-node "n1" (setval [MAP-VALS END] "+" input))
                     (aor/emit! agent-node "n1" (str input "+")))))
                (aor/node
                 "n1"
                 nil
                 (fn [agent-node input]
                   (if (map? input)
                     (do
                       (aor/record-nested-op! agent-node :other 1 2 {})
                       (TopologyUtils/advanceSimTime 1)
                       (aor/result! agent-node (setval [MAP-VALS END] "-" input)))
                     (aor/result! agent-node (str input "-"))))))
           ))
         (rtest/launch-module! ipc module {:tasks 2 :threads 2})
         (bind module-name (get-module-name module))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind global-actions-depot
           (:global-actions-depot (aor-types/underlying-objects agent-manager)))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind bar (aor/agent-client agent-manager "bar"))
         (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
         (bind foo-cursors
           (foreign-pstate ipc module-name (po/agent-rule-cursors-task-global-name "foo")))

         (bind foo-rules
           (:agent-rules-pstate (aor-types/underlying-objects
                                 foo)))

         (bind all-action-builders-query
           (:all-action-builders-query (aor-types/underlying-objects agent-manager)))

         (bind all-builders (foreign-invoke-query all-action-builders-query))
         (is (= (set (concat (keys ana/BUILT-IN-ACTIONS) ["action1" "action2" "action3" "action4"]))
                (set (keys all-builders))))

         (TopologyUtils/advanceSimTime 1000)

         (bind foo-root
           (foreign-pstate ipc
                           module-name
                           (po/agent-root-task-global-name "foo")))
         (bind bar-root
           (foreign-pstate ipc
                           module-name
                           (po/agent-root-task-global-name "bar")))

         (bind foo-feedback
           (fn [{:keys [task-id agent-invoke-id]}]
             (foreign-select-one [(keypath agent-invoke-id) :feedback :results]
                                 foo-root
                                 {:pkey task-id})
           ))

         (bind foo-action-log (:action-log-query (aor-types/underlying-objects foo)))

         (bind cycle!
           (fn []
             (reset! TICKS 0)
             (reset! sample-rates [])
             (reset! ACTIONS [])
             (reset! event-log [])
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
                                "concise7"
                                "aor/conciseness"
                                {"threshold" "7"}
                                "")
         (aor/create-evaluator! agent-manager
                                "mconcise5"
                                "aor/conciseness"
                                {"threshold" "5"}
                                ""
                                {:output-json-path "$.abc"})

         (ana/add-rule! global-actions-depot
                        "eval1"
                        "foo"
                        {:action-name       "aor/eval"
                         :action-params     {"name" "concise5"}
                         :filter            (aor-types/->AndFilter [])
                         :sampling-rate     0.5
                         :start-time-millis 15000
                         :status-filter     :success
                        })


         (bind inv1 (aor/agent-initiate foo "ab"))
         (bind inv2 (aor/agent-initiate foo ".."))
         (bind inv3 (aor/agent-initiate foo "abcd"))
         (bind inv4 (aor/agent-initiate foo ".."))
         (is (= "ab!?" (aor/agent-result foo inv1)))
         (is (= "..!?" (aor/agent-result foo inv2)))
         (is (= "abcd!?" (aor/agent-result foo inv3)))
         (is (= "..!?" (aor/agent-result foo inv4)))

         (cycle!)

         (is (nil? (foo-feedback inv1)))
         (is (nil? (foo-feedback inv2)))
         (bind fb (foo-feedback inv3))
         (is (= 1 (count fb)))
         (is (= {"concise?" false}
                (-> fb
                    first
                    :scores)))
         (is (= "concise5"
                (-> fb
                    first
                    :source
                    :eval-name)))

         (bind fb (foo-feedback inv4))
         (is (= {"concise?" true}
                (-> fb
                    first
                    :scores)))
         (is (= "concise5"
                (-> fb
                    first
                    :source
                    :eval-name)))

         (is (= [0.5 0.5] @sample-rates))

         (ana/add-rule!
          global-actions-depot
          "foo-a1"
          "foo"
          {:action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->FeedbackFilter "eval1"
                                                          "concise?"
                                                          (aor-types/->ComparatorSpec := true))
           :sampling-rate     1.0
           :start-time-millis 0
           :status-filter     :success
          })


         (bind inv5 (aor/agent-initiate foo "."))
         (is (= ".!?" (aor/agent-result foo inv5)))

         (cycle!)

         (bind fb (foo-feedback inv5))
         (is (= {"concise?" true}
                (-> fb
                    first
                    :scores)))
         (is (= "concise5"
                (-> fb
                    first
                    :source
                    :eval-name)))
         (is (= {0.5 1 1.0 1} (frequencies @sample-rates)))
         (is (= @ACTIONS
                [[:action1 [".."] "..!?"
                  {:action-name    "action1"
                   :agent-name     "foo"
                   :node-name      nil
                   :type           :agent
                   :latency-millis 0}
                  [{"concise?" true}]
                  []
                  true]]))

         (cycle!)
         (is (= [1.0] @sample-rates))
         (is (= @ACTIONS
                [[:action1 ["."] ".!?"
                  {:action-name    "action1"
                   :agent-name     "foo"
                   :node-name      nil
                   :type           :agent
                   :latency-millis 0}
                  [{"concise?" true}]
                  []
                  true]]))

         ;; sanity check
         (is (= 51000 (h/current-time-millis)))

         (ana/add-rule!
          global-actions-depot
          "eval2"
          "foo"
          {:action-name       "aor/eval"
           :action-params     {"name" "concise7"}
           :filter            (aor-types/->FeedbackFilter "eval1"
                                                          "concise?"
                                                          (aor-types/->ComparatorSpec :not= ""))
           :sampling-rate     0.1
           :start-time-millis 50000
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a2"
          "foo"
          {:action-name       "action2"
           :action-params     {"a1" "1a"
                               "a2" "XYZ"}
           :filter            (aor-types/->AndFilter
                               [(aor-types/->FeedbackFilter "eval1"
                                                            "concise?"
                                                            (aor-types/->ComparatorSpec := false))
                                (aor-types/->FeedbackFilter "eval2"
                                                            "concise?"
                                                            (aor-types/->ComparatorSpec := true))
                                (aor-types/->InputMatchFilter "$[0]" #"a")])
           :sampling-rate     0.7
           :start-time-millis 50000
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a3"
          "foo"
          {:action-name       "action3"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.8
           :start-time-millis 50000
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a4"
          "foo"
          {:action-name       "action4"
           :action-params     {"jparam1" "ZZZ"}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.9
           :start-time-millis 50000
           :status-filter     :success
          })


         (bind inv (aor/agent-initiate foo "aaaa"))
         (is (= "aaaa!?" (aor/agent-result foo inv)))

         (reset! sample-atom false)
         (cycle!)
         (is (= {0.5 1 0.8 1 0.9 1} (frequencies @sample-rates)))
         (is (= [] @ACTIONS))
         (reset! sample-atom true)
         (cycle!)
         (is (= {} (frequencies @sample-rates)))
         (is (= [] @ACTIONS))
         (cycle!)
         (is (= {} (frequencies @sample-rates)))
         (is (= [] @ACTIONS))

         (bind inv1 (aor/agent-initiate foo "dcba"))
         (is (= "dcba!?" (aor/agent-result foo inv1)))
         (bind inv2 (aor/agent-initiate foo "aaaaaaa"))
         (is (= "aaaaaaa!?" (aor/agent-result foo inv2)))
         (bind inv3 (aor/agent-initiate foo "...."))
         (is (= "....!?" (aor/agent-result foo inv3)))
         (bind inv4 (aor/agent-initiate foo "aaaa"))
         (is (= "aaaa!?" (aor/agent-result foo inv4)))
         (cycle!)
         (is (= {0.5 4 0.8 4 0.9 4} (frequencies @sample-rates)))
         (is (= @ACTIONS []))
         (cycle!)
         (is (= {0.1 4} (frequencies @sample-rates)))
         (is (= [] @ACTIONS))
         (cycle!)
         ;; this is rule dependent on eval2 which is dependent on eval1, which is why it takes 3
         ;; iters
         (is (= {0.7 2} (frequencies @sample-rates)))
         (is (= 2 (count @ACTIONS)))
         (is (= (set @ACTIONS)
                #{[:action2 ["dcba"] "dcba!?" {"a1" "1a" "a2" "XYZ"}]
                  [:action2 ["aaaa"] "aaaa!?" {"a1" "1a" "a2" "XYZ"}]}))


         (bind {:keys [actions pagination-params]}
           (foreign-invoke-query foo-action-log "foo-a3" 2 nil))
         (bind all-actions actions)
         (bind {:keys [actions pagination-params]}
           (foreign-invoke-query foo-action-log "foo-a3" 2 pagination-params))
         (bind all-actions (mapv :action (concat all-actions actions)))
         (is (= {0 nil 1 nil} pagination-params))
         (is (= 4 (count all-actions)))
         (is (every? :success? all-actions))
         (is (every? #(aor-types/AgentInvokeImpl? (:agent-invoke %)) all-actions))
         (is (every? #(nil? (:node-invoke %)) all-actions))
         ;; actions aren't done in strict order across tasks
         (is (= #{{"output" "aaaa!?" "input" ["aaaa"]}
                  {"output" "....!?" "input" ["...."]}
                  {"output" "aaaaaaa!?" "input" ["aaaaaaa"]}
                  {"output" "dcba!?" "input" ["dcba"]}}
                (set (mapv :info-map all-actions))))



         (bind {:keys [actions pagination-params]}
           (foreign-invoke-query foo-action-log "foo-a4" 10 nil))
         (bind all-actions (mapv :action actions))
         (is (every? :success? all-actions))
         (is (= #{{"output" "aaaa!?" "input" ["aaaa"] "params" {"jparam1" "ZZZ"}}
                  {"output" "....!?" "input" ["...."] "params" {"jparam1" "ZZZ"}}
                  {"output" "aaaaaaa!?" "input" ["aaaaaaa"] "params" {"jparam1" "ZZZ"}}
                  {"output" "dcba!?" "input" ["dcba"] "params" {"jparam1" "ZZZ"}}}
                (set (mapv :info-map all-actions))))


         (is (= #{"foo-a1" "foo-a2" "foo-a3" "foo-a4" "eval1" "eval2"}
                (set (keys (foreign-select-one STAY foo-cursors)))))
         (is (= #{"foo-a1" "foo-a2" "foo-a3" "foo-a4" "eval1" "eval2"}
                (set (keys (ana/fetch-agent-rules foo-rules)))))


         (is (thrown? Exception (ana/delete-rule! global-actions-depot "foo" "eval1")))
         (cycle!)
         (is (= #{"foo-a1" "foo-a2" "foo-a3" "foo-a4" "eval1" "eval2"}
                (set (keys (foreign-select-one STAY foo-cursors)))))
         (is (= #{"foo-a1" "foo-a2" "foo-a3" "foo-a4" "eval1" "eval2"}
                (set (keys (ana/fetch-agent-rules foo-rules)))))

         ;; verify cursors get deleted for deleted rule on next microbatch
         (ana/delete-rule! global-actions-depot "foo" "foo-a3")
         (cycle!)
         (is (= #{"foo-a1" "foo-a2" "foo-a4" "eval1" "eval2"}
                (set (keys (foreign-select-one STAY foo-cursors)))))
         (is (= #{"foo-a1" "foo-a2" "foo-a4" "eval1" "eval2"}
                (set (keys (ana/fetch-agent-rules foo-rules)))))


         (ana/delete-rule! global-actions-depot "foo" "foo-a4")
         (ana/delete-rule! global-actions-depot "foo" "foo-a2")
         (ana/delete-rule! global-actions-depot "foo" "foo-a1")
         (ana/delete-rule! global-actions-depot "foo" "eval2")
         (ana/delete-rule! global-actions-depot "foo" "eval1")
         (is (= #{}
                (set (keys (ana/fetch-agent-rules foo-rules)))))
         (cycle!)
         (is (= #{}
                (set (keys (foreign-select-one STAY foo-cursors)))))

         (TopologyUtils/advanceSimTime 1000)

         (ana/add-rule!
          global-actions-depot
          "foo-a1"
          "foo"
          {:action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.5
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a2"
          "foo"
          {:action-name       "action2"
           :action-params     {"a1" "!"
                               "a2" "?"}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.6
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a1" ; give it same name to verify they're independent
          "bar"
          {:action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.55
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "bar-a1"
          "bar"
          {:action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.65
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })

         (TopologyUtils/advanceSimTime 1000)


         (bind invf (aor/agent-initiate foo "lmno"))
         (is (= "lmno!?" (aor/agent-result foo invf)))
         (bind invb (aor/agent-initiate bar "mmmm"))
         (is (= "mmmm+-" (aor/agent-result bar invb)))
         (cycle!)
         (is (= {0.5 1 0.55 1 0.6 1 0.65 1} (frequencies @sample-rates)))
         (is (= 4 (count @ACTIONS)))
         (is (= (frequencies @ACTIONS)
                {[:action1
                  ["lmno"]
                  "lmno!?"
                  {:action-name    "action1"
                   :agent-name     "foo"
                   :node-name      nil
                   :type           :agent
                   :latency-millis 0}
                  []
                  []
                  true]
                 1

                 [:action2 ["lmno"] "lmno!?" {"a1" "!" "a2" "?"}]
                 1

                 [:action1
                  ["mmmm"]
                  "mmmm+-"
                  {:action-name    "action1"
                   :agent-name     "bar"
                   :node-name      nil
                   :type           :agent
                   :latency-millis 0}
                  []
                  []
                  true]
                 2}))

         (ana/delete-rule! global-actions-depot "foo" "foo-a2")
         (ana/delete-rule! global-actions-depot "bar" "foo-a1")
         (ana/delete-rule! global-actions-depot "bar" "bar-a1")
         (cycle!)

         ;; still have foo/foo-a1 at 0.5 sampling rate

         (TopologyUtils/advanceSimTime 1000)
         (ana/add-rule!
          global-actions-depot
          "foo-start"
          "foo"
          {:action-name       "action1"
           :node-name         "start"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.51
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })


         (ana/add-rule! global-actions-depot
                        "meval"
                        "bar"
                        {:node-name         "n1"
                         :action-name       "aor/eval"
                         :action-params     {"name" "mconcise5"}
                         :filter            (aor-types/->AndFilter [])
                         :sampling-rate     0.52
                         :start-time-millis (h/current-time-millis)
                         :status-filter     :success
                        })

         (ana/add-rule!
          global-actions-depot
          "bar-n1"
          "bar"
          {:node-name         "n1"
           :action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->FeedbackFilter "meval"
                                                          "concise?"
                                                          (aor-types/->ComparatorSpec :not= "a"))
           :sampling-rate     0.53
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })

         (TopologyUtils/advanceSimTime 1000)

         (bind invf (aor/agent-initiate foo "aaaa"))
         (is (= "aaaa!?" (aor/agent-result foo invf)))
         (bind invb (aor/agent-initiate bar {"abc" "nnmm"}))
         (is (= {"abc" "nnmm+-"} (aor/agent-result bar invb)))

         (cycle!)
         (is (= {0.5 1 0.51 1 0.52 1} (frequencies @sample-rates)))
         (is (= 2 (count @ACTIONS)))
         (is (= (set @ACTIONS)
                #{[:action1 ["aaaa"] "aaaa!?"
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      nil
                    :type           :agent
                    :latency-millis 0}
                   []
                   []
                   true]
                  [:action1 ["aaaa"] [{"node" "node1" "args" ["aaaa!"]}]
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      "start"
                    :type           :node
                    :latency-millis 0}
                   []
                   []
                   false]}))

         (cycle!)
         (is (= {0.53 1} (frequencies @sample-rates)))
         (is (= @ACTIONS
                [[:action1 [{"abc" "nnmm+"}] {"abc" "nnmm+-"}
                  {:action-name    "action1"
                   :agent-name     "bar"
                   :node-name      "n1"
                   :type           :node
                   :latency-millis 1}
                  [{"concise?" false}]
                  [true]
                  false]]))


         (bind {:keys [actions pagination-params]}
           (foreign-invoke-query foo-action-log "foo-start" 10 nil))
         (is (= 1 (count actions)))
         (bind a
           (-> actions
               first
               :action))
         (is (= {"abc" "ccc" "xyz" "..."}
                (:info-map a)))
         (is (:success? a))
         (is (aor-types/AgentInvokeImpl? (:agent-invoke a)))
         (is (aor-types/NodeInvokeImpl? (:node-invoke a)))

         (is (= {0 nil 1 nil} pagination-params))


         (foreign-append! global-actions-depot (aor-types/change-max-limited-actions-concurrency 2))


         ;; now verify concurrency control and limited vs. unlimited actions processing

         (ana/delete-rule! global-actions-depot "foo" "foo-start")
         (ana/delete-rule! global-actions-depot "bar" "foo-a1")
         (ana/delete-rule! global-actions-depot "bar" "bar-n1")
         (ana/delete-rule! global-actions-depot "bar" "meval")
         (ana/delete-rule! global-actions-depot "foo" "foo-a1")
         (cycle!)


         (ana/add-rule! global-actions-depot
                        "eval1"
                        "foo"
                        {:action-name       "aor/eval"
                         :action-params     {"name" "concise5"}
                         :filter            (aor-types/->AndFilter [])
                         :sampling-rate     0.6
                         :start-time-millis (h/current-time-millis)
                         :status-filter     :success
                        })
         (ana/add-rule!
          global-actions-depot
          "foo-a1"
          "foo"
          {:action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.61
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-a2"
          "foo"
          {:action-name       "action2"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.62
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })


         (bind inv1 (aor/agent-initiate foo "a"))
         (bind inv2 (aor/agent-initiate foo "bbbb"))
         (bind inv3 (aor/agent-initiate foo "c"))
         ;; verifies agent runs from experiments are ignored
         (bind inv4
           (binding [aor-types/OPERATION-SOURCE
                     (aor-types/->valid-ExperimentSourceImpl (h/random-uuid7) (h/random-uuid7))]
             (aor/agent-initiate foo "d")))
         (bind inv5 (aor/agent-initiate foo "e"))
         (is (= "a!?" (aor/agent-result foo inv1)))
         (is (= "bbbb!?" (aor/agent-result foo inv2)))
         (is (= "c!?" (aor/agent-result foo inv3)))
         (is (= "d!?" (aor/agent-result foo inv4)))
         (is (= "e!?" (aor/agent-result foo inv5)))
         (cycle!)

         (bind iters (split-on :loop @event-log))
         (is (= (repeat 4 "foo-a1") (first iters)))
         (is (every? #(= 2 (count %)) (rest iters)))
         (is (= #{"foo-a2" "eval1"} (set (apply concat (rest iters)))))


         ;; now verify node processing when there are aggs
         (ana/delete-rule! global-actions-depot "foo" "foo-a1")
         (ana/delete-rule! global-actions-depot "foo" "foo-a2")
         (ana/delete-rule! global-actions-depot "foo" "eval1")
         (cycle!)

         (TopologyUtils/advanceSimTime 1000)

         (ana/add-rule!
          global-actions-depot
          "foo-as"
          "foo"
          {:node-name         "as"
           :action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.71
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (ana/add-rule!
          global-actions-depot
          "foo-agg"
          "foo"
          {:node-name         "agg"
           :action-name       "action1"
           :action-params     {}
           :filter            (aor-types/->AndFilter [])
           :sampling-rate     0.72
           :start-time-millis (h/current-time-millis)
           :status-filter     :success
          })
         (is (thrown? Exception
                      (ana/add-rule!
                       global-actions-depot
                       "foo-as"
                       "foo"
                       {:node-name         "as"
                        :action-name       "action1"
                        :action-params     {}
                        :filter            (aor-types/->AndFilter [])
                        :sampling-rate     0.71
                        :start-time-millis (h/current-time-millis)
                        :status-filter     :success
                       })))
         (is (thrown? Exception
                      (ana/add-rule!
                       global-actions-depot
                       "foo-abc"
                       "foo"
                       {:action-name       "notanaction"
                        :action-params     {}
                        :filter            (aor-types/->AndFilter [])
                        :sampling-rate     0.71
                        :start-time-millis (h/current-time-millis)
                        :status-filter     :success
                       })))

         (TopologyUtils/advanceSimTime 1000)

         (bind inv (aor/agent-initiate foo "agg"))
         (is (= "3!" (aor/agent-result foo inv)))
         (cycle!)
         (is (= {0.71 1 0.72 1} (frequencies @sample-rates)))
         (is (= (set @ACTIONS)
                #{[:action1 [] [{"node" "agg" "args" [1]} {"node" "agg" "args" [2]}]
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      "as"
                    :type           :node
                    :latency-millis 0}
                   []
                   []
                   false]
                  [:action1 [3 nil] "3!"
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      "agg"
                    :type           :node
                    :latency-millis 0}
                   []
                   []
                   false]}
             ))

         ;; do it again to make sure nothing gets blocked on any of the intermediate agg nodes
         (bind inv (aor/agent-initiate foo "agg"))
         (is (= "3!" (aor/agent-result foo inv)))
         (cycle!)
         (is (= {0.71 1 0.72 1} (frequencies @sample-rates)))
         (is (= (set @ACTIONS)
                #{[:action1 [] [{"node" "agg" "args" [1]} {"node" "agg" "args" [2]}]
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      "as"
                    :type           :node
                    :latency-millis 0}
                   []
                   []
                   false]
                  [:action1 [3 nil] "3!"
                   {:action-name    "action1"
                    :agent-name     "foo"
                    :node-name      "agg"
                    :type           :node
                    :latency-millis 0}
                   []
                   []
                   false]}
             ))
        )))))

(deftest action-failures-test
  (with-redefs [TICKS (atom 0)
                i/SUBSTITUTE-TICK-DEPOTS true

                aor-types/get-config (max-retries-override 0)

                ana/enable-action-error-logs? (constantly false)

                anode/log-node-error (fn [& args])

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))

                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))
               ]
    (with-open [ipc (rtest/create-ipc)]
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
               (cond
                 (= input ["bad-eval-return"])
                 "invalid"

                 (= input ["eval-exception"])
                 (throw (ex-info "fail" {}))

                 :else
                 {"len" (count output)}))))
          (aor/declare-action-builder
           topology
           "action1"
           ""
           (fn [params]
             (fn [fetcher input output run-info]
               (cond
                 (= input ["bad-action-return"])
                 "invalid"

                 (= input ["action-exception"])
                 (throw (ex-info "fail" {}))

                 :else
                 {"input"   input
                  "output"  output
                  "latency" (:latency-millis run-info)})
             )))
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               "node1"
               (fn [agent-node input]
                 (if (= input "fail-agent")
                   (throw (ex-info "fail-agent" {}))
                   (aor/emit! agent-node "node1" (str input "!")))))
              (aor/node
               "node1"
               nil
               (fn [agent-node input]
                 (aor/result! agent-node (str input "?")))))
         ))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
       (bind foo-action-log (:action-log-query (aor-types/underlying-objects foo)))

       (bind last-action
         (fn [action-name]
           (-> foo-action-log
               (foreign-invoke-query action-name 1 nil)
               :actions
               first
               :action)))

       (bind fixed-node-invoke (aor-types/->valid-NodeInvokeImpl 0 (h/max-uuid)))

       (bind last-action-normed
         (fn [action-name]
           (multi-transform
            (multi-path
             [:start-time-millis (termval 0)]
             [:finish-time-millis (termval 0)]
             [:node-invoke aor-types/NodeInvokeImpl? (termval fixed-node-invoke)]
             [:info-map (keypath "latency") NONE>])
            (last-action action-name))))


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
                              "eval1"
                              "my-eval"
                              {}
                              "")

       (ana/add-rule! global-actions-depot
                      "eval-action"
                      "foo"
                      {:node-name         nil
                       :action-name       "aor/eval"
                       :action-params     {"name" "eval1"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })
       (ana/add-rule! global-actions-depot
                      "foo-agent-fail"
                      "foo"
                      {:node-name         nil
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :all
                      })
       (ana/add-rule! global-actions-depot
                      "foo-agent-only-fail"
                      "foo"
                      {:node-name         nil
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :fail
                      })
       (ana/add-rule! global-actions-depot
                      "foo-agent"
                      "foo"
                      {:node-name         nil
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })
       (ana/add-rule! global-actions-depot
                      "foo-start-fail"
                      "foo"
                      {:node-name         "start"
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :all
                      })
       (ana/add-rule! global-actions-depot
                      "foo-start-only-fail"
                      "foo"
                      {:node-name         "start"
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :fail
                      })
       (ana/add-rule! global-actions-depot
                      "foo-start"
                      "foo"
                      {:node-name         "start"
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })

       (bind inv (aor/agent-initiate foo "bad-action-return"))
       (is (= "bad-action-return!?" (aor/agent-result foo inv)))

       (cycle!)
       (is (nil? (last-action "foo-start-only-fail")))
       (is (nil? (last-action "foo-agent-only-fail")))
       (bind action (last-action "foo-agent"))
       (is (not (:success? action)))
       (is (= ["exception"] (keys (:info-map action))))
       (is (h/contains-string? (get (:info-map action) "exception") "Action return must be map"))

       (bind inv (aor/agent-initiate foo "action-exception"))
       (is (= "action-exception!?" (aor/agent-result foo inv)))
       (cycle!)
       (is (nil? (last-action "foo-start-only-fail")))
       (is (nil? (last-action "foo-agent-only-fail")))
       (bind action (last-action "foo-agent"))
       (is (not (:success? action)))
       (is (= ["exception"] (keys (:info-map action))))
       (is (h/contains-string? (get (:info-map action) "exception")
                               "clojure.lang.ExceptionInfo: fail"))

       (bind inv (aor/agent-initiate foo "bad-eval-return"))
       (is (= "bad-eval-return!?" (aor/agent-result foo inv)))
       (cycle!)
       (bind action (last-action "eval-action"))
       (is (= #{"failure" "invoke" "success?"} (set (keys (:info-map action)))))
       (is (= false (get (:info-map action) "success?")))
       (is (h/contains-string? (get (:info-map action) "failure")
                               "Invalid map of results"))

       (bind inv (aor/agent-initiate foo "eval-exception"))
       (is (= "eval-exception!?" (aor/agent-result foo inv)))
       (cycle!)
       (bind action (last-action "eval-action"))
       (is (= #{"failure" "invoke" "success?"} (set (keys (:info-map action)))))
       (is (= false (get (:info-map action) "success?")))
       (is (h/contains-string? (get (:info-map action) "failure")
                               "clojure.lang.ExceptionInfo: fail"))


       (bind inv (aor/agent-initiate foo "a"))
       (is (= "a!?" (aor/agent-result foo inv)))
       (cycle!)
       (is (nil? (last-action "foo-start-only-fail")))
       (is (nil? (last-action "foo-agent-only-fail")))
       (bind ares
         (aor-types/->valid-ActionLog
          0
          0
          inv
          nil
          true
          {"input" ["a"] "output" "a!?"}))
       (is (= ares (last-action-normed "foo-agent") (last-action-normed "foo-agent-fail")))

       (bind nres
         (aor-types/->valid-ActionLog
          0
          0
          inv
          fixed-node-invoke
          true
          {"input" ["a"] "output" [{"node" "node1" "args" ["a!"]}]}))
       (is (= nres (last-action-normed "foo-start") (last-action-normed "foo-start-fail")))

       (bind inv (aor/agent-initiate foo "fail-agent"))
       (is (thrown? Exception (aor/agent-result foo inv)))
       (cycle!)
       (is (= ares (last-action-normed "foo-agent")))
       (is (= nres (last-action-normed "foo-start")))

       (bind action (last-action "foo-agent-fail"))
       (bind action2 (last-action "foo-agent-only-fail"))
       (is (= inv (:agent-invoke action) (:agent-invoke action2)))
       (is (nil? (:node-invoke action)))
       (is (nil? (:node-invoke action2)))
       (is (:success? action))
       (is (:success? action2))
       (bind im (:info-map action))
       (bind im2 (:info-map action2))
       (is (= ["fail-agent"] (get im "input") (get im2 "input")))
       (is (instance? AgentFailedException (get im "output")))
       (is (instance? AgentFailedException (get im2 "output")))
       (is (some? (get im "latency")))
       (is (some? (get im2 "latency")))

       (bind action (last-action "foo-start-fail"))
       (bind action2 (last-action "foo-start-only-fail"))
       (is (= inv (:agent-invoke action) (:agent-invoke action2)))
       (is (some? (:node-invoke action)))
       (is (some? (:node-invoke action2)))
       (is (:success? action))
       (is (:success? action2))
       (bind im (:info-map action))
       (bind im2 (:info-map action2))
       (is (= ["fail-agent"] (get im "input") (get im2 "input")))
       (is (= [] (get im "output") (get im2 "output")))
       (is (nil? (get im "latency")))
       (is (nil? (get im2 "latency")))
      ))))


(def NODE-ACTION-ATOM)

(deftest actions-scanning-test
  (with-redefs [TICKS (atom 0)
                ACTIONS (atom [])
                NODE-ACTION-ATOM (atom nil)
                i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))

                aor-types/get-config (max-retries-override 0)

                anode/log-node-error (fn [& args])

                anode/gen-node-id
                (fn [& args]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))

                at/gen-new-agent-id
                (fn [agent-name]
                  (if (#{"foo"} agent-name)
                    (do
                      (let [ret (h/random-uuid7-at-timestamp (h/current-time-millis))]
                        (TopologyUtils/advanceSimTime 1000)
                        ret
                      ))
                    (h/random-uuid7)))]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-action-builder
           topology
           "action1"
           ""
           (fn [params]
             (fn [fetcher input output run-info]
               (swap! ACTIONS conj [(:rule-name run-info) (:node-name run-info) input output])
               {}
             )))
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               "node1"
               (fn [agent-node input]
                 (if-let [action @NODE-ACTION-ATOM]
                   (if (instance? java.util.concurrent.Semaphore action)
                     (h/acquire-semaphore action)
                     (throw (h/ex-info "fail" {}))))
                 (aor/emit! agent-node "node1" (str input "!"))))
              (aor/node
               "node1"
               nil
               (fn [agent-node input]
                 (aor/result! agent-node (str input "?")))))
         ))
       (rtest/launch-module! ipc module {:tasks 1 :threads 1})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))


       (bind cycle!
         (fn []
           (reset! TICKS 0)
           (reset! ACTIONS [])
           (foreign-append! ana-depot nil)
           (is (condition-attained? (> @TICKS 0)))
           (rtest/pause-microbatch-topology! ipc
                                             module-name
                                             aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
           (rtest/resume-microbatch-topology! ipc
                                              module-name
                                              aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)))

       (bind wait-acquired!
         (fn [^java.util.concurrent.Semaphore sem]
           (is (condition-attained? (> (.getQueueLength sem) 0)))
         ))

       (ana/add-rule! global-actions-depot
                      "foo-agent"
                      "foo"
                      {:node-name         nil
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :all
                      })
       (ana/add-rule! global-actions-depot
                      "foo-start"
                      "foo"
                      {:node-name         "start"
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })
       (ana/add-rule! global-actions-depot
                      "foo-start-fail"
                      "foo"
                      {:node-name         "start"
                       :action-name       "action1"
                       :action-params     {}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :all
                      })



       (bind inv (aor/agent-initiate foo "a"))
       (is (= "a!?" (aor/agent-result foo inv)))
       (bind inv (aor/agent-initiate foo "b"))
       (is (= "b!?" (aor/agent-result foo inv)))
       (bind sem1 (h/mk-semaphore 0))
       (reset! NODE-ACTION-ATOM sem1)
       (bind invc (aor/agent-initiate foo "c"))
       (wait-acquired! sem1)
       (reset! NODE-ACTION-ATOM nil)
       (bind inv (aor/agent-initiate foo "d"))
       (is (= "d!?" (aor/agent-result foo inv)))
       (bind inv (aor/agent-initiate foo "e"))
       (is (= "e!?" (aor/agent-result foo inv)))
       (cycle!)
       (is (= (frequencies @ACTIONS)
              {["foo-agent" nil ["a"] "a!?"] 1
               ["foo-agent" nil ["b"] "b!?"] 1
               ["foo-start" "start" ["a"] [{"node" "node1" "args" ["a!"]}]] 1
               ["foo-start" "start" ["b"] [{"node" "node1" "args" ["b!"]}]] 1
               ["foo-start-fail" "start" ["a"] [{"node" "node1" "args" ["a!"]}]] 1
               ["foo-start-fail" "start" ["b"] [{"node" "node1" "args" ["b!"]}]] 1}))
       (cycle!)
       (is (= [] @ACTIONS))
       (TopologyUtils/advanceSimTime (+ ana/NODE-ACTION-STALL-TIME-MILLIS 1000))
       (cycle!)
       (is (= [] @ACTIONS))
       (h/release-semaphore sem1 1)
       (is (= "c!?" (aor/agent-result foo invc)))
       (cycle!)
       (is (= (frequencies @ACTIONS)
              {["foo-start-fail" "start" ["c"] [{"node" "node1" "args" ["c!"]}]] 1
               ["foo-start-fail" "start" ["d"] [{"node" "node1" "args" ["d!"]}]] 1
               ["foo-start-fail" "start" ["e"] [{"node" "node1" "args" ["e!"]}]] 1
               ["foo-start" "start" ["c"] [{"node" "node1" "args" ["c!"]}]] 1
               ["foo-start" "start" ["d"] [{"node" "node1" "args" ["d!"]}]] 1
               ["foo-start" "start" ["e"] [{"node" "node1" "args" ["e!"]}]] 1
               ["foo-agent" nil ["c"] "c!?"] 1
               ["foo-agent" nil ["d"] "d!?"] 1
               ["foo-agent" nil ["e"] "e!?"] 1}))


       (reset! NODE-ACTION-ATOM :fail)
       (bind inv (aor/agent-initiate foo "f"))
       (is (thrown? Exception (aor/agent-result foo inv)))
       (cycle!)
       (is (= 1 (count @ACTIONS)))
       (bind a (first @ACTIONS))
       (is (= (subvec a 0 3) ["foo-agent" nil ["f"]]))
       (is (instance? Exception (nth a 3)))

       (TopologyUtils/advanceSimTime (+ ana/NODE-ACTION-STALL-TIME-MILLIS 1000))
       (cycle!)
       (is (= (frequencies @ACTIONS)
              {["foo-start-fail" "start" ["f"] []] 1}))
      ))))

(deftest add-to-dataset-action-test
  (with-redefs [TICKS (atom 0)
                i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))]
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
                 (aor/result! agent-node ["." (assoc input "q" 3) ".."]))))
         ))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
       (bind search-examples-query
         (:search-examples-query (aor-types/underlying-objects agent-manager)))
       (bind foo-action-log (:action-log-query (aor-types/underlying-objects foo)))

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


       (bind ds-id
         (aor/create-dataset! agent-manager
                              "Dataset 1 is a dataset"
                              {:description "this is a dataset"}))

       (bind all-examples
         (fn []
           (foreign-invoke-query search-examples-query
                                 ds-id
                                 nil
                                 nil
                                 1000
                                 nil)))

       (ana/add-rule! global-actions-depot
                      "my-add"
                      "foo"
                      {:node-name         nil
                       :action-name       "aor/add-to-dataset"
                       :action-params     {"datasetId" (str ds-id)
                                           "inputJsonPathTemplate" "$[0].a"
                                           "outputJsonPathTemplate" "{\"b\": \"$[1]\"}"
                                          }
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })


       (aor/agent-invoke foo {"a" "ccb"})
       (cycle!)
       (bind {:keys [examples]} (all-examples))
       (is (= 1 (count examples)))
       (bind e (first examples))
       (is (= "ccb" (:input e)))
       (is (= {"b" {"a" "ccb" "q" 3}} (:reference-output e)))
       (is (aor-types/ActionSourceImpl? (:source e)))
       (is (= "foo" (:agent-name (:source e))))
       (is (= "my-add" (:rule-name (:source e))))
       (bind {:keys [actions]} (foreign-invoke-query foo-action-log "my-add" 100 nil))
       (is (contains? (-> actions
                          first
                          :action
                          :info-map)
                      "exampleId"))

       (aor/agent-invoke foo {"a" "x"})
       (aor/agent-invoke foo {"a" "y"})
       (aor/agent-invoke foo {"a" "z"})
       (cycle!)
       (bind {:keys [examples]} (all-examples))
       (is (= 4 (count examples)))
       (bind s
         (set (for [{:keys [input reference-output]} examples] {:i input :o reference-output})))
       (is (= s
              #{{:i "ccb" :o {"b" {"a" "ccb" "q" 3}}}
                {:i "x" :o {"b" {"a" "x" "q" 3}}}
                {:i "y" :o {"b" {"a" "y" "q" 3}}}
                {:i "z" :o {"b" {"a" "z" "q" 3}}}
               }))
      ))))

(nippy/extend-freeze java.lang.ProcessHandleImpl
                     ::process-handle
                     [^java.lang.ProcessHandle ph out]
                     (nippy/freeze-to-out! out (.pid ph)))

(nippy/extend-thaw ::process-handle
                   [in]
                   (let [pid (nippy/thaw-from-in! in)]
                     (.orElse (java.lang.ProcessHandle/of pid) nil)))

(deftest webhook-action-test
  (let [received-atom (atom [])
        stop-server
        (server/run-server
         (fn [req]
           (swap! received-atom conj req)
           {:status  200
            :headers {"Content-Type" "application/json"}
            :body    (j/write-value-as-string {:ok true})})
         {:port 0})]
    (try
      (with-redefs [TICKS (atom 0)
                    i/SUBSTITUTE-TICK-DEPOTS true

                    i/hook:analytics-tick
                    (fn [& args] (swap! TICKS inc))]
        (with-open [ipc (rtest/create-ipc)]
          (letlocals
           (bind port
             (-> stop-server
                 meta
                 :local-port))
           (bind url (str "http://127.0.0.1:" port "/test"))

           (bind module
             (aor/agentmodule
              [topology]
              (aor/declare-evaluator-builder
               topology
               "ph-eval"
               ""
               (fn [params]
                 (fn [fetcher input ref-output output]
                   {"score" (java.lang.ProcessHandle/current)})))
              (-> topology
                  (aor/new-agent "foo")
                  (aor/node
                   "start"
                   nil
                   (fn [agent-node input]
                     (if (= input "qqq")
                       (aor/result! agent-node (java.lang.ProcessHandle/current))
                       (aor/result! agent-node (str input "!"))))))
             ))
           (rtest/launch-module! ipc module {:tasks 2 :threads 2})
           (bind module-name (get-module-name module))
           (bind agent-manager (aor/agent-manager ipc module-name))
           (bind global-actions-depot
             (:global-actions-depot (aor-types/underlying-objects agent-manager)))
           (bind foo (aor/agent-client agent-manager "foo"))
           (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))
           (bind foo-action-log (:action-log-query (aor-types/underlying-objects foo)))

           (bind cycle!
             (fn []
               (reset! TICKS 0)
               (reset! received-atom [])
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
                                  "my-ph-eval"
                                  "ph-eval"
                                  {}
                                  "")

           (ana/add-rule! global-actions-depot
                          "eval1"
                          "foo"
                          {:action-name       "aor/eval"
                           :action-params     {"name" "concise5"}
                           :filter            (aor-types/->AndFilter [])
                           :sampling-rate     1.0
                           :start-time-millis 0
                           :status-filter     :success
                          })
           (ana/add-rule! global-actions-depot
                          "eval2"
                          "foo"
                          {:node-name         "start"
                           :action-name       "aor/eval"
                           :action-params     {"name" "my-ph-eval"}
                           :filter            (aor-types/->AndFilter [])
                           :sampling-rate     1.0
                           :start-time-millis 0
                           :status-filter     :success
                          })


           (ana/add-rule!
            global-actions-depot
            "my-webhook"
            "foo"
            {:node-name         nil
             :action-name       "aor/webhook"
             :action-params     {"url"           url
                                 "headers"       "{\"a\": \"abcdefg\"}"
                                 "timeoutMillis" "30000"
                                 "payloadTemplate" ana/DEFAULT-WEBHOOK-PAYLOAD}
             :filter            (aor-types/->FeedbackFilter "eval1"
                                                            "concise?"
                                                            (aor-types/->ComparatorSpec :not= "a"))
             :sampling-rate     1.0
             :start-time-millis 0
             :status-filter     :success
            })


           (is (= "ccz!" (aor/agent-invoke foo "ccz")))
           (cycle!)
           (cycle!)
           (is (= 1 (count @received-atom)))
           (bind r (first @received-atom))
           (is (= "abcdefg" (get (:headers r) "a")))
           (bind m (j/read-value (slurp (:body r)) ana/STR-MAPPER))
           (is (= #{"input" "output" "runInfo"} (set (keys m))))
           (is (= ["ccz"] (get m "input")))
           (is (= "ccz!" (get m "output")))
           (bind ri (get m "runInfo"))
           (is (= "my-webhook" (get ri "ruleName")))
           (is (= "foo" (get ri "agentName")))
           (is (>= (get ri "latencyMillis") 0))
           (is (= "aor/webhook" (get ri "actionName")))
           (is (= "agent" (get ri "type")))
           (is (> (get ri "startTimeMillis") 0))
           (is (= [{"source" "eval[concise5]" "scores" {"concise?" true}}] (get ri "feedback")))


           (ana/delete-rule! global-actions-depot "foo" "my-webhook")
           (ana/delete-rule! global-actions-depot "foo" "eval1")
           (cycle!)
           (ana/add-rule!
            global-actions-depot
            "my-webhook-start"
            "foo"
            {:node-name         "start"
             :action-name       "aor/webhook"
             :action-params
             {"url"           url
              "headers"       "{\"a\": \"abcdefg\"}"
              "timeoutMillis" "30000"
              "payloadTemplate"
              "{\"o\": %output, \"r\":
                                               %runInfo}"}
             :filter            (aor-types/->FeedbackFilter "eval2"
                                                            "score"
                                                            (aor-types/->ComparatorSpec :not= "a"))
             :sampling-rate     1.0
             :start-time-millis 0
             :status-filter     :success
            })

           (bind res (aor/agent-invoke foo "qqq"))
           (is (instance? java.lang.ProcessHandle res))
           (cycle!)
           (cycle!)
           (is (= 1 (count @received-atom)))
           (bind r (first @received-atom))
           (is (= "abcdefg" (get (:headers r) "a")))
           (bind m (j/read-value (slurp (:body r)) ana/STR-MAPPER))
           (bind pid-str (str (java.lang.ProcessHandle/current)))
           (is (= #{"o" "r"} (set (keys m))))
           (is (= pid-str (get m "o")))
           (bind ri (get m "r"))
           (is (= "my-webhook-start" (get ri "ruleName")))
           (is (= "foo" (get ri "agentName")))
           (is (>= (get ri "latencyMillis") 0))
           (is (= "aor/webhook" (get ri "actionName")))
           (is (= "node" (get ri "type")))
           (is (> (get ri "startTimeMillis") 0))
           (is (= [{"source" "eval[my-ph-eval]" "scores" {"score" pid-str}}] (get ri "feedback")))
          )))
      (finally
        (stop-server)))))
