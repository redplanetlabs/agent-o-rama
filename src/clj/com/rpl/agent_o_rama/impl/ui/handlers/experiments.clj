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
