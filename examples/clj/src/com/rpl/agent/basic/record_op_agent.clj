(ns com.rpl.agent.basic.record-op-agent
  "Demonstrates recording custom operations in agent traces for debugging.

  Features demonstrated:
  - record-nested-op!: Add custom operation info to agent trace
  - start-ui: Launch web UI for viewing traces
  - Agent trace visualization in UI"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule RecordOpAgentModule
  [topology]

  (-> topology
      (aor/new-agent "RecordOpAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node user-name]
         ;; Record timing of a custom operation
         (let [start-time  (System/currentTimeMillis)
               ;; Simulate some work
               greeting    (str "Hello, " user-name "!")
               _ (Thread/sleep 100)
               finish-time (System/currentTimeMillis)]

           ;; Record the operation in the agent trace
           (aor/record-nested-op!
            agent-node
            :other
            start-time
            finish-time
            {"operation" "generate-greeting"
             "input"     user-name
             "output"    greeting})

           (aor/result! agent-node greeting))))))

(defn -main
  "Run the record-op agent example and view traces in UI"
  [& _args]
  (with-open [ipc (rtest/create-ipc)
              _ui (aor/start-ui
                   ipc
                   ;; set this to false or omit to have the example watt for
                   ;; input before closing.
                   {:no-input-before-close true})]
    (rtest/launch-module! ipc RecordOpAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name RecordOpAgentModule))
          agent   (aor/agent-client manager "RecordOpAgent")]

      (println "RecordOp Agent Example")
      (println "======================\n")

      (println "Result:" (aor/agent-invoke agent "Alice"))
      (println "Result:" (aor/agent-invoke agent "Bob"))

      (println "\n✓ Agent invocations complete!")
      (println "\nTo view the recorded operations in the trace:")
      (println "  1. Open the UI at http://localhost:8080")
      (println "  2. Click on an agent invocation")
      (println "  3. Look for the 'generate-greeting' operation in the trace details")
      (println "\nPress Enter to exit and shut down the UI...")
      (read-line))))
