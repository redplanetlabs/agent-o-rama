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

(defui analytics-page []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        decoded-agent-name (common/url-decode agent-name)

        ;; Global control state
        [granularity set-granularity] (uix/use-state :minute)
        [time-offset set-time-offset] (uix/use-state 0) ;; 0 = live, negative = back in time
        [metadata-key set-metadata-key] (uix/use-state nil)

        ;; Chart-specific state
        [metric-id _set-metric-id] (uix/use-state [:agent :latency])
        [metrics-set _set-metrics-set] (uix/use-state #{:min 0.5 0.9 0.99 :max})

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

        ;; Setup the sente query
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:analytics-telemetry
                      module-id
                      agent-name
                      (:seconds granularity-config)
                      (vec metric-id)
                      (:start-time-millis time-window)
                      refresh-counter] ;; Include refresh counter to trigger refetch
          :sente-event [:analytics/fetch-telemetry
                        {:module-id module-id
                         :agent-name decoded-agent-name
                         :granularity (:seconds granularity-config)
                         :metric-id metric-id
                         :start-time-millis (:start-time-millis time-window)
                         :end-time-millis (:end-time-millis time-window)
                         :metrics-set metrics-set
                         :metadata-key metadata-key}]
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

       ;; Charts area
       ($ :div.space-y-6
          ;; Agent Latency chart card
          ($ :div.bg-white.p-6.rounded-lg.shadow-md.border.border-gray-200
             ($ :h3.text-lg.font-medium.text-gray-700.mb-2 "Agent Latency")
             ($ :p.text-sm.text-gray-500.mb-4
                (str "Latency metrics over 60 " (name granularity) " buckets"))

             (cond
               loading?
               ($ :div.flex.items-center.gap-2.text-blue-600
                  ($ common/spinner {:size :medium}) "Loading analytics data...")

               error
               ($ :div.text-red-600 "Error: " (str error))

               :else
               ($ chart/analytics-time-series-chart
                  {:data (or data [])
                   :granularity (:seconds granularity-config)
                   :metrics metrics-set
                   :start-time-millis (:start-time-millis time-window)
                   :end-time-millis (:end-time-millis time-window)
                   :height 300
                   :y-label "Latency (ms)"})))))))
