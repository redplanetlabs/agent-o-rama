(ns com.rpl.agent-o-rama.impl.ui.rpc.hello-world)

(defn index!!
  [_system {:keys [module-id]}]
  {:message "Hello RPC world"
   :module-id module-id})
