(ns com.rpl.agent-o-rama.ui.rules-e2e-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth]
   [com.rpl.agent-o-rama.ui.rules-test-agent :as rta :refer [RulesTestAgentModule]]
   [etaoin.api :as e]))

(def ^:private default-timeout 120)

(defonce system (volatile! nil))
;; (eth/teardown-system system)
;; (vreset! system nil)

(defn post-deploy-hook
  "Creates a post-deploy hook that sets up feedback testing.
   Options are passed through to setup-feedback-testing!"
  [opts]
  (fn [ipc module-name]
    (let [manager (aor/agent-manager ipc module-name)
          depot   (:global-actions-depot
                   (aor-types/underlying-objects manager))]
      (rta/setup-rules-testing! manager depot opts))))

(defn- wait-for-rules-table
  "Wait for the rules table to be visible"
  [driver]
  (e/wait-visible driver {:tag :table} {:timeout eth/default-timeout}))

(defn- rules-url
  [env]
  (str (eth/module-base-url env) "/agent/RulesTestAgent/rules"))

(defn- navigate-to-rules-page
  "Navigate to the rules page for the test agent"
  [driver env]
  (let [rules-url (rules-url env)]
    (e/go driver rules-url)
    (wait-for-rules-table driver)))

(defn- count-table-rows
  "Count the number of data rows in the rules table (excluding header)"
  [driver]
  (let [rows (e/query-all driver {:tag :tbody} {:tag :tr})]
    (count rows)))

(defn- rule-exists?
  "Check if a rule with the given name exists in the table"
  [driver rule-name]
  (try
    (e/exists? driver {:tag :td :fn/text rule-name})
    (catch Exception _
      false)))

(defn- click-add-rule-button
  "Click the '+ Add Rule' button"
  [driver]
  (e/wait-visible driver {:tag :button :fn/has-text "+ Add Rule"} {:timeout 5})
  (e/click driver {:tag :button :fn/has-text "+ Add Rule"}))

(defn- wait-for-modal
  "Wait for the add rule modal to appear"
  [driver]
  (e/wait-visible driver {:tag :h3 :fn/has-text "Add Rule"} {:timeout 5}))

(defn- fill-rule-name
  "Fill in the rule name field"
  [driver rule-name]
  (e/wait-visible driver {:data-id :rule-name} {:timeout 5})
  (e/fill driver {:data-id :rule-name} rule-name))

(defn- select-action
  "Select an action from the dropdown"
  [driver action-name]
  (e/wait-visible driver {:data-id :action-selector} {:timeout 5})
  (e/click driver {:data-id :action-selector})
  (Thread/sleep 200)
  (e/click driver {:tag :option :fn/has-text action-name}))

(defn- click-save-button
  "Click the Save button in the modal"
  [driver]
  (e/wait-visible driver {:tag :button :fn/has-text "Add Rule"} {:timeout 5})
  (e/scroll-query
   driver
   {:tag :button :fn/has-text "Add Rule"}
   {"behavior" "instant"})
  (e/click driver {:tag :button :fn/has-text "Add Rule"}))

(defn- click-delete-rule
  "Click the delete button for a specific rule"
  [driver rule-name]
  (let [row (e/query driver {:tag :tr :fn/has-text rule-name})]
    (e/click driver (e/child row {:tag :button :fn/has-class "hover:text-red-600"}))))

(defn- confirm-delete
  "Confirm the delete action in the browser alert"
  [driver]
  (e/accept-alert driver))

(defn- click-view-log
  "Click the 'View Log' link for a specific rule"
  [driver rule-name]
  (let [row (e/query driver {:tag :tr :fn/has-text rule-name})]
    (e/click driver (e/child row {:tag :a :fn/has-text "View Log"}))))

(defn- wait-for-action-log-entries
  "Wait for action log entries to appear"
  [driver]
  (e/wait-visible driver {:tag :table} {:timeout 10})
  (Thread/sleep 500))

(defn- count-action-log-entries
  "Count the number of entries in the action log table"
  [driver]
  (try
    (let [rows (e/query-all driver {:tag :tbody} {:tag :tr})]
      (count rows))
    (catch Exception _
      0)))

(deftest ^:integration add-and-delete-rule-test
  ;; Test adding and deleting a rule via the UI
  (eth/with-system
    [system RulesTestAgentModule {:post-deploy-hook (post-deploy-hook {})}]
    (eth/with-webdriver [system driver]
      (e/with-postmortem driver {:dir "target/etaoin"}
        (testing "add a new rule"
          (let [env @system]
            (navigate-to-rules-page driver env)

            (let [initial-count (count-table-rows driver)]
              (click-add-rule-button driver)
              (wait-for-modal driver)

              (fill-rule-name driver "test-rule-1")
              (select-action driver "counting-action")
              (click-save-button driver)

              (wait-for-rules-table driver)

              (testing "rule appears in table"
                (is (rule-exists? driver "test-rule-1"))
                (is (= (inc initial-count) (count-table-rows driver)))))))

        (testing "delete a rule"
          (let [env @system]
            (navigate-to-rules-page driver env)

            (let [initial-count (count-table-rows driver)]
              (when (> initial-count 0)
                (click-delete-rule driver "test-rule-1")
                (confirm-delete driver)

                (Thread/sleep 1000)

                (testing "rule is removed from table"
                  (is (not (rule-exists? driver "test-rule-1")))
                  (is (= (dec initial-count) (count-table-rows driver))))))))))))

(deftest ^:integration action-log-test
  ;; Test that rules execute and actions appear in the action log
  (eth/with-system
    [system RulesTestAgentModule {:post-deploy-hook (post-deploy-hook {})}]
    (eth/with-webdriver [system driver]
      (e/with-postmortem driver {:dir "target/etaoin"}
        (testing "rule execution generates action log entries"
          (let [env     @system
                manager (aor/agent-manager (:ipc env) (:module-name env))
                agent   (aor/agent-client manager "RulesTestAgent")
                global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]

            (ana/add-rule!
             global-actions-depot
             "test-success-rule"
             "RulesTestAgent"
             {:node-name         nil
              :action-name       "logging-action"
              :action-params     {}
              :filter            (aor-types/->AndFilter [])
              :sampling-rate     1.0
              :start-time-millis 0
              :status-filter     :success})

            (Thread/sleep 2000)

            (let [invoke (aor/agent-initiate agent {"mode" "success" "input" "test"})]
              @(aor/agent-result-async agent invoke))

            (Thread/sleep 2000)

            (navigate-to-rules-page driver env)
            (click-view-log driver "test-success-rule")
            (wait-for-action-log-entries driver)

            (testing "action log contains entries"
              (is (>= (count-action-log-entries driver) 1)))

            (ana/delete-rule! global-actions-depot "RulesTestAgent" "test-success-rule")))))))

(deftest ^:integration multiple-filter-types-test
  ;; Test rules with different filter types
  (eth/with-system
    [system RulesTestAgentModule {:post-deploy-hook (post-deploy-hook {})}]
    (eth/with-webdriver [system driver]
      (e/with-postmortem driver {:dir "target/etaoin"}
        (testing "rules with different filters execute correctly"
          (let [env     @system
                manager (aor/agent-manager (:ipc env) (:module-name env))
                agent   (aor/agent-client manager "RulesTestAgent")
                global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]

            (ana/add-rule!
             global-actions-depot
             "error-filter-rule"
             "RulesTestAgent"
             {:node-name         nil
              :action-name       "counting-action"
              :action-params     {}
              :filter            (aor-types/->ErrorFilter)
              :sampling-rate     1.0
              :start-time-millis 0
              :status-filter     :all})

            (ana/add-rule!
             global-actions-depot
             "latency-filter-rule"
             "RulesTestAgent"
             {:node-name         nil
              :action-name       "counting-action"
              :action-params     {}
              :filter            (aor-types/->LatencyFilter
                                  (aor-types/->ComparatorSpec :gte 500))
              :sampling-rate     1.0
              :start-time-millis 0
              :status-filter     :success})

            (Thread/sleep 2000)

            (testing "error filter triggers on error"
              (try
                (let [invoke (aor/agent-initiate agent {"mode" "error" "input" "test"})]
                  @(aor/agent-result-async agent invoke))
                (catch Exception _))

              (Thread/sleep 2000)

              (navigate-to-rules-page driver env)
              (click-view-log driver "error-filter-rule")
              (wait-for-action-log-entries driver)

              (is (>= (count-action-log-entries driver) 1)))

            (testing "latency filter triggers on slow execution"
              (let [invoke (aor/agent-initiate agent {"mode" "slow" "input" "test"})]
                @(aor/agent-result-async agent invoke))

              (Thread/sleep 2000)

              (navigate-to-rules-page driver env)
              (click-view-log driver "latency-filter-rule")
              (wait-for-action-log-entries driver)

              (is (>= (count-action-log-entries driver) 1)))

            (ana/delete-rule! global-actions-depot "RulesTestAgent" "error-filter-rule")
            (ana/delete-rule! global-actions-depot "RulesTestAgent" "latency-filter-rule")))))))

(deftest ^:integration node-specific-rules-test
  ;; Test node-specific vs agent-level rules
  (eth/with-system
    [system RulesTestAgentModule {:post-deploy-hook (post-deploy-hook {})}]
    (eth/with-webdriver [system driver]
      (e/with-postmortem driver {:dir "target/etaoin"}
        (testing "node-specific rules only trigger on specified node"
          (let [env     @system
                manager (aor/agent-manager (:ipc env) (:module-name env))
                agent   (aor/agent-client manager "RulesTestAgent")
                global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]

            (ana/add-rule!
             global-actions-depot
             "agent-level-rule"
             "RulesTestAgent"
             {:node-name         nil
              :action-name       "logging-action"
              :action-params     {}
              :filter            (aor-types/->AndFilter [])
              :sampling-rate     1.0
              :start-time-millis 0
              :status-filter     :success})

            (ana/add-rule!
             global-actions-depot
             "node-specific-rule"
             "RulesTestAgent"
             {:node-name         "process-node"
              :action-name       "logging-action"
              :action-params     {}
              :filter            (aor-types/->AndFilter [])
              :sampling-rate     1.0
              :start-time-millis 0
              :status-filter     :success})

            (Thread/sleep 2000)

            (let [invoke (aor/agent-initiate
                          agent
                          {"mode" "node-specific" "input" "test"})]
              @(aor/agent-result-async agent invoke))

            (Thread/sleep 2000)

            (testing "agent-level rule triggers"
              (navigate-to-rules-page driver env)
              (click-view-log driver "agent-level-rule")
              (wait-for-action-log-entries driver)
              (is (>= (count-action-log-entries driver) 1)))

            (testing "node-specific rule triggers"
              (navigate-to-rules-page driver env)
              (click-view-log driver "node-specific-rule")
              (wait-for-action-log-entries driver)
              (is (>= (count-action-log-entries driver) 1)))

            (ana/delete-rule!
             global-actions-depot
             "RulesTestAgent"
             "agent-level-rule")
            (ana/delete-rule!
             global-actions-depot
             "RulesTestAgent"
             "node-specific-rule")))))))

(deftest ^:integration token-count-filter-test
  ;; Test token-count filter with actual model calls
  (testing "Token count filter with chat model"
    (when (System/getenv "OPENAI_API_KEY")
      (eth/with-system
        [system RulesTestAgentModule {:post-deploy-hook (post-deploy-hook {})}]
        (eth/with-webdriver [system driver]
          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [env     @system
                  manager (aor/agent-manager (:ipc env) (:module-name env))
                  agent   (aor/agent-client manager "RulesTestAgent")
                  global-actions-depot (:global-actions-depot
                                        (aor-types/underlying-objects manager))
                  invoke  (aor/agent-initiate
                           agent
                           {"mode" "chat" "input" "Say hello"})]
              (aor/agent-result agent invoke)

              (Thread/sleep 2000)

              (testing "token-count filter triggers on model calls"
                (navigate-to-rules-page driver env)
                (click-view-log driver "token-count-rule")
                (wait-for-action-log-entries driver)
                (is (>= (count-action-log-entries driver) 1)))

              (ana/delete-rule!
               global-actions-depot
               "RulesTestAgent"
               "token-count-rule"))))))))
