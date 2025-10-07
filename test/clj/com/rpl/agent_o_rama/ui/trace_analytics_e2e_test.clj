(ns com.rpl.agent-o-rama.ui.trace-analytics-e2e-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as helpers]
   [com.rpl.agent-o-rama.ui.trace-analytics-test-agent
    :refer [TraceAnalyticsTestAgentModule]]
   [etaoin.api :as e]))

(def ^:private default-timeout 120)

(deftest conditional-rendering-test
  ;; Test conditional rendering of stat sections based on available data
  (helpers/with-system TraceAnalyticsTestAgentModule
    (helpers/with-webdriver [driver]
      (testing "basic mode shows only execution time"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "basic"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible driver {:data-id "trace-analytics"} {:timeout default-timeout})

              (testing "shows execution time"
                (is (e/visible? driver {:data-id "execution-time"})))

              (testing "hides sections without data"
                (is (not (e/exists? driver {:data-id "retry-count"})))
                (is (not (e/exists? driver {:data-id "db-operations"})))
                (is (not (e/exists? driver {:data-id "store-operations"})))
                (is (not (e/exists? driver {:data-id "model-calls"})))
                (is (not (e/exists? driver {:data-id "tokens"})))
                (is (not (e/exists? driver {:data-id "other-operations"}))))))))

      (testing "db mode shows db-operations section"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "db"})]

          @(aor/agent-result-async agent invoke)

          (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
            (e/go driver trace-url)
            (e/wait-visible driver {:data-id "trace-analytics"} {:timeout default-timeout})

            (testing "shows db-operations section"
              (is (e/visible? driver {:data-id "db-operations"}))
              (is (e/has-text?
                   driver
                   {:data-id "db-operations"}
                   "DB Operations")))

            (testing "hides other sections"
              (is (not (e/exists? driver {:data-id "store-operations"})))
              (is (not (e/exists? driver {:data-id "model-calls"})))
              (is (not (e/exists? driver {:data-id "tokens"})))))))

      (testing "store mode shows store-operations section"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "store"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible
               driver
               {:data-id "trace-analytics"}
               {:timeout default-timeout})

              (testing "shows store-operations section"
                (is (e/visible? driver {:data-id "store-operations"}))
                (is (e/has-text?
                     driver
                     {:data-id "store-operations"}
                     "Store Operations")))

              (testing "hides other sections"
                (is (not (e/exists? driver {:data-id "db-operations"})))
                (is (not (e/exists? driver {:data-id "model-calls"})))
                (is (not (e/exists? driver {:data-id "tokens"}))))))))

      (testing "other mode shows other-operations section"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "other"})]

          @(aor/agent-result-async agent invoke)

          (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
            (e/go driver trace-url)
            (e/wait-visible
             driver
             {:data-id "trace-analytics"}
             {:timeout default-timeout})

            (testing "shows other-operations section"
              (is (e/visible? driver {:data-id "other-operations"}))
              (is (e/has-text?
                   driver
                   {:data-id "other-operations"}
                   "Other Operations")))))))))

(deftest dropdown-toggle-test
  ;; Test dropdown expand/collapse functionality
  (helpers/with-system TraceAnalyticsTestAgentModule
    (helpers/with-webdriver [driver]
      (testing "node-stats dropdown toggles correctly"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "basic"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible
               driver
               {:data-id "trace-analytics"}
               {:timeout default-timeout})

              (testing "node-stats section exists"
                (is (e/visible? driver {:data-id "node-stats"})))

              (testing "initially collapsed"
                (is (not (e/visible? driver {:data-id "node-stats-list"}))))

              (testing "expands on click"
                (e/click driver {:data-id "node-stats-toggle"})
                (e/wait-visible
                 driver
                 {:data-id "node-stats-list"}
                 {:timeout 2})
                (is (e/visible? driver {:data-id "node-stats-list"})))

              (testing "collapses on second click"
                (e/click driver {:data-id "node-stats-toggle"})
                (Thread/sleep 200)
                (is (not
                     (e/visible? driver {:data-id "node-stats-list"}))))))))

      (testing "other-operations dropdown toggles correctly"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "other"})]

          @(aor/agent-result-async agent invoke)

          (e/with-postmortem driver {:dir "target/etaoin"}
            (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
              (e/go driver trace-url)
              (e/wait-visible
               driver
               {:data-id "trace-analytics"}
               {:timeout default-timeout})

              (testing "initially collapsed"
                (is (not (e/visible? driver {:data-id "other-operations-list"}))))

              (testing "expands on click"
                (e/click driver {:data-id "other-operations-toggle"})
                (e/wait-visible
                 driver
                 {:data-id "other-operations-list"}
                 {:timeout 2})
                (is (e/visible? driver {:data-id "other-operations-list"}))
                (is (e/has-text?
                     driver
                     {:data-id "other-operations-list"}
                     "Other")))

              (testing "collapses on second click"
                (e/click driver {:data-id "other-operations-toggle"})
                (Thread/sleep 200)
                (is (not
                     (e/visible? driver {:data-id "other-operations-list"}))))))))

      (testing "subagent-stats dropdown toggles correctly"
        (let [env     @helpers/system
              manager (aor/agent-manager (:ipc env) (:module-name env))
              agent   (aor/agent-client manager "TraceTestAgent")
              invoke  (aor/agent-initiate agent {"mode" "sub-agent"})]

          @(aor/agent-result-async agent invoke)

          (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
            (e/go driver trace-url)
            (e/wait-visible
             driver
             {:data-id "trace-analytics"}
             {:timeout default-timeout})

            (testing "initially collapsed"
              (is (not (e/visible? driver {:data-id "subagent-stats-list"}))))

            (testing "shows correct count in label"
              (is (e/has-text?
                   driver
                   {:data-id "subagent-stats-toggle"}
                   "By agent (2)")))

            (testing "expands on click"
              (e/click driver {:data-id "subagent-stats-toggle"})
              (e/wait-visible
               driver
               {:data-id "subagent-stats-list"}
               {:timeout 2})
              (is (e/visible? driver {:data-id "subagent-stats-list"})))

            (testing "shows top-level entry"
              (is (e/visible? driver {:data-id "subagent-top-level"}))
              (is (e/has-text?
                   driver
                   {:data-id "subagent-top-level"}
                   "Top-level"))
              (is (e/has-text?
                   driver
                   {:data-id "subagent-top-level"}
                   "main agent")))

            (testing "shows sub-agent entry"
              (is (e/visible? driver {:data-id "subagent-BasicSubAgent"}))
              (is (e/has-text? driver
                               {:data-id "subagent-BasicSubAgent"}
                               "TraceAnalyticsTestAgentModule/BasicSubAgent"))
              (is (e/has-text?
                   driver
                   {:data-id "subagent-BasicSubAgent"}
                   "1 call")))

            (testing "collapses on second click"
              (e/click driver {:data-id "subagent-stats-toggle"})
              (Thread/sleep 200)
              (is (not
                   (e/visible? driver {:data-id "subagent-stats-list"}))))))))))

(deftest ^:integration trace-analytics-with-model-calls-test
  ;; Test trace analytics with model calls and token tracking
  (testing "Trace analytics with chat model mode"
    (when (System/getenv "OPENAI_API_KEY")
      (helpers/with-system TraceAnalyticsTestAgentModule
        (helpers/with-webdriver [driver]
          (testing "chat mode shows model-calls and tokens sections"
            (let [env     @helpers/system
                  manager (aor/agent-manager (:ipc env) (:module-name env))
                  agent   (aor/agent-client manager "TraceTestAgent")
                  invoke  (aor/agent-initiate agent
                                              {"mode"  "chat"
                                               "input" "Say hello"})]

              @(aor/agent-result-async agent invoke)

              (e/with-postmortem driver {:dir "target/etaoin"}
                (let [trace-url (helpers/agent-invoke-url env "TraceTestAgent" invoke)]
                  (e/go driver trace-url)
                  (e/wait-visible
                   driver
                   {:data-id "trace-analytics"}
                   {:timeout default-timeout})

                  (testing "shows model-calls section"
                    (is (e/visible? driver {:data-id "model-calls"}))
                    (is (e/has-text?
                         driver
                         {:data-id "model-calls"}
                         "Model Calls")))

                  (testing "shows tokens section with breakdown"
                    (is (e/visible? driver {:data-id "tokens"}))
                    (is (e/has-text? driver {:data-id "tokens"} "Tokens"))
                    (is (e/has-text? driver {:data-id "tokens"} "Input"))
                    (is (e/has-text? driver {:data-id "tokens"} "Output"))
                    (is (e/has-text? driver {:data-id "tokens"} "Total"))))))))))))
