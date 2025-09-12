(ns com.rpl.analytics-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]))

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

(deftest agent-trace-analytics-test

)
