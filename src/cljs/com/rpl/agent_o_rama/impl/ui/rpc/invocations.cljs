(ns com.rpl.agent-o-rama.impl.ui.rpc.invocations
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::get-page!!
 {:query-fn (fn [params]
               {:rpc/id ::get-page!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 2000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations module-id agent-name]])})

(rfq/reg-query
 ::get-filter-options!!
 {:query-fn (fn [params]
               {:rpc/id ::get-filter-options!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 30000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations/filter-options module-id agent-name]])})

(rfq/reg-query
 ::get-graph!!
 {:query-fn (fn [params]
               {:rpc/id ::get-graph!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 2000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:graph module-id agent-name]])})

(rfq/reg-query
 ::get-node-stats!!
 {:query-fn (fn [params]
               {:rpc/id ::get-node-stats!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 30000
  :tags (fn [{:keys [module-id agent-name granularity]}]
           [[:node-stats module-id agent-name granularity]])})
