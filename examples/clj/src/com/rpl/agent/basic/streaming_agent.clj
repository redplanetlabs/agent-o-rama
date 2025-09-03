(ns com.rpl.agent.basic.streaming-agent
  "Demonstrates streaming chunk emission from agent nodes.

  Features demonstrated:
  - stream-chunk!: Emit streaming data from nodes
  - agent-stream: Subscribe to streaming data from specific nodes
  - Real-time data flow with incremental results
  - Streaming completion and callbacks"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating streaming functionality
(aor/defagentmodule StreamingAgentModule
  [topology]

  (->
   (aor/new-agent topology "StreamingAgent")

    ;; Node that processes data and streams progress
   (aor/node
    "process-data"
    nil
    (fn [agent-node {:keys [data-size chunk-size]}]
      (let [total-chunks (int (Math/ceil (/ data-size chunk-size)))]
        (println
         (format "Processing %d items in chunks of %d" data-size chunk-size))

         ;; Stream progress as we process chunks
        (doseq [chunk-num (range total-chunks)]
          (let [start-idx (* chunk-num chunk-size)
                end-idx (min (+ start-idx chunk-size) data-size)
                items (range start-idx end-idx)
                progress (double (/ (inc chunk-num) total-chunks))]

             ;; Simulate processing time
            (Thread/sleep 100)

             ;; Stream chunk progress
            (aor/stream-chunk! agent-node
                               {:chunk-number chunk-num
                                :items-processed (count items)
                                :progress progress
                                :items items})

            (println (format "Processed chunk %d/%d (%.1f%%)"
                             (inc chunk-num)
                             total-chunks
                             (double (* progress 100))))))

         ;; Return final result
        (aor/result! agent-node
                     {:action "data-processing"
                      :total-items data-size
                      :total-chunks total-chunks
                      :chunk-size chunk-size
                      :completed-at (System/currentTimeMillis)}))))))

(defn -main
  "Run the streaming agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc StreamingAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name
                                      StreamingAgentModule))
          agent (aor/agent-client manager "StreamingAgent")]

      (println "Streaming Agent Example:")
      (println "Processing data with real-time streaming updates...")

      ;; Start async processing
      (let [invoke (aor/agent-initiate agent
                                       {:data-size 50
                                        :chunk-size 10})
            chunks-received (atom [])]

        ;; Subscribe to streaming chunks
        (aor/agent-stream
         agent
         invoke
         "process-data"
         (fn [all-chunks new-chunks reset? complete?]
           (doseq [chunk new-chunks]
             (swap! chunks-received conj chunk)
             (println (format "Received chunk %d: %d items (%.1f%% complete)"
                              (:chunk-number chunk)
                              (:items-processed chunk)
                              (* (:progress chunk) 100))))))

        ;; Wait for completion
        (let [result (aor/agent-result agent invoke)]
          (println "\nFinal result:")
          (println "  Total items processed:" (:total-items result))
          (println "  Total chunks:" (:total-chunks result))
          (println "  Chunk size:" (:chunk-size result))
          (println "  Chunks received via streaming:" (count @chunks-received)))

        (println "\nNotice how:")
        (println "- Streaming provides real-time progress updates")
        (println "- Chunks are received while processing continues")
        (println "- Final result provides summary information")))))

(comment
  (-main))
