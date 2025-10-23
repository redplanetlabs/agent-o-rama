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

    ;; Create or update chart when data or options change
    (useLayoutEffect
     (fn []
       (when (and (.-current target-ref) data (seq data))
         (let [current-chart (.-current chart-ref)]
           ;; Always destroy and recreate chart when options or data change
           ;; This ensures axis ranges and other config updates are applied
           (when current-chart
             (.destroy current-chart)
             (set! (.-current chart-ref) nil))
           ;; Create new chart instance
           (let [new-chart (uPlot. (clj->js options) (clj->js data) (.-current target-ref))]
             (set! (.-current chart-ref) new-chart))))
       js/undefined)
     #js [data options]) ; Re-run when data OR options change

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

(defn- sort-metrics
  "Sort metrics in display order: min, percentiles (ascending), max"
  [metrics]
  (let [metric-order {:min 0
                      0.5 1
                      0.9 2
                      0.99 3
                      :max 4}]
    (sort-by (fn [k]
               (get metric-order k 999)) ; Unknown metrics go to end
             metrics)))

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
  (println "=== prepare-analytics-data ===")
  (println "metrics-to-show:" metrics-to-show)
  (println "telemetry-data (first 2 buckets):" (take 2 telemetry-data))

  (if (seq telemetry-data)
    (let [;; Sort buckets chronologically
          sorted-buckets (sort (keys telemetry-data))

          ;; Get metadata keys from the first bucket only
          first-bucket (first sorted-buckets)
          first-bucket-data (get telemetry-data first-bucket)
          first-bucket-metadata-keys (when first-bucket
                                       (keys first-bucket-data))
          ;; Use "_aor/default" if available, otherwise first available key
          metadata-key (or (first (filter #(= "_aor/default" %) first-bucket-metadata-keys))
                           (first first-bucket-metadata-keys)
                           "_aor/default")

          _ (println "first-bucket:" first-bucket)
          _ (println "first-bucket-data:" first-bucket-data)
          _ (println "first-bucket-metadata-keys:" first-bucket-metadata-keys)
          _ (println "chosen metadata-key:" metadata-key)

          ;; Convert bucket numbers to timestamps in seconds (uPlot expects seconds for time scale)
          timestamps (mapv #(* % granularity) sorted-buckets)

          ;; For each metric, extract values across all buckets using the metadata key from first bucket
          ;; When metadata split is active, there's an extra "_aor/default" level
          series-data (reduce
                       (fn [acc metric-key]
                         (let [;; Try path with _aor/default first (for metadata splits)
                               path-with-default [first-bucket metadata-key "_aor/default" metric-key]
                               ;; Try direct path (for no metadata split)
                               path-direct [first-bucket metadata-key metric-key]
                               first-value-with-default (get-in telemetry-data path-with-default)
                               first-value-direct (get-in telemetry-data path-direct)
                               ;; Determine which path to use
                               use-default-path? (some? first-value-with-default)
                               _ (println "  metric:" metric-key
                                          "path-with-default:" path-with-default "=>" first-value-with-default
                                          "path-direct:" path-direct "=>" first-value-direct
                                          "using-default-path?:" use-default-path?)
                               values (mapv
                                       (fn [bucket]
                                         (if use-default-path?
                                           (get-in telemetry-data [bucket metadata-key "_aor/default" metric-key])
                                           (get-in telemetry-data [bucket metadata-key metric-key])))
                                       sorted-buckets)]
                           (println "  metric:" metric-key "values (first 5):" (take 5 values))
                           (assoc acc metric-key values)))
                       {}
                       metrics-to-show)]

      ;; Sort metrics in display order: min, percentiles, max
      (let [sorted-metrics (sort-metrics metrics-to-show)
            result (into [timestamps] (map series-data sorted-metrics))]
        (println "Final result - timestamps (first 5):" (take 5 timestamps))
        result))
    ;; Return empty data structure spanning the actual time window
    ;; This ensures the chart x-axis shows the correct historical time range even with no data
    (do
      (println "Empty telemetry-data!")
      (let [sorted-metrics (sort-metrics metrics-to-show)
            ;; Create timestamps at the start and end of the time window (in seconds)
            timestamps [(/ start-time-millis 1000) (/ end-time-millis 1000)]]
        (into [timestamps] (repeat (count sorted-metrics) [nil nil]))))))

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
                   :points {:show true :size 4}})
                ;; Sort metrics in display order: min, percentiles, max
                (sort-metrics metrics))

        ;; Build uPlot options for time-series (size will be set dynamically)
        options (uix/use-memo
                 (fn []
                   {:width (or width 100) ; Use small initial width to avoid overflow
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            ;; Format timestamps as readable dates
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp-seconds]
                                              ;; Convert seconds to milliseconds for JS Date
                                              (.toLocaleString (js/Date. (* timestamp-seconds 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label (or y-label "Value")
                            :labelSize 14}]
                    :scales {:x {:time true
                                 :range (fn [self min max]
                                      ;; Force the range to always be the full time window (in seconds)
                                          #js [(/ start-time-millis 1000) (/ end-time-millis 1000)])}
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
                 ;; Use requestAnimationFrame to ensure DOM has settled
                 (js/requestAnimationFrame
                  (fn []
                    ;; Set initial size after chart is created and DOM is stable
                    (handle-resize)))
                 ;; Listen for window resize
                 (.addEventListener js/window "resize" handle-resize)
                 ;; Cleanup
                 (fn []
                   (.removeEventListener js/window "resize" handle-resize)))))
           [width height chart-data])] ; Add chart-data to re-run resize when data changes

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
;; Add style tag for vertical legend layout
       ($ :style ".uplot-vertical-legend .u-legend { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
                  .uplot-vertical-legend .u-legend .u-series { display: flex; align-items: center; gap: 8px; }
                  .uplot-vertical-legend .u-legend .u-series > * { display: inline-block; }
                  .uplot-vertical-legend .u-legend .u-marker { width: 12px; height: 12px; border-radius: 50%; }")
       ($ :div.uplot-vertical-legend {:ref target-ref}))))

(defn- prepare-bar-chart-data
  "Transform analytics data into uPlot format for bar charts.
  
  Args:
  - telemetry-data: Map of {bucket-number {metadata-key {metric-key value}}}
  - granularity: Granularity in seconds
  - metric-key: The metric key to extract (e.g., :count or :rest-sum)
  - start-time-millis: Start of the time window
  - end-time-millis: End of the time window
  
  Returns:
  - uPlot data format [[timestamps] [values]]"
  [telemetry-data granularity metric-key start-time-millis end-time-millis]
  (println "=== prepare-bar-chart-data ===")
  (println "metric-key:" metric-key)
  (println "telemetry-data (first 2 buckets):" (take 2 telemetry-data))

  (if (seq telemetry-data)
    (let [sorted-buckets (sort (keys telemetry-data))

          ;; Get metadata key from the first bucket only
          first-bucket (first sorted-buckets)
          first-bucket-data (get telemetry-data first-bucket)
          first-bucket-metadata-keys (when first-bucket
                                       (keys first-bucket-data))
          metadata-key-to-use (or (first (filter #(= "_aor/default" %) first-bucket-metadata-keys))
                                  (first first-bucket-metadata-keys)
                                  "_aor/default")

          _ (println "first-bucket:" first-bucket)
          _ (println "first-bucket-data:" first-bucket-data)
          _ (println "metadata-key-to-use:" metadata-key-to-use)

          timestamps (mapv #(* % granularity) sorted-buckets)

          ;; Try both paths - with and without _aor/default layer
          values (mapv
                  (fn [bucket]
                    (let [;; Try path with _aor/default (for both split and no-split cases)
                          path-with-default [bucket metadata-key-to-use "_aor/default" metric-key]
                          ;; Try direct path (fallback)
                          path-direct [bucket metadata-key-to-use metric-key]
                          value-with-default (get-in telemetry-data path-with-default)
                          value-direct (get-in telemetry-data path-direct)]
                      (when (= bucket first-bucket)
                        (println "  path-with-default:" path-with-default "=>" value-with-default)
                        (println "  path-direct:" path-direct "=>" value-direct))
                      (or value-with-default value-direct)))
                  sorted-buckets)]
      (println "values (first 5):" (take 5 values))
      [timestamps values])
    ;; Empty data
    (do
      (println "Empty telemetry-data!")
      [[(/ start-time-millis 1000) (/ end-time-millis 1000)] [nil nil]])))

(defui analytics-bar-chart
  "A bar chart for analytics telemetry data.
  
  Props:
  - :data - Analytics telemetry data {bucket-number {metadata-key {metric-key value}}}
  - :granularity - Time granularity in seconds
  - :metric-key - Which metric to display (e.g., :count or :rest-sum)
  - :start-time-millis - Start of time window
  - :end-time-millis - End of time window
  - :height - Chart height in pixels (optional, defaults to 300)
  - :title - Chart title (optional)
  - :y-label - Y-axis label (optional)
  - :color - Bar color (optional, defaults to blue)"
  [{:keys [data granularity metric-key start-time-millis end-time-millis height title y-label color]}]
  (let [height (or height 300)
        color (or color "#3b82f6")

        container-ref (useRef nil)

        ;; Transform data for uPlot
        chart-data (uix/use-memo
                    (fn [] (prepare-bar-chart-data data granularity metric-key start-time-millis end-time-millis))
                    [data granularity metric-key start-time-millis end-time-millis])

;; Line chart series configuration with dots
        series [{:label (or y-label "Value")
                 :stroke color
                 :width 2
                 :points {:show true :size 4}}]

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp-seconds]
                                              (.toLocaleString (js/Date. (* timestamp-seconds 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label (or y-label "Value")
                            :labelSize 14}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000) (/ end-time-millis 1000)])}
                             :y {:auto true}}
                    :legend {:show true
                             :live true}})
                 [height y-label start-time-millis end-time-millis])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
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
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn []
                 (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
       ($ :div {:ref target-ref}))))

(defn- prepare-percentage-chart-data
  "Transform analytics data into uPlot format for percentage line charts.
  
  Args:
  - telemetry-data: Map of {bucket-number {metadata-key {metric-key value}}}
  - granularity: Granularity in seconds
  - metric-key: The metric key to extract (e.g., :mean)
  - start-time-millis: Start of the time window
  - end-time-millis: End of the time window
  
  Returns:
  - uPlot data format [[timestamps] [values]] where values are in 0-100 range"
  [telemetry-data granularity metric-key start-time-millis end-time-millis]
  (println "=== prepare-percentage-chart-data ===")
  (println "metric-key:" metric-key)
  (println "telemetry-data (first 2 buckets):" (take 2 telemetry-data))

  (if (seq telemetry-data)
    (let [sorted-buckets (sort (keys telemetry-data))

          ;; Get metadata key from the first bucket only
          first-bucket (first sorted-buckets)
          first-bucket-data (get telemetry-data first-bucket)
          first-bucket-metadata-keys (when first-bucket
                                       (keys first-bucket-data))
          metadata-key-to-use (or (first (filter #(= "_aor/default" %) first-bucket-metadata-keys))
                                  (first first-bucket-metadata-keys)
                                  "_aor/default")

          _ (println "first-bucket:" first-bucket)
          _ (println "first-bucket-data:" first-bucket-data)
          _ (println "metadata-key-to-use:" metadata-key-to-use)

          timestamps (mapv #(* % granularity) sorted-buckets)
          ;; Convert 0.0-1.0 values to 0-100 percentages
          ;; Try both paths - with and without _aor/default layer
          values (mapv
                  (fn [bucket]
                    (let [;; Try path with _aor/default (for both split and no-split cases)
                          path-with-default [bucket metadata-key-to-use "_aor/default" metric-key]
                          ;; Try direct path (fallback)
                          path-direct [bucket metadata-key-to-use metric-key]
                          value-with-default (get-in telemetry-data path-with-default)
                          value-direct (get-in telemetry-data path-direct)]
                      (when (= bucket first-bucket)
                        (println "  path-with-default:" path-with-default "=>" value-with-default)
                        (println "  path-direct:" path-direct "=>" value-direct))
                      (when-let [val (or value-with-default value-direct)]
                        (* val 100))))
                  sorted-buckets)]
      (println "values (first 5):" (take 5 values))
      [timestamps values])
    ;; Empty data
    (do
      (println "Empty telemetry-data!")
      [[(/ start-time-millis 1000) (/ end-time-millis 1000)] [nil nil]])))

(defui analytics-percentage-chart
  "A line chart for percentage data (0-100%).
  
  Props:
  - :data - Analytics telemetry data {bucket-number {metadata-key {metric-key value}}}
  - :granularity - Time granularity in seconds
  - :metric-key - Which metric to display (e.g., :mean)
  - :start-time-millis - Start of time window
  - :end-time-millis - End of time window
  - :height - Chart height in pixels (optional, defaults to 300)
  - :title - Chart title (optional)
  - :color - Line color (optional, defaults to green)"
  [{:keys [data granularity metric-key start-time-millis end-time-millis height title color]}]
  (let [height (or height 300)
        color (or color "#10b981")

        container-ref (useRef nil)

        ;; Transform data for uPlot
        chart-data (uix/use-memo
                    (fn [] (prepare-percentage-chart-data data granularity metric-key start-time-millis end-time-millis))
                    [data granularity metric-key start-time-millis end-time-millis])

        ;; Line chart series configuration
        series [{:label "Success Rate"
                 :stroke color
                 :width 2
                 :points {:show true :size 4}}]

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp-seconds]
                                              (.toLocaleString (js/Date. (* timestamp-seconds 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label "Percentage (%)"
                            :labelSize 14
                            :values (fn [_self splits]
                                      ;; Format y-axis as percentages
                                      (.map splits (fn [v] (str (int v) "%"))))}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000) (/ end-time-millis 1000)])}
                             :y {:auto true
                                 :range (fn [_self _min max]
                                          ;; Force y-axis to show 0-100%
                                          #js [0 100])}}
                    :legend {:show true
                             :live true}})
                 [height start-time-millis end-time-millis])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
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
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn []
                 (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
       ($ :div {:ref target-ref}))))

(defn- prepare-multi-category-data
  "Transform analytics data with multiple categories into uPlot format.
  
  Args:
  - telemetry-data: Map of {bucket-number {metadata-key {metric-key value}}} (no split)
                    or {bucket-number {metadata-value {category {metric-key value}}}} (with split)
  - granularity: Granularity in seconds
  - metric-key: The metric key to extract (e.g., :rest-sum)
  - categories: List of category strings (e.g., [\"input\" \"output\" \"total\"])
  - metadata-key: The metadata key being split by (nil if no split)
  - start-time-millis: Start of the time window
  - end-time-millis: End of the time window
  
  Returns:
  - uPlot data format [[timestamps] [category1-values] [category2-values] ...]"
  [telemetry-data granularity metric-key categories metadata-key start-time-millis end-time-millis]
  (println "=== prepare-multi-category-data ===")
  (println "metadata-key:" metadata-key)
  (println "categories:" categories)
  (println "metric-key:" metric-key)
  (println "telemetry-data (first 2 buckets):" (take 2 telemetry-data))

  (if (seq telemetry-data)
    (let [sorted-buckets (sort (keys telemetry-data))

          ;; Get metadata value from first bucket if metadata split is active
          first-bucket (first sorted-buckets)
          first-bucket-data (get telemetry-data first-bucket)
          metadata-value (when metadata-key
                           (first (keys first-bucket-data)))

          _ (println "first-bucket:" first-bucket)
          _ (println "first-bucket-data:" first-bucket-data)
          _ (println "metadata-value:" metadata-value)

          timestamps (mapv #(* % granularity) sorted-buckets)

          ;; Extract values for each category
          category-series (mapv
                           (fn [category]
                             (let [values (mapv
                                           (fn [bucket]
                                             (let [path (if metadata-key
                                                          [bucket metadata-value category metric-key]
                                                          [bucket category metric-key])
                                                   value (get-in telemetry-data path)]
                                               (when (= bucket first-bucket)
                                                 (println "  category:" category "path:" path "value:" value))
                                               value))
                                           sorted-buckets)]
                               (println "category:" category "values (first 5):" (take 5 values))
                               values))
                           categories)]
      (println "Final result - timestamps (first 5):" (take 5 timestamps))
      (into [timestamps] category-series))
    ;; Empty data
    (do
      (println "Empty telemetry-data!")
      (let [empty-timestamps [(/ start-time-millis 1000) (/ end-time-millis 1000)]
            empty-series (repeat (count categories) [nil nil])]
        (into [empty-timestamps] empty-series)))))

(defui analytics-multi-category-chart
  "A multi-line chart for data with multiple categories (e.g., token types).
  
  Props:
  - :data - Analytics telemetry data {bucket-number {category {metric-key value}}}
  - :granularity - Time granularity in seconds
  - :metric-key - Which metric to display (e.g., :rest-sum)
  - :categories - List of category strings to display
  - :metadata-key - The metadata key being split by (nil if no split)
  - :start-time-millis - Start of time window
  - :end-time-millis - End of time window
  - :height - Chart height in pixels (optional, defaults to 300)
  - :title - Chart title (optional)
  - :y-label - Y-axis label (optional)"
  [{:keys [data granularity metric-key categories metadata-key start-time-millis end-time-millis height title y-label]}]
  (let [height (or height 300)

        container-ref (useRef nil)

        ;; Transform data for uPlot
        chart-data (uix/use-memo
                    (fn [] (prepare-multi-category-data data granularity metric-key categories metadata-key start-time-millis end-time-millis))
                    [data granularity metric-key categories metadata-key start-time-millis end-time-millis])

        ;; Colors for categories
        category-colors {"input" "#3b82f6" ; blue
                         "output" "#10b981" ; green
                         "total" "#8b5cf6"} ; purple

        ;; Line chart series configuration for each category
        series (mapv
                (fn [category]
                  {:label (str (clojure.string/capitalize category))
                   :stroke (get category-colors category "#6b7280")
                   :width 2
                   :points {:show true :size 4}})
                categories)

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp-seconds]
                                              (.toLocaleString (js/Date. (* timestamp-seconds 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label (or y-label "Value")
                            :labelSize 14}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000) (/ end-time-millis 1000)])}
                             :y {:auto true}}
                    :legend {:show true
                             :live true}})
                 [height y-label categories start-time-millis end-time-millis])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
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
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn []
                 (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
       ;; Add style for vertical legend
       ($ :style ".uplot-vertical-legend .u-legend { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
                  .uplot-vertical-legend .u-legend .u-series { display: flex; align-items: center; gap: 8px; }
                  .uplot-vertical-legend .u-legend .u-series > * { display: inline-block; }
                  .uplot-vertical-legend .u-legend .u-marker { width: 12px; height: 12px; border-radius: 50%; }")
       ($ :div.uplot-vertical-legend {:ref target-ref}))))

(defn- prepare-model-success-rate-data
  "Transform analytics data for model success rate into uPlot format.
  For each bucket, calculates: success_count / (success_count + failure_count) * 100
  
  Note: This function expects the telemetry data to have \"success \" and \"failure \" as
  the category keys. When no metadata split is active, they're at level 2. When a metadata
  split is active, they're at level 3 (after the metadata value), with an additional
  \"_aor/default\" layer at level 4.
  
  Args:
  - telemetry-data: Map of {bucket-number {category {metric-key value}}} (no split)
                    or {bucket-number {metadata-value {category {metric-key value}}}} (with split)
  - granularity: Granularity in seconds
  - metric-key: The metric key to extract (e.g., :rest-sum)
  - metadata-key: The metadata key being split by (nil if no split)
  - start-time-millis: Start of the time window
  - end-time-millis: End of the time window
  
  Returns:
  - uPlot data format [[timestamps] [percentage-values]]"
  [telemetry-data granularity metric-key metadata-key start-time-millis end-time-millis]
  (println "=== prepare-model-success-rate-data ===")
  (println "metadata-key:" metadata-key)
  (println "metric-key:" metric-key)
  (println "telemetry-data (first 2 buckets):" (take 2 telemetry-data))

  (if (seq telemetry-data)
    (let [sorted-buckets (sort (keys telemetry-data))

          ;; Get metadata value from first bucket if metadata split is active
          first-bucket (first sorted-buckets)
          first-bucket-data (get telemetry-data first-bucket)
          metadata-value (when metadata-key
                           (first (keys first-bucket-data)))

          _ (println "first-bucket:" first-bucket)
          _ (println "first-bucket-data:" first-bucket-data)
          _ (println "metadata-value:" metadata-value)

          timestamps (mapv #(* % granularity) sorted-buckets)

          ;; Calculate success rate for each bucket
          values (mapv
                  (fn [bucket]
                    (let [;; Build paths - try with _aor/default layer first
                          success-path-with-default (if metadata-key
                                                      [bucket metadata-value "success " "_aor/default" metric-key]
                                                      [bucket "success " "_aor/default" metric-key])
                          failure-path-with-default (if metadata-key
                                                      [bucket metadata-value "failure " "_aor/default" metric-key]
                                                      [bucket "failure " "_aor/default" metric-key])
                          ;; Fallback paths without _aor/default
                          success-path-direct (if metadata-key
                                                [bucket metadata-value "success " metric-key]
                                                [bucket "success " metric-key])
                          failure-path-direct (if metadata-key
                                                [bucket metadata-value "failure " metric-key]
                                                [bucket "failure " metric-key])

                          success-count (or (get-in telemetry-data success-path-with-default)
                                            (get-in telemetry-data success-path-direct)
                                            0)
                          failure-count (or (get-in telemetry-data failure-path-with-default)
                                            (get-in telemetry-data failure-path-direct)
                                            0)
                          total (+ success-count failure-count)]
                      (when (= bucket first-bucket)
                        (println "  success-path-with-default:" success-path-with-default "=>" (get-in telemetry-data success-path-with-default))
                        (println "  success-path-direct:" success-path-direct "=>" (get-in telemetry-data success-path-direct))
                        (println "  success-count:" success-count)
                        (println "  failure-count:" failure-count)
                        (println "  total:" total))
                      (if (pos? total)
                        (* (/ success-count total) 100)
                        nil))) ;; Return nil if no data
                  sorted-buckets)]
      (println "values (first 5):" (take 5 values))
      (println "Final result - timestamps (first 5):" (take 5 timestamps))
      [timestamps values])
    ;; Empty data
    (do
      (println "Empty telemetry-data!")
      [[(/ start-time-millis 1000) (/ end-time-millis 1000)] [nil nil]])))

(defui analytics-model-success-rate-chart
  "A line chart for model success rate with special calculation.
  
  Props:
  - :data - Analytics telemetry data {bucket-number {category {metric-key value}}}
  - :granularity - Time granularity in seconds
  - :metric-key - Which metric to display (e.g., :rest-sum)
  - :metadata-key - The metadata key being split by (nil if no split)
  - :start-time-millis - Start of time window
  - :end-time-millis - End of time window
  - :height - Chart height in pixels (optional, defaults to 300)
  - :title - Chart title (optional)
  - :color - Line color (optional, defaults to green)"
  [{:keys [data granularity metric-key metadata-key start-time-millis end-time-millis height title color]}]
  (let [height (or height 300)
        color (or color "#10b981")

        container-ref (useRef nil)

        ;; Transform data for uPlot
        chart-data (uix/use-memo
                    (fn [] (prepare-model-success-rate-data data granularity metric-key metadata-key start-time-millis end-time-millis))
                    [data granularity metric-key metadata-key start-time-millis end-time-millis])

        ;; Line chart series configuration
        series [{:label "Success Rate"
                 :stroke color
                 :width 2
                 :points {:show true :size 4}}]

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :values (fn [self splits]
                                      (.map splits
                                            (fn [timestamp-seconds]
                                              (.toLocaleString (js/Date. (* timestamp-seconds 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label "Percentage (%)"
                            :labelSize 14
                            :values (fn [_self splits]
                                      ;; Format y-axis as percentages
                                      (.map splits (fn [v] (str (int v) "%"))))}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000) (/ end-time-millis 1000)])}
                             :y {:auto true
                                 :range (fn [_self _min _max]
                                          ;; Force y-axis to show 0-100%
                                          #js [0 100])}}
                    :legend {:show true
                             :live true}})
                 [height start-time-millis end-time-millis])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
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
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn []
                 (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div.w-full
       {:ref container-ref}
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-3 title))
       ($ :div {:ref target-ref}))))
