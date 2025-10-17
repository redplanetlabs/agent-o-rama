(ns com.rpl.agent-o-rama.ui.datasets-forms
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon XMarkIcon LockClosedIcon InformationCircleIcon DocumentArrowUpIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]
   [com.rpl.specter :as s]))

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

(defui CreateDatasetForm [{:keys [form-id]}]
  (let [{:keys [fields field-errors]} (forms/use-form form-id)
        dataset-type-field (forms/use-form-field form-id :dataset-type)
        is-remote? (= (:value dataset-type-field) :remote)

        ;; Local dataset fields
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)
        input-schema-field (forms/use-form-field form-id :input-schema)
        output-schema-field (forms/use-form-field form-id :output-schema)

;; Remote dataset fields
        remote-dataset-id-field (forms/use-form-field form-id :remote-dataset-id)
        module-name-field (forms/use-form-field form-id :module-name)
        host-field (forms/use-form-field form-id :cluster-conductor-host)
        port-field (forms/use-form-field form-id :cluster-conductor-port)]

    ($ forms/form
       ;; Dataset Type Toggle
       ($ :div.mb-6
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-3 "Dataset Type")
          ($ :div.flex.gap-4
             ($ :div.flex.items-center
                ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                   {:type "radio"
                    :id "local-dataset"
                    :name "dataset-type"
                    :checked (not is-remote?)
                    :on-change #((:on-change dataset-type-field) :local)})
                ($ :label.ml-2.block.text-sm.text-gray-700
                   {:htmlFor "local-dataset"}
                   "Local Dataset"))
             ($ :div.flex.items-center
                ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                   {:type "radio"
                    :id "remote-dataset"
                    :name "dataset-type"
                    :checked is-remote?
                    :on-change #((:on-change dataset-type-field) :remote)})
                ($ :label.ml-2.block.text-sm.text-gray-700
                   {:htmlFor "remote-dataset"}
                   "Remote Dataset"))))

       ;; Conditional fields based on dataset type
       (if is-remote?
         ;; Remote dataset fields
         ($ :<>
            ($ forms/form-field {:label "Conductor Host (Optional)"
                                 :value (:value host-field)
                                 :on-change (:on-change host-field)
                                 :error (:error host-field)
                                 :placeholder "e.g., cluster-b.example.com"})
            ($ forms/form-field {:label "Conductor Port (Optional)"
                                 :type :number
                                 :value (:value port-field)
                                 :on-change (:on-change port-field)
                                 :error (:error port-field)
                                 :placeholder "e.g., 6657"})
            ($ forms/form-field {:label "Remote Module Name"
                                 :value (:value module-name-field)
                                 :on-change (:on-change module-name-field)
                                 :error (:error module-name-field)
                                 :required? true
                                 :placeholder "e.g., MyRemoteModule"})
            ($ forms/form-field {:label "Remote Dataset ID"
                                 :value (:value remote-dataset-id-field)
                                 :on-change (:on-change remote-dataset-id-field)
                                 :error (:error remote-dataset-id-field)
                                 :required? true
                                 :placeholder "e.g., 01234567-89ab-cdef-0123-456789abcdef"}))
         ;; Local dataset fields
         ($ :<>
            ($ forms/form-field {:label "Name"
                                 :value (:value name-field)
                                 :on-change (:on-change name-field)
                                 :error (:error name-field)
                                 :required? true})
            ($ forms/form-field {:label "Description"
                                 :type :textarea
                                 :value (:value description-field)
                                 :on-change (:on-change description-field)
                                 :error (:error description-field)})
            ($ forms/form-field {:label "Input JSON Schema (Optional)"
                                 :type :textarea
                                 :value (:value input-schema-field)
                                 :on-change (:on-change input-schema-field)
                                 :error (:error input-schema-field)
                                 :placeholder example-schema})
            ($ forms/form-field {:label "Output JSON Schema (Optional)"
                                 :type :textarea
                                 :value (:value output-schema-field)
                                 :on-change (:on-change output-schema-field)
                                 :error (:error output-schema-field)
                                 :placeholder example-schema}))))))

;; =============================================================================
;; NEW: REG-FORM SPECIFICATIONS
;; =============================================================================

(forms/reg-form
 :create-dataset
 {:steps [:main]

  :main
  {:initial-fields (fn [props]
                     (merge {:dataset-type :local
                             ;; Local dataset fields
                             :name ""
                             :description ""
                             :input-schema ""
                             :output-schema ""
                             ;; Remote dataset fields
                             :remote-dataset-id ""
                             :module-name ""
                             :cluster-conductor-host ""
                             :cluster-conductor-port ""}
                            props))

   :validators {:name [(fn [v form-state]
                         (when (and (= (:dataset-type form-state) :local)
                                    (str/blank? v))
                           "Name is required"))]

                :remote-dataset-id [(fn [v form-state]
                                      (when (and (= (:dataset-type form-state) :remote)
                                                 (str/blank? v))
                                        "Remote dataset ID is required"))]
                :module-name [(fn [v form-state]
                                (when (and (= (:dataset-type form-state) :remote)
                                           (str/blank? v))
                                  "Remote module name is required"))]
                :input-schema [forms/valid-json]
                :output-schema [forms/valid-json]
                :cluster-conductor-port [(fn [v form-state]
                                           (when (and (= (:dataset-type form-state) :remote)
                                                      (not (str/blank? v))
                                                      (js/isNaN (js/parseInt v)))
                                             "Port must be a number"))]}

   :ui (fn [{:keys [form-id]}]
         ($ CreateDatasetForm {:form-id form-id}))

   :modal-props {:title "Create New Dataset"
                 :submit-text "Create Dataset"}}
  :on-submit
  {:event (fn [db form-state]
            (if (= (:dataset-type form-state) :remote)
              ;; Remote dataset
              [:datasets/add-remote (-> form-state
                                        (select-keys [:remote-dataset-id :module-name
                                                      :cluster-conductor-host :cluster-conductor-port
                                                      :module-id])
                                        (assoc :cluster-conductor-port
                                               (when-not (str/blank? (:cluster-conductor-port form-state))
                                                 (js/parseInt (:cluster-conductor-port form-state)))))]
              ;; Local dataset
              [:datasets/create (select-keys form-state [:module-id :name :description
                                                         :input-schema :output-schema])]))
   :on-success-invalidate (fn [db {:keys [module-id]} _reply]
                            {:query-key-pattern [:datasets module-id]})}})

(defui EditDatasetForm [{:keys [form-id initial-name initial-description]}]
  (let [{:keys [field-errors]} (forms/use-form form-id)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]

    ($ forms/form
       ($ forms/form-field {:label "Name"
                            :value (:value name-field)
                            :on-change (:on-change name-field)
                            :error (:error name-field)
                            :required? true})
       ($ forms/form-field {:label "Description"
                            :type :textarea
                            :value (:value description-field)
                            :on-change (:on-change description-field)
                            :error (:error description-field)}))))

 ;; =============================================================================
;; EDIT DATASET FORM SPECIFICATION
;; =============================================================================

(forms/reg-form
 :edit-dataset
 {:steps [:main]
  :main
  {:initial-fields (fn [props] props) ; The beautiful simplification - props are the initial fields!
   :validators {:name [forms/required]}
   :ui (fn [{:keys [form-id props]}]
         ;; Props now contains the actual field values directly
         ($ EditDatasetForm {:form-id form-id
                             :initial-name (:name props)
                             :initial-description (:description props)}))
   :modal-props {:title "Edit Dataset"
                 :submit-text "Save Changes"}}
  :on-submit
  (fn [db form-state] ; The elegant single-map signature!
    ;; All data is now in one place - much cleaner!
    (let [{:keys [form-id module-id dataset-id name description initial-name initial-description]} form-state]

      (let [name-promise (js/Promise.
                          (fn [resolve reject]
                            (if (= name initial-name)
                              (resolve {:success true})
                              (sente/request!
                               [:datasets/set-name {:module-id module-id
                                                    :dataset-id dataset-id
                                                    :name name}]
                               5000
                               #(if (:success %) (resolve %) (reject (:error %)))))))
            desc-promise (js/Promise.
                          (fn [resolve reject]
                            (if (= description initial-description)
                              (resolve {:success true})
                              (sente/request!
                               [:datasets/set-description {:module-id module-id
                                                           :dataset-id dataset-id
                                                           :description description}]
                               5000
                               #(if (:success %) (resolve %) (reject (:error %)))))))]

        (-> (.all js/Promise [name-promise desc-promise])
            (.then (fn [_]
                     (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
                     (state/dispatch [:modal/hide])
                     (let [decoded-module-id (when module-id (common/url-decode module-id))]
                       (state/dispatch [:query/invalidate {:query-key-pattern [:datasets decoded-module-id]}])
                       (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-props decoded-module-id dataset-id]}]))
                     (state/dispatch [:form/clear form-id])))
            (.catch (fn [error]
                      (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
                      (state/dispatch [:db/set-value [:forms form-id :error] (str "Failed to save: " error)])))))))})

(defui ExampleForm [{:keys [form-id]}]
  (let [{:keys [field-errors]} (forms/use-form form-id)
        input-field (forms/use-form-field form-id :input)
        output-field (forms/use-form-field form-id :output)]

    ($ forms/form
       ($ forms/form-field {:label "Input (JSON)"
                            :value (:value input-field)
                            :on-change (:on-change input-field)
                            :error (:error input-field)
                            :required? true
                            :type :textarea
                            :placeholder "{\"prompt\": \"Hello world\"}"})
       ($ forms/form-field {:label "Reference Output (JSON, Optional)"
                            :value (:value output-field)
                            :on-change (:on-change output-field)
                            :error (:error output-field)
                            :type :textarea
                            :placeholder "{\"response\": \"Hello there!\"}"}))))
(forms/reg-form
 :add-dataset-example
 {:steps [:main]
  :main
  {:initial-fields (fn [props]
                     (merge {:input ""
                             :output ""}
                            props))
   :validators {:input [forms/required forms/valid-json]
                :output [forms/valid-json]}
   :ui (fn [{:keys [form-id]}]
         ($ ExampleForm {:form-id form-id}))
   :modal-props {:title "Add Example"
                 :submit-text "Add Example"}}
  :on-submit
  {:event (fn [db form-state]
            (try
              (let [parsed-input (-> (:input form-state) js/JSON.parse js->clj)
                    parsed-output (when-not (str/blank? (:output form-state))
                                    (-> (:output form-state) js/JSON.parse js->clj))]
                ;; Return the Sente event with PARSED data
                [:datasets/add-example (assoc form-state
                                              :input parsed-input
                                              :output parsed-output)])
              (catch js/Error e
                ;; If parsing fails, update the form with an error instead of sending.
                (state/dispatch [:db/set-value [:forms (:form-id form-state) :error]
                                 (str "Invalid JSON: " (.-message e))])
                ;; Return nil to prevent Sente request from being sent
                nil)))
   :on-success-invalidate (fn [db {:keys [module-id dataset-id]} _reply]
                            {:query-key-pattern [:dataset-examples module-id dataset-id]})}})

(defn show-add-example-modal! [props]
  (state/dispatch [:modal/show-form :add-dataset-example props]))

(forms/reg-form
 :new-snapshot
 {:steps [:main]
  :main
  {:initial-fields (fn [props] (merge {:to-snapshot-name ""} props))
   :validators {:to-snapshot-name [forms/required]}
   :modal-props {:title "New Snapshot" :submit-text "Create Snapshot"}

   :ui
   (fn [{:keys [form-id]}]
     (let [snapshot-name (forms/use-form-field form-id :to-snapshot-name)]
       ($ forms/form
          ($ forms/form-field {:label "New Snapshot Name"
                               :value (:value snapshot-name)
                               :on-change (:on-change snapshot-name)
                               :error (:error snapshot-name)
                               :required? true}))))}

  :on-submit
  {:event (fn [db form-state]
            [:datasets/create-snapshot form-state])
   :on-success-invalidate (fn [db {:keys [module-id dataset-id]} _reply]
                            {:query-key-pattern [:snapshot-names module-id (str dataset-id)]})
   :on-success (fn [db {:keys [dataset-id]} reply]
                 ;; On success, directly dispatch an event to select the new snapshot
                 (state/dispatch [:datasets/set-selected-snapshot
                                  {:dataset-id dataset-id
                                   :snapshot-name (get-in reply [:data :snapshot-name])}]))}})

(defui CreateSnapshotForm [{:keys [form-id from-snapshot-name]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        snapshot-name-field (forms/use-form-field form-id :snapshot-name)]

    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700 "Source Snapshot")
          ($ :p.mt-1.text-sm.text-gray-500.bg-gray-100.p-2.rounded-md
             (if (str/blank? from-snapshot-name) "Latest (Working Copy)" from-snapshot-name)))

       ($ forms/form-field {:label "New Snapshot Name"
                            :value (:value snapshot-name-field)
                            :on-change (:on-change snapshot-name-field)
                            :error (:error snapshot-name-field)
                            :required? true}))))

;; =============================================================================
;; BULK OPERATION MODALS
;; =============================================================================

(forms/reg-form
 :add-tag-to-selected
 {:steps [:main]
  :main
  {:initial-fields (fn [props] (merge {:tag-name ""} props))
   :validators {:tag-name [forms/required]}
   :ui (fn [{:keys [form-id]}]
         ($ AddTagForm {:form-id form-id}))
   :modal-props {:title "Add Tag to examples"
                 :submit-text "Add Tag"}}
  :on-submit
  {:event (fn [db form-state]
            ;; 1. Get stable IDs from the route.
            (let [{:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)
                  ;; 2. Get the CURRENTLY selected snapshot name from its state path.
                  snapshot-name (s/select-one (state/path->specter-path [:ui :datasets :selected-snapshot-per-dataset dataset-id]) db)
                  ;; 3. Get the CURRENTLY selected example IDs from their state path.
                  example-ids (s/select-one (state/path->specter-path [:ui :datasets :selected-examples dataset-id]) db)
                  ;; 4. Get the tag name from the form state.
                  {:keys [tag-name]} form-state]
              [:datasets/add-tag-to-examples
               {:module-id module-id
                :dataset-id dataset-id
                ;; Only send snapshot-name if it's not blank.
                :snapshot-name (when-not (str/blank? snapshot-name) snapshot-name)
                ;; Ensure example-ids is a vector and not nil.
                :example-ids (vec (or example-ids #{}))
                :tag tag-name}]))
   :on-success-invalidate (fn [db _form-state _reply]
                            (let [{:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)]
                              {:query-key-pattern [:dataset-examples module-id dataset-id]}))
   :on-success (fn [db _form-state _reply]
                 (let [{:keys [dataset-id]} (s/select-one [:route :path-params] db)]
                   (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])))}})

(defn show-add-tag-modal! [props]
  (state/dispatch [:modal/show-form :add-tag-to-selected props]))

(forms/reg-form
 :remove-tag-from-selected
 {:steps [:main]
  :main
  {:initial-fields (fn [props] (merge {:tag-name ""} props))
   :validators {:tag-name [forms/required]}
   :ui (fn [{:keys [form-id props]}]
         ;; The UI for this form needs the list of selected examples to populate the dropdown.
         ($ RemoveTagForm {:form-id form-id
                           :selected-examples (:selected-examples props)}))
   :modal-props {:title "Remove Tag from examples"
                 :submit-text "Remove Tag"}}
  :on-submit
  {:event (fn [db form-state]
            ;; 1. Get stable IDs from the route.
            (let [{:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)
                  ;; 2. Get the CURRENTLY selected snapshot name from its state path.
                  snapshot-name (s/select-one (state/path->specter-path [:ui :datasets :selected-snapshot-per-dataset dataset-id]) db)
                  ;; 3. Get the CURRENTLY selected example IDs from their state path.
                  example-ids (s/select-one (state/path->specter-path [:ui :datasets :selected-examples dataset-id]) db)
                  ;; 4. Get the tag name from the form state.
                  {:keys [tag-name]} form-state]
              [:datasets/remove-tag-from-examples
               {:module-id module-id
                :dataset-id dataset-id
                ;; Only send snapshot-name if it's not blank.
                :snapshot-name (when-not (str/blank? snapshot-name) snapshot-name)
                ;; Ensure example-ids is a vector and not nil.
                :example-ids (vec (or example-ids #{}))
                :tag tag-name}]))
   :on-success-invalidate (fn [db _form-state _reply]
                            (let [{:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)]
                              {:query-key-pattern [:dataset-examples module-id dataset-id]}))
   :on-success (fn [db _form-state _reply]
                 (let [{:keys [dataset-id]} (s/select-one [:route :path-params] db)]
                   (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])))}})

(defn show-remove-tag-modal! [props]
  (state/dispatch [:modal/show-form :remove-tag-from-selected props]))

(defui AddTagForm [{:keys [form-id]}]
  (let [{:keys [field-errors]} (forms/use-form form-id)
        tag-name-field (forms/use-form-field form-id :tag-name)]

    ($ forms/form
       ($ forms/form-field {:label "Tag to add"
                            :value (:value tag-name-field)
                            :on-change (:on-change tag-name-field)
                            :error (:error tag-name-field)
                            :required? true}))))

(defui RemoveTagForm [{:keys [form-id selected-examples]}]
  (let [{:keys [field-errors]} (forms/use-form form-id)
        tag-name-field (forms/use-form-field form-id :tag-name)

        ;; Get all unique tags from selected examples
        all-tags (->> selected-examples
                      (mapcat :tags)
                      (map name) ; Convert keywords to strings
                      (distinct)
                      (sort))]

    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Tag to remove")
          ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.focus:outline-none.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
             {:value (:value tag-name-field)
              :onChange #((:on-change tag-name-field) (.. % -target -value))}
             ($ :option {:value ""} "Select a tag to remove...")
             (for [tag all-tags]
               ($ :option {:key tag :value tag} tag)))
          (when (:error field-errors)
            ($ :div.text-sm.text-red-600.mt-1 (:error field-errors)))))))

(defn handle-delete-selected! [module-id dataset-id snapshot-name example-ids]
  (when (js/confirm (str "Are you sure you want to delete " (count example-ids) " selected examples? This action cannot be undone."))
    (state/dispatch [:dataset/delete-selected
                     {:module-id module-id
                      :dataset-id dataset-id
                      :snapshot-name snapshot-name
                      :example-ids example-ids}])))

(defui ImportDatasetModal [{:keys [module-id dataset-id]}]
  (let [[uploading? set-uploading!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)
        file-input-ref (uix/use-ref nil)

        handle-file-change (fn [e]
                             (let [file (-> e .-target .-files (aget 0))]
                               (when file
                                 (set-uploading! true)
                                 (set-error! nil)
                                 (let [form-data (js/FormData.)
                                       url (str "/api/datasets/" (common/url-encode module-id) "/" (common/url-encode (str dataset-id)) "/import")]
                                   (.append form-data "file" file)
                                   (-> (js/fetch url #js {:method "POST" :body form-data})
                                       (.then (fn [resp]
                                                (if (.-ok resp)
                                                  (.json resp)
                                                  (throw (js/Error. (str "Upload failed with status: " (.-status resp)))))))
                                       (.then (fn [data]
                                                (set-uploading! false)
                                                (state/dispatch [:modal/hide])
                                                ;; Invalidate query to refresh the example list
                                                (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                                ;; Show the results modal
                                                (state/dispatch [:modal/show :import-results
                                                                 {:title "Import Results"
                                                                  :component ($ :div.p-6
                                                                                ;; Summary section
                                                                                ($ :div.mb-6
                                                                                   ($ :div.flex.items-center.gap-4.mb-4
                                                                                      (if (zero? (.-failure_count data))
                                                                                        ($ :div.flex.items-center.gap-2.text-green-700
                                                                                           ($ :svg.h-6.w-6.text-green-600 {:fill "currentColor" :viewBox "0 0 20 20"}
                                                                                              ($ :path {:fillRule "evenodd"
                                                                                                        :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                                                                                                        :clipRule "evenodd"}))
                                                                                           ($ :h3.text-lg.font-semibold "Import Successful"))
                                                                                        ($ :div.flex.items-center.gap-2.text-yellow-700
                                                                                           ($ :svg.h-6.w-6.text-yellow-600 {:fill "currentColor" :viewBox "0 0 20 20"}
                                                                                              ($ :path {:fillRule "evenodd"
                                                                                                        :d "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                                                                                                        :clipRule "evenodd"}))
                                                                                           ($ :h3.text-lg.font-semibold "Import Completed with Errors"))))
                                                                                   ;; Stats
                                                                                   ($ :div.grid.grid-cols-2.gap-4.text-sm
                                                                                      ($ :div.bg-green-50.border.border-green-200.rounded-lg.p-3
                                                                                         ($ :div.text-green-800.font-medium "Successful")
                                                                                         ($ :div.text-2xl.font-bold.text-green-900 (.-success_count data)))
                                                                                      ($ :div.bg-red-50.border.border-red-200.rounded-lg.p-3
                                                                                         ($ :div.text-red-800.font-medium "Failed")
                                                                                         ($ :div.text-2xl.font-bold.text-red-900 (.-failure_count data)))))
                                                                                ;; Errors section
                                                                                (when (and (.-errors data) (> (.-failure_count data) 0))
                                                                                  (let [errors (js->clj (.-errors data) :keywordize-keys true)]
                                                                                    ($ :div.mt-6
                                                                                       ($ :h4.text-md.font-semibold.text-gray-900.mb-3 "Error Details")
                                                                                       ($ :div.max-h-96.overflow-y-auto.border.border-gray-200.rounded-lg
                                                                                          ($ :div.divide-y.divide-gray-200
                                                                                             (for [[idx error] (map-indexed vector errors)]
                                                                                               ($ :div.p-4.hover:bg-gray-50 {:key idx}
                                                                                                  ($ :div.text-sm.font-medium.text-gray-900.mb-2
                                                                                                     (str "Line " (inc idx) ":"))
                                                                                                  ($ :div.text-xs.font-mono.text-gray-600.bg-gray-100.p-2.rounded.mb-2.break-all
                                                                                                     (:line_content error))
                                                                                                  ($ :div.text-sm.text-red-600
                                                                                                     (:error error))))))))))}])))
                                       (.catch (fn [err]
                                                 (set-uploading! false)
                                                 (set-error! (str "An error occurred during upload: " (.-message err))))))))))]

    ($ :div.p-6.space-y-4
       ($ :p.text-sm.text-gray-700
          "Upload a "
          ($ :a.text-blue-600.hover:underline {:href "https://jsonlines.org/examples/" :target "_blank"} "JSONL file")
          " to add examples to this dataset in bulk. Each line in the file should be a valid JSON object representing a single example.")

       ($ :div.bg-gray-50.p-3.rounded-md.border
          ($ :h4.text-sm.font-medium.text-gray-800.mb-2 "Line Format:")
          ($ :p.text-xs.text-gray-600.mb-2
             "Each JSON object can have the following keys:")
          ($ :ul.list-disc.list-inside.space-y-1.text-xs.text-gray-700
             ($ :li ($ :code.font-mono.bg-gray-200.px-1.rounded "input") " (required): The input for the agent.")
             ($ :li ($ :code.font-mono.bg-gray-200.px-1.rounded "output") " (optional): The expected reference output.")
             ($ :li ($ :code.font-mono.bg-gray-200.px-1.rounded "tags") " (optional): An array of strings.")))

       ($ :input {:ref file-input-ref
                  :type "file"
                  :accept ".jsonl"
                  :style {:display "none"}
                  :onChange handle-file-change})

       ($ :div.mt-6.flex.flex-col.items-center
          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick #(.click (.-current file-input-ref))
              :disabled uploading?}
             (if uploading?
               ($ common/spinner {:size :medium})
               ($ DocumentArrowUpIcon {:className "h-5 w-5 mr-2"}))
             (if uploading? "Uploading..." "Choose File..."))
          (when error
            ($ :p.text-sm.text-red-600.mt-2 error))))))
