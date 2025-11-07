(ns ci-playwright-setup
  (:gen-class)
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama.test :as rtest]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server]
   [com.rpl.agent.basic.basic-agent :as basic-agent]
   [com.rpl.agent.e2e-test-agent :as e2e-test-agent]))

(defn -main []
  (println "Starting CI Playwright setup...")

  ;; Start shadow-cljs server and watch frontend
  (println "Starting shadow-cljs server...")
  (shadow.cljs.devtools.server/start!)
  (println "Watching frontend build...")
  (shadow/watch :frontend)

  ;; Small delay to let shadow-cljs start
  (Thread/sleep 2000)

  ;; Create IPC and launch modules BEFORE starting UI
  (println "Creating IPC...")
  (let [ipc (rtest/create-ipc)]
    ;; Launch modules for Playwright tests
    (println "Launching BasicAgentModule...")
    (rtest/launch-module!
     ipc
     basic-agent/BasicAgentModule
     {:tasks 1 :threads 1})

    (println "Launching E2ETestAgentModule...")
    (rtest/launch-module!
     ipc
     e2e-test-agent/E2ETestAgentModule
     {:tasks 1 :threads 1})

    ;; Start UI server AFTER modules are ready
    (println "All modules launched. Starting UI server on port 1974...")
    (aor/start-ui ipc {:port 1974 :no-input-before-close true})

    ;; Keep running - don't exit
    (println "Setup complete. Server running on port 1974 with all modules ready.")
    (println "Waiting for Playwright tests...")
    ;; Block forever
    @(promise)))
