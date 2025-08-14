(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.specter :as s]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; =============================================================================
;; GRAPH UTILITIES
;; =============================================================================

(defn generate-implicit-edges
  "Compares the static historical graph with the dynamic invocation trace to find
   paths to aggregation nodes that could have been taken but were not."
  [invokes-map historical-graph]
  (let [;; A map from {agg-node-invoke-id -> #{emitter-invoke-ids}}
        actual-emits-to-aggs (into {}
                                   (for [[id data] invokes-map
                                         :when (:agg-state data)] ; Check if it's an agg-node trace
                                     [id (set (map :invoke-id (:agg-inputs-first-10 data)))]))]
    (->> invokes-map
         (mapcat (fn [[invoke-id invoke-data]]
                   (let [node-name     (:node invoke-data)
                         static-info   (get-in historical-graph [:node-map node-name])
                         agg-context   (:agg-context static-info)
                         potential-outputs (:output-nodes static-info)]
                     
                     ;; Only consider nodes that are inside an aggregation context
                     (when agg-context
                       (for [out-name potential-outputs
                             ;; We only care about potential outputs that ARE aggregation nodes
                             :when (= :agg-node (get-in historical-graph [:node-map out-name :node-type]))]
                         (let [;; This is the invoke-id of the aggregation this node belongs to.
                               agg-node-invoke-id (:agg-invoke-id invoke-data)
                               actual-emitters    (get actual-emits-to-aggs agg-node-invoke-id)
                               did-emit?          (contains? actual-emitters invoke-id)]
                           
                           (when (and (not did-emit?) agg-node-invoke-id)
                             {:source      (str invoke-id)
                              :target      (str agg-node-invoke-id)
                              :id          (str "implicit-" invoke-id "-" agg-node-invoke-id)
                              :implicit?   true})))))))
         (filter some?)
         (vec))))

;; Simplified view-live event - just track which invocation we're viewing
(state/reg-event :invocation/view-live
  (fn [db {:keys [module-id agent-name invoke-id] :as params}]
    (let [current-invoke-id (get-in db [:current-invocation :invoke-id])
          new-sub-key (str (random-uuid))]
      
      ;; Only switch if we're actually changing invocations
      (if (= invoke-id current-invoke-id)
        nil ;; No state change needed
        (do
          
          ;; Register with server that we want to watch this invocation
          ;; (for security/tracking purposes)
          (sente/push! [:live/subscribe {:sub-key new-sub-key
                                        :sub-type :live-graph
                                        :params params}])
          
          ;; Return atomic multi-transform for local state updates
          (s/multi-path
            [:sente :active-subscription (s/terminal-val {:sub-key new-sub-key :params params})]
            [:invocations-data invoke-id :is-complete (s/terminal-val false)]
            [:invocations-data invoke-id :next-leaves (s/terminal-val nil)]
            [:current-invocation (s/terminal-val {:invoke-id invoke-id
                                                  :module-id module-id
                                                  :agent-name agent-name})]))))))

;; Clean up on app shutdown/navigation away
(state/reg-event :invocation/stop-live
  (fn [db]
    (let [current-sub (get-in db [:sente :active-subscription])]
      (when current-sub
        (sente/push! [:live/unsubscribe {:sub-key (:sub-key current-sub)
                                         :sub-type :live-graph}]))
      [:sente :active-subscription (s/terminal-val nil)])))

;; =============================================================================
;; UNIFIED INVOCATION LOADING
;; =============================================================================

;; Main entry point for loading any invocation (live or historical)
(state/reg-event :invocation/load-or-subscribe
  (fn [db {:keys [invoke-id module-id agent-name]}]
    ;; First, check if we need to load this invocation
    (let [existing-summary (get-in db [:invocations-data invoke-id :summary])]
      (when-not existing-summary
        ;; Request summary to determine if live or historical
        (sente/request! [:api/get-invocation-summary 
                        {:invoke-id invoke-id 
                         :module-id module-id 
                         :agent-name agent-name}]
                       5000
                       (fn [reply]
                         (if (:success reply)
                           (state/dispatch [:invocation/process-summary 
                                          {:invoke-id invoke-id
                                           :module-id module-id  
                                           :agent-name agent-name
                                           :summary (:data reply)}])
                           (js/console.error "Failed to get invocation summary:" (:error reply))))))
      ;; Return nil to indicate no immediate state change
      nil)))

;; Process the summary and decide loading strategy
(state/reg-event :invocation/process-summary
  (fn [db {:keys [invoke-id module-id agent-name summary]}]
    (let [{:keys [is-complete root-invoke-id task-id historical-graph]} summary]
      ;; Store summary and historical-graph atomically
      (state/dispatch [:db/set-values
                       [[:invocations-data invoke-id :summary] summary]
                       [[:invocations-data invoke-id :historical-graph] historical-graph]])
      
      (if is-complete
        ;; HISTORICAL: Fetch the complete graph at once
        (sente/request! [:api/get-full-graph 
                        {:invoke-id invoke-id 
                         :module-id module-id 
                         :agent-name agent-name}]
                       10000
                       (fn [reply]
                         (when (:success reply)
                           (state/dispatch [:invocation/load-full-graph 
                                          invoke-id 
                                          (:data reply)]))))
        ;; LIVE: Store root info and start subscription
        (do
          ;; Atomically set root/task and then start live subscription
          (state/dispatch [:db/set-values
                           [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]
                           [[:invocations-data invoke-id :task-id] task-id]])
          (state/dispatch [:invocation/view-live 
                           {:module-id module-id 
                            :agent-name agent-name 
                            :invoke-id invoke-id}])))
      nil)))

;; Load complete historical graph
(state/reg-event :invocation/load-full-graph
  (fn [db invoke-id graph-data]
    ;; Store all the graph data at once, computing implicit edges on client
    (let [{:keys [invokes-map summary]} graph-data
          historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
          
          ;; Compute implicit edges on the client using the historical graph
          implicit-edges (if (and invokes-map historical-graph)
                          (generate-implicit-edges invokes-map historical-graph)
                          [])]
      [:invocations-data invoke-id
       (s/multi-path
         [:graph :nodes (s/terminal-val invokes-map)]
         [:implicit-edges (s/terminal-val implicit-edges)]
         [:summary (s/terminal-val summary)]
         [:is-complete (s/terminal-val true)])])))

;; Handle paginated data merge
(state/reg-event :invocation/merge-paginated-data
  (fn [db invoke-id paginated-data]
    (let [{:keys [invokes-map]} paginated-data
          current-nodes (get-in db [:invocations-data invoke-id :graph :nodes])]
      [:invocations-data invoke-id :graph :nodes 
       (s/terminal #(merge % invokes-map))])))

;; New event for batched live node updates with implicit edge recalculation
(state/reg-event :invocation/merge-live-nodes
  (fn [db invoke-id new-nodes-map]
    (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
          ;; Merge new nodes with existing ones
          current-nodes (get-in db [:invocations-data invoke-id :graph :nodes])
          merged-nodes (merge current-nodes new-nodes-map)
          
          ;; Recalculate implicit edges with the updated graph
          implicit-edges (if (and merged-nodes historical-graph)
                          (generate-implicit-edges merged-nodes historical-graph)
                          [])]
                          
      ;; Atomically update both nodes and edges
      [:invocations-data invoke-id
       (s/multi-path
         [:graph :nodes (s/terminal-val merged-nodes)]
         [:implicit-edges (s/terminal-val implicit-edges)])])))

;; Cleanup when leaving an invocation
(state/reg-event :invocation/cleanup
  (fn [db {:keys [invoke-id]}]
    ;; Stop any live subscriptions
    (state/dispatch [:invocation/stop-live])
    ;; Clear UI state
    (state/dispatch [:ui/clear-fork-state])
    [:ui :selected-node-id (s/terminal-val nil)]))

;; UI state management for forking
(state/reg-event :ui/clear-fork-state
  (fn [db]
    [:ui (s/multi-path
           [:changed-nodes (s/terminal-val {})]
           [:selected-node-id (s/terminal-val nil)]
           [:forking-mode? (s/terminal-val false)])]))