(ns com.rpl.agent.basic.aggregation-agent
  "Demonstrates fan-out/fan-in aggregation patterns with agg-start-node and agg-node.

  Features demonstrated:
  - agg-start-node: Start aggregation by emitting to multiple targets
  - agg-node: Collect and combine results from multiple executions
  - Fan-out/fan-in execution patterns
  - Built-in aggregators for common operations"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating aggregation functionality
(aor/defagentmodule AggregationAgentModule
  [topology]

  (->
   (aor/new-agent topology "AggregationAgent")

    ;; Start aggregation by distributing work to parallel processors
   (aor/agg-start-node
    "distribute-work"
    "process-chunk"
    (fn [agent-node {:keys [data chunk-size]}]
      (let [chunks (partition-all chunk-size data)]
         ;; Emit each chunk for parallel processing
        (doseq [chunk chunks]
          (aor/emit! agent-node "process-chunk" chunk)))))

    ;; Process individual chunks in parallel
   (aor/node
    "process-chunk"
    "collect-results"
    (fn [agent-node chunk]
       ;; Transform the chunk data
      (let [processed-chunk (mapv #(* % %) chunk)
            chunk-sum (reduce + processed-chunk)]

        (aor/emit! agent-node
                   "collect-results"
                   {:original-chunk chunk
                    :processed-chunk processed-chunk
                    :chunk-sum chunk-sum}))))

    ;; Aggregate all results using built-in vector aggregator
   (aor/agg-node
    "collect-results"
    nil
    aggs/+vec-agg
    (fn [agent-node aggregated-results _]
      (let [;; Sort chunks by their first element to ensure consistent order
            sorted-results (sort-by #(first (:original-chunk %)) aggregated-results)
            total-sum (reduce + (map :chunk-sum sorted-results))
            total-items (reduce +
                                (map #(count (:original-chunk %))
                                     sorted-results))]
        (aor/result! agent-node
                     {:total-items total-items
                      :total-sum total-sum
                      :chunks-processed (count sorted-results)
                      :chunk-results sorted-results}))))))

(defn -main
  "Run the aggregation agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AggregationAgentModule {:tasks 2 :threads 2})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name
                                      AggregationAgentModule))
          agent (aor/agent-client manager "AggregationAgent")]

      (println "Aggregation Agent Example:")
      (println "Processing data in parallel chunks with result aggregation")

      ;; Process data with different chunk sizes
      (let [test-data (range 1 21)] ; [1 2 3 ... 20]

        (println "\n--- Processing with chunk size 5 ---")
        (let [result1 (aor/agent-invoke agent
                                        {:data test-data
                                         :chunk-size 5})]
          (println "Result 1:")
          (println "  Total items:" (:total-items result1))
          (println "  Total sum:" (:total-sum result1))
          (println "  Chunks processed:" (:chunks-processed result1)))

        (println "\n--- Processing with chunk size 3 ---")
        (let [result2 (aor/agent-invoke agent
                                        {:data test-data
                                         :chunk-size 3})]
          (println "Result 2:")
          (println "  Total items:" (:total-items result2))
          (println "  Total sum:" (:total-sum result2))
          (println "  Chunks processed:" (:chunks-processed result2))))

      (println "\nNotice how:")
      (println "- Work is distributed in parallel to multiple nodes")
      (println "- Results are automatically aggregated back together")
      (println "- Different chunk sizes create different parallelization")
      (println "- Built-in aggregators simplify result collection"))))
