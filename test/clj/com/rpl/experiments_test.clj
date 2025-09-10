(ns com.rpl.experiments-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m])
  (:import
   [com.rpl.agent_o_rama.impl.types
    ComparativeExperiment
    RegularExperiment]
   [com.rpl.rama.helpers
    TopologyUtils]))

(defn count-or-num
  [v]
  (cond
    (nil? v)
    0

    (string? v)
    (count v)

    :else
    v))

(defn add-example-and-wait!
  [& args]
  (Thread/sleep 2)
  (apply aor/add-dataset-example! args))

(defn wait-experiment-finished!
  [exp-client agent-invoke]
  (let [res (aor/agent-result exp-client agent-invoke)]
    (when-not (= res :done)
      (throw (h/ex-info "Experiment failed" {:res res})))))

(deftest basic-experiments-test
  (let [example-id-chunks-atom (atom [])]
    (with-redefs [exp/hook:running-invoke-node
                  (fn [result+example-ids]
                    (swap! example-id-chunks-atom conj (count result+example-ids)))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (aor/declare-evaluator-builder
             topology
             "len"
             ""
             (fn [params]
               (fn [fetcher input ref-output output]
                 (let [len (+ (count-or-num input)
                              (count-or-num output)
                              (count-or-num ref-output))]
                   {"len" len}
                 ))))
            (aor/declare-comparative-evaluator-builder
             topology
             "identity-compare"
             ""
             (fn [params]
               (fn [fetcher input ref-output outputs]
                 {"outputs"    outputs
                  "input"      input
                  "ref-output" ref-output})))
            (aor/declare-summary-evaluator-builder
             topology
             "count"
             ""
             (fn [params]
               (fn [fetcher example-runs]
                 {"res" (count example-runs)}
               )))
            (aor/declare-summary-evaluator-builder
             topology
             "sum-sizes"
             ""
             (fn [params]
               (fn [fetcher example-runs]
                 {"res" (reduce
                         (fn [res {:keys [input reference-output output]}]
                           (+ res
                              (count-or-num input)
                              (count-or-num reference-output)
                              (count-or-num output)))
                         0
                         example-runs)}
               )))
            (-> topology
                (aor/new-agent "foo")
                (aor/node
                 "start"
                 ["end" "end2" "a" "b"]
                 (fn [agent-node action & inputs]
                   (if (= action "counts")
                     (let [v (reduce + 0 (mapv count-or-num inputs))]
                       (aor/emit! agent-node "end" :ignore v)
                       (aor/emit! agent-node "end" :keep (inc v))
                       (aor/emit! agent-node "end" :ignore v))
                     (aor/emit! agent-node "end2" (nth inputs 0) (nth inputs 1))
                   )))
                (aor/node
                 "end"
                 nil
                 (fn [agent-node command input]
                   (when (= command :keep)
                     (aor/result!
                      agent-node
                      (if (= 17 input)
                        "100"
                        "50")))))
                (aor/node
                 "end2"
                 nil
                 (fn [agent-node a b]
                   (aor/result!
                    agent-node
                    [{"node" "xyz"
                      "args" [(+ a b)]}])))
                (aor/node
                 "a"
                 ["end" "a"]
                 (fn [agent-node arg1 arg2]
                   (aor/emit! agent-node "end" (+ arg1 arg2 3))
                   (aor/emit! agent-node "a" (str arg1 "+" arg2))
                   (aor/emit! agent-node "end" (str arg1 "!"))
                 ))
                (aor/node
                 "b"
                 nil
                 (fn [agent-node arg1 arg2]
                   (aor/result! agent-node {"a" (str arg1 arg2)})
                 ))
            )
           ))
         (bind ds-module
           (aor/agentmodule
            [topology]))
         (rtest/launch-module! ipc module {:tasks 2 :threads 2})
         (rtest/launch-module! ipc ds-module {:tasks 2 :threads 2})
         (bind module-name (get-module-name module))
         (bind ds-module-name (get-module-name ds-module))
         (bind manager (aor/agent-manager ipc module-name))
         (bind ds-manager (aor/agent-manager ipc ds-module-name))
         (bind exp-client (aor/agent-client manager exp/EXPERIMENTER-NAME))
         (bind global-actions-depot
           (foreign-depot ipc module-name (po/global-actions-depot-name)))

         (bind results
           (foreign-query ipc module-name (queries/experiment-results-name)))

         (aor/create-evaluator! manager
                                "concise2"
                                "aor/conciseness"
                                {"threshold" "2"}
                                "")
         (aor/create-evaluator! manager "mylen" "len" {} "")
         (aor/create-evaluator! manager "mysum" "sum-sizes" {} "")
         (aor/create-evaluator! manager "mycount" "count" {} "")
         (aor/create-evaluator! manager
                                "identity-compare"
                                "identity-compare"
                                {}
                                ""
                                {:input-json-path  "$.a"
                                 :output-json-path "$[0].args"
                                 :reference-output-json-path "$[1]"
                                })
         (aor/create-evaluator! manager
                                "sum-with-paths"
                                "sum-sizes"
                                {}
                                ""
                                {:input-json-path  "$.b"
                                 :output-json-path "$.a"
                                 :reference-output-json-path "$[0]"
                                })

         (aor/create-evaluator! ds-manager
                                "rc3"
                                "aor/conciseness"
                                {"threshold" "3"}
                                ""
                                {:output-json-path "$.a"})

         (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))
         (bind remote-ds (aor/create-dataset! ds-manager "Dataset 3"))
         (aor-types/add-remote-dataset-internal manager remote-ds nil nil ds-module-name)

         (is (thrown?
              Exception
              (aor-types/add-remote-dataset-internal manager remote-ds nil nil "notamodule")))
         (is (thrown?
              Exception
              (aor-types/add-remote-dataset-internal manager remote-ds nil 1234 ds-module-name)))
         (is
          (thrown?
           Exception
           (aor-types/add-remote-dataset-internal manager remote-ds "a.b.c.d" nil ds-module-name)))

         (add-example-and-wait!
          manager
          ds-id1
          "abcdefg"
          {:reference-output "aaaaaaaaaaa"
           :tags #{"tag1" "tag2"}})
         (aor/snapshot-dataset! manager ds-id1 nil "mysnap")
         (add-example-and-wait!
          manager
          ds-id1
          "ab"
          {:reference-output ".."
           :tags #{"tag1"}})
         (add-example-and-wait!
          manager
          ds-id1
          "123456789abcdefg"
          {:reference-output "."
           :tags #{"tag1"}})
         (add-example-and-wait!
          manager
          ds-id1
          "aa"
          {:reference-output "bbbbb"})


         (add-example-and-wait!
          ds-manager
          remote-ds
          {"a" 1 "b" 10}
          {:reference-output ["1234567" "89"]})
         (add-example-and-wait!
          ds-manager
          remote-ds
          {"a" 2 "b" 100})
         (add-example-and-wait!
          ds-manager
          remote-ds
          {"a" 3 "b" 1000}
          {:reference-output ["abcdefg" "hijklmnop"]})

         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "mylen" false)
              (aor-types/->valid-EvaluatorSelector "concise2" false)
              (aor-types/->valid-EvaluatorSelector "mycount" false)
              (aor-types/->valid-EvaluatorSelector "mysum" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-AgentTarget "foo")
               ["\"counts\"" "$"]
              ))
             2
             2)))

         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results ds-id1 exp-id))
         (is (aor-types/StartExperiment? (:experiment-info res)))
         (is (> (:finish-time-millis res) (:start-time-millis res)))
         (is (aor-types/AgentInvokeImpl? (:experiment-invoke res)))
         (is (= 2 (count @example-id-chunks-atom)))
         (is (every? #(= 4 %) @example-id-chunks-atom))

         (bind [ex-id0 ex-id3]
           (select [:results (multi-path (keypath 0) (keypath 3)) :example-id] res))

         (is
          (trace-matches?
           res
           {:summary-evals     {"mycount" {"res" 8} "mysum" {"res" 110}}
            :summary-eval-failures nil
            :latency-number-stats
            {:count 8}
            :eval-number-stats
            {"mylen"
             {"len"
              {:total       110
               :count       8
               :min         6
               :max         20
               :percentiles
               {0.1   6
                0.2   6
                0.3   9
                0.4   9
                0.5   20
                0.6   20
                0.7   20
                0.8   20
                0.9   20
                0.99  20
                0.999 20
               }}}
             "concise2"
             {"concise?"
              {:total       6
               :count       8
               :min         0
               :max         1
               :percentiles
               {0.1   0
                0.2   0
                0.3   1
                0.4   1
                0.5   1
                0.6   1
                0.7   1
                0.8   1
                0.9   1
                0.99  1
                0.999 1}}}}
            :results
            {0
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" true}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}
             1
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 6} "concise2" {"concise?" true}}
              :input            "ab"
              :reference-output ".."}
             2
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "100" :failure? false}}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" false}}
              :input            "123456789abcdefg"
              :reference-output "."}
             3
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 9} "concise2" {"concise?" true}}
              :input            "aa"
              :reference-output "bbbbb"}
             4
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" true}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}
             5
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 6} "concise2" {"concise?" true}}
              :input            "ab"
              :reference-output ".."}
             6
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "100" :failure? false}}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" false}}
              :input            "123456789abcdefg"
              :reference-output "."}
             7
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:result {:val "50" :failure? false}}}
              :evals            {"mylen" {"len" 9} "concise2" {"concise?" true}}
              :input            "aa"
              :reference-output "bbbbb"}
            }}))

         (is (> (-> res
                    :latency-number-stats
                    :total)
                0))

         (is (every? aor-types/AgentInvokeImpl?
                     (select [:results MAP-VALS :agent-initiates MAP-VALS :agent-invoke] res)))



         (doseq [{:keys [start-time-millis finish-time-millis]}
                 (select [:results MAP-VALS :agent-results MAP-VALS] res)]
           (is (number? start-time-millis))
           (is (number? finish-time-millis))
           (is (>= finish-time-millis start-time-millis)))

         ;; test:
         ;;   - comparative experiment
         ;;   - node with emits
         ;;   - JSON paths for regular evaluators
         (reset! example-id-chunks-atom [])
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment 2"
             remote-ds
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "identity-compare" false)]
             (aor-types/->valid-ComparativeExperiment
              [(aor-types/->valid-ExperimentTarget
                (aor-types/->valid-NodeTarget "foo" "a")
                ["$.a" "$.b"])
               (aor-types/->valid-ExperimentTarget
                (aor-types/->valid-AgentTarget "foo")
                ["\"other\"" "$.a" "$.b"])])
             1
             2)))

         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results remote-ds exp-id))
         (is (aor-types/StartExperiment? (:experiment-info res)))
         (is (> (:finish-time-millis res) (:start-time-millis res)))
         (is (aor-types/AgentInvokeImpl? (:experiment-invoke res)))
         (is (= 2 (count @example-id-chunks-atom)))
         (is (= #{2 1} (set @example-id-chunks-atom)))

         (is
          (trace-matches?
           res
           {:summary-evals nil
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}
               1
               {:agent-name "foo"}}
              :agent-results
              {0
               {:result {:val
                         [{"node" "end" "args" [14]}
                          {"node" "a" "args" ["1+10"]}
                          {"node" "end" "args" ["1!"]}]
                         :failure? false}}
               1 {:result {:val [{"node" "xyz" "args" [11]}] :failure? false}}}
              :evals
              {"identity-compare"
               {"outputs" [[14] [11]] "input" 1 "ref-output" "89"}}
              :input            {"a" 1 "b" 10}
              :reference-output ["1234567" "89"]}
             1
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}
               1
               {:agent-name "foo"}}
              :agent-results
              {0
               {:result {:val
                         [{"node" "end" "args" [105]}
                          {"node" "a" "args" ["2+100"]}
                          {"node" "end" "args" ["2!"]}]
                         :failure? false}}
               1 {:result {:val [{"node" "xyz" "args" [102]}] :failure? false}}}
              :evals
              {"identity-compare"
               {"outputs" [[105] [102]] "input" 2 "ref-output" nil}}
              :input            {"a" 2 "b" 100}
              :reference-output nil}
             2
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}
               1
               {:agent-name "foo"}}
              :agent-results
              {0
               {:result {:val
                         [{"node" "end" "args" [1006]}
                          {"node" "a" "args" ["3+1000"]}
                          {"node" "end" "args" ["3!"]}]
                         :failure? false}}
               1 {:result {:val [{"node" "xyz" "args" [1003]}] :failure? false}}}
              :evals
              {"identity-compare"
               {"outputs" [[1006] [1003]] "input" 3 "ref-output" "hijklmnop"}}
              :input            {"a" 3 "b" 1000}
              :reference-output ["abcdefg" "hijklmnop"]}}}
          ))
         (is (every? aor-types/AgentInvokeImpl?
                     (select [:results MAP-VALS :agent-initiates MAP-VALS :agent-invoke] res)))


         ;; test:
         ;;   - node using aor/result!
         ;;   - remote evaluator
         ;;   - summary eval with custom json paths
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment 3"
             remote-ds
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "rc3" true)
              (aor-types/->valid-EvaluatorSelector "sum-with-paths" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "b")
               ["\"$.a\"" "$.b"]
              ))
             1
             2)))

         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results remote-ds exp-id))
         (is
          (trace-matches?
           res
           {:summary-evals {"sum-with-paths" {"res" 1136}}
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "110"} :failure? false}}}
              :evals            {"rc3" {"concise?" true}}
              :input            {"a" 1 "b" 10}
              :reference-output ["1234567" "89"]}
             1
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "2100"} :failure? false}}}
              :evals            {"rc3" {"concise?" false}}
              :input            {"a" 2 "b" 100}
              :reference-output nil}
             2
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "31000"} :failure? false}}}
              :evals            {"rc3" {"concise?" false}}
              :input            {"a" 3 "b" 1000}
              :reference-output ["abcdefg" "hijklmnop"]}}}
          ))


         ;; test selecting specific tag
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             nil
             (aor-types/->valid-TagSelector "tag1")
             [(aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "b")
               ["$" "\"!\""]
              ))
             1
             3)))
         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results ds-id1 exp-id))
         (is
          (trace-matches?
           res
           {:summary-evals {"mycount" {"res" 3}}
            :summary-eval-failures nil
            :results       {0
                            {:example-id       !eid1
                             :agent-initiates
                             {0
                              {:agent-name "_aor-experimenter"}}
                             :agent-results    {0 {:result {:val {"a" "abcdefg!"} :failure? false}}}
                             :input            "abcdefg"
                             :reference-output "aaaaaaaaaaa"}
                            1
                            {:example-id       !eid2
                             :agent-initiates
                             {0
                              {:agent-name "_aor-experimenter"}}
                             :agent-results    {0 {:result {:val {"a" "ab!"} :failure? false}}}
                             :input            "ab"
                             :reference-output ".."}
                            2
                            {:example-id       !eid3
                             :agent-initiates
                             {0
                              {:agent-name "_aor-experimenter"}}
                             :agent-results
                             {0 {:result {:val {"a" "123456789abcdefg!"} :failure? false}}}
                             :input            "123456789abcdefg"
                             :reference-output "."}}}
          ))

         ;; test selecting specific examples
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             nil
             (aor-types/->valid-ExampleIdsSelector [ex-id0 ex-id3])
             [(aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "b")
               ["$" "\"!!!\""]
              ))
             1
             3)))
         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results ds-id1 exp-id))
         (is
          (trace-matches?
           res
           {:summary-evals {"mycount" {"res" 2}}
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "abcdefg!!!"} :failure? false}}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}
             1
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "aa!!!"} :failure? false}}}
              :input            "aa"
              :reference-output "bbbbb"}}}
          ))

         ;; test specific snapshot
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             "mysnap"
             nil
             [(aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "b")
               ["$" "\"!!!\""]
              ))
             1
             100)))
         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results ds-id1 exp-id))
         (is
          (trace-matches?
           res
           {:summary-evals {"mycount" {"res" 1}}
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results    {0 {:result {:val {"a" "abcdefg!!!"} :failure? false}}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}}}
          ))


         ;; test error running experiment with non-existent node
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             "mysnap"
             nil
             [(aor-types/->valid-EvaluatorSelector "mycount" false)
              (aor-types/->valid-EvaluatorSelector "concise2" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "notanode")
               ["$"]
              ))
             1
             100)))
         (wait-experiment-finished! exp-client exp-invoke)
         (bind res (foreign-invoke-query results ds-id1 exp-id))
         (is
          (trace-matches?
           res
           {:summary-evals {"mycount" {"res" 0}}
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "_aor-experimenter"}}
              :agent-results
              {0
               {:result {:val      {:message "Node does not exist" :node "notanode"}
                         :failure? true}}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}}}
          ))
         (doseq [{:keys [start-time-millis finish-time-millis]}
                 (select [:results MAP-VALS :agent-results MAP-VALS] res)]
           (is (number? start-time-millis))
           (is (number? finish-time-millis))
           (is (>= finish-time-millis start-time-millis)))

         ;; test with non-existent dataset
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             (h/random-uuid7)
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "a")
               ["$" "$"]
              ))
             1
             2)))
         (try
           (wait-experiment-finished! exp-client exp-invoke)
           (is false)
           (catch Exception e
             (is (= (ex-data e) {:res {:error "Dataset does not exist"}}))
           ))

         ;; test with non-existent snapshot
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             "notasnapshot"
             nil
             [(aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "a")
               ["$" "$"]
              ))
             1
             2)))
         (try
           (wait-experiment-finished! exp-client exp-invoke)
           (is false)
           (catch Exception e
             (is (= (ex-data e) {:res {:error "Snapshot does not exist or has no examples"}}))
           ))


         ;; test error running regular experiment with comparative evaluator
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment"
             ds-id1
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "identity-compare" false)]
             (aor-types/->valid-RegularExperiment
              (aor-types/->valid-ExperimentTarget
               (aor-types/->valid-NodeTarget "foo" "a")
               ["$" "$"]
              ))
             1
             100)))
         (try
           (wait-experiment-finished! exp-client exp-invoke)
           (is false)
           (catch Exception e
             (is (= (ex-data e)
                    {:res {:error    "Problem with one or more evaluators"
                           :problems [{:problem         "Evaluator type does not match experiment"
                                       :experiment-type RegularExperiment
                                       :evaluator-type  :comparative
                                       :name            "identity-compare"
                                       :remote?         false}]}}))
           ))


         ;; test error running comparative experiment with regular evaluator
         (bind exp-id (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp-id
             "My experiment 2"
             remote-ds
             nil
             nil
             [(aor-types/->valid-EvaluatorSelector "mylen" false)
              (aor-types/->valid-EvaluatorSelector "identity-compare" false)
              (aor-types/->valid-EvaluatorSelector "mycount" false)]
             (aor-types/->valid-ComparativeExperiment
              [(aor-types/->valid-ExperimentTarget
                (aor-types/->valid-NodeTarget "foo" "a")
                ["$.a" "$.b"])
               (aor-types/->valid-ExperimentTarget
                (aor-types/->valid-AgentTarget "foo")
                ["\"other\"" "$.a" "$.b"])])
             1
             2)))
         (try
           (wait-experiment-finished! exp-client exp-invoke)
           (is false)
           (catch Exception e
             (is (= (ex-data e)
                    {:res {:error    "Problem with one or more evaluators"
                           :problems [{:problem         "Evaluator type does not match experiment"
                                       :experiment-type ComparativeExperiment
                                       :evaluator-type  :regular
                                       :name            "mylen"
                                       :remote?         false}
                                      {:problem         "Evaluator type does not match experiment"
                                       :experiment-type ComparativeExperiment
                                       :evaluator-type  :summary
                                       :name            "mycount"
                                       :remote?         false}]}}))
           ))
        )))))

(deftest execution-failures-test
  (with-redefs
    [anode/log-node-error (fn [& args])]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-evaluator-builder
           topology
           "rfail"
           ""
           (fn [params]
             (fn [fetcher input ref-output output]
               (if (= output "a!")
                 (throw (ex-info "fail" {}))
                 {"res" output}))))
          (aor/declare-comparative-evaluator-builder
           topology
           "cfail"
           ""
           (fn [params]
             (fn [fetcher input ref-output outputs]
               (if (some #(= "a!" %) outputs)
                 (throw (ex-info "fail" {}))
                 {"res" outputs}))))
          (aor/declare-comparative-evaluator-builder
           topology
           "ccount"
           ""
           (fn [params]
             (fn [fetcher input ref-output outputs]
               {"res" (count outputs)})))
          (aor/declare-summary-evaluator-builder
           topology
           "sfail"
           ""
           (fn [params]
             (fn [fetcher example-runs]
               (throw (ex-info "fail" {}))
             )))
          (aor/declare-summary-evaluator-builder
           topology
           "count"
           ""
           (fn [params]
             (fn [fetcher example-runs]
               {"res" (count example-runs)}
             )))
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               "a"
               (fn [agent-node arg]
                 (if (= arg "fail-agent")
                   (throw (ex-info "fail" {}))
                   (aor/result! agent-node (str arg "!")))
               ))
              (aor/node
               "a"
               nil
               (fn [agent-node arg]
                 (if (= arg "fail-node")
                   (throw (ex-info "fail" {}))
                   (aor/result! agent-node (str arg "?")))
               ))
          )))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind manager (aor/agent-manager ipc module-name))
       (bind exp-client (aor/agent-client manager exp/EXPERIMENTER-NAME))
       (bind global-actions-depot
         (foreign-depot ipc module-name (po/global-actions-depot-name)))
       (bind results
         (foreign-query ipc module-name (queries/experiment-results-name)))

       (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))

       (add-example-and-wait! manager ds-id1 "a" {:tags #{"t"}})
       (add-example-and-wait! manager ds-id1 "fail-agent")
       (add-example-and-wait! manager ds-id1 "fail-node")

       (aor/create-evaluator! manager
                              "concise2"
                              "aor/conciseness"
                              {"threshold" "2"}
                              "")
       (aor/create-evaluator! manager "rfail" "rfail" {} "")
       (aor/create-evaluator! manager "cfail" "cfail" {} "")
       (aor/create-evaluator! manager "sfail" "sfail" {} "")
       (aor/create-evaluator! manager "mycount" "count" {} "")
       (aor/create-evaluator! manager "ccount" "ccount" {} "")

       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "concise2" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-AgentTarget "foo")
             ["$"]
            ))
           1
           2)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is
        (trace-matches?
         res
         {:summary-evals nil
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "a!" :failure? false}}}
            :evals            {"concise2" {"concise?" true}}
            :input            "a"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results
            {0 {:result {:val {:message "Failure on example"} :failure? true}}}
            :input            "fail-agent"
            :reference-output nil}
           2
           {:example-id       !eid2
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "fail-node!" :failure? false}}}
            :evals            {"concise2" {"concise?" false}}
            :input            "fail-node"
            :reference-output nil}}}
        ))

       ;; verify have start/finish times for failed example
       (doseq [{:keys [start-time-millis finish-time-millis]}
               (select [:results MAP-VALS :agent-results MAP-VALS] res)]
         (is (number? start-time-millis))
         (is (number? finish-time-millis))
         (is (>= finish-time-millis start-time-millis)))

       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "concise2" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-NodeTarget "foo" "a")
             ["$"]
            ))
           1
           2)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is
        (trace-matches?
         res
         {:summary-evals nil
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "_aor-experimenter"}}
            :agent-results    {0 {:result {:val "a?" :failure? false}}}
            :evals            {"concise2" {"concise?" true}}
            :input            "a"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "_aor-experimenter"}}
            :agent-results    {0 {:result {:val "fail-agent?" :failure? false}}}
            :evals            {"concise2" {"concise?" false}}
            :input            "fail-agent"
            :reference-output nil}
           2
           {:example-id       !eid2
            :agent-initiates
            {0
             {:agent-name "_aor-experimenter"}}
            :agent-results
            {0
             {:result {:val
                       {:message "Failure executing node"
                        :node    "a"
                        :args    ["fail-node"]}
                       :failure? true}}}
            :input            "fail-node"
            :reference-output nil}}}
        ))


       (add-example-and-wait! manager ds-id1 "b" {:tags #{"t"}})
       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           (aor-types/->valid-TagSelector "t")
           [(aor-types/->valid-EvaluatorSelector "concise2" false)
            (aor-types/->valid-EvaluatorSelector "rfail" false)
            (aor-types/->valid-EvaluatorSelector "sfail" false)
            (aor-types/->valid-EvaluatorSelector "mycount" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-AgentTarget "foo")
             ["$"]
            ))
           1
           2)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is
        (trace-matches?
         res
         {:summary-evals {"mycount" {"res" 2}}
          :summary-eval-failures
          {"sfail" !ex1}
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "a!" :failure? false}}}
            :evals            {"concise2" {"concise?" true}}
            :eval-failures
            {"rfail" !ex2}
            :input            "a"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "b!" :failure? false}}}
            :evals            {"concise2" {"concise?" true} "rfail" {"res" "b!"}}
            :input            "b"
            :reference-output nil}}}
        ))


       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           (aor-types/->valid-TagSelector "t")
           [(aor-types/->valid-EvaluatorSelector "cfail" false)
            (aor-types/->valid-EvaluatorSelector "ccount" false)]
           (aor-types/->valid-ComparativeExperiment
            [(aor-types/->valid-ExperimentTarget
              (aor-types/->valid-AgentTarget "foo")
              ["$"])
             (aor-types/->valid-ExperimentTarget
              (aor-types/->valid-NodeTarget "foo" "a")
              ["$"])])
           1
           2)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is
        (trace-matches?
         res
         {:summary-evals nil
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "_aor-experimenter"}}
            :agent-results
            {0 {:result {:val "a!" :failure? false}}
             1 {:result {:val "a?" :failure? false}}}
            :evals            {"ccount" {"res" 2}}
            :eval-failures
            {"cfail" !ex1}
            :input            "a"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "_aor-experimenter"}}
            :agent-results
            {0 {:result {:val "b!" :failure? false}}
             1 {:result {:val "b?" :failure? false}}}
            :evals            {"cfail" {"res" ["b!" "b?"]} "ccount" {"res" 2}}
            :input            "b"
            :reference-output nil}}}
        ))

       ;; verify behavior when example is missing
       (bind a-id (select-any [:results (keypath 0) :example-id] res))
       (aor/remove-dataset-example! manager ds-id1 a-id)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is
        (trace-matches?
         res
         {:summary-evals nil
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "_aor-experimenter"}}
            :agent-results
            {0 {:result {:val "a!" :failure? false}}
             1 {:result {:val "a?" :failure? false}}}
            :evals            {"ccount" {"res" 2}}
            :eval-failures
            {"cfail" !ex1}
            :missing-example? true}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "_aor-experimenter"}}
            :agent-results
            {0 {:result {:val "b!" :failure? false}}
             1 {:result {:val "b?" :failure? false}}}
            :evals            {"cfail" {"res" ["b!" "b?"]} "ccount" {"res" 2}}
            :input            "b"
            :reference-output nil}}}
        ))
      ))))

(def RUNS)
(def HANDLER-FNS)

(deftest experimenter-agent-failures-test
  (with-redefs [RUNS (atom [])
                HANDLER-FNS (atom {})

                exp/hook:initiate-target
                (fn [i]
                  ((get @HANDLER-FNS :initiate) i))

                exp/hook:result-target
                (fn [i]
                  ((get @HANDLER-FNS :result) i))

                exp/hook:do-eval
                (fn [eval-name]
                  ((get @HANDLER-FNS :eval) eval-name))

                exp/hook:do-summary-eval
                (fn [eval-name]
                  ((get @HANDLER-FNS :summary) eval-name))

                anode/log-node-error (fn [& args])]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-evaluator-builder
           topology
           "reg"
           ""
           (fn [params]
             (fn [fetcher input ref-output output]
               (swap! RUNS conj :eval-r)
               {"res" output})))
          (aor/declare-comparative-evaluator-builder
           topology
           "ccount"
           ""
           (fn [params]
             (fn [fetcher input ref-output outputs]
               (swap! RUNS conj :eval-c)
               {"res" (count outputs)})))
          (aor/declare-summary-evaluator-builder
           topology
           "count"
           ""
           (fn [params]
             (fn [fetcher example-runs]
               (swap! RUNS conj :eval-s)
               {"res" (count example-runs)}
             )))
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node arg]
                 (swap! RUNS conj :agent)
                 (aor/result! agent-node (str arg "!")))
              ))))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind manager (aor/agent-manager ipc module-name))
       (bind exp-client (aor/agent-client manager exp/EXPERIMENTER-NAME))
       (bind global-actions-depot
         (foreign-depot ipc module-name (po/global-actions-depot-name)))
       (bind results
         (foreign-query ipc module-name (queries/experiment-results-name)))

       (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))
       (add-example-and-wait! manager ds-id1 "aa")
       (add-example-and-wait! manager ds-id1 "bb")
       (add-example-and-wait! manager ds-id1 "cc")

       (aor/create-evaluator! manager "reg" "reg" {} "")
       (aor/create-evaluator! manager "reg2" "reg" {} "")
       (aor/create-evaluator! manager "ccount" "ccount" {} "")
       (aor/create-evaluator! manager "ccount2" "ccount" {} "")
       (aor/create-evaluator! manager "count" "count" {} "")
       (aor/create-evaluator! manager "count2" "count" {} "")

       (bind reset-handlers!
         (fn []
           (reset! HANDLER-FNS {:initiate (constantly nil)
                                :result   (constantly nil)
                                :eval     (constantly nil)
                                :summary  (constantly nil)})))
       (bind handler!
         (fn [k afn]
           (swap! HANDLER-FNS assoc k afn)))

       (bind fail-on-n!
         (fn [k n]
           (handler!
            k
            (let [c (atom 0)]
              (fn [_]
                (when (= n (swap! c inc))
                  (throw (ex-info "fail" {}))))))))

       (bind fail-on-n-arg!
         (fn [k target n]
           (handler!
            k
            (let [c (atom 0)]
              (fn [arg]
                (when (= arg target)
                  (swap! c inc)
                  (when (= n @c)
                    (throw (ex-info "fail" {})))))))))


       (reset-handlers!)
       (fail-on-n! :initiate 2)
       (fail-on-n! :result 2)
       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "reg" false)
            (aor-types/->valid-EvaluatorSelector "reg2" false)
            (aor-types/->valid-EvaluatorSelector "count" false)
            (aor-types/->valid-EvaluatorSelector "count2" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-AgentTarget "foo")
             ["$"]))
           1
           1)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is (= {:agent 3 :eval-r 6 :eval-s 2}
              (->> @RUNS
                   (group-by identity)
                   (transform MAP-VALS count))))

       (is
        (trace-matches?
         res
         {:summary-evals {"count" {"res" 3} "count2" {"res" 3}}
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "aa!" :failure? false}}}
            :evals            {"reg" {"res" "aa!"} "reg2" {"res" "aa!"}}
            :input            "aa"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "bb!" :failure? false}}}
            :evals            {"reg" {"res" "bb!"} "reg2" {"res" "bb!"}}
            :input            "bb"
            :reference-output nil}
           2
           {:example-id       !eid2
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "cc!" :failure? false}}}
            :evals            {"reg" {"res" "cc!"} "reg2" {"res" "cc!"}}
            :input            "cc"
            :reference-output nil}}}
        ))

       (reset-handlers!)
       (reset! RUNS [])
       (fail-on-n-arg! :eval "reg2" 2)
       (fail-on-n-arg! :summary "count2" 1)
       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "reg" false)
            (aor-types/->valid-EvaluatorSelector "reg2" false)
            (aor-types/->valid-EvaluatorSelector "count" false)
            (aor-types/->valid-EvaluatorSelector "count2" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-AgentTarget "foo")
             ["$"]))
           1
           1)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is (= {:agent 3 :eval-r 6 :eval-s 2}
              (->> @RUNS
                   (group-by identity)
                   (transform MAP-VALS count))))
       (is
        (trace-matches?
         res
         {:summary-evals {"count" {"res" 3} "count2" {"res" 3}}
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "aa!" :failure? false}}}
            :evals            {"reg" {"res" "aa!"} "reg2" {"res" "aa!"}}
            :input            "aa"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "bb!" :failure? false}}}
            :evals            {"reg" {"res" "bb!"} "reg2" {"res" "bb!"}}
            :input            "bb"
            :reference-output nil}
           2
           {:example-id       !eid2
            :agent-initiates
            {0
             {:agent-name "foo"}}
            :agent-results    {0 {:result {:val "cc!" :failure? false}}}
            :evals            {"reg" {"res" "cc!"} "reg2" {"res" "cc!"}}
            :input            "cc"
            :reference-output nil}}}
        ))


       (reset-handlers!)
       (reset! RUNS [])
       (fail-on-n-arg! :initiate 1 2)
       (fail-on-n-arg! :eval "ccount2" 2)
       (bind exp-id (h/random-uuid7))
       (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
         (foreign-append!
          global-actions-depot
          (aor-types/->valid-StartExperiment
           exp-id
           "My experiment"
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "ccount" false)
            (aor-types/->valid-EvaluatorSelector "ccount2" false)]
           (aor-types/->valid-ComparativeExperiment
            [(aor-types/->valid-ExperimentTarget
              (aor-types/->valid-AgentTarget "foo")
              ["$"])
             (aor-types/->valid-ExperimentTarget
              (aor-types/->valid-AgentTarget "foo")
              ["$"])])
           1
           1)))
       (wait-experiment-finished! exp-client exp-invoke)
       (bind res (foreign-invoke-query results ds-id1 exp-id))
       (is (= {:agent 6 :eval-c 6}
              (->> @RUNS
                   (group-by identity)
                   (transform MAP-VALS count))))
       (is
        (trace-matches?
         res
         {:summary-evals nil
          :summary-eval-failures nil
          :results
          {0
           {:example-id       !eid0
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "foo"}}
            :agent-results
            {0 {:result {:val "aa!" :failure? false}}
             1 {:result {:val "aa!" :failure? false}}}
            :evals            {"ccount" {"res" 2} "ccount2" {"res" 2}}
            :input            "aa"
            :reference-output nil}
           1
           {:example-id       !eid1
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "foo"}}
            :agent-results
            {0 {:result {:val "bb!" :failure? false}}
             1 {:result {:val "bb!" :failure? false}}}
            :evals            {"ccount" {"res" 2} "ccount2" {"res" 2}}
            :input            "bb"
            :reference-output nil}
           2
           {:example-id       !eid2
            :agent-initiates
            {0
             {:agent-name "foo"}
             1
             {:agent-name "foo"}}
            :agent-results
            {0 {:result {:val "cc!" :failure? false}}
             1 {:result {:val "cc!" :failure? false}}}
            :evals            {"ccount" {"res" 2} "ccount2" {"res" 2}}
            :input            "cc"
            :reference-output nil}}}
        ))
      ))))

(deftest search-experiments-test
  (with-open [ipc (rtest/create-ipc)
              _ (TopologyUtils/startSimTime)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-comparative-evaluator-builder
         topology
         "ccount"
         ""
         (fn [params]
           (fn [fetcher input ref-output outputs]
             {"res" (count outputs)})))
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node arg]
               (aor/result! agent-node (str arg "!")))
            ))))
     (rtest/launch-module! ipc module {:tasks 2 :threads 2})
     (bind module-name (get-module-name module))
     (bind manager (aor/agent-manager ipc module-name))
     (bind exp-client (aor/agent-client manager exp/EXPERIMENTER-NAME))
     (bind global-actions-depot
       (foreign-depot ipc module-name (po/global-actions-depot-name)))
     (bind search
       (foreign-query ipc module-name (queries/search-experiments-name)))

     (aor/create-evaluator! manager
                            "concise2"
                            "aor/conciseness"
                            {"threshold" "2"}
                            "")
     (aor/create-evaluator! manager "ccount" "ccount" {} "")

     (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))
     (add-example-and-wait! manager ds-id1 "aa")

     (bind run-experiment!
       (fn [exp]
         (let [exp-id (h/random-uuid7)
               {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
               (foreign-append! global-actions-depot (assoc exp :id exp-id))]
           (wait-experiment-finished! exp-client exp-invoke)
           (TopologyUtils/advanceSimTime 1000)
           exp-id
         )))

     (bind run-regular!
       (fn [desc]
         (run-experiment!
          (aor-types/->StartExperiment
           nil
           desc
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "concise2" false)]
           (aor-types/->valid-RegularExperiment
            (aor-types/->valid-ExperimentTarget
             (aor-types/->valid-AgentTarget "foo")
             ["$"]))
           1
           1))
       ))

     (bind run-comparative!
       (fn [desc]
         (run-experiment!
          (aor-types/->StartExperiment
           nil
           desc
           ds-id1
           nil
           nil
           [(aor-types/->valid-EvaluatorSelector "ccount" false)]
           (aor-types/->valid-ComparativeExperiment
            [(aor-types/->valid-ExperimentTarget
              (aor-types/->valid-AgentTarget "foo")
              ["$"])
             (aor-types/->valid-ExperimentTarget
              (aor-types/->valid-AgentTarget "foo")
              ["$"])])
           1
           1))
       ))

     (bind matches-ids?
       (fn [res ids]
         (= ids (select [:items ALL :experiment-info :id] res))))

     (bind exp-id1 (run-comparative! "hello world"))
     (bind exp-id2 (run-regular! "hello you"))
     (bind exp-id3 (run-comparative! "what is rama"))
     (bind exp-id4 (run-comparative! "hello"))
     (bind exp-id5 (run-regular! "rama"))
     (bind exp-id6 (run-regular! "rama hello"))
     (bind exp-id7 (run-regular! "world hello"))
     (bind exp-id8 (run-regular! "what"))
     (bind exp-id9 (run-comparative! "a b c"))
     (bind exp-id10 (run-regular! "hello hello"))

     (bind res (foreign-invoke-query search ds-id1 {} 3 nil))
     (is (matches-ids? res [exp-id10 exp-id9 exp-id8]))
     (bind res (foreign-invoke-query search ds-id1 {} 3 (:pagination-params res)))
     (is (matches-ids? res [exp-id7 exp-id6 exp-id5]))
     (bind res (foreign-invoke-query search ds-id1 {} 3 (:pagination-params res)))
     (is (matches-ids? res [exp-id4 exp-id3 exp-id2]))
     (bind res (foreign-invoke-query search ds-id1 {} 3 (:pagination-params res)))
     (is (matches-ids? res [exp-id1]))
     (is (nil? (:pagination-params res)))


     (bind res (foreign-invoke-query search ds-id1 {:search-string "hello"} 2 nil))
     (is (matches-ids? res [exp-id10 exp-id7]))
     (bind res
       (foreign-invoke-query search ds-id1 {:search-string "hello"} 2 (:pagination-params res)))
     (is (matches-ids? res [exp-id6 exp-id4]))
     (bind res
       (foreign-invoke-query search ds-id1 {:search-string "hello"} 2 (:pagination-params res)))
     (is (matches-ids? res [exp-id2 exp-id1]))
     (bind res
       (foreign-invoke-query search ds-id1 {:search-string "hello"} 2 (:pagination-params res)))
     (is (matches-ids? res []))
     (is (nil? (:pagination-params res)))

     (bind res (foreign-invoke-query search ds-id1 {:search-string (str exp-id5)} 2 nil))
     (is (matches-ids? res [exp-id5]))
     (is (nil? (:pagination-params res)))


     (bind res (foreign-invoke-query search ds-id1 {:type ComparativeExperiment} 2 nil))
     (is (matches-ids? res [exp-id9 exp-id4 exp-id3]))
     (bind res
       (foreign-invoke-query search
                             ds-id1
                             {:type ComparativeExperiment}
                             2
                             (:pagination-params res)))
     (is (matches-ids? res [exp-id1]))
     (is (nil? (:pagination-params res)))

     (bind res (foreign-invoke-query search ds-id1 {:type RegularExperiment} 1000 nil))
     (is (matches-ids? res [exp-id10 exp-id8 exp-id7 exp-id6 exp-id5 exp-id2]))
     (is (nil? (:pagination-params res)))
     ;; regular experiments have these additional aggregated stats
     (is (every? #(contains? % :eval-number-stats) (:items res)))
     (is (every? #(contains? % :latency-number-stats) (:items res)))


     (bind res
       (foreign-invoke-query search
                             ds-id1
                             {:type ComparativeExperiment :search-string "hello"}
                             2
                             nil))
     (is (matches-ids? res [exp-id4 exp-id1]))
     (bind res
       (foreign-invoke-query search
                             ds-id1
                             {:type ComparativeExperiment :search-string "hello"}
                             2
                             (:pagination-params res)))
     (is (matches-ids? res []))
     (is (nil? (:pagination-params res)))


     (bind res
       (foreign-invoke-query search
                             ds-id1
                             {:times [{:pred >= :value 1000} {:pred <= :value 4000}]}
                             10
                             nil))
     (is (matches-ids? res [exp-id5 exp-id4 exp-id3 exp-id2]))
     (is (nil? (:pagination-params res)))
    )))


(deftest merge-number-evals-test
  (is (= {"a" {"b" [1] "c" [2 3]}
          "d" {"b" [10 11]}
          "e" {"q" [1 1 0]}}
         (exp/merge-number-evals [{"a" {"b" 1 "c" 2} "d" {"b" 10}}
                                  {"a" {"b" "blah" "c" 3}
                                   "d" {"b" 11}
                                   "e" {"q" true}}
                                  {"e" {"q" true}}
                                  {"e" {"q" false}}
                                  {"e" {"q" nil}}
                                 ])))
)

(deftest compute-eval-number-stats-test
  (is (= {"e1" {"a" (aor-types/->EvalNumberStats 6 3 1 3 nil)
                "b" (aor-types/->EvalNumberStats 35.0 4 2 20 nil)}
          "e2" {"r" (aor-types/->EvalNumberStats 2 3 0 1 nil)}}
         (setval [MAP-VALS MAP-VALS :percentiles]
                 nil
                 (exp/compute-eval-number-stats
                  [{:evals {"e1" {"a" 1 "b" 2} "e2" {"r" true}}}
                   {:evals {"e1" {"a" 2 "b" 3} "e2" {"r" false}}}
                   {:evals {"e1" {"b" 10.0} "e2" {"r" nil}}}
                   {:evals {"e1" {"a" 3 "b" 20} "e2" {"r" true}}}]))
      )))
