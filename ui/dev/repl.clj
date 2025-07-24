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
      (let [manager (try
                      (aor/agent-manager rama-client mod)
                      (catch Exception e ::no-aor))]
        (when-not (= ::no-aor manager)
          (transform [ATOM :aor-cache mod :manager]
                     (constantly manager)
                     sys/system)
          
          (doseq [agent-name (.getAgentNames manager)]
            (transform [ATOM :aor-cache mod :clients agent-name nil?]
                       (constantly (.getAgentClient manager agent-name))
                       sys/system)))))

    ;; stale agents
    :TODO
    
    ;; stale modules
    (let [stale-modules (clojure.set/difference
                         (set (select [:aor-agent-managers MAP-KEYS] @sys/system))
                         modules)]
      (doseq [mod stale-modules]
        :TODO))
    ))

(-> @sys/system :aor-agent-managers (get "examples.core/FlowModule")
    (.getAgentClient "foo")
    )

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
            #_(refresh-agent-modules!)
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
