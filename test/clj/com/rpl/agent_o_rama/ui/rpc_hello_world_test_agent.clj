(ns com.rpl.agent-o-rama.ui.rpc-hello-world-test-agent
  (:require
   [com.rpl.agent-o-rama :as aor]))

(aor/defagentmodule RpcHelloWorldTestAgentModule
  [topology]
  (-> topology
      (aor/new-agent "RpcHelloWorldAgent")
      (aor/node
       "start"
       nil
       (fn [agent-node input]
         (aor/result! agent-node {"ok" true :input input})))))
