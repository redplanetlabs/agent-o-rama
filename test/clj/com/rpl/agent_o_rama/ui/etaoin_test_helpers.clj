(ns com.rpl.agent-o-rama.ui.etaoin-test-helpers
  "Reusable helpers for Etaoin-based E2E tests.
   Provides setup/teardown functions for:
   - Selenium containers
   - WebDriver instances
   - IPC and agent modules
   - UI servers
   - Complete system fixtures"
  (:require
   [clj-test-containers.core :as tc]
   [com.rpl.agent-o-rama :as aor]
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

(def ^:private default-port
  (if (System/getProperty "aor.test.runner")
    8081
    8080))

(def ^:private default-timeout 120)

(defonce system (volatile! nil))

(defn- url-encode
  [^String s]
  (java.net.URLEncoder/encode s "UTF-8"))

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
                        {:headless true
                         :size     [1280 800]
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
   Returns a map with :ipc, :module-name, and :port.

   Options:
   - :port - UI server port (default: 8080)
   - :post-deploy-hook - Function to call after module is deployed,
                         receives ipc and module-name"
  [ipc agent-module {:keys [port post-deploy-hook] :or {port default-port}}]
  (let [module-name (rama/get-module-name agent-module)]
    (when-not (:launched @system)
      (rtest/launch-module! ipc agent-module {:tasks 1 :threads 1})
      (vswap! system assoc :launched true :module-name module-name)

      ;; Call post-deploy hook if provided
      (when post-deploy-hook
        (post-deploy-hook ipc module-name)))

    (when-not (:ui-launched @system)
      (shadow/compile :frontend)
      (aor/start-ui ipc {:port port})
      (vswap! system assoc :ui-launched true :port port))
    {:ipc         ipc
     :module-name module-name
     :port        port}))

(defn setup-system
  "Setup all resources in order: IPC, module, container.

   Options:
   - :post-deploy-hook - Function to call after module is deployed, receives ipc and module-name"
  [agent-module & {:keys [post-deploy-hook]}]
  (let [ipc (setup-ipc)]
    (setup-agent-module
     ipc
     agent-module
     {:port default-port
      :post-deploy-hook post-deploy-hook})
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

(defn reusable-system-fixture
  "Test fixture that sets up system resources.
   agent-module: The agent module to deploy
   f: The test function to execute

   Options:
   - :post-deploy-hook - Function to call after module is deployed, receives ipc and module-name"
  [agent-module f & {:keys [post-deploy-hook]}]
  (setup-system agent-module :post-deploy-hook post-deploy-hook)
  (try
    (f)
    (finally
      (when in-test-runner?
        (teardown-system @system)))))

(defmacro with-system
  "Execute body with a system setup.

   Arguments:
   - agent-module: The agent module to deploy
   - opts (optional): Map with options
     - :post-deploy-hook - Function to call after module is deployed, receives ipc and module-name

   Examples:
   (with-system [FeedbackTestAgentModule]
     (testing ...))

   (with-system [FeedbackTestAgentModule {:post-deploy-hook (fn [ipc module-name] ...)}]
     (testing ...))"
  [[agent-module & [{:keys [post-deploy-hook]}]] & body]
  `(reusable-system-fixture
    ~agent-module
    (fn []
      ~@body)
    ~@(when post-deploy-hook [:post-deploy-hook post-deploy-hook])))

(defn webdriver-fixture
  "Test fixture that sets up webdriver resources.
   f: Function that takes a driver as argument"
  [f]
  (let [container (:container @system)
        driver    (setup-webdriver container)]
    (try
      (f driver)
      (finally
        (teardown-webdriver driver)))))

(defmacro with-webdriver
  "Execute body with a webdriver setup.
   Binds driver-sym to the driver instance."
  [[driver-sym] & body]
  `(webdriver-fixture
    (fn [~driver-sym]
      ~@body)))

(defn teardown-agent-env
  "Clean up agent environment resources."
  [{:keys [ipc module-name]}]
  (aor/stop-ui)
  (rtest/destroy-module! ipc module-name)
  (.close ^AutoCloseable ipc))

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

(defn agent-invoke-url
  "Generate URL for viewing an agent invocation in the UI.
   env: Environment map with :port and :module-name
   agent-name: Name of the agent
   invoke: AgentInvoke instance"
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

(defn wait-visible
  [driver data-id]
  (e/wait-visible
   driver
   {:data-id data-id}
   {:timeout default-timeout}))

(defn scroll
  [driver data-id]
  (e/scroll-query
   driver
   {:data-id data-id}
   {"behavior" "instant" "block" "start"}))
