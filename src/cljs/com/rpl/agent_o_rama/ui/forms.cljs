(ns com.rpl.agent-o-rama.ui.forms
  "Form utilities backed by re-frame app-db.

  Form state lives at [:forms form-id] in re-frame's app-db:
    {:fields  {field-key value ...}
     :errors  {field-key error-str ...}
     :meta    {:submitting? false :error nil :valid? true :step :main :steps [...]}}

  The declarative reg-form spec is unchanged; only the plumbing under it moves."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.re-frame :refer [use-subscribe]]
   [re-frame.core :as rf]
   [re-frame.query :as rfq]
  [com.rpl.agent-o-rama.ui.common :as common]
  [com.rpl.agent-o-rama.ui.rpc :as rpc]
  [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
  [clojure.string :as str]
  [com.rpl.specter :as s]
  ["react-dom" :refer [createPortal]]))

;; =============================================================================
;; FORM REGISTRY  (plain atom — populated at load time, read-only at runtime)
;; =============================================================================

(defonce form-specs (atom {}))

(defn reg-form [form-id spec]
  (swap! form-specs assoc form-id spec))

;; =============================================================================
;; HELPERS
;; =============================================================================

(defn- meta-keys []
  #{:field-errors :valid? :submitting? :error :current-step :steps
    :set-field! :next-step! :prev-step! :submit! :form-id})

(defn- field-data
  "Extract only user-supplied fields from a flat form-state map."
  [form-state]
  (apply dissoc form-state (meta-keys)))

(defn- validate-fields
  "Run validators over fields-map. Returns {:valid? bool :errors {}}."
  [fields validators]
  (reduce-kv
   (fn [acc path validator-fns]
     (let [value (s/select-one path fields)
           err   (some #(% value fields) validator-fns)]
       (if err
         (-> acc (assoc :valid? false) (update :errors #(s/setval path err %)))
         acc)))
   {:valid? true :errors {}}
   (or validators {})))

(defn- step-index
  "Index of `step` in `steps` vector, or -1 if missing. (Avoids JS .indexOf on clj->js keywords.)"
  [steps step]
  (or (first (keep-indexed (fn [i s] (when (= s step) i)) steps))
      -1))

;; =============================================================================
;; RE-FRAME EVENT/SUB REGISTRATION
;; =============================================================================

;; ---- subscriptions ----------------------------------------------------------

(rf/reg-sub ::form
  (fn [db [_ form-id]]
    (get-in db [:forms form-id])))

(rf/reg-sub ::field
  :<- [::forms-map]
  (fn [forms [_ form-id field-path]]
    (s/select-one (into [:forms form-id] (if (vector? field-path) field-path [field-path]))
                  {:forms forms})))

;; Helpers to avoid redundant reads
(rf/reg-sub ::forms-map
  (fn [db _] (:forms db {})))

;; ---- events -----------------------------------------------------------------

(rf/reg-event-db ::initialize
  (fn [db [_ form-id props]]
    (let [spec         (@form-specs form-id)
          initial-step (first (:steps spec))
          step-spec    (get spec initial-step)
          fields-fn    (:initial-fields step-spec)
          fields       (if (fn? fields-fn) (fields-fn props) (or fields-fn {}))
          validators   (:validators step-spec)
          {:keys [valid? errors]} (validate-fields fields validators)
          form-state   {:fields  fields
                        :errors  errors
                        :meta    {:submitting? false :error nil :valid? valid?
                                  :steps       (:steps spec)
                                  :step        initial-step}}]
      (assoc-in db [:forms form-id] form-state))))

(rf/reg-event-db ::set-field
  (fn [db [_ form-id field-path value]]
    (let [path      (if (vector? field-path) field-path [field-path])
          spec      (@form-specs form-id)
          form      (get-in db [:forms form-id])
          step      (get-in form [:meta :step])
          step-spec (get spec step spec)
          validators (:validators step-spec)
          new-fields (assoc-in (:fields form) path value)
          {:keys [valid? errors]} (validate-fields new-fields validators)]
      (-> db
          (assoc-in (into [:forms form-id :fields] path) value)
          (assoc-in [:forms form-id :errors] errors)
          (assoc-in [:forms form-id :meta :valid?] valid?)
          (assoc-in [:forms form-id :meta :error] nil)))))

(rf/reg-event-db ::clear
  (fn [db [_ form-id]]
    (update db :forms dissoc form-id)))

;; Used by experiment form nested editors (targets, evaluators, etc.)
(rf/reg-event-db
 :form/update-field
 (fn [db [_ form-id path value]]
   (let [field-path (into [:forms form-id :fields]
                          (if (vector? path) path [path]))]
     (assoc-in db field-path value))))

(rf/reg-event-db ::set-error
  (fn [db [_ form-id msg]]
    (-> db
        (assoc-in [:forms form-id :meta :error] msg)
        (assoc-in [:forms form-id :meta :submitting?] false))))

(rf/reg-event-db ::set-submitting
  (fn [db [_ form-id v]]
    (assoc-in db [:forms form-id :meta :submitting?] v)))

(rf/reg-event-fx ::next-step
  (fn [{:keys [db]} [_ form-id]]
    (let [form      (get-in db [:forms form-id])
          spec      (@form-specs form-id)
          {:keys [step steps valid?]} (:meta form)]
      (when valid?
        (let [idx       (step-index steps step)
              next-step (get steps (inc idx))]
          (when next-step
            (let [next-spec  (get spec next-step)
                  fields-fn  (:initial-fields next-spec)
                  new-fields (if (fn? fields-fn)
                               (fields-fn (merge (:fields form) (:meta form)))
                               (:fields form))
                  {:keys [valid? errors]} (validate-fields new-fields (:validators next-spec))]
              {:db (-> db
                       (assoc-in [:forms form-id :fields] new-fields)
                       (assoc-in [:forms form-id :errors] errors)
                       (assoc-in [:forms form-id :meta :valid?] valid?)
                       (assoc-in [:forms form-id :meta :step] next-step))})))))))

(rf/reg-event-db ::prev-step
  (fn [db [_ form-id]]
    (let [form   (get-in db [:forms form-id])
          {:keys [step steps]} (:meta form)
          idx    (step-index steps step)
          prev   (get steps (dec idx))]
      (when prev
        (assoc-in db [:forms form-id :meta :step] prev)))))

(rf/reg-event-fx ::submit
  (fn [{:keys [db]} [_ form-id]]
    (let [form       (get-in db [:forms form-id])
          spec       (@form-specs form-id)
          step       (get-in form [:meta :step])
          step-spec  (get spec step spec)
          fields     (:fields form)
          {:keys [valid? errors]} (validate-fields fields (:validators step-spec))
          ;; `modal/show-form` stores opener props on [:ui :modal :data] (e.g. :example-ids for
          ;; bulk tag modals). Merge them into submit payload — they are not always present on
          ;; [:forms form-id :fields] after field updates.
          modal-data (get-in db [:ui :modal :data])
          modal-props (select-keys modal-data [:example-ids :selected-examples :module-id
                                               :dataset-id :snapshot-name :editing?
                                               :agent-name :invoke-id])]
      (if-not valid?
        {:db (-> db
                 (assoc-in [:forms form-id :errors] errors)
                 (assoc-in [:forms form-id :meta :valid?] false))}
        (let [handler       (:on-submit spec)
              ;; Flatten fields + meta for backward-compat with mutation fns
              form-state    (merge fields modal-props (:meta form) {:form-id form-id})
              new-db        (-> db
                                (assoc-in [:forms form-id :meta :submitting?] true)
                                (assoc-in [:forms form-id :meta :error] nil))]
          (cond
            ;; --- declarative map with :mutation ---
            (map? handler)
            (let [{:keys [mutation on-success-invalidate on-success on-error
                           rfq-invalidate-tags]} handler]
              {:db   new-db
               :dispatch [::do-submit form-id handler form-state]})

            ;; --- legacy function ---
            (fn? handler)
            {:db   new-db
             :dispatch [::do-submit-fn form-id handler form-state]}))))))

;; Actual side-effecting submit (outside the pure event-db)
(rf/reg-event-fx ::do-submit
  (fn [{:keys [db]} [_ form-id handler form-state]]
    (let [{:keys [mutation on-success-invalidate on-success on-error
                  rfq-invalidate-tags]} handler

          on-ok
          (fn [reply]
            (rf/dispatch [::submit-success form-id handler form-state reply]))

          on-fail
          (fn [err]
            (rf/dispatch [::submit-failure form-id on-error form-state err]))]

      (when mutation
        (let [mut-result (mutation db form-state)]
          (when mut-result
            (let [[rpc-id params] mut-result]
              (-> (rpc/call rpc-id params)
                  (.then (fn [data] (on-ok {:success true :data data})))
                  (.catch on-fail))))))
      nil)))

(rf/reg-event-fx ::do-submit-fn
  (fn [{:keys [db]} [_ form-id handler form-state]]
    (handler db form-state)
    nil))


(rf/reg-event-fx ::submit-success
  (fn [{:keys [db]} [_ form-id handler form-state reply]]
    (let [{:keys [on-success-invalidate rfq-invalidate-tags on-success]} handler
          ;; `rpc/call` unwraps HTTP {:success true :data x} → x (no :success key on reply)
          wrapped?        (contains? reply :success)
          has-nested-err? (if wrapped?
                            (and (= :error (get-in reply [:data :status]))
                                 (some? (get-in reply [:data :error])))
                            (and (map? reply)
                                 (or (= :error (:status reply)) (some? (:error reply)))))
          actual-err      (if wrapped?
                            (when has-nested-err? (get-in reply [:data :error]))
                            (when has-nested-err? (or (:error reply) (str (:status reply)))))
          success?        (if wrapped?
                            (and (:success reply) (not has-nested-err?))
                            (not has-nested-err?))
          close-modal-db  (fn [d]
                            (-> d
                                (assoc-in [:ui :modal :active] nil)
                                (assoc-in [:ui :modal :data] {})
                                (update :forms dissoc form-id)))]
      (if success?
        (cond-> {:db (close-modal-db (assoc-in db [:forms form-id :meta :submitting?] false))
                 :fx (cond-> []
                        on-success-invalidate
                        (conj [:dispatch-fn
                               #(when-let [inv-map (on-success-invalidate db form-state reply)]
                                  (rf/dispatch [:query/invalidate-bridge inv-map]))])
                        rfq-invalidate-tags
                        (into (let [tags (rfq-invalidate-tags db form-state reply)]
                                (when (seq tags)
                                  [[:dispatch [:re-frame.query/invalidate-tags tags]]]))))}
          on-success (update :fx conj [:dispatch-fn #(on-success db form-state reply)]))
        {:db (-> db
                 (assoc-in [:forms form-id :meta :submitting?] false)
                 (assoc-in [:forms form-id :meta :error] actual-err))}))))

(rf/reg-event-fx ::submit-failure
  (fn [{:keys [db]} [_ form-id on-error form-state err]]
    (let [msg (if (map? err) (or (:error err) (str err)) (str err))]
      (cond-> {:db (-> db
                       (assoc-in [:forms form-id :meta :submitting?] false)
                       (assoc-in [:forms form-id :meta :error] msg))}
        on-error (assoc :fx [[:dispatch-fn #(on-error db form-state msg)]])))))

;; Re-frame effect to call an arbitrary fn (for callbacks that need to escape into js)
(rf/reg-fx :dispatch-fn
  (fn [f] (f)))

;; Shim: legacy custom-state modal/hide still needs to work until modal is ported
(rf/reg-event-fx :modal/hide
  (fn [{:keys [db]} _]
    {:db (-> db
             (assoc-in [:ui :modal :active] nil)
             (assoc-in [:ui :modal :data] {}))}))

;; =============================================================================
;; HOOKS
;; =============================================================================

(defhook use-form
  "Returns merged form state + action fns. Backward-compat with old use-form callers."
  [form-id]
  (let [form (use-subscribe [::form form-id])
        fields (:fields form {})
        meta   (:meta form {})
        errors (:errors form {})]
    (merge fields meta
           {:field-errors errors
            :valid?       (:valid? meta true)
            :submitting?  (:submitting? meta false)
            :error        (:error meta)
            :current-step (:step meta)
            :steps        (:steps meta)
            :set-field!   (fn [path v] (rf/dispatch [::set-field form-id path v]))
            :next-step!   #(rf/dispatch [::next-step form-id])
            :prev-step!   #(rf/dispatch [::prev-step form-id])
            :submit!      #(rf/dispatch [::submit form-id])})))

(defhook use-form-field
  "Returns {:value :on-change :error} for a single field."
  [form-id field-key]
  (let [path   (if (vector? field-key) field-key [field-key])
        value  (use-subscribe [::form-field form-id path])
        error  (use-subscribe [::form-field-error form-id path])
        on-change (uix/use-callback
                   (fn [v] (rf/dispatch [::set-field form-id path v]))
                   [form-id path])]
    {:value value :on-change on-change :error error}))

(rf/reg-sub ::form-field
  (fn [db [_ form-id path]]
    (get-in db (into [:forms form-id :fields] path))))

(rf/reg-sub ::form-field-error
  (fn [db [_ form-id path]]
    (get-in db (into [:forms form-id :errors] path))))

;; Forms and modal chrome live in re-frame at [:forms ...] and [:ui :modal].

(rf/reg-event-fx :modal/show-form
  (fn [{:keys [db]} [_ form-id props]]
    (let [spec        (@form-specs form-id)
          init-step   (first (:steps spec))
          step-spec   (get spec init-step)
          fields-fn   (:initial-fields step-spec)
          fields      (if (fn? fields-fn) (fields-fn props) (or fields-fn {}))
          validators  (:validators step-spec)
          {:keys [valid? errors]} (validate-fields fields validators)
          modal-props (let [mp (get-in spec [init-step :modal-props] {})]
                        (if (fn? mp) (mp props) mp))
          form-state  {:fields  fields
                       :errors  errors
                       :meta    {:submitting? false :error nil :valid? valid?
                                 :steps       (:steps spec)
                                 :step        init-step}}]
      {:db (-> db
               (assoc-in [:forms form-id] form-state)
               (assoc-in [:ui :modal] {:active form-id
                                       :data   (assoc modal-props :form-id form-id)
                                       :form   {:submitting? false :error nil}}))})))

(rf/reg-event-fx :modal/show
  (fn [{:keys [db]} [_ modal-type modal-data]]
    {:db (assoc-in db [:ui :modal] {:active modal-type
                                     :data   modal-data
                                     :form   {:submitting? false :error nil}})}))

;; =============================================================================
;; VALIDATORS
;; =============================================================================

(defn required
  ([value]        (when (str/blank? value) "This field is required"))
  ([value _]      (when (str/blank? value) "This field is required")))

(defn min-length [n]
  (fn
    ([value]   (when (and (string? value) (< (count value) n)) (str "Must be at least " n " characters")))
    ([value _] (when (and (string? value) (< (count value) n)) (str "Must be at least " n " characters")))))

(defn max-length [n]
  (fn
    ([value]   (when (and (string? value) (> (count value) n)) (str "Must be no more than " n " characters")))
    ([value _] (when (and (string? value) (> (count value) n)) (str "Must be no more than " n " characters")))))

(defn valid-json
  ([value]
   (when-not (str/blank? value)
     (try (js/JSON.parse value) nil
          (catch js/Error e (str "Invalid JSON: " (.-message e))))))
  ([value _]
   (when-not (str/blank? value)
     (try (js/JSON.parse value) nil
          (catch js/Error e (str "Invalid JSON: " (.-message e)))))))

;; =============================================================================
;; REUSABLE FORM COMPONENTS (unchanged)
;; =============================================================================

(defui form-field
  [{:keys [label value on-change error required? placeholder class-name
           type rows data-id data-testid disabled]
    :or {type :text rows 3}}]
  (let [[field-id] (uix/use-state #(str "field-" (random-uuid)))
        aria-label (when (string? label) label)
        input-classes (str "w-full p-3 border rounded-md text-sm transition-colors "
                           (if disabled
                             "bg-gray-100 text-gray-500 cursor-not-allowed border-gray-200"
                             (if error
                               "border-red-300 focus:ring-red-500 focus:border-red-500"
                               "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                           (when class-name (str " " class-name)))]
    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700 {:htmlFor field-id}
          label
          (when required? ($ :span.text-red-500.ml-1 "*")))
       (case type
         :textarea
         ($ :textarea
            (cond-> {:id field-id :className input-classes
                     :value (or value "") :placeholder placeholder :rows rows
                     :onChange #(on-change (.. % -target -value))}
              aria-label (assoc :aria-label aria-label)
              data-id     (assoc :data-id data-id)
              data-testid (assoc :data-testid data-testid)
              disabled    (assoc :disabled true)))
         ($ :input
            (cond-> {:id field-id :type (name type) :className input-classes
                     :value (or value "") :placeholder placeholder
                     :onChange #(on-change (.. % -target -value))}
              aria-label (assoc :aria-label aria-label)
              data-id     (assoc :data-id data-id)
              data-testid (assoc :data-testid data-testid)
              disabled    (assoc :disabled true))))
       (if error
         ($ :p.text-sm.text-red-600.mt-1 error)
         ($ :div.mt-1.h-5)))))

(defui form-error [{:keys [error class-name]}]
  (when error
    ($ :div {:className (str "mt-4 p-3 bg-red-50 border border-red-200 rounded-md " class-name)}
       ($ :p.text-sm.text-red-700.whitespace-pre-wrap error))))

(defui form [{:keys [children]}]
  ($ :form.p-4 children))

;; =============================================================================
;; WIZARD / MODAL COMPONENTS
;; =============================================================================

(defui WizardForm [{:keys [form-id]}]
  (let [form      (use-form form-id)
        form-spec (@form-specs form-id)
        step      (:current-step form)
        ui-fn     (:ui (get form-spec step))]
    ($ :div.flex.flex-col.h-full
       ($ :div.flex-1.min-h-0.overflow-y-auto
          (if ui-fn
            (ui-fn {:form-id form-id :props form})
            ($ :div.p-8.text-center.text-gray-500 (str "No UI for step: " step)))))))

(defui ModalFormContent [{:keys [form-id modal-data]}]
  (let [form      (use-form form-id)
        form-spec (@form-specs form-id)
        wizard?   (seq (:steps form-spec))
        cancel!   (fn []
                    (rf/dispatch [::clear form-id])
                    (rf/dispatch [:modal/hide]))]
    ($ :<>
       ($ :div.flex-1.min-h-0.overflow-y-auto
          (if wizard?
            ($ WizardForm {:form-id form-id})
            (let [ui-fn (or (:ui form-spec) (get-in form-spec [:main :ui]))]
              (if ui-fn
                (ui-fn {:form-id form-id :props form})
                ($ :div "No UI defined.")))))
       (when form
         ($ :div.flex-shrink-0.border-t.border-gray-200.bg-white.px-6.py-4
            ($ form-error {:error (:error form)})
            ($ :div.flex.justify-end.gap-3
               ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer"
                            :type "button" :onClick cancel!} "Cancel")
               (when (and wizard? (not= (first (:steps form)) (:current-step form)))
                 ($ :button {:className "px-4 py-2 border border-gray-300 rounded-md text-sm font-medium cursor-pointer"
                              :type "button" :onClick (:prev-step! form)} "Back"))
               (if (and wizard? (not= (last (:steps form)) (:current-step form)))
                 ($ :button {:type "button" :disabled (not (:valid? form))
                              :onClick (:next-step! form)
                              :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium "
                                              (if (not (:valid? form)) "text-gray-400 bg-gray-300 cursor-not-allowed"
                                                  "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    "Next")
                 ($ :button {:type "button"
                              :disabled (or (not (:valid? form)) (:submitting? form) (:error form))
                              :onClick (:submit! form)
                              :data-id "form-submit"
                              :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                              (if (or (not (:valid? form)) (:submitting? form) (:error form))
                                                "text-gray-400 bg-gray-300 cursor-not-allowed"
                                                "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                    (when (:submitting? form) ($ common/spinner {:size :medium}))
                    (:submit-text modal-data "Submit")))))))))

(defui global-modal-component []
  (let [modal-state  (use-subscribe [::aor-rf/aor-global-modal])
        {:keys [active data]} modal-state
        form-id      (when active (:form-id data))
        form-state   (use-subscribe [::form form-id])
        step         (get-in form-state [:meta :step])
        form-spec    (when form-id (@form-specs form-id))
        step-spec    (when step (get form-spec step))
        modal-props  (:modal-props step-spec)
        dynamic-title (when modal-props
                        (if (fn? modal-props)
                          (:title (modal-props (merge (:fields form-state {})
                                                      (:meta form-state {}))))
                          (:title modal-props)))
        title        (or dynamic-title (:title data))
        handle-cancel (fn []
                        (when form-id (rf/dispatch [::clear form-id]))
                        (rf/dispatch [:modal/hide]))
        handle-keydown (fn [e]
                         (when (= (.-key e) "Escape")
                           (.preventDefault e)
                           (handle-cancel)))]

    (uix/use-effect
     (fn []
       (when active
         (.addEventListener js/document "keydown" handle-keydown)
         #(.removeEventListener js/document "keydown" handle-keydown)))
     [active handle-keydown])

    (when active
      (createPortal
       ($ :div {:className "fixed inset-0 flex items-center justify-center z-50"
                :style {:backgroundColor "rgba(0,0,0,0.5)"}
                :onClick handle-cancel}
          ($ :div {:className "bg-white rounded-lg shadow-xl w-full max-w-5xl overflow-hidden mx-4 my-8 flex flex-col max-h-screen"
                   :role "dialog" :aria-modal "true"
                   :onClick #(.stopPropagation %)}
             ($ :div.flex-shrink-0.p-4.border-b.border-gray-200.flex.justify-between.items-center.bg-white
                ($ :h3.text-lg.font-medium.text-gray-800 title)
                ($ :button.text-gray-400.hover:text-gray-600.text-xl.font-bold.cursor-pointer
                   {:onClick handle-cancel} "×"))
             (when form-id
               ($ ModalFormContent {:form-id form-id :modal-data data}))
             (when (:component data)
               ($ :div.flex-1.min-h-0.overflow-y-auto {:data-id "form-container"}
                  (:component data)))))
       (.-body js/document)))))

;; =============================================================================
;; IMPERATIVE HELPERS  — for fn-based :on-submit handlers
;; =============================================================================

(defn set-submitting! [form-id v]
  (rf/dispatch [::set-submitting form-id v]))

(defn set-error! [form-id msg]
  (rf/dispatch [::set-error form-id msg]))

(defn clear-form! [form-id]
  (rf/dispatch [::clear form-id]))

;; =============================================================================
;; CENTRALIZED FORM HOOK (legacy callers)
;; =============================================================================

(defhook use-centralized-form
  "Alias for use-form — for callers that used the old name."
  [form-id]
  (use-form form-id))
