(ns com.rpl.agent.basic.module-update-agent
  "Demonstrates module updates with set-update-mode.

   Shows how agents can continue running when their module is updated,
   using the :continue update mode to preserve state across updates."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.rama RamaModule]))

;;; Counter Agent Module - Version 1
;;; Increments by 1

(aor/defagentmodule CounterModule
  [topology]

  (-> topology
      (aor/new-agent "CounterAgent")
      (aor/set-update-mode :continue)
      (aor/node "count" nil
                (fn [agent-node ^Long current-count]
                  (let [new-count (+ (or current-count 0) 1)]
                    (println "V1 counting:" new-count)
                    (Thread/sleep 1000)
                    (if (< new-count 10)
                      (aor/emit! agent-node "count" new-count)
                      (aor/result! agent-node new-count)))))))

;;; Counter Agent Module - Version 2
;;; Same structure but increments by 5
;;; Note: For module updates to work, we need to define a second version
;;; with the same name but different behavior

(def CounterModuleV2
  (aor/agentmodule
   {:module-name "CounterModule"}  ; Same name as above for updates
   [topology]

   (-> topology
       (aor/new-agent "CounterAgent")
       (aor/set-update-mode :continue)
       (aor/node "count" nil
                 (fn [agent-node ^Long current-count]
                   (let [new-count (+ (or current-count 0) 5)]
                     (println "V2 counting:" new-count)
                     (Thread/sleep 1000)
                     (if (< new-count 30)
                       (aor/emit! agent-node "count" new-count)
                       (aor/result! agent-node new-count))))))))

(defn demonstrate-module-update
  []
  (with-open [ipc (rtest/create-ipc)]
    (println "\n=== Module Update Example ===\n")

    ;; Deploy Version 1
    (println "Deploying Version 1 (increments by 1)...")
    (rtest/launch-module! ipc CounterModule {:tasks 1 :threads 1})

    (let [module-name "CounterModule"
          manager (aor/agent-manager ipc module-name)]

      ;; Start counter with Version 1
      (println "\nStarting counter agent...")
      (let [agent (aor/agent-client manager "CounterAgent")
            invoke-id (aor/agent-initiate agent 0)]

        ;; Let it count a few times
        (Thread/sleep 4500)

        ;; Update to Version 2
        (println "\nUpdating to Version 2 (increments by 5)...")
        (rama/update-module! ipc CounterModuleV2)
        (println "Module updated! Agent continues with new logic.\n")

        ;; Get final result
        (let [final-count (aor/agent-result agent invoke-id)]
          (println "\nFinal count:" final-count)
          (println "\nNote: Counter started with +1 increments, then")
          (println "continued with +5 increments after update,")
          (println "preserving its state due to :continue mode."))))))

(defn -main
  [& _args]
  (demonstrate-module-update)
  (System/exit 0))