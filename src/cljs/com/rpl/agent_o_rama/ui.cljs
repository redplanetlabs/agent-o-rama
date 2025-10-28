(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]

   [com.rpl.agent-o-rama.ui.agents :as agents]
   [com.rpl.agent-o-rama.ui.config-page :as config-page]
   [com.rpl.agent-o-rama.ui.global-config-page :as global-config-page]
   [com.rpl.agent-o-rama.ui.datasets :as datasets]
   [com.rpl.agent-o-rama.ui.evaluators :as evaluators]
   [com.rpl.agent-o-rama.ui.module-page :as module-page]
   [com.rpl.agent-o-rama.ui.experiments.index :as experiments]
   [com.rpl.agent-o-rama.ui.experiments.comparative :as comparative-experiments]
   [com.rpl.agent-o-rama.ui.experiments.regular-detail :as experiments-detail]
   [com.rpl.agent-o-rama.ui.experiments.comparative-detail :as comparative-experiments-detail]
   [com.rpl.agent-o-rama.ui.analytics :as analytics]
   [reitit.core :as r]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   [reitit.coercion :as coercion]
   [reitit.coercion.malli :as malli]
   ["@heroicons/react/24/outline" :refer [HomeIcon CpuChipIcon CircleStackIcon ChevronLeftIcon ChevronRightIcon
                                          RectangleStackIcon ChartBarIcon BeakerIcon Cog6ToothIcon BoltIcon]]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.forms :refer [global-modal-component]]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.events] ;; Ensure event handlers are registered at app startup
   [com.rpl.agent-o-rama.ui.experiments.forms]
   [com.rpl.agent-o-rama.ui.datasets.add-from-trace]
   [com.rpl.agent-o-rama.ui.rules :as rules]
   [com.rpl.agent-o-rama.ui.action-log :as action-log]))

(def routes
  [""
   ["/" {:name :home, :views [agents/index]}]
   ["/agents"
    ["" {:name :agents/index, :views [agents/index]}]
    ["/:module-id"
     ["" {:name :module/detail, :views [module-page/index]}]
     ["/datasets"
      ["" {:name :module/datasets, :views [datasets/index]}]
      ["/:dataset-id"
       {:name :module/dataset, :views [datasets/detail]}
       ["" {:name :module/dataset-detail, :views [datasets/detail-examples-router]}]
       ["/examples" {:name :module/dataset-detail.examples, :views [datasets/detail-examples-router]}]
       ["/experiments" {:name :module/dataset-detail.experiments, :views [experiments/index]}]
       ["/experiments/:experiment-id" {:name :module/dataset-detail.experiment-detail, :views [experiments-detail/regular-experiment-detail-page]}]
       ["/comparative-experiments" {:name :module/dataset-detail.comparative-experiments, :views [comparative-experiments/index]}]
       ["/comparative-experiments/:experiment-id" {:name :module/dataset-detail.comparative-experiment-detail, :views [comparative-experiments-detail/detail-page]}]]]
     ["/evaluations" {:name :module/evaluations, :views [evaluators/index]}]
     ["/global-config" {:name :module/global-config, :views [global-config-page/page]}]
     ["/agent/:agent-name"
      ["" {:name :agent/detail, :views [agents/agent]}]
      ["/invocations"
       ["" {:name :agent/invocations, :views [agents/invocations]}]
       ["/:invoke-id" {:name :agent/invocation-detail, :views [agents/invoke]}]]
      ["/analytics" {:name :agent/analytics, :views [analytics/analytics-page]}]
      ["/rules"
       ["" {:name :agent/rules, :views [rules/rules-page]}]
       ["/:rule-name/action-log" {:name :agent/action-log, :views [action-log/action-log-page]}]]
      ["/config" {:name :agent/config, :views [config-page/config-page]}]]]]])

(defui ViewStack []
  (let [match (state/use-sub [:route])
        ;; Get the stack of views to render from the route data.
        ;; Defaults to an empty vector if no views are defined for the route.
        view-stack (get-in match [:data :views] [])
        path-params (:path-params match)]
    ;; Render the views as siblings inside a single div.
    ;; Each view component is responsible for subscribing to the parts
    ;; of the app-db it needs, including the route data itself.
    ($ :div.flex-1.overflow-auto
       (if (seq view-stack)
         (for [view-component view-stack]
           ($ view-component (merge {:key (str view-component) ;; React needs a key for lists
                                     :match match}
                                    path-params)))
         ;; Render a fallback if no views are matched
         ($ :div.p-8.text-center "Route not found or has no associated view.")))))

(defui main-layout []
  ($ :div.flex.h-screen.bg-gray-50
     ($ sidebar-nav)
     ($ :div.flex-1.flex.flex-col.min-h-0.min-w-0
        ($ breadcrumb)
        ($ :div.flex-1.overflow-auto
           ($ ViewStack))
        ($ global-modal-component))))

(defonce router-instance (atom nil))

(defui with-router [{:keys [routes children]}]
  (let [router (uix/use-memo #(rf/router routes {:data {:coercion malli/coercion}}) [routes])]
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
        link-classes (common/cn
                      "flex items-center rounded-md transition-colors text-sm font-medium"
                      {"justify-center p-2 w-10 h-10" collapsed?
                       "px-3 py-2" (not collapsed?)}
                      {"bg-gray-300 text-gray-900" is-active?
                       "hover:bg-gray-200 text-gray-700" (not is-active?)})]
    ($ :a {:href href :className link-classes :title (when collapsed? title)}
       (if collapsed?
         (first children) ; Only show the icon when collapsed
         children)))) ; Show icon and label

;; Agent-specific navigation component
(defui agent-context-nav [{:keys [module-id agent-name collapsed?]}]
  (let [location (or (get-in (state/use-sub [:route]) [:path]) "/")]
    ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
       (when-not collapsed?
         ($ :div.px-3.text-xs.font-semibold.text-gray-500 "AGENT"))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")
                    :location location :collapsed? collapsed? :title "Invocations"}
          ($ RectangleStackIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Invocations")))

       ($ nav-link {:href (rfe/href :agent/analytics {:module-id module-id :agent-name agent-name})
                    :location location :collapsed? collapsed? :title "Analytics"}
          ($ ChartBarIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Analytics")))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/rules")
                    :location location :collapsed? collapsed? :title "Rules/Actions"}
          ($ BoltIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Rules/Actions")))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/config")
                    :location location :collapsed? collapsed? :title "Agent Config"}
          ($ Cog6ToothIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Agent Config"))))))

;; Module-specific navigation component
(defui module-context-nav [{:keys [module-id collapsed?]}]
  (let [location (or (get-in (state/use-sub [:route]) [:path]) "/")
        ;; Query for module-specific agents
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:module-agents module-id]
          :sente-event [:agents/get-for-module {:module-id module-id}]
          :enabled? (boolean module-id)})]
    ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
       (when-not collapsed?
         ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))

       ($ nav-link {:href (rfe/href :module/datasets {:module-id module-id})
                    :location location :collapsed? collapsed? :title "Datasets"}
          ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Datasets & Experiments")))

       ($ nav-link {:href (str "/agents/" (common/url-encode module-id) "/evaluations")
                    :location location :collapsed? collapsed? :title "Evaluations"}
          ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Evaluators")))

       ($ nav-link {:href (rfe/href :module/global-config {:module-id module-id})
                    :location location :collapsed? collapsed? :title "Global Config"}
          ($ Cog6ToothIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Global Config")))


       ;; Module-specific agents list
       (cond
         ;; Show loading state
         loading?
         ($ :div.px-3.py-2.text-xs.text-gray-500.flex.items-center.gap-2
            ($ common/spinner {:size :small})
            (when-not collapsed? "Loading agents..."))

         ;; Show error state
         error
         ($ :div.px-3.py-2.text-xs.text-red-500 {:title error}
            (when-not collapsed? "Error loading agents"))

         ;; Render the list of agents directly in MODULE section
         (seq data)
         (let [sorted-agents (sort-by
                              (fn [agent]
                                (let [name (:agent-name agent)
                                      decoded-name (common/url-decode name)]
                                  ;; Put agents starting with _ last, sort alphabetically within each group
                                  [(str/starts-with? decoded-name "_") decoded-name]))
                              data)]
           (for [agent sorted-agents
                 :let [decoded-agent-name (common/url-decode (:agent-name agent))]]
             ($ nav-link {:key (:agent-name agent)
                          :href (str "/agents/" (common/url-encode module-id) "/agent/" (:agent-name agent))
                          :location location
                          :collapsed? collapsed?
                          :title decoded-agent-name}
                ($ CpuChipIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed?
                  ($ :span.ml-3.truncate decoded-agent-name)))))))))

(defui sidebar-nav []
  (let [match (state/use-sub [:route])
        location (or (:path match) "/")
        {:keys [module-id agent-name]} (or (:path-params match) {})
        route-name (get-in match [:data :name])
        is-agent-context? (and module-id agent-name)
        is-module-context? (and module-id (not agent-name))
        [collapsed? set-collapsed] (common/use-local-storage "sidebar-collapsed?" false)
        toggle-collapsed #(set-collapsed (not collapsed?))]

    ($ :div {:className (common/cn
                         "h-screen flex flex-col bg-gray-100 transition-all duration-300"
                         {"w-16" collapsed?, "w-64" (not collapsed?)})}
       ;; Header (no changes here)
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
       ($ :nav.flex-1.p-3.overflow-y-auto
          ($ :div.space-y-2
             ($ nav-link {:href "/" :location location :collapsed? collapsed? :title "Overview"}
                ($ HomeIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed? ($ :span.ml-3 "Overview"))))

          ;; Show MODULE section first when we have a module-id
          (when module-id
            ($ module-context-nav {:module-id module-id
                                   :collapsed? collapsed?}))

          ;; Then show AGENT section when we're in agent context
          (when is-agent-context?
            ($ agent-context-nav {:module-id module-id
                                  :agent-name agent-name
                                  :collapsed? collapsed?}))))))

;; =============================================================================
;; BREADCRUMB COMPONENT
;; =============================================================================

(defui breadcrumb []
  (let [match (state/use-sub [:route])
        {:keys [module-id agent-name dataset-id invoke-id rule-name]} (or (:path-params match) {})
        route-name (get-in match [:data :name])

        ;; Build breadcrumb items based on current route
        build-breadcrumbs (fn []
                            (let [items []]
                              (cond
                                ;; Action log for a specific rule
                                (and module-id agent-name rule-name)
                                [{:label (common/url-decode module-id)
                                  :path (rfe/href :module/detail {:module-id module-id})}
                                 {:label (common/url-decode agent-name)
                                  :path (rfe/href :agent/detail {:module-id module-id :agent-name agent-name})}
                                 {:label "Rules"
                                  :path (rfe/href :agent/rules {:module-id module-id :agent-name agent-name})}
                                 {:label (common/url-decode rule-name)
                                  :path nil}] ; Current page

                                ;; Agent invocation detail
                                (and module-id agent-name invoke-id)
                                [{:label (common/url-decode module-id)
                                  :path (rfe/href :module/detail {:module-id module-id})}
                                 {:label (common/url-decode agent-name)
                                  :path (rfe/href :agent/detail {:module-id module-id :agent-name agent-name})}
                                 {:label "Invocations"
                                  :path (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")}
                                 {:label (common/url-decode invoke-id)
                                  :path nil}] ; Current page

                                ;; Agent detail pages
                                (and module-id agent-name)
                                [{:label (common/url-decode module-id)
                                  :path (rfe/href :module/detail {:module-id module-id})}
                                 {:label (common/url-decode agent-name)
                                  :path (rfe/href :agent/detail {:module-id module-id :agent-name agent-name})}
                                 {:label (case route-name
                                           :agent/invocations "Invocations"
                                           :agent/rules "Rules"
                                           :agent/analytics "Analytics"
                                           :agent/config "Config"
                                           :agent/stats "Stats"
                                           "Agent")
                                  :path nil}] ; Current page

                                ;; Dataset detail
                                (and module-id dataset-id)
                                [{:label (common/url-decode module-id)
                                  :path (rfe/href :module/detail {:module-id module-id})}
                                 {:label "Datasets"
                                  :path (str "/agents/" (common/url-encode module-id) "/datasets")}
                                 {:label (common/url-decode dataset-id)
                                  :path nil}] ; Current page

                                ;; Module level pages
                                (and module-id)
                                [{:label (common/url-decode module-id)
                                  :path (rfe/href :module/detail {:module-id module-id})}
                                 {:label (case route-name
                                           :module/datasets "Datasets"
                                           :module/evaluations "Evaluations"
                                           :module/detail "Dashboard"
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
;; ROUTER COMPONENT FOR NESTED ROUTES
;; =============================================================================

;; =============================================================================
;; MAIN APP COMPONENT
;; =============================================================================

(defui app [] ($ with-router {:routes routes} ($ main-layout)))

(defn init []
  (sente/init!)
  (uix.dom/render-root
   ($ app)
   (uix.dom/create-root
    (.getElementById js/document "root"))))
