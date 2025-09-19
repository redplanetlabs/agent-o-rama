(ns com.rpl.agent.basic.forking-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.forking-agent :refer [ForkingAgentModule]]))

(deftest forking-agent-test
  (System/gc)
  (testing "ForkingAgent example produces expected forking behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc ForkingAgentModule {:tasks 2 :threads 2})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        ForkingAgentModule))
            agent   (aor/agent-client manager "ForkingAgent")]

        (testing "base execution works correctly"
          (let [result (aor/agent-invoke agent
                                         {:base-value 4
                                          :multiplier 5})]
            (is (= "calculation-complete" (:action result)))
            (is (= {:base-value 4 :multiplier 5} (:original-input result)))
            (is (= 20 (:processed-value result))) ; 4 * 5 = 20
            (is (= 400 (:squared result))) ; 20² = 400
            (is (= 10.0 (:halved result))) ; 20 / 2 = 10
            (is (= true (:valid? result))) ; positive and squared >= processed
            (is (number? (:completed-at result)))))

        (testing "synchronous forking works correctly"
          (let [base-invoke (aor/agent-initiate agent
                                                {:base-value 3
                                                 :multiplier 2})
                _ (aor/agent-result agent base-invoke) ; Wait for completion
                ;; Fork without modifying node data - should re-run with same inputs
                fork-result (aor/agent-fork
                             agent
                             base-invoke
                             {})]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= {:base-value 3 :multiplier 2} (:original-input fork-result)))
            (is (= 6 (:processed-value fork-result))) ; 3 * 2 = 6
            (is (= 36 (:squared fork-result))) ; 6² = 36
            (is (= 3.0 (:halved fork-result))) ; 6 / 2 = 3
            (is (= true (:valid? fork-result)))))

        (testing "async forking works correctly"
          (let [base-invoke (aor/agent-initiate agent
                                                {:base-value 2
                                                 :multiplier 3})
                _ (aor/agent-result agent base-invoke) ; Wait for completion
                ;; Fork without modifying node data - should re-run with same inputs
                fork-invoke (aor/agent-initiate-fork
                             agent
                             base-invoke
                             {})
                fork-result (aor/agent-result agent fork-invoke)]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= {:base-value 2 :multiplier 3} (:original-input fork-result)))
            (is (= 6 (:processed-value fork-result))) ; 2 * 3 = 6
            (is (= 36 (:squared fork-result))) ; 6² = 36
            (is (= 3.0 (:halved fork-result))) ; 6 / 2 = 3
            (is (= true (:valid? fork-result)))))

        (testing "fork re-runs with same data"
          (let [base-invoke (aor/agent-initiate agent
                                                {:base-value 5
                                                 :multiplier 1})
                _ (aor/agent-result agent base-invoke) ; Wait for completion
                ;; Fork without modifying data - should produce same results
                fork-result (aor/agent-fork
                             agent
                             base-invoke
                             {})]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= {:base-value 5 :multiplier 1} (:original-input fork-result)))
            (is (= 5 (:processed-value fork-result))) ; 5 * 1 = 5
            (is (= 25 (:squared fork-result))) ; 5² = 25
            (is (= 2.5 (:halved fork-result))) ; 5 / 2 = 2.5
            (is (= true (:valid? fork-result)))
          ))))))
