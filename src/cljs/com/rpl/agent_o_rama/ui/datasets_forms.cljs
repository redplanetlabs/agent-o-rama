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

(defn show-create-dataset-modal!
  "Shows the create dataset modal."
  [module-id]
  (state/dispatch [:modal/show-form :create-dataset {:module-id module-id}]))

(def edit-dataset-form-spec
  {:fields {:name ""
            :description ""}
   :validators {:name [forms/required]}
   :submit-event [:dataset/edit]})

(defui EditDatasetForm [{:keys [form-id initial-name initial-description]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]

    ;; Set initial values when component mounts
    (uix/use-effect
     (fn []
       ((:on-change name-field) initial-name)
       ((:on-change description-field) initial-description))
     [initial-name initial-description])

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

(defn example-form-spec [config]
  {:fields {:input ""
            :output ""}
   :validators {:input [forms/required forms/valid-json]
                :output [forms/valid-json]}
   :submit-event [:dataset/add-example config]})

(defui ExampleForm [{:keys [form-id]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
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

(defn show-add-example-modal! [config]
  (state/dispatch [:form/init :add-example
                   (example-form-spec config)])
  (state/dispatch [:modal/show :add-example
                   {:title "Add Example"
                    :form-id :add-example
                    :submit-text "Add Example"
                    :component ($ ExampleForm {:form-id :add-example})}]))

(def create-snapshot-form-spec
  {:fields {:snapshot-name ""}
   :validators {:snapshot-name [forms/required]}
   :submit-event [:dataset/create-snapshot]})

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

(defn show-create-snapshot-modal!
  "Shows the create snapshot modal and accepts an on-success callback."
  [module-id dataset-id from-snapshot-name on-success] ;; Add on-success parameter
  (state/dispatch [:form/init :create-snapshot
                   (-> create-snapshot-form-spec
                       (assoc :submit-event [:dataset/create-snapshot {:module-id module-id
                                                                       :dataset-id dataset-id
                                                                       :from-snapshot-name from-snapshot-name
                                                                       :on-success on-success}]))]) ;; Pass on-success
  (state/dispatch [:modal/show :create-snapshot
                   {:title "Create New Snapshot"
                    :form-id :create-snapshot
                    :submit-text "Create Snapshot"
                    :component ($ CreateSnapshotForm {:form-id :create-snapshot
                                                      :from-snapshot-name from-snapshot-name})}]))

 ;; =============================================================================
;; BULK OPERATION MODALS
;; =============================================================================

(def add-tag-form-spec
  {:fields {:tag-name ""}
   :validators {:tag-name [forms/required]}
   :submit-event [:dataset/add-tag-to-selected]})

(defui AddTagForm [{:keys [form-id]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        tag-name-field (forms/use-form-field form-id :tag-name)]

    ($ forms/form
       ($ forms/form-field {:label "Tag to add"
                            :value (:value tag-name-field)
                            :on-change (:on-change tag-name-field)
                            :error (:error tag-name-field)
                            :required? true}))))

(defn show-add-tag-modal! [module-id dataset-id snapshot-name example-ids]
  (state/dispatch [:form/init :add-tag-to-selected
                   (-> add-tag-form-spec
                       (assoc :submit-event [:dataset/add-tag-to-selected
                                             {:module-id module-id
                                              :dataset-id dataset-id
                                              :snapshot-name snapshot-name
                                              :example-ids example-ids}]))])
  (state/dispatch [:modal/show :add-tag-to-selected
                   {:title (str "Add Tag to " (count example-ids) " Examples")
                    :form-id :add-tag-to-selected
                    :submit-text "Add Tag"
                    :component ($ AddTagForm {:form-id :add-tag-to-selected})}]))

(def remove-tag-form-spec
  {:fields {:tag-name ""}
   :validators {:tag-name [forms/required]}
   :submit-event [:dataset/remove-tag-from-selected]})

(defui RemoveTagForm [{:keys [form-id selected-examples]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
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
          (when (:error tag-name-field)
            ($ :div.text-sm.text-red-600.mt-1 (:error tag-name-field)))))))

(defn show-remove-tag-modal! [module-id dataset-id snapshot-name example-ids selected-examples]
  (state/dispatch [:form/init :remove-tag-from-selected
                   (-> remove-tag-form-spec
                       (assoc :submit-event [:dataset/remove-tag-from-selected
                                             {:module-id module-id
                                              :dataset-id dataset-id
                                              :snapshot-name snapshot-name
                                              :example-ids example-ids}]))])
  (state/dispatch [:modal/show :remove-tag-from-selected
                   {:title (str "Remove Tag from " (count example-ids) " Examples")
                    :form-id :remove-tag-from-selected
                    :submit-text "Remove Tag"
                    :component ($ RemoveTagForm {:form-id :remove-tag-from-selected
                                                 :selected-examples selected-examples})}]))

(defn handle-delete-selected! [module-id dataset-id snapshot-name example-ids]
  (when (js/confirm (str "Are you sure you want to delete " (count example-ids) " selected examples? This action cannot be undone."))
    (state/dispatch [:dataset/delete-selected
                     {:module-id module-id
                      :dataset-id dataset-id
                      :snapshot-name snapshot-name
                      :example-ids example-ids}])))
