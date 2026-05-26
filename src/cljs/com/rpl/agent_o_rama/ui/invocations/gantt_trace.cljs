(ns com.rpl.agent-o-rama.ui.invocations.gantt-trace
  "Row / timeline (Gantt-style) visualization of agent node invocations."
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["react" :refer [useState useMemo useEffect]]
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]
   [com.rpl.agent-o-rama.ui.common :as common]))

(defn- node-key-str [id]
  (str id))

(defn- children-by-parent [edges]
  (reduce (fn [m {:keys [source target]}]
            (let [s (node-key-str source)
                  t (node-key-str target)]
              (update m s (fnil conj []) t)))
          {}
          edges))

(defn- parents-by-target [edges target-id]
  "All source node ids with an edge to `target-id` (real or implicit)."
  (let [tid (node-key-str target-id)]
    (->> edges
         (filter #(= (node-key-str (:target %)) tid))
         (map :source)
         distinct
         vec)))

(defn- canonical-fan-in-parent
  "When many nodes emit to the same agg, the drawable graph adds an implicit edge from
  each agg-start output to the agg. That is correct for React Flow but duplicates the agg
  as a child under every parallel branch in a naive tree. Pick one parent: prefer the
  agg-start (`starter-node?`), else the earliest-start parent."
  [graph-data parent-ids]
  (when (seq parent-ids)
    (let [starters (filter #(gn/starter-node? (gn/graph-node-data graph-data %)) parent-ids)]
      (if (seq starters)
        (apply min-key str starters)
        (->> parent-ids
             (sort-by (fn [p]
                        [(or (:start-time-millis (gn/graph-node-data graph-data p))
                             js/Number.POSITIVE_INFINITY)
                         (str p)]))
             first)))))

(defn- collapse-fan-in-agg-children
  "For each agg node, if multiple parents point at it in `edges`, keep only one parent→child
  link in `children-map` so the Gantt shows a single fan-in subtree (matches mental model:
  one agg collecting many parallel workers)."
  [graph-data edges children-map]
  (let [agg-ids (->> (keys graph-data)
                    (filter #(gn/agg-node? (gn/graph-node-data graph-data %))))]
    (reduce (fn [m agg-id]
              (let [parents (parents-by-target edges agg-id)]
                (if (<= (count parents) 1)
                  m
                  (if-let [keeper (canonical-fan-in-parent graph-data parents)]
                    (reduce (fn [m2 p]
                              (if (= (str p) (str keeper))
                                m2
                                (update m2 (node-key-str p)
                                        (fn [chs]
                                          (vec (remove #(= (str %) (str agg-id)) (or chs [])))))))
                            m
                            parents)
                    m))))
            children-map
            agg-ids)))

(defn- gantt-children-map [graph-data real-edges implicit-edges]
  (let [edges (concat (or real-edges []) (or implicit-edges []))
        raw (children-by-parent edges)]
    (collapse-fan-in-agg-children graph-data edges raw)))

(defn- sort-child-ids [graph-data child-ids]
  (sort-by (fn [id]
             (let [n (gn/graph-node-data graph-data id)]
               (or (:start-time-millis n) js/Number.POSITIVE_INFINITY)))
           child-ids))

(defn collect-visible-rows
  "DFS flattening of the invocation tree. `collapsed` is a set of node-id strings whose children are hidden."
  [graph-data children-map root-id collapsed]
  (letfn [(walk [node-id depth]
            (when-let [data (gn/graph-node-data graph-data node-id)]
              (let [nid (node-key-str node-id)
                    children (sort-child-ids graph-data (distinct (get children-map nid [])))
                    row {:node-id node-id
                         :depth depth
                         :label (str (or (:node data) "?"))
                         :data data}
                    child-rows (when-not (contains? collapsed nid)
                                 (mapcat walk children))]
                (cons row child-rows))))]
    (vec (walk root-id 0))))

(defn trace-time-bounds
  "Returns [t0-ms t1-ms] covering all rows that have at least a start time."
  [rows now-ms]
  (let [starts (keep (comp :start-time-millis :data) rows)
        ends (for [r rows
                   :let [d (:data r)
                         f (:finish-time-millis d)
                         s (:start-time-millis d)]]
               (or f (when s now-ms)))
        t0 (when (seq starts) (apply min starts))
        t1 (when (seq ends) (apply max ends))]
    (when (and t0 t1 (>= t1 t0))
      (if (= t0 t1)
        [t0 (+ t0 1)]
        [t0 t1]))))

(defn format-duration-ms [ms]
  (cond
    (nil? ms) "—"
    (< ms 1) "<1ms"
    (< ms 1000) (str (int (js/Math.round ms)) "ms")
    (< ms 60000) (str (.toFixed (/ ms 1000) 2) "s")
    :else (str (.toFixed (/ ms 60000) 2) "m")))

(defn- total-root-ms [rows]
  (when-let [root (first rows)]
    (let [d (:data root)
          s (:start-time-millis d)
          f (:finish-time-millis d)]
      (when (and s f)
        (- f s)))))

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
        end (or end-ms (when row-in-progress? now-ms) start-ms)
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
        children-map (useMemo (fn [] (gantt-children-map graph-data real-edges implicit-edges))
                              [graph-data real-edges implicit-edges])
        rows (useMemo (fn [] (if (and graph-data root-invoke-id)
                               (collect-visible-rows graph-data children-map root-invoke-id collapsed)
                               []))
                      [graph-data children-map root-invoke-id collapsed])
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
     [has-in-progress?])
    (let [now now-ms
          [t0 t1] (or (trace-time-bounds rows now) [0 1])
          span (- t1 t0)
          tick-step (nice-tick-step span 8)
          ticks (vec (take 24 (iterate #(+ % tick-step) t0)))
          root-label (:node (:data (first rows)))
          total-ms (or (total-root-ms rows) span)]
      ($ :div {:className "flex flex-col border border-gray-200 rounded-lg bg-white overflow-hidden"
               :data-testid "gantt-trace-view"}
         ($ :div {:className "flex items-center justify-between gap-3 px-3 py-2 border-b border-gray-200 bg-gray-50"}
            ($ :div {:className "text-xs font-mono text-gray-600"}
               (str (count rows) " node" (when (not= 1 (count rows)) "s")))
            ($ :div {:className "text-xs font-mono text-gray-700"}
               (when root-label
                 ($ :span {:className "font-semibold text-gray-900 mr-2"} root-label))
               (str "total " (format-duration-ms total-ms))))
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
                  :let [{:keys [node-id depth label data]} row
                        nid (node-key-str node-id)
                        ch (get children-map nid [])
                        has-children? (seq ch)
                        is-collapsed? (contains? collapsed nid)
                        start (:start-time-millis data)
                        end (:finish-time-millis data)
                        row-in-progress? (and start (not end))
                        dur (when start (if end (- end start) (- now start)))
                        selected? (= (str selected-node-id) nid)
                        bar (when start
                              (row-bar-style {:start-ms start :end-ms end :t0 t0 :t1 t1
                                              :row-in-progress? row-in-progress?
                                              :now-ms now}))]]
              ($ :div {:key nid
                       :className (common/cn "grid grid-cols-[minmax(200px,32%)_1fr_minmax(72px,10%)] gap-2 items-center px-2 py-1.5 border-b border-gray-100 hover:bg-gray-50/80 cursor-pointer"
                                             (when selected? "bg-blue-50/80"))
                       :onClick #(when on-select-node (on-select-node node-id))}
                 ($ :div {:className "flex items-center gap-1 min-w-0 font-mono text-xs text-gray-800"
                          :style {:paddingLeft (str (* depth 12) "px")}}
                    (if has-children?
                      ($ :button {:type "button"
                                  :className "flex-shrink-0 w-5 h-5 flex items-center justify-center text-gray-500 hover:text-gray-800 rounded"
                                  :aria-label (if is-collapsed? "Expand nested nodes" "Collapse nested nodes")
                                  :onClick (fn [e]
                                             (.stopPropagation e)
                                             (set-collapsed (fn [s]
                                                              (if (contains? s nid)
                                                                (disj s nid)
                                                                (conj s nid)))))}
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
                                       :minWidth "2px"}}
                         (when (and dur (> dur 400))
                           ($ :span {:className "text-[10px] font-mono text-white drop-shadow-sm truncate"}
                              (format-duration-ms dur))))))
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
