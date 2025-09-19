(ns com.rpl.agent.basic.router-agent
  "Demonstrates conditional routing between different nodes in an agent graph.

  Features demonstrated:
  - Conditional routing based on input
  - Multiple emit! calls to different nodes
  - Branching execution paths that reconverge
  - Different processing for different input types"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent that routes messages to different processing nodes based on content
(aor/defagentmodule RouterAgentModule
  [topology]
  (->
    (aor/new-agent topology "RouterAgent")

    ;; Router node: decides which processing node to send to
    (aor/node
     "route"
     ["handle-urgent" "handle-default"] ; can emit to these nodes
     (fn [agent-node message]
       (if (str/starts-with? message "urgent:")
         (aor/emit! agent-node "handle-urgent" message)
         (aor/emit! agent-node "handle-default" message))))

    ;; Urgent message handler
    (aor/node
     "handle-urgent"
     "finalize"
     (fn [agent-node message]
       (let [content (subs message 7)] ; remove "urgent:" prefix
         (aor/emit!
          agent-node
          "finalize"
          {:priority "HIGH" :message content}))))

    ;; Default message handler
    (aor/node
     "handle-default"
     "finalize"
     (fn [agent-node message]
       (aor/emit!
        agent-node
        "finalize"
        {:priority "NORMAL" :message message})))

    ;; Final node: creates the result
    ;; Both urgent and default handlers emit to this node - reconvergence
    ;; point
    (aor/node
     "finalize"
     nil
     (fn [agent-node {:keys [priority message]}]
       (let [result (format "[%s] %s" priority message)]
         (aor/result! agent-node result))))))

(defn -main
  "Run the router agent example with different message types"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc RouterAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name RouterAgentModule))
          agent   (aor/agent-client manager "RouterAgent")]

      (println "Router Agent Results:")

      (println "\n--- Urgent Message ---")
      (let [result1 (aor/agent-invoke agent "urgent:system failure detected")]
        (println "Result:" result1))

      (println "\n--- Default Message ---")
      (let [result2 (aor/agent-invoke agent "just a regular message")]
        (println "Result:" result2)))))
