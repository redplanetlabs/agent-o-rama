(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
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

(def ^:export _q5
  (rfq/reg-query
   ::get-metrics-inf!!
   {:query-fn (fn [params]
                (let [{:keys [cursor]} params
                      base (dissoc params :cursor)]
                  {:rpc/id ::get-metrics!!
                   :payload (cond-> base
                              cursor (assoc :pagination cursor))}))
    :stale-time-ms 0
    :transform-response (fn [page params]
                          (if page
                            (let [items (or (:items page) [])]
                              {:items items
                               :pagination-params (:pagination-params page)
                               :full-page? (common/full-page-of-items? items (:limit params))})
                            {:items [] :pagination-params nil :full-page? false}))
    :infinite {:initial-cursor nil
               :get-next-cursor (fn [page]
                                  (when (and (seq (:items page)) (:full-page? page))
                                    (common/pagination-cursor-for-next-page (:pagination-params page))))}
    :tags (fn [{:keys [module-id]}]
            [[:human-feedback/metrics module-id]])}))

(def ^:export _q6
  (rfq/reg-query
   ::get-queues-inf!!
   {:query-fn (fn [params]
                (let [{:keys [cursor]} params
                      base (dissoc params :cursor)]
                  {:rpc/id ::get-queues!!
                   :payload (cond-> base
                              cursor (assoc :pagination cursor))}))
    :stale-time-ms 0
    :transform-response (fn [page params]
                          (if page
                            (let [items (or (:items page) [])]
                              {:items items
                               :pagination-params (:pagination-params page)
                               :full-page? (common/full-page-of-items? items (:limit params))})
                            {:items [] :pagination-params nil :full-page? false}))
    :infinite {:initial-cursor nil
               :get-next-cursor (fn [page]
                                  (when (and (seq (:items page)) (:full-page? page))
                                    (common/pagination-cursor-for-next-page (:pagination-params page))))}
    :tags (fn [{:keys [module-id]}]
            [[:human-feedback/queues module-id]])}))
