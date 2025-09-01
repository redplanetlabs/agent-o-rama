(ns com.rpl.agent.multi-agg-agent
  "Demonstrates custom aggregation logic with multi-agg for complex data combination.

  Features demonstrated:
  - multi-agg: Custom aggregation with multiple tagged input streams
  - init clause: Initialize aggregation state
  - on clauses: Handle different types of incoming data
  - Complex aggregation patterns and state management"
  (:require
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
     ["analyze-numbers" "analyze-strings" "analyze-keywords"]
     (fn [agent-node {:keys [numbers strings keywords]}]
       (println "Distributing mixed data types for analysis")

       ;; Send numbers for processing
       (doseq [num numbers]
         (aor/emit! agent-node "analyze-numbers" num))

       ;; Send strings for processing  
       (doseq [str strings]
         (aor/emit! agent-node "analyze-strings" str))

       ;; Send keywords for processing
       (doseq [kw keywords]
         (aor/emit! agent-node "analyze-keywords" kw))))

    ;; Process numbers
    (aor/node
     "analyze-numbers"
     "combine-analysis"
     (fn [agent-node number]
       (let [analysis {:value number
                       :square (* number number)
                       :even? (even? number)}]
         (aor/emit! agent-node "combine-analysis" "number" analysis))))

    ;; Process strings
    (aor/node
     "analyze-strings"
     "combine-analysis"
     (fn [agent-node string]
       (let [analysis {:value string
                       :length (count string)
                       :uppercase (.toUpperCase string)}]
         (aor/emit! agent-node "combine-analysis" "string" analysis))))

    ;; Process keywords
    (aor/node
     "analyze-keywords"
     "combine-analysis"
     (fn [agent-node keyword]
       (let [analysis {:value keyword
                       :name (name keyword)
                       :namespace (namespace keyword)}]
         (aor/emit! agent-node "combine-analysis" "keyword" analysis))))

    ;; Combine all analysis using multi-agg with tagged inputs
    (aor/agg-node
     "combine-analysis"
     nil
     (aor/multi-agg
      (init [] {:numbers [] :strings [] :keywords []})
      (on "number"
          [state analysis]
          (update state :numbers conj analysis))
      (on "string"
          [state analysis]
          (update state :strings conj analysis))
      (on "keyword"
          [state analysis]
          (update state :keywords conj analysis)))
     (fn [agent-node aggregated-state _]
       (let [numbers-count (count (:numbers aggregated-state))
             strings-count (count (:strings aggregated-state))
             keywords-count (count (:keywords aggregated-state))
             
             number-sum (reduce + (map :square (:numbers aggregated-state)))
             avg-string-length (if (seq (:strings aggregated-state))
                                 (/ (reduce + (map :length (:strings aggregated-state)))
                                    strings-count)
                                 0)
             namespaced-keywords (filter :namespace (:keywords aggregated-state))]

         (println (format "Combined analysis: %d numbers, %d strings, %d keywords"
                          numbers-count strings-count keywords-count))

         (aor/result! agent-node
                      {:action "multi-analysis-complete"
                       :summary {:numbers-analyzed numbers-count
                                 :strings-analyzed strings-count
                                 :keywords-analyzed keywords-count
                                 :total-square-sum number-sum
                                 :avg-string-length avg-string-length
                                 :namespaced-keywords-count (count namespaced-keywords)}
                       :detailed-results aggregated-state
                       :processed-at (System/currentTimeMillis)}))))))

(defn -main
  "Run the multi-agg agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MultiAggAgentModule {:tasks 2 :threads 2})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name MultiAggAgentModule))
          agent   (aor/agent-client manager "MultiAggAgent")]

      (println "Multi-Agg Agent Example:")
      (println "Processing mixed data types with custom aggregation logic")

      (let [result (aor/agent-invoke agent
                                     {:numbers [1 2 3 4 5]
                                      :strings ["hello" "world" "test"]
                                      :keywords [:foo :bar :baz/qux ::local]})]

        (println "\nResults:")
        (println "  Action:" (:action result))
        
        (let [summary (:summary result)]
          (println "  Summary:")
          (println "    Numbers analyzed:" (:numbers-analyzed summary))
          (println "    Strings analyzed:" (:strings-analyzed summary))
          (println "    Keywords analyzed:" (:keywords-analyzed summary))
          (println "    Total square sum:" (:total-square-sum summary))
          (println "    Average string length:" (:avg-string-length summary))
          (println "    Namespaced keywords:" (:namespaced-keywords-count summary)))

        (let [details (:detailed-results result)]
          (println "  Sample detailed results:")
          (println "    First number analysis:" (first (:numbers details)))
          (println "    First string analysis:" (first (:strings details)))
          (println "    First keyword analysis:" (first (:keywords details)))))

      (println "\nNotice how:")
      (println "- Multi-agg handles different types of tagged inputs")
      (println "- Each 'on' clause processes specific data types")
      (println "- Complex state accumulation across multiple input streams")
      (println "- Custom aggregation logic beyond simple built-in aggregators"))))