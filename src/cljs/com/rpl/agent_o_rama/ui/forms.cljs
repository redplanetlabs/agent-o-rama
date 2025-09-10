(ns com.rpl.agent-o-rama.ui.forms
  "Reusable form utilities and patterns for cleaner form components."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]))

(defonce forms-specs (atom {}))

(defn reg-form
  "Registers a self-contained form specification."
  [form-id spec]
  (swap! form-specs assoc form-id spec))

(defhook use-form
  "The primary hook for components to interact with form state.
   It provides reactive state and memoized action dispatchers."
  [form-id]
  (let [form-state (state/use-sub [:forms form-id])
        {:keys [fields field-errors valid? submitting? error current-step steps]} form-state

        ;; Memoized action dispatchers
        set-field! (uix/use-callback
                    (fn [field-key value]
                      (state/dispatch [:form/update-field form-id field-key value]))
                    [form-id])

        next-step! (uix/use-callback
                    #(state/dispatch [:form/next-step form-id])
                    [form-id])

        prev-step! (uix/use-callback
                    #(state/dispatch [:form/prev-step form-id])
                    [form-id])

        submit! (uix/use-callback
                 #(state/dispatch [:form/submit form-id])
                 [form-id])]

    {:fields (or fields {})
     :field-errors (or field-errors {})
     :valid? (boolean valid?)
     :submitting? (boolean submitting?)
     :error error
     :current-step current-step
     :steps steps
     :set-field! set-field!
     :next-step! next-step!
     :prev-step! prev-step!
     :submit! submit!}))

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

;; TODO delete maybe?
#_
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

(defui global-modal-component []
  (let [modal-state (state/use-sub [:ui :modal])
        {:keys [active data]} modal-state
        form-id (when active (:form-id data))

        ;; Use our new form hook. It will return nil if form-id is nil.
        form (when form-id (forms/use-form form-id))

        handle-cancel (fn []
                        (when form-id (state/dispatch [:form/clear form-id]))
                        (state/dispatch [:modal/hide]))

        handle-keydown (fn [e] (when (= (.-key e) "Escape") (.preventDefault e) (handle-cancel)))]

    (uix/use-effect (fn [] (when active (.addEventListener js/document "keydown" handle-keydown) #(.removeEventListener js/document "keydown" handle-keydown))) [active])

    (when active
      (react-dom/createPortal
       ($ :div {:className "fixed inset-0 flex items-center justify-center z-50", :style {:backgroundColor "rgba(0, 0, 0, 0.5)"}, :onClick handle-cancel}
          ($ :div {:className "bg-white rounded-lg shadow-xl w-full max-w-5xl overflow-hidden mx-4 my-8 flex flex-col max-h-screen", :role "dialog", :aria-modal "true", :onClick #(.stopPropagation %)}
             ($ :div {:className "flex-shrink-0 p-4 border-b border-gray-200 flex justify-between items-center bg-white"}
                ($ :h3 {:className "text-lg font-medium text-gray-800"} (:title data))
                ($ :button {:className "text-gray-400 hover:text-gray-600 text-xl font-bold cursor-pointer", :onClick handle-cancel} "×"))
             ($ :div {:className "flex-1 min-h-0 overflow-y-auto"}
                (if form
                  (let [form-spec (get @forms/form-specs form-id)
                        ui-fn (if (:steps form)
                                (get-in form-spec [(:current-step form) :ui])
                                (:ui form-spec))]
                    (if ui-fn (ui-fn {:form-id form-id}) ($ :div "No UI for this form step.")))
                  (:component data))) ; Fallback for non-form modals
             (when form
               ($ :div {:className "flex-shrink-0 border-t border-gray-200 bg-white px-6 py-4"}
                  ($ forms/form-error {:error (:error form)})
                  ($ :div {:className "flex justify-end gap-3"}
                     ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick handle-cancel} "Cancel")
                     (when (and (:steps form) (not= (first (:steps form)) (:current-step form)))
                       ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick (:prev-step! form)} "Back"))
                     (if (and (:steps form) (not= (last (:steps form)) (:current-step form)))
                       ($ :button {:type "button", :disabled (not (:valid? form)), :onClick (:next-step! form)
                                   :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium "
                                                   (if (not (:valid? form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                          "Next")
                       ($ :button {:type "button", :disabled (or (not (:valid? form)) (:submitting? form)), :onClick (:submit! form)
                                   :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                                   (if (or (not (:valid? form)) (:submitting? form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                          (when (:submitting? form) ($ :div {:className "animate-spin rounded-full h-4 w-4 border-b-2 border-white"}))
                          "Submit"))))))))
      (.-body js/document))))

