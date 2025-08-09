(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; Single active subscription management - much simpler!
(state/reg-event :invocation/view-live
  (fn [db {:keys [module-id agent-name invoke-id] :as params}]
    (let [current-sub (get-in db [:sente :active-subscription])
          new-sub-key (str (random-uuid))]
      
      ;; If we have an active subscription, stop it first
      (when current-sub
        (println "Stopping previous subscription:" (:sub-key current-sub))
        (sente/push! [:live/unsubscribe {:sub-key (:sub-key current-sub)
                                         :sub-type :live-graph}]))
      
      ;; Start new subscription
      (println "Starting new subscription for:" invoke-id)
      (sente/push! [:live/subscribe {:sub-key new-sub-key
                                     :sub-type :live-graph
                                     :params params}])
      
      ;; Store the active subscription
      (state/dispatch [:db/set-value [:sente :active-subscription] 
                       {:sub-key new-sub-key :params params}])
      
      ;; Update current invocation and clear old data
      [[:current-invocation]
       (constantly {:invoke-id invoke-id
                   :module-id module-id
                   :agent-name agent-name
                   :graph {}
                   :summary {}})])))

;; Clean up on app shutdown/navigation away
(state/reg-event :invocation/stop-live
  (fn [db]
    (let [current-sub (get-in db [:sente :active-subscription])]
      (when current-sub
        (println "Stopping subscription:" (:sub-key current-sub))
        (sente/push! [:live/unsubscribe {:sub-key (:sub-key current-sub)
                                         :sub-type :live-graph}]))
      [[:sente :active-subscription] (constantly nil)])))


