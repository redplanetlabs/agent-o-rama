(ns com.rpl.experiments-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m]))


(defn count-or-num
  [v]
  (if (string? v) (count v) v))


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
             "compare-min"
             ""
             (fn [params]
               (fn [fetcher input ref-output outputs]
                 (let [outputs (mapv parse-long outputs)
                       m       (apply min outputs)]
                   {"res"
                    (select-any [INDEXED-VALS (selected? LAST (pred= m)) FIRST] outputs)
                    "input"      input
                    "ref-output" ref-output}))))
            (aor/declare-comparative-evaluator-builder
             topology
             "compare-max"
             ""
             (fn [params]
               (fn [fetcher input ref-output outputs]
                 (let [outputs (mapv parse-long outputs)
                       m       (apply max outputs)]
                   {"res"
                    (select-any [INDEXED-VALS (selected? LAST (pred= m)) FIRST] outputs)
                    "input"      input
                    "ref-output" ref-output}))))
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
                           ;; TODO: <<<<>>>> getting ["50"] here for output
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
                 ["end" "a"]
                 (fn [agent-node action & inputs]
                   (if (= action "counts")
                     (let [v (reduce + 0 (mapv count-or-num inputs))]
                       (aor/emit! agent-node "end" :ignore v)
                       (aor/emit! agent-node "end" :keep (inc v))
                       (aor/emit! agent-node "end" :ignore v))
                     (aor/emit! agent-node "end2" (get (nth inputs 0) "a") (get (nth inputs 1) "b"))
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
                   (aor/result! agent-node (+ a b))))
                (aor/node
                 "a"
                 ["end" "a"]
                 (fn [agent-node arg1 arg2 arg3]
                   (aor/emit! agent-node "end" (str arg1 "-" arg2 "-" arg3))
                   (aor/emit! agent-node "a" (str arg1 "-" arg3))
                   (aor/emit! agent-node "end" (str arg1 "!"))
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
         (bind datasets
           (foreign-pstate ipc module-name (po/datasets-task-global-name)))
         (bind search
           (foreign-query ipc module-name (queries/search-experiments-name)))
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
         (aor/create-evaluator! manager "cmin" "compare-min" {} "")
         (aor/create-evaluator! manager "cmax" "compare-min" {} "")

         (aor/create-evaluator! ds-manager "rc3" "aor/conciseness" {"threshold" "3"} "")

         (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))
         (bind ds-id2 (aor/create-dataset! manager "Dataset 2"))
         (bind remote-ds (aor/create-dataset! ds-manager "Dataset 3"))

         (bind add-example-and-wait!
           (fn [& args]
             (Thread/sleep 2)
             (apply aor/add-dataset-example! args)))

         (add-example-and-wait!
          manager
          ds-id1
          "abcdefg"
          {:reference-output "aaaaaaaaaaa"
           :tags #{"tag1" "tag2"}})
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
          {"a" 1 "b" "abc"}
          {:reference-output "1234567"})
         (add-example-and-wait!
          ds-manager
          remote-ds
          {"a" 2 "b" "123456789"})
         (add-example-and-wait!
          ds-manager
          remote-ds
          {"a" 3 "b" "."})

         (bind exp1 (h/random-uuid7))
         (bind {exp-invoke aor-types/AGENTS-TOPOLOGY-NAME}
           (foreign-append!
            global-actions-depot
            (aor-types/->valid-StartExperiment
             exp1
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
         (bind res (foreign-invoke-query results ds-id1 exp1))
         (is (aor-types/StartExperiment? (:experiment-info res)))
         (is (> (:finish-time-millis res) (:start-time-millis res)))
         (is (aor-types/AgentInvokeImpl? (:experiment-invoke res)))
         (is (= 2 (count @example-id-chunks-atom)))
         (is (every? #(= 4 %) @example-id-chunks-atom))

         (is
          (trace-matches?
           res
           {:summary-evals {"mycount" {"res" 8} "mysum" {"res" 110}}
            :summary-eval-failures nil
            :results
            {0
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" true}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}
             1
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 6} "concise2" {"concise?" true}}
              :input            "ab"
              :reference-output ".."}
             2
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "100" :failure? false}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" false}}
              :input            "123456789abcdefg"
              :reference-output "."}
             3
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 9} "concise2" {"concise?" true}}
              :input            "aa"
              :reference-output "bbbbb"}
             4
             {:example-id       !eid0
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" true}}
              :input            "abcdefg"
              :reference-output "aaaaaaaaaaa"}
             5
             {:example-id       !eid1
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 6} "concise2" {"concise?" true}}
              :input            "ab"
              :reference-output ".."}
             6
             {:example-id       !eid2
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "100" :failure? false}}
              :evals            {"mylen" {"len" 20} "concise2" {"concise?" false}}
              :input            "123456789abcdefg"
              :reference-output "."}
             7
             {:example-id       !eid3
              :agent-initiates
              {0
               {:agent-name "foo"}}
              :agent-results    {0 {:val "50" :failure? false}}
              :evals            {"mylen" {"len" 9} "concise2" {"concise?" true}}
              :input            "aa"
              :reference-output "bbbbb"}
            }}))

         (is (every? aor-types/AgentInvokeImpl?
                     (select [:results MAP-VALS :agent-initiates MAP-VALS :agent-invoke] res)))


         ;; TODO: <<<<>>>>
         ;;  - do comparative experiment between node and agent on remote dataset
         ;;   - need evals with custom path to parse the outputs...
         ;;     - need same output structure between agent and node

         ; (clojure.pprint/pprint res)

         ;; TODO: <<<<>>>>
         ;;  - create local and remote datasets
         ;;    - remote one shouldn't have any agents
         ;;  - more complicated input->args
         ;;     - [{"a": "$.a", "b": "$$.a"}, "abc"]
         ;;  - test experiment with node
         ;;     - comparative with mixed
         ;;     - regular experiment
         ;;     - node failure propagates correctly
         ;;  - test all different selection types:
         ;;    - specific snapshot
         ;;    - specific example IDs
         ;;    - specific tag
         ;;  - test remote evaluators
         ;;  - running regular experiment with comparative evaluator
         ;;  - running comparative experiment with regular or summary evaluator

        )))))

(deftest failures-test
         ;; TODO: <<<<>>>>>
         ;;  - agent failures
         ;;  - node execution failures
         ;;  - regular, comparative, and summary eval failures
)

(deftest experimenter-agent-failures-test
         ;; TODO: <<<<>>>>
         ;; - doesn't retry things it succeeded on after failures
         ;;   - agent/node invokes
         ;;   - regular/comparative evaluators
         ;;   - summary evaluators
         ;; - put hook in to cause failure of the experimenter agent in all of those nodes after at
         ;; least one has succeeded
)

(deftest search-experiments-test
         ;; TODO: <<<<>>>>
         ;;  - test search and all filter types
         ;;     - easier in another test with experiments that run without any examples

)
