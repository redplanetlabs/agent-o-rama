(ns com.rpl.agent-o-rama.ui.forms
  "Reusable form utilities and patterns for cleaner form components."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   [com.rpl.specter :as s]
   ["react-dom" :refer [createPortal]]))

(defonce form-specs (atom {}))

(defn reg-form
  "Registers a self-contained form specification."
  [form-id spec]
  (swap! form-specs assoc form-id spec)
  (println "form spects" @form-specs))

(defhook use-form
  [form-id]
  (let [form-state (state/use-sub [:forms form-id])
        {:keys [fields field-errors valid? submitting? error current-step steps]} form-state]
    
    {:fields (or fields {})
     :field-errors (or field-errors {})
     :valid? (boolean valid?)
     :submitting? (boolean submitting?)
     :error error
     :current-step current-step
     :steps steps
     ;; The set-field! function is now a direct dispatch.
     :set-field! (fn [field-path value]
                   (state/dispatch [:form/update-field form-id field-path value]))
     :next-step! #(state/dispatch [:form/next-step form-id])
     :prev-step! #(state/dispatch [:form/prev-step form-id])
     :submit! #(state/dispatch [:form/submit form-id])}))

;; =============================================================================
;; REUSABLE FORM COMPONENTS
;; =============================================================================

(defui form-field
  "Reusable form field component with label, input, and error display.

   Props:
   - :label - Field label text
   - :type - Input type (:text, :textarea, :email, etc.)
   - :value - Current field value
   - :on-change - Change handler function
   - :error - Error message to display
   - :required? - Whether field is required
   - :placeholder - Placeholder text
   - :class-name - Additional CSS classes
   - :rows - For textarea, number of rows"
  [{:keys [label value on-change error required? placeholder class-name type rows]
    :or {type :text rows 3}}]

  (let [input-classes (str "w-full p-3 border rounded-md text-sm transition-colors "
                           (if error
                             "border-red-300 focus:ring-red-500 focus:border-red-500"
                             "border-gray-300 focus:ring-blue-500 focus:border-blue-500")
                           (when class-name (str " " class-name)))

        field-id (str "field-" (random-uuid))]

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          {:htmlFor field-id}
          label
          (when required? ($ :span.text-red-500.ml-1 "*")))

       (case type
         :textarea
         ($ :textarea {:id field-id
                       :className input-classes
                       :value (or value "")
                       :placeholder placeholder
                       :rows rows
                       :onChange #(on-change (.. % -target -value))})

         ;; Default to text input for all other types
         ($ :input {:id field-id
                    :type (name type)
                    :className input-classes
                    :value (or value "")
                    :placeholder placeholder
                    :onChange #(on-change (.. % -target -value))}))

       (if error
         ($ :p.text-sm.text-red-600.mt-1 error)
         ($ :div.mt-1.h-5)))))

(defui form-error
  "Reusable error display component.
   
   Props:
   - :error - Error message to display
   - :class-name - Additional CSS classes"
  [{:keys [error class-name]}]

  (when error
    ($ :div {:className (str "mt-4 p-3 bg-red-50 border border-red-200 rounded-md " class-name)}
       ($ :p.text-sm.text-red-700.whitespace-pre-wrap error))))

(defui form
  [{:keys [children]}]

  ($ :form.p-4
     children))

(defui ModalFormContent [{:keys [form-id modal-data]}]
  (let [form (use-form form-id)
        form-spec (get @form-specs form-id)
        current-step-spec (get form-spec (:current-step form))
        ui-fn (:ui current-step-spec)
        handle-cancel (fn []
                        (state/dispatch [:form/clear form-id])
                        (state/dispatch [:modal/hide]))]

    ($ :<>

       ($ :div {:className "flex-1 min-h-0 overflow-y-auto"}
          (if ui-fn
            (ui-fn {:form-id form-id})
            ($ :div "No UI for this form step.")))

       ;; The footer with form actions
       (when form
         ($ :div {:className "flex-shrink-0 border-t border-gray-200 bg-white px-6 py-4"}
            ($ form-error {:error (:error form)})
            ($ :div {:className "flex justify-end gap-3"}
               ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick handle-cancel} "Cancel")
               
               ;; "Back" button
               (when (and (:steps form) (not= (first (:steps form)) (:current-step form)))
                 ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick (:prev-step! form)} "Back"))

               ;; "Next" or "Submit" button
               (if (and (:steps form) (not= (last (:steps form)) (:current-step form)))
                 ($ :button {:type "button", :disabled (not (:valid? form)), :onClick (:next-step! form)
                             :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium "
                                             (if (not (:valid? form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    "Next")
                 ($ :button {:type "button", :disabled (or (not (:valid? form)) (:submitting? form)), :onClick (:submit! form)
                             :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                             (if (or (not (:valid? form)) (:submitting? form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    (when (:submitting? form) ($ :div {:className "animate-spin rounded-full h-4 w-4 border-b-2 border-white"}))
                    (:submit-text modal-data "Submit")))))))))

(defui global-modal-component []
  (let [modal-state (state/use-sub [:ui :modal])
        {:keys [active data]} modal-state
        form-id (when active (:form-id data))

        handle-cancel (fn []
                        (when form-id (state/dispatch [:form/clear form-id]))
                        (state/dispatch [:modal/hide]))

        handle-keydown (fn [e] (when (= (.-key e) "Escape") (.preventDefault e) (handle-cancel)))]

    (uix/use-effect (fn [] (when active (.addEventListener js/document "keydown" handle-keydown) #(.removeEventListener js/document "keydown" handle-keydown))) [active])

    (when active
      (createPortal
       ($ :div {:className "fixed inset-0 flex items-center justify-center z-50", :style {:backgroundColor "rgba(0, 0, 0, 0.5)"}, :onClick handle-cancel}
          ($ :div {:className "bg-white rounded-lg shadow-xl w-full max-w-5xl overflow-hidden mx-4 my-8 flex flex-col max-h-screen", :role "dialog", :aria-modal "true", :onClick #(.stopPropagation %)}
             ;; Header remains the same
             ($ :div {:className "flex-shrink-0 p-4 border-b border-gray-200 flex justify-between items-center bg-white"}
                ($ :h3 {:className "text-lg font-medium text-gray-800"} (:title data))
                ($ :button {:className "text-gray-400 hover:text-gray-600 text-xl font-bold cursor-pointer", :onClick handle-cancel} "×"))

             ;; Conditionally render the new content component
             (when form-id
               ($ ModalFormContent {:form-id form-id :modal-data data}))))
       (.-body js/document)))))


(defn required [value] (when (str/blank? value) "This field is required"))

(defn min-length
  "Validator for minimum string length"
  [n]
  (fn [value]
    (when (and (string? value) (< (count value) n))
      (str "Must be at least " n " characters long"))))

(defn max-length
  "Validator for maximum string length"
  [n]
  (fn [value]
    (when (and (string? value) (> (count value) n))
      (str "Must be no more than " n " characters long"))))

(defn valid-json
  "Validator for JSON strings"
  [value]
  (when-not (str/blank? value)
    (try
      (js/JSON.parse value)
      nil ; Valid JSON
      (catch js/Error e
        (str "Invalid JSON: " (.-message e))))))

(defn- validate-form-fields
  "Validate fields against validators keyed by Specter paths.
   Returns a map {:valid? boolean :errors {nested-error-map}}"
  [fields validators]
  (reduce-kv
   (fn [acc path validator-fns]
     (let [value (s/select-one path fields)
           first-error (some #(% value) validator-fns)]
       (if first-error
         (-> acc
             (assoc :valid? false)
             (update :errors #(s/setval path first-error %)))
         acc)))
   {:valid? true, :errors {}}
   validators))

(state/reg-event :form/update-field
           (fn [db form-id field-path value]
             (let [form-spec (@form-specs form-id)
                   current-step-key (get-in db [:forms form-id :current-step])
                   step-spec (get form-spec current-step-key form-spec)
                   validators-for-field (get-in step-spec [:validators field-path])]

               (s/multi-path
                ;; Path 1: Update the field's value
                [:forms form-id :fields (s/path field-path) (s/terminal-val value)]

                ;; Path 2: Update only this field's error message
                [:forms form-id :field-errors (s/path field-path)
                 (s/terminal-val (some #(% value) validators-for-field))]))))

(state/reg-event :form/validate
           (fn [db form-id]
             (let [form-state (get-in db [:forms form-id])
                   form-spec (@form-specs form-id)
                   current-step-key (:current-step form-state)
                   step-spec (get form-spec current-step-key form-spec)
                   {:keys [valid? errors]} (validate-form-fields (:fields form-state) (:validators step-spec))]
               [:forms form-id (s/terminal #(assoc % :valid? valid? :field-errors errors))])))

(state/reg-event :form/next-step
                 (fn [db form-id]
                   (let [form-state (get-in db [:forms form-id])
                         form-spec (@form-specs form-id)
                         {:keys [valid? current-step steps]} form-state]
                     (when valid?
                       (let [current-idx (.indexOf steps current-step)
                             next-step (get steps (inc current-idx))]
                         (when next-step
                           [:forms form-id :current-step (s/terminal-val next-step)]))))))

(state/reg-event :form/prev-step
                 (fn [db form-id]
                   (let [form-state (get-in db [:forms form-id])
                         {:keys [current-step steps]} form-state
                         current-idx (.indexOf steps current-step)
                         prev-step (get steps (dec current-idx))]
                     (when prev-step
                       [:forms form-id :current-step (s/terminal-val prev-step)]))))

(state/reg-event :form/submit
                 (fn [db form-id]
                   (let [form-state (get-in db [:forms form-id])
                         form-spec (@form-specs form-id)
                         current-step-key (:current-step form-state)
                         step-spec (get form-spec current-step-key form-spec)
                         {:keys [valid? errors]} (validate-form-fields (:fields form-state) (:validators step-spec))]

                     (if-not valid?
                       (dispatch [:db/set-value
                                  [:forms form-id]
                                  (assoc form-state :valid? false :field-errors errors)])
                       (let [on-submit-handler (:on-submit form-spec)]
                         ;; Set submitting state and let the handler run its side-effect
                         (dispatch [:db/set-value [:forms form-id] (assoc form-state :submitting? true :error nil)])
                         (when on-submit-handler
                           (on-submit-handler db {:form-id form-id
                                                  :form-fields (:fields form-state)
                                                  :props (:props form-state)})))))
                   nil))

(state/reg-event :form/clear
                 (fn [db form-id]
                   ;; TODO use s/NONE not dissoc
                   [:forms (s/terminal #(dissoc % form-id))]))


;; =============================================================================
;; NEW MODAL AND FORM INTEGRATION EVENTS
;; =============================================================================

(state/reg-event
 :modal/show-form
 
 (fn [db form-id props]
   (let [form-spec (get @form-specs form-id)]
     (if-not form-spec
       (do (js/console.error "No form spec registered for" form-id) nil)
       (let [initial-step (first (:steps form-spec))
             step-spec (get form-spec initial-step)
             initial-fields-fn (:initial-fields step-spec)
             initial-fields (if (fn? initial-fields-fn)
                              (initial-fields-fn props)
                              (or initial-fields-fn {}))
             validators (:validators step-spec)
             {:keys [valid? errors]} (validate-form-fields initial-fields validators)
             modal-data (get-in form-spec [initial-step :modal-props] {})

             ;; 1. Construct the full state for the form
             form-state {:fields initial-fields
                         :validators validators
                         :field-errors errors
                         :valid? valid?
                         :submitting? false
                         :error nil
                         :props props
                         :steps (:steps form-spec)
                         :current-step initial-step}
             
             ;; 2. Construct the full state for the modal
             modal-state {:active form-id
                          :data (assoc modal-data :form-id form-id)}]
         
         ;; 3. Return a single Specter path to update both parts of the DB atomically
         (s/multi-path
          [:forms form-id (s/terminal-val form-state)]
          [:ui :modal (s/terminal-val modal-state)]))))))


(state/reg-event :modal/show
                 (fn [db modal-type modal-data]
                   [:ui :modal (s/terminal-val {:active modal-type
                                                :data modal-data})]))

(state/reg-event :modal/hide
                 (fn [db]
                   [:ui :modal (s/terminal-val {:active nil
                                                :data {}})]))


