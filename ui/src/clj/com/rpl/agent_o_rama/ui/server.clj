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
   [ring.middleware.not-modified :as not-modified]
   [reitit.coercion.malli :as rcm]
   [reitit.ring.coercion :as rrc]
   [malli.core :as mc]))

(defn my-handler [req]
  {:status 200
   :headers {"content-type" "text/plain"}
   :body "Hello World!"})

(defn get-user-handler [{:keys [parameters]}]
  (let [user-id (get-in parameters [:path :user-id])]
    (resp/response
     [{:user-id user-id :name "Alice" :email "alice@example.com"}
      {:user-id user-id :name "Alice" :email "alice@example.com"}])))

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
                         ["/api/*" {:handler (fn [_req] (resp/not-found ""))}]
                         ;; Return index.html for any non-API routes for History API routing
                         ["/*" {:get {:handler spa-index-handler}}]]
                        {:conflicts nil}))))
(defn app-routes []
  (ring/ring-handler
   (ring/router
    ["/api"
     ["/users/:user-id"
      {:get {:parameters {:path [:map [:user-id :int]]}
             :coercion rcm/coercion
             :handler #'get-user-handler}}]
     ["/items" {:post #'create-item-handler}]]
    {:data {:muuntaja m/instance
            :middleware [muuntaja/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]
            :coercion rcm/coercion}})
   default-handler))

(def handler (app-routes))
