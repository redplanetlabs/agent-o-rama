(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [re-frame.query :as rfq]))

(def ^:export _q1
  (rfq/reg-query
 ::get-metrics!!
 {:query-fn (fn [params]
               {:rpc/id ::get-metrics!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:human-feedback/metrics module-id]])}))

(def ^:export _q2
  (rfq/reg-query
 ::get-queues!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queues!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:human-feedback/queues module-id]])}))

(def ^:export _q3
  (rfq/reg-query
 ::get-queue-info!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queue-info!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-info module-id queue-name]])}))

(def ^:export _q4
  (rfq/reg-query
 ::get-queue-items!!
 {:query-fn (fn [params]
               {:rpc/id ::get-queue-items!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-items module-id queue-name]])}))
