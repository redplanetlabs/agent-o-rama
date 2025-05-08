(ns backend.core
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :as httpkit]))


(defrecord HttpKitWebServer [port handler]
  component/Lifecycle
  (start [this]
    (println (str "Starting http-kit on port " port))
    (if (:server-stopper this)
      this 
      (assoc this :server-stopper (httpkit/run-server (:handler this) {:port port}))))
  
  (stop [this]
    (println "Stopping http-kit")
    (if-let [stop-fn (:server-stopper this)]
      (do
        (stop-fn)
        (assoc this :server-stopper nil))
      this)))

(defrecord ReititHandler []
  component/Lifecycle
  (start [this] (fn [req] {:status 200 :body "hello world"}))
  (stop [this]))

(defn agentorama [config]
  (component/system-map
   :handler (map->ReititHandler {}) 
   :web-server (component/using
                (map->HttpKitWebServer {:port (:port config)})
                [:handler])))
