(ns backend.router
  (:require [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [reitit.ring.middleware.parameters :as parameters]
            [muuntaja.core :as m]
            [ring.util.response :as resp]
            [ring.middleware.resource :as resource]
            [ring.middleware.content-type :as content-type]
            [ring.middleware.not-modified :as not-modified]
            [com.stuartsierra.component :as component]))

(defn get-user-handler [request]
  (let [user-id (get-in request [:path-params :user-id])]
    (resp/response {:user-id user-id :name "Alice" :email "alice@example.com"})))

(defn create-item-handler [request]
  (let [item-data (:body-params request)]
    (println "Creating item:" item-data)
    (:status 201 :body item-data)))


(defn spa-index-handler [_request]
  (-> (resp/resource-response "public/index.html")
      (resp/content-type "text/html")))

(defn app-routes []
  (ring/router
   [["/api"
     {:middleware []
      :conflicting true}
     ["/users/:user-id" {:get #'get-user-handler}]
     ["/items" {:post #'create-item-handler}]]
    ["/*" {:get {:handler spa-index-handler}
           :conflicting true}]]
   {:data {:muuntaja m/instance
           :middleware [parameters/parameters-middleware 
                        muuntaja/format-negotiate-middleware
                        muuntaja/format-response-middleware
                        muuntaja/format-request-middleware
                        #(resource/wrap-resource % "public")
                        content-type/wrap-content-type 
                        not-modified/wrap-not-modified]}}))

(defn app []
  (ring/ring-handler
    (app-routes)
    (ring/routes
      (ring/create-resource-handler {:path "/"}) ; Serves static resources, redundant if wrap-resource is used above but good for root
      (ring/create-default-handler ; Handles 404s, 405s etc.
        {:not-found (constantly (-> (resp/response "Sorry, page not found!")
                                    (resp/status 404)
                                    (resp/content-type "text/plain")))}))))

(defrecord ReititHandler []
  component/Lifecycle
  (start [this] (app))
  (stop [this]))
