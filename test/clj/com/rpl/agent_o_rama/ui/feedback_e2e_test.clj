(ns com.rpl.agent-o-rama.ui.feedback-e2e-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth]
   [com.rpl.agent-o-rama.ui.feedback-test-agent
    :refer [FeedbackTestAgentModule setup-feedback-testing!]]
   [com.rpl.rama.test :as rtest]
   [etaoin.api :as e]))

(def ^:private default-timeout 120)

;;; Helper functions

(defn make-post-deploy-hook
  "Creates a post-deploy hook that sets up feedback testing.
   Options are passed through to setup-feedback-testing!"
  [opts]
  (fn [ipc module-name]
    (let [manager (aor/agent-manager ipc module-name)
          depot   (:global-actions-depot
                   (aor-types/underlying-objects manager))]
      (setup-feedback-testing! manager depot opts))))

(defn- wait-for-feedback
  [driver trace-url]
  (loop [i 0]
    (e/go driver trace-url)
    (eth/wait-visible driver "feedback-tab")
    (eth/scroll driver "agent-info-panel")
    (eth/scroll driver "feedback-tab")
    (e/click driver {:data-id "feedback-tab"})
    (when-not (or (> i 20)
                  (e/visible? driver {:data-id "feedback-list"}))
      (Thread/sleep 1000)
      (recur (unchecked-inc i)))))

(deftest ^:integration agent-level-feedback-test
  ;; Test agent-level feedback display in the main Feedback tab.
  ;; Uses feedback-test-agent which generates agent-level evaluator feedback.
  (eth/with-system
    [FeedbackTestAgentModule
     {:post-deploy-hook (make-post-deploy-hook {:include-node-rules? false})}]
    (eth/with-webdriver [driver]
      (testing "agent-level feedback displays correctly"
        (let [env    @eth/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "medium" "text" "test"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (eth/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (wait-for-feedback driver trace-url)
              (eth/wait-visible driver "feedback-tab")

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
  (eth/with-system
    [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (eth/with-webdriver [driver]
      (testing "node-level feedback displays correctly"
        (let [env    @eth/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "long" "text" "eval"})]

          @(aor/agent-result-async agent invoke)
          (Thread/sleep 100)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (eth/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (wait-for-feedback driver trace-url)
              (eth/wait-visible driver "feedback-tab")

              (testing "click on process node"
                (e/click driver {:fn/has-text "process"})
                (eth/wait-visible driver "node-feedback-tab"))

              (testing "node feedback tab exists"
                (is (e/visible? driver {:data-id "node-feedback-tab"})))

              (testing "switch to node feedback tab"
                (e/click driver {:data-id "node-feedback-tab"})
                (eth/wait-visible driver "node-feedback-container"))

              (testing "node feedback is visible"
                (is (e/visible? driver {:data-id "feedback-list"})))

              (testing "has feedback items from node-level evaluators"
                (is (e/exists? driver {:data-id "feedback-item-0"}))))))))))

(deftest ^:integration empty-feedback-state-test
  ;; Test empty state display when no feedback is present.
  ;; Uses an agent run without any evaluator rules configured.
  (eth/with-system [FeedbackTestAgentModule]
    (eth/with-webdriver [driver]
      (testing "empty feedback state displays correctly"
        (let [env    @eth/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "short"})]

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (eth/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (e/go driver trace-url)
              (aor/agent-result agent invoke)

              ;; NOTE rules run after node invocation is complete
              ;; which means display is not updated.
              (eth/wait-visible driver "feedback-tab")

              (testing "switch to feedback tab"
                (eth/scroll driver "feedback-tab")
                (e/click driver {:data-id "feedback-tab"})
                (eth/wait-visible driver "feedback-empty-state"))

              (testing "empty state is visible"
                (is (e/visible? driver {:data-id "feedback-empty-state"}))
                (is (e/has-text? driver
                                 {:data-id "feedback-empty-state"}
                                 "No feedback available"))))))))))

(deftest ^:integration feedback-score-types-test
  ;; Test display of different score types (boolean and numeric).
  ;; Uses feedback-test-agent evaluators that return different score formats.
  (eth/with-system
    [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (eth/with-webdriver [driver]
      (testing "feedback scores display with correct types"
        (let [env    @eth/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate
                      agent
                      {"mode" "prefixed" "text" "score-check"})]

          (aor/agent-result agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (eth/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]
              (wait-for-feedback driver trace-url)
              (eth/wait-visible driver "feedback-tab")

              (testing "switch to feedback tab"
                (e/click driver {:data-id "feedback-tab"})
                (eth/wait-visible driver "feedback-list"))

              (testing "feedback items display"
                (is (e/visible? driver {:data-id "feedback-item-0"})))

              (testing "navigate to node feedback for numeric scores"
                (e/click driver {:fn/has-text "process"})
                (eth/wait-visible driver "node-feedback-tab")
                (e/click driver {:data-id "node-feedback-tab"})
                (eth/wait-visible driver "node-feedback-container"))

              (testing "numeric scores display in node feedback"
                (is (e/visible? driver {:data-id "feedback-list"}))))))))))

(deftest ^:integration multiple-feedback-sources-test
  ;; Test that feedback from multiple evaluators displays together.
  ;; Uses both agent-level and node-level evaluators.
  (eth/with-system
    [FeedbackTestAgentModule {:post-deploy-hook (make-post-deploy-hook {})}]
    (eth/with-webdriver [driver]
      (testing "multiple feedback sources display together"
        (let [env    @eth/system
              agent  (aor/agent-client
                      (aor/agent-manager (:ipc env) (:module-name env))
                      "FeedbackTestAgent")
              invoke (aor/agent-initiate agent {"mode" "medium"})
              wait-for-mb-count
              #(rtest/wait-for-microbatch-processed-count
                (:ipc env)
                (:module-name env)
                aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME
                %)]

          (is (= "test-medium-result" (aor/agent-result agent invoke)))

          (wait-for-mb-count 5) ; wait for rules to run

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (eth/agent-invoke-url
                             env
                             "FeedbackTestAgent"
                             invoke)]

              (testing "agent-level feedback has multiple items"
                (wait-for-feedback driver trace-url)
                (is (e/visible? driver {:data-id "feedback-list"}))
                (is (e/exists? driver {:data-id "feedback-item-0"}))
                (is (e/exists? driver {:data-id "feedback-item-1"})))

              (testing "node-level feedback also has multiple items"
                ;; (e/click driver {:data-id "info-tab"})
                ;; (e/wait-visible
                ;;  driver
                ;;  {:class "react-flow__node"}
                ;;  {:timeout default-timeout})
                (e/click driver {:fn/has-text "process"})
                (eth/wait-visible driver "node-feedback-tab")
                (e/click driver {:data-id "node-feedback-tab"})
                (eth/wait-visible driver "node-feedback-container")
                (is (e/exists? driver {:data-id "feedback-item-0"}))
                (is (e/exists? driver {:data-id "feedback-item-1"}))))))))))
