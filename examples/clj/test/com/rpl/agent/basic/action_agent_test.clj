(ns com.rpl.agent.basic.action-agent-test
  (:use [com.rpl.rama])
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.action-agent :refer [ActionAgentModule]]))

(deftest action-agent-test
  ;; Test demonstrates the use of actions and rules with feedback dependencies
  ;; Contracts being tested:
  ;; - declare-action-builder creates custom actions
  ;; - add-rule! registers rules that trigger actions
  ;; - FeedbackFilter filters based on previous rule feedback
  ;; - delete-rule! removes rules
  (System/gc)
  (testing "ActionAgent example"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc ActionAgentModule {:tasks 1 :threads 1})
      (let [manager (aor/agent-manager
                     ipc
                     (get-module-name ActionAgentModule))
            agent   (aor/agent-client manager "TextAgent")
            depot   (:global-actions-depot (aor-types/underlying-objects manager))]

        ;; Create evaluator
        (aor/create-evaluator! manager "eval1" "length-eval" {} "")

        (testing "add-rule! creates evaluation rule"
          (ana/add-rule!
           depot
           "eval-rule"
           "TextAgent"
           {:action-name       "aor/eval"
            :action-params     {"name" "eval1"}
            :filter            (aor-types/->AndFilter [])
            :sampling-rate     1.0
            :start-time-millis 0
            :include-failures? false}))

        (testing "adds dependent rule with FeedbackFilter"
          (ana/add-rule!
           depot
           "log-short"
           "TextAgent"
           {:action-name   "log-action"
            :action-params {"note" "short output"}
            :filter        (aor-types/->FeedbackFilter
                            "eval-rule"
                            "short?"
                            (aor-types/->ComparatorSpec := true))
            :sampling-rate 1.0
            :start-time-millis 0
            :include-failures? false}))

        (testing "agent processes text"
          (is (= "Hi!" (aor/agent-invoke agent "Hi")))
          (is (= "Hello World!" (aor/agent-invoke agent "Hello World"))))

        (testing "delete-rule! removes rule"
          (ana/delete-rule! depot "TextAgent" "log-short")
          (is (= "Bye!" (aor/agent-invoke agent "Bye"))))))))
