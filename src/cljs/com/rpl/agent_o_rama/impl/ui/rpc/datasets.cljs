(ns com.rpl.agent-o-rama.impl.ui.rpc.datasets
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::get-all!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:datasets module-id]])})

(rfq/reg-query
 ::get-props!!
 {:query-fn (fn [params]
               {:rpc/id ::get-props!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id dataset-id]}]
           [[:dataset-props module-id dataset-id]])})

(rfq/reg-query
 ::get-snapshot-names!!
 {:query-fn (fn [params]
               {:rpc/id ::get-snapshot-names!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id dataset-id]}]
           [[:snapshot-names module-id dataset-id]])})

(rfq/reg-query
 ::search-examples!!
 {:query-fn (fn [params]
               {:rpc/id ::search-examples!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id dataset-id snapshot-name]}]
           [[:dataset-examples module-id dataset-id snapshot-name]])})

(rfq/reg-query
 ::fetch-example!!
 {:query-fn (fn [params]
               {:rpc/id ::fetch-example!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id dataset-id example-id]}]
           [[:fetch-example module-id dataset-id example-id]])})

(rfq/reg-query
 ::validate-direct-data!!
 {:query-fn (fn [params]
               {:rpc/id ::validate-direct-data!!
                :payload params})
  :stale-time-ms 0})

(rfq/reg-query
 ::preview-expression!!
 {:query-fn (fn [params]
               {:rpc/id ::preview-expression!!
                :payload params})
  :stale-time-ms 0})
