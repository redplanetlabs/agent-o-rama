(ns com.rpl.agent.basic.rama-module-agent
  "Demonstrates using Rama module directly instead of defagentmodule.

  Features demonstrated:
  - rama/module: Define a Rama module directly
  - agents-topology: Create agent topology manually
  - underlying-stream-topology: Access the underlying StreamTopology
  - declare-depot: Declare a Rama depot alongside agents
  - <<sources: Process depot streams with Rama's stream processing
  - new-agent: Create an agent within the topology
  - node: Define agent node
  - define-agents!: Explicitly finalize agent definitions
  
  This example shows how to integrate agent-o-rama with full Rama features
  when you need access to depots, stream processing, or other Rama primitives."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Rama module with agents and depot
(def RamaModuleAgent
  (rama/module
   {:module-name "RamaModuleAgent"}
   [setup topologies]
   ;; Declare a depot to demonstrate Rama feature integration
   (rama/declare-depot setup *example-depot (rama/hash-by identity))

   ;; Create agents topology
   (let [topology (aor/agents-topology setup topologies)
         ;; Access underlying stream topology for Rama features
         s-topology (aor/underlying-stream-topology topology)]

     ;; Use Rama's stream processing alongside agents
     (rama/<<sources
      s-topology
      (rama/source> *example-depot :> *value)
      (println "Depot received:" *value))

     ;; Define a simple feedback agent
     (-> topology
         (aor/new-agent "FeedbackAgent")
         (aor/node
          "process-feedback"
          nil
          (fn [agent-node feedback-text]
            ;; Process feedback and return success response
            (let [response {:status :success
                            :message (str "Processed: " feedback-text)
                            :length (count feedback-text)}]
              (aor/result! agent-node response)))))

     ;; Finalize agent definitions
     (aor/define-agents! topology))))

(defn -main
  "Run the rama module agent example"
  [& _args]
  ;; Create in-process cluster
  (with-open [ipc (rtest/create-ipc)]
    ;; Launch the Rama module
    (rtest/launch-module! ipc RamaModuleAgent {:tasks 1 :threads 1})

    (let [module-name (rama/get-module-name RamaModuleAgent)]
      ;; Get the depot for demonstration
      (let [depot (rama/foreign-depot ipc module-name "*example-depot")]
        ;; Append to depot to show it's working
        (println "\n=== Depot Integration Demo ===")
        (rama/foreign-append! depot "Example data 1")
        (rama/foreign-append! depot "Example data 2")
        (Thread/sleep 100)) ; Allow time for depot processing

      ;; Get agent manager and client
      (let [manager (aor/agent-manager ipc module-name)
            agent (aor/agent-client manager "FeedbackAgent")]

        (println "\n=== Agent Results ===")
        (println "Available agents:" (aor/agent-names manager))

        ;; Invoke agent with sample feedback
        (let [result1 (aor/agent-invoke agent "Great product!")
              result2 (aor/agent-invoke agent "Needs improvement")]
          (println "Feedback 1:" result1)
          (println "Feedback 2:" result2))))))