(ns com.rpl.agent-o-rama.impl.ui.handlers.agents
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.ui.sente :refer [-event-msg-handler]])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defmethod -event-msg-handler :agents/get-all
  [ev-msg]
  (common/handle-api-call
   (fn [_ uid]
     (for [[module-name agent-name]
           (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (ui/get-object :aor-cache))]
       {:module-id (common/url-encode module-name)
        :agent-name (common/url-encode agent-name)}))
   ev-msg))

(defmethod -event-msg-handler :agents/get-for-module
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id]} uid]
     (let [decoded-module-id (common/url-decode module-id)]
       (if-let [manager (common/get-manager decoded-module-id)]
         (let [agent-names (aor/agent-names manager)]
           (mapv (fn [agent-name]
                   {:module-id module-id
                    :agent-name (common/url-encode agent-name)})
                 agent-names))
         [])))
   ev-msg))