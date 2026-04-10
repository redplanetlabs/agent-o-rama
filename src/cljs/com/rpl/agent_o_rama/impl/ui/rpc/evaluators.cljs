(ns com.rpl.agent-o-rama.impl.ui.rpc.evaluators
  (:require
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
