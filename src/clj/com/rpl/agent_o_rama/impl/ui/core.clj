(ns com.rpl.agent-o-rama.impl.ui.core
  (:use
   [com.rpl.rama]
   [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.ui.server :as srv]
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [clojure.tools.logging :as cljlogging]
   [org.httpkit.server :as http-kit])
  (:import
   [java.lang
    AutoCloseable]
   [java.util.concurrent
    ScheduledThreadPoolExecutor
    TimeUnit]))

(defn refresh-agent-modules!
  []
  (let [rama-client (ui/get-object :rama-client)
        modules     (deployed-module-names rama-client)
        aor-modules (set (filter #(queries/has-aor-modules? rama-client %) modules))]
    
    ;; so that get-object doesn't throw on :aor-cache on fresh startup
    (setval [ATOM :aor-cache nil?] {} ui/system)
    
    (cljlogging/trace "Refreshing agent from modules" {:modules modules})
    (doseq [mod aor-modules]
      ;; nil? check only creates manager when it's not already cached.
      (transform [ATOM :aor-cache (keypath mod) :manager nil?]
                 (fn [_] (aor/agent-manager rama-client mod))
                 ui/system)
      
      (let [manager (select-one [ATOM :aor-cache (keypath mod) :manager] ui/system)
            agent-names (aor/agent-names manager)]
        (cljlogging/trace "Found agents" {:agent-names agent-names :module mod})
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
                       ui/system)))))

    ;; stale modules - compares against aor-modules to handle modules that
    ;; were updated to no longer have AOR support
    (let [stale-modules (clojure.set/difference
                         (set (select [ATOM :aor-cache MAP-KEYS] ui/system))
                         aor-modules)]
      (doseq [mod stale-modules]
        (transform [ATOM :aor-cache (keypath mod) :clients MAP-VALS] close! ui/system)
        (setval [ATOM :aor-cache (keypath mod)] NONE ui/system)))))

(defn start [ipc port]
  (sente/start-sente!)
  (swap! ui/system assoc :server (http-kit/run-server #'srv/handler
                                                     {:port port
                                                      :join? false}))
  (swap! ui/system assoc :rama-client ipc)
  (swap! ui/system assoc :background-exec (ScheduledThreadPoolExecutor. 1))
  (.scheduleWithFixedDelay
   ^ScheduledThreadPoolExecutor (:background-exec @ui/system)
   (fn []
     (try
       (refresh-agent-modules!)
       (catch Throwable t
         (cljlogging/error t "Error in refreshing agent modules" {}))))
   0
   5
   TimeUnit/SECONDS))

(defn stop-ui []
  (sente/stop-sente!)
  ;; Gracefully shutdown executor and wait for in-flight refresh task to complete
  ;; before closing clients (avoids race condition with cluster shutdown)
  (when-let [exec ^ScheduledThreadPoolExecutor (:background-exec @ui/system)]
    (.shutdown exec)
    (.awaitTermination exec 10 TimeUnit/SECONDS))
  (transform [ATOM :aor-cache MAP-VALS :clients MAP-VALS] close! ui/system)
  (setval [ATOM :aor-cache MAP-VALS :clients MAP-VALS] NONE ui/system)
  ;; Clear managers/clients so fresh IPC creates new ones on restart.
  (setval [ATOM :aor-cache] {} ui/system)
  (when-let [stop-server (:server @ui/system)]
    (stop-server)))

(defn start-ui
  ^AutoCloseable
  ([ipc] (start-ui ipc nil))
  ([ipc options]
   (let [options (merge {:port 1974} options)]
     (cljlogging/info "Starting Agent-o-rama UI on port" (:port options))
     (start ipc (:port options))
     (reify
      AutoCloseable
      (close [_this]
        (when-not (:no-input-before-close options)
          (print "press enter to close the ui, default port is 1974")
          ;; this flush is necessary for Java API, not for Clojure API
          (flush)
          (read-line))
        (stop-ui)
        :closed)))))

(defn -main
  "Main entry point for the Agent-o-rama UI"
  [port]
  (let [port (Long/parseLong port)
        cluster-manager (open-cluster-manager {"conductor.host" "localhost"})]
    (cljlogging/info "Starting Agent-o-rama UI...")
    (start-ui cluster-manager {:port port})))
