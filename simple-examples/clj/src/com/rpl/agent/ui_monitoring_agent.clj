(ns com.rpl.agent.ui-monitoring-agent
  "Demonstrates UI integration for monitoring and visualizing agent execution.

  Features demonstrated:
  - start-ui: Launch the web-based monitoring interface
  - Real-time visualization of agent execution flows
  - State inspection and debugging capabilities
  - Agent execution tracing and monitoring"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module for UI monitoring demonstration
(aor/defagentmodule UIMonitoringAgentModule
  [topology]

  ;; Declare a simple counter store for state visualization
  (aor/declare-key-value-store topology "counters" String Long)

  (->
    topology
    (aor/new-agent "MonitoredAgent")

    ;; Multi-step process that's interesting to monitor
    (aor/node
     "initialize"
     "process-data"
     (fn [agent-node {:keys [items batch-size]}]
       (let [counters (aor/get-store agent-node "counters")]
         (println "Initializing processing...")
         
         ;; Initialize counters
         (store/put! counters "total-items" (long (count items)))
         (store/put! counters "processed-items" 0)
         (store/put! counters "current-batch" 0)
         
         ;; Stream progress updates for UI visualization
         (aor/stream-chunk! agent-node
                            {:stage "initialization"
                             :total-items (count items)
                             :batch-size batch-size
                             :timestamp (System/currentTimeMillis)})

         (aor/emit! agent-node "process-data"
                    {:items items
                     :batch-size batch-size
                     :current-batch 0}))))

    ;; Process data in batches with state updates
    (aor/node
     "process-data"
     "process-data" ; Self-loop for batch processing
     (fn [agent-node {:keys [items batch-size current-batch]}]
       (let [counters (aor/get-store agent-node "counters")
             start-idx (* current-batch batch-size)
             end-idx (min (+ start-idx batch-size) (count items))
             batch (subvec (vec items) start-idx end-idx)
             processed-count (+ (store/get counters "processed-items") (count batch))]

         (when (seq batch)
           (println (format "Processing batch %d: %d items" (inc current-batch) (count batch)))
           
           ;; Simulate processing work
           (Thread/sleep 200)
           
           ;; Update state
           (store/put! counters "processed-items" processed-count)
           (store/put! counters "current-batch" (inc current-batch))
           
           ;; Stream progress for UI
           (aor/stream-chunk! agent-node
                              {:stage "processing"
                               :batch-number (inc current-batch)
                               :items-in-batch (count batch)
                               :total-processed processed-count
                               :batch-data batch
                               :timestamp (System/currentTimeMillis)})

           ;; Continue processing if more items remain
           (if (< end-idx (count items))
             (aor/emit! agent-node "process-data"
                        {:items items
                         :batch-size batch-size
                         :current-batch (inc current-batch)})
             (aor/emit! agent-node "finalize"
                        {:total-processed processed-count
                         :total-batches (inc current-batch)}))))))

    ;; Finalize processing
    (aor/node
     "finalize"
     nil
     (fn [agent-node {:keys [total-processed total-batches]}]
       (let [counters (aor/get-store agent-node "counters")]
         (println "Finalizing processing...")
         
         ;; Final state update
         (store/put! counters "status" "completed")
         
         ;; Stream completion status
         (aor/stream-chunk! agent-node
                            {:stage "completion"
                             :total-processed total-processed
                             :total-batches total-batches
                             :status "completed"
                             :timestamp (System/currentTimeMillis)})

         (aor/result! agent-node
                      {:action "batch-processing-complete"
                       :total-processed total-processed
                       :total-batches total-batches
                       :final-status "completed"
                       :processed-at (System/currentTimeMillis)}))))))

(defn -main
  "Run the UI monitoring agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc UIMonitoringAgentModule {:tasks 2 :threads 2})

    ;; Start the UI for monitoring
    (with-open [ui (aor/start-ui ipc {:port 1974 :no-input-before-close true})]
      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name UIMonitoringAgentModule))
            agent   (aor/agent-client manager "MonitoredAgent")]

        (println "UI Monitoring Agent Example:")
        (println "Web UI available at: http://localhost:1974")
        (println "Processing data with real-time monitoring visualization...\n")

        ;; Create some test data
        (let [test-data (mapv #(str "Item-" %) (range 1 21)) ; 20 items
              invoke (aor/agent-initiate agent
                                         {:items test-data
                                          :batch-size 5})]

          ;; Set up streaming to monitor progress
          (let [progress-updates (atom [])]
            (aor/agent-stream agent
                              invoke
                              "initialize"
                              (fn [all-chunks new-chunks reset? complete?]
                                (doseq [chunk new-chunks]
                                  (swap! progress-updates conj chunk)
                                  (println "Progress update:" 
                                           (select-keys chunk [:stage :batch-number :total-processed])))))

            ;; Wait for completion
            (let [result (aor/agent-result agent invoke)]
              (println "\nProcessing completed!")
              (println "  Final result:" (select-keys result [:action :total-processed :total-batches]))
              (println "  Progress updates received:" (count @progress-updates))
              
              ;; Show final progress summary
              (println "\nProgress Summary:")
              (doseq [update @progress-updates]
                (println (format "  %s: %s" 
                                (:stage update) 
                                (case (:stage update)
                                  "initialization" (format "%d items, batch size %d" 
                                                          (:total-items update) (:batch-size update))
                                  "processing" (format "batch %d, %d total processed" 
                                                      (:batch-number update) (:total-processed update))
                                  "completion" (format "%d items in %d batches, status: %s"
                                                      (:total-processed update) (:total-batches update) (:status update))))))
              
              (println "\nTo view detailed execution traces and state:")
              (println "1. Open http://localhost:1974 in your browser")
              (println "2. Navigate to the agent execution view")
              (println "3. Explore the real-time monitoring features")
              (println "\nPress Enter to close the UI and exit...")
              (read-line))))

        (println "\nNotice how:")
        (println "- The UI provides real-time visualization of agent execution")
        (println "- State changes are visible through the monitoring interface")
        (println "- Streaming data shows up as live updates in the UI")
        (println "- Complex agent graphs can be debugged and traced visually"))))