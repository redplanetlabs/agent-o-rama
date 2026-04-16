(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-metrics!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:human-feedback/metrics module-id]])})

(rpcq/defrpc-query ::get-queues!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:human-feedback/queues module-id]])})

(rpcq/defrpc-query ::get-queue-info!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-info module-id queue-name]])})

(rpcq/defrpc-query ::get-queue-items!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id queue-name]}]
           [[:human-feedback/queue-items module-id queue-name]])})

(rpcq/defrpc-query ::get-metrics-inf!!
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
           [[:human-feedback/metrics module-id]])})

(rpcq/defrpc-query ::get-queues-inf!!
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
           [[:human-feedback/queues module-id]])})
