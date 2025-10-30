(ns com.rpl.agent-o-rama.ui.module-page
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [CpuChipIcon CircleStackIcon BeakerIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [reitit.frontend.easy :as rfe]))

;; --- Reusable Table Components (Extracted from existing pages) ---

(defui AgentListTable [{:keys [agents module-id]}]
  (if (empty? agents)
    ($ :div.text-sm.text-gray-500.italic "No agents defined in this module.")
    ($ :div {:className (:container common/table-classes)}
       ($ :table {:className (:table common/table-classes)}
          ($ :thead {:className (:thead common/table-classes)}
             ($ :tr
                ($ :th {:className (:th common/table-classes)}
                   ($ :div.flex.gap-1.items-center
                      ($ CpuChipIcon {:className "h-6 w-6 text-indigo-600"})
                      "Agent Name"))))
          ($ :tbody
             (for [agent agents
                   :let [decoded-name (common/url-decode (:agent-name agent))]]
               ($ :tr {:key decoded-name
                       :className "hover:bg-gray-50 cursor-pointer"
                       :onClick #(rfe/push-state :agent/detail {:module-id module-id :agent-name (:agent-name agent)})}
                  ($ :td {:className (:td common/table-classes)} decoded-name))))))))

(defui DatasetListTable [{:keys [datasets module-id]}]
  (if (empty? datasets)
    ($ :div.text-sm.text-gray-500.italic "No datasets found for this module.")
    ($ :div {:className (:container common/table-classes)}
       ($ :table {:className (:table common/table-classes)}
          ($ :thead {:className (:thead common/table-classes)}
             ($ :tr
                ($ :th {:className (:th common/table-classes)}
                   ($ :div.flex.gap-1.items-center
                      ($ CircleStackIcon {:className "h-6 w-6 text-indigo-600"})
                      "Dataset Name"))
                ($ :th {:className (:th common/table-classes)} "Description")))
          ($ :tbody
             (for [dataset datasets]
               ($ :tr {:key (:dataset-id dataset)
                       :className "hover:bg-gray-50 cursor-pointer"
                       :onClick #(rfe/push-state :module/dataset-detail.examples {:module-id module-id :dataset-id (:dataset-id dataset)})}
                  ($ :td {:className (:td common/table-classes)} (:name dataset))
                  ($ :td {:className (:td common/table-classes)} (or (:description dataset) "—")))))))))

(defui EvaluatorListTable [{:keys [evaluators module-id]}]
  (if (empty? evaluators)
    ($ :div.text-sm.text-gray-500.italic "No evaluators created for this module.")
    ($ :div {:className (:container common/table-classes)}
       ($ :table {:className (:table common/table-classes)}
          ($ :thead {:className (:thead common/table-classes)}
             ($ :tr
                ($ :th {:className (:th common/table-classes)}
                   ($ :div.flex.gap-1.items-center
                      ($ BeakerIcon {:className "h-6 w-6 text-indigo-600"})
                      "Evaluator Name"))
                ($ :th {:className (:th common/table-classes)} "Builder")))
          ($ :tbody
             (for [evaluator evaluators]
               ($ :tr {:key (:name evaluator)
                       :className "hover:bg-gray-50 cursor-pointer"
                       :onClick #(rfe/push-state :module/evaluations {:module-id module-id})}
                  ($ :td {:className (:td common/table-classes)} (:name evaluator))
                  ($ :td {:className (:td common/table-classes)} ($ :code.font-mono.text-xs (:builder-name evaluator))))))))))

;; --- Main Dashboard Component ---

(defui DashboardSection [{:keys [title icon loading? error children]}]
  ($ :div.bg-white.shadow-sm.rounded-lg.border.border-gray-200
     ($ :div.px-6.py-4.border-b.border-gray-200
        ($ :h3.text-lg.font-semibold.text-gray-800.flex.items-center.gap-3
           icon
           title))
     ($ :div.p-6
        (cond
          loading? ($ common/spinner {:size :medium})
          error ($ :div.text-red-500 "Error loading data: " (str error))
          :else children))))

(defui index [{:keys [module-id]}]
  (let [decoded-module-id (common/url-decode module-id)
        ;; Fetch data for all three sections
        agents-query (queries/use-sente-query
                      {:query-key [:module-agents module-id]
                       :sente-event [:agents/get-for-module {:module-id module-id}]})
        datasets-query (queries/use-sente-query
                        {:query-key [:datasets module-id]
                         :sente-event [:datasets/get-all {:module-id module-id}]})
        evaluators-query (queries/use-sente-query
                          {:query-key [:evaluator-instances-list module-id]
                           :sente-event [:evaluators/get-all-instances {:module-id module-id}]})]

    ($ :div.p-6.space-y-8
       ($ :h2.text-2xl.font-bold.text-gray-900 (str "Module: " decoded-module-id))

       ($ AgentListTable {:agents (:data agents-query) :module-id module-id})

       ($ DatasetListTable {:datasets (get-in datasets-query [:data :datasets]) :module-id module-id})

       ($ EvaluatorListTable {:evaluators (get-in evaluators-query [:data :items]) :module-id module-id}))))
