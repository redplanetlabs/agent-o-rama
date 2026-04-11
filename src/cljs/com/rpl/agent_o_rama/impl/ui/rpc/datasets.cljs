(ns com.rpl.agent-o-rama.impl.ui.rpc.datasets
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq]))

;; upsert!! is a mutation — called directly via rfq/reg-mutation or rpc/call
;; (no reg-query needed)

(def ^:export q-get-all
  (rfq/reg-query
   ::get-all!!
   {:query-fn (fn [params]
                 {:rpc/id ::get-all!!
                  :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id]}]
             [[:datasets module-id]])}))

(def ^:export q-get-props
  (rfq/reg-query
   ::get-props!!
   {:query-fn (fn [params]
                 {:rpc/id ::get-props!!
                  :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id dataset-id]}]
             [[:dataset-props module-id dataset-id]])}))

(def ^:export q-get-snapshot-names
  (rfq/reg-query
   ::get-snapshot-names!!
   {:query-fn (fn [params]
                 {:rpc/id ::get-snapshot-names!!
                  :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id dataset-id]}]
             [[:snapshot-names module-id dataset-id]])}))

(def ^:export q-search-examples
  (rfq/reg-query
   ::search-examples!!
   {:query-fn (fn [params]
                 {:rpc/id ::search-examples!!
                  :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id dataset-id snapshot-name]}]
            ;; Broad tag matches mutation invalidations that omit snapshot
            [[:dataset-examples module-id dataset-id]
             [:dataset-examples module-id dataset-id snapshot-name]])}))

(def ^:export q-fetch-example
  (rfq/reg-query
   ::fetch-example!!
   {:query-fn (fn [params]
                 {:rpc/id ::fetch-example!!
                  :payload params})
    :stale-time-ms 0
    :polling-interval-ms 500
    :tags (fn [{:keys [module-id dataset-id example-id]}]
             [[:fetch-example module-id dataset-id example-id]])}))

(def ^:export q-validate-direct-data
  (rfq/reg-query
   ::validate-direct-data!!
   {:query-fn (fn [params]
                 {:rpc/id ::validate-direct-data!!
                  :payload params})
    :stale-time-ms 0}))

(def ^:export q-preview-expression
  (rfq/reg-query
   ::preview-expression!!
   {:query-fn (fn [params]
                 {:rpc/id ::preview-expression!!
                  :payload params})
    :stale-time-ms 0}))

(def ^:export q-get-all-inf
  (rfq/reg-query
   ::get-all-inf!!
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
            [[:datasets module-id]])}))

(def ^:export q-search-examples-inf
  (rfq/reg-query
   ::search-examples-inf!!
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
             [:dataset-examples module-id dataset-id snapshot-name]])}))
