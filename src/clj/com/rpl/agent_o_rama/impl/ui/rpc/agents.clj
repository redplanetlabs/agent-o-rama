(ns com.rpl.agent-o-rama.impl.ui.rpc.agents
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn get-all!!
  [system _payload]
  (for [[module-name agent-name]
        (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (:aor-cache system))]
    {:module-id  (common/url-encode module-name)
     :agent-name (common/url-encode agent-name)}))

(defn get-for-module!!
  [system {:keys [module-id]}]
  (let [manager (get-in system [:aor-cache module-id :manager])]
    (if manager
      (let [agent-names (aor/agent-names manager)]
        (mapv (fn [agent-name]
                {:module-id  module-id
                 :agent-name (common/url-encode agent-name)})
              agent-names))
      [])))
