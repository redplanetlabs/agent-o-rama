(ns com.rpl.agent-o-rama.ui.forms
  "Reusable form utilities and patterns for cleaner form components."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]))

;; =============================================================================
;; REUSABLE FORM HOOKS
;; =============================================================================

(defhook use-form-state
  "Hook for managing form field state with optional validation.

   Usage:
   (let [{:keys [value set-value error]} (use-form-state initial-value validators)]
     ...)"
  [initial-value & [validators]]
  (let [[value set-value] (uix/use-state initial-value)
        [error set-error] (uix/use-state nil)

         ;; Validate the current value whenever it changes
        _ (uix/use-effect
           (fn []
             (let [validation-error (when (seq validators)
                                      (some #(% value) validators))]
               (set-error validation-error)))
           [value])]

    {:value value
     :set-value set-value
     :error error
     :set-error set-error}))

(defhook use-global-form-submission
  "Hook that provides access to global form submission state.
   
   Returns a map with:
   - :submitting? - boolean indicating if form is submitting
   - :error - current error message (or nil)
   - :clear-error - function to clear error
   - :submit - function that dispatches the form event"
  [form-event]
  (let [submitting? (state/use-sub [:ui :modal :form :submitting?])
        error (state/use-sub [:ui :modal :form :error])

        clear-error (uix/use-callback
                     #(state/dispatch [:db/set-value [:ui :modal :form :error] nil])
                     [])

        submit (uix/use-callback
                (fn [form-data]
                  (state/dispatch [form-event form-data]))
                [form-event])]

    {:submitting? submitting?
     :error error
     :clear-error clear-error
     :submit submit}))

(defhook use-centralized-form
  "Hook for working with centralized form state management.
   
   Usage:
   (let [{:keys [fields field-errors set-field valid? submitting? error]} (use-centralized-form form-id)]
     ...)"
  [form-id]
  (let [form-state (state/use-sub [:forms form-id])
        fields (or (:fields form-state) {})
        field-errors (or (:field-errors form-state) {})
        valid? (boolean (:valid? form-state))
        submitting? (boolean (:submitting? form-state))
        error (:error form-state)

        set-field (uix/use-callback
                   (fn [field-key value]
                     (state/dispatch [:form/update-field form-id field-key value]))
                   [form-id])

        get-field (uix/use-callback
                   (fn [field-key]
                     (get fields field-key ""))
                   [fields])]

    {:fields fields
     :field-errors field-errors
     :get-field get-field
     :set-field set-field
     :valid? valid?
     :submitting? submitting?
     :error error}))

(defhook use-form-field
  "Hook for individual form field that integrates with centralized state.
   
   Usage:
   (let [{:keys [value error on-change]} (use-form-field form-id :name)]
     ...)"
  [form-id field-key]
  (let [{:keys [get-field set-field field-errors]} (use-centralized-form form-id)
        value (get-field field-key)
        error (get field-errors field-key)
        on-change (uix/use-callback
                   (fn [new-value]
                     (set-field field-key new-value))
                   [set-field field-key])]

    {:value value
     :error error
     :on-change on-change}))

;; =============================================================================
;; COMMON FORM VALIDATORS
;; =============================================================================

(def required
  "Validator for required fields"
  (fn [value]
    (when (str/blank? value)
      "This field is required")))

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

(def valid-json
  "Validator for JSON strings"
  (fn [value]
    (when-not (str/blank? value)
      (try
        (js/JSON.parse value)
        nil ; Valid JSON
        (catch js/Error e
          (str "Invalid JSON: " (.-message e)))))))

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
                       :value value
                       :placeholder placeholder
                       :rows rows
                       :onChange #(on-change (.. % -target -value))})

         ;; Default to text input for all other types
         ($ :input {:id field-id
                    :type (name type)
                    :className input-classes
                    :value value
                    :placeholder placeholder
                    :onChange #(on-change (.. % -target -value))}))

       (when error
         ($ :p.text-sm.text-red-600.mt-1 error)))))

(defui form-actions
  "Reusable form action buttons (Cancel/Submit).
   
   Props:
   - :on-cancel - Cancel button handler
   - :on-submit - Submit button handler  
   - :submit-text - Text for submit button
   - :submitting? - Whether form is submitting
   - :disabled? - Whether submit should be disabled
   - :submit-variant - :primary (default) or :secondary"
  [{:keys [on-cancel on-submit submit-text submitting? disabled? submit-variant]
    :or {submit-text "Submit" submit-variant :primary}}]

  (let [submit-classes (case submit-variant
                         :secondary "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50"
                         :primary (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                       (if disabled?
                                         "text-gray-400 bg-gray-300 cursor-not-allowed"
                                         "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer")))]

    ($ :div.mt-6.flex.justify-end.gap-3
       ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer
          {:type "button" :onClick on-cancel}
          "Cancel")

       ($ :button
          {:type "button"
           :disabled disabled?
           :onClick #(when-not disabled? (on-submit))
           :className submit-classes}
          (when submitting? ($ common/spinner {:size :medium}))
          submit-text))))

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

;; =============================================================================
;; EXAMPLE USAGE PATTERN
;; =============================================================================

(comment
  ;; Example of how to use these utilities in a form component:

  (defui my-form-component [{:keys [on-success]}]
    (let [name-field (use-form-state "" [required (min-length 3)])
          email-field (use-form-state "" [required])
          {:keys [submitting? error submit]} (use-global-form-submission :my-form/submit)

          is-valid? (and (nil? (:error name-field))
                         (nil? (:error email-field))
                         (not (str/blank? (:value name-field)))
                         (not (str/blank? (:value email-field))))]

      ($ :form
         ($ form-field {:label "Name"
                        :value (:value name-field)
                        :on-change (:set-value name-field)
                        :error (:error name-field)
                        :required? true})

         ($ form-field {:label "Email"
                        :type :email
                        :value (:value email-field)
                        :on-change (:set-value email-field)
                        :error (:error email-field)
                        :required? true})

         ($ form-error {:error error})

         ($ form-actions {:on-cancel #(state/dispatch [:modal/hide])
                          :on-submit #(submit {:name (:value name-field)
                                               :email (:value email-field)
                                               :on-success on-success})
                          :submit-text "Create User"
                          :submitting? submitting?
                          :disabled? (or submitting? (not is-valid?))})))))
