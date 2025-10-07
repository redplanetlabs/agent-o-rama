(ns com.rpl.agent-o-rama.impl.ui.server
  (:require
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]
   ;; Load all handler namespaces to register their defmethods
   ;; Load all handler namespaces to register their defmethods
   [com.rpl.agent-o-rama.impl.ui.handlers.agents]
   [com.rpl.agent-o-rama.impl.ui.handlers.analytics]
   [com.rpl.agent-o-rama.impl.ui.handlers.config]
   [com.rpl.agent-o-rama.impl.ui.handlers.datasets]
   [com.rpl.agent-o-rama.impl.ui.handlers.evaluators]
   [com.rpl.agent-o-rama.impl.ui.handlers.invocations]
   [com.rpl.agent-o-rama.impl.ui.handlers.experiments]
   [com.rpl.agent-o-rama.impl.ui.handlers.http :as http]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.file :as ring-file]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.cors :refer [wrap-cors]]))

(defn spa-index-handler
  [_request]
  (-> (resp/resource-response "index.html")
      (resp/content-type "text/html")))

(def file-handler
  (-> (fn [_] nil)
      ;; Fallback to serving source assets for development.
      (resource/wrap-resource "assets")
      ;; First, try to serve from "public" for compiled JS and other assets.
      (resource/wrap-resource "public")))

(defn routes
  [request]
  (let [uri    (:uri request)
        method (:request-method request)]
    (cond
      ;; Sente routes are the only specific routes we need
      (= uri "/chsk")
      (case method
        :get (sente/ring-ajax-get-or-ws-handshake request)
        :post (sente/ring-ajax-post request))

      ;; Dataset export
      (and (= method :get)
           (re-matches #"/api/datasets/.+/.+/export" uri))
      (http/handle-dataset-export request)

      ;; Dataset import - back to including dataset-id in path
      (and (= method :post)
           (re-matches #"/api/datasets/.+/.+/import" uri))
      (http/handle-dataset-import request)

      ;; For any other route, return nil to let the next handler take over.
      :else nil)))

(defn app-handler
  [request]
  (or
   ;; 1. Try to serve a static file from "public" or "resources/public".
   (file-handler request)
   ;; 2. Try our specific Sente routes.
   (routes request)
   ;; 3. As a fallback for any other GET request, serve the SPA's index.html.
   ;; This enables client-side routing.
   (when (= :get (:request-method request))
     (spa-index-handler request))))

;; Keep wrap-defaults for Sente's session management
(def handler
  (-> #'app-handler
      wrap-multipart-params
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:security :ssl-redirect] false)))
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods #{:get :post :put :delete :options}
                 :access-control-allow-headers #{"Content-Type"
                                                 "Authorization"
                                                 "X-CSRF-Token"
                                                 "x-requested-with"})))
