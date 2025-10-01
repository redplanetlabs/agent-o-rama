(ns repl
  (:use
   [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama.test :as rtest]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server]))

(defn start-repl [ipc]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (aor/start-ui ipc))

(defn stop-repl [ipc]
  (aor/stop-ui)
  (close! ipc))

(comment
  (def ipc (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (def ipc (rtest/create-ipc))
  (start-repl ipc)
  (stop-repl ipc)

  (require 'com.rpl.agent.basic.basic-agent)
  (rtest/launch-module!
   ipc
   com.rpl.agent.basic.basic-agent/BasicAgentModule
   {:tasks 1 :threads 1})
  )
