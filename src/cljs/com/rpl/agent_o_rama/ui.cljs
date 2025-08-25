(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]

   [com.rpl.agent-o-rama.ui.agents :as agents]
   [com.rpl.agent-o-rama.ui.config-page :as config-page]
   [com.rpl.agent-o-rama.ui.datasets :as datasets]
   ;; Replace wouter with reitit
   [reitit.core :as r]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [HomeIcon CpuChipIcon CircleStackIcon ChevronLeftIcon ChevronRightIcon
                                          RectangleStackIcon ChartBarIcon BeakerIcon Cog6ToothIcon]]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.stats :as stats]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.invocation-graph-view :refer [global-modal-component]]
   [com.rpl.agent-o-rama.ui.events])) ;; Ensure event handlers are registered at app startup

;; =============================================================================
;; ROUTE DEFINITIONS
;; =============================================================================

(def routes
  ["/"
   ["" {:name :home, :view agents/index}]
   ["agents"
    ["" {:name :agents/index, :view agents/index}]
    ["/:module-id"
     ;; Module-level routes with literal segments FIRST
     ["/datasets"
      ["" {:name :module/datasets, :view datasets/index}]
      ["/:dataset-id" {:name :module/dataset-detail, :view datasets/detail}]]
     ["/evaluations" {:name :module/evaluations, :view agents/evaluations}]
     ;; Agent routes with prefix to avoid conflicts
     ["/agent/:agent-name"
      ["" {:name :agent/detail, :view agents/agent}]
      ["/invocations"
       ["" {:name :agent/invocations, :view agents/invocations}]
       ["/:invoke-id" {:name :agent/invocation-detail, :view agents/invoke}]]
      ["/config" {:name :agent/config, :view config-page/config-page}]
      ["/stats" {:name :agent/stats, :view stats/stats}]]]]])

;; Store router instance globally for navigation
(defonce router-instance (atom nil))

;; =============================================================================
;; ROUTER WRAPPER COMPONENT
;; =============================================================================

(defui with-router [{:keys [routes children]}]
  (let [router (uix/use-memo #(rf/router routes) [routes])]
    (uix/use-effect
     #(do
        (reset! router-instance router)
        (rfe/start! router
                    (fn [new-match] (state/dispatch [:route/navigated new-match]))
                    {:use-fragment false}))
     [router])
    ($ :<> children)))

;; =============================================================================
;; NAVIGATION COMPONENTS
;; =============================================================================

;; Reusable nav-link component (changed from wouter/Link to anchor tag)
(defui nav-link [{:keys [href location collapsed? title children]}]
  (let [is-active? (or (= location href)
                       (and (not= href "/") (.startsWith location href)))
        link-classes (str "flex items-center rounded-md transition-colors text-sm font-medium "
                          (if collapsed?
                            "justify-center p-2 w-10 h-10"
                            "px-3 py-2")
                          (if is-active?
                            " bg-gray-300 text-gray-900"
                            " hover:bg-gray-200 text-gray-700"))]
    ($ :a {:href href :className link-classes :title (when collapsed? title)}
       (if collapsed?
         (first children) ; Only show the icon when collapsed
         children)))) ; Show icon and label

;; Agent-specific navigation component
(defui agent-context-nav [{:keys [module-id agent-name collapsed?]}]
  (let [location (or (get-in (state/use-sub [:route]) [:path]) "/")]
    ($ :<>
       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))

          ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/datasets")
                       :location location :collapsed? collapsed? :title "Datasets"}
             ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Datasets")))

          ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/evaluations")
                       :location location :collapsed? collapsed? :title "Evaluations"}
             ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Evaluations"))))

       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "AGENT"))

          ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")
                       :location location :collapsed? collapsed? :title "Invocations"}
             ($ RectangleStackIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Invocations")))

          ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/config")
                       :location location :collapsed? collapsed? :title "Config"}
             ($ Cog6ToothIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Config")))))))

;; Module-specific navigation component
(defui module-context-nav [{:keys [module-id collapsed?]}]
  (let [location (or (get-in (state/use-sub [:route]) [:path]) "/")]
    ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
       (when-not collapsed?
         ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/datasets")
                    :location location :collapsed? collapsed? :title "Datasets"}
          ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Datasets")))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/evaluations")
                    :location location :collapsed? collapsed? :title "Evaluations"}
          ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Evaluations"))))))

(defui sidebar-nav []
  (let [match (state/use-sub [:route])
        location (or (:path match) "/")
        {:keys [module-id agent-name]} (or (:path-params match) {})
        route-name (get-in match [:data :name])
        is-agent-context? (and module-id agent-name)
        is-module-context? (and module-id (not agent-name))
        [collapsed? set-collapsed] (common/use-local-storage "sidebar-collapsed?" false)
        toggle-collapsed #(set-collapsed (not collapsed?))]

    ($ :div {:className (str "h-screen flex flex-col bg-gray-100 transition-all duration-300 "
                             (if collapsed? "w-16" "w-64"))}
       ;; Header
       ($ :div.flex.items-center.justify-between.p-4.border-b.border-gray-200.overflow-hidden
          (when-not collapsed?
            ($ :img {:src "/logo-black.png"
                     :alt "Agent-O-Rama"
                     :className "h-8 max-w-48 object-contain"}))
          ($ :button {:onClick toggle-collapsed
                      :className "p-2 rounded-md hover:bg-gray-200 transition-colors"
                      :title (if collapsed? "Expand sidebar" "Collapse sidebar")}
             (if collapsed?
               ($ ChevronRightIcon {:className "h-5 w-5"})
               ($ ChevronLeftIcon {:className "h-5 w-5"}))))

       ;; Navigation
       ($ :nav.flex-1.p-3
          ($ :div.space-y-2
             ($ nav-link {:href "/" :location location :collapsed? collapsed? :title "Overview"}
                ($ HomeIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed? ($ :span.ml-3 "Overview"))))

          (when is-agent-context?
            ($ agent-context-nav {:module-id module-id
                                  :agent-name agent-name
                                  :collapsed? collapsed?}))

          (when is-module-context?
            ($ module-context-nav {:module-id module-id
                                   :collapsed? collapsed?}))))))

;; =============================================================================
;; BREADCRUMB COMPONENT
;; =============================================================================

(defui breadcrumb []
  (let [match (state/use-sub [:route])
        {:keys [module-id agent-name dataset-id invoke-id]} (or (:path-params match) {})
        route-name (get-in match [:data :name])

        ;; Build breadcrumb items based on current route
        build-breadcrumbs (fn []
                            (let [items []]
                              (cond
                                ;; Agent invocation detail
                                (and module-id agent-name invoke-id)
                                [{:label (str (common/url-decode module-id) ":" (common/url-decode agent-name))
                                  :path (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name))}
                                 {:label "Invocations"
                                  :path (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")}
                                 {:label (common/url-decode invoke-id)
                                  :path nil}] ; Current page

                                ;; Agent detail pages
                                (and module-id agent-name)
                                [{:label (str (common/url-decode module-id) ":" (common/url-decode agent-name))
                                  :path (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name))}
                                 {:label (case route-name
                                           :agent/invocations "Invocations"
                                           :agent/config "Config"
                                           :agent/stats "Stats"
                                           "Agent")
                                  :path nil}] ; Current page

                                ;; Dataset detail
                                (and module-id dataset-id)
                                [{:label (common/url-decode module-id)
                                  :path (str "/agents/" (common/url-encode module-id))}
                                 {:label "Datasets"
                                  :path (str "/agents/" (common/url-encode module-id) "/datasets")}
                                 {:label (common/url-decode dataset-id)
                                  :path nil}] ; Current page

                                ;; Module level pages
                                (and module-id)
                                [{:label (common/url-decode module-id)
                                  :path (str "/agents/" (common/url-encode module-id))}
                                 {:label (case route-name
                                           :module/datasets "Datasets"
                                           :module/evaluations "Evaluations"
                                           "Module")
                                  :path nil}] ; Current page

                                ;; Default
                                :else [])))

        breadcrumb-items (build-breadcrumbs)]

    ($ :div.bg-gray-100.px-4.py-2.text-sm.text-gray-600
       ($ :div.flex.items-center.space-x-2
          ;; Home link (always present)
          ($ :a {:href "/" :className "text-blue-600 hover:text-blue-800"} "Home")

          ;; Build breadcrumbs
          (when (seq breadcrumb-items)
            (map-indexed
             (fn [idx item]
               (let [is-last? (= idx (dec (count breadcrumb-items)))]
                 (list
                  ;; Separator
                  ($ :span {:key (str "sep-" idx)} " › ")
                  ;; Link or text
                  (if (and (:path item) (not is-last?))
                    ;; Clickable link
                    ($ :a {:key (str "crumb-" idx)
                           :href (:path item)
                           :className "text-blue-600 hover:text-blue-800"}
                       (:label item))
                    ;; Current page - not clickable
                    ($ :span {:key (str "crumb-" idx) :className "text-gray-500"}
                       (:label item))))))
             breadcrumb-items))))))

;; =============================================================================
;; MAIN APP COMPONENT
;; =============================================================================

(defui app []
  (let [match (state/use-sub [:route])
        view (get-in match [:data :view])]
    ($ :div.flex.h-screen.bg-gray-50
       ($ sidebar-nav)
       ($ :div.flex-1.flex.flex-col.min-h-0
          ($ breadcrumb)
          ($ :div.flex-1.overflow-auto
             (if view
               ($ view)
               ;; 404 component
               ($ :div.p-8.text-center "Route not found")))
          ($ global-modal-component)))))

(defn init []
  (sente/init!)
  (uix.dom/render-root
   ($ with-router {:routes routes}
      ($ app))
   (uix.dom/create-root
    (.getElementById js/document "root"))))