(ns com.rpl.agent-o-rama.ui.experiments-datetime-filter-test-agent
  "Test agent module for experiments datetime filter E2E tests.

  Provides a simple agent and helper functions to:
  - Create datasets with examples
  - Run experiments with controlled timestamps using TopologyUtils/advanceSimTime
  - Test date range filters"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama :as rama])
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

;;; Constants for simulated time
;;; We'll create experiments at these simulated timestamps (in milliseconds)

(def ^:const ONE-DAY-MS (* 24 60 60 1000))

;;; Helper functions for test setup

(defn create-test-dataset!
  "Creates a dataset with some test examples.
   Returns the dataset-id."
  [manager]
  (let [dataset-id (aor/create-dataset! manager "Datetime Filter Test Dataset")]
    ;; Add some test examples
    (doseq [i (range 3)]
      (aor/add-dataset-example! manager dataset-id (str "input-" i)
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

;;; Main setup function for datetime filter testing with simulated time

(defn setup-datetime-filter-testing!
  "Sets up test data for datetime filter testing using simulated time.
   
   IMPORTANT: TopologyUtils/startSimTime must be called BEFORE launching the module.
   This is done via the :pre-launch-hook in the test fixture.

   This function creates:
   - An evaluator
   - A dataset with examples
   - Three experiments at different simulated times:
     - Experiment 1: time 0 (represents '3 days ago')
     - Experiment 2: after advancing 2 days (represents 'yesterday')  
     - Experiment 3: after advancing 1 more day (represents 'today')

   Returns a map with dataset-id, experiment info, and timestamps."
  [ipc module-name]
  (let [manager (aor/agent-manager ipc module-name)
        exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)
        global-actions-depot (rama/foreign-depot
                              ipc module-name
                              (po/global-actions-depot-name))

        ;; Create evaluator first
        _ (create-evaluator! manager)

        ;; Create dataset
        dataset-id (create-test-dataset! manager)

        ;; Small sleep to let microbatch process examples
        ;; (same pattern as search-experiments-test uses)
        _ (Thread/sleep 100)

        ;; Capture initial sim time (time 0)
        time-0 (System/currentTimeMillis)

        ;; Run experiment 1 at sim time 0 ("3 days ago")
        exp1 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from 3 days ago")

        ;; Advance sim time by 2 days and run experiment 2 ("yesterday")
        _ (TopologyUtils/advanceSimTime (* 2 ONE-DAY-MS))
        time-2-days (System/currentTimeMillis)
        exp2 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from yesterday")

        ;; Advance sim time by 1 more day and run experiment 3 ("today")
        _ (TopologyUtils/advanceSimTime ONE-DAY-MS)
        time-3-days (System/currentTimeMillis)
        exp3 (run-experiment! manager exp-client global-actions-depot
                              dataset-id "Experiment from today")]

    {:dataset-id dataset-id
     :experiments [{:id (:experiment-id exp1)
                    :name "Experiment from 3 days ago"
                    :timestamp time-0}
                   {:id (:experiment-id exp2)
                    :name "Experiment from yesterday"
                    :timestamp time-2-days}
                   {:id (:experiment-id exp3)
                    :name "Experiment from today"
                    :timestamp time-3-days}]
     ;; Return timestamps for filter assertions
     :timestamps {:three-days-ago time-0
                  :yesterday time-2-days
                  :today time-3-days}}))
