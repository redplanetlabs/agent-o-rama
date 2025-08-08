(ns com.rpl.agent-o-rama.impl.ui.server
  (:require
   [ring.middleware.file :as ring-file]
   [ring.middleware.file-info :as ring-file-info]
   [reitit.ring :as ring]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.parameters :as parameters]
   [reitit.ring.middleware.exception :as exception]
   [muuntaja.core :as m]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.content-type :as content-type]
   [ring.middleware.not-modified :as not-modified]
   [reitit.coercion.malli :as rcm]
   [reitit.ring.coercion :as rrc]
   [malli.core :as mc]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]] ; Add this
   [clojure.string]
   [com.rpl.agent-o-rama.impl.ui.sente :as sente] ; Add this

   [com.rpl.agent-o-rama.impl.ui.agents :as agents]))

(defn spa-index-handler [_request]
  (-> (resp/resource-response "index.html")
      (resp/content-type "text/html")))

(def default-handler (ring/routes
                      (->
                       
                       ;; for serving shadow/watch dev files
                       (ring/create-file-handler
                        {:path ""
                         :root "public"}) ; /public
                       
                       ;; TODO make it so we only have one of these
                       
                       ;; for serving files out of the jar when used as library
                       (resource/wrap-resource "public") ; /resources/public
                       )
                      (ring/ring-handler
                       (ring/router
                        [""
                         ["/api/*" {:handler (fn [_req] (resp/not-found ""))}]
                         ;; Return index.html for any non-API routes for History API routing
                         ["/*" {:get {:handler spa-index-handler}}]]
                        {:conflicts nil}))))

(defn exception-handler [^Exception e request]
  (def e e)
  (let [sw (java.io.StringWriter.)
        pw (java.io.PrintWriter. sw)]
    (.printStackTrace e pw)
    {:status 500
     :headers {"Content-Type" "text/plain"}
     :body (.toString sw)}))

(def exception-middleware
  (exception/create-exception-middleware
   {::exception/default exception-handler}))

(defn app-routes []
  (ring/ring-handler
   (ring/router
    [""
     ;; START: Add Sente routes
     ["/chsk" {:get  {:handler #'sente/ring-ajax-get-or-ws-handshake}
               :post {:handler #'sente/ring-ajax-post}}]
     ;; END: Add Sente routes

     ["/api" ; This comes AFTER /chsk so /chsk isn't treated as an API call
      ["/agents/:module-id/:agent-name/graph"
       {:get {:handler #'agents/get-graph}}]
      ["/agents/:module-id/:agent-name/fork"
       {:post {:handler #'agents/fork}}]
      ["/agents/:module-id/:agent-name/invocations/:invoke-id/paginated"
       {:get {:parameters {:query [:map
                                   [:paginate-task-id {:optional true} int?]
                                   [:missing-node-id {:optional true} string?]]}
              :handler #'agents/invoke-paginated}}]]]
    {:data {:muuntaja m/instance
            :middleware [exception-middleware
                         parameters/parameters-middleware
                         muuntaja/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]
            :coercion rcm/coercion}})
   default-handler))

;; The main change is wrapping the entire router with `wrap-defaults`
;; This adds the necessary session middleware Sente requires.
;; We disable CSRF protection for development.
(def handler
  (-> (#'app-routes)
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:security :ssl-redirect] false)))))
