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
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server])
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

(def default-timeout 120)

(def ^:private in-test-runner? (System/getProperty "aor.test.runner"))

(defn- url-encode
  [^String s]
  (java.net.URLEncoder/encode s "UTF-8"))

(defn setup-container
  "Create and start a Selenium webdriver container.
   Returns the container."
  [system port]
  (when-not (:container system)
    (Testcontainers/exposeHostPorts (int-array [port]))
    (let [webdriver-port 4444
          container      (-> (tc/create
                              {:image-name    "selenium/standalone-chromium:latest"
                               :exposed-ports [webdriver-port]})
                             (tc/start!))]
      {:container container})))

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
    driver))

(defn teardown-webdriver
  "Clean up webdriver resources."
  [driver]
  (e/quit driver))

(defn teardown-container
  "Clean up container resources."
  [{:keys [container]}]
  (when container
    (tc/stop! container)
    {:container nil}))

(defn setup-ipc
  "Create or get existing IPC from system-vol.
   Returns the IPC instance."
  [system]
  (when-not (:ipc system)
    {:ipc (rtest/create-ipc)}))

(defn setup-agent-module
  "Deploy agent module.
   Returns a map with :ipc, :module-name, and :launched flag.

   Options:
   - :post-deploy-hook - Function to call after module is deployed,
                         receives ipc and module-name"
  [system agent-module {:keys [post-deploy-hook]}]
  (when-not (:launched system)
    (let [module-name (rama/get-module-name agent-module)
          ipc         (:ipc system)]
      (rtest/launch-module!
       ipc
       agent-module
       {:tasks 1 :threads 1})
      (when post-deploy-hook
        (post-deploy-hook ipc module-name))
      {:module-name module-name
       :launched    true})))

(defn setup-agent-ui
  "Start the agent UI server.
   Returns true if UI was started, false if already running.

   Options:
   - :port - UI server port (default: 8080)"
  [system {:keys [port] :or {port default-port}}]
  (when-not (:ui-launched system)
    (if in-test-runner?
      (shadow/compile :dev)
      (do
        (shadow.cljs.devtools.server/start!)
        (shadow/watch :dev)))
    (aor/start-ui (:ipc system) {:port port})
    {:ui-launched true
     :port        port}))

(defn setup-system
  "Setup all resources in order: IPC, module, UI, container.
   Returns a system map with all resources.

   Options:
   - :post-deploy-hook - Function to call after module is deployed,
                         receives ipc and module-name"
  [system-vol agent-module & {:keys [post-deploy-hook]}]
  (let [merge-vol (fn [val] (vswap! system-vol merge val))]
    (-> @system-vol
        (setup-ipc)
        merge-vol
        (setup-agent-module
         agent-module
         {:post-deploy-hook post-deploy-hook})
        merge-vol
        (setup-agent-ui {:port default-port})
        merge-vol
        (setup-container default-port)
        merge-vol)))

(defn- teardown-agent-ui
  [{:keys [ui-launched]}]
  (when ui-launched
    (when-not in-test-runner?
      (shadow.cljs.devtools.server/stop!))
    (aor/stop-ui)
    {:ui-launched nil
     :port        nil}))

(defn- teardown-module
  [{:keys [ipc launched module-name]}]
  (when (and ipc launched module-name)
    (try
      (rtest/destroy-module! ipc module-name)
      (catch Exception e
        (println "Destroying module failed:" (ex-message e) (ex-data e))))
    {:launched    nil
     :module-name nil}))

(defn- teardown-ipc
  [{:keys [ipc]}]
  (when ipc
    (.close ^AutoCloseable ipc)
    {:ipc nil}))

(defn teardown-system
  "Teardown all resources in the system map in reverse order."
  [system-vol]
  (let [merge-vol (fn [val] (vswap! system-vol merge val))]
    (-> @system-vol
        (teardown-container)
        merge-vol
        (teardown-agent-ui)
        merge-vol
        (teardown-module)
        merge-vol
        (teardown-ipc)
        merge-vol)))

(defn reusable-system-fixture
  "Test fixture that sets up system resources.
   system-vol: Volatile to store system state (shared within namespace)
   agent-module: The agent module to deploy
   f: The test function to execute

   Options:
   - :post-deploy-hook - Function to call after module is deployed,
                         receives ipc and module-name"
  [system-vol agent-module f & {:keys [post-deploy-hook]}]
  (setup-system
   system-vol
   agent-module
   :post-deploy-hook
   post-deploy-hook)
  (try
    (f)
    (finally
      (when in-test-runner?
        (teardown-system system-vol)))))

(defmacro with-system
  "Execute body with a system setup.

   Arguments:
   - system-vol: Volatile to store system state
   - agent-module: The agent module to deploy
   - opts (optional): Map with options
     - :post-deploy-hook - Function to call after module is deployed,
                           receives ipc and module-name

   Examples:
   (with-system [system FeedbackTestAgentModule]
     (testing ...))

   (with-system [system MyModule {:post-deploy-hook (fn [ipc module-name] ...)}]
     (testing ...))"
  [[system-vol agent-module & [{:keys [post-deploy-hook]}]] & body]
  `(reusable-system-fixture
    ~system-vol
    ~agent-module
    (fn []
      ~@body)
    ~@(when post-deploy-hook [:post-deploy-hook post-deploy-hook])))

(defn webdriver-fixture
  "Test fixture that sets up webdriver resources.
   system-vol: Volatile containing system with :container
   f: Function that takes a driver as argument"
  [system-vol f]
  (let [container (:container @system-vol)
        driver    (setup-webdriver container)]
    (try
      (f driver)
      (finally
        (teardown-webdriver driver)))))

(defmacro with-webdriver
  "Execute body with a webdriver setup.
   system-vol: Volatile containing system with :container
   Binds driver-sym to the driver instance."
  [[system-vol driver-sym] & body]
  `(webdriver-fixture
    ~system-vol
    (fn [~driver-sym]
      ~@body)))

(defn module-base-url
  "Generate base URL for a module in the UI.
   Returns URL up to and including the module name.
   env: Environment map with :port and :module-name"
  [env]
  (str
   "http://host.testcontainers.internal:" (:port env)
   "/agents/" (url-encode (:module-name env))))

(defn agent-invoke-url
  "Generate URL for viewing an agent invocation in the UI.
   env: Environment map with :port and :module-name
   agent-name: Name of the agent
   invoke: AgentInvoke instance"
  [env agent-name ^AgentInvoke invoke]
  (let [invoke-id (.getAgentInvokeId invoke)
        task-id   (.getTaskId invoke)]
    (str
     (module-base-url env)
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
