(ns com.rpl.agent-o-rama.impl.ui.rpc.experiment-list
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [clojure.string :as str])
  (:use [com.rpl.rama]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-all-for-dataset!!
  [system {:keys [module-id dataset-id pagination filters]}]
  (let [manager (get-manager system module-id)
        search-query (:search-experiments-query (aor-types/underlying-objects manager))
        keyword->pred (fn [k]
                        (case k
                          :>= >=
                          :<= <=
                          :< <
                          :> >
                          k))
        processed-filters (cond-> filters
                            (:type filters)
                            (assoc :type (case (:type filters)
                                           :regular com.rpl.agent_o_rama.impl.types.RegularExperiment
                                           :comparative com.rpl.agent_o_rama.impl.types.ComparativeExperiment
                                           nil))
                            (:times filters)
                            (assoc :times (mapv (fn [time-spec]
                                                  (update time-spec :pred keyword->pred))
                                                (:times filters))))]
    (foreign-invoke-query search-query
                          dataset-id
                          (or processed-filters {})
                          20
                          pagination)))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn- parse-selector [selector]
  (when selector
    (case (:type selector)
      :tag (aor-types/->TagSelector (:tag selector))
      :example-ids (aor-types/->ExampleIdsSelector (:example-ids selector))
      nil)))

(defn- parse-target [t]
  (let [target-spec (:target-spec t)
        type (:type target-spec)
        metadata (get t :metadata {})]
    (aor-types/->ExperimentTarget
     (if (= type :agent)
       (aor-types/->AgentTarget (:agent-name target-spec))
       (aor-types/->NodeTarget (:agent-name target-spec) (:node target-spec)))
     metadata
     (:input->args t))))

(defn- parse-spec [spec]
  (if (= (get spec :type) :regular)
    (aor-types/->RegularExperiment (parse-target (first (get spec :targets))))
    (aor-types/->ComparativeExperiment (mapv parse-target (get spec :targets)))))

(defn start!!
  [system {:keys [module-id dataset-id form-data]}]
  (let [manager (get-manager system module-id)
        global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))
        {:keys [name snapshot selector evaluators spec num-repetitions concurrency]} form-data
        experiment-id (h/random-uuid7)]
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
                      (long concurrency)))
    {:status :ok :experiment-id (str experiment-id)}))

(defn delete!!
  [system {:keys [module-id dataset-id experiment-id]}]
  (let [manager (get-manager system module-id)
        global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]
    (foreign-append! global-actions-depot
                     (aor-types/->DeleteExperiment experiment-id dataset-id))
    {:status :ok}))
