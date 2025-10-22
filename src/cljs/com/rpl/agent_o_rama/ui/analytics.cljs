(ns com.rpl.agent-o-rama.ui.analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.chart :as chart]
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
  - :chart-type - One of :bar, :line, :percentage, :multi-line
  - :metric-id - The metric ID to query (e.g., [:agent :latency])
  - :metrics-set - Set of metric keys to request (e.g., #{:count}, #{:min 0.5 :max})
  - :metric-key - For single-value charts, which key to display
  - :y-label - Y-axis label
  - :color - Optional color override for single-series charts"
  [;; 1. Agent Invokes
   {:id :agent-invokes
    :title "Agent invokes"
    :description "Total number of agent runs per time bucket"
    :chart-type :bar
    :metric-id [:agent :success-rate]
    :metrics-set #{:count}
    :metric-key :count
    :y-label "Count"
    :color "#6366f1"} ; indigo

   ;; 2. Agent Success Rate
   {:id :agent-success-rate
    :title "Agent success rate"
    :description "Percentage of successful agent runs per time bucket"
    :chart-type :percentage
    :metric-id [:agent :success-rate]
    :metrics-set #{:mean}
    :metric-key :mean
    :color "#10b981"} ; green

   ;; 3. Agent Latency
   {:id :agent-latency
    :title "Agent latency"
    :description "Distribution of end-to-end agent execution time"
    :chart-type :multi-line
    :metric-id [:agent :latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 4. Total Model Calls
   {:id :total-model-calls
    :title "Total model calls"
    :description "Sum of all LLM calls made by agents in each time bucket"
    :chart-type :bar
    :metric-id [:agent :model-call-count]
    :metrics-set #{:rest-sum}
    :metric-key :rest-sum
    :y-label "Total Calls"
    :color "#8b5cf6"} ; purple

   ;; 5. Model Calls Per Agent Invoke
   {:id :model-calls-per-invoke
    :title "Model calls per agent invoke"
    :description "Distribution of LLM calls per agent run"
    :chart-type :multi-line
    :metric-id [:agent :model-call-count]
    :metrics-set #{:min 0.25 0.5 0.75 :max}
    :y-label "Calls per Invoke"}

   ;; 6. Model Latency
   {:id :model-latency
    :title "Model latency"
    :description "Distribution of individual LLM call latency"
    :chart-type :multi-line
    :metric-id [:agent :model-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 7. Store Read Latency
   {:id :store-read-latency
    :title "Store read latency"
    :description "Distribution of store read operation latency"
    :chart-type :multi-line
    :metric-id [:agent :store-read-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 8. Store Write Latency
   {:id :store-write-latency
    :title "Store write latency"
    :description "Distribution of store write operation latency"
    :chart-type :multi-line
    :metric-id [:agent :store-write-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 9. Database Read Latency
   {:id :db-read-latency
    :title "Database read latency"
    :description "Distribution of database read operation latency"
    :chart-type :multi-line
    :metric-id [:agent :db-read-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 10. Database Write Latency
   {:id :db-write-latency
    :title "Database write latency"
    :description "Distribution of database write operation latency"
    :chart-type :multi-line
    :metric-id [:agent :db-write-latency]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Latency (ms)"}

   ;; 11. Time to First Token (Agent)
   {:id :agent-first-token
    :title "Time to first token (agent)"
    :description "Distribution of time until first token in agent response"
    :chart-type :multi-line
    :metric-id [:agent :first-token-time]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Time (ms)"}

   ;; 12. Time to First Token (Model Call)
   {:id :model-first-token
    :title "Time to first token (individual model call)"
    :description "Distribution of time until first token in individual LLM calls"
    :chart-type :multi-line
    :metric-id [:agent :model-first-token-time]
    :metrics-set #{:min 0.5 0.9 0.99 :max}
    :y-label "Time (ms)"}

   ;; 13. Token Usage (Multi-category)
   {:id :token-usage
    :title "Token usage"
    :description "Total tokens consumed by LLM calls over time"
    :chart-type :multi-category
    :metric-id [:agent :token-counts]
    :metrics-set #{:rest-sum}
    :y-label "Tokens"
    :categories ["input" "output" "total"]}

   ;; 14. Token Usage Per Agent Invoke
   {:id :token-usage-per-invoke
    :title "Token usage per agent invoke"
    :description "Distribution of token consumption per agent run"
    :chart-type :multi-category-percentiles
    :metric-id [:agent :token-counts]
    :metrics-set #{:min 0.25 0.5 0.75 :max}
    :y-label "Tokens"
    :categories ["input" "output" "total"]}

   ;; 15. Model Success Rate (Special calculation)
   {:id :model-success-rate
    :title "Model success rate"
    :description "Percentage of successful individual LLM calls"
    :chart-type :model-success-rate
    :metric-id [:agent :model-success-rate]
    :metrics-set #{:rest-sum}
    :color "#10b981"}

   ;; NOTE: Evaluator Score Charts
   ;; Evaluator charts can be added dynamically or statically to this configuration.
   ;; To add an evaluator chart, use the following pattern:
   ;;
   ;; For numeric/boolean scores (percentile distribution):
   ;; {:id :eval-rule-name-score-name
   ;;  :title "Evaluator score: rule-name/score-name"
   ;;  :description "Distribution of evaluator scores"
   ;;  :chart-type :multi-line
   ;;  :metric-id [:eval :rule-name :score-name]
   ;;  :metrics-set #{:min 0.5 0.9 0.99 :max}
   ;;  :y-label "Score"}
   ;;
   ;; For categorical scores (count per category):
   ;; {:id :eval-rule-name-score-name
   ;;  :title "Evaluator score: rule-name/score-name"
   ;;  :description "Count by category"
   ;;  :chart-type :multi-category
   ;;  :metric-id [:eval :rule-name :score-name]
   ;;  :metrics-set #{:count}
   ;;  :metric-key :count
   ;;  :categories ["category1" "category2" ...]
   ;;  :y-label "Count"}
   ;;
   ;; Future enhancement: Implement dynamic discovery of evaluator metrics
   ;; by querying all-agent-metrics-query and automatically generating charts
   ;; based on available [:eval ...] metrics.
   ])

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

(defui global-controls
  [{:keys [granularity set-granularity
           time-offset set-time-offset
           metadata-key set-metadata-key]}]
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
                               granularities)

        ;; Metadata split-by items (placeholder for now)
        metadata-items [{:key :none
                         :label "None (Overall)"
                         :selected? (nil? metadata-key)
                         :on-select #(set-metadata-key nil)}
                        {:key :aor-status
                         :label "aor/status"
                         :selected? (= metadata-key "aor/status")
                         :on-select #(set-metadata-key "aor/status")}]]

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

          ;; Metadata split-by selector
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Split by:")
             ($ :div.w-48
                ($ common/Dropdown
                   {:label "Split by"
                    :display-text (if metadata-key metadata-key "None (Overall)")
                    :items metadata-items
                    :data-testid "metadata-selector"}))))

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
  - :config - Chart configuration from chart-configs
  - :module-id - Module ID for queries
  - :agent-name - Agent name for queries
  - :granularity-config - Current granularity configuration
  - :time-window - Current time window {:start-time-millis :end-time-millis :is-live?}
  - :metadata-key - Current metadata key (or nil)
  - :refresh-counter - Counter that increments to trigger refetch"
  [{:keys [config module-id agent-name granularity-config time-window metadata-key refresh-counter]}]
  (let [{:keys [id title description chart-type metric-id metrics-set metric-key y-label color]} config

        ;; Setup the sente query for this chart
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:analytics-telemetry
                      id
                      module-id
                      agent-name
                      (:seconds granularity-config)
                      (vec metric-id)
                      (:start-time-millis time-window)
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
          :enabled? (boolean (and module-id agent-name))})]

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
         (case chart-type
           :bar
           ($ chart/analytics-bar-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metric-key metric-key
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :y-label y-label
               :color color})

           :percentage
           ($ chart/analytics-percentage-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metric-key metric-key
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :color color})

           :multi-line
           ($ chart/analytics-time-series-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metrics metrics-set
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :y-label y-label})

           :multi-category
           ($ chart/analytics-multi-category-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metric-key metric-key
               :categories (:categories config)
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :y-label y-label})

           :multi-category-percentiles
           ($ chart/analytics-multi-category-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metric-key metric-key
               :categories (:categories config)
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :y-label y-label})

           :model-success-rate
           ($ chart/analytics-model-success-rate-chart
              {:data (or data [])
               :granularity (:seconds granularity-config)
               :metric-key metric-key
               :start-time-millis (:start-time-millis time-window)
               :end-time-millis (:end-time-millis time-window)
               :height 300
               :color color})

           ;; Default fallback
           ($ :div.text-gray-500 "Unsupported chart type: " (str chart-type)))))))

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
           [is-live?])]

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
           :set-metadata-key set-metadata-key})

       ;; Charts grid - 2 columns on large screens, 1 column on mobile
       ($ :div.grid.grid-cols-1.lg:grid-cols-2.gap-6
          ;; Render all charts from configuration
          (map (fn [config]
                 ($ chart-card
                    {:key (:id config)
                     :config config
                     :module-id module-id
                     :agent-name decoded-agent-name
                     :granularity-config granularity-config
                     :time-window time-window
                     :metadata-key metadata-key
                     :refresh-counter refresh-counter}))
               chart-configs)))))
