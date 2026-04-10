(ns com.rpl.agent-o-rama.impl.ui.rpc.experiment-list
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
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
