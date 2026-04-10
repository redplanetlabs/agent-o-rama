(ns com.rpl.agent-o-rama.impl.ui.rpc.experiment-list
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::get-all-for-dataset!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all-for-dataset!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 2000
  :tags (fn [{:keys [module-id dataset-id]}]
           [[:experiments module-id dataset-id]])})
