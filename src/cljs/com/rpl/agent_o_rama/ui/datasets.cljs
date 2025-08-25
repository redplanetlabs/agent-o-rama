(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]))
(def example-schema "{
  \"type\": \"object\",
  \"properties\": {
    \"context\": {
      \"type\": \"string\",
      \"description\": \"Information about the user\"
    },
    \"prompt\": {
      \"x-javaType\": \"dev.langchain4j.data.message.UserMessage\",
    }
  },
  \"required\": [\"context\", \"prompt\"]
}")
;; =============================================================================
;; MODAL FOR CREATING DATASETS
;; =============================================================================
(defui CreateDatasetForm [{:keys [module-id on-success]}]
  (let [[name set-name] (uix/use-state "")
        [description set-description] (uix/use-state "")
        [input-schema set-input-schema] (uix/use-state "")
        [output-schema set-output-schema] (uix/use-state "")
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)]

    (letfn [(handle-create [e]
              (println "Form submitted! Name:" name "Module ID:" module-id)
              (.preventDefault e)
              (println "After preventDefault - about to set submitting state")
              (set-submitting true)
              (set-error-msg nil)
              (println "Making sente request...")
              (sente/request!
               [:api/create-dataset {:module-id module-id
                                     :name name
                                     :description description
                                     :input-schema input-schema
                                     :output-schema output-schema}]
               15000 ;; Timeout
               (fn [reply]
                 (println "Got reply from server:" reply)
                 (set-submitting false)
                 (if (:success reply)
                   (do
                     (println "Success! Hiding modal and calling on-success")
                     (state/dispatch [:modal/hide])
                     (on-success))
                   (do
                     (println "Error in reply:" (:error reply))
                     (set-error-msg (or (:error reply) "An unknown error occurred.")))))))]

      ($ :form {:onSubmit handle-create}
         ($ :div.space-y-4
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Name *")
               ($ :input {:className "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                          :type "text"
                          :value name
                          :required true
                          :onChange #(set-name (.. % -target -value))}))
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Description (Optional)")
               ($ :textarea {:className "w-full h-15 p-3 border rounded-md text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                             :value description
                             :onChange #(set-description (.. % -target -value))}))
            ($ :div.grid.grid-cols-2.gap-4
               ($ :div
                  ($ :label.block.text-sm.font-medium.text-gray-700 "Input JSON Schema (Optional)")
                  ($ :textarea {:className "w-full h-80 p-3 border rounded-md font-mono text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                                :value input-schema
                                :placeholder example-schema
                                :onChange #(set-input-schema (.. % -target -value))}))
               ($ :div
                  ($ :label.block.text-sm.font-medium.text-gray-700 "Output JSON Schema (Optional)")
                  ($ :textarea {:className "w-full h-80 p-3 border rounded-md font-mono text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                                :value output-schema
                                :placeholder example-schema
                                :onChange #(set-output-schema (.. % -target -value))})))

            ;; JSON Schema Help Box
            ($ :div.bg-blue-50.border.border-blue-200.rounded-md.p-4
               ($ :div.flex
                  ($ :div.flex-shrink-0
                     ($ :svg {:className "h-5 w-5 text-blue-400" :fill "currentColor" :viewBox "0 0 20 20"}
                        ($ :path {:fillRule "evenodd" :d "M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" :clipRule "evenodd"})))
                  ($ :div.ml-3
                     ($ :h3.text-sm.font-medium.text-blue-800 "JSON Schema Guidelines")
                     ($ :div.mt-2.text-sm.text-blue-700
                        ($ :ul.list-disc.space-y-1.pl-5
                           ($ :li
                              "Follow "
                              ($ :a.underline.hover:text-blue-900 {:href "https://json-schema.org/" :target "_blank"} "JSON Schema")
                              " specification")
                           ($ :li "AOR supports " ($ :code.bg-blue-100.px-1.rounded "x-javaType") " extension to reference Java types")
                           ($ :li "Do not include " ($ :code.bg-blue-100.px-1.rounded "$schema") " or " ($ :code.bg-blue-100.px-1.rounded "$vocabulary") " keys - these are added automatically")))))))

         (when error-msg
           ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
              ($ :p.text-sm.text-red-700.whitespace-pre-wrap error-msg)))

         ($ :div.mt-6.flex.justify-end.gap-3
            ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer {:type "button" :onClick #(state/dispatch [:modal/hide])} "Cancel")
            (let [is-disabled? (or submitting? (str/blank? name))]
              ($ :button
                 {:type "submit"
                  :disabled is-disabled?
                  :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                  (if is-disabled?
                                    "text-gray-400 bg-gray-300 cursor-not-allowed"
                                    "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Create")))))))

(defn get-dataset-path [module-id dataset-id]
  (rfe/href :module/dataset-detail
            {:module-id (common/url-encode module-id)
             :dataset-id (str dataset-id)}))

;; =============================================================================
;; DATASETS INDEX PAGE
;; =============================================================================
(defui datasets-index []
  (let [;; Get module_id from route, needs decoding for display
        module-id-raw (get-in (state/use-sub [:route]) [:path-params :module-id])
        module-id (when module-id-raw (common/url-decode module-id-raw))

        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:datasets module-id]
          :sente-event [:api/get-datasets {:module-id module-id-raw :pagination nil}]
          :enabled? (boolean module-id-raw)
          :refetch-interval-ms 5000})
        datasets (get-in data [:datasets])]

    ($ :div.p-6
       ;; Header
       ($ :div.flex.items-center.justify-between.mb-6
          ($ :div
             ($ :h1.text-2xl.font-bold.text-gray-900 "Datasets for " ($ :span.text-indigo-600 module-id))
             ($ :p.mt-2.text-sm.text-gray-600 "Create and manage datasets for agent training and evaluation."))
          ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer
             {:onClick #(state/dispatch [:modal/show :create-dataset
                                         {:title "Create New Dataset"
                                          :component ($ CreateDatasetForm {:module-id module-id-raw
                                                                           :on-success refetch})}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Create New Dataset"))

       (cond
         loading? ($ :div.text-center.py-12 "Loading datasets...")
         error ($ :div.text-center.py-12.text-red-500 "Error: " error)
         (empty? datasets)
         ($ :div.text-center.py-12
            ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No datasets yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Get started by creating your first dataset."))
         :else
         ($ :div.space-y-4
            (for [dataset datasets]
              ($ :div.bg-white.shadow.rounded-lg.p-6 {:key (:dataset-id dataset)}
                 ($ :div.flex.items-center.justify-between
                    ($ :a {:href (get-dataset-path module-id (:dataset-id dataset))}
                       ($ :h3.text-lg.font-medium.text-gray-900.hover:text-blue-600 (:name dataset))
                       ($ :p.mt-1.text-sm.text-gray-600 (or (:description dataset) "No description.")))
                    ($ :div.flex.space-x-4
                       ($ :button.text-red-600.hover:text-red-800.p-1.rounded-full.hover:bg-red-100
                          {:onClick (fn []
                                      (when (js/confirm (str "Are you sure you want to delete '" (:name dataset) "'?"))
                                        (sente/request! [:api/delete-dataset
                                                         {:module-id module-id-raw :dataset-id (:dataset-id dataset)}]
                                                        5000
                                                        #(when (:success %) (refetch)))))}
                          ($ TrashIcon {:className "h-5 w-5"})))))))))))

;; =============================================================================
;; DATASET DETAIL PAGE
;; =============================================================================

(defui dataset-detail []
  (let [;; Get IDs from route
        {:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))

        ;; Fetch dataset properties
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:api/get-dataset-props {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})]

    ($ :div.p-6
       (cond
         loading? ($ :div "Loading dataset details...")
         error ($ :div "Error: " error)
         data
         (let [dataset data
               [is-editing? set-is-editing] (uix/use-state false)
               [edit-name set-edit-name] (uix/use-state (:name dataset))
               [edit-desc set-edit-desc] (uix/use-state (:description dataset))]

           (letfn [(handle-save []
                     (sente/request! [:api/update-dataset-props
                                      {:module-id module-id
                                       :dataset-id dataset-id
                                       :name edit-name
                                       :description edit-desc}]
                                     5000
                                     (fn [reply]
                                       (if (:success reply)
                                         (do
                                           (set-is-editing false)
                                           (refetch))
                                         (js/alert (str "Save failed: " (:error reply)))))))
                   (handle-cancel []
                     (set-edit-name (:name dataset))
                     (set-edit-desc (:description dataset))
                     (set-is-editing false))]
             ($ :div
                ;; Header
                ($ :div.flex.justify-between.items-center.mb-6
                   (if is-editing?
                     ($ :input.text-2xl.font-bold.text-gray-900.border.rounded.px-2 {:value edit-name :onChange #(set-edit-name (.. % -target -value))})
                     ($ :h1.text-2xl.font-bold.text-gray-900 (:name dataset)))
                   (if is-editing?
                     ($ :div.space-x-2
                        ($ :button.px-3.py-1.rounded.bg-gray-200.text-sm {:onClick handle-cancel} "Cancel")
                        ($ :button.px-3.py-1.rounded.bg-blue-600.text-white.text-sm {:onClick handle-save} "Save"))
                     ($ :button {:onClick #(set-is-editing true)}
                        ($ PencilIcon {:className "h-5 w-5 text-gray-500 hover:text-gray-700"}))))
                (if is-editing?
                  ($ :textarea.text-sm.text-gray-600.border.rounded.w-full.p-2 {:value edit-desc :rows 3 :onChange #(set-edit-desc (.. % -target -value))})
                  ($ :p.text-sm.text-gray-600 (or (:description dataset) "No description.")))

                ;; Placeholder for snapshots and examples
                ($ :div.mt-8.space-y-8
                   ($ :div.bg-gray-50.p-4.rounded-lg "Snapshots section coming soon...")
                   ($ :div.bg-gray-50.p-4.rounded-lg "Examples section coming soon...")))))
         :else ($ :div "No data available.")))))

;; =============================================================================
;; EXPORTS
;; =============================================================================

(def index datasets-index)
(def detail dataset-detail)