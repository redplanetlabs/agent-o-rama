(ns com.rpl.agent-o-rama.ui.human-metrics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]))

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        decoded-module-id (common/url-decode module-id)]
    ($ :div.p-6
       ($ :h2.text-2xl.font-bold.text-gray-900.mb-4 "Human Metrics")
       ($ :div.text-gray-600
          (str "Module: " decoded-module-id))
       ($ :div.mt-8.text-gray-500
          "Human metrics functionality coming soon..."))))

