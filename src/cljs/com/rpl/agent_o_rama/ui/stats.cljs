(ns com.rpl.agent-o-rama.ui.stats
  (:require
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   [clojure.string :as str]

   [uix.core :as uix :refer [defui defhook $]]
   ["uplot" :as uplot]
   ["css-element-queries/src/ResizeSensor" :as ResizeSensor]

   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]))

;; Dummy git SHA data
(def dummy-versions
  [{:sha "a1b2c3d" :message "feat: add new agent functionality" :date "2024-01-15"}
   {:sha "e4f5g6h" :message "fix: resolve connection timeout issues" :date "2024-01-14"}
   {:sha "i7j8k9l" :message "refactor: optimize graph rendering performance" :date "2024-01-13"}
   {:sha "m0n1o2p" :message "feat: implement multi-agent coordination" :date "2024-01-12"}
   {:sha "q3r4s5t" :message "fix: handle edge case in token counting" :date "2024-01-11"}
   {:sha "u6v7w8x" :message "docs: update API documentation" :date "2024-01-10"}])

(defui version-dropdown [{:keys [selected-version set-selected-version]}]
  (let [[is-open set-is-open] (uix/use-state false)]
    ($ :div {:className "relative inline-block text-left mb-6"}
       ($ :div
          ($ :button {:type "button"
                      :className "inline-flex w-full justify-between gap-x-1.5 rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 min-w-80"
                      :onClick #(set-is-open (not is-open))}
             ($ :div {:className "flex flex-col items-start"}
                ($ :div {:className "font-mono text-sm"}
                   (str "Version: " (:sha selected-version)))
                ($ :div {:className "text-xs text-gray-500 truncate max-w-64"}
                   (:message selected-version)))
             ($ :svg {:className "h-5 w-5 text-gray-400" :viewBox "0 0 20 20" :fill "currentColor"}
                ($ :path {:fillRule "evenodd"
                          :d "M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z"
                          :clipRule "evenodd"}))))

       (when is-open
         ($ :div {:className "absolute right-0 z-10 mt-2 w-80 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none"}
            ($ :div {:className "py-1"}
               (for [version dummy-versions]
                 ($ :button {:key (:sha version)
                             :className (str "block w-full px-4 py-3 text-left text-sm hover:bg-gray-100 "
                                             (when (= (:sha version) (:sha selected-version))
                                               "bg-gray-50"))
                             :onClick #(do (set-selected-version version)
                                           (set-is-open false))}
                    ($ :div {:className "flex justify-between items-start"}
                       ($ :div {:className "flex-1"}
                          ($ :div {:className "font-mono text-sm font-medium text-gray-900"}
                             (:sha version))
                          ($ :div {:className "text-xs text-gray-600 mt-1 pr-2"}
                             (:message version)))
                       ($ :div {:className "text-xs text-gray-400"}
                          (:date version)))))))))))

;; Generate dummy stats data for a selected node, varying by version
(defn generate-dummy-stats [node-id version]
  (let [version-seed (hash (:sha version))
        base-seed (hash node-id)
        combined-seed (+ version-seed base-seed)]
    {:execution-time (+ 50 (mod (* combined-seed 13) 500)) ; 50-550ms
     :tokens {:input (+ 100 (mod (* combined-seed 17) 1000))
              :output (+ 50 (mod (* combined-seed 19) 800))}
     :store-operations {:reads (mod (* combined-seed 7) 20)
                        :writes (mod (* combined-seed 11) 10)}
     :model-calls (+ 1 (mod (* combined-seed 23) 5))}))

;; Generate dummy overall stats for the entire graph, varying by version
(defn generate-overall-stats [version]
  (let [version-seed (hash (:sha version))]
    {:total-execution-time (+ 500 (mod (* version-seed 29) 2000)) ; 500-2500ms
     :total-tokens {:input (+ 2000 (mod (* version-seed 31) 5000))
                    :output (+ 1000 (mod (* version-seed 37) 4000))}
     :total-store-operations {:reads (+ 50 (mod (* version-seed 41) 100))
                              :writes (+ 20 (mod (* version-seed 43) 50))}
     :total-model-calls (+ 10 (mod (* version-seed 47) 20))
     :nodes-executed (+ 5 (mod (* version-seed 53) 10))}))

(defui stats-panel [{:keys [selected-node selected-version]}]
  (if selected-node
    ;; Show individual node stats
    (let [node-id (.-id selected-node)
          stats (generate-dummy-stats node-id selected-version)]
      ($ :div {:className "mt-6 p-6"}
         ($ :h3 {:className "text-lg font-semibold text-gray-800 mb-4"}
            (str "Stats for Node: " node-id))

         ($ :div {:className "grid grid-cols-2 md:grid-cols-4 gap-4"}

            ;; Execution Time
            ($ :div {:className "bg-blue-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-blue-600"}
                  "Execution Time")
               ($ :div {:className "text-2xl font-bold text-blue-900"}
                  (str (:execution-time stats) "ms")))

            ;; Model Calls
            ($ :div {:className "bg-green-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-green-600"}
                  "Model Calls")
               ($ :div {:className "text-2xl font-bold text-green-900"}
                  (:model-calls stats)))

            ;; Tokens
            ($ :div {:className "bg-purple-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-purple-600"}
                  "Tokens")
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "In: " (get-in stats [:tokens :input])))
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "Out: " (get-in stats [:tokens :output]))))

            ;; Store Operations
            ($ :div {:className "bg-orange-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-orange-600"}
                  "Store Operations")
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "R: " (get-in stats [:store-operations :reads])))
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "W: " (get-in stats [:store-operations :writes])))))))

    ;; Show overall stats when no node is selected
    (let [stats (generate-overall-stats selected-version)]
      ($ :div {:className "mt-6 p-6"}
         ($ :h3 {:className "text-lg font-semibold text-gray-800 mb-2"}
            "Overall Agent Graph Stats")
         ($ :p {:className "text-sm text-gray-600 mb-4"}
            "Aggregate performance metrics across all nodes. Click on a node to see individual stats.")

         ($ :div {:className "grid grid-cols-2 md:grid-cols-5 gap-4"}

            ;; Total Execution Time
            ($ :div {:className "bg-blue-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-blue-600"}
                  "Total Execution Time")
               ($ :div {:className "text-2xl font-bold text-blue-900"}
                  (str (:total-execution-time stats) "ms")))

            ;; Nodes Executed
            ($ :div {:className "bg-indigo-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-indigo-600"}
                  "Nodes Executed")
               ($ :div {:className "text-2xl font-bold text-indigo-900"}
                  (:nodes-executed stats)))

            ;; Total Model Calls
            ($ :div {:className "bg-green-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-green-600"}
                  "Total Model Calls")
               ($ :div {:className "text-2xl font-bold text-green-900"}
                  (:total-model-calls stats)))

            ;; Total Tokens
            ($ :div {:className "bg-purple-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-purple-600"}
                  "Total Tokens")
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "In: " (get-in stats [:total-tokens :input])))
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "Out: " (get-in stats [:total-tokens :output]))))

            ;; Total Store Operations
            ($ :div {:className "bg-orange-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-orange-600"}
                  "Total Store Operations")
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "R: " (get-in stats [:total-store-operations :reads])))
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "W: " (get-in stats [:total-store-operations :writes])))))))))

(defui agent-graph [{:keys [selected-version]}]
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        {:keys [data loading?]}
        (queries/use-sente-query {:query-key [:graph module-id agent-name]
                                  :sente-event [:query/graph module-id agent-name]})
        [selected-node set-selected-node] (uix/use-state nil)]
    (if loading?
      "...loading"
      ($ :div
         ($ agent-graph/graph {:initial-data data
                               :height "500px"
                               :selected-node selected-node
                               :set-selected-node set-selected-node
                               :fitView false})
         ($ stats-panel {:selected-node selected-node
                         :selected-version selected-version})))))

;; Generate sinusoidal data for charts
(defn generate-sine-data [frequency amplitude phase-offset points]
  (let [time-data (range 0 (* 2 Math/PI) (/ (* 2 Math/PI) points))
        sine-data (map #(+ amplitude (* amplitude (Math/sin (+ (* frequency %) phase-offset)))) time-data)]
    [time-data sine-data]))

;; Generate different sinusoidal patterns for different metrics
(defn generate-metric-data [metric-name points]
  (case metric-name
    "execution time" (generate-sine-data 2 100 0 points) ; Higher frequency, moderate amplitude
    "tokens" (generate-sine-data 1.5 500 0.5 points) ; Medium frequency, high amplitude
    "latency" (generate-sine-data 3 50 1 points) ; High frequency, low amplitude
    "model calls" (generate-sine-data 0.8 10 0.2 points) ; Low frequency, low amplitude
    "nodes executed" (generate-sine-data 1.2 8 0.8 points) ; Medium frequency, low amplitude
    (generate-sine-data 1 100 0 points))) ; Default

(defn- element-available-content-width
  [element]
  (let [computed-style (js/getComputedStyle element)
        padding-left (js/parseInt (.-paddingLeft computed-style))
        padding-right (js/parseInt (.-paddingRight computed-style))
        element-client-width (.-clientWidth element)]
    (- element-client-width padding-left padding-right)))

(defui chart [{:keys [dimension data]}]
  (let [container-ref (uix/use-ref)
        chart-instance-ref (uix/use-ref)
        [time-data sine-data] data]

    ;; Initialize uPlot chart
    (uix/use-effect
     (fn []
       (when (and @container-ref (not @chart-instance-ref))
         ;; Use requestAnimationFrame to ensure container is rendered
         (js/requestAnimationFrame
          (fn []
            (when @container-ref
              (let [;; Get the actual rendered width
                    container-rect (.getBoundingClientRect @container-ref)
                    chart-width (.-width container-rect)

                    ;; Format data for uPlot: [[x-values], [y-values]]
                    chart-data (clj->js [(js/Array.from time-data) (js/Array.from sine-data)])

                    ;; Chart options
                    opts (clj->js
                          {:width chart-width
                           :height 200
                           :padding [0 0 0 0]
                           :scales {:x {:time false}
                                    :y {}}
                           :series [{:font "12px sans-serif"
                                     :label "Time"} ;; X-axis series
                                    {:label dimension
                                     :stroke (case dimension
                                               "execution time" "#3b82f6" ;; blue
                                               "tokens" "#8b5cf6" ;; purple
                                               "latency" "#ef4444" ;; red
                                               "model calls" "#10b981" ;; green
                                               "nodes executed" "#f59e0b" ;; orange
                                               "#6b7280") ;; gray default
                                     :width 2}]
                           :axes [{:font "12px sans-serif"
                                   :stroke "#e5e7eb"
                                   :grid {:stroke "#f3f4f6"}
                                   :size 35}
                                  {:font "12px sans-serif"
                                   :stroke "#e5e7eb"
                                   :grid {:stroke "#f3f4f6"}
                                   :size 60}]})

                    ;; Create a div inside the container for uPlot
                    chart-div (js/document.createElement "div")]

                ;; Append the div to container
                (.appendChild @container-ref chart-div)

                ;; Create chart instance in the new div
                (let [chart-instance (uplot. opts chart-data chart-div)]
                  (reset! chart-instance-ref chart-instance)))))))
       ;; Cleanup function
       (fn []
         (when-let [chart-instance @chart-instance-ref]
           (.destroy chart-instance)
           (reset! chart-instance-ref nil))))
     [dimension data])

    ;; Automatic plot resizing effect
    (uix/use-effect
     (fn []
       (let [container-element @container-ref]
         (when (and container-element @chart-instance-ref)
           (let [resize-sensor (ResizeSensor.
                                container-element
                                (fn []
                                  (when-let [chart-instance @chart-instance-ref]
                                    (let [container-rect (.getBoundingClientRect container-element)
                                          new-width (.-width container-rect)]
                                      (.setSize
                                       chart-instance
                                       (clj->js
                                        {:width new-width
                                         :height 200}))))))]
              ;; Return cleanup function
             (fn []
               (.detach resize-sensor))))))
     [@container-ref])

    ($ :div {:className "bg-white p-4 rounded-lg shadow-sm border w-full"
             :ref container-ref
             :style {:minHeight "200px"}})))

(defui stats-timeseries []
  (let [points 100
        chart-data (into {}
                         (for [dimension
                               #_["execution time" "tokens" "latency" "model calls" "nodes executed"]
                               ["execution time"]]
                           [dimension (generate-metric-data dimension points)]))]
    ($ :div {:className "mt-6"}
       ($ :h3 {:className "text-lg font-semibold text-gray-800 mb-4"}
          "Performance Metrics Over Time")
       ($ :div {:className "space-y-6"}
          (for [[dimension data] chart-data]
            ($ chart {:key dimension
                      :dimension dimension
                      :data data}))))))

(defui stats []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        [selected-version set-selected-version] (uix/use-state (first dummy-versions))]
    ($ :div.p-4
       ($ version-dropdown {:selected-version selected-version
                            :set-selected-version set-selected-version})
       ($ agent-graph {:selected-version selected-version})
       ($ stats-timeseries))))