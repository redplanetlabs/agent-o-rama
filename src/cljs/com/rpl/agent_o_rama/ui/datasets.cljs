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

    ;; Test if we can even get console output at all
    (js/console.log "CreateDatasetForm rendered with name:" name)

    (letfn [(handle-create []
              (js/console.log "Button clicked! Name:" name "Module ID:" module-id)
              (println "Button clicked! Name:" name "Module ID:" module-id)
              (js/console.log "About to set submitting state")
              (set-submitting true)
              (set-error-msg nil)
              (js/console.log "Making sente request...")
              (sente/request!
               [:datasets/create {:module-id module-id
                                  :name name
                                  :description description
                                  :input-schema input-schema
                                  :output-schema output-schema}]
               15000 ;; Timeout
               (fn [reply]
                 (js/console.log "Got reply from server:" reply)
                 (println "Got reply from server:" reply)
                 (set-submitting false)
                 (if (:success reply)
                   (do
                     (js/console.log "Success! Hiding modal and calling on-success")
                     (state/dispatch [:modal/hide])
                     (on-success))
                   (do
                     (js/console.log "Error in reply:" (:error reply))
                     (set-error-msg (or (:error reply) "An unknown error occurred.")))))))]

      ($ :div
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
            ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer
               {:type "button" :onClick #(state/dispatch [:modal/hide])}
               "Cancel")
            (let [is-disabled? (or submitting? (str/blank? name))]
              ($ :button
                 {:type "button"
                  :disabled is-disabled?
                  :onClick #(when-not is-disabled? (handle-create))
                  :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                  (if is-disabled?
                                    "text-gray-400 bg-gray-300 cursor-not-allowed"
                                    "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Create")))))))

(defui EditDatasetForm [{:keys [module-id dataset-id initial-name initial-description on-success]}]
  (let [[name set-name] (uix/use-state initial-name)
        [description set-description] (uix/use-state initial-description)
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)
        is-changed? (or (not= name initial-name) (not= description initial-description))]

    (letfn [(handle-save []
              (set-submitting true)
              (set-error-msg nil)

              ;; Create two promises, one for each Sente request
              (let [name-promise (js/Promise.
                                  (fn [resolve reject]
                                    (sente/request!
                                     [:datasets/set-name {:module-id module-id :dataset-id dataset-id :name name}]
                                     5000
                                     #(if (:success %) (resolve %) (reject (:error %))))))
                    desc-promise (js/Promise.
                                  (fn [resolve reject]
                                    (sente/request!
                                     [:datasets/set-description {:module-id module-id :dataset-id dataset-id :description description}]
                                     5000
                                     #(if (:success %) (resolve %) (reject (:error %))))))]

                ;; Use Promise.all to wait for both to complete
                (-> (.all js/Promise [name-promise desc-promise])
                    (.then (fn [_]
                             (set-submitting false)
                             (state/dispatch [:modal/hide])
                             (on-success)))
                    (.catch (fn [error]
                              (set-submitting false)
                              (set-error-msg (str "Failed to save: " error)))))))]

      ($ :div
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
               ($ :textarea {:className "w-full h-24 p-3 border rounded-md text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                             :value description
                             :onChange #(set-description (.. % -target -value))})))

         (when error-msg
           ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
              ($ :p.text-sm.text-red-700 error-msg)))

         ($ :div.mt-6.flex.justify-end.gap-3
            ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer
               {:type "button" :onClick #(state/dispatch [:modal/hide])}
               "Cancel")
            (let [is-disabled? (or submitting? (not is-changed?) (str/blank? name))]
              ($ :button
                 {:type "button"
                  :disabled is-disabled?
                  :onClick #(when-not is-disabled? (handle-save))
                  :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                  (if is-disabled?
                                    "text-gray-400 bg-gray-300 cursor-not-allowed"
                                    "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Save Changes")))))))

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
          :sente-event [:datasets/get-all {:module-id module-id-raw :pagination nil}]
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
                    ;; Link to the detail page (now view-only for properties)
                    ($ :a {:href (get-dataset-path module-id (:dataset-id dataset))
                           :className "flex-grow"}
                       ($ :h3.text-lg.font-medium.text-gray-900.hover:text-blue-600 (:name dataset))
                       ($ :p.mt-1.text-sm.text-gray-600 (or (:description dataset) "No description.")))

                    ;; Action buttons
                    ($ :div.flex.space-x-4.flex-shrink-0.ml-4
                       ;; Edit Button
                       ($ :button.text-gray-600.hover:text-gray-800.p-1.rounded-full.hover:bg-gray-100.cursor-pointer
                          {:title "Edit Dataset"
                           :onClick #(state/dispatch [:modal/show :edit-dataset
                                                      {:title (str "Edit Dataset: " (:name dataset))
                                                       :component ($ EditDatasetForm {:module-id module-id-raw
                                                                                      :dataset-id (:dataset-id dataset)
                                                                                      :initial-name (:name dataset)
                                                                                      :initial-description (:description dataset)
                                                                                      :on-success refetch})}])}
                          ($ PencilIcon {:className "h-5 w-5"}))

                       ;; Delete Button
                       ($ :button.text-red-600.hover:text-red-800.p-1.rounded-full.hover:bg-red-100.cursor-pointer
                          {:title "Delete Dataset"
                           :onClick (fn []
                                      (when (js/confirm (str "Are you sure you want to delete '" (:name dataset) "'?"))
                                        (sente/request! [:datasets/delete
                                                         {:module-id module-id-raw :dataset-id (:dataset-id dataset)}]
                                                        5000
                                                        #(when (:success %) (refetch)))))}
                          ($ TrashIcon {:className "h-5 w-5"})))))))))))

;; =============================================================================
;; DATASET DETAIL PAGE
;; =============================================================================

(defn pretty-print-json [json-str]
  "Pretty prints a JSON string, falls back to original if parsing fails"
  (try
    (js/JSON.stringify (js/JSON.parse json-str) nil 2)
    (catch js/Error _
      json-str)))

(defui dataset-detail []
  (let [;; Get IDs from route
        {:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))

        ;; Fetch dataset properties
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})]

    ($ :div.p-6
       (cond
         loading? ($ :div "Loading dataset details...")
         error ($ :div "Error: " error)
         data
         (let [dataset data]
           ($ :div
              ;; Header (now view-only)
              ($ :div.mb-6
                 ($ :h1.text-2xl.font-bold.text-gray-900 (:name dataset))
                 ($ :p.text-sm.text-gray-600.mt-1 (or (:description dataset) "No description.")))

              ($ :div.mt-8
                 ($ :h2.text-lg.font-semibold.text-gray-900.mb-4 "JSON Schemas")
                 (let [input-schema (:input-json-schema dataset)
                       output-schema (:output-json-schema dataset)]
                   (if (and (nil? input-schema) (nil? output-schema))
                     ($ :div.p-6.border.rounded-md.bg-gray-50.text-gray-500.text-center
                        {:className "border-gray-300"}
                        "No schemas defined")
                     ($ :div.grid.grid-cols-2.gap-6
                        ($ :div.overflow-x-scroll
                           ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Input Schema")
                           (if input-schema
                             ($ :pre.w-full.h-80.p-3.border.rounded-md.font-mono.text-sm.bg-gray-50.overflow-auto.text-gray-800
                                {:className "border-gray-300"}
                                (pretty-print-json input-schema))
                             ($ :div.w-full.h-80.p-3.border.rounded-md.bg-gray-50.text-gray-500.text-sm.flex.items-center.justify-center
                                {:className "border-gray-300"}
                                "No input schema defined")))
                        ($ :div.overflow-x-scroll
                           ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Output Schema")
                           (if output-schema
                             ($ :pre.w-full.h-80.p-3.border.rounded-md.font-mono.text-sm.bg-gray-50.overflow-auto.text-gray-800
                                {:className "border-gray-300"}
                                (pretty-print-json output-schema))
                             ($ :div.w-full.h-80.p-3.border.rounded-md.bg-gray-50.text-gray-500.text-sm.flex.items-center.justify-center
                                {:className "border-gray-300"}
                                "No output schema defined")))))))

              ;; Placeholder for snapshots and examples
              ($ :div.mt-8.space-y-8
                 ($ :div.bg-gray-50.p-4.rounded-lg "Snapshots section coming soon...")
                 ($ :div.bg-gray-50.p-4.rounded-lg "Examples section coming soon..."))))
         :else ($ :div "No data available.")))))

;; =============================================================================
;; EXPORTS
;; =============================================================================

(def index datasets-index)
(def detail dataset-detail)