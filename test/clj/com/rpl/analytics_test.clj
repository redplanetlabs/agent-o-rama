(ns com.rpl.analytics-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m])
  (:import
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

(defn ai-stats [& args] (apply aor-types/->AgentInvokeStats args))
(defn bai-stats [& args] (apply aor-types/->BasicAgentInvokeStats args))
(defn op-stats [& args] (apply aor-types/->OpStats args))
(defn nop-info [& args] (apply aor-types/->NestedOpInfo args))
(defn sa-ref [& args] (apply aor-types/->AgentRef args))
(defn sa-stats [& args] (apply aor-types/->SubagentInvokeStats args))

(deftest mk-node-stats-test
  (is (= (ana/mk-node-stats "a" 3 5 [])
         (ai-stats {} (bai-stats {} 0 0 0 {"a" (op-stats 1 2)}))))
  (is
   (=
    (ana/mk-node-stats
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
    (ana/mk-node-stats
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
       (ana/aggregated-basic-stats
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
