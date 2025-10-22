(ns com.rpl.agent-o-rama.ui.chart
  "uPlot-based charting components for time-series data visualization."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["react" :refer [useRef useLayoutEffect useEffect]]
   ["uplot" :as uPlot]))

(defhook use-uplot
  "React hook to manage a uPlot chart instance lifecycle.
  
  Args:
  - options: uPlot configuration object (will be converted to JS)
  - data: uPlot data array [[timestamps] [series1] [series2] ...] (will be converted to JS)
  
  Returns:
  - A two-element vector: [target-ref chart-ref]
    - target-ref: React ref to attach to the DOM element where the chart will render
    - chart-ref: React ref containing the uPlot chart instance (for calling methods like .setSize())"
  [options data]
  (let [chart-ref (useRef nil)
        target-ref (useRef nil)]

    ;; Create or update chart when data changes
    (useLayoutEffect
     (fn []
       (when (and (.-current target-ref) data (seq data))
         (let [current-chart (.-current chart-ref)]
           (if current-chart
             ;; If chart exists, update its data
             (.setData current-chart (clj->js data))
             ;; Otherwise, create a new chart instance
             (let [new-chart (uPlot. (clj->js options) (clj->js data) (.-current target-ref))]
               (set! (.-current chart-ref) new-chart)))))
       js/undefined)
     #js [data]) ; Only re-run when data changes

    ;; Cleanup effect to destroy chart on unmount
    (useLayoutEffect
     (fn []
       (fn []
         (when-let [chart (.-current chart-ref)]
           (.destroy chart)
           (set! (.-current chart-ref) nil))))
     #js [])

    ;; Return both refs so caller can access the chart instance
    [target-ref chart-ref]))

(defui time-series-chart
  "A time-series chart component using uPlot.
  
  Props:
  - :data - uPlot data format [[timestamps] [series1] [series2] ...]
  - :series - Series configuration [{:label :stroke :width} ...]
  - :width - Chart width in pixels (optional, defaults to 800)
  - :height - Chart height in pixels (optional, defaults to 400)
  - :title - Chart title (optional)
  - :axes - Custom axes configuration (optional)
  - :show-legend - Whether to show the legend (optional, defaults to true)"
  [{:keys [data series width height title axes show-legend]}]
  (let [width (or width 800)
        height (or height 400)
        show-legend (if (nil? show-legend) true show-legend)

        ;; Build uPlot options
        options {:width width
                 :height height
                 :series (into [{:label "Experiment #"}] series)
                 :axes (or axes
                           ;; Default axes configuration with custom value formatting
                           [{:stroke "#64748b"
                             :grid {:show true :stroke "#e2e8f0" :width 1}
                             :ticks {:show true :stroke "#cbd5e1"}
                             ;; Custom splits function to only show integer experiment numbers
                             :splits (fn [self axis-idx scale-min scale-max inc-space]
                                       ;; Generate integer splits from min to max
                                       (let [min (js/Math.ceil scale-min)
                                             max (js/Math.floor scale-max)
                                             result #js []]
                                         (loop [i min]
                                           (when (<= i max)
                                             (.push result i)
                                             (recur (inc i))))
                                         result))
                             ;; Custom value formatter for x-axis: show as "#1", "#2", etc.
                             :values (fn [self splits-array axis-index]
                                       ;; splits-array contains the tick values, use JS .map directly
                                       (.map splits-array (fn [v] (str "#" (int v)))))}
                            {:stroke "#64748b"
                             :grid {:show true :stroke "#e2e8f0" :width 1}
                             :ticks {:show true :stroke "#cbd5e1"}}])
                 :scales {:x {:time false ; Explicitly NOT a time scale
                              :auto false ; Don't auto-detect
                              :range (fn [self min max]
                                       ;; Force integer range based on actual data
                                       #js [(js/Math.floor min) (js/Math.ceil max)])}
                          :y {:auto true}}
                 :legend {:show show-legend
                          :live true}}

        ;; Get the ref from our hook
        [target-ref _chart-ref] (use-uplot options data)]

    ($ :div.w-full
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-2.text-center title))
       ($ :div {:ref target-ref}))))

(defn- prepare-analytics-data
  "Transform analytics data into uPlot format.
  
  Args:
  - telemetry-data: Map of {bucket-number {metadata-key {metric-key value}}}
  - granularity: Granularity in seconds (e.g., 60 for minute)
  - metrics-to-show: Set of metric keys to display (e.g., #{:min :max 0.5 0.9})
  - start-time-millis: Start of the time window being queried
  - end-time-millis: End of the time window being queried
  
  Returns:
  - uPlot data format [[timestamps] [series1] [series2] ...], or empty structure spanning the time window if no data"
  [telemetry-data granularity metrics-to-show start-time-millis end-time-millis]
  (if (seq telemetry-data)
    (let [;; Sort buckets chronologically
          sorted-buckets (sort (keys telemetry-data))

          ;; Convert bucket numbers to timestamps (bucket * granularity * 1000)
          timestamps (mapv #(* % granularity 1000) sorted-buckets)

          ;; For each metric, extract values across all buckets
          ;; Using "_aor/default" as the metadata key for now
          series-data (reduce
                       (fn [acc metric-key]
                         (let [values (mapv
                                       (fn [bucket]
                                         (get-in telemetry-data
                                                 [bucket "_aor/default" metric-key]))
                                       sorted-buckets)]
                           (assoc acc metric-key values)))
                       {}
                       metrics-to-show)]

          ;; Return in uPlot format: [timestamps series1 series2 ...]
          ;; Sort metrics: keywords first (alphabetically), then numbers (numerically)
      (let [sorted-metrics (sort-by (fn [k] [(if (keyword? k) 0 1) k]) metrics-to-show)]
        (into [timestamps] (map series-data sorted-metrics))))
    ;; Return empty data structure spanning the actual time window
    ;; This ensures the chart x-axis shows the correct historical time range even with no data
    (let [sorted-metrics (sort-by (fn [k] [(if (keyword? k) 0 1) k]) metrics-to-show)
          ;; Create timestamps at the start and end of the time window
          timestamps [start-time-millis end-time-millis]]
      (into [timestamps] (repeat (count sorted-metrics) [nil nil])))))

(defui analytics-time-series-chart
  "A time-series chart for analytics telemetry data.
  
  Props:
  - :data - Analytics telemetry data {bucket-number {metadata-key {metric-key value}}}
  - :granularity - Time granularity in seconds (e.g., 60 for minute)
  - :metrics - Set of metrics to display (defaults to #{:min :max 0.5 0.9 0.99})
  - :start-time-millis - Start of time window (required for proper empty chart display)
  - :end-time-millis - End of time window (required for proper empty chart display)
  - :width - Chart width in pixels (optional, auto-detects container width if not provided)
  - :height - Chart height in pixels (optional, defaults to 300)
  - :title - Chart title (optional)
  - :y-label - Y-axis label (optional)"
  [{:keys [data granularity metrics start-time-millis end-time-millis width height title y-label]}]
  (let [metrics (or metrics #{:min :max 0.5 0.9 0.99})
        height (or height 300)

        ;; Container ref to measure width
        container-ref (useRef nil)

        ;; Transform data for uPlot
        chart-data (uix/use-memo
                    (fn [] (prepare-analytics-data data granularity metrics start-time-millis end-time-millis))
                    [data granularity metrics start-time-millis end-time-millis])

        ;; Define series configurations with nice colors
        metric-colors {:min "#10b981" ; green
                       :max "#ef4444" ; red
                       0.5 "#3b82f6" ; blue
                       0.9 "#f59e0b" ; amber
                       0.99 "#8b5cf6"} ; purple

        series (mapv
                (fn [metric-key]
                  {:label (cond
                            (keyword? metric-key) (name metric-key)
                            (number? metric-key) (str "p" (int (* metric-key 100)))
                            :else (str metric-key))
                   :stroke (get metric-colors metric-key "#6b7280")
                   :width 2
                   :points {:show false}})
                ;; Sort metrics: keywords first (alphabetically), then numbers (numerically)
                (sort-by (fn [k] [(if (keyword? k) 0 1) k]) metrics))

        ;; Build uPlot options for time-series (size will be set dynamically)
        options (uix/use-memo
                 (fn []
                   {:width (or width 800) ; Initial width, will be updated on mount/resize
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            ;; Format timestamps as readable dates
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp]
                                              (let [date (js/Date. timestamp)
                                                    hours (.getHours date)
                                                    minutes (.getMinutes date)]
                                                (str (when (< hours 10) "0") hours
                                                     ":"
                                                     (when (< minutes 10) "0") minutes)))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label (or y-label "Value")
                            :labelSize 14}]
                    :scales {:x {:time true
                                 :range (fn [self min max]
                                      ;; Force the range to always be the full time window
                                          #js [start-time-millis end-time-millis])}
                             :y {:auto true}}
                    :legend {:show true
                             :live true}})
                 [height y-label series start-time-millis end-time-millis]) ; width intentionally not in deps

        ;; Get both refs from our hook
        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize following uPlot's recommended pattern
        _ (uix/use-effect
           (fn []
             (when-not width ;; Only auto-resize if width not explicitly provided
               (let [get-size (fn []
                                (when-let [container (.-current container-ref)]
                                  (let [rect (.getBoundingClientRect container)
                                        container-width (.-width rect)]
                                    (when (> container-width 0)
                                      {:width container-width :height height}))))
                     handle-resize (fn []
                                     (when-let [chart (.-current chart-ref)]
                                       (when-let [size (get-size)]
                                         (.setSize chart (clj->js size)))))]
                 ;; Set initial size after chart is created
                 (handle-resize)
                 ;; Listen for window resize
                 (.addEventListener js/window "resize" handle-resize)
                 ;; Cleanup
                 (fn []
                   (.removeEventListener js/window "resize" handle-resize)))))
           [width height])] ; Re-run if explicit width or height changes

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
       ($ :div {:ref target-ref}))))
