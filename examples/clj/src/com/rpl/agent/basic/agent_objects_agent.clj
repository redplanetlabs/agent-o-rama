(ns com.rpl.agent.basic.agent-objects-agent
  "Demonstrates agent objects for sharing resources across agent nodes.

  Features demonstrated:
  - declare-agent-object: Static shared objects
  - declare-agent-object-builder: Dynamic object creation with setup context
  - get-agent-object: Access shared objects from agent nodes
  - Thread-unsafe objects: Safely using non-thread-safe objects via pooling
  - Object sharing across multiple nodes and invocations"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Thread-unsafe service using volatile for fast, non-thread-safe state
(defrecord MessageService [version counter-vol]
  Object
  (toString [_]
    (format "MessageService[version=%s, counter=%d]"
            version
            @counter-vol)))

(defn create-message-service
  "Factory function for MessageService"
  [version]
  (->MessageService version (volatile! 0)))

(defn reset-message-service!
  "Reset the message service for a new agent invocation  - NOT thread-safe"
  [service]
  (vreset! (:counter-vol service) 0))

(defn use-message-service!
  "Use the message service - NOT thread-safe"
  [service input send-to]
  (let [new-count (vswap! (:counter-vol service) inc)
        version   (:version service)]
    {:message (str "v" version ": " input " (#" new-count " -> " send-to ")")}))

;;; Agent module demonstrating agent objects
(aor/defagentmodule AgentObjectsModule
  [topology]

  ;; Static agent objects - simple values
  (aor/declare-agent-object topology "app-version" "1.2.3")
  (aor/declare-agent-object topology "send-to" "alerts")

  ;; Dynamic agent object builder - service that uses version
  (aor/declare-agent-object-builder
   topology
   "message-service"
   (fn [setup]
     (let [version (aor/get-agent-object setup "app-version")]
       (create-message-service version))))

  (-> (aor/new-agent topology "AgentObjectsAgent")
      (aor/node
       "use-service"
       nil
       (fn [agent-node input]
         (let [service (aor/get-agent-object agent-node "message-service")
               send-to (aor/get-agent-object agent-node "send-to")]
           (reset-message-service! service)
           ;; Use the thread-unsafe service (safe due to pooling)
           (let [service-result (use-message-service! service input send-to)]
             (aor/result! agent-node (:message service-result))))))))

(defn -main
  "Run the agent objects example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AgentObjectsModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name AgentObjectsModule))
          agent   (aor/agent-client manager "AgentObjectsAgent")]

      (println "Agent Objects Example:")

      ;; Multiple concurrent invocations to show shared state
      (println "\n--- Initiating concurrent invocations ---")
      (let [invoke1 (aor/agent-initiate agent "Hello")
            invoke2 (aor/agent-initiate agent "World")
            invoke3 (aor/agent-initiate agent "Again")]

        (println "Getting results...")
        (let [result1 (aor/agent-result agent invoke1)
              result2 (aor/agent-result agent invoke2)
              result3 (aor/agent-result agent invoke3)]
          (println "Result 1:" result1)
          (println "Result 2:" result2)
          (println "Result 3:" result3)))

      (println
       "\nEach message includes version and send-to from static objects")
      (println "and the counter is always #1 -> alerts as the service is reset")
      (println "at the start of each invocation, ")
      (println "and messaege-service instances are not shared."))))
