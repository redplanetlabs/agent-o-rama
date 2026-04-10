(ns com.rpl.agent-o-rama.impl.ui.rpc.experiments
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

(defn get-results!!
  [{:keys [manager dataset-id experiment-id]} _uid]
  (let [results-query (:experiments-results-query (aor-types/underlying-objects manager))
        base-results (foreign-invoke-query results-query
                                           dataset-id
                                           experiment-id)]
    (if-let [invoke (:experiment-invoke base-results)]
      (do
        (with-open [exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)]
          (if (aor/agent-invoke-complete? exp-client invoke)
            (let [result (try (aor/agent-result exp-client invoke)
                              (catch Exception e
                                (Throwable->map e)))]
              (if (not= :done result)
                (assoc base-results :invocation-error result)
                base-results))
            base-results)))
      base-results)))
