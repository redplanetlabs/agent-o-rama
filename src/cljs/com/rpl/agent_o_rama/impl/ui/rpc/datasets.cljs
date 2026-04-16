(ns com.rpl.agent-o-rama.impl.ui.rpc.datasets
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

;; upsert!! is a mutation — called directly via rfq/reg-mutation or rpc/call
;; (no reg-query needed)

(rpcq/defrpc-query ::get-all!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:datasets module-id]])})

(rpcq/defrpc-query ::get-props!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id dataset-id]}]
           [[:dataset-props module-id dataset-id]])})

(rpcq/defrpc-query ::get-snapshot-names!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id dataset-id]}]
           [[:snapshot-names module-id dataset-id]])})

(rpcq/defrpc-query ::search-examples!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id dataset-id snapshot-name]}]
           ;; Broad tag matches mutation invalidations that omit snapshot
           [[:dataset-examples module-id dataset-id]
            [:dataset-examples module-id dataset-id snapshot-name]])})

(rpcq/defrpc-query ::fetch-example!!
  {:stale-time-ms 0
   :polling-interval-ms 500
   :tags (fn [{:keys [module-id dataset-id example-id]}]
           [[:fetch-example module-id dataset-id example-id]])})

(rpcq/defrpc-query ::get-example!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id dataset-id example-id]}]
           [[:fetch-example module-id dataset-id example-id]])})

(rpcq/defrpc-query ::validate-direct-data!!
  {:stale-time-ms 0})

(rpcq/defrpc-query ::preview-expression!!
  {:stale-time-ms 0})

(rpcq/defrpc-query ::get-all-inf!!
  {:query-fn (fn [params]
               (let [{:keys [cursor]} params
                     base (dissoc params :cursor)]
                 {:rpc/id ::get-all!!
                  :payload (cond-> base
                           cursor (assoc :pagination cursor))}))
   :stale-time-ms 0
   :transform-response (fn [page params]
                         (if page
                           (let [items (or (:datasets page) [])]
                             {:items items
                              :pagination-params (:pagination-params page)
                              :full-page? (common/full-page-of-items? items (:limit params))})
                           {:items [] :pagination-params nil :full-page? false}))
   :infinite {:initial-cursor nil
              :get-next-cursor (fn [page]
                                 (when (and (seq (:items page)) (:full-page? page))
                                   (common/pagination-cursor-for-next-page (:pagination-params page))))}
   :tags (fn [{:keys [module-id]}]
           [[:datasets module-id]])})

(rpcq/defrpc-query ::search-examples-inf!!
  {:query-fn (fn [params]
               (let [{:keys [cursor]} params
                     base (dissoc params :cursor)]
                 {:rpc/id ::search-examples!!
                  :payload (cond-> base
                           cursor (assoc :pagination cursor))}))
   :stale-time-ms 0
   :transform-response (fn [page params]
                         (if page
                           (let [items (or (:examples page) [])]
                             {:items items
                              :pagination-params (:pagination-params page)
                              :full-page? (common/full-page-of-items? items (:limit params))})
                           {:items [] :pagination-params nil :full-page? false}))
   :infinite {:initial-cursor nil
              :get-next-cursor (fn [page]
                                 (when (and (seq (:items page)) (:full-page? page))
                                   (common/pagination-cursor-for-next-page (:pagination-params page))))}
   :tags (fn [{:keys [module-id dataset-id snapshot-name]}]
           [[:dataset-examples module-id dataset-id]
            [:dataset-examples module-id dataset-id snapshot-name]])})
