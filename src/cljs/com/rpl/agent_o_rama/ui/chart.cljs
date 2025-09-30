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
  - A React ref to attach to the target DOM element where the chart will render"
  [options data]
  (let [chart-ref (useRef nil)
        target-ref (useRef nil)]

    ;; Create or update chart when data or options change
    (useLayoutEffect
     (fn []
       (when (and (.-current target-ref) data (seq data))
         (let [current-chart (.-current chart-ref)]
           (if current-chart
             ;; If chart exists, update its data
             (.setData current-chart (clj->js data))
             ;; Otherwise, create a new chart instance
             ;; uPlot is the constructor function itself when imported with :as
             (let [new-chart (uPlot. (clj->js options) (clj->js data) (.-current target-ref))]
               (set! (.-current chart-ref) new-chart)))))
       ;; No cleanup needed here since we handle it in the separate effect below
       js/undefined)
     #js [data options]) ; Recreate chart if options change

    ;; Cleanup effect to destroy chart on unmount
    (useLayoutEffect
     (fn []
       (fn []
         (when-let [chart (.-current chart-ref)]
           (.destroy chart)
           (set! (.-current chart-ref) nil))))
     #js []) ; Empty deps = run only on mount/unmount

    ;; Return the ref to attach to the DOM element
    target-ref))

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
        chart-ref (use-uplot options data)]

    ($ :div.w-full
       (when title
         ($ :h4.text-base.font-medium.text-gray-700.mb-2.text-center title))
       ($ :div {:ref chart-ref}))))
