(ns com.rpl.agent-o-rama.ui.feedback-e2e-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as helpers]
   [com.rpl.agent-o-rama.ui.feedback-test-agent
    :refer [FeedbackTestAgentModule setup-feedback-testing!]]
   [etaoin.api :as e]))

(def ^:private default-timeout 120)

;;; Helper functions

(defn make-post-deploy-hook
  "Creates a post-deploy hook that sets up feedback testing.
   Options are passed through to setup-feedback-testing!"
  [opts]
  (fn [ipc module-name]
    (let [manager (aor/agent-manager ipc module-name)
          depot   (:global-actions-depot (aor-types/underlying-objects manager))]
      (setup-feedback-testing! manager depot opts))))

(deftest ^:integration agent-level-feedback-test
  ;; Test agent-level feedback display in the main Feedback tab.
  ;; Uses feedback-test-agent which generates agent-level evaluator feedback.
  (helpers/with-system [FeedbackTestAgentModule
                        {:post-deploy-hook (make-post-deploy-hook {:include-node-rules? false})}]
    (helpers/with-webdriver [driver]
      (testing "agent-level feedback displays correctly"
        (let [env    @helpers/system
              agent  (aor/agent-client (aor/agent-manager (:ipc env) (:module-name env))
                                       "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "medium" "text" "test"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "FeedbackTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible driver {:data-id "feedback-tab"} {:timeout default-timeout})

              (testing "feedback tab exists"
                (is (e/visible? driver {:data-id "feedback-tab"})))

              (testing "switch to feedback tab"
                (e/click driver {:data-id "feedback-tab"})
                (e/wait-visible driver {:data-id "feedback-list"} {:timeout 2}))

              (testing "feedback list is visible"
                (is (e/visible? driver {:data-id "feedback-list"})))

              (testing "has multiple feedback items from agent-level evaluators"
                (is (e/exists? driver {:data-id "feedback-item-0"}))
                (is (e/exists? driver {:data-id "feedback-item-1"})))

              (testing "feedback panels display scores"
                (is (e/visible? driver {:data-id "feedback-panel"}))))))))))

(deftest ^:integration node-level-feedback-test
  ;; Test node-level feedback display in node details Feedback tab.
  ;; Uses feedback-test-agent which generates node-level evaluator feedback.
  (helpers/with-system [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (helpers/with-webdriver [driver]
      (testing "node-level feedback displays correctly"
        (let [env    @helpers/system
              agent  (aor/agent-client (aor/agent-manager (:ipc env) (:module-name env))
                                       "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "long" "text" "eval"})]

          @(aor/agent-result-async agent invoke)
          (Thread/sleep 100)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "FeedbackTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible driver {:data-id "feedback-tab"} {:timeout default-timeout})

              #_(testing "navigate to graph view to select a node"
                  (e/click driver {:data-id "info-tab"})
                  (e/wait-visible driver {:class "react-flow__node"} {:timeout 2}))

              (testing "click on process node"
                (e/click driver {:data-id "agent-graph-node-process"})
                (e/wait-visible driver {:data-id "node-feedback-tab"} {:timeout 2}))

              (testing "node feedback tab exists"
                (is (e/visible? driver {:data-id "node-feedback-tab"})))

              (testing "switch to node feedback tab"
                (e/click driver {:data-id "node-feedback-tab"})
                (e/wait-visible driver {:data-id "node-feedback-container"} {:timeout 2}))

              (testing "node feedback is visible"
                (is (e/visible? driver {:data-id "feedback-list"})))

              (testing "has feedback items from node-level evaluators"
                (is (e/exists? driver {:data-id "feedback-item-0"}))))))))))

(deftest ^:integration empty-feedback-state-test
  ;; Test empty state display when no feedback is present.
  ;; Uses an agent run without any evaluator rules configured.
  (helpers/with-system [FeedbackTestAgentModule]
    (helpers/with-webdriver [driver]
      (testing "empty feedback state displays correctly"
        (let [env    @helpers/system
              agent  (aor/agent-client (aor/agent-manager (:ipc env) (:module-name env))
                                       "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "short"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "FeedbackTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible driver {:data-id "feedback-tab"} {:timeout default-timeout})

              (testing "switch to feedback tab"
                (e/scroll-query driver {:data-id "feedback-tab"})
                (e/click driver {:data-id "feedback-tab"})
                (e/wait-visible driver {:data-id "feedback-empty-state"} {:timeout 2}))

              (testing "empty state is visible"
                (is (e/visible? driver {:data-id "feedback-empty-state"}))
                (is (e/has-text? driver
                                 {:data-id "feedback-empty-state"}
                                 "No feedback available"))))))))))

(deftest ^:integration feedback-score-types-test
  ;; Test display of different score types (boolean and numeric).
  ;; Uses feedback-test-agent evaluators that return different score formats.
  (helpers/with-system [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (helpers/with-webdriver [driver]
      (testing "feedback scores display with correct types"
        (let [env    @helpers/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate
                      agent
                      {"mode" "prefixed" "text" "score-check"})]

          @(aor/agent-result-async agent invoke)
          (Thread/sleep 100)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (e/go driver trace-url)
              (e/wait-visible
               driver
               {:data-id "feedback-tab"}
               {:timeout default-timeout})

              (testing "switch to feedback tab"
                (e/click driver {:data-id "feedback-tab"})
                (e/wait-visible driver {:data-id "feedback-list"} {:timeout 2}))

              (testing "feedback items display"
                (is (e/visible? driver {:data-id "feedback-item-0"})))

              (testing "navigate to node feedback for numeric scores"
                (e/click driver {:data-id "info-tab"})
                (e/wait-visible driver {:class "react-flow__node"} {:timeout 2})
                (e/click driver {:data-id "node-process"})
                (e/wait-visible driver {:data-id "node-feedback-tab"} {:timeout 2})
                (e/click driver {:data-id "node-feedback-tab"})
                (e/wait-visible driver {:data-id "node-feedback-container"} {:timeout 2}))

              (testing "numeric scores display in node feedback"
                (is (e/visible? driver {:data-id "feedback-list"}))))))))))

(deftest ^:integration multiple-feedback-sources-test
  ;; Test that feedback from multiple evaluators displays together.
  ;; Uses both agent-level and node-level evaluators.
  (helpers/with-system
    [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (helpers/with-webdriver [driver]
      (testing "multiple feedback sources display together"
        (let [env    @helpers/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate
                      agent
                      {"mode" "medium" "text" "multi-eval"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (e/go driver trace-url)
              (e/wait-visible
               driver
               {:data-id "feedback-tab"}
               {:timeout default-timeout})

              (testing "agent-level feedback has multiple items"
                (e/click driver {:data-id "feedback-tab"})
                (e/wait-visible driver {:data-id "feedback-list"} {:timeout 2})
                (is (e/exists? driver {:data-id "feedback-item-0"}))
                (is (e/exists? driver {:data-id "feedback-item-1"})))

              (testing "node-level feedback also has multiple items"
                (e/click driver {:data-id "info-tab"})
                (e/wait-visible driver {:class "react-flow__node"} {:timeout 2})
                (e/click driver {:data-id "node-process"})
                (e/wait-visible driver {:data-id "node-feedback-tab"} {:timeout 2})
                (e/click driver {:data-id "node-feedback-tab"})
                (e/wait-visible driver {:data-id "node-feedback-container"} {:timeout 2})
                (is (e/exists? driver {:data-id "feedback-item-0"}))
                (is (e/exists? driver {:data-id "feedback-item-1"}))))))))))
