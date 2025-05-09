(ns com.rpl.agent-o-rama.ui.server
  (:require
   [ring.middleware.file :as ring-file]
   [ring.middleware.file-info :as ring-file-info]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [muuntaja.core :as m]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.content-type :as content-type]
   [ring.middleware.not-modified :as not-modified]))

(defn my-handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "Hello World!"})

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
  ;; TODO https://github.com/metosin/example-project/blob/92aaeef26483ba93cd6b5faa89eaeba3911d50fc/src/clj/backend/routes.clj#L99-L110
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

(def handler
  (-> (app-routes)
      (ring-file/wrap-file "public")
      (ring-file-info/wrap-file-info)))
