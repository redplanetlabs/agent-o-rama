(ns com.rpl.agent-o-rama.impl.ui.server
  (:require
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.file :as ring-file]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]))

(defn spa-index-handler [_request]
  (-> (resp/resource-response "index.html")
      (resp/content-type "text/html")))

(def file-handler
  (-> (fn [_] nil)
      (resource/wrap-resource "public")))

(defn routes [request]
  (let [uri (:uri request)
        method (:request-method request)]
    (cond
      ;; Sente routes are the only specific routes we need
      (= uri "/chsk")
      (case method
        :get (sente/ring-ajax-get-or-ws-handshake request)
        :post (sente/ring-ajax-post request))

      ;; For any other route, return nil to let the next handler take over.
      :else nil)))

(defn app-handler [request]
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
      (wrap-defaults (-> site-defaults
                         (assoc-in [:security :anti-forgery] false)
                         (assoc-in [:security :ssl-redirect] false)))))
