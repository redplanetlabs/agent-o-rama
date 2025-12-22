(ns com.rpl.agent-o-rama.ui.human-feedback-queues
  (:require
   [uix.core :as uix :refer [defui $]]
   [reitit.frontend.easy :as rfe]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]))

(defui detail []
  (let [{:keys [module-id queue-id]} (state/use-sub [:route :path-params])
        decoded-module-id (common/url-decode module-id)
        decoded-queue-id (common/url-decode queue-id)]
    ($ :div.p-6
       ($ :h2.text-2xl.font-bold.text-gray-900.mb-4 "Human Feedback Queue")
       ($ :div.text-gray-600.mb-2
          (str "Module: " decoded-module-id))
       ($ :div.text-gray-600.mb-4
          (str "Queue: " decoded-queue-id))
       ($ :div.mt-8.text-gray-500
          "Queue detail functionality coming soon..."))))

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        decoded-module-id (common/url-decode module-id)
        ;; Hardcoded first queue ID
        first-queue-id "queue-1"]
    ($ :div.p-6
       ($ :h2.text-2xl.font-bold.text-gray-900.mb-4 "Human Feedback Queues")
       ($ :div.text-gray-600.mb-4
          (str "Module: " decoded-module-id))
       
       ;; Hardcoded link to first queue
       ($ :div.mt-8
          ($ :a {:href (rfe/href :module/human-feedback-queue-detail {:module-id module-id :queue-id first-queue-id})
                 :className "inline-flex items-center px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 transition-colors"}
             "View Queue: " first-queue-id)))))

