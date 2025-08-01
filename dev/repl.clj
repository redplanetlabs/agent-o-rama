(ns repl
  (:use
   [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.core :as uic]
   [shadow.cljs.devtools.server]
   [shadow.cljs.devtools.api :as shadow]))

(defn start-repl [ipc]
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (uic/start ipc))

(comment
  (start-repl (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (uic/stop))
