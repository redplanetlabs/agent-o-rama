(ns user
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.namespace.repl :refer (refresh)]
            [backend.core :as backend]))

(def system nil)

(defn init []
  (alter-var-root #'system
    (constantly (backend/agentorama {:host "dbhost.com" :port 8079}))))

(defn start []
  (alter-var-root #'system component/start))

(defn stop []
  (alter-var-root #'system
    (fn [s] (when s (component/stop s)))))

(defn go []
  (init)
  (start))

(defn reset []
  (stop)
  (refresh :after 'user/go))

(comment
  (stop)
  (reset))
