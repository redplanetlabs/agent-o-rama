(ns com.rpl.agent-o-rama.ui.invocations.gantt-trace
  "Row / timeline (Gantt-style) visualization of agent node invocations."
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["react" :refer [useState useMemo useEffect]]
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]
   [com.rpl.agent-o-rama.ui.invocations.gantt-model :as gantt-model]
   [com.rpl.agent-o-rama.ui.common :as common]))

(defn- node-key-str [id]
  (str id))

(defn format-duration-ms [ms]
  (cond
    (nil? ms) "—"
    (< ms 1) "<1ms"
    (< ms 1000) (str (int (js/Math.round ms)) "ms")
    (< ms 60000) (str (.toFixed (/ ms 1000) 2) "s")
    :else (str (.toFixed (/ ms 60000) 2) "m")))

(defn- nice-tick-step [span-ms max-ticks]
  (let [span-ms (max span-ms 1)
        raw (/ span-ms max-ticks)
        pow10 (js/Math.pow 10 (js/Math.floor (js/Math.log10 raw)))
        n (/ raw pow10)
        bucket (cond (<= n 1) 1
                     (<= n 2) 2
                     (<= n 5) 5
                     :else 10)]
    (* bucket pow10)))

(defn- row-bar-style
  [{:keys [start-ms end-ms t0 t1 row-in-progress? now-ms]}]
  (let [span (max (- t1 t0) 1)
        end (or (when row-in-progress? now-ms) end-ms start-ms)
        left-pct (* 100.0 (/ (- start-ms t0) span))
        width-pct (* 100.0 (/ (max (- end start-ms) 1) span))]
    {:left (str (.toFixed (min 100 (max 0 left-pct)) 2) "%")
     :width (str (.toFixed (min 100 (max 0.3 width-pct)) 2) "%")}))

(defn- bar-color-classes [data selected?]
  (let [base (cond
               (gn/agg-node? data)
               ["bg-amber-500"]

               (gn/starter-node? data)
               ["bg-emerald-500"]

               :else
               ["bg-indigo-500"])]
    (common/cn
     (conj base "rounded-sm" "shadow-sm" "min-h-[14px]")
     (when selected? "ring-2 ring-offset-1 ring-blue-500"))))

(defui gantt-trace-view
  [{:keys [graph-data real-edges implicit-edges root-invoke-id
            selected-node-id on-select-node is-complete]}]
  (let [[collapsed set-collapsed] (useState #{})
        [now-ms set-now-ms!] (useState (js/Date.now))
        row-model (useMemo (fn []
                             (gantt-model/build-row-model graph-data
                                                          real-edges
                                                          implicit-edges
                                                          root-invoke-id
                                                          collapsed))
                           #js [graph-data real-edges implicit-edges root-invoke-id collapsed])
        rows (:rows row-model)
        ;; tick clock while any row is in-progress
        has-in-progress? (some (fn [r]
                                 (let [d (:data r)]
                                   (and (:start-time-millis d) (not (:finish-time-millis d)))))
                               rows)]
    (useEffect
     (fn []
       (if has-in-progress?
         (let [id (js/setInterval #(set-now-ms! (js/Date.now)) 500)]
           (fn [] (js/clearInterval id)))
         js/undefined))
     #js [has-in-progress?])
    (let [now now-ms
          [t0 t1] (or (gantt-model/trace-time-bounds rows now) [0 1])
          span (- t1 t0)
          tick-step (nice-tick-step span 8)
          ticks (vec (take 24 (iterate #(+ % tick-step) t0)))
          root-label (:node (:data (first rows)))
          total-ms (or (gantt-model/total-root-ms rows) span)]
      ($ :div {:className "flex flex-col border border-gray-200 rounded-lg bg-white overflow-hidden"
               :data-testid "gantt-trace-view"}
         ($ :div {:className "flex items-center justify-between gap-3 px-3 py-2 border-b border-gray-200 bg-gray-50"})
         ;; header: time axis
         ($ :div {:className "grid grid-cols-[minmax(200px,32%)_1fr_minmax(72px,10%)] gap-2 px-2 py-1 border-b border-gray-100 text-[10px] font-mono text-gray-500"}
            ($ :span "Node")
            ($ :div.relative {:style {:height "18px"}}
               (for [[i t] (map-indexed vector ticks)
                     :while (<= t t1)
                     :let [pct (* 100.0 (/ (- t t0) span))]]
                 ($ :span {:key (str "tick-" i)
                           :className "absolute top-0 text-gray-400 whitespace-nowrap"
                           :style {:left (str pct "%")
                                   :transform "translateX(-50%)"}}
                    (format-duration-ms (- t t0)))))
            ($ :span.text-right "Duration"))
         ;; rows
         ($ :div {:className "max-h-[min(560px,calc(100vh-14rem))] overflow-y-auto overflow-x-hidden"}
            (for [row rows
                  :let [{:keys [node-id depth label data child-ids descendant-time-summary]} row
                        nid (node-key-str node-id)
                        has-children? (seq child-ids)
                        is-collapsed? (contains? collapsed node-id)
                        start (:start-time-millis data)
                        end (:finish-time-millis data)
                        row-in-progress? (and start (not end))
                        dur (when start (if end (- end start) (- now start)))
                        selected? (= selected-node-id node-id)
                        bar (when start
                              (row-bar-style {:start-ms start :end-ms end :t0 t0 :t1 t1
                                              :row-in-progress? row-in-progress?
                                              :now-ms now}))
                        collapsed-descendant-start (:start-time-millis descendant-time-summary)
                        collapsed-descendant-finish (:finish-time-millis descendant-time-summary)
                        collapsed-descendant-in-progress? (:in-progress? descendant-time-summary)
                        collapsed-descendant-dur (when collapsed-descendant-start
                                                   (if collapsed-descendant-in-progress?
                                                     (- now collapsed-descendant-start)
                                                     (- collapsed-descendant-finish collapsed-descendant-start)))
                        collapsed-descendant-bar
                        (when (and is-collapsed? collapsed-descendant-start)
                          (row-bar-style {:start-ms collapsed-descendant-start
                                          :end-ms collapsed-descendant-finish
                                          :t0 t0
                                          :t1 t1
                                          :row-in-progress? collapsed-descendant-in-progress?
                                          :now-ms now}))]]
              ($ :div {:key nid
                       :className (common/cn "grid grid-cols-[minmax(200px,32%)_1fr_minmax(72px,10%)] gap-2 items-center px-2 py-1.5 border-b border-gray-100 hover:bg-gray-50/80 cursor-pointer"
                                             (when selected? "bg-blue-50/80"))
                       :onClick #(when on-select-node (on-select-node node-id))}
                 ($ :div {:className "flex items-center gap-1 min-w-0 font-mono text-xs text-gray-800"
                          :style {:paddingLeft (str (* depth 12) "px")}}
                    (if has-children?
                      ($ :button {:type "button"
                                  :className "flex-shrink-0 w-5 h-5 flex items-center justify-center text-gray-500 hover:text-gray-800 rounded cursor-pointer"
                                  :aria-label (if is-collapsed? "Expand nested nodes" "Collapse nested nodes")
                                  :onClick (fn [e]
                                             (.stopPropagation e)
                                             (set-collapsed (fn [s]
                                                              (if (contains? s node-id)
                                                                (disj s node-id)
                                                                (conj s node-id)))))}
                         (if is-collapsed? "▸" "▾"))
                      ($ :span {:className "w-5 inline-block flex-shrink-0"}))
                    ($ :span {:className "truncate" :title label} label)
                    (when (gn/agg-node? data)
                      ($ :span {:className "flex-shrink-0 text-[9px] uppercase tracking-wide text-amber-700 bg-amber-100 px-1 rounded"}
                         "agg"))
                    (when (gn/starter-node? data)
                      ($ :span {:className "flex-shrink-0 text-[9px] uppercase tracking-wide text-emerald-700 bg-emerald-100 px-1 rounded"}
                         "start")))
                 ($ :div {:className "relative h-7 bg-gray-100 rounded overflow-hidden"}
                    ($ :div {:className "absolute inset-y-0 left-0 right-0 opacity-30 pointer-events-none"
                             :style {:background (str "repeating-linear-gradient(90deg, transparent, transparent calc(12.5% - 1px), #ddd calc(12.5% - 1px), #ddd 12.5%)")}})
                    (when bar
                      ($ :div {:className (common/cn "absolute top-1 bottom-1 flex items-center justify-end px-1"
                                                     (bar-color-classes data selected?))
                               :style {:left (:left bar)
                                       :width (:width bar)
                                       :minWidth "2px"}}))
                    (when collapsed-descendant-bar
                      ($ :div {:className "absolute top-1 bottom-1 rounded-sm border-2 border-gray-600 bg-transparent pointer-events-none"
                               :title (str "Collapsed children span: "
                                           (format-duration-ms collapsed-descendant-dur))
                               :style {:left (:left collapsed-descendant-bar)
                                       :width (:width collapsed-descendant-bar)
                                       :minWidth "2px"}})))
                 ($ :div {:className "text-right font-mono text-[11px] text-gray-600 tabular-nums"}
                    (format-duration-ms dur)
                    (when row-in-progress?
                      ($ :div {:className "text-[9px] text-amber-700 normal-case mt-0.5"}
                         (if is-complete "In progress (stuck?)" "In progress…"))))))
         (when (empty? rows)
           ($ :div {:className "p-8 text-center text-sm text-gray-500"}
              "No timed node data to display yet.")))))))

(defui gantt-trace-view-connected
  "Subscribes to re-frame graph data for the given invocation."
  [{:keys [invoke-id on-select-node]}]
  (let [graph-data (use-subscribe [:invocation/graph-data invoke-id])
        real-edges (use-subscribe [:invocation/real-edges invoke-id])
        implicit-edges (use-subscribe [:invocation/implicit-edges invoke-id])
        root-invoke-id (use-subscribe [:invocation/root-invoke-id invoke-id])
        selected-node-id (use-subscribe [:invocation/selected-node-id invoke-id])
        is-complete (use-subscribe [:invocation/is-complete invoke-id])]
    ($ gantt-trace-view {:graph-data graph-data
                         :real-edges real-edges
                         :implicit-edges implicit-edges
                         :root-invoke-id root-invoke-id
                         :selected-node-id selected-node-id
                         :on-select-node on-select-node
                         :is-complete is-complete})))
