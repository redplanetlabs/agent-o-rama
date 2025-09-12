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
  ;; TODO: <<<<>>>> unit test with subagent calls
)
