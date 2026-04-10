(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::get-metrics!!
 {:query-fn (fn [params]
               {:rpc/id ::get-metrics!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:human-feedback/metrics module-id]])})

(rfq/reg-query
 ::get-queues!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queues!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:human-feedback/queues module-id]])})

(rfq/reg-query
 ::get-queue-info!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queue-info!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-info module-id queue-name]])})

(rfq/reg-query
 ::get-queue-items!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queue-items!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-items module-id queue-name]])})
