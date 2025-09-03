(ns com.rpl.agent.basic.async-agent
  "Demonstrates asynchronous agent initiation and result handling.

  Features demonstrated:
  - agent-initiate: Start agent execution asynchronously
  - agent-result: Get result from async execution
  - AgentInvoke handle for tracking execution
  - Concurrent agent execution patterns"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent that simulates some processing time in a single node
(aor/defagentmodule AsyncAgentModule
  [topology]
  (-> (aor/new-agent topology "AsyncAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node task-name]
         (println (format "Starting task '%s'" task-name))
         (Thread/sleep 500) ; Simulate work
         (println (format "Completed task '%s'" task-name))
         (aor/result!
          agent-node
          (str "Task '" task-name "' completed successfully"))))))

(defn -main
  "Run async agent example demonstrating concurrent execution"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AsyncAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name AsyncAgentModule))
          agent   (aor/agent-client manager "AsyncAgent")]

      (println "Async Agent Example - Starting multiple concurrent tasks")

      ;; Start multiple async executions of the agent
      (let [task1-invoke (aor/agent-initiate agent "Data Processing")
            task2-invoke (aor/agent-initiate agent "Report Generation")
            task3-invoke (aor/agent-initiate agent "Email Sending")]

        (println "All tasks initiated, waiting for completion...")

        ;; Get results, waiting for each one to complete complete
        (println "\n--- Results ---")
        (println "Task 3 result:" (aor/agent-result agent task3-invoke))
        (println "Task 2 result:" (aor/agent-result agent task2-invoke))
        (println "Task 1 result:" (aor/agent-result agent task1-invoke))

        (println "\nAll tasks completed!")))))
