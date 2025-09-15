(ns com.rpl.agent-o-rama.ui.experiments.comparative
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon]]))

(defui index [{:keys [module-id dataset-id]}]
  ($ :div.p-6
     ($ :div.flex.justify-between.items-center.mb-6
        ($ :h2.text-2xl.font-bold "Comparative Experiments")
        ;; You can add action buttons here later
        )
     ($ :div.text-center.py-12
        ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
        ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "Comparative Experiments View")
        ($ :p.mt-1.text-sm.text-gray-500 "This is where the new view for comparing experiments will be displayed."))))