(ns com.rpl.agent-o-rama.impl.ui.rpc.datasets
  (:require
   [re-frame.query :as rfq]))

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
             [[:dataset-examples module-id dataset-id snapshot-name]])}))

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
