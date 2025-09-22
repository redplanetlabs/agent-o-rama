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
                     (merge {:name ""
                             :description ""
                             :input-schema ""
                             :output-schema ""}
                            props))

   :validators {:name [forms/required]
                :input-schema [forms/valid-json]
                :output-schema [forms/valid-json]}

   :ui (fn [{:keys [form-id]}]
         ($ CreateDatasetForm {:form-id form-id}))

   :modal-props {:title "Create New Dataset"
                 :submit-text "Create Dataset"}}
  :on-submit
  {:event (fn [db form-state]
            [:datasets/create form-state])
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
                            {:query-key-pattern [:snapshot-names module-id dataset-id]})
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
                  snapshot-name (s/select-one [:ui :datasets :selected-snapshot-per-dataset dataset-id] db)
                  ;; 3. Get the CURRENTLY selected example IDs from their state path.
                  example-ids (s/select-one [:ui :datasets :selected-examples dataset-id] db)
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
                  snapshot-name (s/select-one [:ui :datasets :selected-snapshot-per-dataset dataset-id] db)
                  ;; 3. Get the CURRENTLY selected example IDs from their state path.
                  example-ids (s/select-one [:ui :datasets :selected-examples dataset-id] db)
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
