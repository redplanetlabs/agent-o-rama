(ns com.rpl.agent-o-rama.impl.ui.rpc.experiment-list
  (:require
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-all-for-dataset!!
  {:stale-time-ms 0
   :polling-interval-ms 2000
   :tags (fn [{:keys [module-id dataset-id]}]
           [[:experiments module-id dataset-id]])})
