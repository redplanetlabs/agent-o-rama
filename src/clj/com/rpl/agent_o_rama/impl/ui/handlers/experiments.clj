(ns com.rpl.agent-o-rama.impl.ui.handlers.experiments
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:use [com.rpl.rama])
  (:import [java.util UUID]))

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
      nil))) ; :all case

(defn- parse-spec [spec]
  (let [parse-target (fn [t]
                       (let [target-spec (:target-spec t)
                             type (:type target-spec)]
                         (aor-types/->ExperimentTarget
                          (if (= type "AgentTarget")
                            (aor-types/->AgentTarget (:agent-name target-spec))
                            (aor-types/->NodeTarget (:agent-name target-spec) (:node target-spec)))
                          (:input->args t))))]
    ;; The :type value will be a keyword on the backend, but a string from the UI
    (if (= (get spec "type") "RegularExperiment")
      (aor-types/->RegularExperiment (parse-target (first (get spec "targets"))))
      (aor-types/->ComparativeExperiment (mapv parse-target (get spec "targets"))))))

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
                            snapshot
                            (parse-selector selector)
                            (mapv #(aor-types/->EvaluatorSelector (:name %) (:remote? %)) evaluators)
                            (parse-spec spec)
                            (long num-repetitions)
                            (long concurrency)))]
      {:status :ok :invoke-id (:agent-invoke-id agent-invoke)})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-results
  [{:keys [manager dataset-id experiment-id]} uid]
  (let [results-query (:experiments-results-query (aor-types/underlying-objects manager))]
    (foreign-invoke-query results-query
                          dataset-id
                          experiment-id)))
