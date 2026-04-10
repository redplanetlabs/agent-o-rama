(ns com.rpl.agent-o-rama.impl.ui.rpc.hello-world)

(defn index!!
  [_system {:keys [decoded-module-id]}]
  {:message "Hello RPC world"
   :module-id decoded-module-id
   :transport "http-transit"})
