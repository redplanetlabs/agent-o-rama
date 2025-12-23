(ns com.rpl.agent-o-rama.ui.human-feedback-queues
  (:require
   [uix.core :as uix :refer [defui $]]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [PencilIcon ChevronLeftIcon ChevronRightIcon XMarkIcon TrashIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [clojure.string :as str]))

;; =============================================================================
;; DUMMY DATA
;; =============================================================================

(def dummy-queue-info
  {:description "Queue for evaluating customer support responses"
   :rubrics [{:name "helpfulness"
              :description "How helpful was the response?"
              :metric {:__typename "HumanCategoryMetric"
                       :categories #{"Not helpful" "Somewhat helpful" "Very helpful"}}
              :required true}
             {:name "accuracy-score"
              :description "Accuracy rating from 0-100"
              :metric {:__typename "HumanNumericMetric"
                       :min 0
                       :max 100}
              :required true}
             {:name "tone"
              :description "Professional tone assessment (optional)"
              :metric {:__typename "HumanCategoryMetric"
                       :categories #{"Too casual" "Appropriate" "Too formal"}}
              :required false}]})

(def dummy-queue-items
  {:items [{:id "item-1"
            :comment "Customer asked about refund policy"
            :input {:query "What is your refund policy?"
                    :context "Customer purchased item 3 days ago"}
            :output {:response "You can return items within 30 days for a full refund with original receipt."
                     :confidence 0.95}}
           {:id "item-2"
            :comment "Technical support question"
            :input {:query "My app keeps crashing on startup"
                    :device "iPhone 12"
                    :os-version "iOS 16.3"}
            :output {:response "Try uninstalling and reinstalling the app. If that doesn't work, clear your cache."
                     :steps ["Uninstall app" "Restart device" "Reinstall app"]}}
           {:id "item-3"
            :comment "Billing inquiry"
            :input {:query "Why was I charged twice?"
                    :customer-id "cust_12345"}
            :output {:response "I see two charges on your account. One appears to be a pending authorization that will drop off in 3-5 business days."
                     :action-taken "Checked billing history"}}
           {:id "item-4"
            :comment "Product recommendation"
            :input {:query "Which laptop is best for video editing?"
                    :budget "$2000"
                    :use-case "4K video editing"}
            :output {:response "I'd recommend the MacBook Pro 16\" with M2 Pro chip. It has excellent performance for video editing and stays within your budget."
                     :alternatives ["Dell XPS 17" "Lenovo ThinkPad P1"]}}
           {:id "item-5"
            :comment "Account access issue"
            :input {:query "I can't log into my account"
                    :email "user@example.com"
                    :error-message "Invalid credentials"}
            :output {:response "I'll send a password reset link to your email. Please check your spam folder if you don't see it in a few minutes."
                     :action-taken "Initiated password reset"}}]
   :pagination-params nil})

;; =============================================================================
;; METRICS INDEX PAGE
;; =============================================================================

(defui metrics-index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))
         ;; Search state
        [search-term set-search-term] (useState "")
        [debounced-search] (useDebounce search-term 300)

        ;; Use paginated query
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:human-metrics module-id debounced-search]
          :sente-event [:human-feedback/get-metrics
                        {:module-id decoded-module-id
                         :filters (when-not (str/blank? debounced-search)
                                    {:search-string debounced-search})}]
          :page-size 20
          :enabled? (and module-id (boolean decoded-module-id))})

        handle-delete (uix/use-callback
                       (fn [metric-name]
                         (when (js/confirm (str "Delete metric '" metric-name "'?"))
                           (sente/request!
                            [:human-feedback/delete-metric {:module-id decoded-module-id
                                                            :name metric-name}]
                            10000
                            (fn [reply]
                              (if (:success reply)
                                (state/dispatch [:query/invalidate
                                                 {:query-key-pattern [:human-metrics module-id]}])
                                (js/alert (str "Error: " (:error reply))))))))
                       [decoded-module-id])]

    (if-not decoded-module-id
      ($ :div.p-6
         ($ :div.text-center.text-gray-500 "No module specified"))

      ($ :div.p-6
         ;; Header
         ($ :div.flex.justify-between.items-center.mb-6
            ($ :h2.text-2xl.font-bold.text-gray-900 "Human Metrics")
            ($ :button.bg-blue-600.text-white.px-4.py-2.rounded-md.hover:bg-blue-700.transition-colors
               {:data-testid "create-metric-button"
                :onClick #(state/dispatch [:modal/show-form :create-human-metric {:module-id decoded-module-id}])}
               "+ Create Metric"))

       ;; Search
         ($ :div.mb-4
            ($ :input.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
               {:type "text"
                :placeholder "Search metrics..."
                :value search-term
                :onChange #(set-search-term (.. % -target -value))}))

       ;; Table
         (cond
           isLoading
           ($ :div.flex.justify-center.items-center.py-12
              ($ common/spinner {:size :medium}))

           error
           ($ :div.text-red-600 "Error loading metrics: " (str error))

           (empty? data)
           ($ :div.text-center.py-12.text-gray-500
              (if (str/blank? search-term)
                "No metrics defined yet. Create one to get started."
                "No metrics match your search."))

           :else
           ($ :div {:className (:container common/table-classes)}
              ($ :table {:className (:table common/table-classes)}
                 ($ :thead {:className (:thead common/table-classes)}
                    ($ :tr
                       ($ :th {:className (:th common/table-classes)} "Name")
                       ($ :th {:className (:th common/table-classes)} "Type")
                       ($ :th {:className (:th common/table-classes)} "Configuration")
                       ($ :th {:className (:th common/table-classes)} "Actions")))
                 ($ :tbody
                    (into []
                          (for [metric data
                                :let [metric-name (:name metric)
                                      metric-def (:metric metric)
                                      metric-type (:__typename metric-def)
                                      is-numeric? (= metric-type "HumanNumericMetric")
                                      is-category? (= metric-type "HumanCategoryMetric")]]
                            ($ :tr {:key metric-name
                                    :className "hover:bg-gray-50"}
                               ($ :td {:className (:td common/table-classes)}
                                  metric-name)
                               ($ :td {:className (:td common/table-classes)}
                                  ($ :span.inline-flex.px-2.py-1.rounded.text-xs.font-medium
                                     {:className (if is-numeric?
                                                   "bg-blue-100 text-blue-700"
                                                   "bg-purple-100 text-purple-700")}
                                     (if is-numeric? "Numeric" "Categorical")))
                               ($ :td {:className (:td common/table-classes)}
                                  (cond
                                    is-numeric?
                                    (str "Range: " (:min metric-def) " - " (:max metric-def))

                                    is-category?
                                    (str "Options: " (str/join ", " (:categories metric-def)))))
                               ($ :td {:className (:td-right common/table-classes)}
                                  ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                     {:onClick (fn [e]
                                                 (.stopPropagation e)
                                                 (handle-delete metric-name))}
                                     ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                     "Delete")))))))

            ;; Load More button
              (when hasMore
                ($ :tfoot.bg-gray-50.border-t.border-gray-200
                   ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                      {:onClick (when-not isFetchingMore loadMore)}
                      ($ :td.px-6.py-3.text-center.text-sm.text-blue-600.font-medium.cursor-pointer
                         {:colSpan "4"}
                         (if isFetchingMore
                           ($ :div.flex.justify-center.items-center.gap-2
                              ($ common/spinner {:size :small})
                              "Loading...")
                           "Load More")))))))))))

;; =============================================================================
;; METRICS FORM
;; =============================================================================

(forms/reg-form
 :create-human-metric
 {:steps [:main]
  :main
  {:initial-fields (fn [props]
                     (merge {:name ""
                             :type :numeric
                             :min 1
                             :max 10
                             :categories ""}
                            props))
   :validators {:name [forms/required]
                :categories [(fn [v form-state]
                               (when (= (:type form-state) :categorical)
                                 (cond
                                   (str/blank? v)
                                   "Categories are required for categorical metrics"

                                   :else
                                   (let [categories (->> (str/split v #",")
                                                         (map str/trim)
                                                         (filter #(not (str/blank? %))))]
                                     (cond
                                       (empty? categories)
                                       "At least two categories are required"

                                       (= (count categories) 1)
                                       "At least two categories are required"

                                       (not= (count categories) (count (set categories)))
                                       "Duplicate categories are not allowed"

                                       :else nil)))))]
                :min [(fn [v form-state]
                        (when (= (:type form-state) :numeric)
                          (cond
                            (or (str/blank? (str v))
                                (js/isNaN (js/parseFloat v)))
                            "Min must be a number"

                            :else
                            (let [min-val (js/parseFloat v)
                                  max-val (js/parseFloat (:max form-state))]
                              (when (and (not (js/isNaN max-val))
                                         (>= min-val max-val))
                                "Min must be less than Max")))))]
                :max [(fn [v form-state]
                        (when (= (:type form-state) :numeric)
                          (cond
                            (or (str/blank? (str v))
                                (js/isNaN (js/parseFloat v)))
                            "Max must be a number"

                            :else
                            (let [min-val (js/parseFloat (:min form-state))
                                  max-val (js/parseFloat v)]
                              (when (and (not (js/isNaN min-val))
                                         (<= max-val min-val))
                                "Max must be greater than Min")))))]}
   :ui (fn [{:keys [form-id]}]
         (let [type-field (forms/use-form-field form-id :type)
               name-field (forms/use-form-field form-id :name)
               min-field (forms/use-form-field form-id :min)
               max-field (forms/use-form-field form-id :max)
               categories-field (forms/use-form-field form-id :categories)]
           ($ :div.space-y-4.p-4
              ;; Name field
              ($ forms/form-field (merge {:label "Metric Name"
                                          :required? true
                                          :placeholder "e.g., helpfulness, accuracy"}
                                         name-field))

              ;; Type selector
              ($ :div.space-y-1
                 ($ :label.block.text-sm.font-medium.text-gray-700
                    "Metric Type"
                    ($ :span.text-red-500.ml-1 "*"))
                 ($ :select.w-full.p-3.border.border-gray-300.rounded-md.text-sm.focus:ring-blue-500.focus:border-blue-500
                    {:value (name (:value type-field))
                     :onChange #((:on-change type-field) (keyword (.. % -target -value)))}
                    ($ :option {:value "numeric"} "Numeric Range")
                    ($ :option {:value "categorical"} "Categorical (Options)")))

              ;; Conditional fields based on type
              (if (= (:value type-field) :numeric)
                ;; Numeric fields
                ($ :div.flex.gap-4
                   ($ :div.flex-1
                      ($ forms/form-field (merge {:label "Min"
                                                  :type :number
                                                  :required? true
                                                  :placeholder "1"}
                                                 min-field)))
                   ($ :div.flex-1
                      ($ forms/form-field (merge {:label "Max"
                                                  :type :number
                                                  :required? true
                                                  :placeholder "10"}
                                                 max-field))))

                ;; Categorical field with preview
                ($ :div
                   ($ forms/form-field (merge {:label "Options (comma separated)"
                                               :required? true
                                               :placeholder "Good, Bad, Average"}
                                              categories-field))

                   ;; Preview pillboxes
                   (let [cat-value (:value categories-field)
                         categories (when-not (str/blank? cat-value)
                                      (->> (str/split cat-value #",")
                                           (map str/trim)
                                           (filter #(not (str/blank? %)))))]
                     (when (seq categories)
                       ($ :div.mt-2
                          ($ :div.text-xs.text-gray-500.mb-1 "Preview:")
                          ($ :div.flex.flex-wrap.gap-2
                             (for [category categories]
                               ($ :span.inline-flex.items-center.px-3.py-1.rounded-full.text-sm.font-medium.bg-purple-100.text-purple-700
                                  {:key category}
                                  category)))))))))))
   :modal-props {:title "Create Human Metric"
                 :submit-text "Create"}}

  :on-submit
  {:event (fn [db form-state]
            (let [{:keys [name type min max categories module-id]} form-state]
              [:human-feedback/create-metric
               (cond-> {:module-id module-id
                        :name name
                        :type type}
                 (= type :numeric)
                 (assoc :min (js/parseFloat min)
                        :max (js/parseFloat max))

                 (= type :categorical)
                 (assoc :categories categories))]))
   :on-success-invalidate (fn [db {:keys [module-id]} _reply]
                            {:query-key-pattern [:human-metrics module-id]})}})

;; =============================================================================
;; QUEUE FORM - Create Human Feedback Queue
;; =============================================================================

(defui metric-selector
  "Metric selector with required checkbox and remove button for rubric forms.
   
   Uses SearchableSelector under the hood."
  [{:keys [module-id value on-change on-remove required? index]}]
  ($ :div.flex.items-start.gap-2 {:data-testid (str "rubric-" index)}
     ;; Searchable metric selector
     ($ :div.flex-1
        {:key "selector"}
        ($ ss/SearchableSelector
           {:module-id module-id
            :value value
            :on-change on-change
            :sente-event-fn (fn [mid search-string]
                              [:human-feedback/get-metrics
                               {:module-id mid
                                :filters {:search-string search-string}}])
            :items-key :items
            :item-id-fn :name
            :item-label-fn :name
            :placeholder "Select metric..."
            :label "Metric"
            :hide-label? true
            :data-testid "metric-selector"}))

     ;; Required checkbox
     ($ :label.flex.items-center.gap-1.pt-2
        {:key "checkbox"}
        ($ :input.rounded.border-gray-300
           {:data-testid "metric-required-checkbox"
            :type "checkbox"
            :checked (boolean required?)
            :onChange #(on-change value {:required (.. % -target -checked)})})
        ($ :span.text-sm.text-gray-600 "Required"))

     ;; Remove button
     ($ :button.text-red-600.hover:text-red-800.p-2.rounded.mt-1
        {:key "remove"
         :data-testid "remove-rubric-button"
         :type "button"
         :onClick on-remove}
        ($ TrashIcon {:className "h-5 w-5"}))))

(forms/reg-form
 :create-human-feedback-queue
 {:steps [:main]
  :main
  {:initial-fields (fn [props]
                     (merge {:name ""
                             :description ""
                             :rubrics []}
                            props))
   :validators {:name [forms/required]
                :rubrics [(fn [rubrics _form-state]
                            (cond
                              (empty? rubrics)
                              "At least one rubric is required"

                              :else
                              ;; Check for duplicate metrics
                              (let [metric-names (->> rubrics
                                                      (map :metric)
                                                      (filter some?))
                                    duplicates (->> metric-names
                                                    frequencies
                                                    (filter #(> (val %) 1))
                                                    (map key))]
                                (when (seq duplicates)
                                  (str "Duplicate metrics: " (str/join ", " duplicates))))))]}
   :ui (fn [{:keys [form-id props]}]
         (let [{:keys [module-id]} props
               name-field (forms/use-form-field form-id :name)
               desc-field (forms/use-form-field form-id :description)
               rubrics-field (forms/use-form-field form-id :rubrics)
               rubrics (:value rubrics-field)

               add-rubric (fn []
                            ((:on-change rubrics-field)
                             (conj rubrics {:id (random-uuid)
                                            :metric nil
                                            :required false})))

               update-rubric (fn [idx metric-name opts]
                               (let [updated (assoc-in rubrics [idx]
                                                       (merge {:metric metric-name}
                                                              opts))]
                                 ((:on-change rubrics-field) updated)))

               remove-rubric (fn [idx]
                               (let [updated (vec (concat (subvec rubrics 0 idx)
                                                          (subvec rubrics (inc idx))))]
                                 ((:on-change rubrics-field) updated)))]

           ($ :div.space-y-4.p-4
              ;; Name field
              ($ forms/form-field (merge {:label "Queue Name"
                                          :required? true
                                          :data-testid "queue-name-input"
                                          :placeholder "e.g., support-quality"}
                                         name-field))

              ;; Description field
              ($ forms/form-field (merge {:label "Description"
                                          :data-testid "queue-description-input"
                                          :placeholder "What is this queue for?"}
                                         desc-field))

              ;; Rubrics section
              ($ :div.space-y-2
                 ($ :label.block.text-sm.font-medium.text-gray-700
                    "Rubrics"
                    ($ :span.text-red-500.ml-1 "*"))

                 ($ :div.text-sm.text-gray-500.mb-2
                    "Add metrics that reviewers should evaluate")

                 ;; Rubric list
                 ($ :div.space-y-2
                    (vec
                     (for [[idx rubric] (map-indexed vector rubrics)]
                       ($ metric-selector
                          {:key (:id rubric)
                           :index idx
                           :module-id module-id
                           :value (:metric rubric)
                           :required? (:required rubric)
                           :on-change (fn [metric-name & [opts]]
                                        (update-rubric idx metric-name opts))
                           :on-remove #(remove-rubric idx)}))))

                 ;; Add rubric button
                 ($ :button.w-full.px-3.py-2.border-2.border-dashed.border-gray-300.rounded-md.text-gray-600.hover:border-gray-400.hover:text-gray-700.transition-colors
                    {:data-testid "add-rubric-button"
                     :type "button"
                     :onClick add-rubric}
                    "+ Add Rubric")

                 ;; Error message
                 (when (:error rubrics-field)
                   ($ :p.text-sm.text-red-600.mt-1 (:error rubrics-field)))))))
   :modal-props {:title "Create Human Feedback Queue"
                 :submit-text "Create"}}

  :on-submit
  {:event (fn [db form-state]
            (let [{:keys [name description rubrics module-id]} form-state
                  ;; Strip out :id field used for React keys
                  clean-rubrics (mapv #(dissoc % :id) rubrics)]
              [:human-feedback/create-queue
               {:module-id module-id
                :name name
                :description description
                :rubrics clean-rubrics}]))
   :on-success-invalidate (fn [db {:keys [module-id]} _reply]
                            {:query-key-pattern [:human-feedback-queues module-id]})}})

;; =============================================================================
;; QUEUE LIST PAGE
;; =============================================================================

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        decoded-module-id (common/url-decode module-id)

        ;; Search state
        [search-term set-search-term] (useState "")
        [debounced-search] (useDebounce search-term 300)

        ;; Use paginated query
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:human-feedback-queues module-id debounced-search]
          :sente-event [:human-feedback/get-queues
                        {:module-id decoded-module-id
                         :filters {:search-string debounced-search}}]
          :page-size 20})]

    ($ :div.p-6
       ;; Header with Create Button
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h2.text-2xl.font-bold.text-gray-900 "Human Feedback Queues")
          ($ :button.bg-blue-600.text-white.px-4.py-2.rounded-md.hover:bg-blue-700.transition-colors
             {:data-testid "create-queue-button"
              :onClick #(state/dispatch [:modal/show-form :create-human-feedback-queue {:module-id decoded-module-id}])}
             "+ Create Queue"))

       ;; Search bar
       ($ :div.mb-4
          ($ :input.w-full.px-4.py-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
             {:data-testid "search-queues-input"
              :type "text"
              :placeholder "Search queues..."
              :value search-term
              :onChange #(set-search-term (-> % .-target .-value))}))

       ;; Table or empty state
       (if (and (not isLoading) (empty? data))
         ($ :div.text-center.py-12.bg-gray-50.rounded-md
            {:data-testid "empty-state"}
            ($ :p.text-gray-500 "No queues found"))

         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Description")
                     ($ :th {:className (:th common/table-classes)} "Rubrics")
                     ($ :th {:className (:th common/table-classes)} "Actions")))

               ($ :tbody {:className (:tbody common/table-classes)}
                  (for [queue data]
                    (let [queue-name (:name queue)]
                      ($ :tr {:key queue-name
                              :className (:tr common/table-classes)
                              :data-testid (str "queue-row-" queue-name)}
                         ;; Name (clickable)
                         ($ :td {:className (:td common/table-classes)}
                            ($ :a.text-blue-600.hover:text-blue-800.font-medium
                               {:data-testid "queue-name-link"
                                :href (rfe/href :module/human-feedback-queue-detail
                                                {:module-id module-id
                                                 :queue-id (common/url-encode queue-name)})
                                :onClick (fn [e]
                                           (.preventDefault e)
                                           (rfe/push-state :module/human-feedback-queue-detail
                                                           {:module-id module-id
                                                            :queue-id (common/url-encode queue-name)}))}
                               queue-name))

                         ;; Description
                         ($ :td {:className (:td common/table-classes)}
                            ($ :span.text-gray-600 (or (:description queue) "")))

                         ;; Rubrics count
                         ($ :td {:className (:td common/table-classes)}
                            ($ :span.text-gray-600
                               {:data-testid "queue-rubric-count"}
                               (str (count (:rubrics queue)) " rubric"
                                    (if (= 1 (count (:rubrics queue))) "" "s"))))

                         ;; Actions
                         ($ :td {:className (:td common/table-classes)}
                            ($ :button.text-red-600.hover:text-red-800.p-2.rounded
                               {:data-testid "delete-queue-button"
                                :onClick (fn [e]
                                           (.stopPropagation e)
                                           (state/dispatch [:modal/show-confirmation
                                                            {:title "Delete Queue"
                                                             :message (str "Are you sure you want to delete \"" queue-name "\"?")
                                                             :on-confirm #(do
                                                                            (sente/send! [:human-feedback/delete-queue
                                                                                          {:module-id decoded-module-id
                                                                                           :name queue-name}])
                                                                            (queries/invalidate-query [:human-feedback-queues module-id]))}]))}
                               ($ TrashIcon {:className "h-5 w-5"})))))))

               ;; Load more row
               (when hasMore
                 ($ :tfoot
                    ($ :tr
                       ($ :td {:colSpan 4 :className "px-6 py-4 text-center"}
                          ($ :button.text-blue-600.hover:text-blue-800.font-medium
                             {:onClick loadMore
                              :disabled isFetchingMore}
                             (if isFetchingMore "Loading..." "Load More"))))))))))))

;; =============================================================================
;; QUEUE DETAIL PAGE
;; =============================================================================

(defui queue-info-header [{:keys [queue-info queue-id module-id]}]
  ($ :div.bg-white.rounded-md.border.border-gray-200.p-6.mb-6
     ($ :div.flex.justify-between.items-start
        ($ :div
           ($ :h3.text-lg.font-semibold.text-gray-900.mb-2
              (str "Queue: " queue-id))
           ($ :p.text-gray-600 (:description queue-info)))
        ($ :button.inline-flex.items-center.px-3.py-2.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors
           {:onClick #(js/alert "Edit queue - not yet implemented")}
           ($ PencilIcon {:className "h-5 w-5 mr-2"})
           "Edit Queue"))

     ;; Rubrics
     ($ :div.mt-4
        ($ :h4.text-sm.font-medium.text-gray-700.mb-2 "Rubrics:")
        ($ :div.space-y-2
           (for [rubric (:rubrics queue-info)]
             (let [metric (:metric rubric)
                   is-category? (= (:__typename metric) "HumanCategoryMetric")
                   is-numeric? (= (:__typename metric) "HumanNumericMetric")]
               ($ :div.flex.items-start.gap-2 {:key (:name rubric)}
                  ($ :span.inline-flex.px-2.py-1.rounded.text-xs.font-medium
                     {:className (if (:required rubric)
                                   "bg-blue-100 text-blue-700"
                                   "bg-gray-100 text-gray-600")}
                     (if (:required rubric) "Required" "Optional"))
                  ($ :div
                     ($ :span.font-medium.text-gray-900 (:name rubric))
                     ($ :span.text-gray-600 " - " (:description rubric))
                     (when is-category?
                       ($ :div.text-xs.text-gray-500.mt-1
                          "Categories: " (clojure.string/join ", " (:categories metric))))
                     (when is-numeric?
                       ($ :div.text-xs.text-gray-500.mt-1
                          (str "Range: " (:min metric) " - " (:max metric))))))))))))

(defui queue-item-row [{:keys [item module-id queue-id]}]
  ($ :tr.hover:bg-gray-50.cursor-pointer
     {:onClick #(rfe/push-state :module/human-feedback-queue-item
                                {:module-id module-id
                                 :queue-id queue-id
                                 :item-id (:id item)})}
     ($ :td.px-4.py-3.text-sm.text-gray-900.font-mono (:id item))
     ($ :td.px-4.py-3.text-sm.text-gray-600 (:comment item))
     ($ :td.px-4.py-3.text-sm.text-gray-600.max-w-xs.truncate
        (common/to-json (:input item)))
     ($ :td.px-4.py-3.text-sm.text-gray-600.max-w-xs.truncate
        (common/to-json (:output item)))))

(defui detail []
  (let [{:keys [module-id queue-id]} (state/use-sub [:route :path-params])
        decoded-module-id (common/url-decode module-id)
        decoded-queue-id (common/url-decode queue-id)

        ;; Dummy data
        queue-info dummy-queue-info
        queue-items dummy-queue-items]

    ($ :div.p-6
       ;; Queue info header
       ($ queue-info-header {:queue-info queue-info
                             :queue-id decoded-queue-id
                             :module-id module-id})

       ;; Queue items table
       ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
          ($ :table.w-full.text-sm
             ($ :thead.bg-gray-50.border-b.border-gray-200
                ($ :tr
                   ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "ID")
                   ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Comment")
                   ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Input")
                   ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Output")))
             ($ :tbody.divide-y.divide-gray-200
                (for [item (:items queue-items)]
                  ($ queue-item-row {:key (:id item)
                                     :item item
                                     :module-id module-id
                                     :queue-id queue-id}))))))))

;; =============================================================================
;; EVALUATION FORM
;; =============================================================================

(defn get-reviewer-name-from-cookie []
  "reviewer-from-cookie")

(defn save-reviewer-name-to-cookie! [name]
  ;; TODO: implement cookie storage
  nil)

(defui metric-field [{:keys [rubric value on-change error]}]
  (let [metric (:metric rubric)
        is-category? (= (:__typename metric) "HumanCategoryMetric")
        is-numeric? (= (:__typename metric) "HumanNumericMetric")]
    ($ :div.mb-4
       ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
          (:name rubric)
          (when (:required rubric)
            ($ :span.text-red-500.ml-1 "*"))
          ($ :div.text-xs.text-gray-500.font-normal.mt-1
             (:description rubric)))

       (cond
         is-category?
         ($ :select.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
            {:value (or value "")
             :onChange #(on-change (.. % -target -value))
             :className (if error "border-red-500" "")}
            ($ :option {:value ""} "-- Select --")
            (for [category (sort (:categories metric))]
              ($ :option {:key category :value category} category)))

         is-numeric?
         ($ :div
            ($ :input.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
               {:type "number"
                :min (:min metric)
                :max (:max metric)
                :value (or value "")
                :onChange #(on-change (.. % -target -value))
                :placeholder (str (:min metric) " - " (:max metric))
                :className (if error "border-red-500" "")})
            ($ :div.text-xs.text-gray-500.mt-1
               (str "Valid range: " (:min metric) " - " (:max metric)))))

       (when error
         ($ :div.text-sm.text-red-600.mt-1 error)))))

(defui item-detail []
  (let [{:keys [module-id queue-id item-id]} (state/use-sub [:route :path-params])

        ;; Dummy data
        queue-info dummy-queue-info
        items (:items dummy-queue-items)
        current-idx (.indexOf (clj->js (map :id items)) item-id)
        current-item (nth items current-idx nil)
        has-prev? (> current-idx 0)
        has-next? (< current-idx (dec (count items)))
        prev-item-id (when has-prev? (:id (nth items (dec current-idx))))
        next-item-id (when has-next? (:id (nth items (inc current-idx))))

        ;; Form state
        [scores set-scores] (uix/use-state {})
        [comment set-comment] (uix/use-state "")
        [reviewer-name set-reviewer-name] (uix/use-state (get-reviewer-name-from-cookie))
        [errors set-errors] (uix/use-state {})
        [show-dismiss-confirm? set-show-dismiss-confirm] (uix/use-state false)

        validate-form (fn []
                        (let [errs (reduce (fn [acc rubric]
                                             (let [metric-name (:name rubric)
                                                   value (get scores metric-name)
                                                   metric (:metric rubric)]
                                               (cond
                                                 (and (:required rubric) (or (nil? value) (= value "")))
                                                 (assoc acc metric-name "This field is required")

                                                 (and (= (:__typename metric) "HumanNumericMetric")
                                                      value
                                                      (not= value ""))
                                                 (let [num-val (js/parseFloat value)]
                                                   (cond
                                                     (js/isNaN num-val)
                                                     (assoc acc metric-name "Must be a number")

                                                     (< num-val (:min metric))
                                                     (assoc acc metric-name (str "Must be at least " (:min metric)))

                                                     (> num-val (:max metric))
                                                     (assoc acc metric-name (str "Must be at most " (:max metric)))

                                                     :else acc))

                                                 :else acc)))
                                           {}
                                           (:rubrics queue-info))]
                          (if (clojure.string/blank? reviewer-name)
                            (assoc errs :reviewer-name "Reviewer name is required")
                            errs)))

        handle-submit (fn []
                        (let [validation-errors (validate-form)]
                          (if (empty? validation-errors)
                            (do
                              (save-reviewer-name-to-cookie! reviewer-name)
                              (js/alert (str "Submitted! Scores: " (pr-str scores)))
                              ;; Auto-advance to next item
                              (if has-next?
                                (rfe/push-state :module/human-feedback-queue-item
                                                {:module-id module-id
                                                 :queue-id queue-id
                                                 :item-id next-item-id})
                                (rfe/push-state :module/human-feedback-queue-end
                                                {:module-id module-id
                                                 :queue-id queue-id})))
                            (set-errors validation-errors))))

        handle-dismiss (fn []
                         (set-show-dismiss-confirm false)
                         (js/alert "Item dismissed!")
                         ;; Navigate to next item or back to queue
                         (if has-next?
                           (rfe/push-state :module/human-feedback-queue-item
                                           {:module-id module-id
                                            :queue-id queue-id
                                            :item-id next-item-id})
                           (rfe/push-state :module/human-feedback-queue-detail
                                           {:module-id module-id
                                            :queue-id queue-id})))]

    (if-not current-item
      ($ :div.p-6
         ($ :div.text-center.text-gray-500 "Item not found"))

      ($ :div.p-6.max-w-5xl.mx-auto
         ;; Header with navigation
         ($ :div.flex.justify-between.items-center.mb-6
            ($ :h2.text-2xl.font-bold.text-gray-900
               (str "Review Item: " item-id))
            ($ :div.flex.gap-2
               ($ :button.px-3.py-2.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors.disabled:opacity-50.disabled:cursor-not-allowed
                  {:disabled (not has-prev?)
                   :onClick #(when has-prev?
                               (rfe/push-state :module/human-feedback-queue-item
                                               {:module-id module-id
                                                :queue-id queue-id
                                                :item-id prev-item-id}))}
                  ($ ChevronLeftIcon {:className "h-5 w-5"}))
               ($ :button.px-3.py-2.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors.disabled:opacity-50.disabled:cursor-not-allowed
                  {:disabled (not has-next?)
                   :onClick #(when has-next?
                               (rfe/push-state :module/human-feedback-queue-item
                                               {:module-id module-id
                                                :queue-id queue-id
                                                :item-id next-item-id}))}
                  ($ ChevronRightIcon {:className "h-5 w-5"}))))

         ;; Comment
         (when (:comment current-item)
           ($ :div.bg-blue-50.border.border-blue-200.rounded-md.p-4.mb-6
              ($ :div.text-sm.font-medium.text-blue-900 "Feedback Request Comment")
              ($ :div.text-sm.text-blue-800 (:comment current-item))))

         ;; Input/Output Display
         ($ :div.grid.grid-cols-2.gap-4.mb-6
            ($ :div.bg-white.border.border-gray-200.rounded-md.p-4
               ($ :h3.text-sm.font-semibold.text-gray-700.mb-2 "Input")
               ($ :pre.text-xs.bg-gray-50.p-3.rounded.overflow-auto.max-h-64
                  (common/to-json (:input current-item))))
            ($ :div.bg-white.border.border-gray-200.rounded-md.p-4
               ($ :h3.text-sm.font-semibold.text-gray-700.mb-2 "Output")
               ($ :pre.text-xs.bg-gray-50.p-3.rounded.overflow-auto.max-h-64
                  (common/to-json (:output current-item)))))

         ;; Evaluation Form
         ($ :div.bg-white.border.border-gray-200.rounded-md.p-6.mb-6
            ($ :h3.text-lg.font-semibold.text-gray-900.mb-4 "Evaluation")

            ;; Metric fields
            (for [rubric (:rubrics queue-info)]
              ($ metric-field {:key (:name rubric)
                               :rubric rubric
                               :value (get scores (:name rubric))
                               :on-change #(set-scores (assoc scores (:name rubric) %))
                               :error (get errors (:name rubric))}))

            ;; Comment field
            ($ :div.mb-4
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
                  "Comment (optional)")
               ($ :textarea.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
                  {:value comment
                   :onChange #(set-comment (.. % -target -value))
                   :rows 3
                   :placeholder "Add any additional notes..."}))

            ;; Reviewer name
            ($ :div.mb-4
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
                  "Reviewer Name"
                  ($ :span.text-red-500.ml-1 "*"))
               ($ :input.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
                  {:type "text"
                   :value reviewer-name
                   :onChange #(set-reviewer-name (.. % -target -value))
                   :placeholder "Your name"
                   :className (if (:reviewer-name errors) "border-red-500" "")})
               (when (:reviewer-name errors)
                 ($ :div.text-sm.text-red-600.mt-1 (:reviewer-name errors)))))

         ;; Action buttons
         ($ :div.flex.justify-between
            ($ :button.px-4.py-2.border.border-red-300.text-red-700.rounded-md.hover:bg-red-50.transition-colors.inline-flex.items-center
               {:onClick #(set-show-dismiss-confirm true)}
               ($ XMarkIcon {:className "h-5 w-5 mr-2"})
               "Dismiss")
            ($ :button.px-6.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
               {:onClick handle-submit}
               "Submit & Continue"))

         ;; Dismiss confirmation dialog
         (when show-dismiss-confirm?
           ($ :div.fixed.inset-0.bg-black.bg-opacity-50.flex.items-center.justify-center.z-50
              {:onClick #(set-show-dismiss-confirm false)}
              ($ :div.bg-white.rounded-lg.p-6.max-w-md.mx-4
                 {:onClick #(. % stopPropagation)}
                 ($ :h3.text-lg.font-semibold.text-gray-900.mb-2
                    "Dismiss Item?")
                 ($ :p.text-gray-600.mb-4
                    "This will remove the item from the queue without adding feedback. This action cannot be undone.")
                 ($ :div.flex.justify-end.gap-2
                    ($ :button.px-4.py-2.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors
                       {:onClick #(set-show-dismiss-confirm false)}
                       "Cancel")
                    ($ :button.px-4.py-2.bg-red-600.text-white.rounded-md.hover:bg-red-700.transition-colors
                       {:onClick handle-dismiss}
                       "Dismiss Item")))))))))

;; End of queue page
(defui queue-end []
  (let [{:keys [module-id queue-id]} (state/use-sub [:route :path-params])]
    ($ :div.p-6.max-w-2xl.mx-auto.text-center
       ($ :div.bg-white.border.border-gray-200.rounded-lg.p-12
          ($ :h2.text-2xl.font-bold.text-gray-900.mb-4
             "🎉 Reached End of Queue")
          ($ :p.text-gray-600.mb-6
             "You've reviewed all items in this queue. Great work!")
          ($ :button.px-6.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick #(rfe/push-state :module/human-feedback-queue-detail
                                        {:module-id module-id
                                         :queue-id queue-id})}
             "Back to Queue")))))

