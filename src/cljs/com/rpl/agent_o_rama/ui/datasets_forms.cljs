(ns com.rpl.agent-o-rama.ui.datasets-forms
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon XMarkIcon LockClosedIcon InformationCircleIcon]]
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
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)
        input-schema-field (forms/use-form-field form-id :input-schema)
        output-schema-field (forms/use-form-field form-id :output-schema)]

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
                            :error (:error description-field)})
       ($ forms/form-field {:label "Input JSON Schema"
                            :type :textarea
                            :value (:value input-schema-field)
                            :on-change (:on-change input-schema-field)
                            :error (:error input-schema-field)
                            :placeholder example-schema})
       ($ forms/form-field {:label "Output JSON Schema"
                            :type :textarea
                            :value (:value output-schema-field)
                            :on-change (:on-change output-schema-field)
                            :error (:error output-schema-field)
                            :placeholder example-schema}))))

;; =============================================================================
;; NEW: REG-FORM SPECIFICATIONS
;; =============================================================================

(forms/reg-form
 :create-dataset
 {:steps [:main]

  :main
  {:initial-fields (fn [props]
                     {:name ""
                      :description ""
                      :input-schema ""
                      :output-schema ""})

   :validators {:name [forms/required]
                :input-schema [forms/valid-json]
                :output-schema [forms/valid-json]}

   :ui (fn [{:keys [form-id]}]
         ($ CreateDatasetForm {:form-id form-id}))

   :modal-props {:title "Create New Dataset"
                 :submit-text "Create Dataset"}}
  :on-submit
  (fn [db {:keys [form-id form-fields props]}]
    (let [{:keys [module-id]} props
          {:keys [name description input-schema output-schema]} form-fields]
      (sente/request!
       [:datasets/create {:module-id module-id
                          :name name
                          :description description
                          :input-schema input-schema
                          :output-schema output-schema}]
       15000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             (let [decoded-module-id (when module-id (common/url-decode module-id))]
               (state/dispatch [:query/invalidate {:query-key-pattern [:datasets decoded-module-id]}]))
             (state/dispatch [:form/clear form-id]))
           (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)]))))))})

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
  {:initial-fields (fn [props]
                     {:name (:initial-name props)
                      :description (:initial-description props)})
   :validators {:name [forms/required]}
   :ui (fn [{:keys [form-id props]}]
         ($ EditDatasetForm {:form-id form-id
                             :initial-name (:initial-name props)
                             :initial-description (:initial-description props)}))
   :modal-props {:title "Edit Dataset"
                 :submit-text "Save Changes"}}
  :on-submit
  (fn [db {:keys [form-id form-fields props]}]
    ;; This logic is moved from the old :dataset/edit event handler
    (let [{:keys [module-id dataset-id initial-name initial-description]} props
          {:keys [name description]} form-fields]

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
  {:initial-fields (fn [_props]
                     {:input ""
                      :output ""})
   :validators {:input [forms/required forms/valid-json]
                :output [forms/valid-json]}
   :ui (fn [{:keys [form-id]}]
         ($ ExampleForm {:form-id form-id}))
   :modal-props {:title "Add Example"
                 :submit-text "Add Example"}}
  :on-submit
  (fn [db {:keys [form-id form-fields props]}]
    ;; This logic is moved from events.cljs and adapted
    (let [{:keys [module-id dataset-id snapshot-name]} props
          {:keys [input output]} form-fields]
      (sente/request!
       [:datasets/add-example {:module-id module-id
                               :dataset-id dataset-id
                               :snapshot-name snapshot-name
                               :input input
                               :output output}]
       10000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
             (state/dispatch [:form/clear form-id]))
           (state/dispatch [:db/set-value [:forms form-id :error] (or (:error reply) "An unknown server error occurred.")]))))))})

(defn show-add-example-modal! [props]
  (state/dispatch [:modal/show-form :add-dataset-example props]))

(forms/reg-form
 :new-snapshot
 {:steps [:main]
  :main
  {:initial-fields (fn [_] {:to-snapshot-name ""})
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
  (fn [db {:keys [form-id form-fields props]}]
    (let [{:keys [module-id dataset-id from-snapshot-name]} props
          {:keys [to-snapshot-name]} form-fields]
      (sente/request!
       [:datasets/create-snapshot {:module-id module-id
                                   :dataset-id dataset-id
                                   :from-snapshot-name from-snapshot-name
                                   :to-snapshot-name to-snapshot-name}]
       15000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             ;; On success, directly dispatch an event to select the new snapshot
             (state/dispatch [:datasets/set-selected-snapshot
                              {:dataset-id dataset-id
                               :snapshot-name (get-in reply [:data :snapshot-name])}])
             ;; Invalidate the query to refetch the list of snapshots
             (state/dispatch [:query/invalidate {:query-key-pattern [:snapshot-names module-id dataset-id]}])
             (state/dispatch [:form/clear form-id]))
           ;; On failure, display the error in the form
           (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)]))))))})

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
  {:initial-fields (fn [_props] {:tag-name ""})
   :validators {:tag-name [forms/required]}
   :ui (fn [{:keys [form-id]}]
         ($ AddTagForm {:form-id form-id}))
   :modal-props {:title "Add Tag to examples"
                 :submit-text "Add Tag"}}
  :on-submit
  (fn [db {:keys [form-id form-fields props]}]
    (let [;; 1. Get stable IDs from the route.
          {:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)

          ;; 2. Get the CURRENTLY selected snapshot name from its state path.
          snapshot-name (s/select-one [:ui :datasets :selected-snapshot-per-dataset dataset-id] db)

          ;; 3. Get the CURRENTLY selected example IDs from their state path.
          example-ids (s/select-one [:ui :datasets :selected-examples dataset-id] db)

          ;; 4. Get the tag name from the form's fields.
          {:keys [tag-name]} form-fields]

      (sente/request!
       [:datasets/add-tag-to-examples
        {:module-id module-id
         :dataset-id dataset-id
         ;; Only send snapshot-name if it's not blank.
         :snapshot-name (when-not (str/blank? snapshot-name) snapshot-name)
         ;; Ensure example-ids is a vector and not nil.
         :example-ids (vec (or example-ids #{}))
         :tag tag-name}]
       15000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])
             ;; Invalidate the query to force a refetch of the examples list.
             (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
             (state/dispatch [:form/clear form-id]))
           (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)]))))))})

(defn show-add-tag-modal! [props]
  (state/dispatch [:modal/show-form :add-tag-to-selected props]))

(forms/reg-form
 :remove-tag-from-selected
 {:steps [:main]
  :main
  {:initial-fields (fn [_props] {:tag-name ""})
   :validators {:tag-name [forms/required]}
   :ui (fn [{:keys [form-id props]}]
         ;; Debug logging
         (println "remove-tag-from-selected :ui props:" props)
         (println "remove-tag-from-selected :ui selected-examples:" (:selected-examples props))
         ;; The UI for this form needs the list of selected examples to populate the dropdown.
         ($ RemoveTagForm {:form-id form-id
                           :selected-examples (:selected-examples props)}))
   :modal-props {:title "Remove Tag from examples"
                 :submit-text "Remove Tag"}}
  :on-submit
  (fn [db {:keys [form-id form-fields props]}]
    (let [;; 1. Get stable IDs from the route.
          {:keys [module-id dataset-id]} (s/select-one [:route :path-params] db)

          ;; 2. Get the CURRENTLY selected snapshot name from its state path.
          snapshot-name (s/select-one [:ui :datasets :selected-snapshot-per-dataset dataset-id] db)

          ;; 3. Get the CURRENTLY selected example IDs from their state path.
          example-ids (s/select-one [:ui :datasets :selected-examples dataset-id] db)

          ;; 4. Get the tag name from the form's fields.
          {:keys [tag-name]} form-fields]

      (sente/request!
       [:datasets/remove-tag-from-examples
        {:module-id module-id
         :dataset-id dataset-id
         ;; Only send snapshot-name if it's not blank.
         :snapshot-name (when-not (str/blank? snapshot-name) snapshot-name)
         ;; Ensure example-ids is a vector and not nil.
         :example-ids (vec (or example-ids #{}))
         :tag tag-name}]
       15000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])
             ;; Invalidate the query to force a refetch of the examples list.
             (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
             (state/dispatch [:form/clear form-id]))
           (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)]))))))})

(defn show-remove-tag-modal! [props]
  (println "show-remove-tag-modal! props:" props)
  (println "show-remove-tag-modal! selected-examples:" (:selected-examples props))
  (state/dispatch [:modal/show-form :remove-tag-from-selected props]))

;; DELETED: The old specs are no longer needed.
;; (def add-tag-form-spec ...)
;; (def remove-tag-form-spec ...)

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

        ;; Debug g
        _ (println "RemoveTagForm selected-examples:" selected-examples)
        _ (println "RemoveTagForm selected-examples count:" (count selected-examples))

        ;; Get all unique tags from selected examples
        all-tags (->> selected-examples
                      (mapcat :tags)
                      (map name) ; Convert keywords to strings
                      (distinct)
                      (sort))

        ;; Debug logging for tags
        _ (println "RemoveTagForm all-tags:" all-tags)]

    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Tag to remove")
          ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.focus:outline-none.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
             {:value (:value tag-name-field)
              :onChange (:on-change tag-name-field)}
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
