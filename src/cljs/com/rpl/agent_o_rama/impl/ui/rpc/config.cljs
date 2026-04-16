(ns com.rpl.agent-o-rama.impl.ui.rpc.config
  (:require
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-all!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name]}]
           [[:config module-id agent-name]])})

(rpcq/defrpc-query ::get-all-global!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:global-config module-id]])})
