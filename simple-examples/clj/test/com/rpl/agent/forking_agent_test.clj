(ns com.rpl.agent.forking-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.forking-agent :refer [ForkingAgentModule]]))

(deftest forking-agent-test
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
                fork-result (aor/agent-fork agent
                                            base-invoke
                                            {"calculate" [{:original-input
                                                           {:base-value 8
                                                            :multiplier 3}
                                                           :processed-value
                                                           24}]})]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= {:base-value 8 :multiplier 3} (:original-input fork-result)))
            (is (= 24 (:processed-value fork-result)))
            (is (= 576 (:squared fork-result))) ; 24² = 576
            (is (= 12.0 (:halved fork-result))) ; 24 / 2 = 12
            (is (= true (:valid? fork-result)))))

        (testing "async forking works correctly"
          (let [base-invoke (aor/agent-initiate agent
                                                {:base-value 2
                                                 :multiplier 3})
                _ (aor/agent-result agent base-invoke) ; Wait for completion
                fork-invoke (aor/agent-initiate-fork agent
                                                     base-invoke
                                                     {"validate"
                                                      [{:original-input
                                                        {:base-value 1
                                                         :multiplier 1}
                                                        :processed-value 1
                                                        :squared         1
                                                        :halved          0.5}]})
                fork-result (aor/agent-result agent fork-invoke)]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= {:base-value 1 :multiplier 1} (:original-input fork-result)))
            (is (= 1 (:processed-value fork-result)))
            (is (= 1 (:squared fork-result)))
            (is (= 0.5 (:halved fork-result)))
            (is (= true (:valid? fork-result)))))

        (testing "fork with invalid data"
          (let [base-invoke (aor/agent-initiate agent
                                                {:base-value 5
                                                 :multiplier 1})
                _ (aor/agent-result agent base-invoke) ; Wait for completion
                fork-result (aor/agent-fork agent
                                            base-invoke
                                            {"validate" [{:original-input
                                                          {:base-value 0
                                                           :multiplier 0}
                                                          :processed-value 0
                                                          :squared 0
                                                          :halved 0.0}]})]
            (is (= "calculation-complete" (:action fork-result)))
            (is (= 0 (:processed-value fork-result)))
            ;; Not valid because processed-value is not positive
            (is (= false (:valid? fork-result)))
          ))))))
