(ns repl
  (:use
   [com.rpl.rama]
   [com.rpl.specter])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.server :as srv]
   [com.rpl.agent-o-rama.system :as sys]
   [clojure.tools.logging :as cljlogging]
   [ring.adapter.jetty :as jetty]
   [shadow.cljs.devtools.api :as shadow])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

;; check for closed modules, check for new agents on existing modules,
;; check for removed agents, and for new modules
(defn refresh-agent-modules! []
  (let [rama-client (sys/get-object :rama-client)
        modules (deployed-module-names rama-client)]
    (doseq [mod modules]
      (reset! sys/system (transform [:aor-agent-managers mod]
                                    (constantly (aor/agent-manager rama-client mod))
                                    @sys/system)))))

#_(-> @sys/system :aor-agent-managers (get "examples.core/FlowModule"))

(defn start []
  (shadow/watch :frontend)
  (swap! sys/system assoc :jetty (jetty/run-jetty #'srv/handler
                                                  {:port 1974 ;; TODO make configurable
                                                   :join? false}))
  (swap! sys/system assoc :rama-client (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (swap! sys/system assoc :background-exec (ScheduledThreadPoolExecutor. 1) )
  (.scheduleAtFixedRate
   ^ScheduledThreadPoolExecutor (:background-exec @sys/system)
   (fn [] (try
            (refresh-agent-modules!)
            (catch Throwable t
              (cljlogging/error t "Error in refreshing agent modules" {}))))
   0
   5
   TimeUnit/SECONDS))

(defn stop []
  (.stop (:jetty @sys/system))
  (close! (:rama-client @sys/system))
  (.shutdownNow (:background-exec @sys/system)))

(defn go []
  (stop)
  (start))

(comment
  (go))
