(ns com.rpl.agent-o-rama.impl.ui.launch
  (:gen-class))

(defn -main
  "Main entry point for the Agent-o-rama UI"
  [port]
  (require 'com.rpl.agent-o-rama.impl.ui.core)
  (require 'com.rpl.rama)
  (let [port (Long/parseLong port)
        cluster-manager ((resolve 'com.rpl.rama/open-cluster-manager)
                         {"conductor.host" "localhost"})]
    ((resolve 'com.rpl.agent-o-rama.impl.ui.core/start-ui)
     cluster-manager
     {:port port :no-input-before-close true})))
