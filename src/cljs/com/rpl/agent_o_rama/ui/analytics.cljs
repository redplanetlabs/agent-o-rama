(ns com.rpl.agent-o-rama.ui.analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   ["@heroicons/react/24/outline" :refer [ChartBarIcon]]))

(defui analytics-page []
  (let [{:keys [agent-name]} (state/use-sub [:route :path-params])]
    ($ :div.p-6
       ($ :div.flex.items-center.gap-3.mb-6
          ($ ChartBarIcon {:className "h-8 w-8 text-indigo-600"})
          ($ :h2.text-2xl.font-bold.text-gray-900
             (str "Analytics for " (common/url-decode agent-name))))
       ($ :div.bg-white.p-12.rounded-lg.shadow-md.border.border-gray-200.text-center
          ($ :h3.text-lg.font-medium.text-gray-700 "Analytics Dashboard")
          ($ :p.mt-2.text-sm.text-gray-500 "The new analytics and charting features will be available here soon.")))))