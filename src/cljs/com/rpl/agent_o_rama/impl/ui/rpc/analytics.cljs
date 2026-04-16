(ns com.rpl.agent-o-rama.impl.ui.rpc.analytics
  (:require
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::fetch-rules!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/rules module-id agent-name]])})

(rpcq/defrpc-query ::all-action-builders!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:analytics/action-builders module-id]])})

(rpcq/defrpc-query ::fetch-action-log!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name rule-name]}]
           [[:analytics/action-log module-id agent-name rule-name]])})

(rpcq/defrpc-query ::search-metadata!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/metadata module-id agent-name]])})

(rpcq/defrpc-query ::fetch-telemetry!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name metric-id granularity]}]
           [[:analytics/telemetry module-id agent-name metric-id granularity]])})

(rpcq/defrpc-query ::fetch-all-metrics!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name]}]
           [[:analytics/all-metrics module-id agent-name]])})
