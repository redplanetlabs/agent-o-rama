(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]

   [com.rpl.agent-o-rama.ui.common :as common]))

(defui datasets []
  (let [{:strs [module-id agent-id]}
        (js->clj (wouter/useParams))

        {:keys [data isLoading]}
        (common/use-query {:query-keys ["dataset" module-id agent-id]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/datasets")} )]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Datasets")
       ($ :div.text-gray-500 "Datasets functionality coming soon...")
       ($ :pre (when data (common/pp data))))))
