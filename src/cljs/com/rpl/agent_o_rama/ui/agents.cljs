(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.invocation-page :as invocation-page]
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]

   [uix.core :as uix :refer [defui defhook $]]
   [reitit.frontend.easy :as rfe]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [clojure.string :as str]))

;; =============================================================================
;; FORM REGISTRATION - Manual Run Agent
;; =============================================================================

(forms/reg-form
 :manual-run-agent
 {:steps [:main]
  :main {:initial-fields (fn [props]
                           {:args ""
                            :metadata-args ""
                            :module-id (:module-id props)
                            :agent-name (:agent-name props)})
         :validators {:args [forms/required forms/valid-json]
                      :metadata-args [forms/valid-json]}
         :modal-props {:title "Manual Run Agent"}}
  :on-submit (fn [db form-state]
               (let [{:keys [args metadata-args module-id agent-name]} form-state
                     parsed-args (try (js->clj (js/JSON.parse args)) (catch js/Error _ nil))
                     parsed-metadata (try (if (str/blank? metadata-args)
                                            {}
                                            (js->clj (js/JSON.parse metadata-args)))
                                          (catch js/Error _ {}))]

                 ;; Mark as submitting
                 (state/dispatch [:db/set-value [:forms (:form-id form-state) :submitting?] true])

                 ;; Make the Sente request
                 (sente/request!
                  [:invocations/run-agent {:module-id module-id
                                           :agent-name agent-name
                                           :args parsed-args
                                           :metadata parsed-metadata}]
                  5000
                  (fn [reply]
                    (state/dispatch [:db/set-value [:forms (:form-id form-state) :submitting?] false])
                    (if (:success reply)
                      (let [data (:data reply)]
                        ;; Clear the form fields on success
                        (state/dispatch [:form/update-field (:form-id form-state) :args ""])
                        (state/dispatch [:form/update-field (:form-id form-state) :metadata-args ""])
                        ;; Navigate to the trace
                        (rfe/push-state :agent/invocation-detail
                                        {:module-id module-id
                                         :agent-name agent-name
                                         :invoke-id (str (:task-id data) "-" (:invoke-id data))}))
                      ;; Set error on failure
                      (state/dispatch [:db/set-value
                                       [:forms (:form-id form-state) :error]
                                       (str "Error: " (or (:error reply) "Unknown error"))]))))))})

(defui result-badge
  [{:keys [status human-request?]}]
  (cond
    human-request?
    ($
     :span.px-2.py-1.bg-amber-100.text-amber-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
     "🙋 Needs input")

    (= status :pending)
    ($
     :span.px-2.py-1.bg-blue-100.text-blue-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
     ($ common/spinner {:size :small})
     "Pending")

    (= status :failure)
    ($ :span.px-2.py-1.bg-red-100.text-red-800.rounded-full.text-xs.font-medium "Failed")

    :else
    ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Success")))

(defui invocation-row [{:keys [invoke module-id agent-name on-click show-feedback-metric? feedback-metric-name]}]
  (let [task-id (:task-id invoke)
        agent-id (:agent-id invoke)
        start-time (:start-time-millis invoke)
        href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" task-id "-" agent-id)
        invoke-id (str task-id "-" agent-id)
        args-json (common/to-json (:invoke-args invoke))]
    ($ :tr.hover:bg-gray-50.transition-colors.duration-150
       {:key href}
       ($ :td.px-4.py-3
          ($ :a.px-3.py-1.bg-blue-100.text-blue-700.rounded.text-xs.font-medium.hover:bg-blue-200.transition-colors.duration-150.cursor-pointer
             {:href href}
             "View trace"))
       ($ :td.px-4.py-3.text-sm.text-gray-600.font-mono
          {:title (common/format-timestamp start-time)}
          (common/format-relative-time start-time))
       ($ :td.px-4.py-3.max-w-md.cursor-pointer.hover:bg-gray-100.rounded
          {:onClick (fn [e]
                      (. e stopPropagation)
                      (state/dispatch [:modal/show :arguments-detail
                                       {:title "Invocation Arguments"
                                        :component ($ common/ContentDetailModal
                                                      {:title "Invocation Arguments"
                                                       :content args-json})}]))}
          ($ :div.truncate.text-gray-900.font-mono
             args-json))
       ($ :td.px-4.py-3.font-mono.text-gray-600 (:graph-version invoke))
       ($ :td.px-4.py-3.text-sm
          ($ result-badge {:status (:status invoke)
                           :human-request? (:human-request? invoke)}))
       (when show-feedback-metric?
         ($ :td.px-4.py-3.text-sm.text-gray-700.font-mono
            (let [metric-values (:feedback-metric-values invoke)
                  v (when feedback-metric-name
                      (get metric-values feedback-metric-name))]
              (if (nil? v) "-" (str v))))))))

(defui index []
  (let [{:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:agents]
                                  :sente-event [:agents/get-all]
                                  :refetch-interval-ms 2000})]

    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading agents..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading agents: " error))
      (empty? data) ($ :div.flex.justify-center.items-center.py-8
                       ($ :div.text-gray-500 "No agents found"))
      :else ($ :div.p-4
               ($ :div {:className "inline-block bg-white shadow sm:rounded-md"}
                  ($ :table {:className "divide-y divide-gray-200"}
                     ($ :thead {:className (:thead common/table-classes)}
                        ($ :tr
                           ($ :th {:className (:th common/table-classes)} "Module")
                           ($ :th {:className (:th common/table-classes)} "Agent")))
                     ($ :tbody
                        (let [sorted-agents (sort-by
                                             (fn [agent]
                                               (let [module-name (:module-id agent)
                                                     decoded-module (common/url-decode module-name)
                                                     agent-name (:agent-name agent)
                                                     decoded-agent (common/url-decode agent-name)]
                                                  ;; Sort by: 1) module name, 2) underscore-prefixed agents last, 3) agent name
                                                 [decoded-module (str/starts-with? decoded-agent "_") decoded-agent]))
                                             data)]
                          (into []
                                (for [agent sorted-agents
                                      :let [module (common/url-decode (:module-id agent))
                                            agent-name (common/url-decode (:agent-name agent))
                                            href (str "/agents/" (common/url-encode (:module-id agent)) "/agent/" (common/url-encode (:agent-name agent)))]]
                                  ($ :tr {:key href :className "hover:bg-gray-50 cursor-pointer"
                                          :onClick (fn [_]
                                                     (rfe/push-state :agent/detail
                                                                     {:module-id (:module-id agent)
                                                                      :agent-name (:agent-name agent)}))}
                                     ($ :td {:className (:td common/table-classes)} module)
                                     ($ :td {:className (:td common/table-classes)} agent-name))))))))))))

(defui invocations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        query-params (state/use-sub [:route :query-params])
        filters-query-param (:filters query-params)
        default-draft-filters {:node-names []
                               :node-current ""
                               :latency-min ""
                               :latency-max ""
                               :error-filter "all"
                               :source "EXPERIMENT"
                               :source-not? true
                               :feedback-current {:metric-name ""
                                                  :metric-type "numeric"
                                                  :match-any-value? false
                                                  :allowed-values []
                                                  :comparator "<="
                                                  :value ""
                                                  :source "any"}
                               :feedback-metrics [{:metric-name ""
                                                   :metric-type "numeric"
                                                   :match-any-value? false
                                                   :allowed-values []
                                                   :comparator "<="
                                                   :value ""
                                                   :source "any"}]}
        default-applied-filters {:source "EXPERIMENT"
                                 :source-not? true}
        encode-filters-param
        (fn [filters]
          (try
            (-> filters
                clj->js
                js/JSON.stringify
                js/btoa)
            (catch js/Error _
              nil)))
        decode-filters-param
        (fn [encoded]
          (try
            (-> encoded
                js/atob
                js/JSON.parse
                (js->clj :keywordize-keys true))
            (catch js/Error _
              nil)))
        normalize-applied-filters
        (fn [filters]
          (let [raw-filters (or filters {})
                feedback-metrics
                (->> (or (:feedback-metrics raw-filters) [])
                     (keep (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                             (let [metric-name (str/trim (str (or metric-name "")))
                                   normalized-type (keyword (name (or metric-type :numeric)))
                                   match-any-value? (boolean match-any-value?)
                                   value (str/trim (str (or value "")))
                                   normalized-allowed-values (->> (or allowed-values [])
                                                                  (map str)
                                                                  (map str/trim)
                                                                  (remove str/blank?)
                                                                  vec)]
                               (when (not (str/blank? metric-name))
                                 (cond
                                   match-any-value?
                                   (cond-> {:metric-name metric-name
                                            :metric-type normalized-type
                                            :match-any-value? true}
                                     (some? source)
                                     (assoc :source (keyword (name source))))

                                   (= normalized-type :categorical)
                                   (when (seq normalized-allowed-values)
                                     (cond-> {:metric-name metric-name
                                              :metric-type :categorical
                                              :allowed-values normalized-allowed-values}
                                       (some? source)
                                       (assoc :source (keyword (name source)))))

                                   (not (str/blank? value))
                                   (cond-> {:metric-name metric-name
                                            :metric-type :numeric
                                            :comparator (keyword (name (or comparator :<=)))
                                            :value value}
                                     (some? source)
                                     (assoc :source (keyword (name source)))))))))
                     vec)
                node-names
                (->> (or (:node-names raw-filters) [])
                     (map str)
                     (map str/trim)
                     (remove str/blank?)
                     vec)]
            (cond-> {}
              (seq node-names)
              (assoc :node-names node-names)

              (some? (get-in raw-filters [:latency-ms :min]))
              (assoc-in [:latency-ms :min] (get-in raw-filters [:latency-ms :min]))

              (some? (get-in raw-filters [:latency-ms :max]))
              (assoc-in [:latency-ms :max] (get-in raw-filters [:latency-ms :max]))

              (contains? raw-filters :has-error?)
              (assoc :has-error? (:has-error? raw-filters))

              (some? (:source raw-filters))
              (assoc :source (str (:source raw-filters)))

              (:source-not? raw-filters)
              (assoc :source-not? true)

              (seq feedback-metrics)
              (assoc :feedback-metrics feedback-metrics))))
        applied->draft-filters
        (fn [applied]
          (let [filters (or applied {})]
            (assoc default-draft-filters
                   :node-names (vec (or (:node-names filters) []))
                   :node-current ""
                   :latency-min (let [v (get-in filters [:latency-ms :min])]
                                  (if (some? v) (str v) ""))
                   :latency-max (let [v (get-in filters [:latency-ms :max])]
                                  (if (some? v) (str v) ""))
                   :error-filter (cond
                                   (true? (:has-error? filters)) "errors-only"
                                   (false? (:has-error? filters)) "no-errors"
                                   :else "all")
                   :source (or (:source filters) "all")
                   :source-not? (boolean (:source-not? filters))
                   :feedback-current {:metric-name ""
                                      :metric-type "numeric"
                                      :match-any-value? false
                                      :allowed-values []
                                      :comparator "<="
                                      :value ""
                                      :source "any"}
                   :feedback-metrics
                   (if (seq (:feedback-metrics filters))
                     (mapv (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                             {:metric-name (or metric-name "")
                              :metric-type (name (or metric-type :numeric))
                              :match-any-value? (boolean match-any-value?)
                              :allowed-values (vec (or allowed-values []))
                              :comparator (name (or comparator :<=))
                              :value (if (nil? value) "" (str value))
                              :source (if (some? source) (name source) "any")})
                           (:feedback-metrics filters))
                     [{:metric-name ""
                       :metric-type "numeric"
                       :match-any-value? false
                       :allowed-values []
                       :comparator "<="
                       :value ""
                       :source "any"}]))))
        derive-active-filter-types
        (fn [applied]
          (let [filters (or applied {})]
            (vec
             (remove nil?
                     [(when (seq (:node-names filters)) :node)
                      (when (or (some? (get-in filters [:latency-ms :min]))
                                (some? (get-in filters [:latency-ms :max])))
                        :latency)
                      (when (contains? filters :has-error?) :error)
                      (when (or (some? (:source filters))
                                (:source-not? filters))
                        :source)
                      (when (seq (:feedback-metrics filters)) :feedback)]))))
        applied-filters-from-url
        (fn [encoded]
          (if (nil? encoded)
            default-applied-filters
            (if-let [decoded (decode-filters-param encoded)]
              (normalize-applied-filters decoded)
              default-applied-filters)))
        initial-applied-filters
        (applied-filters-from-url filters-query-param)
        filter-type-order [:node :latency :error :source :feedback]
        filter-type-labels {:node "Node"
                            :latency "Latency"
                            :error "Error"
                            :source "Source"
                            :feedback "Feedback"}
        [draft-filters set-draft-filters!] (uix/use-state (applied->draft-filters initial-applied-filters))
        [applied-filters set-applied-filters!] (uix/use-state initial-applied-filters)
        [active-filter-types set-active-filter-types!] (uix/use-state (derive-active-filter-types initial-applied-filters))
        [open-filter-editor set-open-filter-editor!] (uix/use-state nil)
        filter-key (common/to-json applied-filters)
        filter-options-query
        (queries/use-sente-query
         {:query-key [:invocations/filter-options module-id agent-name]
          :sente-event [:invocations/get-filter-options {:module-id module-id
                                                         :agent-name agent-name}]
          :enabled? (boolean (and module-id agent-name))
          :refetch-interval-ms 30000})
        filter-options-data (:data filter-options-query)
        node-options (or (:nodes filter-options-data) [])
        raw-feedback-metric-options (or (:feedback-metrics filter-options-data) [])
        feedback-metric-options
        (->> raw-feedback-metric-options
             (keep (fn [item]
                     (let [name-value (cond
                                        (string? item) item
                                        :else (or (:name item)
                                                  (get item "name")
                                                  (:metric-name item)
                                                  (get item "metric-name")))
                           metric-value (when-not (string? item)
                                          (or (:metric item)
                                              (get item "metric")))
                           normalized-name (some-> name-value str str/trim)]
                       (when (and normalized-name (not (str/blank? normalized-name)))
                         {:name normalized-name
                          :metric metric-value}))))
             vec)
        metric-item->type
        (fn [metric-item]
          (let [metric (:metric metric-item)]
            (if (or (contains? metric :categories)
                    (some? (get metric "categories")))
              "categorical"
              "numeric")))
        metric-item->categories
        (fn [metric-item]
          (let [metric (:metric metric-item)]
            (->> (or (:categories metric)
                     (get metric "categories")
                     [])
                 (map str)
                 sort
                 vec)))
        feedback-metric-options-by-name (into {}
                                              (map (fn [metric] [(:name metric) metric]))
                                              feedback-metric-options)
        feedback-metric-option-names (mapv :name feedback-metric-options)
        applied-node-names (or (:node-names applied-filters) [])
        applied-feedback-metrics (or (:feedback-metrics applied-filters) [])
        selected-feedback-metric-name (when (= 1 (count applied-feedback-metrics))
                                        (:metric-name (first applied-feedback-metrics)))
        show-feedback-metric-column? (some? selected-feedback-metric-name)

        parse-filter-value
        (fn [s]
          (let [trimmed (str/trim (or s ""))
                parsed (js/Number trimmed)]
            (if (or (str/blank? trimmed) (js/isNaN parsed))
              trimmed
              parsed)))
        build-filter-map
        (fn [f]
          (let [latency-min (parse-filter-value (:latency-min f))
                latency-max (parse-filter-value (:latency-max f))
                node-names (->> (or (:node-names f) [])
                                (map str/trim)
                                (remove str/blank?)
                                vec)
                feedback-metrics
                (->> (or (:feedback-metrics f) [])
                     (keep (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                             (let [metric-name (str/trim (or metric-name ""))
                                   feedback-type (keyword (or metric-type "numeric"))
                                   match-any-value? (boolean match-any-value?)
                                   value (str/trim (or value ""))
                                   allowed-values (->> (or allowed-values [])
                                                       (map str)
                                                       (map str/trim)
                                                       (remove str/blank?)
                                                       vec)]
                               (when (not (str/blank? metric-name))
                                 (cond
                                   match-any-value?
                                   (cond-> {:metric-name metric-name
                                            :metric-type feedback-type
                                            :match-any-value? true}
                                     (not= "any" source)
                                     (assoc :source (keyword source)))

                                   (= feedback-type :categorical)
                                   (when (seq allowed-values)
                                     (cond-> {:metric-name metric-name
                                              :metric-type :categorical
                                              :allowed-values allowed-values}
                                       (not= "any" source)
                                       (assoc :source (keyword source))))

                                   (not (str/blank? value))
                                   (cond-> {:metric-name metric-name
                                            :metric-type :numeric
                                            :comparator (keyword comparator)
                                            :value value}
                                     (not= "any" source)
                                     (assoc :source (keyword source))))
                                       
                                       ))))
                     vec)]
            (cond-> {}
              (seq node-names)
              (assoc :node-names node-names)

              (number? latency-min)
              (assoc-in [:latency-ms :min] latency-min)

              (number? latency-max)
              (assoc-in [:latency-ms :max] latency-max)

              (= "errors-only" (:error-filter f))
              (assoc :has-error? true)

              (= "no-errors" (:error-filter f))
              (assoc :has-error? false)

              (not= "all" (:source f))
              (assoc :source (:source f))

              (and (not= "all" (:source f))
                   (:source-not? f))
              (assoc :source-not? true)

              (seq feedback-metrics)
              (assoc :feedback-metrics feedback-metrics))))

        clear-filter-type!
        (fn [filter-type]
          (set-draft-filters!
           (fn [prev]
             (let [next-draft (case filter-type
                                :node (assoc prev :node-names [] :node-current "")
                                :latency (assoc prev :latency-min "" :latency-max "")
                                :error (assoc prev :error-filter "all")
                                :source (assoc prev :source "all" :source-not? false)
                                :feedback (assoc prev
                                                 :feedback-current {:metric-name ""
                                                                    :metric-type "numeric"
                                                                    :match-any-value? false
                                                                    :allowed-values []
                                                                    :comparator "<="
                                                                    :value ""
                                                                    :source "any"}
                                                 :feedback-metrics [{:metric-name ""
                                                                     :metric-type "numeric"
                                                                     :match-any-value? false
                                                                     :allowed-values []
                                                                     :comparator "<="
                                                                     :value ""
                                                                     :source "any"}])
                                prev)]
               (set-applied-filters! (build-filter-map next-draft))
               next-draft)))
          (set-active-filter-types!
           (fn [types]
             (vec (remove #(= % filter-type) types))))
          (when (= filter-type (:filter-type open-filter-editor))
            (set-open-filter-editor! nil)))
        add-filter-type!
        (fn [filter-type]
          (set-active-filter-types!
           (fn [types]
             (if (some #(= % filter-type) types)
               types
               (conj (vec types) filter-type))))
          (case filter-type
            :node
            (do
              (set-draft-filters! (fn [prev] (assoc prev :node-current "")))

              (set-open-filter-editor! {:chip-id "new-node"
                                        :filter-type :node
                                        :mode :new}))

            :feedback
            (do
              (set-draft-filters! (fn [prev]
                                    (assoc prev
                                           :feedback-current {:metric-name ""
                                                              :metric-type "numeric"
                                                              :match-any-value? false
                                                              :allowed-values []
                                                              :comparator "<="
                                                              :value ""
                                                              :source "any"})))
              (set-open-filter-editor! {:chip-id "new-feedback"
                                        :filter-type :feedback
                                        :mode :new}))

            (set-open-filter-editor! {:chip-id (str "singleton-" (name filter-type))
                                      :filter-type filter-type
                                      :mode :edit})))
        active-filter-chips
        (vec
         (concat
          (map-indexed
           (fn [idx node-name]
             {:chip-id (str "node-" idx "-" node-name)
              :filter-type :node
              :description node-name
              :node-idx idx
              :node-name node-name})
           applied-node-names)
          (when (contains? applied-filters :latency-ms)
            [{:chip-id "latency"
              :filter-type :latency
              :description (let [mn (get-in applied-filters [:latency-ms :min])
                                 mx (get-in applied-filters [:latency-ms :max])]
                             (cond
                               (and (some? mn) (some? mx)) (str mn "ms-" mx "ms")
                               (some? mn) (str ">= " mn "ms")
                               (some? mx) (str "<= " mx "ms")
                               :else "Any latency"))}])
          (when (contains? applied-filters :has-error?)
            [{:chip-id "error"
              :filter-type :error
              :description (if (:has-error? applied-filters) "Errors only" "No errors")}])
          (when (contains? applied-filters :source)
            [{:chip-id "source"
              :filter-type :source
              :description (str (if (:source-not? applied-filters) "!=" "=")
                                " "
                                ((fn [source]
                                   (case source
                                     "MANUAL" "Manual"
                                     "EXPERIMENT" "Experiment"
                                     source))
                                 (:source applied-filters)))}])
          (map-indexed
           (fn [idx {:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
             (let [comparator-label (name (or comparator :<=))
                   source-label (when (some? source)
                                  (name source))
                   display-source (when (and source-label
                                             (not= source-label "any"))
                                    (str " (" source-label ")"))]
               {:chip-id (str "feedback-" idx "-" metric-name "-" metric-type "-" comparator "-" value "-" source "-" (common/to-json allowed-values))
                :filter-type :feedback
                :feedback-idx idx
                :description (cond
                               match-any-value?
                               (str metric-name " (any value)" display-source)

                               (= metric-type :categorical)
                               (str metric-name " in ["
                                    (str/join ", " (or allowed-values []))
                                    "]"
                                    display-source)
                               :else
                               (str metric-name " " comparator-label " " value display-source))}))
           applied-feedback-metrics)))
        remove-filter-chip!
        (fn [{:keys [filter-type node-idx feedback-idx]}]
          (cond
            (or (= filter-type :node) (= filter-type "node"))
            (let [remove-node (fn [f]
                                (update f :node-names
                                        (fn [names]
                                          (->> (map-indexed vector (or names []))
                                               (remove (fn [[idx _]] (= idx node-idx)))
                                               (mapv second)))))]
              (set-draft-filters!
               (fn [prev]
                 (let [next-draft (remove-node prev)
                       next-applied (build-filter-map next-draft)]
                   (set-applied-filters! next-applied)
                   (set-active-filter-types! (derive-active-filter-types next-applied))
                   next-draft))))

            (or (= filter-type :feedback) (= filter-type "feedback"))
            (let [remove-feedback (fn [f]
                                    (update f :feedback-metrics
                                            (fn [rows]
                                              (->> (map-indexed vector (or rows []))
                                                   (remove (fn [[idx _]] (= idx feedback-idx)))
                                                   (mapv second)))))]
              (set-draft-filters!
               (fn [prev]
                 (let [next-draft (remove-feedback prev)
                       next-applied (build-filter-map next-draft)]
                   (set-applied-filters! next-applied)
                   (set-active-filter-types! (derive-active-filter-types next-applied))
                   next-draft))))

            (or (= filter-type :source) (= filter-type "source"))
            (clear-filter-type! :source)

            (or (= filter-type :latency) (= filter-type "latency"))
            (clear-filter-type! :latency)

            (or (= filter-type :error) (= filter-type "error"))
            (clear-filter-type! :error)

            :else
            nil))
        open-filter-chip!
        (fn [{:keys [chip-id filter-type node-name node-idx feedback-idx]}]
          (if (= chip-id (:chip-id open-filter-editor))
            (set-open-filter-editor! nil)
            (do
              (case filter-type
                :node
                (set-draft-filters! (fn [prev] (assoc prev :node-current node-name)))

                :feedback
                (let [metric (or (get applied-feedback-metrics feedback-idx)
                                 {:metric-name ""
                                  :metric-type :numeric
                                  :match-any-value? false
                                  :allowed-values []
                                  :comparator :<=
                                  :value ""
                                  :source :any})
                      metric-name (:metric-name metric)
                      metric-option (get feedback-metric-options-by-name metric-name)
                      metric-type (keyword (name (or (:metric-type metric)
                                                     (some-> metric-option metric-item->type keyword)
                                                     :numeric)))
                      metric-categories (or (some-> metric-option metric-item->categories)
                                            [])]
                  (set-draft-filters!
                   (fn [prev]
                     (assoc prev
                            :feedback-current {:metric-name (or metric-name "")
                                               :metric-type (name metric-type)
                                               :match-any-value? (boolean (:match-any-value? metric))
                                               :allowed-values (vec (or (:allowed-values metric) []))
                                               :categories metric-categories
                                               :comparator (name (or (:comparator metric) :<=))
                                               :value (str (or (:value metric) ""))
                                               :source (name (or (:source metric) :any))}))))

                nil)
              (set-open-filter-editor! {:chip-id chip-id
                                        :filter-type filter-type
                                        :mode :edit
                                        :node-idx node-idx
                                        :node-name node-name
                                        :feedback-idx feedback-idx}))))
        add-filter-items
        (map (fn [filter-type]
               {:key (name filter-type)
                :label (get filter-type-labels filter-type)
                :disabled? (and (not (#{:node :feedback} filter-type))
                                (boolean (some #(= % filter-type) active-filter-types)))
                :on-select #(add-filter-type! filter-type)})
             filter-type-order)
        apply-open-filter! (fn []
                             (let [{:keys [filter-type mode node-idx feedback-idx]} open-filter-editor
                                   next-draft
                                   (case filter-type
                                     :node
                                     (let [selected (str/trim (or (:node-current draft-filters) ""))]
                                       (if (str/blank? selected)
                                         draft-filters
                                         (if (= mode :edit)
                                           (update draft-filters
                                                   :node-names
                                                   (fn [names]
                                                     (mapv (fn [idx n]
                                                             (if (= idx node-idx) selected n))
                                                           (range)
                                                           (vec (or names [])))))
                                           (update draft-filters
                                                   :node-names
                                                   (fn [names]
                                                     (conj (vec (or names [])) selected))))))

                                     :feedback
                                     (let [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]} (:feedback-current draft-filters)
                                           metric-name (str/trim (or metric-name ""))
                                           feedback-type (keyword (or metric-type "numeric"))]
                                       match-any-value? (boolean match-any-value?)
                                       value (str/trim (or value ""))
                                       allowed-values (->> (or allowed-values [])
                                                           (map str)
                                                           (map str/trim)
                                                           (remove str/blank?)
                                                           vec)
                                       (if (str/blank? metric-name)
                                         draft-filters
                                         (let [new-row (if match-any-value?
                                                         {:metric-name metric-name
                                                          :metric-type feedback-type
                                                          :match-any-value? true
                                                          :source (or source "any")}
                                                         (if (= feedback-type :categorical)
                                                           {:metric-name metric-name
                                                            :metric-type :categorical
                                                            :allowed-values allowed-values
                                                            :source (or source "any")}
                                                           {:metric-name metric-name
                                                            :metric-type :numeric
                                                            :comparator (or comparator "<=")
                                                            :value value
                                                            :source (or source "any")}))]
                                           (if (or (and (= feedback-type :categorical)
                                                        (empty? allowed-values)
                                                        (not match-any-value?))
                                                   (and (= feedback-type :numeric)
                                                        (str/blank? value)
                                                        (not match-any-value?)))
                                             draft-filters
                                             (update draft-filters
                                                     :feedback-metrics
                                                     (fn [rows]
                                                       (let [base (vec (or rows []))]
                                                         (if (= mode :edit)
                                                           (mapv (fn [idx m]
                                                                   (if (= idx feedback-idx) new-row m))
                                                                 (range)
                                                                 base)
                                                           (conj base new-row))))))))))
                                   next-applied (build-filter-map next-draft)]
                               (set-draft-filters! next-draft)
                               (set-applied-filters! next-applied)
                               (set-active-filter-types! (derive-active-filter-types next-applied))
                               (set-open-filter-editor! nil)))
        _sync-filters-from-url
        (uix/use-effect
         (fn []
           (let [next-applied (applied-filters-from-url filters-query-param)]
             (when (not= next-applied applied-filters)
               (set-applied-filters! next-applied)
               (set-draft-filters! (applied->draft-filters next-applied))
               (set-active-filter-types! (derive-active-filter-types next-applied))))
           js/undefined)
         [filters-query-param])
        _sync-filters-to-url
        (uix/use-effect
         (fn []
           (when (and module-id agent-name)
             (let [encoded (encode-filters-param applied-filters)
                   current-encoded filters-query-param]
               (when (not= encoded current-encoded)
                 (rfe/replace-state :agent/invocations
                                    {:module-id module-id
                                     :agent-name agent-name}
                                    (if encoded
                                      {:filters encoded}
                                      {})))))
           js/undefined)
         [module-id agent-name applied-filters filters-query-param])

        ;; Use the new paginated query hook
        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-paginated-query
         {:query-key [:invocations module-id agent-name filter-key]
          :sente-event [:invocations/get-page {:module-id module-id
                                               :agent-name agent-name
                                               :filters applied-filters}]
          :page-size 20
          :enabled? (boolean (and module-id agent-name))})]

    ($ :div.p-4.space-y-4
       ($ :div.bg-white.rounded-md.border.border-gray-200.p-4.shadow-sm
          ($ :div.flex.flex-wrap.items-center.gap-2
             ($ common/Dropdown
                {:label "Add filter"
                 :display-text "Add filter"
                 :items add-filter-items
                 :full-width? false
                 :data-testid "add-invocations-filter"})
             (if (seq active-filter-chips)
               (for [{:keys [chip-id filter-type description] :as chip} active-filter-chips]
                 ($ :button.inline-flex.items-center.gap-2.px-3.py-1.5.rounded-full.bg-blue-50.text-blue-700.text-xs.font-medium.border.border-blue-200.cursor-pointer.hover:bg-blue-100.transition-colors.duration-150
                    {:key chip-id
                     :type "button"
                     :onClick #(open-filter-chip! chip)}
                    ($ :span (get filter-type-labels filter-type))
                    ($ :span.text-blue-500 description)
                    ($ :span.text-blue-400.hover:text-blue-700.cursor-pointer
                       {:onClick (fn [e]
                                   (.stopPropagation e)
                                   (remove-filter-chip! chip))}
                       "x")))
                ($ :div.text-xs.text-gray-500 "No filters added"))))

          (when open-filter-editor
            ($ :div.mt-3.p-3.border.border-gray-200.rounded-md.bg-gray-50.max-w-2xl
               ($ :div.flex.items-center.justify-between.mb-3
                  ($ :div.text-sm.font-medium.text-gray-800
                     (str (if (= :new (:mode open-filter-editor)) "Add " "Edit ")
                          (get filter-type-labels (:filter-type open-filter-editor))
                          " filter"))
                  ($ :button.text-xs.px-2.py-1.bg-blue-600.text-white.rounded.hover:bg-blue-700.cursor-pointer
                     {:type "button"
                      :data-testid "invocations-filter-apply"
                      :onClick apply-open-filter!}
                     "Apply"))
               (case (:filter-type open-filter-editor)
                 :node
                 ($ :div.space-y-2
                    (if (seq node-options)
                      ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                         {:value (:node-current draft-filters)
                          :data-testid "invocations-filter-node-select"
                          :onChange #(set-draft-filters! (fn [prev]
                                                           (assoc prev :node-current (.. % -target -value))))}
                         ($ :option {:value ""} "Select node")
                         (for [node-name node-options]
                           ($ :option {:key node-name :value node-name} node-name)))
                      ($ :div.text-xs.text-gray-500 "No nodes available"))
                    ($ :div.text-xs.text-gray-500
                       "This filter adds one required node condition."))

                 :latency
                 ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                    ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                       {:type "number"
                        :data-testid "invocations-filter-latency-min"
                        :placeholder "Latency min (ms)"
                        :value (:latency-min draft-filters)
                        :onChange #(set-draft-filters! (fn [prev]
                                                         (assoc prev :latency-min (.. % -target -value))))})
                    ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                       {:type "number"
                        :data-testid "invocations-filter-latency-max"
                        :placeholder "Latency max (ms)"
                        :value (:latency-max draft-filters)
                        :onChange #(set-draft-filters! (fn [prev]
                                                         (assoc prev :latency-max (.. % -target -value))))}))

                 :error
                 ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                    {:value (:error-filter draft-filters)
                     :data-testid "invocations-filter-error-select"
                     :onChange #(set-draft-filters! (fn [prev]
                                                      (assoc prev :error-filter (.. % -target -value))))}
                    ($ :option {:value "all"} "All results")
                    ($ :option {:value "errors-only"} "Errors only")
                    ($ :option {:value "no-errors"} "No errors"))

                 :source
                 ($ :div.space-y-2
                    ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                       {:value (:source draft-filters)
                        :data-testid "invocations-filter-source-select"
                        :onChange #(set-draft-filters! (fn [prev]
                                                         (assoc prev :source (.. % -target -value))))}
                       ($ :option {:value "all"} "All sources")
                       ($ :option {:value "API"} "API")
                       ($ :option {:value "MANUAL"} "Manual")
                       ($ :option {:value "EXPERIMENT"} "Experiment"))
                    ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700
                       ($ :input.h-4.w-4.border.border-gray-300.rounded
                          {:type "checkbox"
                           :data-testid "invocations-filter-source-not"
                           :checked (boolean (:source-not? draft-filters))
                           :disabled (= "all" (:source draft-filters))
                           :onChange #(set-draft-filters! (fn [prev]
                                                            (assoc prev :source-not? (.. % -target -checked))))})
                       ($ :span "Not selected source"))
                    ($ :div.text-xs.text-gray-500
                       "When enabled, returns invokes from all source types except the selected one."))

                 :feedback
                 ($ :div.space-y-2
                    ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                       ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                          {:value (get-in draft-filters [:feedback-current :metric-name])
                           :data-testid "invocations-filter-feedback-metric"
                           :onChange #(set-draft-filters!
                                       (fn [prev]
                                         (let [selected-name (.. % -target -value)
                                               metric-option (get feedback-metric-options-by-name selected-name)
                                               next-type (if metric-option
                                                           (metric-item->type metric-option)
                                                           "numeric")
                                               next-categories (if metric-option
                                                                 (metric-item->categories metric-option)
                                                                 [])]
                                           (assoc prev
                                                  :feedback-current {:metric-name selected-name
                                                                     :metric-type next-type
                                                                     :match-any-value? false
                                                                     :allowed-values []
                                                                     :categories next-categories
                                                                     :comparator "<="
                                                                     :value ""
                                                                     :source (get-in prev [:feedback-current :source] "any")}))))}
                          ($ :option {:value ""} "Select metric")
                          (for [[idx metric-name] (map-indexed vector feedback-metric-option-names)]
                            ($ :option {:key (str "feedback-metric-option-" idx "-" metric-name)
                                        :value metric-name}
                               metric-name)))
                       ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                          {:value (get-in draft-filters [:feedback-current :source])
                           :data-testid "invocations-filter-feedback-source"
                           :onChange #(set-draft-filters!
                                       (fn [prev]
                                         (assoc-in prev [:feedback-current :source] (.. % -target -value))))}
                          ($ :option {:value "any"} "Any source")
                          ($ :option {:value "human"} "Human")
                          ($ :option {:value "non-human"} "Non-human")))
                    ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                       ($ :input.h-4.w-4.border.border-gray-300.rounded
                          {:type "checkbox"
                           :data-testid "invocations-filter-feedback-any-value"
                           :checked (boolean (get-in draft-filters [:feedback-current :match-any-value?]))
                           :onChange #(set-draft-filters!
                                      (fn [prev]
                                        (update-in prev [:feedback-current :match-any-value?] not)))}
                       ($ :span "Match any value (metric exists)"))
                    (let [match-any? (boolean (get-in draft-filters [:feedback-current :match-any-value?]))
                          form-wrapper-class (when match-any?
                                               "opacity-60 pointer-events-none")]
                      ($ :div {:className (str "space-y-2 " (or form-wrapper-class ""))}
                         (when match-any?
                           ($ :div.text-xs.text-gray-600
                              "Matches any invocation that has this metric, regardless of metric value."))
                         (if (= "categorical" (get-in draft-filters [:feedback-current :metric-type]))
                           ($ :div.space-y-2
                              ($ :div.text-xs.text-gray-600 "Select one or more categorical values:")
                              (if (seq (get-in draft-filters [:feedback-current :categories]))
                                ($ :div.max-h-56.overflow-auto.border.border-gray-200.rounded-md.bg-white.p-2.space-y-1
                                   (for [cat-value (get-in draft-filters [:feedback-current :categories])]
                                     ($ :label.flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                                        {:key cat-value}
                                        ($ :input.h-4.w-4.border.border-gray-300.rounded
                                           {:type "checkbox"
                                            :data-testid "invocations-filter-feedback-category-select"
                                            :disabled match-any?
                                            :checked (boolean (some (fn [v] (= v cat-value))
                                                                    (get-in draft-filters [:feedback-current :allowed-values])))
                                            :onChange (fn [e]
                                                        (let [checked? (.. e -target -checked)]
                                                          (set-draft-filters!
                                                           (fn [prev]
                                                             (update-in prev [:feedback-current :allowed-values]
                                                                        (fn [vals]
                                                                          (let [curr (vec (or vals []))]
                                                                            (if checked?
                                                                              (vec (distinct (conj curr cat-value)))
                                                                              (vec (remove (fn [v] (= v cat-value)) curr))))))))))})
                                        ($ :span cat-value))))
                                ($ :div.text-xs.text-gray-500 "No categories available for this metric.")))
                           ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                              ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                                 {:value (get-in draft-filters [:feedback-current :comparator])
                                  :data-testid "invocations-filter-feedback-comparator"
                                  :disabled match-any?
                                  :onChange #(set-draft-filters!
                                              (fn [prev]
                                                (assoc-in prev [:feedback-current :comparator] (.. % -target -value))))}
                                 ($ :option {:value "<="} "<=")
                                 ($ :option {:value "<"} "<")
                                 ($ :option {:value "="} "=")
                                 ($ :option {:value "not="} "!=")
                                 ($ :option {:value ">"} ">")
                                 ($ :option {:value ">="} ">="))
                              ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                                 {:placeholder "Feedback value"
                                  :data-testid "invocations-filter-feedback-value"
                                  :disabled match-any?
                                  :value (get-in draft-filters [:feedback-current :value])
                                  :onChange #(set-draft-filters!
                                              (fn [prev]
                                                (assoc-in prev [:feedback-current :value] (.. % -target -value))))})))
                         ($ :div.text-xs.text-gray-500
                            "This filter adds one feedback condition."))))

                 ($ :div.text-sm.text-gray-500 "Unknown filter")))))

       (cond
         ;; Use isLoading for the initial loading state
         (and isLoading (empty? data))
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "Loading invocations..."))

         error
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-red-500 "Error loading invocations: " error))

         (empty? data)
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "No invocations found"))

         :else
         ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
            ($ :table.w-full.text-sm
               ($ :thead.bg-gray-50.border-b.border-gray-200
                  ($ :tr
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Trace")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Start Time")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")
                     (when show-feedback-metric-column?
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide
                          "Metric value"))))
               ($ :tbody.divide-y.divide-gray-200
                  (for [invoke data]
                    ($ invocation-row {:key (str (:task-id invoke) "-" (:agent-id invoke))
                                       :invoke invoke
                                       :module-id module-id
                                       :agent-name agent-name
                                       :show-feedback-metric? show-feedback-metric-column?
                                       :feedback-metric-name selected-feedback-metric-name
                                       :on-click (fn [url] (set! (.-href (.-location js/window)) url))})))

               ;; Load More button
               (when hasMore
                 ($ :tfoot.bg-gray-50.border-t.border-gray-200
                    ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                       {:onClick (when-not isFetchingMore loadMore)}
                       ($ :td.px-4.py-3.cursor-pointer {:colSpan (if show-feedback-metric-column? 6 5)}
                          ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                             ($ :span.mr-2.text-sm.font-medium (if isFetchingMore "Loading..." "Load More"))
                             (when-not isFetchingMore
                               ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                                  ($ :path {:fillRule "evenodd"
                                            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                            :clipRule "evenodd"}))))))))))))))

(defui mini-invocations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        {:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:mini-invocations module-id agent-name]
                                  :sente-event [:invocations/get-page {:module-id module-id
                                                                       :agent-name agent-name
                                                                       :filters {:source "EXPERIMENT"
                                                                                 :source-not? true}
                                                                       :pagination {}}]
                                  :refetch-interval-ms 2000})]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading invocations..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading invocations: " error))
      (not data) ($ :div.flex.justify-center.items-center.py-8
                    ($ :div.text-gray-500 "No invocations found"))
      :else
      ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
         ($ :table.w-full.text-sm
            ($ :thead.bg-gray-50.border-b.border-gray-200
               ($ :tr
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Trace")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Start Time")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")))
            ($ :tbody.divide-y.divide-gray-200
               (for [invoke (:agent-invokes data)]
                 ($ invocation-row {:key (str (:task-id invoke) "-" (:agent-id invoke))
                                    :invoke invoke
                                    :module-id module-id
                                    :agent-name agent-name
                                    :show-feedback-metric? false
                                    :on-click (fn [url] (set! (.-href (.-location js/window)) url))})))
            ($ :tfoot.bg-gray-50.border-t.border-gray-200
               ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                  {:onClick (fn [_]
                              (set! (.-href (.-location js/window))
                                    (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")))}
                  ($ :td.px-4.py-3.cursor-pointer {:colSpan 5}
                     ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                        ($ :span.mr-2.text-sm.font-medium "View all invocations")
                        ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                           ($ :path {:fillRule "evenodd"
                                     :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                     :clipRule "evenodd"})))))))))))

(defui evaluations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Evaluations")
       ($ :div.text-gray-500 "Evaluations functionality coming soon..."))))

(defui node-stats-panel [{:keys [selected-node module-id agent-name granularity time-label
                                 granularity-items granularity-label stat-items stat-label]}]
  (let [node-id (when selected-node (aget selected-node "id"))
        decoded-agent-name (common/url-decode agent-name)

        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:node-stats module-id agent-name granularity]
          :sente-event [:invocations/get-node-stats {:module-id module-id
                                                     :agent-name decoded-agent-name
                                                     :granularity granularity}]
          :refetch-interval-ms 30000
          :enabled? (boolean (and module-id agent-name))})]

    ($ :div
       ;; Header
       ($ :div.p-4.border-b.border-gray-200
          ($ :h3.text-lg.font-semibold.text-gray-800 "Node Stats"))

       ;; Dropdowns at top of panel
       ($ :div.p-4.border-b.border-gray-200.space-y-3
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Time window:")
             ($ :div
                ($ common/Dropdown
                   {:label "Granularity"
                    :display-text granularity-label
                    :items granularity-items
                    :data-testid "node-granularity-selector"})))
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Show on nodes:")
             ($ :div
                ($ common/Dropdown
                   {:label "Stat"
                    :display-text stat-label
                    :items stat-items
                    :data-testid "node-stat-selector"}))))

       ;; Stats content
       (cond
         (nil? selected-node)
         ($ :div.p-6.text-center.text-gray-500
            "Select a node to view stats")

         loading?
         ($ :div.p-6.text-center
            ($ common/spinner {:size :medium}))

         error
         ($ :div.p-6.text-center.text-red-500
            (str "Error loading stats: " error))

         :else
         (let [node-stats (:node-stats data)
               node-data (get node-stats node-id)]
           (if node-data
             ($ :div.grid.grid-cols-2.gap-4
                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Mean Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:mean node-data) (int (:mean node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Invocations")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (or (:count node-data) 0)))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Min Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:min node-data) (int (:min node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Max Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:max node-data) (int (:max node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P50 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.5) (int (get node-data 0.5))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P90 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.9) (int (get node-data 0.9))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P99 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.99) (int (get node-data 0.99))) "ms"))))
             ($ :div.p-6.text-center.text-gray-500
                (str "No data for \"" node-id "\" at " time-label))))))))

(defui agent-graph [{:keys [selected-node set-selected-node granularity selected-stat]}]
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        decoded-agent-name (common/url-decode agent-name)

        ;; Fetch graph topology
        graph-query (queries/use-sente-query {:query-key [:graph module-id agent-name]
                                              :sente-event [:invocations/get-graph {:module-id module-id
                                                                                    :agent-name agent-name}]
                                              :refetch-interval-ms 2000})
        data (:data graph-query)
        loading? (:loading? graph-query)
        error (:error graph-query)

        ;; Fetch node stats for display on nodes
        stats-query (queries/use-sente-query {:query-key [:node-stats module-id agent-name granularity]
                                              :sente-event [:invocations/get-node-stats {:module-id module-id
                                                                                         :agent-name decoded-agent-name
                                                                                         :granularity granularity}]
                                              :refetch-interval-ms 30000})
        node-stats (:node-stats (:data stats-query))]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading graph..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading graph: " error))
      (nil? (:graph data)) ($ :div.flex.justify-center.items-center.py-8
                              ($ :div.text-gray-500 "No graph available"))
      :else ($ agent-graph/graph {:initial-data data
                                  :height "500px"
                                  :selected-node selected-node
                                  :set-selected-node set-selected-node
                                  :node-stats node-stats
                                  :selected-stat selected-stat}))))

(defui stats-summary [{:keys [module-id agent-name]}]
  ($ :div.p-4.flex.gap-1
     ($ :a
        {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/stats")
         :style {:flex-grow "1"}}
        ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6.hover:shadow-md.transition-shadow.duration-150.cursor-pointer.relative
           ($ :div.flex.justify-between.items-start
              ($ :div
                 ($ :div.text-sm.font-medium.text-gray-600.mb-3 "Last 10,000 runs")
                 ($ :div.flex.flex-row.gap-4
                    ($ :div.flex.flex-col
                       ($ :span.text-xs.text-gray-500.uppercase.tracking-wide "Avg Tokens")
                       ($ :span.text-lg.font-semibold.text-gray-900 "1,247.3"))
                    ($ :div.flex.flex-col
                       ($ :span.text-xs.text-gray-500.uppercase.tracking-wide "Avg Latency")
                       ($ :span.text-lg.font-semibold.text-gray-900 "342ms"))))
              ($ :div.text-gray-400.hover:text-gray-600.transition-colors.duration-150
                 ($ :svg.w-5.h-5 {:viewBox "0 0 20 20" :fill "currentColor"}
                    ($ :path {:fillRule "evenodd"
                              :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                              :clipRule "evenodd"}))))))))

(defui alerts [{:keys [module-id agent-name]}]
  (let [dummy-alerts [{:metric "Error Rate" :value "2.3%" :threshold "< 5%" :time-ago "2h ago"}
                      {:metric "Latency" :value "847ms" :threshold "< 500ms" :time-ago "4h ago"}
                      {:metric "Error Rate" :value "8.1%" :threshold "< 5%" :time-ago "1d ago"}]]
    ($ :div.p-4.flex.gap-1
       ($ :a
          {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/alerts")
           :style {:flex-grow "1"}}
          ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6.hover:shadow-md.transition-shadow.duration-150.cursor-pointer.relative
             ($ :div.flex.justify-between.items-start
                ($ :div.w-full
                   ($ :div.text-sm.font-medium.text-gray-600.mb-3 "Recent Alerts")
                   ($ :div.space-y-3
                      (for [alert dummy-alerts]
                        ($ :div.flex.justify-between.items-center.text-sm.pb-2.border-b.border-gray-100.last:border-b-0.last:pb-0 {:key (str (:metric alert) (:time-ago alert))}
                           ($ :div.flex-1
                              ($ :div.font-semibold.text-red-600 (:metric alert))
                              ($ :div.text-xs.text-gray-500.mt-1 (str (:value alert) " (threshold: " (:threshold alert) ")")))
                           ($ :div.text-xs.text-gray-400.text-right.ml-3 (:time-ago alert))))))
                ($ :div.text-gray-400.hover:text-gray-600.transition-colors.duration-150.ml-2
                   ($ :svg.w-5.h-5 {:viewBox "0 0 20 20" :fill "currentColor"}
                      ($ :path {:fillRule "evenodd"
                                :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                :clipRule "evenodd"})))))))))

(defui manual-run [{:keys [form-id]}]
  (let [form (forms/use-form form-id)
        args-field (forms/use-form-field form-id :args)
        metadata-field (forms/use-form-field form-id :metadata-args)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))
        is-blank (str/blank? (:value args-field))]

    ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6
       ($ :form {:onSubmit handle-submit}
          ($ :div.text-sm.font-medium.text-gray-600.mb-4 "Manually Run Agent")
          ($ :div.flex.gap-3.justify-between
             ;; Arguments textarea with live validation
             ($ :div.flex-1.flex.flex-col
                ($ :textarea
                   {:className (str "flex-1 p-3 border rounded-md text-sm placeholder-gray-400 focus:ring-2 transition-colors duration-150 resize-none "
                                    (if (and (not is-blank) (:error args-field))
                                      "border-red-300 focus:ring-red-500 focus:border-red-500"
                                      "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                    :placeholder "[arg1, arg2, arg3, ...] (json)"
                    :value (or (:value args-field) "")
                    :onChange #((:on-change args-field) (.. % -target -value))
                    :rows 3
                    :disabled (:submitting? form)})
                ;; Always render error container to prevent layout shift
                ($ :div.text-sm.text-red-600.mt-1 {:style {:min-height "1.25rem"}}
                   (if is-blank
                     ""
                     (or (:error args-field) ""))))

             ;; Metadata textarea with live validation
             ($ :div.flex-1.flex.flex-col
                ($ :textarea
                   {:className (str "flex-1 p-3 border rounded-md text-sm placeholder-gray-400 focus:ring-2 transition-colors duration-150 resize-none "
                                    (if (:error metadata-field)
                                      "border-red-300 focus:ring-red-500 focus:border-red-500"
                                      "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                    :placeholder "Metadata (JSON map, optional)"
                    :value (or (:value metadata-field) "")
                    :onChange #((:on-change metadata-field) (.. % -target -value))
                    :rows 3
                    :disabled (:submitting? form)})
                ;; Always render error container to prevent layout shift
                ($ :div.text-sm.text-red-600.mt-1 {:style {:min-height "1.25rem"}}
                   (or (:error metadata-field) "")))

             ;; Submit button
             ($ :button
                {:type "submit"
                 :disabled (or (not (:valid? form)) (:submitting? form))
                 :className (if (or (not (:valid? form)) (:submitting? form))
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-not-allowed transition-colors duration-150 bg-gray-400"
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-pointer transition-colors duration-150 bg-blue-600 hover:bg-blue-700")}
                (if (:submitting? form) "Running..." "Submit"))))

       ;; Show form-level errors only (success navigates to trace)
       (when (:error form)
         ($ :div.mt-4.p-3.rounded-md.bg-red-50.border.border-red-200
            ($ :div.text-red-700.text-sm (:error form)))))))

(defui agent []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        ;; Use a simple keyword for the form-id (schema expects Keyword, not vector)
        form-id :manual-run-agent

        ;; State for selected node and graph controls
        [selected-node set-selected-node] (uix/use-state nil)
        [granularity set-granularity] (uix/use-state 3600) ;; hour granularity = last hour
        [selected-stat set-selected-stat] (uix/use-state :mean) ;; default to mean

        ;; Granularity options
        ;; Granularity selector - each bucket is already aggregated by telemetry
        granularity-items [{:key 3600 :label "Last Hour" :selected? (= granularity 3600) :on-select #(set-granularity 3600)}
                           {:key 86400 :label "Last Day" :selected? (= granularity 86400) :on-select #(set-granularity 86400)}
                           {:key 2592000 :label "Last Month" :selected? (= granularity 2592000) :on-select #(set-granularity 2592000)}]

        ;; Time label for display
        time-label (condp = granularity
                     3600 "Last Hour"
                     86400 "Last Day"
                     2592000 "Last Month"
                     "Recent")

        ;; Stat selector options
        stat-items [{:key :mean :label "Mean" :selected? (= selected-stat :mean) :on-select #(set-selected-stat :mean)}
                    {:key :count :label "Count" :selected? (= selected-stat :count) :on-select #(set-selected-stat :count)}
                    {:key :min :label "Min" :selected? (= selected-stat :min) :on-select #(set-selected-stat :min)}
                    {:key :max :label "Max" :selected? (= selected-stat :max) :on-select #(set-selected-stat :max)}
                    {:key 0.25 :label "P25" :selected? (= selected-stat 0.25) :on-select #(set-selected-stat 0.25)}
                    {:key 0.5 :label "P50" :selected? (= selected-stat 0.5) :on-select #(set-selected-stat 0.5)}
                    {:key 0.75 :label "P75" :selected? (= selected-stat 0.75) :on-select #(set-selected-stat 0.75)}
                    {:key 0.9 :label "P90" :selected? (= selected-stat 0.9) :on-select #(set-selected-stat 0.9)}
                    {:key 0.99 :label "P99" :selected? (= selected-stat 0.99) :on-select #(set-selected-stat 0.99)}]

        granularity-label (or (:label (first (filter :selected? granularity-items))) "Last Hour")
        stat-label (or (:label (first (filter :selected? stat-items))) "Mean")]

    ;; Initialize the form when the component mounts or when module-id/agent-name changes
    (uix/use-effect
     (fn []
       (state/dispatch [:form/initialize form-id {:module-id module-id
                                                  :agent-name agent-name}])
       ;; Cleanup: Clear the form when the component unmounts or agent changes
       (fn []
         (state/dispatch [:form/clear form-id])))
     [module-id agent-name form-id])

    ($ :div.p-4

       ($ :div.flex.gap-4
          ($ :div {:className "w-1/2"}
             ($ agent-graph {:selected-node selected-node
                             :set-selected-node set-selected-node
                             :granularity granularity
                             :selected-stat selected-stat}))
          ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm {:className "w-1/2"}
             ($ node-stats-panel {:selected-node selected-node
                                  :module-id module-id
                                  :agent-name agent-name
                                  :granularity granularity
                                  :time-label time-label
                                  :granularity-items granularity-items
                                  :granularity-label granularity-label
                                  :stat-items stat-items
                                  :stat-label stat-label})))
       ($ :div.p-4.flex.gap-1
          ($ :div
             {:style {:flex-grow "1"}}
             ($ manual-run {:form-id form-id})))

       ($ :div.p-4
          ($ mini-invocations)))))

(defui invoke []
  (let [{:keys [module-id agent-name invoke-id]} (state/use-sub [:route :path-params])]

    ($ :div
       ;; Graph content
       ($ :div.bg-white.p-6.rounded-lg.shadow.mt-4
          ($ invocation-page/invocation-page)))))
