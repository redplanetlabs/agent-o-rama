(ns com.rpl.agent-o-rama.impl.ui.rpc.agents
  (:require
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-all!!
  {:stale-time-ms 0
   :polling-interval-ms 2000
   :tags (constantly [[:agents]])})

(rpcq/defrpc-query ::get-for-module!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id]}]
           [[:module-agents module-id]])})
