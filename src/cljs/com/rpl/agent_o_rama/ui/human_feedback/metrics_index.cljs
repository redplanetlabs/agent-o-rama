(ns com.rpl.agent-o-rama.ui.human-feedback.metrics-index
  "Human metrics index page and metric creation form."
  (:require
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["@heroicons/react/24/outline" :refer [TrashIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.impl.ui.rpc.human-feedback :as rpc-hf]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [re-frame.core :as rf]
   [clojure.string :as str]))

;; =============================================================================
;; METRICS INDEX PAGE
;; =============================================================================

(defui metrics-index []
  (let [{:keys [module-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        decoded-module-id (when module-id (common/url-decode module-id))
        ;; Search state
        [search-term set-search-term] (useState "")
        [debounced-search] (useDebounce search-term 300)

        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-infinite-rpc-query
         {:rfq-key ::rpc-hf/get-metrics-inf!!
          :params {:module-id decoded-module-id
                   :filters (when-not (str/blank? debounced-search)
                              {:search-string debounced-search})}
          :page-size 20
          :enabled? (and module-id (boolean decoded-module-id))})

        handle-delete (uix/use-callback
                       (fn [metric-name]
                         (when (js/confirm (str "Delete metric '" metric-name "'?"))
                           (-> (rpc/call ::rpc-hf/delete-metric!! {:module-id decoded-module-id :name metric-name})
                           (.then (fn [_]
                                    (rf/dispatch [:query/invalidate {:query-key-pattern [:human-metrics module-id]}])
                                    (rf/dispatch [:re-frame.query/invalidate-tags [[:human-feedback/metrics module-id]]])))
                           (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))
                       [decoded-module-id])]

    (if-not decoded-module-id
      ($ :div.p-6
         ($ :div.text-center.text-gray-500 "No module specified"))

      ($ :div.p-6
         ;; Header
         ($ :div.flex.justify-between.items-center.mb-6
            ($ :h2.text-2xl.font-bold.text-gray-900 "Human Metrics")
            ($ :button.bg-blue-600.text-white.px-4.py-2.rounded-md.hover:bg-blue-700.transition-colors.cursor-pointer
               {:data-testid "create-metric-button"
                :onClick #(rf/dispatch [:modal/show-form :create-human-metric {:module-id decoded-module-id}])}
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
                                      ;; Determine type by checking which fields exist
                                      is-numeric? (and (:min metric-def) (:max metric-def))
                                      is-category? (contains? metric-def :categories)]]
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
                                (js/isNaN (js/parseInt v 10)))
                            "Min must be an integer"

                            (not= (js/parseInt v 10) (js/parseFloat v))
                            "Min must be an integer (no decimals)"

                            :else
                            (let [min-val (js/parseInt v 10)
                                  max-val (js/parseInt (:max form-state) 10)]
                              (when (and (not (js/isNaN max-val))
                                         (>= min-val max-val))
                                "Min must be less than Max")))))]
                :max [(fn [v form-state]
                        (when (= (:type form-state) :numeric)
                          (cond
                            (or (str/blank? (str v))
                                (js/isNaN (js/parseInt v 10)))
                            "Max must be an integer"

                            (not= (js/parseInt v 10) (js/parseFloat v))
                            "Max must be an integer (no decimals)"

                            :else
                            (let [min-val (js/parseInt (:min form-state) 10)
                                  max-val (js/parseInt v 10)]
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
                    ($ :option {:value "categorical"} "Categorical")))

              ;; Conditional fields based on type
              (if (= (:value type-field) :numeric)
                ;; Numeric fields
                ($ :div.flex.gap-4
                   ($ :div.flex-1
                      ($ forms/form-field (merge {:label "Min (inclusive)"
                                                  :type :number
                                                  :required? true
                                                  :placeholder "1"}
                                                 min-field)))
                   ($ :div.flex-1
                      ($ forms/form-field (merge {:label "Max (inclusive)"
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
  {:mutation (fn [_db form-state]
               (let [{:keys [name type min max categories module-id]} form-state]
                 [::rpc-hf/create-metric!!
                  (cond-> {:module-id module-id :name name :type type}
                    (= type :numeric)
                    (assoc :min (js/parseInt min 10) :max (js/parseInt max 10))
                    (= type :categorical)
                    (assoc :categories categories))]))
   :on-success-invalidate (fn [_db {:keys [module-id]} _reply]
                            {:query-key-pattern [:human-metrics module-id]})
   :rfq-invalidate-tags (fn [_db {:keys [module-id]} _reply]
                          [[:human-feedback/metrics module-id]])}})
