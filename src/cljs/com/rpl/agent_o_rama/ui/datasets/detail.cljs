(ns com.rpl.agent-o-rama.ui.datasets.detail
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["@heroicons/react/24/outline" :refer [ChevronDownIcon ChevronUpIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.datasets.common :refer [pretty-print-json]]
   [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
   [re-frame.query :as rfq]
   [reitit.frontend.easy :as rfe]))

(defui detail [{:keys [match module-id dataset-id]}]
  (let [route-name (get-in match [:data :name])
        experiments-routes #{:module/dataset-detail
                             :module/dataset-detail.experiments
                             :module/dataset-detail.experiment-detail}
        comparative-routes #{:module/dataset-detail.comparative-experiments
                             :module/dataset-detail.comparative-experiment-detail}
        active-tab (cond
                     (contains? comparative-routes route-name)
                     "comparative"

                     (contains? experiments-routes route-name)
                     "experiments"

                     :else "examples")
        [show-info? set-show-info] (uix/use-state false)
        {:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-datasets/get-props!!
                        {:module-id module-id :dataset-id dataset-id}])
        loading? (#{:loading :idle} query-status)
        dataset data
        is-remote? (boolean (:module-name dataset))]
    ($ :div.h-full.flex.flex-col
       (cond
         loading? ($ :div.p-6 "Loading dataset details...")
         error ($ :div.p-6 "Error: " error)
         dataset
         ($ :div.h-full.flex.flex-col
            ;; Header Bar for the whole dataset page
            (if is-remote?
              ;; Remote dataset header - show connection info
              ($ :div.bg-purple-50.border-b.border-purple-200.px-6.py-4
                 ($ :div.flex.items-center.gap-3
                    ($ :span.text-sm.font-semibold.text-purple-700.uppercase "Remote Dataset:")
                    ($ :span.font-mono.text-purple-900
                       (let [host (:remote-host dataset)
                             port (:remote-port dataset)
                             module (:module-name dataset)]
                         (cond
                           (and host port) (str host ":" port " / " module)
                           host (str host " / " module)
                           :else module)))))
              ;; Local dataset header - show title and details
              ($ :div.bg-white.px-6.py-4
                 ($ :div.flex.items-center.justify-between
                    ;; Left side - Title and info
                    ($ :div.flex.items-center.space-x-4
                       ($ :h1.text-2xl.font-bold.text-gray-900 (:name dataset))
                       ;; Details button with conditional chevron
                       ($ :button.inline-flex.items-center.px-3.py-1.text-sm.text-gray-600.hover:text-gray-800.rounded-md.hover:bg-gray-100.cursor-pointer
                          {:onClick #(set-show-info (not show-info?))
                           :title (if show-info? "Hide Dataset Information" "Show Dataset Information")}
                          ($ :span.mr-1 "Details")
                          (if show-info?
                            ($ ChevronUpIcon {:className "h-4 w-4"})
                            ($ ChevronDownIcon {:className "h-4 w-4"}))))
                    ;; Right side - reserved for actions
                    ($ :div.flex.items-center.space-x-4))))

            ;; Collapsible info panel (only for local datasets)
            (when (and show-info? (not is-remote?))
              ($ :div.bg-blue-50.border-b.border-blue-200.px-6.py-4
                 ($ :div.space-y-4
                    ;; Description
                    (when (:description dataset)
                      ($ :div
                         ($ :h3.text-sm.font-medium.text-blue-900 "Description")
                         ($ :p.text-sm.text-blue-700.mt-1 (:description dataset))))

                    ;; Schemas - Always show this section
                    (let [input-schema (:input-json-schema dataset)
                          output-schema (:output-json-schema dataset)]
                      ($ :div
                         ($ :h3.text-sm.font-medium.text-blue-900 "Schemas")
                         ($ :div.grid.grid-cols-2.gap-4.mt-2
                            ;; Input Schema - always show
                            ($ :div
                               ($ :h4.text-xs.font-medium.text-blue-800.mb-1 "Input Schema")
                               (if input-schema
                                 ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-32.text-blue-800
                                    (pretty-print-json input-schema))
                                 ($ :div.text-xs.bg-gray-100.p-2.rounded.text-gray-500.italic
                                    "Schema: nil")))
                            ;; Output Schema - always show
                            ($ :div
                               ($ :h4.text-xs.font-medium.text-blue-800.mb-1 "Output Schema")
                               (if output-schema
                                 ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-32.text-blue-800
                                    (pretty-print-json output-schema))
                                 ($ :div.text-xs.bg-gray-100.p-2.rounded.text-gray-500.italic
                                    "Schema: nil")))))))))

            ;; Tab navigation bar
            ($ :div.bg-white.border-b.border-gray-200
               ($ :nav.flex.space-x-8.px-6
                  ($ :a {:href (rfe/href :module/dataset-detail.examples {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "examples")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "examples")})}
                     "Examples")
                  ($ :a {:href (rfe/href :module/dataset-detail.experiments {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "experiments")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "experiments")})}
                     "Experiments")
                  ($ :a {:href (rfe/href :module/dataset-detail.comparative-experiments {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "comparative")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "comparative")})}
                     "Comparative Experiments"))))
         :else ($ :div.p-6 "Dataset not found.")))))
