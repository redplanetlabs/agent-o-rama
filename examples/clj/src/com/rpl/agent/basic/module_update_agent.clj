(ns com.rpl.agent.basic.module-update-agent
  "Demonstrates module updates with set-update-mode.

   Shows how agents can continue running when their module is updated,
   using the :continue update mode to preserve state across updates."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Counter Agent Module

(aor/defagentmodule CounterModule
  [topology]

  (->
    (aor/new-agent topology "CounterAgent")
    (aor/set-update-mode :continue)
    (aor/node
     "count"
     "count"
     (fn [agent-node current-count]
       (let [new-count (inc (or current-count 0))]
         (println "counting:" new-count)
         (Thread/sleep 200)
         (if (< new-count 50)
           (aor/emit! agent-node "count" new-count)
           (aor/result! agent-node new-count)))))))

(defn demonstrate-module-update
  []
  (with-open [ipc (rtest/create-ipc)]
    (println "\n=== Module Update Example ===\n")

    ;; Deploy Version 1
    (println "Deploying...")
    (rtest/launch-module! ipc CounterModule {:tasks 1 :threads 1})

    (let [module-name (rama/get-module-name CounterModule)
          manager     (aor/agent-manager ipc module-name)]

      ;; Start counter
      (println "\nStarting counter agent...")
      (let [agent     (aor/agent-client manager "CounterAgent")
            invoke-id (aor/agent-initiate agent 0)]
        (println "\nUpdating...")
        (rtest/update-module! ipc CounterModule)
        (println "Module updated! Agent continues.\n")

        ;; Get final result
        (let [final-count (aor/agent-result agent invoke-id)]
          (println "\nFinal count:" final-count))))))

(defn -main
  [& _args]
  (demonstrate-module-update)
  (shutdown-agents))
