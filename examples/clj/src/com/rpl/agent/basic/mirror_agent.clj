(ns com.rpl.agent.basic.mirror-agent
  "Demonstrates cross-module agent invocation using mirror agents.

  Features demonstrated:
  - defagentmodule: Define an agent module
  - agentmodule: Define parameterized agent module
  - declare-cluster-agent: Create mirror reference to agent in another module
  - agent-client: Get client for mirror agent within agent node
  - agent-invoke: Invoke mirror agent across modules
  - rama/get-module-name: Get module name for cross-module references
  - Multiple module deployment to IPC"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Module 1: Greeter agent that creates greeting messages
(aor/defagentmodule GreeterModule
  [topology]

  (-> topology
      (aor/new-agent "Greeter")
      (aor/node
       "greet"
       nil
       (fn [agent-node name]
         (let [greeting (str "Hello, " name "!")]
           (aor/result! agent-node greeting))))))

(defn create-mirror-module
  "Create Module 2: Mirror agent that invokes Greeter from Module 1"
  [greeter-module-name]
  (aor/agentmodule
   [topology]

   ;; Declare mirror reference to Greeter agent in GreeterModule
   (aor/declare-cluster-agent
    topology
    "GreeterMirror"
    greeter-module-name
    "Greeter")

   (-> topology
       (aor/new-agent "MirrorAgent")
       (aor/node
        "process"
        nil
        (fn [agent-node name]
          ;; Get client for the mirror agent
          (let [greeter-client (aor/agent-client agent-node "GreeterMirror")
                ;; Invoke the mirror agent (cross-module call)
                greeting       (aor/agent-invoke greeter-client name)
                ;; Add prefix to result
                result         (str "Mirror says: " greeting)]
            (aor/result! agent-node result)))))))

(defn -main
  "Run the mirror agent example demonstrating cross-module invocation"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    ;; Launch GreeterModule first
    (rtest/launch-module! ipc GreeterModule {:tasks 1 :threads 1})
    (let [greeter-module-name (rama/get-module-name GreeterModule)
          ;; Create MirrorModule with reference to GreeterModule
          mirror-module       (create-mirror-module greeter-module-name)]

      ;; Launch MirrorModule
      (rtest/launch-module! ipc mirror-module {:tasks 1 :threads 1})

      ;; Get agent manager for MirrorModule
      (let [manager      (aor/agent-manager ipc (rama/get-module-name mirror-module))
            mirror-agent (aor/agent-client manager "MirrorAgent")]

        (println "Mirror Agent Results:")
        (println "Input: \"Alice\" -> Result:" (aor/agent-invoke mirror-agent "Alice"))
        (println "Input: \"Bob\" -> Result:" (aor/agent-invoke mirror-agent "Bob"))))))
