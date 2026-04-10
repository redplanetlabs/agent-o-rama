 (ns com.rpl.agent-o-rama.impl.ui.rpc.experiments
   (:require
    [re-frame.query :as rfq]))

 (rfq/reg-query
  ::get-results!!
  {:query-fn (fn [params]
               {:rpc/id ::get-results!!
                :payload params})
   :stale-time-ms 0
   :polling-interval-ms 2000
   :tags (fn [{:keys [module-id dataset-id experiment-id]}]
           [[:experiment-results module-id dataset-id experiment-id]])})
