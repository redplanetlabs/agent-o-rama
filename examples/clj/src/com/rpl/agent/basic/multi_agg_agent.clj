(ns com.rpl.agent.basic.multi-agg-agent
  "Demonstrates custom aggregation logic with multi-agg for complex data combination.

  Features demonstrated:
  - multi-agg: Custom aggregation with multiple tagged input streams
  - init clause: Initialize aggregation state
  - on clauses: Handle different types of incoming data
  - Complex aggregation patterns and state management"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating multi-agg functionality
(aor/defagentmodule MultiAggAgentModule
  [topology]

  (->
   topology
   (aor/new-agent "MultiAggAgent")

    ;; Start by distributing different types of data
   (aor/agg-start-node
    "distribute-data"
    ["process-numbers" "process-text"]
    (fn [agent-node {:keys [numbers text]}]
      (println "Distributing data for parallel processing")

       ;; Send numbers for mathematical analysis
      (doseq [num numbers]
        (aor/emit! agent-node "process-numbers" num))

       ;; Send text for linguistic analysis
      (doseq [txt text]
        (aor/emit! agent-node "process-text" txt))))

    ;; Process numbers - compute statistics
   (aor/node
    "process-numbers"
    "combine-results"
    (fn [agent-node number]
      (let [analysis {:value number
                      :square (* number number)
                      :even? (even? number)}]
        (aor/emit! agent-node "combine-results" "number" analysis))))

    ;; Process text - analyze content
   (aor/node
    "process-text"
    "combine-results"
    (fn [agent-node text]
      (let [analysis {:value text
                      :length (count text)
                      :uppercase (str/upper-case text)
                      :words (count (str/split text #"\s+"))}]
        (aor/emit! agent-node "combine-results" "text" analysis))))

    ;; Combine all analysis using multi-agg with tagged inputs
   (aor/agg-node
    "combine-results"
    nil
    (aor/multi-agg
     (init [] {:numbers [] :text []})
     (on "number"
         [state analysis]
         (update state :numbers conj analysis))
     (on "text"
         [state analysis]
         (update state :text conj analysis)))
    (fn [agent-node aggregated-state _]
      (let [numbers (:numbers aggregated-state)
            text-entries (:text aggregated-state)

             ;; Calculate statistics from numbers
            number-sum (reduce + (map :value numbers))
            square-sum (reduce + (map :square numbers))
            even-count (count (filter :even? numbers))

             ;; Calculate statistics from text
            total-words (reduce + (map :words text-entries))
            total-chars (reduce + (map :length text-entries))]

        (println
         (format "Processed %d numbers and %d text entries"
                 (count numbers)
                 (count text-entries)))

        (aor/result! agent-node
                     {:summary {:numbers-processed (count numbers)
                                :text-processed (count text-entries)
                                :number-sum number-sum
                                :square-sum square-sum
                                :even-count even-count
                                :total-words total-words
                                :total-characters total-chars}
                      :details aggregated-state}))))))

(defn -main
  "Run the multi-agg agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MultiAggAgentModule {:tasks 2 :threads 2})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name MultiAggAgentModule))
          agent (aor/agent-client manager "MultiAggAgent")]

      (println "Multi-Agg Agent Example:")
      (println "Processing mixed data types with custom aggregation logic")

      (let [result (aor/agent-invoke agent
                                     {:numbers [1 2 3 4 5 6 7 8 9 10]
                                      :text ["Hello world"
                                             "Multi-agg is powerful"
                                             "Parallel processing rocks"]})]

        (println "\nResults:")

        (let [summary (:summary result)]
          (println "  Summary:")
          (println "    Numbers processed:" (:numbers-processed summary))
          (println "    Text entries processed:" (:text-processed summary))
          (println "    Sum of numbers:" (:number-sum summary))
          (println "    Sum of squares:" (:square-sum summary))
          (println "    Even numbers:" (:even-count summary))
          (println "    Total words:" (:total-words summary))
          (println "    Total characters:" (:total-characters summary)))

        (println "\n  Sample detailed results:")
        (println "    First number analysis:"
                 (first (get-in result [:details :numbers])))
        (println "    First text analysis:"
                 (first (get-in result [:details :text]))))

      (println "\nNotice how:")
      (println "- Multi-agg handles different types of tagged inputs")
      (println "- Each 'on' clause processes specific data types")
      (println "- State accumulation works across multiple input streams")
      (println "- Parallel processing with custom aggregation logic"))))
