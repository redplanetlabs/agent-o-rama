(ns com.rpl.agent-o-rama.impl.ui.rpc.invocations
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq]))

(def ^:export _q1
  (rfq/reg-query
 ::get-page!!
 {:query-fn (fn [params]
               {:rpc/id ::get-page!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 2000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations module-id agent-name]])}))

(def ^:export _q2
  (rfq/reg-query
 ::get-filter-options!!
 {:query-fn (fn [params]
               {:rpc/id ::get-filter-options!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 30000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations/filter-options module-id agent-name]])}))

(def ^:export _q3
  (rfq/reg-query
 ::get-graph!!
 {:query-fn (fn [params]
               {:rpc/id ::get-graph!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 2000
  :tags (fn [{:keys [module-id agent-name]}]
           [[:graph module-id agent-name]])}))

(def ^:export _q4
  (rfq/reg-query
 ::get-node-stats!!
 {:query-fn (fn [params]
               {:rpc/id ::get-node-stats!!
                :payload params})
  :stale-time-ms 0
  :polling-interval-ms 30000
  :tags (fn [{:keys [module-id agent-name granularity]}]
           [[:node-stats module-id agent-name granularity]])}))

(def ^:export _q-graph-page
  (rfq/reg-query
   ::get-graph-page!!
   {:query-fn (fn [params]
                {:rpc/id ::get-graph-page!!
                 :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id agent-name invoke-id]}]
            [[:invocation-graph-page module-id agent-name invoke-id]])}))

(def ^:export _q5
  (rfq/reg-query
   ::get-page-inf!!
   {:query-fn (fn [params]
                (let [{:keys [cursor]} params
                      base (dissoc params :cursor)]
                  {:rpc/id ::get-page!!
                   :payload (cond-> base
                              cursor (assoc :pagination cursor))}))
    :stale-time-ms 0
    :transform-response (fn [page params]
                          (if page
                            (let [items (or (:agent-invokes page) [])]
                              {:items items
                               :pagination-params (:pagination-params page)
                               :full-page? (common/full-page-of-items? items (:limit params))})
                            {:items [] :pagination-params nil :full-page? false}))
    :infinite {:initial-cursor nil
               :get-next-cursor (fn [page]
                                  (when (and (seq (:items page)) (:full-page? page))
                                    (common/pagination-cursor-for-next-page (:pagination-params page))))}
    :tags (fn [{:keys [module-id agent-name]}]
            [[:invocations module-id agent-name]])}))
