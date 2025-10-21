(ns com.rpl.agent-o-rama.ui.analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.chart :as chart]
   ["@heroicons/react/24/outline" :refer [ChartBarIcon]]))

(defui analytics-page []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        decoded-agent-name (common/url-decode agent-name)

        ;; Define state for analytics parameters with defaults
        [granularity _set-granularity] (uix/use-state 60) ;; default: minute
        [metric-id _set-metric-id] (uix/use-state [:agent :latency])
        [metrics-set _set-metrics-set] (uix/use-state #{:min 0.5 0.9 0.99 :max})
        [_metadata-key _set-metadata-key] (uix/use-state nil)

        ;; Calculate time window for the last 60 buckets
        time-window (uix/use-memo
                     (fn []
                       (let [now-millis (.now js/Date)
                             granularity-millis (* granularity 1000)
                             end-bucket (js/Math.floor (/ now-millis granularity-millis))
                             start-bucket (- end-bucket 59)
                             start-time-millis (* start-bucket granularity-millis)
                             end-time-millis (* (inc end-bucket) granularity-millis)]
                         {:start-time-millis start-time-millis
                          :end-time-millis end-time-millis}))
                     [granularity])

        ;; Setup the sente query
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:analytics-telemetry
                      module-id
                      agent-name
                      granularity
                      (vec metric-id) ;; Ensure key is stable
                      (:start-time-millis time-window)]
          :sente-event [:analytics/fetch-telemetry
                        {:module-id module-id
                         :agent-name decoded-agent-name
                         :granularity granularity
                         :metric-id metric-id
                         :start-time-millis (:start-time-millis time-window)
                         :end-time-millis (:end-time-millis time-window)
                         :metrics-set metrics-set
                         :metadata-key nil}]
          :enabled? (boolean (and module-id agent-name))})]

    ;; Effect to log data when it arrives
    (uix/use-effect
     (fn []
       (when data
         (println "Analytics data received for" (vec metric-id) ":" (pr-str data)))
       (when error
         (println "Analytics query error:" error))
       js/undefined)
     [data error])

    ($ :div.p-6
       ($ :div.flex.items-center.gap-3.mb-6
          ($ ChartBarIcon {:className "h-8 w-8 text-indigo-600"})
          ($ :h2.text-2xl.font-bold.text-gray-900
             (str "Analytics for " decoded-agent-name)))
       ($ :div.space-y-6
          ;; Header card
          ($ :div.bg-white.p-6.rounded-lg.shadow-md.border.border-gray-200
             ($ :h3.text-lg.font-medium.text-gray-700.mb-2 "Agent Latency")
             ($ :p.text-sm.text-gray-500.mb-4
                "Latency metrics over the last 60 minutes (minute granularity)")

             (cond
               loading?
               ($ :div.flex.items-center.gap-2.text-blue-600
                  ($ common/spinner {:size :medium}) "Loading analytics data...")

               error
               ($ :div.text-red-600 "Error: " (str error))

               (and data (seq data))
               ($ chart/analytics-time-series-chart
                  {:data data
                   :granularity granularity
                   :metrics metrics-set
                   :height 300
                   :y-label "Latency (ms)"})

               :else
               ($ :div.text-gray-500.text-center.py-8
                  "No data available for the selected time range")))))))