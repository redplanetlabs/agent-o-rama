(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]))

;; =============================================================================
;; DATASETS INDEX PAGE
;; =============================================================================

(defui datasets-index []
  ($ :div.p-6
     ;; Header
     ($ :div.flex.items-center.justify-between.mb-6
        ($ :div
           ($ :h1.text-2xl.font-bold.text-gray-900 "Datasets")
           ($ :p.mt-2.text-sm.text-gray-600
              "Create and manage datasets for agent training and evaluation."))
        ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-blue-500
           ($ PlusIcon {:className "h-5 w-5 mr-2"})
           "Create New Dataset"))

     ;; Search bar
     ($ :div.mb-6
        ($ :input {:type "text"
                   :placeholder "Search datasets..."
                   :className "block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm placeholder-gray-400 focus:outline-none focus:ring-blue-500 focus:border-blue-500"}))

     ;; Datasets list placeholder
     ($ :div.space-y-4
        ;; Placeholder dataset card
        ($ :div.bg-white.overflow-hidden.shadow.rounded-lg.p-6
           ($ :div.flex.items-center.justify-between
              ($ :div
                 ($ :h3.text-lg.font-medium.text-gray-900 "Example Dataset")
                 ($ :p.mt-1.text-sm.text-gray-600 "A sample dataset for demonstration purposes."))
              ($ :div.flex.space-x-2
                 ($ :button.text-blue-600.hover:text-blue-800.text-sm.font-medium "Edit")
                 ($ :button.text-red-600.hover:text-red-800.text-sm.font-medium "Delete"))))

        ;; Empty state
        ($ :div.text-center.py-12
           ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400"})
           ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No datasets yet")
           ($ :p.mt-1.text-sm.text-gray-500 "Get started by creating your first dataset.")
           ($ :div.mt-6
              ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.shadow-sm.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-blue-500
                 ($ PlusIcon {:className "h-5 w-5 mr-2"})
                 "Create Dataset"))))))

;; =============================================================================
;; DATASET DETAIL PAGE
;; =============================================================================

(defui dataset-detail []
  ($ :div.p-6
     ;; Header
     ($ :div.mb-6
        ($ :h1.text-2xl.font-bold.text-gray-900 "Dataset Detail")
        ($ :p.mt-2.text-sm.text-gray-600 "Manage dataset properties, snapshots, and examples."))

     ;; Dataset properties section
     ($ :div.bg-white.shadow.rounded-lg.p-6.mb-6
        ($ :h2.text-lg.font-medium.text-gray-900.mb-4 "Dataset Properties")
        ($ :div.space-y-4
           ($ :div
              ($ :label.block.text-sm.font-medium.text-gray-700 "Name")
              ($ :p.mt-1.text-sm.text-gray-900 "My Awesome Dataset"))
           ($ :div
              ($ :label.block.text-sm.font-medium.text-gray-700 "Description")
              ($ :p.mt-1.text-sm.text-gray-900 "A detailed description of this dataset..."))))

     ;; Snapshots section
     ($ :div.bg-white.shadow.rounded-lg.p-6.mb-6
        ($ :div.flex.items-center.justify-between.mb-4
           ($ :h2.text-lg.font-medium.text-gray-900 "Snapshots")
           ($ :button.text-blue-600.hover:text-blue-800.text-sm.font-medium "Create Snapshot"))
        ($ :div
           ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Current Snapshot")
           ($ :select.block.w-64.px-3.py-2.border.border-gray-300.rounded-md.shadow-sm.focus:outline-none.focus:ring-blue-500.focus:border-blue-500
              ($ :option {:value "latest"} "Latest")
              ($ :option {:value "snapshot-1"} "Snapshot 1"))))

     ;; Examples section
     ($ :div.bg-white.shadow.rounded-lg.p-6
        ($ :div.flex.items-center.justify-between.mb-4
           ($ :h2.text-lg.font-medium.text-gray-900 "Examples")
           ($ :div.flex.space-x-2
              ($ :button.text-blue-600.hover:text-blue-800.text-sm.font-medium "Add Example")
              ($ :button.text-blue-600.hover:text-blue-800.text-sm.font-medium "Upload JSONL")))
        ($ :div.text-center.py-8
           ($ :p.text-sm.text-gray-500 "No examples in this snapshot yet.")))))

;; =============================================================================
;; EXPORTS
;; =============================================================================

(def index datasets-index)
(def detail dataset-detail)