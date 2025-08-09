(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

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


