(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]

   [com.rpl.agent-o-rama.ui.agents :as agents]
   [com.rpl.agent-o-rama.ui.config-page :as config-page]
   ["wouter" :refer [Link Route Switch Router useLocation]]
   ["@heroicons/react/24/outline" :refer [HomeIcon CpuChipIcon CircleStackIcon ChevronLeftIcon ChevronRightIcon
                                          RectangleStackIcon ChartBarIcon BeakerIcon Cog6ToothIcon]]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.stats :as stats]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.invocation-graph-view :refer [global-modal-component]]
   [com.rpl.agent-o-rama.ui.events])) ;; Ensure event handlers are registered at app startup

;; Sidebar navigation component
 ;; Reusable nav-link component
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
    ($ Link {:href href :className link-classes :title (when collapsed? title)}
       (if collapsed?
         (first children) ; Only show the icon when collapsed
         children)))) ; Show icon and label ; Show icon and label ; Show icon and label 

;; Agent-specific navigation component
(defui agent-context-nav [{:keys [module-id agent-name collapsed?]}]
  (let [[location _] (useLocation)]
    ($ :<>
       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))

          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/datsets")
                       :location location :collapsed? collapsed? :title "Datasets"}
             ($ ChartBarIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Datasets")))

          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/evaluations")
                       :location location :collapsed? collapsed? :title "Evaluations"}
             ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Evaluations"))))

       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "AGENT"))

          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/invocations")
                       :location location :collapsed? collapsed? :title "Invocations"}
             ($ RectangleStackIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Invocations")))

          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/config")
                       :location location :collapsed? collapsed? :title "Config"}
             ($ Cog6ToothIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Config")))))))

(defui sidebar-nav []
  (let [[location _] (useLocation)
        ;; this is a hack, because wouter doesn't support useParams outside of Route components
        ;; or nested routes. probably should switch to reitit or something.
        url-segments (-> location
                         (str/replace #"^/" "")
                         (str/split #"/")
                         vec)
        ;; Extract agent context from URL: /agents/module-id/agent-name/...
        [section module-id agent-name] url-segments
        is-agent-context? (and (= section "agents") module-id agent-name)
        [collapsed? set-collapsed] (common/use-local-storage "sidebar-collapsed?" false)
        toggle-collapsed #(set-collapsed (not collapsed?))]

    ($ :div {:className (str "h-screen flex flex-col bg-gray-100 transition-all duration-300 "
                             (if collapsed? "w-16" "w-64"))}
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
       ($ :nav.flex-1.p-3
          ;; Global Navigation (always visible)
          ($ :div.space-y-2
             ($ nav-link {:href "/" :location location :collapsed? collapsed? :title "Overview"}
                ($ HomeIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed? ($ :span.ml-3 "Overview"))))

          (when is-agent-context?
            ($ agent-context-nav {:module-id module-id
                                  :agent-name agent-name
                                  :collapsed? collapsed?}))

          ;; TODO: You can add another section here for Datasets when a module-id is present but an agent-name is not.
          ;; (when (and module-id (not agent-name))
          ;;  ($ dataset-context-nav ...))
          ))))

;; Breadcrumb for sub-navigation within sections
(defui breadcrumb []
  (let [[location _] (useLocation)
        segments (-> location
                     (str/replace #"^/" "")
                     (str/split #"/")
                     vec)

        ;; Build breadcrumb items with proper merging for module/agent
        build-breadcrumbs (fn [segments]
                            (loop [remaining segments
                                   result []
                                   path ""]
                              (if (empty? remaining)
                                result
                                (let [segment (first remaining)
                                      next-segment (second remaining)
                                      ;; Check if this is an agent module/agent-name pair
                                      is-agent-pair? (and (= (get segments 0) "agents")
                                                          (= (count result) 1)
                                                          next-segment)
                                      ;; Build the item
                                      item (if is-agent-pair?
                                             ;; Merge module/agent into one breadcrumb
                                             {:label (str segment ":" next-segment)
                                              :path (str path "/" segment "/" next-segment)
                                              :segments-consumed 2}
                                             ;; Regular breadcrumb
                                             {:label (str/capitalize segment)
                                              :path (str path "/" segment)
                                              :segments-consumed 1})]
                                  (recur (drop (:segments-consumed item) remaining)
                                         (conj result item)
                                         (:path item))))))

        breadcrumb-items (when (seq segments)
                           (build-breadcrumbs segments))]

    ($ :div.bg-gray-100.px-4.py-2.text-sm.text-gray-600
       ($ :div.flex.items-center.space-x-2
          ;; Home link (always present)
          ($ Link {:href "/" :className "text-blue-600 hover:text-blue-800"} "Home")

          ;; Build breadcrumbs from segments
          (when breadcrumb-items
            (map-indexed
             (fn [idx item]
               (let [is-last? (= idx (dec (count breadcrumb-items)))]
                 (list
                  ;; Separator
                  ($ :span {:key (str "sep-" idx)} " › ")
                  ;; Link or text
                  (if is-last?
                    ;; Current page - not clickable
                    ($ :span {:key (str "crumb-" idx) :className "text-gray-500"}
                       (common/url-decode (:label item)))
                    ;; Clickable link
                    ($ Link {:key (str "crumb-" idx)
                             :href (:path item)
                             :className "text-blue-600 hover:text-blue-800"}
                       (common/url-decode (:label item)))))))
             breadcrumb-items))))))

 ;; Main app component
(defui app []
  ($ Router
     ($ :div.flex.h-screen.bg-gray-50
        ($ sidebar-nav)
        ($ :div.flex-1.flex.flex-col.min-h-0
           ($ breadcrumb)
           ($ :div.flex-1.overflow-auto
              ;; Agent routes
              ($ Route {:path "/agents/:module-id/:agent-name/invocations" :component agents/invocations})
              ($ Route {:path "/agents/:module-id/:agent-name/invocations/:invoke-id" :component agents/invoke})
              ($ Route {:path "/agents/:module-id/:agent-name/evaluations" :component agents/evaluations})
              ($ Route {:path "/agents/:module-id/:agent-name/config" :component config-page/config-page})
              ($ Route {:path "/agents/:module-id/:agent-name/stats" :component stats/stats})
              ($ Route {:path "/agents/:module-id/:agent-name" :component agents/agent})
              ($ Route {:path "/agents" :component agents/index})

              ;; Home route
              ($ Route {:path "/" :component agents/index}))))
     ;; Global modal component
     ($ global-modal-component)))

(defn init []
  (sente/init!)
  (uix.dom/render-root
   ($ app)
   (uix.dom/create-root
    (.getElementById js/document "root"))))
