(ns com.rpl.agent-o-rama.system
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]
            [com.rpl.rama :as r]))

(defrecord WebServerComponent [port handler jetty-server]
  component/Lifecycle
  (start [component]
    (if jetty-server
      (do
        (println "Webserver already running on port" port)
        component)
      (do
        (println "Starting webserver on port" port)
        (let [server (jetty/run-jetty handler
                                      {:port port
                                       :join? false})]
          (assoc component :jetty-server server)))))
  
  (stop [component]
    (when jetty-server
      (println "Stopping webserver")
      (.stop jetty-server))
    (assoc component :jetty-server nil)))

(defn new-webserver-component [port handler]
  (->WebServerComponent port handler nil))

(defrecord RamaClient []
  component/Lifecycle
  (start [component]
    (assoc component :rama-client #_(r/open-cluster-manager) ::TODO))
  
  (stop [component]
    ::TODO))

(defn new-rama-client []
  (->RamaClient))

