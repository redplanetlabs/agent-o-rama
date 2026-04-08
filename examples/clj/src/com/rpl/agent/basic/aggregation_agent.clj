(ns com.rpl.agent.basic.aggregation-agent
  "Demonstrates fan-out/fan-in aggregation patterns with agg-start-node and agg-node.

  Features demonstrated:
  - agg-start-node: Start aggregation by emitting to multiple targets
  - agg-node: Collect and combine results from multiple executions
  - Fan-out/fan-in execution patterns
  - Built-in aggregators for common operations"
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [dev.langchain4j.model.openai
    OpenAiChatModel])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as rpath]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

;;; Agent module demonstrating aggregation functionality
(aor/defagentmodule AggregationAgentModule
  [topology]

  (aor/declare-agent-object-builder
   topology
   "gpt-5-4"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (System/getenv "OPENAI_API_KEY"))
         (.modelName "gpt-5.4")
         (.temperature 0.0)
         .build))
   {:thread-safe? true})

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

(defn map->StartExperiment
  [{:keys [id name dataset-id snapshot selector evaluators spec num-repetitions concurrency]}]
  (aor-types/->StartExperiment
   (java.util.UUID/fromString id)
   name
   (java.util.UUID/fromString dataset-id)
   snapshot
   selector
   evaluators
   spec
   num-repetitions
   concurrency))

(defn map->constructed
  [constructor]
  (fn [m] (apply constructor
                (vec (for [k (first (:arglists (meta constructor)))]
                       ((keyword k) m))))))

((map->constructed #'aor-types/->AgentInvokeImpl)
 {:task-id 3 :agent-invoke-id (java.util.UUID/randomUUID)}
 )

(def uuid-regex #"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

(defn uuidify [data]
  (clojure.walk/postwalk
   (fn [x]
     (if (and (string? x) (re-matches uuid-regex x))
       (java.util.UUID/fromString x)
       x))
   data))

(def kwint (fn [kw] (Long/parseLong (name kw))))


(comment
  (def ipc (rtest/create-ipc))
  (rtest/launch-module! ipc AggregationAgentModule {:tasks 2 :threads 2})
  (aor/start-ui ipc)
  (def pstate-write-depot (rama/foreign-depot ipc
                                              (rama/get-module-name AggregationAgentModule)
                                              (po/agent-pstate-write-depot-name)))

  (def payload (clojure.data.json/read-json (slurp "/Users/tommy/programming/agent/data/experiments-split/03-Copy_of_first-019d30a9-bf7e-7926-b985-1c6941097055.json")))
  (def dataset-id (java.util.UUID/fromString (:dataset-id payload)))

  (keys (rpath/select-one [:experiments rpath/FIRST] payload))
  (keys (rpath/select-one [:experiments rpath/FIRST :results :0] payload))
  (keys (rpath/select-one [:experiments rpath/FIRST :experiment-info] payload))
  (sort (map (comp #(Integer/parseInt %) name) (keys (rpath/select-one [:experiments rpath/FIRST :results] payload))))
  (def dataset-items (rpath/select [:experiments rpath/FIRST :results rpath/MAP-VALS
                                    (rpath/submap [:input :reference-output :example-id])] payload))
  
  (rpath/select [:experiments rpath/FIRST :results rpath/MAP-VALS
                 (rpath/submap [:input :reference-output :example-id])] payload)
  
  (def experiment (rpath/select-one [:experiments rpath/FIRST] payload))
  (def experimentsp
    
    (->> experiment
         uuidify
         (rpath/transform [:experiment-info :spec] (map->constructed #'aor-types/->RegularExperiment))
         (rpath/transform [:experiment-info] (map->constructed #'aor-types/->StartExperiment))
         (rpath/transform [:experiment-invoke] (map->constructed #'aor-types/->AgentInvokeImpl))
         (rpath/transform [:results rpath/MAP-VALS :agent-initiates rpath/MAP-VALS :agent-invoke]
                          (map->constructed #'aor-types/->AgentInvokeImpl))
         (rpath/transform [:results rpath/MAP-VALS :agent-results rpath/MAP-VALS :result]
                          (map->constructed #'aor-types/->AgentResult))
         (rpath/transform [:results rpath/MAP-VALS :eval-initiates rpath/MAP-VALS]
                          (map->constructed #'aor-types/->AgentInvokeImpl))
         
         ;; fixup keys
         (rpath/transform [:results rpath/MAP-KEYS] kwint)
         (rpath/transform [:results rpath/MAP-VALS :agent-initiates rpath/MAP-KEYS] kwint)
         (rpath/transform [:results rpath/MAP-VALS :agent-results rpath/MAP-KEYS] kwint)
         (rpath/transform [:results rpath/MAP-VALS :eval-initiates rpath/MAP-KEYS] name)
         (rpath/transform [:results rpath/MAP-VALS :evals rpath/MAP-KEYS] name)
         (rpath/transform [:results rpath/MAP-VALS :evals rpath/MAP-VALS rpath/MAP-KEYS] name)
         (rpath/transform [:results rpath/MAP-VALS :eval-failures rpath/MAP-KEYS] name)

         (rpath/transform [:results rpath/MAP-VALS] (fn [m] (select-keys
                                                             m
                                                             [:example-id
                                                              :agent-initiates
                                                              :agent-results
                                                              :eval-initiates
                                                              :evals
                                                              :eval-failures])))
         (rpath/select-one [(rpath/submap [:experiment-info
                                           :experiment-invoke
                                           :start-time-millis
                                           :finish-time-millis
                                           :results])])))
  (def snapshots (into {}
                       (map (fn [{:keys [example-id] :as m}]
                              [(java.util.UUID/fromString example-id)
                               (-> m
                                   (dissoc :example-id)
                                   (assoc :tags #{}))])
                            dataset-items)))
  (def v {:props {:name (:dataset-name payload)
                  :created-at (h/current-time-millis)
                  :modified-at (h/current-time-millis)}
          :snapshots {nil snapshots}
          :experiments
          {(get-in experimentsp [:experiment-info :id]) experimentsp}})
  (count (pr-str v))
  
  (def app (fn [v] (rama/foreign-append!
                   pstate-write-depot
                   (aor-types/->PStateWrite
                    nil
                    "$$_aor-datasets"
                    (rpath/path
                     (rpath/keypath dataset-id)
                     (rpath/termval v))
                    dataset-id))))
  (app v)

  ;; -- Evaluator setup (run after launching module with new AggregationAgentModule) --

  (def eval-name "gpt-5-4-judge")
  (def eval-prompt
    (str "You are evaluating <output></output> from an agent, which is responding to a user <input></input>. "
         "The <reference></reference> has guidelines for what it should contain or not contain.\n\n"
         "<output/> should contain no information or concepts not explicitly outlined in the <reference/>.\n\n"
         "Score 0,1,2.\n"
         "0: mostly missed the guidelines\n"
         "1: missing some guidelines\n"
         "2: Nailed it\n\n"
         "<input>%input</input>\n\n"
         "<output>%output<output>\n\n"
         "<reference>%referenceOutput</reference>"))
  (def eval-schema
    "{\"type\":\"object\",\"properties\":{\"reasoning\":{\"type\":\"string\",\"description\":\"why you gave the score you gave. what about the <output> aligned or didn't with the <reference>\"},\"score\":{\"type\":\"integer\",\"description\":\"Numeric score from 0-10\"}},\"required\":[\"score\",\"reasoning\"],\"additionalProperties\":false}")

  (aor/create-evaluator!
   manager
   eval-name
   "aor/llm-judge"
   {"model"        "gpt-5-4"
    "temperature"  "0.0"
    "prompt"       eval-prompt
    "outputSchema" eval-schema}
   "GPT-5.4 LLM judge")

  ;; -- Run evaluations over experiment results --

  (def experiment-id (java.util.UUID/fromString "019d30a9-bf7e-7926-b985-1c6941097055"))
  (def {:keys [datasets-pstate]} (aor-types/underlying-objects manager))

  (def results
    (rpath/select-one (rpath/keypath dataset-id :experiments experiment-id :results)
                      datasets-pstate))

  (def eval-results
    (doall
     (for [[result-idx result-entry] results
           :let [example-id (:example-id result-entry)
                 output     (get-in result-entry [:agent-results 0 :result :val])
                 example    (rpath/select-one (rpath/keypath dataset-id :snapshots nil example-id)
                                              datasets-pstate)
                 input      (:input example)
                 ref-output (:reference-output example)]]
       (do
         (println "Evaluating result" result-idx "example-id" example-id)
         (let [scores (aor/try-evaluator manager eval-name input ref-output output)]
           (println "  score:" (get scores "score") "| reasoning:" (get scores "reasoning"))
           {:result-idx result-idx
            :example-id example-id
            :scores     scores})))))

  )
