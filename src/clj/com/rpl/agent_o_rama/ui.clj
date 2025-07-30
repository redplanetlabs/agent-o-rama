(ns com.rpl.agent-o-rama.ui
  (:use
   [com.rpl.rama]
   [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.server :as srv]
   [clojure.tools.logging :as cljlogging]
   [ring.adapter.jetty :as jetty]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor TimeUnit]))

(defonce system (atom {}))

(defn get-object [k]
  (if-let [v (get @system k)]
    v
    (throw (ex-info "not found" {:key k :availible-keys (keys @system)}))))

(defn refresh-agent-modules! []
  (let [rama-client (get-object :rama-client)
        modules (deployed-module-names rama-client)]
    (doseq [mod modules]
      (let [manager (try
                      (aor/agent-manager rama-client mod)
                      (catch Exception e ::no-aor))]
        (when-not (= ::no-aor manager)
          (setval [ATOM :aor-cache (keypath mod) :manager]
                  manager
                  system)
          (let [agent-names (aor/agent-names manager)]
            (doseq [agent-name agent-names]
              ;; nil? so that it doesn't waste resources on uneeded clients
              ;; doesn't use constantly because that evals its body
              (transform [ATOM :aor-cache (keypath mod) :clients (keypath agent-name) nil?]
                         (fn [_] (aor/agent-client manager agent-name))
                         system))

            ;; stale agents
            (let [stale-agents (clojure.set/difference
                                (set
                                 (select [ATOM :aor-cache (keypath mod) :clients MAP-KEYS]
                                         system))
                                agent-names)]
              (doseq [stale-agent stale-agents]
                (transform [ATOM :aor-cache (keypath mod) :clients (keypath stale-agent)]
                           (fn [client]
                             (close! client)
                             NONE)
                           system)))))))

    ;; stale modules
    (let [stale-modules (clojure.set/difference
                         (set (select [ATOM :aor-cache MAP-KEYS] system))
                         modules)]
      (doseq [mod stale-modules]
        (transform [ATOM :aor-cache (keypath mod) :client MAP-VALS] close! system)
        (setval [ATOM :aor-cache (keypath mod)] NONE system)))))

(defn start []
  (shadow.cljs.devtools.server/start!)
  (shadow/watch :frontend)
  (swap! system assoc :jetty (jetty/run-jetty #'srv/handler
                                              {:port 1974 ;; TODO make configurable
                                               :join? false}))
  (swap! system assoc :rama-client (open-cluster-manager-internal {"conductor.host" "localhost"}))
  (swap! system assoc :background-exec (ScheduledThreadPoolExecutor. 1))
  (.scheduleAtFixedRate
   ^ScheduledThreadPoolExecutor (:background-exec @system)
   (fn [] (try
            (refresh-agent-modules!)
            (catch Throwable t
              (cljlogging/error t "Error in refreshing agent modules" {}))))
   0
   5
   TimeUnit/SECONDS))

(defn stop []
  (.stop ^org.eclipse.jetty.server.Server (:jetty @system))
  (close! (:rama-client @system))
  (.shutdownNow ^ScheduledThreadPoolExecutor (:background-exec @system)))

(comment
  (start)
  (stop))
