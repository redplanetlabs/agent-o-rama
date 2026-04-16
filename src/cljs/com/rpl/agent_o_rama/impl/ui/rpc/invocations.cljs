(ns com.rpl.agent-o-rama.impl.ui.rpc.invocations
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [re-frame.query :as rfq])
  (:require-macros [com.rpl.agent-o-rama.impl.ui.rpc.query-macros :as rpcq]))

(rpcq/defrpc-query ::get-page!!
  {:stale-time-ms 0
   :polling-interval-ms 2000
   :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations module-id agent-name]])})

(rpcq/defrpc-query ::get-filter-options!!
  {:stale-time-ms 0
   :polling-interval-ms 30000
   :tags (fn [{:keys [module-id agent-name]}]
           [[:invocations/filter-options module-id agent-name]])})

(rpcq/defrpc-query ::get-graph!!
  {:stale-time-ms 0
   :polling-interval-ms 2000
   :tags (fn [{:keys [module-id agent-name]}]
           [[:graph module-id agent-name]])})

(rpcq/defrpc-query ::get-node-stats!!
  {:stale-time-ms 0
   :polling-interval-ms 30000
   :tags (fn [{:keys [module-id agent-name granularity]}]
           [[:node-stats module-id agent-name granularity]])})

(rpcq/defrpc-query ::get-graph-page!!
  {:stale-time-ms 0
   :tags (fn [{:keys [module-id agent-name invoke-id]}]
           [[:invocation-graph-page module-id agent-name invoke-id]])})

(rpcq/defrpc-query ::get-page-inf!!
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
           [[:invocations module-id agent-name]])})
