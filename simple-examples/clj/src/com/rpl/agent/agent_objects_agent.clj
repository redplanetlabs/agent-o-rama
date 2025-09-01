(ns com.rpl.agent.agent-objects-agent
  "Demonstrates agent objects for sharing resources across agent nodes.
  
  Features demonstrated:
  - declare-agent-object: Static shared objects
  - declare-agent-object-builder: Dynamic object creation with setup context
  - get-agent-object: Access shared objects from agent nodes
  - Object sharing across multiple nodes and invocations"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Simple configuration object
(defrecord AppConfig [api-endpoint timeout-ms debug-mode])

;;; Simple service that uses configuration
(defrecord MessageService [config counter]
  Object
  (toString [_] (format "MessageService[endpoint=%s, counter=%d]"
                        (:api-endpoint config) @counter)))

(defn create-message-service
  "Factory function for MessageService"
  [config]
  (->MessageService config (atom 0)))

;;; Agent module demonstrating agent objects
(aor/defagentmodule AgentObjectsModule
  [topology]

  ;; Static agent object - shared configuration
  (aor/declare-agent-object
   topology
   "app-config"
   (->AppConfig "https://api.example.com" 5000 true))

  ;; Static agent object - simple value
  (aor/declare-agent-object topology "app-version" "1.2.3")

  ;; Dynamic agent object builder - service that uses configuration
  (aor/declare-agent-object-builder
   topology
   "message-service"
   (fn [setup]
     (let [config (aor/get-agent-object setup "app-config")]
       (create-message-service config))))

  ;; Another dynamic object that depends on both config and version
  (aor/declare-agent-object-builder
   topology
   "system-info"
   (fn [setup]
     (let [config (aor/get-agent-object setup "app-config")
           version (aor/get-agent-object setup "app-version")]
       {:config config
        :version version
        :startup-time (System/currentTimeMillis)
        :environment "development"})))

  (-> topology
      (aor/new-agent "AgentObjectsAgent")

      ;; First node: access static objects
      (aor/node "access-static" "use-service"
                (fn [agent-node input]
                  (let [config (aor/get-agent-object agent-node "app-config")
                        version (aor/get-agent-object agent-node "app-version")]
                    (println "Static objects accessed:")
                    (println "  Config:" config)
                    (println "  Version:" version)
                    (aor/emit! agent-node "use-service" {:input input
                                                         :config config
                                                         :version version}))))

      ;; Second node: use dynamic objects
      (aor/node "use-service" "combine-info"
                (fn [agent-node {:keys [input config version]}]
                  (let [service (aor/get-agent-object agent-node "message-service")]
                    ;; Use the service (increment counter)
                    (swap! (:counter service) inc)
                    (println "Service used:" service)
                    (aor/emit! agent-node "combine-info" {:input input
                                                          :service-state service
                                                          :usage-count @(:counter service)}))))

      ;; Final node: combine all information
      (aor/node "combine-info" nil
                (fn [agent-node {:keys [input service-state usage-count]}]
                  (let [system-info (aor/get-agent-object agent-node "system-info")]
                    (println "System info:" system-info)
                    (let [result {:processed-input input
                                  :service-info (str service-state)
                                  :usage-count usage-count
                                  :system-info system-info
                                  :processed-at (System/currentTimeMillis)}]
                      (aor/result! agent-node result)))))))

(defn -main
  "Run the agent objects example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AgentObjectsModule {:tasks 2 :threads 1})

    (let [manager (aor/agent-manager ipc (rama/get-module-name AgentObjectsModule))
          agent (aor/agent-client manager "AgentObjectsAgent")]

      (println "Agent Objects Example:")

      ;; Multiple invocations to show shared state
      (println "\n--- First invocation ---")
      (let [result1 (aor/agent-invoke agent "Hello")]
        (println "Result 1:" result1))

      (println "\n--- Second invocation ---")
      (let [result2 (aor/agent-invoke agent "World")]
        (println "Result 2:" result2))

      (println "\n--- Third invocation ---")
      (let [result3 (aor/agent-invoke agent "Again")]
        (println "Result 3:" result3))

      (println "\nNotice how the usage-count increases across invocations,")
      (println "demonstrating that agent objects maintain state between invocations."))))