(ns com.rpl.agent-o-rama.impl.ui.rpc.experiments
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:use [com.rpl.rama]))

(defn get-results!!
  [system {:keys [module-id dataset-id experiment-id]}]
  (let [manager (get-in system [:aor-cache module-id :manager])
        exp-client (get-in system [:aor-cache module-id :clients aor-types/EVALUATOR-AGENT-NAME])
        results-query (:experiments-results-query (aor-types/underlying-objects manager))
        base-results (foreign-invoke-query results-query
                                           dataset-id
                                           experiment-id)]
    (if-let [invoke (:experiment-invoke base-results)]
      (if (aor/agent-invoke-complete? exp-client invoke)
        (let [result (try (aor/agent-result exp-client invoke)
                          (catch Exception e
                            (Throwable->map e)))]
          (if (= :done result)
            base-results
            (assoc base-results :invocation-error result)))
        base-results)
      base-results)))
