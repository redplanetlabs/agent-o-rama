(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; Legacy events for backward compatibility
(state/reg-event :live/start
  (fn [db {:keys [module-id agent-name invoke-id interval-ms] :as data}]
    (when (and module-id agent-name invoke-id)
      (sente/live-start! data))
    nil))

(state/reg-event :live/stop
  (fn [db {:keys [module-id agent-name invoke-id] :as data}]
    (when (and module-id agent-name invoke-id)
      (sente/live-stop! data))
    nil))

;; New subscription-based events with proper cleanup tracking
(state/reg-event :live/subscribe-with-key
  (fn [db {:keys [sub-key sub-type params] :as data}]
    (println [[memory:5635510]] "Subscribing with key:" sub-key "type:" sub-type)
    ;; Send subscription request to server
    (sente/request! [:live/subscribe data] 5000
                    (fn [reply]
                      (when-not (:success reply)
                        (println "Subscription failed:" reply))))
    ;; Track active subscriptions in app-db for debugging
    [[:sente :subscriptions sub-key] 
     (constantly {:sub-type sub-type :params params})]))

(state/reg-event :live/unsubscribe-with-key
  (fn [db {:keys [sub-key sub-type] :as data}]
    (println [[memory:5635510]] "Unsubscribing with key:" sub-key "type:" sub-type)
    ;; Send unsubscribe request to server
    (sente/request! [:live/unsubscribe data] 3000
                    (fn [reply]
                      (when-not (:success reply)
                        (println "Unsubscribe failed:" reply))))
    ;; Remove from app-db tracking
    [[:sente :subscriptions]
     #(dissoc % sub-key)]))


