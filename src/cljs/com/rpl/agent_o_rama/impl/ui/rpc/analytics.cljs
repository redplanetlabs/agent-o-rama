(ns com.rpl.agent-o-rama.impl.ui.rpc.analytics
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::fetch-rules!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-rules!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/rules module-id agent-name]])})

(rfq/reg-query
 ::all-action-builders!!
 {:query-fn (fn [params]
               {:rpc/id ::all-action-builders!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:analytics/action-builders module-id]])})

(rfq/reg-query
 ::fetch-action-log!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-action-log!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name rule-name]}]
           [[:analytics/action-log module-id agent-name rule-name]])})

(rfq/reg-query
 ::search-metadata!!
 {:query-fn (fn [params]
               {:rpc/id ::search-metadata!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/metadata module-id agent-name]])})

(rfq/reg-query
 ::fetch-telemetry!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-telemetry!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name metric-id granularity]}]
           [[:analytics/telemetry module-id agent-name metric-id granularity]])})

(rfq/reg-query
 ::fetch-all-metrics!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-all-metrics!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/all-metrics module-id agent-name]])})
