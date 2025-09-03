(ns com.rpl.agent.basic.multi-node-agent
  "Demonstrates agent graphs with multiple nodes and inter-node emissions.

  Features demonstrated:
  - Agent graph with multiple connected nodes
  - emit!: Send data from one node to another
  - Node chaining and data flow
  - Multi-step greeting process through graph traversal"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Multi-node agent demonstrating greeting workflow through graph
(aor/defagentmodule MultiNodeAgentModule
  [topology]

  ;; Thread the agent through multiple node definitions using ->
  ;; Each aor/node call returns a modified agent with the new node added
  (-> (aor/new-agent topology "MultiNodeAgent")

      ;; First node: receive user name from the invoke call and forward it
      (aor/node
       "receive"
       "personalize" ; specifies the node we emit to
       (fn [agent-node user-name]
         (aor/emit! agent-node "personalize" user-name)))

      ;; Second node: personalize the greeting message
      (aor/node
       "personalize"
       "finalize" ; next node in the workflow
       (fn [agent-node user-name]
         (let [greeting (str "Hello, " user-name "!")]
           ;; we emit as many values as the next node expects as input
           (aor/emit! agent-node "finalize" user-name greeting))))

      ;; Final node: create complete welcome message
      (aor/node
       "finalize"
       nil ; indicates this is a terminal node
       (fn [agent-node user-name greeting] ; two values from emit!
         (let [result (str greeting
                           " Welcome to agent-o-rama! "
                           "Thanks for joining us, "
                           user-name
                           ".")]
           (aor/result! agent-node result))))))

(defn -main
  "Run the multi-node agent example with user names"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MultiNodeAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name MultiNodeAgentModule))
          agent   (aor/agent-client manager "MultiNodeAgent")]

      (println "Multi-Node Agent Results:")
      (println "\n--- Greeting Alice ---")
      (let [result1 (aor/agent-invoke agent "Alice")]
        (println "Result:" result1))

      (println "\n--- Greeting Bob ---")
      (let [result2 (aor/agent-invoke agent "Bob")]
        (println "Result:" result2))

      (println "\n--- Greeting Charlie ---")
      (let [result3 (aor/agent-invoke agent "Charlie")]
        (println "Result:" result3)))))
