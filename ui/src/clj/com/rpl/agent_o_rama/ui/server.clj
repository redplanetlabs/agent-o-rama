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
    (resp/response
     [{:user-id user-id :name "Alice" :email "alice@example.com"}])))

(defn create-item-handler [request]
  (let [item-data (:body-params request)]
    (:status 201 :body item-data)))

(defn spa-index-handler [_request]
  (-> (resp/resource-response "index.html")
      (resp/content-type "text/html")))

(def default-handler (ring/routes
                      (ring/create-file-handler {:path ""
                                                 :root "public"})
                      (ring/ring-handler
                       (ring/router
                        [""
                         ["/api/*" {:handler (fn [_req] (resp/not-found "arst"))}]
                         ;; Return index.html for any non-API routes for History API routing
                         ["/*" {:get {:handler spa-index-handler}}]]
                        {:conflicts nil}))))
(defn app-routes []
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/users/:user-id" {:get #'get-user-handler}]
     ["/items" {:post #'create-item-handler}]]
    
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware]}})
   default-handler))

(def handler (app-routes))
