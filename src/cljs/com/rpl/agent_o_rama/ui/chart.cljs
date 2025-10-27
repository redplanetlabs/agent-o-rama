(ns com.rpl.agent-o-rama.ui.chart
  "uPlot-based charting components for time-series data visualization."
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["react" :refer [useRef useLayoutEffect useEffect]]
   ["uplot" :as uPlot]
   [clojure.string :as str]
   [com.rpl.specter :as sp :refer [select select-one ALL MAP-KEYS MAP-VALS collect-one]]))

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
                      0.25 0.5
                      0.5 1
                      0.75 1.5
                      0.9 2
                      0.99 3
                      :max 4}]
    (sort-by (fn [k]
               (get metric-order k 999)) ; Unknown metrics go to end
             metrics)))

(defui metrics-selector
  "A dropdown component for selecting which metrics to display.
  
  Props:
  - :available-metrics - Set of all available metrics (e.g., #{:min :max 0.5 0.9 0.99})
  - :selected-metrics - Set of currently selected metrics
  - :on-change - Function called with new set of selected metrics"
  [{:keys [available-metrics selected-metrics on-change]}]
  (let [[dropdown-open? set-dropdown-open] (uix/use-state false)
        button-ref (useRef nil)
        [dropdown-position set-dropdown-position] (uix/use-state nil)

        ;; Calculate dropdown position when opened
        _ (uix/use-effect
           (fn []
             (when (and dropdown-open? (.-current button-ref))
               (let [rect (.getBoundingClientRect (.-current button-ref))]
                 (set-dropdown-position {:top (+ (.-bottom rect) 4)
                                         :right (- js/window.innerWidth (.-right rect))})))
             js/undefined)
           #js [dropdown-open?])

        ;; Close dropdown when clicking outside
        _ (uix/use-effect
           (fn []
             (when dropdown-open?
               (let [handle-click (fn [e]
                                    (set-dropdown-open false))]
                 (.addEventListener js/document "click" handle-click)
                 (fn []
                   (.removeEventListener js/document "click" handle-click))))
             js/undefined)
           #js [dropdown-open?])

        ;; Helper to format metric label
        format-metric-label (fn [m]
                              (cond
                                (keyword? m) (name m)
                                (number? m) (str "p" (int (* m 100)))
                                :else (str m)))

        ;; Toggle metric selection
        toggle-metric (fn [metric]
                        (let [new-selection (if (contains? selected-metrics metric)
                                              (if (> (count selected-metrics) 1) ; Don't allow deselecting all
                                                (disj selected-metrics metric)
                                                selected-metrics)
                                              (conj selected-metrics metric))]
                          (when on-change
                            (on-change new-selection))))]

    ($ :div.relative
       ($ :button.px-3.py-1.text-sm.border.border-gray-300.rounded.bg-white.hover:bg-gray-50.flex.items-center.gap-2
          {:ref button-ref
           :on-click (fn [e]
                       (.preventDefault e)
                       (.stopPropagation e)
                       (set-dropdown-open not))}
          "Metrics"
          ($ :svg.w-4.h-4 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor"}
             ($ :path {:fill-rule "evenodd" :d "M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" :clip-rule "evenodd"})))

       ;; Dropdown menu with fixed positioning
       (when (and dropdown-open? dropdown-position)
         ($ :div.fixed.w-48.bg-white.border.border-gray-300.rounded-md.shadow-lg.z-50
            {:style {:top (str (:top dropdown-position) "px")
                     :right (str (:right dropdown-position) "px")}
             :on-click (fn [e] (.stopPropagation e))}
            ($ :div.py-1
               (for [metric (sort-metrics available-metrics)]
                 ($ :label.flex.items-center.px-3.py-2.hover:bg-gray-100.cursor-pointer
                    {:key (str metric)}
                    ($ :input.mr-2
                       {:type "checkbox"
                        :checked (contains? selected-metrics metric)
                        :on-change (fn [e]
                                     (.stopPropagation e)
                                     (toggle-metric metric))})
                    ($ :span.text-sm.text-gray-700 (format-metric-label metric))))))))))

;; ============================================================================
;; UNIFIED ANALYTICS CHART
;; ============================================================================

(defn- build-series-specs
  "Build series specifications for a chart variant.
  
  Returns vector of specs: [{:path [...] :metadata-val mv :value-fn fn} ...]
  Each spec describes one line on the chart."
  [variant variant-opts metadata-values]
  (case variant
    :single-metric
    (let [{:keys [metric-key]} variant-opts
          path ["_aor/default" metric-key]]
      (if metadata-values
        (mapv (fn [mv] {:path path :metadata-val mv :value-fn identity}) metadata-values)
        [{:path path :metadata-val nil :value-fn identity}]))

    :multi-metric
    (let [{:keys [metrics]} variant-opts
          sorted-metrics (sort-metrics metrics)]
      (if metadata-values
        (vec (for [metric sorted-metrics, mv metadata-values]
               {:path ["_aor/default" metric] :metadata-val mv :value-fn identity}))
        (mapv (fn [metric]
                {:path ["_aor/default" metric] :metadata-val nil :value-fn identity})
              sorted-metrics)))

    :percentage
    (let [{:keys [metric-key]} variant-opts
          path ["_aor/default" metric-key]
          scale-fn #(when % (* % 100))]
      (if metadata-values
        (mapv (fn [mv] {:path path :metadata-val mv :value-fn scale-fn}) metadata-values)
        [{:path path :metadata-val nil :value-fn scale-fn}]))

    :multi-category
    (let [{:keys [metric-key categories]} variant-opts]
      (if metadata-values
        (vec (for [category categories, mv metadata-values]
               {:path [category metric-key] :metadata-val mv :value-fn identity}))
        (mapv (fn [category]
                {:path [category metric-key] :metadata-val nil :value-fn identity})
              categories)))

    :computed-percentage
    (let [{:keys [metric-key]} variant-opts
          compute-fn (fn [telemetry-data bucket mv]
                       (let [base-path (vec (concat [bucket] (when mv [mv])))
                             success (or (select-one (vec (concat base-path ["success" metric-key])) telemetry-data) 0)
                             failure (or (select-one (vec (concat base-path ["failure" metric-key])) telemetry-data) 0)
                             total (+ success failure)]
                         (when (pos? total) (* (/ success total) 100))))]
      (if metadata-values
        (mapv (fn [mv] {:metadata-val mv :computed? true :compute-fn compute-fn}) metadata-values)
        [{:metadata-val nil :computed? true :compute-fn compute-fn}]))))

(defn- evaluate-series-spec
  "Evaluate a series spec across all buckets to produce a time series.
  
  Returns: [val1 val2 ... valN] where N = (count sorted-buckets)"
  [spec sorted-buckets telemetry-data]
  (if (:computed? spec)

    ;; Computed series: call compute-fn for each bucket
    (mapv #((:compute-fn spec) telemetry-data % (:metadata-val spec)) sorted-buckets)
    ;; Simple series: navigate path and apply value-fn
    (mapv (fn [bucket]
            (let [full-path (vec (concat [bucket]
                                         (when (:metadata-val spec) [(:metadata-val spec)])
                                         (:path spec)))
                  value (select-one full-path telemetry-data)]
              ((:value-fn spec) value)))
          sorted-buckets)))

(defn- prepare-chart-data
  "Universal data preparation for all chart variants using series specs.
  
  Returns: Map with :data (uPlot format) and :metadata-values (vector or nil)"
  [telemetry-data granularity metadata-key start-time-millis end-time-millis variant variant-opts]

  (if (seq telemetry-data)
    (let [;; Phase 1: Extract context
          sorted-buckets (sort (keys telemetry-data))
          timestamps (mapv #(* % granularity) sorted-buckets)
          metadata-values (when metadata-key
                            (vec (take 5 (distinct (select [MAP-VALS MAP-KEYS] telemetry-data)))))

          ;; Phase 2: Build series specifications
          series-specs (build-series-specs variant variant-opts metadata-values)

          ;; Phase 3: Evaluate each spec to produce time series
          series-data (mapv #(evaluate-series-spec % sorted-buckets telemetry-data) series-specs)]

      {:data (into [timestamps] series-data)
       :metadata-values metadata-values})

    ;; Empty data - generate 60 evenly-spaced timestamps to match bucket structure
    ;; This ensures consistent x-axis rendering with charts that have data
    (let [start-seconds (/ start-time-millis 1000)
          end-seconds (/ end-time-millis 1000)
          ;; Generate 60 evenly-spaced buckets
          timestamps (mapv (fn [i]
                             (+ start-seconds (* i (/ (- end-seconds start-seconds) 59))))
                           (range 60))
          num-series (case variant
                       :single-metric 1
                       :multi-metric (count (:metrics variant-opts))
                       :percentage 1
                       :multi-category (count (:categories variant-opts))
                       :computed-percentage 1)]
      {:data (into [timestamps] (repeat num-series (vec (repeat 60 nil))))
       :metadata-values nil})))

(defn- build-series-config
  "Build uPlot series configuration based on variant and data.
  
  Args:
  - variant: Chart type
  - variant-opts: Variant-specific options
  - metadata-values: Vector of metadata values if split is active (or nil)
  - color: Optional color override
  
  Returns:
  - Vector of series configs [{:label :stroke :width :points :spanGaps} ...]"
  [variant variant-opts metadata-values color]

  (let [;; Standard colors
        metric-colors {:min "#10b981" ; green
                       :max "#ef4444" ; red
                       0.25 "#06b6d4" ; cyan
                       0.5 "#3b82f6" ; blue
                       0.75 "#f59e0b" ; amber
                       0.9 "#f59e0b" ; amber
                       0.99 "#8b5cf6"} ; purple

        category-colors {"input" "#3b82f6" ; blue
                         "output" "#10b981" ; green
                         "total" "#8b5cf6"} ; purple

        series-colors ["#3b82f6" "#10b981" "#f59e0b" "#ef4444" "#8b5cf6" "#ec4899" "#14b8a6" "#f97316"]

        format-metric (fn [m]
                        (cond
                          (keyword? m) (name m)
                          (number? m) (str "p" (int (* m 100)))
                          :else (str m)))]

    (case variant
      :single-metric
      (if (seq metadata-values)
        (mapv (fn [idx mv]
                {:label (str mv)
                 :stroke (get series-colors idx "#6b7280")
                 :width 2
                 :points {:show true :size 4}
                 :spanGaps true})
              (range)
              metadata-values)
        [{:label (:y-label variant-opts "Value")
          :stroke (or color "#3b82f6")
          :width 2
          :points {:show true :size 4}
          :spanGaps true}])

      :multi-metric
      (let [metrics (sort-metrics (:metrics variant-opts))]
        (if (seq metadata-values)
          (vec (mapcat (fn [metric]
                         (map-indexed (fn [idx mv]
                                        {:label (str (format-metric metric) " (" mv ")")
                                         :stroke (get series-colors idx "#6b7280")
                                         :width 2
                                         :points {:show true :size 4}
                                         :spanGaps true})
                                      metadata-values))
                       metrics))
          (mapv (fn [metric]
                  {:label (format-metric metric)
                   :stroke (get metric-colors metric "#6b7280")
                   :width 2
                   :points {:show true :size 4}
                   :spanGaps true})
                metrics)))

      :percentage
      (let [pct-formatter (fn [_self v] (when v (str (int v) "%")))]
        (if (seq metadata-values)
          (mapv (fn [idx mv]
                  {:label (str mv)
                   :stroke (get series-colors idx "#6b7280")
                   :width 2
                   :points {:show true :size 4}
                   :spanGaps true
                   :value pct-formatter})
                (range)
                metadata-values)
          [{:label "Success Rate"
            :stroke (or color "#10b981")
            :width 2
            :points {:show true :size 4}
            :spanGaps true
            :value pct-formatter}]))

      :multi-category
      (let [{:keys [categories]} variant-opts]
        (if (seq metadata-values)
          ;; Multiple series per category (one per metadata value)
          (vec (mapcat (fn [cat]
                         (map-indexed (fn [idx mv]
                                        {:label (str (str/capitalize cat) " (" mv ")")
                                         :stroke (get series-colors idx "#6b7280")
                                         :width 2
                                         :points {:show true :size 4}
                                         :spanGaps true})
                                      metadata-values))
                       categories))
          ;; Single series per category - use series-colors array by index
          (mapv (fn [idx cat]
                  {:label (str/capitalize cat)
                   :stroke (get series-colors idx "#6b7280")
                   :width 2
                   :points {:show true :size 4}
                   :spanGaps true})
                (range)
                categories)))

      :computed-percentage
      (let [pct-formatter (fn [_self v] (when v (str (int v) "%")))]
        (if (seq metadata-values)
          (mapv (fn [idx mv]
                  {:label (str mv)
                   :stroke (get series-colors idx "#6b7280")
                   :width 2
                   :points {:show true :size 4}
                   :spanGaps true
                   :value pct-formatter})
                (range)
                metadata-values)
          [{:label "Success Rate"
            :stroke (or color "#10b981")
            :width 2
            :points {:show true :size 4}
            :spanGaps true
            :value pct-formatter}])))))

(defui analytics-chart
  "Unified analytics chart component that handles all chart variants.
  
  Props:
  - :data - Raw telemetry data from backend
  - :granularity - Time granularity in seconds
  - :metadata-key - Metadata key for splitting (nil if no split)
  - :start-time-millis - Start of time window
  - :end-time-millis - End of time window
  - :height - Chart height (optional, defaults to 300)
  - :title - Chart title (optional)
  - :y-label - Y-axis label (optional)
  - :color - Color override for single-series charts (optional)
  - :variant - Chart type: :single-metric, :multi-metric, :percentage, :multi-category, :computed-percentage
  - :variant-opts - Variant-specific options (see prepare-chart-data for details)"
  [{:keys [data granularity metadata-key start-time-millis end-time-millis
           height title y-label color variant variant-opts]}]

  (let [height (or height 300)
        container-ref (useRef nil)

        ;; For multi-metric charts with metadata split, allow metric selection
        all-metrics (when (and (= variant :multi-metric) metadata-key)
                      (:metrics variant-opts))
        [selected-metrics set-selected-metrics] (uix/use-state (when all-metrics #{0.5}))

        ;; Determine final variant options (with selected metrics if applicable)
        final-variant-opts (if (and all-metrics selected-metrics)
                             (assoc variant-opts :metrics selected-metrics)
                             variant-opts)

        ;; Transform data - returns both data and metadata-values used
        prepared (uix/use-memo
                  (fn [] 
                    (prepare-chart-data data granularity metadata-key
                                        start-time-millis end-time-millis
                                        variant final-variant-opts))
                  [data granularity metadata-key start-time-millis end-time-millis
                   variant final-variant-opts])
        
        chart-data (:data prepared)
        metadata-values (:metadata-values prepared)

        ;; Build series configuration using same metadata-values
        series (uix/use-memo
                (fn [] (build-series-config variant final-variant-opts metadata-values color))
                [variant final-variant-opts metadata-values color])

        ;; Determine if this is a percentage chart (affects y-axis)
        is-percentage? (or (= variant :percentage) (= variant :computed-percentage))

        ;; Build uPlot options
        options (uix/use-memo
                 (fn []
                   {:width 100
                    :height height
                    :series (into [{:label "Time"}] series)
                    :axes [{:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :values (fn [_self splits]
                                      (.map splits
                                            (fn [ts]
                                              (.toLocaleString (js/Date. (* ts 1000))
                                                               "en-US"
                                                               #js {:hour "numeric"
                                                                    :minute "2-digit"
                                                                    :hour12 true}))))}
                           {:stroke "#64748b"
                            :grid {:show true :stroke "#e2e8f0" :width 1}
                            :ticks {:show true :stroke "#cbd5e1"}
                            :label (or y-label "Value")
                            :labelSize 14
                            :values (when is-percentage?
                                      (fn [_self splits]
                                        (.map splits (fn [v] (str (int v) "%")))))}]
                    :scales {:x {:time true
                                 :range (fn [_self _min _max]
                                          #js [(/ start-time-millis 1000)
                                               (/ end-time-millis 1000)])}
                             :y (if is-percentage?
                                  {:auto true
                                   :range (fn [_self _min _max] #js [0 100])}
                                  {:auto true})}
                    :legend {:show true :live true}})
                 [height y-label series start-time-millis end-time-millis is-percentage?])

        [target-ref chart-ref] (use-uplot options chart-data)

        ;; Handle resize
        _ (uix/use-effect
           (fn []
             (let [get-size (fn []
                              (when-let [container (.-current container-ref)]
                                (let [rect (.getBoundingClientRect container)
                                      w (.-width rect)]
                                  (when (> w 0) {:width w :height height}))))
                   handle-resize (fn []
                                   (when-let [chart (.-current chart-ref)]
                                     (when-let [size (get-size)]
                                       (.setSize chart (clj->js size)))))]
               (js/requestAnimationFrame handle-resize)
               (.addEventListener js/window "resize" handle-resize)
               (fn [] (.removeEventListener js/window "resize" handle-resize))))
           [height chart-data])]

    ($ :div.w-full
       {:ref container-ref}
       ;; Header with title and optional metrics selector
       ($ :div.flex.items-center.justify-between.mb-3
          (when title
            ($ :h4.text-base.font-medium.text-gray-700 title))

          ;; Metrics selector for multi-metric charts with metadata split
          (when (and all-metrics metadata-key)
            ($ metrics-selector
               {:available-metrics all-metrics
                :selected-metrics selected-metrics
                :on-change set-selected-metrics})))

       ;; Add style tag for vertical legend layout
       ($ :style ".uplot-vertical-legend .u-legend { display: flex; flex-direction: column; align-items: flex-start; gap: 4px; }
                  .uplot-vertical-legend .u-legend .u-series { display: flex; align-items: center; gap: 8px; }
                  .uplot-vertical-legend .u-legend .u-series > * { display: inline-block; }
                  .uplot-vertical-legend .u-legend .u-marker { width: 12px; height: 12px; border-radius: 50%; }")

       ;; Chart container with vertical legend class
       ($ :div.uplot-vertical-legend {:ref target-ref}))))
