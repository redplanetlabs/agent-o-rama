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
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [com.rpl.agent-o-rama.impl.ui.sente.adpater :as adapter]
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

;; Sente WebSocket setup
(let [{:keys [ch-recv send-fn connected-uids ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server! (get-sch-adapter)
                                         {:packer (sente-transit/get-transit-packer)})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

;; Sente event handlers
(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id)

(defn event-msg-handler
  [{:as ev-msg :keys [id ?data event ring-req ?reply-fn send-fn]}]
  (println "Sente event:" event)
  (-event-msg-handler ev-msg))

;; Default handler
(defmethod -event-msg-handler :default
  [{:keys [event id ?data ring-req ?reply-fn send-fn]}]
  (println "Unhandled event:" event))

;; API event: get agents
(defmethod -event-msg-handler :api/agents
  [{:keys [?reply-fn]}]
  (when ?reply-fn
    (let [result (agents/index {:parameters {}})]
      (?reply-fn (:body result)))))

;; Start the Sente event router
(def router_ (atom nil))

(defn stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-server-chsk-router! ch-chsk event-msg-handler)))

(defn app-routes []
  (ring/ring-handler
   (ring/router
    [""
     ;; Sente WebSocket routes
     ["/chsk" {:get {:handler ring-ajax-get-or-ws-handshake}
               :post {:handler ring-ajax-post}}]
     
     ["/api"
      ["/agents"
       {:get {:handler #'agents/index}}]
      ["/agents/:module-id/:agent-name/invocations"
       {:get {:handler #'agents/get-invokes}
        :post {:handler #'agents/manually-trigger-invoke}}]
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
            :middleware [;; Add anti-forgery for CSRF protection needed by Sente
                         anti-forgery/wrap-anti-forgery
                         exception-middleware
                         parameters/parameters-middleware
                         muuntaja/format-middleware
                         rrc/coerce-exceptions-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-response-middleware]
            :coercion rcm/coercion}})
   default-handler))

(def handler (#'app-routes))

;; Initialize Sente when the namespace loads
(start-router!)
