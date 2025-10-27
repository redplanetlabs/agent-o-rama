(ns com.rpl.agent-o-rama.ui.analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.chart :as chart]
   [clojure.string :as str]
   [clojure.set :as set]
   ["use-debounce" :refer [useDebounce]]
   ["@heroicons/react/24/outline" :refer [ChartBarIcon
                                          ChevronLeftIcon
                                          ChevronRightIcon]]))

;; Granularity configurations
(def granularities
  [{:id :minute
    :label "Minute"
    :seconds 60
    :buckets 60
    :format-fn (fn [ms] (.toLocaleString (js/Date. ms)
                                         "en-US"
                                         #js {:month "short"
                                              :day "numeric"
                                              :hour "numeric"
                                              :minute "2-digit"
                                              :hour12 true}))}
   {:id :hour
    :label "Hour"
    :seconds 3600
    :buckets 60
    :format-fn (fn [ms] (.toLocaleString (js/Date. ms)
                                         "en-US"
                                         #js {:month "short"
                                              :day "numeric"
                                              :hour "numeric"
                                              :minute "2-digit"
                                              :hour12 true}))}
   {:id :day
    :label "Day"
    :seconds 86400
    :buckets 60
    :format-fn (fn [ms] (.toLocaleString (js/Date. ms)
                                         "en-US"
                                         #js {:month "short"
                                              :day "numeric"
                                              :year "numeric"}))}
   {:id :30-day
    :label "30-Day"
    :seconds (* 30 86400)
    :buckets 60
    :format-fn (fn [ms] (.toLocaleString (js/Date. ms)
                                         "en-US"
                                         #js {:month "short"
                                              :day "numeric"
                                              :year "numeric"}))}])

;; Chart configurations for all static analytics charts
(def chart-configs
  "Configuration for all analytics charts to display.
  Each chart specifies:
  - :id - Unique identifier
  - :title - Display title
  - :description - Description of what the chart shows
  - :variant - Chart variant: :single-metric, :multi-metric, :percentage, :multi-category, :computed-percentage
  - :metric-id - The metric ID to query (e.g., [:agent :latency])
  - :metrics-set - Set of metric keys to request (e.g., #{:count}, #{:min 0.5 :max})
  - :variant-opts - Options passed to the unified chart component
  - :y-label - Y-axis label
  - :color - Optional color override for single-series charts"
  [;; 1. Agent Invokes
   {:id :agent-invokes
    :title "Agent invokes"
    :description "Total number of agent runs per time bucket"
    :variant :single-metric
    :metric-id [:agent :success-rate]
    :metrics-set #{:count}
    :variant-opts {:metric-key :count}
    :y-label "Count"
    :color "#6366f1"} ; indigo

   ;; 2. Agent Success Rate
   {:id :agent-success-rate
    :title "Agent success rate"
    :description "Percentage of successful agent runs per time bucket"
    :variant :percentage
    :metric-id [:agent :success-rate]
    :metrics-set #{:mean}
    :variant-opts {:metric-key :mean}
    :color "#10b981"} ; green

   ;; 3. Agent Latency
   {:id :agent-latency
    :title "Agent latency"
    :description "Distribution of end-to-end agent execution time"
    :variant :multi-metric
    :metric-id [:agent :latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 4. Total Model Calls
   {:id :total-model-calls
    :title "Total model calls"
    :description "Sum of all LLM calls made by agents in each time bucket"
    :variant :single-metric
    :metric-id [:agent :model-call-count]
    :metrics-set #{:rest-sum}
    :variant-opts {:metric-key :rest-sum}
    :y-label "Total Calls"
    :color "#8b5cf6"} ; purple

   ;; 5. Model Calls Per Agent Invoke
   {:id :model-calls-per-invoke
    :title "Model calls per agent invoke"
    :description "Distribution of LLM calls per agent run"
    :variant :multi-metric
    :metric-id [:agent :model-call-count]
    :metrics-set #{:min 0.25 0.5 0.75 :max}
    :variant-opts {:metrics #{:min 0.25 0.5 0.75 :max}}
    :y-label "Calls per Invoke"}

   ;; 6. Model Latency
   {:id :model-latency
    :title "Model latency"
    :description "Distribution of individual LLM call latency"
    :variant :multi-metric
    :metric-id [:agent :model-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 7. Store Read Latency
   {:id :store-read-latency
    :title "Store read latency"
    :description "Distribution of store read operation latency"
    :variant :multi-metric
    :metric-id [:agent :store-read-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 8. Store Write Latency
   {:id :store-write-latency
    :title "Store write latency"
    :description "Distribution of store write operation latency"
    :variant :multi-metric
    :metric-id [:agent :store-write-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 9. Database Read Latency
   {:id :db-read-latency
    :title "Database read latency"
    :description "Distribution of database read operation latency"
    :variant :multi-metric
    :metric-id [:agent :db-read-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 10. Database Write Latency
   {:id :db-write-latency
    :title "Database write latency"
    :description "Distribution of database write operation latency"
    :variant :multi-metric
    :metric-id [:agent :db-write-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Latency (ms)"}

   ;; 11. Time to First Token (Agent)
   {:id :agent-first-token
    :title "Time to first token (agent)"
    :description "Distribution of time until first token in agent response"
    :variant :multi-metric
    :metric-id [:agent :first-token-time]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Time (ms)"}

   ;; 12. Time to First Token (Model Call)
   {:id :model-first-token
    :title "Time to first token (individual model call)"
    :description "Distribution of time until first token in individual LLM calls"
    :variant :multi-metric
    :metric-id [:agent :model-first-token-time]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :variant-opts {:metrics #{:min 0.5 0.9 0.99 :max}}
    :y-label "Time (ms)"}

   ;; 13. Token Usage (Multi-category)
   {:id :token-usage
    :title "Token usage"
    :description "Total tokens consumed by LLM calls over time"
    :variant :multi-category
    :metric-id [:agent :token-counts]
    :metrics-set #{:rest-sum}
    :variant-opts {:metric-key :rest-sum
                   :categories ["input" "output" "total"]}
    :y-label "Tokens"}

   ;; 14. Token Usage Per Agent Invoke
   {:id :token-usage-per-invoke
    :title "Token usage per agent invoke"
    :description "Distribution of token consumption per agent run"
    :variant :multi-category
    :metric-id [:agent :token-counts]
    :metrics-set #{:min 0.25 0.5 0.75 :max}
    :variant-opts {:metric-key :min
                   :categories ["input" "output" "total"]}
    :y-label "Tokens"}

   ;; 15. Model Success Rate (Special calculation)
   {:id :model-success-rate
    :title "Model success rate"
    :description "Percentage of successful individual LLM calls"
    :variant :computed-percentage
    :metric-id [:agent :model-success-rate]
    :metrics-set #{:rest-sum}
    :variant-opts {:metric-key :rest-sum}
    :color "#10b981"}])

(defn calculate-time-window
  "Calculate start and end times for the time window.
   If offset is 0, this is 'live' mode (most recent 60 buckets).
   Negative offset moves backward in time."
  [granularity-seconds offset]
  (let [now-millis (.now js/Date)
        granularity-millis (* granularity-seconds 1000)
        current-bucket (js/Math.floor (/ now-millis granularity-millis))
        ;; Apply offset (negative moves back in time)
        end-bucket (+ current-bucket offset)
        start-bucket (- end-bucket 59)
        start-time-millis (* start-bucket granularity-millis)
        end-time-millis (* (inc end-bucket) granularity-millis)]
    {:start-time-millis start-time-millis
     :end-time-millis end-time-millis
     :is-live? (= offset 0)}))

(defn- parse-eval-metric-id
  "Parse an eval metric ID like [:eval :numeric-rule :score] into component parts."
  [metric-id]
  (when (and (vector? metric-id) 
             (= 3 (count metric-id))
             (= :eval (first metric-id)))
    (let [[_ rule-name score-name] metric-id]
      {:rule-name (name rule-name)
       :score-name (name score-name)
       :metric-id metric-id})))

(defn- detect-categories
  "Detect categories in telemetry data.
  Returns nil for numeric data (only _aor/default), or vector of category names for categorical.
  
  Handles both structures:
  - Without metadata: {bucket {category {stats}}} or {bucket {:stats}}
  - With metadata: {bucket {metadata-val {category {stats}}}} or {bucket {metadata-val {:stats}}}"
  [telemetry-data metadata-key]
  (when (seq telemetry-data)
    (let [;; Get sample data to check structure
          sample-bucket-data (val (first telemetry-data))
          sample-inner (if metadata-key
                         (val (first sample-bucket-data))
                         sample-bucket-data)
          
          ;; Check if we have categories by seeing if the inner values are maps (stats)
          ;; vs actual numbers/values
          first-inner-val (val (first sample-inner))
          is-categorical? (map? first-inner-val)
          
          ;; Get all category keys from all buckets (if categorical)
          all-keys (when is-categorical?
                     (if metadata-key
                       ;; With metadata: bucket -> metadata-val -> category
                       (set (mapcat (fn [bucket-data]
                                      (mapcat keys (vals bucket-data)))
                                    (vals telemetry-data)))
                       ;; Without metadata: bucket -> category
                       (set (mapcat keys (vals telemetry-data)))))
          
          ;; Filter out _aor/default
          category-keys (disj all-keys "_aor/default")]
      (when (seq category-keys)
        ;; Sort only strings to avoid comparing mixed types
        (vec (sort (filter string? category-keys)))))))

(defn- create-eval-chart-config
  "Create a chart configuration for an eval metric.
  
  Spec: Use options [:count :min 0.5 0.9 0.99 :max]
  If there are categories besides _aor/default, only display count for each category."
  [{:keys [rule-name score-name metric-id]}]
  {:id (keyword (str "eval-" rule-name "-" score-name))
   :title (str "Evaluator score " rule-name "/" score-name)
   :description (str "Score distribution for " rule-name "/" score-name)
   :variant :multi-metric
   :metric-id metric-id
   :metrics-set #{:count :min 0.5 0.9 0.99 :max}
   :variant-opts {:metrics #{:count :min 0.5 0.9 0.99 :max}}
   :y-label "Score"
   :eval-metric? true})

(defn- group-charts-by-metric
  "Group charts by their metric-id and compute union of metrics-set for each group.
  Returns: {metric-id {:charts [chart-configs...] :metrics-set #{...}}}"
  [charts]
  (reduce
   (fn [acc chart]
     (let [metric-id (:metric-id chart)]
       (update acc metric-id
               (fn [existing]
                 {:charts (conj (get existing :charts []) chart)
                  :metrics-set (set/union (get existing :metrics-set #{})
                                          (:metrics-set chart))}))))
   {}
   charts))

(defui metadata-search-dropdown
  "Searchable dropdown for metadata keys with example values.
  
  Props:
  - :module-id - Module ID for queries
  - :agent-name - Agent name for queries
  - :value - Current metadata key value
  - :on-change - Callback when selection changes"
  [{:keys [module-id agent-name value on-change]}]
  (let [[search-term set-search-term!] (uix/use-state "")
        [debounced-search] (useDebounce search-term 300)
        [is-open? set-open!] (uix/use-state false)
        [highlighted-idx set-highlighted-idx!] (uix/use-state 0)
        input-ref (uix/use-ref nil)

        ;; Fetch metadata keys with search filter
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:metadata-search module-id agent-name debounced-search]
          :sente-event [:analytics/search-metadata
                        {:module-id module-id
                         :agent-name agent-name
                         :search-string debounced-search}]
          :enabled? is-open?
          :refetch-on-mount true})

        ;; Extract metadata array from response
        metadata-items (or (:metadata data) [])

        ;; Display value - show current selection or placeholder
        display-value (cond
                        value value
                        (and is-open? (not (str/blank? search-term))) search-term
                        :else "")

        ;; Event handlers
        handle-input-change (fn [e]
                              (let [v (.. e -target -value)]
                                (set-search-term! v)
                                (set-open! true)
                                (set-highlighted-idx! 0)))

        handle-select (fn [metadata-key]
                        (on-change metadata-key)
                        (set-search-term! metadata-key)
                        (set-open! false))

        handle-clear (fn []
                       (on-change nil)
                       (set-search-term! "")
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
                                              #(min (dec (count metadata-items)) (inc %))))
                             "ArrowUp" (do (.preventDefault e)
                                           (set-highlighted-idx!
                                            #(max 0 (dec %))))
                             "Enter" (do (.preventDefault e)
                                         (when (< highlighted-idx (count metadata-items))
                                           (handle-select (:name (nth metadata-items highlighted-idx)))))
                             "Escape" (do (.preventDefault e)
                                          (set-open! false))
                             nil)))]

    ;; Reset search term when value changes externally
    (uix/use-effect
     (fn []
       (when (and value (not= search-term value))
         (set-search-term! value))
       js/undefined)
     [value])

    ($ :div.relative.w-full
       ($ :div.relative
          ($ :input {:ref input-ref
                     :type "text"
                     :className "w-full px-3 py-2 border border-gray-300 rounded-md text-sm focus:ring-blue-500 focus:border-blue-500"
                     :value display-value
                     :placeholder "Search metadata keys..."
                     :onChange handle-input-change
                     :onFocus handle-input-focus
                     :onBlur handle-input-blur
                     :onKeyDown handle-keydown})

          ;; Clear button (X) when there's a selected value
          (when value
            ($ :button.absolute.right-2.top-1.5.p-1.text-gray-400.hover:text-gray-600
               {:onClick #(do (.stopPropagation %)
                              (.preventDefault %)
                              (handle-clear))
                :type "button"}
               ($ :svg.h-4.w-4 {:fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                  ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :strokeWidth 2 :d "M6 18L18 6M6 6l12 12"})))))

       ;; Dropdown list
       (when is-open?
         ($ :div.absolute.z-50.w-full.mt-1.bg-white.border.border-gray-300.rounded-md.shadow-lg.max-h-60.overflow-y-auto
            (if loading?
              ($ :div.p-4.text-center.text-gray-500.flex.items-center.justify-center
                 ($ common/spinner {:size :medium})
                 ($ :span.ml-2 "Loading..."))

              (if error
                ($ :div.p-4.text-center.text-red-500
                   "Error loading metadata")

                (if (empty? metadata-items)
                  ($ :div.p-4.text-center.text-gray-500
                     "No metadata keys found")

                  (for [[idx metadata-item] (map-indexed vector metadata-items)]
                    (let [metadata-name (:name metadata-item)
                          example-values (:examples metadata-item)]
                      ($ :div {:key metadata-name
                               :className (str "p-3 cursor-pointer hover:bg-blue-50 "
                                               (when (= idx highlighted-idx) "bg-blue-100"))
                               :onMouseEnter #(set-highlighted-idx! idx)
                               :onClick #(handle-select metadata-name)}
                         ($ :div.font-medium.text-sm metadata-name)
                         (when (seq example-values)
                           ($ :div.text-xs.text-gray-500.mt-1
                              (str "Examples: " (str/join ", " (take 3 example-values))))))))))))))))

(defui global-controls
  [{:keys [granularity set-granularity
           time-offset set-time-offset
           metadata-key set-metadata-key
           module-id
           agent-name]}]
  (let [granularity-config (first (filter #(= (:id %) granularity) granularities))
        time-window (calculate-time-window (:seconds granularity-config) time-offset)
        is-live? (:is-live? time-window)
        format-fn (:format-fn granularity-config)

        ;; Navigation handlers
        go-back (fn [] (set-time-offset (fn [offset] (- offset 60))))
        go-forward (fn [] (set-time-offset (fn [offset] (+ offset 60))))
        go-live (fn [] (set-time-offset 0))

        ;; Granularity dropdown items
        granularity-items (map (fn [g]
                                 {:key (:id g)
                                  :label (:label g)
                                  :selected? (= (:id g) granularity)
                                  :on-select #(do
                                                (set-granularity (:id g))
                                                (set-time-offset 0))})
                               granularities)]

    ($ :div.bg-white.p-4.rounded-lg.shadow-sm.border.border-gray-200.mb-6
       ;; First row: Granularity and Metadata Split-by
       ($ :div.flex.flex-wrap.items-center.gap-4.mb-4
          ;; Granularity selector
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Granularity:")
             ($ :div.w-40
                ($ common/Dropdown
                   {:label "Granularity"
                    :display-text (:label granularity-config)
                    :items granularity-items
                    :data-testid "granularity-selector"})))

          ;; Metadata split-by selector with search
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Split by:")
             ($ :div.w-72
                ($ metadata-search-dropdown
                   {:module-id module-id
                    :agent-name agent-name
                    :value metadata-key
                    :on-change set-metadata-key}))))

       ;; Second row: Time navigation
       ($ :div.flex.items-center.justify-between
          ;; Left side: Navigation controls and time range
          ($ :div.flex.items-center.gap-3
             ;; Back button
             ($ :button.p-2.rounded.border.border-gray-300.bg-white.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed
                {:onClick go-back
                 :title "Go back 60 buckets"
                 :data-testid "time-nav-back"}
                ($ ChevronLeftIcon {:className "h-5 w-5 text-gray-600"}))

             ;; Forward button
             ($ :button.p-2.rounded.border.border-gray-300.bg-white.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed
                {:onClick go-forward
                 :disabled is-live?
                 :title (if is-live? "Already at live view" "Go forward 60 buckets")
                 :data-testid "time-nav-forward"}
                ($ ChevronRightIcon {:className "h-5 w-5 text-gray-600"}))

             ;; Time range display
             ($ :div.text-sm.text-gray-700.font-medium
                (str (format-fn (:start-time-millis time-window))
                     " - "
                     (format-fn (:end-time-millis time-window)))))

          ;; Right side: Live indicator or "Go Live" button
          ($ :div.flex.items-center.gap-2
             (if is-live?
               ($ :div.flex.items-center.gap-2.px-3.py-1.bg-red-50.border.border-red-200.rounded-full
                  ($ :div.h-2.w-2.bg-red-500.rounded-full.animate-pulse)
                  ($ :span.text-sm.font-medium.text-red-700 "LIVE"))
               ($ :button.px-4.py-1.text-sm.font-medium.text-white.bg-indigo-600.rounded.hover:bg-indigo-700
                  {:onClick go-live
                   :data-testid "go-live-button"}
                  "Go to Live")))))))

(defui chart-card
  "Renders a single analytics chart in a card.
  
  Props:
  - :config - Chart configuration
  - :data - Pre-fetched telemetry data (or nil if loading/error)
  - :loading? - Whether data is loading
  - :error - Error object if query failed
  - :granularity-config - Current granularity configuration
  - :time-window - Current time window
  - :metadata-key - Current metadata key (or nil)"
  [{:keys [config data loading? error granularity-config time-window metadata-key]}]
  (let [{:keys [title description variant variant-opts y-label color]} config]

    ($ :div.bg-white.p-6.rounded-lg.shadow-md.border.border-gray-200
       ($ :h3.text-lg.font-medium.text-gray-700.mb-2 title)
       ($ :p.text-sm.text-gray-500.mb-4 description)

       (cond
         loading?
         ($ :div.flex.items-center.justify-center.h-64.gap-2.text-blue-600
            ($ common/spinner {:size :medium}) "Loading...")

         error
         ($ :div.text-red-600 "Error: " (str error))

         :else
         ($ chart/analytics-chart
            {:data (or data {})
             :granularity (:seconds granularity-config)
             :metadata-key metadata-key
             :start-time-millis (:start-time-millis time-window)
             :end-time-millis (:end-time-millis time-window)
             :height 300
             :title nil ; Title shown above chart card
             :y-label y-label
             :color color
             :variant variant
             :variant-opts variant-opts})))))

(defui eval-chart-card
  "Renders an eval metric chart - handles its own data fetching to avoid hooks-in-loop issues.
  
  Props:
  - :config - Chart configuration
  - :module-id, :agent-name - Agent identifiers
  - :granularity-config - Current granularity configuration
  - :time-window - Current time window
  - :metadata-key - Current metadata key (or nil)
  - :refresh-counter - Refresh counter for live mode"
  [{:keys [config module-id agent-name granularity-config time-window metadata-key refresh-counter]}]
  (let [{:keys [title description variant variant-opts y-label color metric-id metrics-set]} config
        
        ;; Fetch data for this eval metric
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:analytics-telemetry
                      metric-id
                      module-id
                      agent-name
                      (:seconds granularity-config)
                      (:start-time-millis time-window)
                      metadata-key
                      refresh-counter]
          :sente-event [:analytics/fetch-telemetry
                        {:module-id module-id
                         :agent-name agent-name
                         :granularity (:seconds granularity-config)
                         :metric-id metric-id
                         :start-time-millis (:start-time-millis time-window)
                         :end-time-millis (:end-time-millis time-window)
                         :metrics-set metrics-set
                         :metadata-key metadata-key}]
          :enabled? (boolean (and module-id agent-name))})
        
        ;; Detect if data is categorical and adjust config accordingly
        categories (uix/use-memo
                    (fn [] (detect-categories data metadata-key))
                    [data metadata-key])
        
        ;; Dynamically adjust variant for categorical data
        actual-variant (if categories :multi-category variant)
        actual-variant-opts (if categories
                              {:categories categories
                               :metric-key :count}
                              variant-opts)
        actual-y-label (if categories "Count" y-label)]

    ($ :div.bg-white.p-6.rounded-lg.shadow-md.border.border-gray-200
       ($ :h3.text-lg.font-medium.text-gray-700.mb-2 title)
       ($ :p.text-sm.text-gray-500.mb-4 description)

       (cond
         loading?
         ($ :div.flex.items-center.justify-center.h-64.gap-2.text-blue-600
            ($ common/spinner {:size :medium}) "Loading...")

         error
         ($ :div.text-red-600 "Error: " (str error))

         :else
         ($ chart/analytics-chart
            {:data (or data {})
             :granularity (:seconds granularity-config)
             :metadata-key metadata-key
             :start-time-millis (:start-time-millis time-window)
             :end-time-millis (:end-time-millis time-window)
             :height 300
             :title nil
             :y-label actual-y-label
             :color color
             :variant actual-variant
             :variant-opts actual-variant-opts})))))

(defui analytics-page []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        decoded-agent-name (common/url-decode agent-name)

        ;; Global control state
        [granularity set-granularity] (uix/use-state :minute)
        [time-offset set-time-offset] (uix/use-state 0) ;; 0 = live, negative = back in time
        [metadata-key set-metadata-key] (uix/use-state nil)

        ;; Get granularity config and calculate time window
        granularity-config (first (filter #(= (:id %) granularity) granularities))
        time-window (uix/use-memo
                     (fn []
                       (calculate-time-window (:seconds granularity-config) time-offset))
                     [granularity time-offset])

        is-live? (:is-live? time-window)

        ;; Auto-refresh in live mode
        [refresh-counter set-refresh-counter] (uix/use-state 0)

        ;; Auto-refresh effect - only when in live mode
        _ (uix/use-effect
           (fn []
             (if is-live?
               (let [interval-id (js/setInterval
                                  (fn [] (set-refresh-counter (fn [c] (inc c))))
                                  60000)] ;; Refresh every 60 seconds
                 (fn [] (js/clearInterval interval-id)))
               js/undefined))
           [is-live?])

        ;; Query for all agent metrics to get eval metrics
        {all-metrics :data} (queries/use-sente-query
                             {:query-key [:all-agent-metrics module-id decoded-agent-name]
                              :sente-event [:analytics/fetch-all-metrics
                                            {:module-id module-id
                                             :agent-name decoded-agent-name}]
                              :enabled? (boolean (and module-id decoded-agent-name))})

        ;; Parse eval metrics (stable - only used for rendering, not querying)
        eval-chart-configs (uix/use-memo
                            (fn []
                              (when all-metrics
                                (->> all-metrics
                                     (filter #(and (vector? %) (= :eval (first %))))
                                     (keep parse-eval-metric-id)
                                     (mapv create-eval-chart-config))))
                            [all-metrics])

        ;; Group ONLY static charts by metric-id (stable hook count)
        static-metric-groups (uix/use-memo
                              (fn [] (group-charts-by-metric chart-configs))
                              [])

        ;; Create queries for static metrics
        static-metric-queries
        (into {}
              (map (fn [[metric-id {:keys [metrics-set]}]]
                     (let [{:keys [data loading? error]}
                           (queries/use-sente-query
                            {:query-key [:analytics-telemetry
                                         metric-id
                                         module-id
                                         decoded-agent-name
                                         (:seconds granularity-config)
                                         (:start-time-millis time-window)
                                         metadata-key
                                         refresh-counter]
                             :sente-event [:analytics/fetch-telemetry
                                           {:module-id module-id
                                            :agent-name decoded-agent-name
                                            :granularity (:seconds granularity-config)
                                            :metric-id metric-id
                                            :start-time-millis (:start-time-millis time-window)
                                            :end-time-millis (:end-time-millis time-window)
                                            :metrics-set metrics-set
                                            :metadata-key metadata-key}]
                             :enabled? (boolean (and module-id decoded-agent-name))})]
                       [metric-id {:data data :loading? loading? :error error}]))
                   static-metric-groups))

        metric-queries static-metric-queries]

    ($ :div.p-6
       ;; Page header
       ($ :div.flex.items-center.gap-3.mb-6
          ($ ChartBarIcon {:className "h-8 w-8 text-indigo-600"})
          ($ :h2.text-2xl.font-bold.text-gray-900
             (str "Analytics for " decoded-agent-name)))

       ;; Global controls
       ($ global-controls
          {:granularity granularity
           :set-granularity set-granularity
           :time-offset time-offset
           :set-time-offset set-time-offset
           :metadata-key metadata-key
           :set-metadata-key set-metadata-key
           :module-id module-id
           :agent-name decoded-agent-name})

       ;; Static metrics section
       ($ :div.grid.grid-cols-1.lg:grid-cols-2.gap-6
          (map (fn [config]
                 (let [metric-id (:metric-id config)
                       query-result (get metric-queries metric-id)]
                   ($ chart-card
                      {:key (:id config)
                       :config config
                       :data (:data query-result)
                       :loading? (:loading? query-result)
                       :error (:error query-result)
                       :granularity-config granularity-config
                       :time-window time-window
                       :metadata-key metadata-key})))
               chart-configs))

       ;; Eval metrics section (if any exist)
       (when (seq eval-chart-configs)
         ($ :div
            ;; Section header
            ($ :h3.text-xl.font-bold.text-gray-900.mt-8.mb-4.border-t.border-gray-200.pt-6
               "Evaluator Metrics")

            ;; Eval charts grid - each handles its own query
            ($ :div.grid.grid-cols-1.lg:grid-cols-2.gap-6
               (map (fn [config]
                      ($ eval-chart-card
                         {:key (:id config)
                          :config config
                          :module-id module-id
                          :agent-name decoded-agent-name
                          :granularity-config granularity-config
                          :time-window time-window
                          :metadata-key metadata-key
                          :refresh-counter refresh-counter}))
                    eval-chart-configs)))))))
