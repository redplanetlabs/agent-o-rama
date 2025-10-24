(ns com.rpl.agent-o-rama.impl.ui.launch
  (:gen-class))

(defn -main
  "Main entry point for the Agent-o-rama UI"
  [port]
  (let [port (Long/parseLong port)
        cluster-manager (open-cluster-manager {"conductor.host" "localhost"})]
    (cljlogging/info "Starting Agent-o-rama UI...")
    (require 'com.rpl.agent-o-rama.impl.ui.core)
    ((resolve 'com.rpl.agent-o-rama.impl.ui.core/start-ui) {:port port})))
