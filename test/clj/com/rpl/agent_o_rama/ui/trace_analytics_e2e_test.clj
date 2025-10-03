(ns com.rpl.agent-o-rama.ui.trace-analytics-e2e-test
  (:require
   [clj-test-containers.core :as tc]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.trace-analytics-test-agent
    :refer [TraceAnalyticsTestAgentModule]]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [etaoin.api :as e]
   [shadow.cljs.devtools.api :as shadow])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [java.lang
    AutoCloseable]
   [org.testcontainers
    Testcontainers]))

(def ^:private default-port 8080)
(def ^:private default-timeout 60)

(defonce system (volatile! nil))

(defn- url-encode
  [^String s]
  (java.net.URLEncoder/encode s "UTF-8"))

;; (tc/create-network)
;; (reset! tc/started-instances #{})

(defn setup-container
  "Create and start a Selenium webdriver container.
   Returns the container."
  [port]
  (Testcontainers/exposeHostPorts (int-array [port]))
  (let [webdriver-port 4444
        container      (-> (tc/create
                            {:image-name    "selenium/standalone-chromium:latest"
                             :exposed-ports [webdriver-port]})
                           (tc/start!))]
    (vswap! system assoc :container container)
    container))

(defn setup-webdriver
  "Create and start a webdriver connected to the container.
   Returns the driver."
  [container]
  (let [webdriver-port 4444
        driver         (e/chrome
                        {:headless false
                         :port     (get (:mapped-ports container) webdriver-port)
                         :host     (:host container)
                         :args     ["--no-sandbox"]})]
    (vswap! system assoc :driver driver)
    driver))

(defn teardown-webdriver
  "Clean up webdriver resources."
  [driver]
  (e/quit driver))

(defn teardown-container
  "Clean up container resources."
  [container]
  (tc/stop! container))


(defn setup-ipc
  "Create or get existing IPC.
   Returns the IPC instance."
  []
  (or (:ipc @system)
      (let [ipc (rtest/create-ipc)]
        (vswap! system assoc :ipc ipc)
        ipc)))

(defn setup-agent-module
  "Deploy agent module and start UI server.
   Returns a map with :ipc, :module-name, and :port."
  [ipc agent-module {:keys [port] :or {port default-port}}]
  (let [module-name (rama/get-module-name agent-module)]
    (when-not (:launched @system)
      (rtest/launch-module! ipc agent-module {:tasks 1 :threads 1})
      (vswap! system assoc :launched true :module-name module-name))

    (when-not (:ui-launched @system)
      (shadow/compile :frontend)
      (aor/start-ui ipc {:port port})
      (vswap! system assoc :ui-launched true :port port))
    {:ipc         ipc
     :module-name module-name
     :port        port}))

(defn setup-system
  "Setup all resources in order: IPC, module, container."
  []
  (let [ipc (setup-ipc)]
    (setup-agent-module ipc TraceAnalyticsTestAgentModule {:port default-port})
    (setup-container default-port)))

(defn teardown-system
  "Teardown all resources in the system map in reverse order."
  [{:keys [container ipc module-name ui-launched launched]}]
  (when container
    (teardown-container container))
  (when ipc
    (when ui-launched
      (aor/stop-ui))
    (when (and launched module-name)
      (rtest/destroy-module! ipc module-name))
    (.close ^AutoCloseable ipc))
  (vreset! system nil))

(def ^:private in-test-runner? (System/getenv "aor.test-runner"))

(defn reusable-resource-fixture
  [f]
  (setup-system)
  (try
    (f)
    (finally
      (when in-test-runner?
        (teardown-system @system)))))

;; used by test runners
(use-fixtures :once reusable-resource-fixture)

(comment
  ;; Use these at a repl
  (setup-system)
  (teardown-system @system))


(defn teardown-agent-env
  "Clean up agent environment resources."
  [{:keys [ipc module-name]}]
  (aor/stop-ui)
  (rtest/destroy-module! ipc module-name)
  (.close ^AutoCloseable ipc))

(defmacro with-webdriver
  "Execute body with a webdriver setup.
   Binds driver-sym to the driver instance."
  [[driver-sym] & body]
  `(let [container#  (:container @~'system)
         ~driver-sym (setup-webdriver container#)]
     (try
       ~@body
       (finally
         (teardown-webdriver ~driver-sym)))))

(defmacro with-agent-env
  "Execute body with an agent environment setup.
   Binds env-sym to the environment map containing :ipc, :module-name, :port."
  [[env-sym agent-module & {:keys [port] :or {port default-port}}] & body]
  `(let [ipc#     (setup-ipc)
         ~env-sym (setup-agent-module ipc# ~agent-module {:port ~port})]
     (try
       ~@body
       (finally
         #_(teardown-agent-env ~env-sym)))))

(defn- agent-invoke-url
  [env agent-name ^AgentInvoke invoke]
  (let [invoke-id (.getAgentInvokeId invoke)
        task-id   (.getTaskId invoke)]
    (str
     "http://host.testcontainers.internal:" (:port env)
     "/agents/" (url-encode (:module-name env))
     "/agent/" agent-name
     "/invocations/"
     task-id
     "-" invoke-id)))

(deftest conditional-rendering-test
  ;; Test conditional rendering of stat sections based on available data
  (with-webdriver [driver]
    (testing "basic mode shows only execution time"
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :basic})]

        @(aor/agent-result-async agent invoke)

        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :db})]

        @(aor/agent-result-async agent invoke)

        (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :store})]

        @(aor/agent-result-async agent invoke)

        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :other})]

        @(aor/agent-result-async agent invoke)

        (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
                 "Other Operations"))))))))

(deftest dropdown-toggle-test
  ;; Test dropdown expand/collapse functionality
  (with-webdriver [driver (:port env)]

    (testing "other-operations dropdown toggles correctly"
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :other})]

        @(aor/agent-result-async agent invoke)

        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
      (let [env     @system
            manager (aor/agent-manager (:ipc env) (:module-name env))
            agent   (aor/agent-client manager "TraceTestAgent")
            invoke  (aor/agent-initiate agent {:mode :sub-agent})]

        @(aor/agent-result-async agent invoke)

        (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
                 (e/visible? driver {:data-id "subagent-stats-list"})))))))))

(deftest ^:integration trace-analytics-with-model-calls-test
  ;; Test trace analytics with model calls and token tracking
  (testing "Trace analytics with chat model mode"
    (when (System/getenv "OPENAI_API_KEY")
      (with-webdriver [driver 8081]
        (testing "chat mode shows model-calls and tokens sections"
          (let [env     @system
                manager (aor/agent-manager (:ipc env) (:module-name env))
                agent   (aor/agent-client manager "TraceTestAgent")
                invoke  (aor/agent-initiate agent
                                            {:mode  :chat
                                             :input "Say hello"})]

            @(aor/agent-result-async agent invoke)

            (e/with-postmortem driver {:dir "target/etaoin"}
              (let [trace-url (agent-invoke-url env "TraceTestAgent" invoke)]
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
                  (is (e/has-text? driver {:data-id "tokens"} "Total")))))))))))
