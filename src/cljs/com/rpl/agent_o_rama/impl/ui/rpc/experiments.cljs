(ns com.rpl.agent-o-rama.impl.ui.rpc.experiments
  (:require
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-results!!
  {:stale-time-ms 0
   :polling-interval-ms 2000
   :tags (fn [{:keys [module-id dataset-id experiment-id]}]
           [[:experiment-results module-id dataset-id experiment-id]])})
