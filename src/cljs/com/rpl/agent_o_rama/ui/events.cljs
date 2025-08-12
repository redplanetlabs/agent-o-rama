(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; Simplified view-live event - just track which invocation we're viewing
(state/reg-event :invocation/view-live
  (fn [db {:keys [module-id agent-name invoke-id] :as params}]
    (let [current-invoke-id (get-in db [:current-invocation :invoke-id])
          new-sub-key (str (random-uuid))]
      
      ;; Only switch if we're actually changing invocations
      (if (= invoke-id current-invoke-id)
        (do
          (println "Already viewing invocation:" invoke-id)
          nil) ;; No state change needed
        (do
          (println "Switching to live view for:" invoke-id)
          
          ;; Register with server that we want to watch this invocation
          ;; (for security/tracking purposes)
          (sente/push! [:live/subscribe {:sub-key new-sub-key
                                        :sub-type :live-graph
                                        :params params}])
          
          ;; Store the active subscription key for cleanup
          (state/dispatch [:db/set-value [:sente :active-subscription] 
                          {:sub-key new-sub-key :params params}])
          
          ;; Reset completion flag but keep existing nodes (they might still be valid)
          (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] false])
          (state/dispatch [:db/set-value [:invocations-data invoke-id :next-leaves] nil])
          
          ;; Update current invocation pointer
          [[:current-invocation]
           (constantly {:invoke-id invoke-id
                       :module-id module-id
                       :agent-name agent-name})])))))

;; Clean up on app shutdown/navigation away
(state/reg-event :invocation/stop-live
  (fn [db]
    (let [current-sub (get-in db [:sente :active-subscription])]
      (when current-sub
        (println "Stopping subscription:" (:sub-key current-sub))
        (sente/push! [:live/unsubscribe {:sub-key (:sub-key current-sub)
                                         :sub-type :live-graph}]))
      [[:sente :active-subscription] (constantly nil)])))