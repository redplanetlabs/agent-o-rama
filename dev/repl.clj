(ns repl
  (:use
   [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [shadow.cljs.devtools.server]
   [shadow.cljs.devtools.api :as shadow]))

(defn start-repl [ipc]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (aor/start-ui ipc))

(defn stop-repl [ipc]
  (aor/stop-ui)
  (close! ipc))

(comment
  (def ipc (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (start-repl ipc)
  (stop-repl ipc))
