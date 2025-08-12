(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.specter :as s]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

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
    (let [{:keys [is-complete root-invoke-id task-id]} summary]
      ;; Optimistically return an update for the summary, and possibly more below
      (state/dispatch [:db/set-value [:invocations-data invoke-id :summary] summary])
      ;; Note: We keep this dispatch for compatibility; we could also include it in a returned multi-path
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
    ;; Store all the graph data at once
    (let [{:keys [invokes-map implicit-edges summary]} graph-data]
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