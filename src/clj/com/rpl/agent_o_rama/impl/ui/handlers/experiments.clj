(ns com.rpl.agent-o-rama.impl.ui.handlers.experiments
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:use [com.rpl.rama])
  (:import
   [java.util
    UUID]
   [com.rpl.agentorama
    AgentFailedException]
   [com.rpl.agent_o_rama.impl.types
    RegularExperiment
    ComparativeExperiment]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-all-for-dataset
  [{:keys [manager dataset-id pagination filters]} uid]
  (let [search-query (:search-experiments-query (aor-types/underlying-objects manager))
        ;; Process filters to translate type keywords to classes
        processed-filters (if-let [type (:type filters)]
                            (assoc filters :type (case type
                                                   :regular RegularExperiment
                                                   :comparative ComparativeExperiment
                                                   nil)) ; Default to nil if unknown
                            filters)]
    ;; For the index table, we get the first page with a reasonable limit
    (foreign-invoke-query search-query
                          dataset-id
                          (or processed-filters {}) ; Use processed filters
                          20 ; limit
                          pagination)))

(defn- parse-selector [selector]
  (when selector
    (case (:type selector)
      :tag (aor-types/->TagSelector (:tag selector))
      :example-ids (aor-types/->ExampleIdsSelector
                    (:example-ids selector))
      nil)))

(defn- parse-target [t]
  (let [target-spec (:target-spec t)
        type (:type target-spec)]
    (aor-types/->ExperimentTarget
     (if (= type :agent)
       (aor-types/->AgentTarget (:agent-name target-spec))
       (aor-types/->NodeTarget (:agent-name target-spec) (:node target-spec)))
     (:input->args t))))

(defn- parse-spec [spec]
  (if (= (get spec :type) :regular)
    (aor-types/->RegularExperiment (parse-target (first (get spec :targets))))
    (aor-types/->ComparativeExperiment (mapv parse-target (get spec :targets)))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/start
  [{:keys [manager dataset-id form-data]} uid]
  (let [global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))
        {:keys [name snapshot selector evaluators spec num-repetitions concurrency]} form-data
        experiment-id (h/random-uuid7)]
    (let [{:keys [agent-invoke]}
          (foreign-append! global-actions-depot
                           (aor-types/->StartExperiment
                            experiment-id
                            name
                            dataset-id
                            (if (str/blank? snapshot) nil snapshot)
                            (parse-selector selector)
                            (mapv #(aor-types/->EvaluatorSelector (:name %) (:remote? %)) evaluators)
                            (parse-spec spec)
                            (long num-repetitions)
                            (long concurrency)))]
      {:status :ok :experiment-id (str experiment-id)})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-results
  [{:keys [manager dataset-id experiment-id]} uid]
  (let [results-query (:experiments-results-query (aor-types/underlying-objects manager))
        ;; 1. Fetch the base experiment data as before.
        base-results (foreign-invoke-query results-query
                                           dataset-id
                                           experiment-id)]


    ;; 2. NEW LOGIC STARTS HERE: Check for early failure.
    (if-let [invoke (:experiment-invoke base-results)]
      ;; If we have the invoke coordinates for the experimenter agent...
      (do
        (with-open [exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)]
          (if (aor/agent-invoke-complete? exp-client invoke)
            ;; If the agent is complete, fetch its result.
            (let [result (try (aor/agent-result exp-client invoke)
                              (catch Exception e
                                (Throwable->map e)))]
              ;; A successful run returns :done. Anything else is an error.
              (if (not= :done result)
                (assoc base-results :invocation-error result)
                base-results))
            ;; If the agent is not yet complete, just return the base results.
            base-results)))
      ;; If there are no invoke coordinates, it's too early, return base results.
      base-results)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/delete
  [{:keys [manager dataset-id experiment-id]} uid]
  (let [global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]
    (foreign-append! global-actions-depot
                     (aor-types/->DeleteExperiment experiment-id dataset-id))
    {:status :ok}))
