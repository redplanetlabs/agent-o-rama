(ns com.rpl.agent.basic.basic-agent
  "Demonstrates basic agent definition with a single node and invocation.

  Features demonstrated:
  - defagentmodule: Define an agent module
  - agent-topology: Create agent topology
  - new-agent: Create a new agent
  - agent node function: Define a single agent node declaration
  - agent-node: Use agent-node argument
  - result!: Return final result from a node
  - agent-manager: Create client manager
  - agent-names: Query available agent names
  - agent-client: Get client for specific agent
  - agent-invoke: Synchronously invoke agent"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Basic agent module with single node
(aor/defagentmodule BasicAgentModule
  [topology]

  ;; Create agent with single node that processes input and returns result
  (-> topology
      (aor/new-agent "BasicAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node user-name] ; user-name is the value in the agent-invoke
         ;; Create a welcome message for the user
         (let [result (str "Welcome to agent-o-rama, " user-name "!")]
           ;; Return the final result
           (aor/result! agent-node result))))))

(defn -main
  "Run the basic agent example with sample input"
  [& _args]
  ;; Create in-process cluster
  (with-open [ipc (rtest/create-ipc)]
    ;; launch the agent module
    (rtest/launch-module! ipc BasicAgentModule {:tasks 1 :threads 1})

    ;; Get agent manager and client
    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name BasicAgentModule))]

      ;; List all available agents in this module
      (println "Available agents:" (aor/agent-names manager))

      ;; Get client for our specific agent
      (let [agent (aor/agent-client manager "BasicAgent")]

        ;; Invoke agent synchronously with sample user names
        (println "\nBasic Agent Results:")
        (println "User: \"Alice\" -> Result:" (aor/agent-invoke agent "Alice"))
        (println "User: \"Bob\" -> Result:" (aor/agent-invoke agent "Bob"))))))
