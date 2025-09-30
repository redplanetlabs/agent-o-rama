(ns com.rpl.agent.basic.stream-specific-agent
  "Demonstrates subscribing to streaming chunks from a specific agent invocation.

  Features demonstrated:
  - agent-initiate: Start agent execution without immediately consuming result
  - agent-stream-specific: Subscribe to streaming from a specific node invocation
  - agent-next-step: Manually progress agent execution step-by-step
  - Targeting streaming subscription to one invocation among multiple"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating stream-specific functionality
(aor/defagentmodule StreamSpecificAgentModule
  [topology]

  (->
    (aor/new-agent topology "StreamSpecificAgent")

    ;; Node that processes a task and streams progress
    (aor/node
     "process-task"
     nil
     (fn [agent-node {:keys [task-id items-to-process]}]
       (println (format "\nProcessing task %s with %d items" task-id items-to-process))
       (Thread/sleep 1000)

       ;; Stream progress as we process items
       (doseq [item-num (range items-to-process)]

         ;; Stream progress update
         (aor/stream-chunk! agent-node
                            {:task-id     task-id
                             :item-number item-num
                             :status      "processing"})

         (println (format "Task %s: Processed item %d/%d"
                          task-id
                          (inc item-num)
                          items-to-process)))

       ;; Return final result
       (aor/result! agent-node
                    {:task-id      task-id
                     :status       "completed"
                     :total-items  items-to-process
                     :completed-at (System/currentTimeMillis)})))))

(defn -main
  "Run the stream-specific agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc StreamSpecificAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name
                                      StreamSpecificAgentModule))
          agent   (aor/agent-client manager "StreamSpecificAgent")]

      (println "Stream-Specific Agent Example:")
      (println "Demonstrating streaming from a specific invocation...\n")

      ;; Track chunks received for the specific invocation we're monitoring
      (let [chunks-received  (atom [])
            ;; Start three invocations
            invoke1          (aor/agent-initiate agent
                                                 {:task-id "task-1"
                                                  :items-to-process 3})
            invoke2          (aor/agent-initiate agent
                                                 {:task-id "task-2"
                                                  :items-to-process 4})
            invoke3          (aor/agent-initiate agent
                                                 {:task-id "task-3"
                                                  :items-to-process 2})
            ;; Get the invoke-id for the specific invocation we want to monitor
            target-invoke-id (:agent-invoke-id invoke2)]

        (println "Started 3 agent invocations")
        (println (format "Subscribing to streaming from invoke-id: %s (task-2 only)..."
                         target-invoke-id))

        ;; Subscribe to streaming chunks from invoke2's node execution.
        ;; Using agent-stream-specific with the agent-invoke-id as node-invoke-id
        ;; to target this specific agent invocation's first node execution.
        (aor/agent-stream-specific
         agent
         invoke2
         "process-task"
         target-invoke-id
         (fn [_all-chunks new-chunks _reset? _complete?]
           (doseq [chunk new-chunks]
             (swap! chunks-received conj chunk)
             (println (format "Received streaming chunk: Task=%s Item=%d"
                              (:task-id chunk)
                              (:item-number chunk))))))

        ;; Wait for all invocations to complete
        (println "\nWaiting for all invocations to complete...")
        (let [result1 (aor/agent-result agent invoke1)
              result2 (aor/agent-result agent invoke2)
              result3 (aor/agent-result agent invoke3)]

          (println "\nFinal results:")
          (println (format "  %s: %d items processed"
                           (:task-id result1)
                           (:total-items result1)))
          (println (format "  %s: %d items processed"
                           (:task-id result2)
                           (:total-items result2)))
          (println (format "  %s: %d items processed"
                           (:task-id result3)
                           (:total-items result3)))

          (println "\nStreaming summary:")
          (println (format "  Chunks received from task-2 only: %d"
                           (count @chunks-received)))

          (println "\nNotice how:")
          (println "- agent-stream-specific subscribes to a specific node invocation")
          (println "- We passed the agent-invoke-id as the node-invoke-id parameter")
          (println "- This targets the first execution of the node within that agent invocation")
          (println "- Only chunks from task-2 were received, not task-1 or task-3")
          (println
           "- Multiple agent invocations can stream concurrently with selective monitoring"))))))

(comment
  (-main))
