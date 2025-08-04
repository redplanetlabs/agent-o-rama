(ns com.rpl.agent-o-rama.impl.ui.core
  (:use
   [com.rpl.rama]
   [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.ui.server :as srv]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [clojure.tools.logging :as cljlogging]
   [ring.adapter.jetty :as jetty])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

(defn refresh-agent-modules! []
  (let [rama-client (ui/get-object :rama-client)
        modules (deployed-module-names rama-client)]
    (when (empty? modules) (setval [ATOM :aor-cache] {} ui/system))
    (doseq [mod modules]
      (let [manager (try
                      (aor/agent-manager rama-client mod)
                      (catch Exception e ::no-aor))]
        (when-not (= ::no-aor manager)
          (setval [ATOM :aor-cache (keypath mod) :manager]
                  manager
                  ui/system)
          (let [agent-names (aor/agent-names manager)]
            (doseq [agent-name agent-names]
              ;; nil? so that it doesn't waste resources on uneeded clients
              ;; doesn't use constantly because that evals its body
              (transform [ATOM :aor-cache (keypath mod) :clients (keypath agent-name) nil?]
                         (fn [_] (aor/agent-client manager agent-name))
                         ui/system))

            ;; stale agents
            (let [stale-agents (clojure.set/difference
                                (set
                                 (select [ATOM :aor-cache (keypath mod) :clients MAP-KEYS]
                                         ui/system))
                                agent-names)]
              (doseq [stale-agent stale-agents]
                (transform [ATOM :aor-cache (keypath mod) :clients (keypath stale-agent)]
                           (fn [client]
                             (close! client)
                             NONE)
                           ui/system)))))))

    ;; stale modules
    (let [stale-modules (clojure.set/difference
                         (set (select [ATOM :aor-cache MAP-KEYS] ui/system))
                         modules)]
      (doseq [mod stale-modules]
        (transform [ATOM :aor-cache (keypath mod) :clients MAP-VALS] close! ui/system)
        (setval [ATOM :aor-cache (keypath mod)] NONE ui/system)))))

(defn start [ipc]
  (swap! ui/system assoc :jetty (jetty/run-jetty #'srv/handler
                                                 {:port 1974 ;; TODO make configurable
                                                  :join? false}))
  (swap! ui/system assoc :rama-client ipc)
  (swap! ui/system assoc :background-exec (ScheduledThreadPoolExecutor. 1))
  (.scheduleAtFixedRate
   ^ScheduledThreadPoolExecutor (:background-exec @ui/system)
   (fn [] (try
            (refresh-agent-modules!)
            (catch Throwable t
              (cljlogging/error t "Error in refreshing agent modules" {}))))
   0
   5
   TimeUnit/SECONDS))

(defn stop-ui []
  (transform [ATOM :aor-cache MAP-VALS :clients MAP-VALS] close! ui/system)
  (setval [ATOM :aor-cache MAP-VALS :clients MAP-VALS] NONE ui/system)
  (.stop ^org.eclipse.jetty.server.Server (:jetty @ui/system))
  (close! (:rama-client @ui/system))
  (.shutdownNow ^ScheduledThreadPoolExecutor (:background-exec @ui/system)))

(defn start-ui ^java.io.Closeable [ipc]
  (start ipc)
  (reify java.io.Closeable
    (close [this]
      (println "press enter to close the ui, default port is 1974")
      (read-line)
      (stop-ui)
      :closed)))

