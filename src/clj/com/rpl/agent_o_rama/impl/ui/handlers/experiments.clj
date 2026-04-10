(ns com.rpl.agent-o-rama.impl.ui.handlers.experiments
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:use [com.rpl.rama]))


(defn- parse-selector [selector]
  (when selector
    (case (:type selector)
      :tag (aor-types/->TagSelector (:tag selector))
      :example-ids (aor-types/->ExampleIdsSelector
                    (:example-ids selector))
      nil)))

(defn- parse-target
  [t]
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


(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/delete
  [{:keys [manager dataset-id experiment-id]} uid]
  (let [global-actions-depot (:global-actions-depot (aor-types/underlying-objects manager))]
    (foreign-append! global-actions-depot
                     (aor-types/->DeleteExperiment experiment-id dataset-id))
    {:status :ok}))
