(ns com.rpl.agent-o-rama.impl.ui.rpc.agents
  (:require
   [re-frame.query :as rfq]))

(def ^:export q-get-all
  (rfq/reg-query
   ::get-all!!
   {:query-fn (fn [_params]
                 {:rpc/id ::get-all!!
                  :payload {}})
    :stale-time-ms 0
    :polling-interval-ms 2000
    :tags (constantly [[:agents]])}))

(def ^:export q-get-for-module
  (rfq/reg-query
   ::get-for-module!!
   {:query-fn (fn [{:keys [module-id] :as params}]
                 {:rpc/id ::get-for-module!!
                  :payload params})
    :stale-time-ms 0
    :tags (fn [{:keys [module-id]}]
             [[:module-agents module-id]])}))
