(ns com.rpl.agent-o-rama.ui.experiments.comparative
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon PlusIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [reitit.frontend.easy :as rfe]))

(defui index [{:keys [module-id dataset-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id :comparative]
          :sente-event [:experiments/get-all-for-dataset
                        {:module-id module-id
                         :dataset-id dataset-id
                         :filters {:type :comparative}}]
          :enabled? (boolean (and module-id dataset-id))})

        experiments (get data :items)]

    ($ :div.p-6
       ;; Header
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h2.text-2xl.font-bold "Comparative Experiments")
          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.cursor-pointer
             {:onClick #(state/dispatch [:modal/show-form :create-experiment
                                         {:module-id module-id
                                          :dataset-id dataset-id
                                          :spec {:type :comparative
                                                 :targets [{:target-spec {:type :agent :agent-name nil} :input->args [{:id (random-uuid) :value "$"}]}
                                                           {:target-spec {:type :agent :agent-name nil} :input->args [{:id (random-uuid) :value "$"}]}]}}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Run New Comparative Experiment"))

       ;; Content now shows a table, similar to the regular experiments index
       (cond
         loading? ($ :div.text-center.py-12 ($ common/spinner {:size :large}))
         error ($ :div.text-red-500.text-center.py-8 "Error loading experiments: " error)
         (empty? experiments)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No comparative experiments run yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Run your first comparative experiment to compare multiple agents."))
         :else
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Experiment Name")
                     ($ :th {:className (:th common/table-classes)} "Started")
                     ($ :th {:className (:th common/table-classes)} "Finished")))
               ($ :tbody
                  (for [exp experiments
                        :let [info (get exp :experiment-info)]]
                    ($ :tr {:key (:id info)
                            :className "hover:bg-gray-50 cursor-pointer"
                            :onClick (fn [_]
                                       (rfe/push-state :module/dataset-detail.comparative-experiment-detail
                                                       {:module-id module-id
                                                        :dataset-id dataset-id
                                                        :experiment-id (:id info)}))}
                       ($ :td {:className (:td common/table-classes)}
                          ($ :div.font-medium.text-gray-900 (:name info)))
                       ($ :td {:className (:td common/table-classes)}
                          (common/format-relative-time (:start-time-millis exp)))
                       ($ :td {:className (:td common/table-classes)}
                          (if (:finish-time-millis exp)
                            (common/format-relative-time (:finish-time-millis exp))
                            "--")))))))))))