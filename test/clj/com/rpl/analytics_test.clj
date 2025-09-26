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
   [meander.epsilon :as m])
  (:import
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
        (aor-types/->valid-ActionSourceImpl "xyz")))

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

(deftest to-action-queue-test
  (let [data {0 {"A" {"A-R1" {:offsets [0]}
                      "A-R2" {:offsets [1 2 3]}}
                 "B" {"B-R1" {:offsets []}
                      "B-R2" {:offsets [12]}
                      "B-R3" {:offsets [13 14 15 16]}}}
              1 {"A" {"A-R1" {:offsets [4 5]}
                      "A-R2" {:offsets [6]}}
                 "B" {"B-R1" {:offsets [17]}
                      "B-R2" {:offsets [18 19]}
                      "B-R3" {:offsets [20 21 22]}}}
              2 {"A" {"A-R1" {:offsets [7 8 9]}}
                 "B" {"B-R1" {:offsets [24 25 26]}
                      "B-R2" {:offsets [27]}
                      "B-R3" {:offsets [28 29 30 31 32 33 34 35 36]}}}}
        out  (vec (ana/to-action-queue data))]
    ;; 1. Offsets for each task/agent/rule are emitted in order
    (doseq [[task agents] data
            [agent rules] agents
            [rule {:keys [offsets]}] rules]
      (let [emitted (->> out
                         (filter #(and (= (:task-id %) task)
                                       (= (:agent-name %) agent)
                                       (= (:rule-name %) rule)))
                         (map :offset))]
        (is (= offsets emitted)
            (str "Offsets not preserved for " [task agent rule]))))

    ;; 2. Every offset across input is included exactly once
    (let [input-offsets  (->> data
                              (mapcat (fn [[t agents]]
                                        (for [[a rules] agents
                                              [r {:keys [offsets]}] rules
                                              o         offsets]
                                          {:task-id t :agent-name a :rule-name r :offset o})))
                              set)
          output-offsets (set out)]
      (is (= input-offsets output-offsets)
          "All offsets should be present exactly once in output"))))

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

(def ACTIONS)
(def TICKS)

(deftest actions-test
  (let [sample-rates (atom [])
        sample-atom  (atom true)]
    (with-redefs [ACTIONS (atom [])
                  TICKS (atom 0)
                  i/SUBSTITUTE-TICK-DEPOTS true

                  i/hook:analytics-tick
                  (fn [& args] (swap! TICKS inc))

                  ana/sample?
                  (fn [sampling-rate]
                    (swap! sample-rates conj sampling-rate)
                    @sample-atom)

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
                    (select-keys run-info [:action-name :agent-name :node-name :type])
                    (select [:feedback ALL :scores] run-info)])
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
                       "a2" {:description "param2"}}})
            (TestSnippets/declareActionBuilders topology)
            (-> topology
                (aor/new-agent "foo")
                (aor/node
                 "start"
                 "node1"
                 (fn [agent-node input]
                   (aor/emit! agent-node "node1" (str input "!"))))
                (aor/node
                 "node1"
                 nil
                 (fn [agent-node input]
                   (aor/result! agent-node (str input "?")))))
            (-> topology
                (aor/new-agent "bar")
                (aor/node
                 "begin"
                 "n1"
                 (fn [agent-node input]
                   (aor/emit! agent-node "node1" (str input "+"))))
                (aor/node
                 "n1"
                 nil
                 (fn [agent-node input]
                   (aor/result! agent-node (str input "-")))))
           ))
         (rtest/launch-module! ipc module {:tasks 2 :threads 2})
         (bind module-name (get-module-name module))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind global-actions-depot
           (:global-actions-depot (aor-types/underlying-objects agent-manager)))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind bar (aor/agent-client agent-manager "bar"))
         (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))

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

         (bind cycle!
           (fn []
             (reset! TICKS 0)
             (reset! sample-rates [])
             (reset! ACTIONS [])
             (foreign-append! ana-depot nil)
             (is (condition-attained? (> @TICKS 0)))
             (rtest/pause-microbatch-topology! ipc
                                               module-name
                                               aor-types/AGENTS-MB-TOPOLOGY-NAME)
             (rtest/resume-microbatch-topology! ipc
                                                module-name
                                                aor-types/AGENTS-MB-TOPOLOGY-NAME)))


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

         (ana/add-rule! global-actions-depot
                        "eval1"
                        "foo"
                        {:action-name       "aor/eval"
                         :action-params     {"name" "concise5"}
                         :filter            (aor-types/->AndFilter [])
                         :sampling-rate     0.5
                         :start-time-millis 15000
                         :include-failures? false
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
           :include-failures? false
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
                  {:action-name "action1" :agent-name "foo" :node-name nil :type :agent}
                  [{"concise?" true}]]]))

         (cycle!)
         (is (= [1.0] @sample-rates))
         (is (= @ACTIONS
                [[:action1 ["."] ".!?"
                  {:action-name "action1" :agent-name "foo" :node-name nil :type :agent}
                  [{"concise?" true}]]]))

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
           :include-failures? false
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
           :include-failures? false
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
           :include-failures? false
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
           :include-failures? false
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



         ;; TODO: <<<<>>>>> do java ones
         ;;   - can only verify those through action logs


         ;; TODO: <<<<>>>> deleting a rule is not deleting its cursors
         ;;   - can clean those up in microbatch




         ;; TODO: <<<<>>>> check action logs
         ;;   - need query topology first






         ;; TODO: <<<<>>>>
         ;;  - multiple agents with different rules (some with same name) at same time
         ;;  - verify include-failures? behavior
         ;;   - gives AgentFailedException for agent failure
         ;;   - gives nil for node output in that case
         ;;     - node latency is nil
         ;;     - agent latency is not nil
         ;;  - verify filters work
         ;;  - verify sampling rate
         ;;  - verify respects max concurrency
         ;;  - verify how much it does in one iteration
         ;;  - agent invokes from experiments are skipped
         ;;  - start from time for rule
         ;;  - error handling:
         ;;    - online eval throws exception
         ;;      - online eval doesn't return map
         ;;    - action doesn't return map

         ;; TODO: <<<<>>>>
         ;;  - ana/add-rule!
         ;;  - ana/delete-rule!
         ;;     - verify dependency checking
         ;;  - ana/fetch-agent-rules
         ;;     - use unerlying-objects to get it
        )))))
