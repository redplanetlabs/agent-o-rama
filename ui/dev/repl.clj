(ns repl
  (:use
   [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama.ui.server :as srv]
   [com.rpl.agent-o-rama.system :as sys]
   [shadow.cljs.devtools.api :as shadow]
   [ring.adapter.jetty :as jetty])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor]))

(defn start []
  (shadow/watch :frontend)
  (swap! sys/system assoc :jetty (jetty/run-jetty #'srv/handler
                                                  {:port 2999
                                                   :join? false}))
  (swap! sys/system assoc :rama-client (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (swap! sys/system assoc :background-exec (ScheduledThreadPoolExecutor. 1) ))

(defn stop []
  (.stop (:jetty @sys/system))
  (close! (:rama-client @sys/system))
  (.shutdownNow (:background-exec @sys/system)))

(defn go []
  (stop)
  (start))

(comment
  (go))
