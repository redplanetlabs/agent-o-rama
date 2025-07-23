(ns com.rpl.agent-o-rama.system
  (:require
   [com.stuartsierra.component :as component]
   [ring.adapter.jetty :as jetty]
   [com.rpl.rama :as r])
  (:import
   [java.util.concurrent ScheduledThreadPoolExecutor]))

(defonce system (atom nil))

(defrecord WebServerComponent [port handler jetty-server]
  component/Lifecycle
  (start [component]
    (println "Starting webserver on port" port)
    (let [server (jetty/run-jetty handler
                                  {:port port
                                   :join? false})]
      (-> component
          (assoc :aor-client-task (ScheduledThreadPoolExecutor. 1))
          (assoc :jetty-server server))))
  
  (stop [component]
    (.stop jetty-server)
    (assoc component :jetty-server nil)))

(defn new-webserver-component [port handler]
  (->WebServerComponent port handler nil))

(defrecord RamaClient []
  component/Lifecycle
  (start [component]
    (println "starting rama client")
    (assoc component :rama-client (r/open-cluster-manager-internal {"conductor.host" "localhost"})))
  
  (stop [component]
    (println "stopping rama client")
    (.close (:rama-client component))
    (assoc component :rama-client nil)))

(defn new-rama-client []
  (->RamaClient))
