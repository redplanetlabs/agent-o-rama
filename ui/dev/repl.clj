(ns repl
  (:use
   [com.rpl.rama]
   [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.server :as srv]
   [com.rpl.agent-o-rama.system :as sys]
   [clojure.tools.logging :as cljlogging]
   [ring.adapter.jetty :as jetty]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

(defn refresh-agent-modules! []
  (let [rama-client (sys/get-object :rama-client)
        modules (deployed-module-names rama-client)]
    (doseq [mod modules]
      (let [manager (try
                      (aor/agent-manager rama-client mod)
                      (catch Exception e ::no-aor))]
        (when-not (= ::no-aor manager)
          (setval [ATOM :aor-cache (keypath mod) :manager]
                  manager
                  sys/system)
          (let [agent-names (aor/agent-names manager)]
            (doseq [agent-name agent-names]
              ;; nil? so that it doesn't waste resources on uneeded clients
              ;; doesn't use constantly because that evals its body
              (transform [ATOM :aor-cache (keypath mod) :clients (keypath agent-name) nil?]
                         (fn [_] (aor/agent-client manager agent-name))
                         sys/system))

            ;; stale agents
            (let [stale-agents (clojure.set/difference
                                (set
                                 (select [ATOM :aor-cache (keypath mod) :clients MAP-KEYS]
                                         sys/system))
                                agent-names)]
              (doseq [stale-agent stale-agents]
                (transform [ATOM :aor-cache (keypath mod) :clients (keypath stale-agent)]
                           (fn [client]
                             (close! client)
                             NONE)
                           sys/system)))))))

    ;; stale modules
    (let [stale-modules (clojure.set/difference
                         (set (select [ATOM :aor-cache MAP-KEYS] sys/system))
                         modules)]
      (doseq [mod stale-modules]
        (transform [ATOM :aor-cache (keypath mod) :client MAP-VALS] close! sys/system)
        (setval [ATOM :aor-cache (keypath mod)] NONE sys/system)))))

(defn start []
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (swap! sys/system assoc :jetty (jetty/run-jetty #'srv/handler
                                                  {:port 1974 ;; TODO make configurable
                                                   :join? false}))
  (swap! sys/system assoc :rama-client (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (swap! sys/system assoc :background-exec (ScheduledThreadPoolExecutor. 1))
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
  (.stop ^org.eclipse.jetty.server.Server (:jetty @sys/system))
  (close! (:rama-client @sys/system))
  (.shutdownNow ^ScheduledThreadPoolExecutor (:background-exec @sys/system)))

(comment
  (start)
  (stop))
