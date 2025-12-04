(ns com.rpl.agent-o-rama.ui.experiments-datetime-filter-test-agent
  "Test agent module for experiments datetime filter E2E tests.

  Provides a simple agent and helper functions to:
  - Create datasets with examples
  - Run experiments with controlled timestamps using TopologyUtils/advanceSimTime
  - Test date range filters"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.rama.helpers TopologyUtils]))

;;; Test evaluator

(defn simple-length-evaluator
  "Evaluator that returns length of output"
  [_params]
  (fn [_fetcher _input _ref-output output]
    {"length" (if (string? output) (count output) 0)}))

;;; Test agent implementation

(defn simple-test-agent-impl
  "Simple agent that returns a predictable result"
  [agent-node input]
  (aor/result! agent-node (str "result-" input)))

;;; Test agent module

(aor/defagentmodule ExperimentsDatetimeFilterTestAgentModule
  [topology]

  ;; Declare evaluator builder
  (aor/declare-evaluator-builder
   topology
   "simple-length"
   "Evaluator that returns the length of the output"
   simple-length-evaluator)

  ;; Main test agent
  (-> topology
      (aor/new-agent "DatetimeFilterTestAgent")
      (aor/node
       "process"
       nil
       simple-test-agent-impl)))

;;; Helper functions for test setup

(defn create-test-dataset!
  "Creates a dataset with some test examples.
   Returns the dataset-id."
  [manager]
  (let [dataset-id (aor/create-dataset! manager "Datetime Filter Test Dataset")]
    ;; Add some test examples
    (doseq [i (range 3)]
      (aor/add-example! manager dataset-id (str "input-" i)
                        {:reference-output (str "expected-" i)}))
    dataset-id))

(defn create-evaluator!
  "Creates a simple evaluator for experiments."
  [manager]
  (aor/create-evaluator! manager
                         "test-length-eval"
                         "simple-length"
                         {}
                         "Test length evaluator"))

(defn run-experiment!
  "Runs an experiment and waits for it to complete.
   Returns {:experiment-id <uuid> :invoke <AgentInvoke>}"
  [manager exp-client global-actions-depot dataset-id experiment-name]
  (let [exp-id (h/random-uuid7)
        {exp-invoke aor-types/AGENT-TOPOLOGY-NAME}
        (rama/foreign-append!
         global-actions-depot
         (aor-types/->valid-StartExperiment
          exp-id
          experiment-name
          dataset-id
          nil ; snapshot
          nil ; selector
          [(aor-types/->valid-EvaluatorSelector "test-length-eval" false)]
          (aor-types/->valid-RegularExperiment
           (aor-types/->valid-ExperimentTarget
            (aor-types/->valid-AgentTarget "DatetimeFilterTestAgent")
            {} ; metadata
            nil)) ; input->args
          1 ; num-repetitions
          1))] ; concurrency

    ;; Wait for experiment to complete
    (aor/agent-result exp-client exp-invoke)

    {:experiment-id exp-id
     :invoke exp-invoke}))

;;; Constants for simulated time
;;; We'll create experiments at these simulated timestamps (in milliseconds)

(def ^:const ONE-DAY-MS (* 24 60 60 1000))
(def ^:const THREE-DAYS-AGO-SIM 0)              ;; Simulated: 3 days ago
(def ^:const ONE-DAY-AGO-SIM (* 2 ONE-DAY-MS))  ;; Simulated: 1 day ago (after 2 days advance)
(def ^:const TODAY-SIM (* 3 ONE-DAY-MS))        ;; Simulated: today (after 3 days advance)

(defn days-ago-sim-millis
  "Returns the simulated timestamp for n days ago.
   Based on the pattern: experiments are created at 0, 2 days, 3 days sim time."
  [n]
  (case n
    3 THREE-DAYS-AGO-SIM
    1 ONE-DAY-AGO-SIM
    0 TODAY-SIM
    ;; For other values, calculate relative to 'today'
    (- TODAY-SIM (* n ONE-DAY-MS))))

;;; Post-deploy hook for setting up test data

(defn setup-datetime-filter-testing!
  "Sets up test data for datetime filter testing.
   
   Uses TopologyUtils/advanceSimTime to create experiments at different
   simulated timestamps, allowing the date filter to be tested properly.

   Creates:
   - A dataset with examples
   - An evaluator
   - Three experiments at different simulated times:
     - Experiment 1: time 0 (represents '3 days ago')
     - Experiment 2: time 2 days (represents 'yesterday')
     - Experiment 3: time 3 days (represents 'today')

   Returns a map with all the IDs and timestamps for use in tests."
  [ipc module-name]
  ;; Start simulated time at 0
  (TopologyUtils/startSimTime)

  (let [manager (aor/agent-manager ipc module-name)
        exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)
        global-actions-depot (rama/foreign-depot
                              ipc module-name
                              (str "$$" aor-types/AGENT-TOPOLOGY-NAME "_global-actions"))

        ;; Create evaluator first
        _ (create-evaluator! manager)

        ;; Create dataset
        dataset-id (create-test-dataset! manager)

        ;; Wait for examples to be processed
        _ (rtest/wait-for-microbatch-processed-count
           ipc module-name
           aor-types/AGENT-TOPOLOGY-NAME
           3)

        ;; Run experiment 1 at sim time 0 ("3 days ago")
        exp1 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from 3 days ago")

        ;; Advance sim time by 2 days and run experiment 2 ("yesterday")
        _ (TopologyUtils/advanceSimTime (* 2 ONE-DAY-MS))
        exp2 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from yesterday")

        ;; Advance sim time by 1 more day and run experiment 3 ("today")
        _ (TopologyUtils/advanceSimTime ONE-DAY-MS)
        exp3 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from today")]

    {:dataset-id dataset-id
     :experiments [{:id (:experiment-id exp1)
                    :name "Experiment from 3 days ago"
                    :sim-timestamp THREE-DAYS-AGO-SIM}
                   {:id (:experiment-id exp2)
                    :name "Experiment from yesterday"
                    :sim-timestamp ONE-DAY-AGO-SIM}
                   {:id (:experiment-id exp3)
                    :name "Experiment from today"
                    :sim-timestamp TODAY-SIM}]
     ;; For the date filter, we need to know the simulated timestamps
     ;; to construct proper filter predicates
     :filter-timestamps {:three-days-ago THREE-DAYS-AGO-SIM
                         :two-days-ago ONE-DAY-MS
                         :yesterday ONE-DAY-AGO-SIM
                         :today TODAY-SIM}}))

(defn make-post-deploy-hook
  "Creates a post-deploy hook that sets up datetime filter testing."
  []
  (fn [ipc module-name]
    (setup-datetime-filter-testing! ipc module-name)))
