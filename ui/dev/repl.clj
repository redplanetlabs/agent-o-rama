(ns repl
  (:require
   [com.rpl.agent-o-rama.ui.server :as srv]
   [shadow.cljs.devtools.api :as shadow]
   [com.stuartsierra.component :as component]

   [ring.adapter.jetty :as jetty]))

(defrecord ShadowComponent []
  component/Lifecycle
  (start [component]
    (shadow/watch :frontend)
    (assoc component :shadow-started? true))
  
  (stop [component]
    (when (:shadow-started? component) (shadow/stop-worker :frontend))
    (dissoc component :shadow-started?)))

(defn new-shadow-component []
  (->ShadowComponent))

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

(defn new-system []
  (component/system-map
   :shadow (new-shadow-component)
   :webserver (new-webserver-component 3000 #'srv/handler)))

(defonce system (atom nil))

(defn stop []
  (when @system (reset! system (component/stop @system)))
  ::stopped)

(defn start []
  (when @system
    (stop))
  (reset! system (component/start (new-system)))
  ::started)

(defn go []
  (stop)
  (start))

(comment
  (go))
