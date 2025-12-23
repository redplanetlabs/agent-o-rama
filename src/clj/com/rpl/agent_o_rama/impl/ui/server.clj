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
   [com.rpl.agent-o-rama.impl.ui.handlers.experiments]
   [com.rpl.agent-o-rama.impl.ui.handlers.human-feedback]
   [com.rpl.agent-o-rama.impl.ui.handlers.invocations]
   [com.rpl.agent-o-rama.impl.ui.handlers.streaming]
   [com.rpl.agent-o-rama.impl.ui.handlers.http :as http]
   [ring.util.response :as resp]
   [ring.middleware.resource :as resource]
   [ring.middleware.file :as ring-file]
   [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
   [ring.middleware.multipart-params :refer [wrap-multipart-params]]
   [ring.middleware.cors :refer [wrap-cors]]
   [clojure.string :as str]
   [clojure.java.io :as io]
   [clojure.edn :as edn]))

(defn- cache-control-for-uri
  "Returns appropriate Cache-Control header value based on URI/content-type.
   - JS/CSS: Long cache (1 year) since we use content-hashed filenames
   - HTML: No cache (always validate for fresh asset references)
   - Images/fonts: Long cache"
  [uri content-type]
  (cond
    ;; HTML - always revalidate to get fresh JS references
    (or (str/ends-with? uri ".html")
        (str/includes? (str content-type) "text/html"))
    "no-cache"

    ;; JS/CSS - cache for 1 year (use content hashing for cache busting)
    (or (str/ends-with? uri ".js")
        (str/ends-with? uri ".css")
        (str/includes? (str content-type) "javascript")
        (str/includes? (str content-type) "text/css"))
    "public, max-age=31536000, immutable"

    ;; Images and fonts - cache for 1 year
    (or (str/ends-with? uri ".png")
        (str/ends-with? uri ".jpg")
        (str/ends-with? uri ".jpeg")
        (str/ends-with? uri ".gif")
        (str/ends-with? uri ".svg")
        (str/ends-with? uri ".ico")
        (str/ends-with? uri ".woff")
        (str/ends-with? uri ".woff2")
        (str/ends-with? uri ".ttf")
        (str/ends-with? uri ".eot"))
    "public, max-age=31536000, immutable"

    ;; Default - short cache with revalidation
    :else "public, max-age=3600"))

(defn wrap-cache-control
  "Middleware that adds Cache-Control headers to static asset responses."
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (let [uri (:uri request)
            content-type (get-in response [:headers "Content-Type"])
            cache-control (cache-control-for-uri uri content-type)]
        (resp/header response "Cache-Control" cache-control)))))

(defn- get-js-filename
  "Reads the shadow-cljs manifest to get the hashed JS filename.
   Throws if manifest doesn't exist or is malformed."
  []
  (let [manifest-resource (io/resource "public/manifest.edn")]
    (when-not manifest-resource
      (throw (ex-info "manifest.edn not found - run shadow-cljs release :frontend" {})))
    (let [manifest (edn/read-string (slurp manifest-resource))
          output-name (:output-name (first manifest))]
      (when-not output-name
        (throw (ex-info "Could not read :output-name from manifest.edn" {:manifest manifest})))
      output-name)))

(defn- render-index-html
  "Renders index.html with the correct hashed JS filename."
  []
  (-> (io/resource "index.html")
      slurp
      (str/replace "{{MAIN_JS}}" (get-js-filename))))

(defn spa-index-handler
  "Serves the SPA index.html and ensures session cookie is set.
   This is critical for Firefox AJAX mode - the session must be established
   on the initial page load since Sente's async responses can't set cookies."
  [request]
  (let [response (-> (resp/response (render-index-html))
                     (resp/content-type "text/html")
                     (resp/header "Cache-Control" "no-cache"))]
    ;; Always set session on index.html response to establish cookie
    (if (get-in request [:session :uid])
      response
      (assoc response :session {:uid (str (random-uuid))}))))

(defn file-handler
  "Serves static files from public and assets directories with cache headers."
  [request]
  (when-let [response (or ((resource/wrap-resource (fn [_] nil) "public") request)
                          ((resource/wrap-resource (fn [_] nil) "assets") request))]
    (let [uri (:uri request)
          content-type (get-in response [:headers "Content-Type"])
          cache-control (cache-control-for-uri uri content-type)]
      (resp/header response "Cache-Control" cache-control))))

(defn- ensure-session-uid
  "Middleware that ensures the session has a unique :uid.
  Creates one if not present, using a random UUID."
  [handler]
  (fn [request]
    (if (get-in request [:session :uid])
      ;; Session already has uid, proceed normally
      (handler request)
      ;; Create new uid and add to session
      (let [new-uid (str (random-uuid))
            response (handler (assoc-in request [:session :uid] new-uid))]
        ;; Ensure the new uid is persisted in the session cookie
        (when response
          (assoc response :session (assoc (:session response (:session request)) :uid new-uid)))))))

(defn routes
  [request]
  (let [uri (:uri request)
        method (:request-method request)]
    (cond
      ;; Sente routes are the only specific routes we need
      (= uri "/chsk")
      (case method
        :get ((ensure-session-uid sente/ring-ajax-get-or-ws-handshake) request)
        :post ((ensure-session-uid sente/ring-ajax-post) request))

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
                                                 "x-requested-with"})
      ;; Add cache control headers as outermost middleware
      wrap-cache-control))
