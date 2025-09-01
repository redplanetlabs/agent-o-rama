(ns com.rpl.agent.async-agent
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

;;; Agent that simulates some processing time
(aor/defagentmodule AsyncAgentModule
  [topology]

  (-> topology
      (aor/new-agent "AsyncAgent")

      ;; Simulate work by processing input with delay
      (aor/node "work" "complete"
                (fn [agent-node {:keys [task-name duration]}]
                  (println (format "Starting task '%s' (will take %d ms)" task-name duration))
                  ;; Simulate work
                  (Thread/sleep duration)
                  (aor/emit! agent-node "complete" {:task task-name
                                                    :duration duration
                                                    :started-at (System/currentTimeMillis)})))

      ;; Complete the task
      (aor/node "complete" nil
                (fn [agent-node {:keys [task duration started-at]}]
                  (let [completed-at (System/currentTimeMillis)
                        actual-duration (- completed-at started-at)]
                    (println (format "Completed task '%s' (actual duration: %d ms)" task actual-duration))
                    (aor/result! agent-node {:task task
                                             :expected-duration duration
                                             :actual-duration actual-duration
                                             :completed-at completed-at}))))))

(defn -main
  "Run async agent example demonstrating concurrent execution"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AsyncAgentModule {:tasks 4 :threads 2})

    (let [manager (aor/agent-manager ipc (rama/get-module-name AsyncAgentModule))
          agent (aor/agent-client manager "AsyncAgent")]

      (println "Async Agent Example - Starting multiple concurrent tasks")

      ;; Start multiple async executions
      (let [task1-invoke (aor/agent-initiate agent {:task-name "Data Processing" :duration 1000})
            task2-invoke (aor/agent-initiate agent {:task-name "Report Generation" :duration 800})
            task3-invoke (aor/agent-initiate agent {:task-name "Email Sending" :duration 500})]

        (println "All tasks initiated, waiting for completion...")

        ;; Get results as they complete
        (println "\n--- Results ---")
        (println "Task 3 result:" (aor/agent-result agent task3-invoke))
        (println "Task 2 result:" (aor/agent-result agent task2-invoke))
        (println "Task 1 result:" (aor/agent-result agent task1-invoke))

        (println "\nAll tasks completed!")))))

(defn run-agent-sequential-comparison
  "Compare async vs sequential execution"
  []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AsyncAgentModule {:tasks 4 :threads 2})

    (let [manager (aor/agent-manager ipc (rama/get-module-name AsyncAgentModule))
          agent (aor/agent-client manager "AsyncAgent")]

      ;; Sequential execution
      (println "=== Sequential Execution ===")
      (let [start-time (System/currentTimeMillis)]
        (doseq [i (range 1 4)]
          (aor/agent-invoke agent {:task-name (str "Sequential Task " i) :duration 300}))
        (let [sequential-time (- (System/currentTimeMillis) start-time)]
          (println (format "Sequential execution took: %d ms" sequential-time))

          ;; Async execution
          (println "\n=== Async Execution ===")
          (let [async-start (System/currentTimeMillis)
                invokes (doall (for [i (range 1 4)]
                                 (aor/agent-initiate agent {:task-name (str "Async Task " i) :duration 300})))]
            ;; Wait for all to complete
            (doseq [invoke invokes]
              (aor/agent-result agent invoke))
            (let [async-time (- (System/currentTimeMillis) async-start)]
              (println (format "Async execution took: %d ms" async-time))
              (println (format "Speedup: %.1fx" (double (/ sequential-time async-time)))))))))))