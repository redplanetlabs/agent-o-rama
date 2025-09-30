(ns com.rpl.agent.basic.trace-agent
  "Demonstrates agent traces.

  Features demonstrated:
  - agent-invoke: Synchronously invoke agent"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]))

;;; Basic agent module with single node
(aor/defagentmodule TraceAgentModule
  [topology]

  ;; Create agent with single node that processes input and returns result
  (-> topology
      (aor/new-agent "TraceAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node user-name] ; user-name is the value in the agent-invoke
         ;; Create a welcome message for the user
         (let [result (str "Welcome to agent-o-rama, " user-name "!")]
           ;; Return the final result
           (aor/result! agent-node result))))))

(defn -main
  "Run the basic agent example with sample input"
  [& _args]
  ;; Create in-process cluster
  (with-open [ipc (rtest/create-ipc)]
    ;; launch the agent module
    (rtest/launch-module! ipc TraceAgentModule {:tasks 1 :threads 1})

    ;; Get agent manager and client
    (let [manager       (aor/agent-manager
                         ipc
                         (rama/get-module-name TraceAgentModule))
          agent         (aor/agent-client manager "TraceAgent")
          {:keys [task-id agent-invoke-id] :as init}
          (aor/agent-initiate agent "Alice")

          tracing-query
          (rama/foreign-query
           ipc
           (rama/get-module-name TraceAgentModule)
           (queries/tracing-query-name "TraceAgent"))
          root-pstate
          (rama/foreign-pstate
           ipc
           (rama/get-module-name TraceAgentModule)
           (po/agent-root-task-global-name "TraceAgent"))
          root-id
          (rama/foreign-select-one
           [(path/keypath agent-invoke-id) :root-invoke-id]
           root-pstate
           {:pkey task-id})
          result
          (rama/foreign-invoke-query
           tracing-query
           task-id
           [[task-id root-id]]
           1000)]

      ;; Invoke agent synchronously with sample user names
      (prn result))))

(comment

  (def ipc (rtest/create-ipc))
  (def ui (aor/start-ui ipc {:port 1975}))

  (rtest/launch-module! ipc TraceAgentModule {:tasks 1 :threads 1})
  (def manager
    (aor/agent-manager
     ipc
     (rama/get-module-name TraceAgentModule)))
  (def agent (aor/agent-client manager "TraceAgent"))

  (def init (aor/agent-initiate agent "Alice"))

  (def task-id (:task-id init))
  (def agent-invoke-id (:agent-invoke-id init))

  (def agent-result (aor/agent-result agent init))

  (def tracing-query
    (rama/foreign-query
     ipc
     (rama/get-module-name TraceAgentModule)
     (queries/tracing-query-name "TraceAgent")))

  (def root-pstate
    (rama/foreign-pstate
     ipc
     (rama/get-module-name TraceAgentModule)
     (po/agent-root-task-global-name "TraceAgent")))

  (def root-id
    (rama/foreign-select-one
     [(path/keypath agent-invoke-id) :root-invoke-id]
     root-pstate
     {:pkey task-id}))

  (def result
    (rama/foreign-invoke-query
     tracing-query
     task-id
     [[task-id root-id]]
     1000))
)
