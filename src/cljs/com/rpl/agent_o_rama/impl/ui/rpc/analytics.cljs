(ns com.rpl.agent-o-rama.impl.ui.rpc.analytics
  (:require
   [re-frame.query :as rfq]))

(def ^:export _q1
  (rfq/reg-query
 ::fetch-rules!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-rules!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/rules module-id agent-name]])}))

(def ^:export _q2
  (rfq/reg-query
 ::all-action-builders!!
 {:query-fn (fn [params]
               {:rpc/id ::all-action-builders!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:analytics/action-builders module-id]])}))

(def ^:export _q3
  (rfq/reg-query
 ::fetch-action-log!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-action-log!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name rule-name]}]
           [[:analytics/action-log module-id agent-name rule-name]])}))

(def ^:export _q4
  (rfq/reg-query
 ::search-metadata!!
 {:query-fn (fn [params]
               {:rpc/id ::search-metadata!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/metadata module-id agent-name]])}))

(def ^:export _q5
  (rfq/reg-query
 ::fetch-telemetry!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-telemetry!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name metric-id granularity]}]
           [[:analytics/telemetry module-id agent-name metric-id granularity]])}))

(def ^:export _q6
  (rfq/reg-query
 ::fetch-all-metrics!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-all-metrics!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/all-metrics module-id agent-name]])}))
