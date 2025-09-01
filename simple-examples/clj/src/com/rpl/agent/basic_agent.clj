(ns com.rpl.agent.basic-agent
  "Demonstrates basic agent definition with a single node and synchronous invocation.
  
  Features demonstrated:
  - defagentmodule: Define an agent module
  - agents-topology: Create agent topology
  - new-agent: Create a new agent
  - node: Define a single agent node
  - result!: Return final result from a node
  - agent-manager: Create client manager
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
      (aor/node "process" nil
                (fn [agent-node input]
                  ;; Simple processing: uppercase the input string
                  (let [result (if (string? input)
                                 (.toUpperCase input)
                                 (str "PROCESSED: " input))]
                    ;; Return the final result
                    (aor/result! agent-node result))))))

(defn -main
  "Run the basic agent example with sample input"
  [& _args]
  ;; Create in-process cluster and launch the module
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc BasicAgentModule {:tasks 2 :threads 1})

    ;; Get agent manager and client
    (let [manager (aor/agent-manager ipc (rama/get-module-name BasicAgentModule))
          agent (aor/agent-client manager "BasicAgent")]

      ;; Invoke agent synchronously with sample inputs
      (println "Basic Agent Results:")
      (println "Input: \"hello world\" -> Result:" (aor/agent-invoke agent "hello world"))
      (println "Input: 42 -> Result:" (aor/agent-invoke agent 42))
      (println "Input: [:a :b :c] -> Result:" (aor/agent-invoke agent [:a :b :c])))))