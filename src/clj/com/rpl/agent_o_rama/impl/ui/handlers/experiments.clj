(ns com.rpl.agent-o-rama.impl.ui.handlers.experiments
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.experiments :as exp])
  (:use [com.rpl.rama])
  (:import [java.util UUID]
           [com.rpl.agentorama AgentFailedException]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-all-for-dataset
  [{:keys [manager dataset-id pagination]} uid]
  (let [search-query (:search-experiments-query (aor-types/underlying-objects manager))]
    ;; For the index table, we get the first page with a reasonable limit
    (foreign-invoke-query search-query
                          dataset-id
                          {} ; no filters
                          20 ; limit
                          pagination)))

(defn- parse-selector [selector]
  (when selector
    (case (:type selector)
      "tag" (aor-types/->TagSelector (:tag selector))
      "example-ids" (aor-types/->ExampleIdsSelector (mapv #(UUID/fromString %) (:example-ids selector)))
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
        {:keys [name snapshot selector evaluators spec num-repetitions concurrency]} form-data]
    (let [{:keys [agent-invoke]}
          (foreign-append! global-actions-depot
                           (aor-types/->StartExperiment
                            (h/random-uuid7)
                            name
                            dataset-id
                            (if (str/blank? snapshot) nil snapshot)
                            (parse-selector selector)
                            (mapv #(aor-types/->EvaluatorSelector (:name %) (:remote? %)) evaluators)
                            (parse-spec spec)
                            (long num-repetitions)
                            (long concurrency)))]
      {:status :ok :invoke-id (:agent-invoke-id agent-invoke)})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-results
  [{:keys [manager dataset-id experiment-id]} uid]
  (let [results-query (:experiments-results-query (aor-types/underlying-objects manager))
        ;; 1. Fetch the base experiment data as before.
        base-results (foreign-invoke-query results-query
                                           dataset-id
                                           ;; TODO move this uuid parse to client
                                           (java.util.UUID/fromString experiment-id))]

    ;; 2. NEW LOGIC STARTS HERE: Check for early failure.
    (if-let [invoke (:experiment-invoke base-results)]
      ;; If we have the invoke coordinates for the experimenter agent...
      (with-open [exp-client (aor/agent-client manager exp/EXPERIMENTER-NAME)]
        (if (aor/agent-invoke-complete? exp-client invoke)
          ;; If the agent is complete, fetch its result.
          (let [result (aor/agent-result exp-client invoke)]
            ;; A successful run returns :done. Anything else is an error.
            (if (not= :done result)
              (assoc base-results :invocation-error result)
              base-results))
          ;; If the agent is not yet complete, just return the base results.
          base-results))
      ;; If there are no invoke coordinates, it's too early, return base results.
      base-results))
  )
