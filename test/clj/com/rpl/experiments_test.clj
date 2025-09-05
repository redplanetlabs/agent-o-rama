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
   [com.rpl.test-common :as tc]))


(defn count-or-num
  [v]
  (if (string? v) (count v) v))


(defn wait-experiment-finished
  [exp-client agent-invoke]
  (let [res (aor/agent-result exp-client agent-invoke)]
    (when-not (= res :done)
      (throw (h/ex-info "Experiment failed" {:res res})))))

(deftest basic-experiments-test
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
             "end"
             (fn [agent-node input]
               (let [v (count-or-num input)]
                 (aor/emit! agent-node "end" :ignore v)
                 (aor/emit! agent-node "end" :keep (inc v))
                 (aor/emit! agent-node "end" :ignore v))))
            (aor/node
             "end"
             nil
             (fn [agent-node command input]
               (when (= command :keep)
                 (aor/result!
                  agent-node
                  (cond (= 0 input)
                        "100"

                        (= 1 input)
                        "10"

                        :else
                        "50"))))))
       ))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind manager (aor/agent-manager ipc module-name))
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
                            "concise5"
                            "aor/conciseness"
                            {"threshold" "5"}
                            "")
     (aor/create-evaluator! manager "mylen" "len" {} "")
     (aor/create-evaluator! manager "mysum" "sum-sizes" {} "")
     (aor/create-evaluator! manager "mycount" "count" {} "")
     (aor/create-evaluator! manager "cmin" "compare-min" {} "")
     (aor/create-evaluator! manager "cmax" "compare-min" {} "")

     (bind ds-id1 (aor/create-dataset! manager "Dataset 1"))
     (bind ds-id2 (aor/create-dataset! manager "Dataset 2"))

     (aor/add-dataset-example!
      manager
      ds-id1
      "abcdefg"
      {:reference-output "aaaaaaaaaaa"
       :tags #{"tag1" "tag2"}})
     (aor/add-dataset-example!
      manager
      ds-id1
      "ab"
      {:reference-output ".."
       :tags #{"tag1"}})
     (aor/add-dataset-example!
      manager
      ds-id1
      "123456789abcdefg"
      {:reference-output "."
       :tags #{"tag1"}})
     (aor/add-dataset-example!
      manager
      ds-id1
      "aa"
      {:reference-output "bbbbb"})

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
          (aor-types/->valid-EvaluatorSelector "concise5" false)
          (aor-types/->valid-EvaluatorSelector "mycount" false)
          (aor-types/->valid-EvaluatorSelector "mysum" false)]
         (aor-types/->valid-RegularExperiment
          (aor-types/->valid-ExperimentTarget
           (aor-types/->valid-AgentTarget "foo")
           ["$"]
          ))
         1
         2)))


     (wait-experiment-finished exp-client exp-invoke)
     (clojure.pprint/pprint
      (foreign-invoke-query results ds-id1 exp1))

     ;; TODO: <<<<>>>> wait for experiment to be done by checking for finish-time-millis
     ;;  - check experiment results with query topology

     ;; TODO: <<<<>>>>
     ;;  - create local and remote datasets
     ;;    - remote one sholdn't have any agents
     ;;  - test concurrency and num-repetitions
     ;;  - test search and all filter types
     ;;  - test experiment with node
     ;;     - comparative with mixed
     ;;     - regular experiment
     ;;     - node failure propagates correctly
     ;;  - test all different selection types:
     ;;    - specific snapshot
     ;;    - specific example IDs
     ;;    - specific tag
     ;;  - test remote evaluators

    )))

(deftest failures-test
         ;; TODO: <<<<>>>>>
         ;;  - agent failures
         ;;  - regular, comparative, and summary eval failures
)
