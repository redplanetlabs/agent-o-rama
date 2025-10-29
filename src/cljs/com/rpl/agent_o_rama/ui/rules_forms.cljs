(ns com.rpl.agent-o-rama.ui.rules-forms
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.filter-builder :as filter-builder]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.selectors :as selectors]
   [clojure.string :as str]
   ["use-debounce" :refer [useDebounce]]))

(defn action-friendly-name
  "Returns a user-friendly display name for an action builder ID."
  [action-id]
  (case action-id
    "aor/eval" "Online evaluation"
    "aor/add-to-dataset" "Add to dataset"
    "aor/webhook" "Webhook"
    action-id))

(defn compute-start-time-millis
  "Converts nested start-time state to epoch milliseconds."
  [{:keys [mode date relative-value relative-unit]}]
  (case mode
    :from-start 0

    :absolute
    (if (str/blank? date)
      (.now js/Date)
      (.parse js/Date date))

    :relative
    (let [now (.now js/Date)
          millis-per-unit {:minutes (* 60 1000)
                           :hours (* 60 60 1000)
                           :days (* 24 60 60 1000)
                           :weeks (* 7 24 60 60 1000)}
          unit-millis (get millis-per-unit relative-unit (* 60 1000))]
      (- now (* (or relative-value 0) unit-millis)))

    (.now js/Date)))

(defui StartTimeField
  [{:keys [form-id]}]
  (let [mode-field (forms/use-form-field form-id [:start-time :mode])
        date-field (forms/use-form-field form-id [:start-time :date])
        relative-value-field (forms/use-form-field
                              form-id
                              [:start-time :relative-value])
        relative-unit-field (forms/use-form-field
                             form-id
                             [:start-time :relative-unit])

        input-classes "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
        radio-label-classes "flex items-center space-x-2 cursor-pointer"]

    ($ :div.space-y-3
       ($ :label.block.text-sm.font-medium.text-gray-700 "Start Time")

       ($ :div.space-y-2
          ($ :label {:className radio-label-classes}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :value "from-start"
                        :checked (= (:value mode-field) :from-start)
                        :onChange #((:on-change mode-field) :from-start)})
             ($ :span.text-sm "From start"))

          ($ :label {:className radio-label-classes}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :value "absolute"
                        :checked (= (:value mode-field) :absolute)
                        :onChange #((:on-change mode-field) :absolute)})
             ($ :span.text-sm "Specific date/time"))

          ($ :label {:className radio-label-classes}
             ($ :input {:type "radio"
                        :name "time-mode"
                        :value "relative"
                        :checked (= (:value mode-field) :relative)
                        :onChange #((:on-change mode-field) :relative)})
             ($ :span.text-sm "Relative time ago")))

       (when (= (:value mode-field) :absolute)
         ($ :input {:type "datetime-local"
                    :className input-classes
                    :value (or (:value date-field) "")
                    :onChange #((:on-change date-field)
                                (.. % -target -value))}))

       (when (= (:value mode-field) :relative)
         ($ :div.flex.gap-2.items-center
            ($ :input {:type "number"
                       :min "1"
                       :maxLength "8"
                       :className "w-20 text-right p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                       :placeholder "5"
                       :value (or (:value relative-value-field) "")
                       :onChange #(let [parsed (js/parseInt
                                                (.. % -target -value))]
                                    ((:on-change relative-value-field)
                                     (if (js/isNaN parsed) nil parsed)))})
            ($ :select {:className "p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                        :value (name (:value relative-unit-field))
                        :onChange #((:on-change relative-unit-field)
                                    (keyword (.. % -target -value)))}
               ($ :option {:value "minutes"} "minutes ago")
               ($ :option {:value "hours"} "hours ago")
               ($ :option {:value "days"} "days ago")
               ($ :option {:value "weeks"} "weeks ago"))))

       ($ :div.mt-1.h-5))))

(defui DatasetCombobox
  "Autocomplete combobox for selecting a dataset by ID.

  Props:
  - :module-id - The module ID to fetch datasets from
  - :value - Current dataset ID value
  - :on-change - Callback when selection changes
  - :error - Error message to display
  - :required? - Whether field is required"
  [{:keys [module-id value on-change error required?]}]
  (let [[search-term set-search-term!] (uix/use-state "")
        [debounced-search] (useDebounce search-term 300)
        [is-open? set-open!] (uix/use-state false)
        [highlighted-idx set-highlighted-idx!] (uix/use-state 0)
        input-ref (uix/use-ref nil)

        ;; Fetch datasets with search filter
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:dataset-selector module-id debounced-search]
          :sente-event [:datasets/get-all
                        {:module-id module-id
                         :filters {:search-string debounced-search}}]
          :enabled? is-open?
          :refetch-on-mount true})

        datasets (or (:datasets data) [])

        ;; Find the currently selected dataset name
        selected-dataset (when value
                           (first (filter #(= (str (:dataset-id %)) value) datasets)))
        display-value (or (:name selected-dataset) value "")

        input-classes (str "w-full p-2 border rounded-md text-sm transition-colors "
                           (if error
                             "border-red-300 focus:ring-red-500 focus:border-red-500"
                             "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))

        ;; Event handlers
        handle-input-change (fn [e]
                              (let [v (.. e -target -value)]
                                (set-search-term! v)
                                (set-open! true)
                                (set-highlighted-idx! 0)))

        handle-select (fn [dataset]
                        (on-change (str (:dataset-id dataset)))
                        (set-search-term! (:name dataset))
                        (set-open! false))

        handle-input-focus (fn []
                             (set-open! true)
                             (when (str/blank? search-term)
                               (set-search-term! "")))

        handle-input-blur (fn []
                           ;; Delay to allow click on dropdown item
                            (js/setTimeout #(set-open! false) 200))

        handle-keydown (fn [e]
                         (when is-open?
                           (case (.-key e)
                             "ArrowDown" (do (.preventDefault e)
                                             (set-highlighted-idx!
                                              #(min (dec (count datasets)) (inc %))))
                             "ArrowUp" (do (.preventDefault e)
                                           (set-highlighted-idx!
                                            #(max 0 (dec %))))
                             "Enter" (do (.preventDefault e)
                                         (when (< highlighted-idx (count datasets))
                                           (handle-select (nth datasets highlighted-idx))))
                             "Escape" (do (.preventDefault e)
                                          (set-open! false))
                             nil)))]

    ;; Reset search term when value changes externally
    (uix/use-effect
     (fn []
       (when (and value (not= search-term display-value))
         (set-search-term! display-value))
       js/undefined)
     [value display-value search-term])

    ;; Refetch datasets when dropdown opens
    (uix/use-effect
     (fn []
       (when is-open?
         (refetch))
       js/undefined)
     [is-open? refetch])

    ($ :div.relative
       ($ :div.space-y-1
          ($ :label.block.text-sm.font-medium.text-gray-700
             "Dataset ID"
             (when required? ($ :span.text-red-500.ml-1 "*")))

          ($ :input {:ref input-ref
                     :type "text"
                     :className input-classes
                     :value search-term
                     :placeholder "Type to search datasets..."
                     :onChange handle-input-change
                     :onFocus handle-input-focus
                     :onBlur handle-input-blur
                     :onKeyDown handle-keydown})

          (if error
            ($ :p.text-sm.text-red-600.mt-1 error)
            ($ :div.mt-1.h-5)))

       ;; Dropdown list
       (when is-open?
         ($ :div.absolute.z-50.w-full.mt-1.bg-white.border.border-gray-300.rounded-md.shadow-lg.max-h-60.overflow-y-auto
            (if loading?
              ($ :div.p-4.text-center.text-gray-500.flex.items-center.justify-center
                 ($ common/spinner {:size :medium})
                 ($ :span.ml-2 "Loading datasets..."))

              (if (empty? datasets)
                ($ :div.p-4.text-center.text-gray-500
                   "No datasets found")

                (for [[idx dataset] (map-indexed vector datasets)]
                  ($ :div {:key (str (:dataset-id dataset))
                           :className (str "p-3 cursor-pointer hover:bg-blue-50 "
                                           (when (= idx highlighted-idx) "bg-blue-100"))
                           :onMouseEnter #(set-highlighted-idx! idx)
                           :onClick #(handle-select dataset)}
                     ($ :div.font-medium.text-sm (:name dataset))
                     ($ :div.text-xs.text-gray-500 (str (:dataset-id dataset))))))))))))

(defui ParamField
  [{:keys [form-id param-name param-info action-name module-id data-id]}]
  (let [field-path [:action-params param-name]
        param-field (forms/use-form-field form-id field-path)
        required? (nil? (:default param-info))
        description (:description param-info)
        show-description-below? (and (= action-name "aor/eval") description)
        input-classes "w-full p-2 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"]

    ;; Use DatasetCombobox for datasetId parameter
    (if (= param-name "datasetId")
      ($ DatasetCombobox {:module-id module-id
                          :value (:value param-field)
                          :on-change (:on-change param-field)
                          :error (:error param-field)
                          :required? required?})

      ;; Default text input for all other parameters
      ($ :div.space-y-1
         ($ :div.flex.items-center.gap-2
            ($ :label.text-sm.font-medium.text-gray-700
               param-name
               (when required? ($ :span.text-red-500.ml-1 "*")))
            (when (and description (not show-description-below?))
              ($ common/InfoTooltip {:content description
                                     :html? true})))

         ($ :input
            (cond-> {:type "text"
                     :className input-classes
                     :value (or (:value param-field) "")
                     :placeholder (or (:default param-info) "")
                     :onChange #((:on-change param-field) (.. % -target -value))}
              data-id (assoc :data-id data-id)))

         (when show-description-below?
           ($ :p.text-xs.text-gray-500.mt-1 description))

         (if (:error param-field)
           ($ :p.text-sm.text-red-600.mt-1 (:error param-field))
           ($ :div.mt-1.h-5))))))

(defui EvaluatorParamField
  [{:keys [form-id param-name module-id]}]
  (let [field (forms/use-form-field form-id [:action-params param-name])]
    ($ :div
       ($ :label.block.text-sm.font-medium.text-gray-700.mb-1
          "Evaluator"
          ($ :span.text-red-500.ml-1 "*"))
       ($ selectors/EvaluatorSelector
          {:module-id module-id
           :value (:value field)
           :on-change (:on-change field)
           :error (:error field)
           :allowed-types #{:regular}
           :placeholder "Search for an evaluator..."}))))

(defui ActionParamsForm
  [{:keys [form-id action-builders module-id]}]
  (let [action-name-field (forms/use-form-field form-id :action-name)
        action-name (:value action-name-field)
        action-info (get action-builders action-name)
        params (get-in action-info [:options :params])]

    (when (seq params)
      ($ :div.p-4.bg-gray-50.rounded-md.space-y-3
         ($ :div.text-sm.font-medium.text-gray-700 "Action Parameters")
         (for [[param-name param-info] params]
           (if (and (= action-name "aor/eval") (= param-name "name"))
             ;; Use the new EvaluatorParamField for the evaluator 'name' parameter
             ($ EvaluatorParamField {:key param-name
                                     :form-id form-id
                                     :param-name param-name
                                     :module-id module-id})
             ;; Use default ParamField for all other parameters
             ($ ParamField {:key param-name
                            :form-id form-id
                            :param-name param-name
                            :param-info param-info
                            :action-name action-name
                            :module-id module-id
                            :data-id (str "param-" param-name)})))))))

(defui StatusFilterField
  [{:keys [form-id]}]
  (let [status-filter-field (forms/use-form-field form-id :status-filter)
        select-classes "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"]

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          "Status Filter"
          ($ :span.text-red-500.ml-1 "*"))
       ($ :select {:className select-classes
                   :value (name (:value status-filter-field))
                   :onChange #((:on-change status-filter-field) (keyword (.. % -target -value)))}
          ($ :option {:value "success"} "Success")
          ($ :option {:value "all"} "All")
          ($ :option {:value "failure"} "Failure"))
       (if (:error status-filter-field)
         ($ :p.text-sm.text-red-600.mt-1 (:error status-filter-field))
         ($ :div.mt-1.h-5)))))

(defui SamplingRateField
  [{:keys [form-id]}]
  (let [sampling-rate-field (forms/use-form-field form-id :sampling-rate)
        input-classes (str "w-full p-3 border rounded-md text-sm transition-colors "
                           (if (:error sampling-rate-field)
                             "border-red-300 focus:ring-red-500 focus:border-red-500"
                             "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))

        handle-change (fn [e]
                        (let [raw-value (.. e -target -value)
                              parsed (js/parseFloat raw-value)]
                          (if (or (js/isNaN parsed) (str/blank? raw-value))
                            ((:on-change sampling-rate-field) nil)
                            ;; Clamp value between 0.0 and 1.0
                            (let [clamped (max 0.0 (min 1.0 parsed))]
                              ((:on-change sampling-rate-field) clamped)))))]

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          "Sampling Rate"
          ($ :span.text-red-500.ml-1 "*"))
       ($ :input {:type "number"
                  :className input-classes
                  :value (or (:value sampling-rate-field) "")
                  :min "0.0"
                  :max "1.0"
                  :step "0.1"
                  :placeholder "1.0"
                  :onChange handle-change})
       (if (:error sampling-rate-field)
         ($ :p.text-sm.text-red-600.mt-1 (:error sampling-rate-field))
         ($ :div.mt-1.h-5)))))

(defui RuleScopeEditor [{:keys [form-id module-id agent-name]}]
  (let [node-name-field (forms/use-form-field form-id :node-name)
        scope-type (if (nil? (:value node-name-field)) :agent :node)]
    ($ :div.space-y-3
       ($ :label.block.text-sm.font-medium.text-gray-700 "Scope")
       ($ selectors/ScopeSelector
          {:value scope-type
           :on-change #(state/dispatch [:form/set-rule-scope-type form-id %])})
       (when (= scope-type :node)
         ($ selectors/NodeSelectorDropdown
            {:module-id module-id
             :agent-name agent-name
             :value (:value node-name-field)
             :on-change (:on-change node-name-field)
             :error (:error node-name-field)
             :disabled? (not agent-name)
             :data-testid "node-name-dropdown"})))))

(defui ActionSelector
  [{:keys [module-id agent-name form-id]}]
  (let [action-name-field (forms/use-form-field form-id :action-name)
        [action-builders set-action-builders!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state false)
        select-classes "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"]

    (uix/use-effect
     (fn []
       (set-loading! true)
       (sente/request!
        [:analytics/all-action-builders
         {:module-id module-id
          :agent-name agent-name}]
        5000
        (fn [reply]
          (set-loading! false)
          (if (:success reply)
            (set-action-builders! (:data reply))
            (.error
             js/console
             "Failed to fetch action builders:"
             (:error reply)))))
       js/undefined)
     [])

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          "Action"
          ($ :span.text-red-500.ml-1 "*"))
       ($ :select {:className select-classes
                   :value (or (:value action-name-field) "")
                   :disabled loading?
                   :onChange #((:on-change action-name-field)
                               (.. % -target -value))
                   :data-id "action-selector"}
          ($ :option {:value ""} (if loading? "Loading..." "Select an action"))
          (when action-builders
            (for [[action-id _action-info] action-builders]
              ($ :option {:key action-id :value action-id}
                 (action-friendly-name action-id)))))
       (when (:error action-name-field)
         ($ :p.text-sm.text-red-600.mt-1 (:error action-name-field)))

       (when (and (:value action-name-field) action-builders)
         ($ ActionParamsForm {:form-id form-id
                              :action-builders action-builders
                              :module-id module-id})))))

(defui AddRuleForm
  [{:keys [form-id module-id agent-name]}]
  (let [rule-name-field (forms/use-form-field form-id :rule-name)]

    ($ forms/form
       ($ forms/form-field
          {:label "Rule Name"
           :value (:value rule-name-field)
           :on-change (:on-change rule-name-field)
           :error (:error rule-name-field)
           :required? true
           :placeholder "my-rule"
           :data-id "rule-name"})

       ($ RuleScopeEditor {:form-id form-id
                           :module-id module-id
                           :agent-name agent-name})

       ($ StatusFilterField {:form-id form-id})

       ($ SamplingRateField {:form-id form-id})

       ($ StartTimeField {:form-id form-id})

       ($ ActionSelector {:form-id form-id
                          :module-id module-id
                          :agent-name agent-name})

       ($ filter-builder/FilterBuilder {:form-id form-id
                                        :module-id module-id
                                        :agent-name agent-name}))))

(forms/reg-form
 :add-rule
 {:steps [:main]

  :main
  {:initial-fields (fn [props]
                     (merge {:rule-name ""
                             :node-name nil
                             :status-filter :success
                             :sampling-rate 1.0
                             :start-time {:mode :from-start
                                          :date ""
                                          :relative-value 1
                                          :relative-unit :minutes}
                             :action-name ""
                             :action-params {}
                             :filter {:type :and :filters []}}
                            props))

   :validators {:rule-name [forms/required]
                :node-name [(fn [v form-state]
                              (when (not (nil? v))
                                (forms/required v)))]
                :status-filter [forms/required]
                :sampling-rate [forms/required
                                (fn [v]
                                  (when (or (js/isNaN v) (nil? v))
                                    "Must be a valid number"))
                                (fn [v]
                                  (when (or (< v 0.0) (> v 1.0))
                                    "Must be between 0.0 and 1.0"))]
                :action-name [forms/required]
                [:action-params "name"] [(fn [v form-state]
                                           (when (= (:action-name form-state) "aor/eval")
                                             (forms/required v)))]
                :filter [(fn [filter-val]
                           (letfn [(validate-filter [f]
                                     (case (:type f)
                                       :feedback
                                       (cond
                                         (str/blank? (:rule-name f))
                                         "Feedback filter requires a rule name"
                                         (str/blank? (:feedback-key f))
                                         "Feedback filter requires a metric key"
                                         :else nil)

                                       :and
                                       (some validate-filter (:filters f))

                                       :or
                                       (some validate-filter (:filters f))

                                       :not
                                       (validate-filter (:filter f))

                                       nil))]
                             (some validate-filter (:filters filter-val))))]}

   :ui (fn [{:keys [form-id props]}]
         (let [{:keys [module-id agent-name]} props]
           ($ AddRuleForm {:form-id form-id
                           :module-id module-id
                           :agent-name agent-name})))

   :modal-props {:title "Add Rule"
                 :submit-text "Add Rule"}}

  :on-submit
  {:event (fn [_db form-state]
            (let [{:keys [module-id agent-name rule-name node-name status-filter
                          sampling-rate filter start-time
                          action-name action-params]} form-state
                  start-time-millis (compute-start-time-millis start-time)
                  action-params-cleaned (into {}
                                              #_(filter #(not (str/blank? (val %))))
                                              action-params)
                  rule-spec {:node-name node-name
                             :action-name action-name
                             :action-params action-params-cleaned
                             :filter filter
                             :sampling-rate sampling-rate
                             :start-time-millis start-time-millis
                             :status-filter status-filter}]
              [:analytics/add-rule
               {:module-id module-id
                :agent-name agent-name
                :rule-name rule-name
                :rule-spec rule-spec}]))
   :on-success (fn [db {:keys [module-id agent-name]} _reply]
                 (let [current-val (get-in db [:ui :rules :refetch-trigger module-id agent-name] 0)
                       new-val (inc current-val)]
                   (state/dispatch [:db/set-value [:ui :rules :refetch-trigger module-id agent-name] new-val])))}})

(defn show-add-rule-modal!
  [props]
  (state/dispatch [:modal/show-form :add-rule props]))
