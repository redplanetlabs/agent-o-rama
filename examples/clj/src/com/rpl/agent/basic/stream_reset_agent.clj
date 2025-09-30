(ns com.rpl.agent.basic.stream-reset-agent
  "Demonstrates stream reset behavior when a node is retried after failure.

  Features demonstrated:
  - stream-chunk!: Emit streaming data from nodes
  - Exception handling: Node throws exception on first execution
  - Automatic retry: Agent-o-rama automatically retries failed nodes
  - Stream reset: Streaming subscriptions are notified when a node retries
  - agent-stream-reset-info: Query the number of resets that occurred"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Atom to track whether this is the first execution attempt
(defonce first-execution? (atom true))

;;; Agent module demonstrating stream reset on retry
(aor/defagentmodule StreamResetAgentModule
  [topology]

  (->
    (aor/new-agent topology "StreamResetAgent")

    ;; Node that fails on first execution, succeeds on retry
    (aor/node
     "process-with-retry"
     nil
     (fn [agent-node {:keys [data-size]}]
       (println "Starting data processing...")

       (if @first-execution?
         ;; First execution: emit some chunks, then fail
         (do
           (println "First execution: emitting partial data before failure")
           (aor/stream-chunk! agent-node {:chunk-number 0 :data "chunk-1"})
           (Thread/sleep 50)
           (aor/stream-chunk! agent-node {:chunk-number 1 :data "chunk-2"})
           (Thread/sleep 50)

           (reset! first-execution? false)
           (println "Simulating failure...")
           (throw (RuntimeException. "Simulated processing failure")))

         ;; Retry execution: emit all chunks successfully
         (do
           (println "Retry execution: processing all data successfully")
           (doseq [i (range data-size)]
             (aor/stream-chunk! agent-node
                                {:chunk-number i
                                 :data         (str "chunk-" (inc i))})
             (Thread/sleep 50))

           (println "Processing completed successfully after retry")
           (aor/result! agent-node
                        {:status       "completed"
                         :total-chunks data-size
                         :message      "Processing completed after retry"})))))))

(defn -main
  "Run the stream reset agent example"
  [& _args]
  ;; Reset the execution flag for this run
  (reset! first-execution? true)

  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc StreamResetAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name
                                      StreamResetAgentModule))
          agent   (aor/agent-client manager "StreamResetAgent")]

      (println "Stream Reset Agent Example:")
      (println "This example demonstrates what happens when a streaming node fails and retries.\n")

      ;; Track streaming updates
      (let [invoke          (aor/agent-initiate agent {:data-size 5})
            chunks-received (atom [])
            reset-seen?     (atom false)]

        ;; Subscribe to streaming chunks
        (let [stream (aor/agent-stream
                      agent
                      invoke
                      "process-with-retry"
                      (fn [all-chunks new-chunks reset? complete?]
                        (when reset?
                          (reset! reset-seen? true)
                          (println "\n*** STREAM RESET DETECTED ***")
                          (println "The node was retried and the stream was reset."))

                        (doseq [chunk new-chunks]
                          (swap! chunks-received conj chunk)
                          (println (format "Received: %s (reset=%s, complete=%s)"
                                           (:data chunk)
                                           reset?
                                           complete?)))

                        (when complete?
                          (println "\nStreaming completed."))))]

          ;; Wait for completion
          (let [result      (aor/agent-result agent invoke)
                reset-count (aor/agent-stream-reset-info stream)]

            (println "\n=== Final Results ===")
            (println "Status:" (:status result))
            (println "Message:" (:message result))
            (println "Total chunks received:" (count @chunks-received))
            (println "Stream was reset:" @reset-seen?)
            (println "Reset count:" reset-count)

            (println "\nNotice how:")
            (println "- The first execution emitted 2 chunks before failing")
            (println "- The stream was automatically reset when the node retried")
            (println "- The retry execution emitted all 5 chunks successfully")
            (println "- The callback received reset?=true to indicate the reset")
            (println "- agent-stream-reset-info shows the reset count is" reset-count)))))))

(comment
  (-main))
