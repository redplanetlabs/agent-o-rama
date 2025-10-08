(ns com.rpl.agent-o-rama.ui.feedback-test-agent-test
  (:use [clojure.test]
        [com.rpl.rama]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.ui.feedback-test-agent :as fta]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.rama.helpers
    TopologyUtils]))

(deftest feedback-test-agent-basic-test
  (testing "FeedbackTestAgent runs successfully with different modes"
    (with-open [ipc (rtest/create-ipc)
                _   (TopologyUtils/startSimTime)]
      (rtest/launch-module! ipc fta/FeedbackTestAgentModule {:tasks 2 :threads 2})
      (let [module-name  (get-module-name fta/FeedbackTestAgentModule)
            agent-manager (aor/agent-manager ipc module-name)
            agent-client  (aor/agent-client agent-manager "FeedbackTestAgent")]

        (let [inv1 (aor/agent-initiate agent-client "hello")]
          (is (= "test-hello" (aor/agent-result agent-client inv1))))

        (let [inv2 (aor/agent-initiate agent-client {"mode" "short"})]
          (is (= "ok" (aor/agent-result agent-client inv2))))

        (let [inv3 (aor/agent-initiate agent-client {"mode" "medium"})]
          (is (= "test-medium-result" (aor/agent-result agent-client inv3))))

        (let [inv4 (aor/agent-initiate agent-client {"mode" "long"})]
          (is (= "test-this-is-a-very-long-result-string-for-testing"
                 (aor/agent-result agent-client inv4))))

        (let [inv5 (aor/agent-initiate agent-client {"mode" "prefixed" "text" "custom"})]
          (is (= "test-custom" (aor/agent-result agent-client inv5))))))))

(deftest feedback-generation-test
  (testing "FeedbackTestAgent generates feedback with varying scores"
    (let [ticks (atom 0)]
      (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                    i/hook:analytics-tick
                    (fn [& args] (swap! ticks inc))]
        (with-open [ipc (rtest/create-ipc)
                    _   (TopologyUtils/startSimTime)]
          (rtest/launch-module! ipc fta/FeedbackTestAgentModule {:tasks 2 :threads 2})
          (let [module-name  (get-module-name fta/FeedbackTestAgentModule)
                agent-manager       (aor/agent-manager ipc module-name)
                global-actions-depot (:global-actions-depot (aor-types/underlying-objects agent-manager))
                agent-client        (aor/agent-client agent-manager "FeedbackTestAgent")
                ana-depot           (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name))
                feedback-query      (:feedback-query (aor-types/underlying-objects agent-client))
                
                get-feedback (fn [inv]
                               (foreign-invoke-query feedback-query inv))
                
                cycle! (fn []
                         (reset! ticks 0)
                         (foreign-append! ana-depot nil)
                         (is (condition-attained? (> @ticks 0)))
                         (rtest/pause-microbatch-topology! ipc
                                                           module-name
                                                           aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
                         (rtest/resume-microbatch-topology! ipc
                                                            module-name
                                                            aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME))]

            (let [setup-result (fta/setup-feedback-testing! agent-manager global-actions-depot)]
              (is (= 4 (count (:evaluators setup-result))))
              (is (= 3 (count (:agent-rules setup-result))))
              (is (= 2 (count (:node-rules setup-result)))))

            (let [inv1 (aor/agent-initiate agent-client {"mode" "short"})]
              (is (= "ok" (aor/agent-result agent-client inv1)))
              (cycle!)
              (let [feedback1 (get-feedback inv1)]
                (is (seq feedback1))
                (is (>= (count feedback1) 2))))

            (let [inv2 (aor/agent-initiate agent-client {"mode" "medium"})]
              (is (= "test-medium-result" (aor/agent-result agent-client inv2)))
              (cycle!)
              (let [feedback2 (get-feedback inv2)]
                (is (seq feedback2))
                (is (>= (count feedback2) 4))))

            (let [inv3 (aor/agent-initiate agent-client {"mode" "long"})]
              (is (= "test-this-is-a-very-long-result-string-for-testing"
                     (aor/agent-result agent-client inv3)))
              (cycle!)
              (let [feedback3 (get-feedback inv3)
                    numeric-fb (first (filter #(contains? (:scores %) "length") feedback3))]
                (is (seq feedback3))
                (is (some? numeric-fb))
                (is (number? (get (:scores numeric-fb) "length")))
                (is (number? (get (:scores numeric-fb) "score")))
                (is (number? (get (:scores numeric-fb) "rating")))))))))))
