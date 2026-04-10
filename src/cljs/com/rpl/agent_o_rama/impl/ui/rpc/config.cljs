(ns com.rpl.agent-o-rama.impl.ui.rpc.config
  (:require
   [re-frame.query :as rfq]))

(rfq/reg-query
 ::get-all!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id agent-name]}]
           [[:config module-id agent-name]])})

(rfq/reg-query
 ::get-all-global!!
 {:query-fn (fn [params]
               {:rpc/id ::get-all-global!!
                :payload params})
  :stale-time-ms 0
  :tags (fn [{:keys [module-id]}]
           [[:global-config module-id]])})
