(ns com.rpl.agent.basic.stream-all-agent
  "Demonstrates subscribing to streaming chunks across multiple agent invocations.

  Features demonstrated:
  - stream-chunk!: Emit streaming data from nodes
  - agent-stream-all: Subscribe to streaming data from all invocations of a node
  - Multiple agent invocations with single subscription
  - Tracking chunks by invoke ID
  - Invoke ID mapping in streaming callbacks"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating stream-all functionality
(aor/defagentmodule StreamAllAgentModule
  [topology]

  (->
   (aor/new-agent topology "StreamAllAgent")

    ;; Node that processes a task and streams progress
   (aor/node
    "process-task"
    nil
    (fn [agent-node {:keys [task-id items-to-process]}]
      (println (format "\nProcessing task %s with %d items" task-id items-to-process))

       ;; Stream progress as we process items
      (doseq [item-num (range items-to-process)]
        (Thread/sleep 50)

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
  "Run the stream-all agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc StreamAllAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name
                                      StreamAllAgentModule))
          agent   (aor/agent-client manager "StreamAllAgent")]

      (println "Stream-All Agent Example:")
      (println "Demonstrating streaming across multiple agent invocations...")

       ;; Track chunks received by invoke ID
      (let [chunks-by-invoke (atom {})
            ;; Start two invocations
            invoke1 (aor/agent-initiate agent
                                        {:task-id          "task-1"
                                         :items-to-process 3})
            invoke2 (aor/agent-initiate agent
                                        {:task-id          "task-2"
                                         :items-to-process 4})]

        (println "\nStarted 2 agent invocations")
        (println "Subscribing to streaming from all invocations...")

         ;; Subscribe to streaming chunks from ALL invocations
        (aor/agent-stream-all
         agent
         invoke1
         "process-task"
         (fn [_invoke-id->all-chunks invoke-id->new-chunks _reset-invoke-ids _complete?]
           (doseq [[invoke-id new-chunks] invoke-id->new-chunks]
             (swap! chunks-by-invoke update invoke-id
                    (fn [existing]
                      (concat (or existing []) new-chunks)))
             (doseq [chunk new-chunks]
               (println (format "Received streaming chunk: Task=%s Item=%d [invoke-id=%s]"
                                (:task-id chunk)
                                (:item-number chunk)
                                invoke-id))))))

         ;; Wait for all invocations to complete
        (println "\nWaiting for all invocations to complete...")
        (let [result1 (aor/agent-result agent invoke1)
              result2 (aor/agent-result agent invoke2)]

          (println "\nFinal results:")
          (println (format "  %s: %d items processed"
                           (:task-id result1)
                           (:total-items result1)))
          (println (format "  %s: %d items processed"
                           (:task-id result2)
                           (:total-items result2)))

          (println "\nStreaming summary:")
          (println (format "  Total invocations tracked: %d"
                           (count @chunks-by-invoke)))
          (doseq [[invoke-id chunks] @chunks-by-invoke]
            (println (format "  Invoke %s: received %d streaming chunks"
                             invoke-id
                             (count chunks))))

          (println "\nNotice how:")
          (println "- agent-stream-all subscribes to ALL invocations of an agent")
          (println "- Both invocations were started before subscribing")
          (println "- Chunks are grouped and delivered by invoke ID")
          (println "- A single callback handles all invocations")
          (println "- Each invocation is tracked independently"))))))

(comment
  (-main))