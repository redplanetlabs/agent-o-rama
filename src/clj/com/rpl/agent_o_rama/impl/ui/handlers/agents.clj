(ns com.rpl.agent-o-rama.impl.ui.handlers.agents
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

;; We need to reference the multimethod from sente namespace
;; Since we can't require it directly due to circular dependency,
;; we'll use a qualified symbol to extend it
(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :agents/get-all
  [_ uid]
  (for [[module-name agent-name]
        (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (ui/get-object :aor-cache))]
    {:module-id (common/url-encode module-name)
     :agent-name (common/url-encode agent-name)}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :agents/get-for-module
  [{:keys [module-id]} uid]
  (let [decoded-module-id (common/url-decode module-id)]
    (if-let [manager (common/get-manager decoded-module-id)]
      (let [agent-names (aor/agent-names manager)]
        (mapv (fn [agent-name]
                {:module-id module-id
                 :agent-name (common/url-encode agent-name)})
              agent-names))
      [])))