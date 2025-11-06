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

  ;; Create IPC and start UI
  (println "Creating IPC and starting UI...")
  (let [ipc (rtest/create-ipc)]
    (aor/start-ui ipc {:port 1974 :no-input-before-close true})

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

    ;; Keep running - don't exit
    (println "Setup complete. Server running on port 1974.")
    (println "Waiting for Playwright tests...")
    ;; Block forever
    @(promise)))
