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
   [malli.core :as mc]

   [com.rpl.agent-o-rama.ui.agents :as agents]
   [com.rpl.agent-o-rama.ui.datasets :as datasets]))

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
     ["/agents"
      {:get {:handler #'agents/index}}]
     ["/agents/:module-id/:agent-id/invocations"
      {:get {:handler #'agents/get-invokes}}]
     ["/agents/:module-id/:agent-id/graph"
      {:get {:handler #'agents/get-graph}}]
     ["/agents/:module-id/:agent-id/invocations/:invoke-id"
      {:get {:handler #'agents/invoke}}]
     ["/agents/:module-id/:agent-id/invocations/:invoke-id/paginated"
      {:get {:parameters {:query [:map
                                  [:depth int?]
                                  [:start-node-id {:optional true} string?]]}
             :handler #'agents/invoke-paginated}}]
     
     ["/agents/:module-id/:agent-id/datasets"
      {:get {:handler #'datasets/index}}]
     ["/agents/:module-id/:agent-id/evaluate"
      {:post {:handler #'datasets/start-evaluation}}]

     ;; Dataset management routes
     ["/datasets"
      {:get {:handler #'datasets/list-datasets}
       :post {:handler #'datasets/create-dataset}}]
     ["/datasets/:id"
      {:get {:handler #'datasets/get-dataset}
       :put {:handler #'datasets/update-dataset}
       :delete {:handler #'datasets/delete-dataset}}]
     ["/datasets/:id/entries"
      {:get {:parameters {:query [:map
                                  [:limit {:optional true} int?]
                                  [:offset {:optional true} int?]]}
             :handler #'datasets/get-dataset-entries}
       :post {:handler #'datasets/add-dataset-entry}}]

     ;; Evaluation routes  
     ["/evaluations"
      {:get {:parameters {:query [:map
                                  [:dataset-id {:optional true} string?]]}
             :handler #'datasets/list-evaluations}
       :post {:handler #'datasets/start-evaluation}}]
     ["/evaluations/:id"
      {:get {:handler #'datasets/get-evaluation}}]]
    {:data {:muuntaja m/instance
            :middleware [parameters/parameters-middleware
                         muuntaja/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]
            :coercion rcm/coercion}})
   default-handler))

(def handler (#'app-routes))
