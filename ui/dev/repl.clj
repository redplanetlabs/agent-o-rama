(ns repl
  (:require
   [com.rpl.agent-o-rama.ui.server :as srv]
   [com.rpl.agent-o-rama.system :as sys]
   [shadow.cljs.devtools.api :as shadow]
   [com.stuartsierra.component :as component]))

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


(defn new-system []
  (component/system-map
   :shadow (new-shadow-component)
   :webserver (sys/new-webserver-component 2999 #'srv/handler)
   :rama-client (sys/new-rama-client)))

(defn stop []
  (when @sys/system (reset! sys/system (component/stop @sys/system)))
  ::stopped)

(defn start []
  (when @sys/system
    (stop))
  (reset! sys/system (component/start (new-system)))
  ::started)

(defn go []
  (stop)
  (start))

(comment
  (-> sys/system deref :rama-client)
  (go))
