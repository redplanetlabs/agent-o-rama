(ns com.rpl.agent-o-rama.impl.ui.rpc.evaluators
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq]))

(def ^:export _q1
  (rfq/reg-query
 ::get-all-builders!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all-builders!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:evaluator-builders module-id]])}))

(def ^:export _q2
  (rfq/reg-query
 ::get-all-instances!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all-instances!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:evaluator-instances module-id]])}))

(def ^:export _q3
  (rfq/reg-query
   ::get-all-instances-inf!!
   {:query-fn (fn [params]
                (let [{:keys [cursor]} params
                      base (dissoc params :cursor)]
                  {:rpc/id ::get-all-instances!!
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
            [[:evaluator-instances module-id]])}))
