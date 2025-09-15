(ns com.rpl.agent-o-rama.ui.experiments.comparative-detail
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon ArrowLeftIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [reitit.frontend.easy :as rfe]))

(defui detail-page [{:keys [module-id dataset-id experiment-id]}]
  ;; This is the stub you requested.
  ;; You can build out the full comparative view here later.
  ($ :div.p-6
     ($ :div.flex.items-center.gap-4.mb-6
        ($ :a.inline-flex.items-center.text-gray-600.hover:text-gray-900
           {:href (rfe/href :module/dataset-detail.comparative-experiments {:module-id module-id :dataset-id dataset-id})}
           ($ ArrowLeftIcon {:className "h-5 w-5 mr-2"})
           "Back to Comparative Experiments")
        ($ :h2.text-2xl.font-bold "Comparative Experiment Details"))

     ($ :div.text-center.py-12.bg-gray-50.rounded-lg
        ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
        ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "Comparative View Under Construction")
        ($ :p.mt-1.text-sm.text-gray-500
           "This view will show side-by-side results and comparisons for your experiment.")
        ($ :p.mt-4.font-mono.text-xs.text-gray-400
           (str "ID: " experiment-id)))))