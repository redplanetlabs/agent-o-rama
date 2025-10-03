(ns com.rpl.agent-o-rama.ui.forms
  "Reusable form utilities and patterns for cleaner form components."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [clojure.string :as str]
   [com.rpl.specter :as s]
   ["react-dom" :refer [createPortal]]))

(defonce form-specs (atom {}))

(defn reg-form
  "Registers a self-contained form specification."
  [form-id spec]
  (swap! form-specs assoc form-id spec))

(defhook use-form
  [form-id]
  (let [form-state (state/use-sub [:forms form-id])
        {:keys [field-errors valid? submitting? error current-step steps]} form-state]

    (merge form-state
           {:field-errors (or field-errors {})
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
            :submit! #(state/dispatch [:form/submit form-id])})))

(defhook use-form-field
  "Subscribes to a single field's state within a form."
  [form-id field-key]
  ;; Ensure field-key is always a vector for consistency with specter paths
  (let [field-path (if (vector? field-key) field-key [field-key])]
    (let [value (state/use-sub (into [:forms form-id] field-path))
          error (state/use-sub (into [:forms form-id :field-errors] field-path))

          ;; Memoize the on-change handler for performance.
          ;; It will only be recreated if form-id or field-path changes.
          on-change (uix/use-callback
                     (fn [new-value]
                       (state/dispatch [:form/update-field form-id field-path new-value]))
                     [form-id field-path])]

      ;; Return the convenient props map
      {:value value
       :on-change on-change
       :error error})))
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

 ;; =============================================================================
;; WIZARD FORM COMPONENTS
;; =============================================================================

(defui WizardProgressBar [{:keys [steps current-step]}]
  ;; Only render if there are multiple steps
  (when (> (count steps) 1)
    ($ :div.mb-8.px-4.pt-4
       ($ :ol.flex.items-center.w-full
          (for [[idx step-key] (map-indexed vector steps)
                :let [step-title (-> (name step-key) (str/replace "-" " ") str/capitalize)
                      current-step-idx (.indexOf steps current-step)
                      is-done (< idx current-step-idx)
                      is-current (= idx current-step-idx)
                      is-last? (= idx (dec (count steps)))]]
            ($ :li {:key (str step-key)
                    :className (common/cn "flex w-full items-center"
                                          {"after:content-[''] after:w-full after:h-1 after:border-b after:border-4 after:inline-block" (not is-last?)}
                                          (if is-done "after:border-blue-600" "after:border-gray-200"))}
               ($ :span {:className (common/cn "flex items-center justify-center w-10 h-10 rounded-full shrink-0"
                                               (if (or is-done is-current) "bg-blue-100 text-blue-600" "bg-gray-100 text-gray-500"))}
                  ($ :span.text-xs.font-bold step-title))))))))

(defui WizardForm [{:keys [form-id]}]
  (let [form (use-form form-id)
        form-spec (get @form-specs form-id)
        {:keys [steps current-step]} form
        current-step-spec (get form-spec current-step)
        ui-fn (:ui current-step-spec)]

    ($ :div.flex.flex-col.h-full
       ;; Only show progress bar for multi-step wizards
       (when (> (count steps) 1)
         ($ WizardProgressBar {:steps steps :current-step current-step}))

       ($ :div.flex-1.min-h-0.overflow-y-auto
          (if ui-fn
            (ui-fn {:form-id form-id :props form})
            ($ :div.p-8.text-center.text-gray-500
               (str "No UI defined for step: " current-step)))))))

(defui ModalFormContent [{:keys [form-id modal-data]}]
  (let [form (use-form form-id)
        form-spec (get @form-specs form-id)
        is-wizard? (seq (:steps form-spec)) ;; Check if it's a wizard
        handle-cancel (fn []
                        (state/dispatch [:form/clear form-id])
                        (state/dispatch [:modal/hide]))]

    ($ :<>
       ;; Main content area
       ($ :div {:className "flex-1 min-h-0 overflow-y-auto"}
          (if is-wizard?
            ($ WizardForm {:form-id form-id}) ;; Render the generic wizard
            (let [ui-fn (or (:ui form-spec) (get-in form-spec [:main :ui]))]
              (if ui-fn
                (ui-fn {:form-id form-id :props form})
                ($ :div "No UI defined for this form.")))))

       ;; The footer with form actions
       (when form
         ($ :div {:className "flex-shrink-0 border-t border-gray-200 bg-white px-6 py-4"}
            ($ form-error {:error (:error form)})
            ($ :div {:className "flex justify-end gap-3"}
               ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick handle-cancel} "Cancel")

               ;; "Back" button for wizards
               (when (and is-wizard? (not= (first (:steps form)) (:current-step form)))
                 ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer", :type "button", :onClick (:prev-step! form)} "Back"))

               ;; "Next" or "Submit" button
               (if (and is-wizard? (not= (last (:steps form)) (:current-step form)))
                 ($ :button {:type "button", :disabled (not (:valid? form)), :onClick (:next-step! form)
                             :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium "
                                             (if (not (:valid? form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    "Next")
                 ($ :button {:type "button", :disabled (or (not (:valid? form)) (:submitting? form) (:error form)), :onClick (:submit! form)
                             :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                             (if (or (not (:valid? form)) (:submitting? form) (:error form)) "text-gray-400 bg-gray-300 cursor-not-allowed" "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    (when (:submitting? form) ($ common/spinner {:size :medium}))
                    (:submit-text modal-data "Submit")))))))))

(defui global-modal-component []
  (let [modal-state (state/use-sub [:ui :modal])
        {:keys [active data]} modal-state
        form-id (when active (:form-id data))

        ;; Get current form state to determine current step
        form-state (state/use-sub [:forms form-id])
        current-step (:current-step form-state)

        ;; Get dynamic title based on current step
        form-spec (when form-id (get @form-specs form-id))
        current-step-spec (when current-step (get form-spec current-step))
        modal-props (:modal-props current-step-spec)
        dynamic-title (when modal-props
                        (if (fn? modal-props)
                          (:title (modal-props form-state))
                          (:title modal-props)))

        ;; Use dynamic title if available, otherwise fall back to data title
        title (or dynamic-title (:title data))

        handle-cancel (fn []
                        (when form-id (state/dispatch [:form/clear form-id]))
                        (state/dispatch [:modal/hide]))

        handle-keydown (fn [e] (when (= (.-key e) "Escape") (.preventDefault e) (handle-cancel)))]

    (uix/use-effect (fn [] (when active (.addEventListener js/document "keydown" handle-keydown) #(.removeEventListener js/document "keydown" handle-keydown))) [active])

    (when active
      (createPortal
       ($ :div {:className "fixed inset-0 flex items-center justify-center z-50", :style {:backgroundColor "rgba(0, 0, 0, 0.5)"}, :onClick handle-cancel}
          ($ :div {:className "bg-white rounded-lg shadow-xl w-full max-w-5xl overflow-hidden mx-4 my-8 flex flex-col max-h-screen", :role "dialog", :aria-modal "true", :onClick #(.stopPropagation %)}
             ;; Header with dynamic title
             ($ :div {:className "flex-shrink-0 p-4 border-b border-gray-200 flex justify-between items-center bg-white"}
                ($ :h3 {:className "text-lg font-medium text-gray-800"} title)
                ($ :button {:className "text-gray-400 hover:text-gray-600 text-xl font-bold cursor-pointer", :onClick handle-cancel} "×"))

             ;; Conditionally render the new content component
             (when form-id
               ($ ModalFormContent {:form-id form-id :modal-data data}))

             ;; Wrap component in proper scrollable container
             (when (:component data)
               ($ :div {:className "flex-1 min-h-0 overflow-y-auto"}
                  (:component data)))))
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
   Returns a map {:valid? boolean :errors {nested-error-map}}
   
   The form-state contains both field data and metadata. We need to extract
   only the field data for validation by excluding known metadata keys."
  [form-state validators]
  (let [metadata-keys #{:field-errors :valid? :submitting? :error :current-step :steps :set-field! :next-step! :prev-step! :submit!}
        field-data (apply dissoc form-state metadata-keys)]
    (reduce-kv
     (fn [acc path validator-fns]
       (let [value (s/select-one path field-data)
             first-error (some #(% value) validator-fns)]
         (if first-error
           (-> acc
               (assoc :valid? false)
               (update :errors #(s/setval path first-error %)))
           acc)))
     {:valid? true, :errors {}}
     validators)))

(state/reg-event :form/update-field
                 (fn [db form-id field-path value]
                   (let [form-state (get-in db [:forms form-id])
                         form-spec (@form-specs form-id)
                         current-step-key (:current-step form-state)
                         step-spec (get form-spec current-step-key form-spec)
                         all-validators (:validators step-spec)

                         ;; Update the field value in the form state
                         updated-form-state (if (vector? field-path)
                                              (assoc-in form-state field-path value)
                                              (assoc form-state field-path value))

                         ;; Validate the updated form state
                         validation-result (validate-form-fields updated-form-state all-validators)]

                     ;; Return the updated form state with new validation results
                     [:forms form-id
                      (s/terminal-val
                       (assoc updated-form-state
                              :field-errors (:errors validation-result)
                              :valid? (:valid? validation-result)
                              :error nil))])))

(state/reg-event :form/validate
                 (fn [db form-id]
                   (let [form-state (get-in db [:forms form-id])
                         form-spec (@form-specs form-id)
                         current-step-key (:current-step form-state)
                         step-spec (get form-spec current-step-key form-spec)
                         {:keys [valid? errors]} (validate-form-fields form-state (:validators step-spec))]
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
                           (let [next-step-spec (get form-spec next-step)
                                 initial-fields-fn (:initial-fields next-step-spec)
                                 ;; Call the initial-fields function with current form state
                                 ;; This allows each step to add new fields while preserving existing ones
                                 updated-form-state (if (fn? initial-fields-fn)
                                                      (initial-fields-fn form-state)
                                                      form-state)
                                 ;; Validate the updated form state for the new step
                                 validators (:validators next-step-spec)
                                 {:keys [valid? errors]} (validate-form-fields updated-form-state validators)]

                             ;; Update the entire form state with the new step data
                             [:forms form-id (s/terminal-val
                                              (assoc updated-form-state
                                                     :current-step next-step
                                                     :field-errors errors
                                                     :valid? valid?))])))))))

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
                         {:keys [valid? errors]} (validate-form-fields form-state (:validators step-spec))]

                     (if-not valid?
                       (state/dispatch [:db/set-value
                                        [:forms form-id]
                                        (assoc form-state :valid? false :field-errors errors)])
                       (let [on-submit-handler (:on-submit form-spec)
                             form-state-with-id (assoc form-state :form-id form-id)]
                         (cond
                           ;; New declarative path
                           (map? on-submit-handler)
                           (let [{:keys [event on-success-invalidate on-success on-error]} on-submit-handler
                                 sente-event (event db form-state-with-id)]
                             (state/dispatch [:db/set-value [:forms form-id] (assoc form-state :submitting? true :error nil)])
                             (sente/request!
                              sente-event
                              15000
                              (fn [reply]
                                (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
                                ;; Check for nested error structure: {:success true, :data {:status :error, :error "..."}}
                                (let [has-nested-error? (and (:success reply)
                                                             (= :error (get-in reply [:data :status]))
                                                             (get-in reply [:data :error]))
                                      actual-error (if has-nested-error?
                                                     (get-in reply [:data :error])
                                                     (:error reply))
                                      is-success? (and (:success reply) (not has-nested-error?))]
                                  (if is-success?
                                    (do
                                      (state/dispatch [:modal/hide])
                                      (when on-success-invalidate
                                        (state/dispatch [:query/invalidate (on-success-invalidate db form-state-with-id reply)]))
                                      (when on-success (on-success db form-state-with-id reply))
                                      (state/dispatch [:form/clear form-id]))
                                    (do
                                      (state/dispatch [:db/set-value [:forms form-id :error] actual-error])
                                      (when on-error (on-error db form-state-with-id actual-error))))))))

                           ;; Fallback to original function-based handler for complex cases
                           (fn? on-submit-handler)
                           (do
                             (state/dispatch [:db/set-value [:forms form-id] (assoc form-state :submitting? true :error nil)])
                             (on-submit-handler db form-state-with-id))))))
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

             ;; Create initial form state with metadata
             initial-form-state (merge initial-fields
                                       {:steps (:steps form-spec)
                                        :current-step initial-step})

             ;; Validate the initial form state
             {:keys [valid? errors]} (validate-form-fields initial-form-state validators)
             modal-data (let [mp (get-in form-spec [initial-step :modal-props] {})]
                          (if (fn? mp)
                            (mp props)
                            mp))

             ;; 1. Construct the full state for the form - now it's just a single flat map
             form-state (assoc initial-form-state
                               :field-errors errors
                               :valid? valid?
                               :submitting? false
                               :error nil)

             ;; 2. Construct the full state for the modal
             modal-state {:active form-id
                          :data (assoc modal-data :form-id form-id)
                          :form {:submitting? false
                                 :error nil}}]

         ;; 3. Return a single Specter path to update both parts of the DB atomically
         (s/multi-path
          [:forms form-id (s/terminal-val form-state)]
          [:ui :modal (s/terminal-val modal-state)]))))))

(state/reg-event :modal/show
                 (fn [db modal-type modal-data]
                   [:ui :modal (s/terminal-val {:active modal-type
                                                :data modal-data
                                                :form {:submitting? false
                                                       :error nil}})]))

(state/reg-event :modal/hide
                 (fn [db]
                   [:ui :modal (s/terminal-val {:active nil
                                                :data {}
                                                :form {:submitting? false
                                                       :error nil}})]))


