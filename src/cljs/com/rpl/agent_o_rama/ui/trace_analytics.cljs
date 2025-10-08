(ns com.rpl.agent-o-rama.ui.trace-analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   ["@heroicons/react/24/outline" :refer [ArrowPathIcon ChevronRightIcon ChevronDownIcon]]))

;;; Helper functions for stats aggregation

(def ^:private nested-op-keys
  "All nested operation type keys"
  [:store-read :store-write :db-read :db-write :model-call :tool-call
   :agent-call :human-input :other])

(defn has-operations?
  "Check if aggregated operations contain any of the specified operation types."
  [basic-stats op-keys]
  (some #(pos? (get-in basic-stats [:nested-op-stats % :count] 0))
        op-keys))

(defn get-op-stats
  "Get operation stats for a specific operation key.
  Returns default empty stats if not present."
  [basic-stats op-key]
  (get-in
   basic-stats
   [:nested-op-stats op-key]
   {:count 0 :total-time-millis 0}))

(defn format-op-stats
  "Format operation stats as 'Nx  ·  Nms'.
   Returns a map with :display (formatted string) and :tooltip (average time)."
  [{:keys [count total-time-millis]}]
  (let [avg-time (if (pos? count) (/ total-time-millis count) 0)]
    {:display (str count "x  ·  " (common/format-duration-ms (or total-time-millis 0)))
     :tooltip (str "Avg: " (common/format-duration-ms avg-time))}))

(defui op-stat-row
  "Display a single operation stat row"
  [{:keys [label stats text-size]}]
  (when (pos? (:count stats))
    (let [formatted (format-op-stats stats)]
      ($ :div {:className "flex justify-between items-center"}
         ($ :div {:className "text-xs text-gray-600"} label)
         ($ :div {:className (str (or text-size "text-sm") " font-bold text-gray-800")
                  :title (:tooltip formatted)}
            (:display formatted))))))

(defui multi-value-display
  "Display one or more labeled values in a flexible layout.
   Values can be pre-formatted strings or stats objects.
   Supports optional title and icons."
  [{:keys [title values]}]
  (let [value-count (count values)]
    ($ :div
       ;; Optional title
       (when title
         ($ :div {:className "text-sm font-medium text-gray-700 mb-2"} title))

       ;; Single value: horizontal layout
       (if (= value-count 1)
         (let [{:keys [label value stats icon]} (first values)
               formatted (when stats (format-op-stats stats))]
           ($ :div
              {:className "flex justify-between items-center"}
              ($ :div
                 {:className "flex items-center gap-2"}
                 (when icon ($ icon {:className "h-4 w-4 text-gray-600"}))
                 ($ :div {:className "text-sm font-medium text-gray-700"} label))
              ($ :div
                 {:className "text-right"}
                 ($ :div
                    {:className "text-sm font-bold text-gray-800"
                     :title (when formatted (:tooltip formatted))}
                    (or value (:display formatted))))))

         ;; Multiple values: vertical columns layout
         ($ :div
            {:className "flex justify-between items-center"}
            (for [{:keys [label value stats icon] :as _v} values]
              (let [formatted (when stats (format-op-stats stats))]
                ($ :div
                   {:key label}
                   (when icon ($ icon {:className "h-4 w-4 text-gray-600 mb-1"}))
                   ($ :div {:className "text-xs text-gray-600"} label)
                   ($ :div {:className "text-sm font-bold text-gray-800"
                            :title (when formatted (:tooltip formatted))}
                      (or value (:display formatted)))))))))))

(defui stat-card
  "Common card wrapper for stat sections"
  [{:keys [data-id children]}]
  ($ :div
     {:className "bg-gray-50 px-2 py-1.5 rounded-lg border border-gray-200"
      :data-id   data-id}
     children))

;;; UI Components

(defui agent-stats-display
  "Displays statistics for an agent execution.
   Takes basic-stats and optional execution-time and retry-count."
  [{:keys [basic-stats execution-time retry-count]}]
  (let [[nested-ops-expanded? set-nested-ops-expanded!] (uix/use-state false)
        [node-stats-expanded? set-node-stats-expanded!] (uix/use-state false)

        ;; Token counts from basic stats
        total-tokens  (:total-token-count basic-stats)
        input-tokens  (:input-token-count basic-stats)
        output-tokens (:output-token-count basic-stats)

        ;; Node stats
        node-stats-map (:node-stats basic-stats)

        ;; Local helper for getting operation stats
        get-op (partial get-op-stats basic-stats)

        ;; Check if there are any stats to display
        has-stats? (or execution-time
                       (and retry-count (> retry-count 0))
                       (and total-tokens (pos? total-tokens))
                       (has-operations? basic-stats nested-op-keys)
                       (seq node-stats-map))]
    ($ :div
       {:className "grid grid-cols-1 gap-2"}

       ;; Execution time (optional)
       (when execution-time
         ($ stat-card
            {:data-id "execution-time"}
            ($ multi-value-display
               {:values
                [{:label "Execution Time"
                  :value (str (.toLocaleString execution-time) "ms")}]})))

       ;; Retry count (optional)
       (when (and retry-count (> retry-count 0))
         ($ stat-card
            {:data-id "retry-count"}
            ($ multi-value-display
               {:values [{:label "Retries"
                          :value retry-count
                          :icon ArrowPathIcon}]})))

       ;; DB operations
       (when (has-operations? basic-stats [:db-read :db-write])
         ($ stat-card
            {:data-id "db-operations"}
            ($ multi-value-display
               {:title "DB Operations"
                :values
                [{:label "Reads" :stats (get-op :db-read)}
                 {:label "Writes" :stats (get-op :db-write)}]})))

       ;; Store operations
       (when (has-operations? basic-stats [:store-read :store-write])
         ($ stat-card
            {:data-id "store-operations"}
            ($ multi-value-display
               {:title "Store Operations"
                :values
                [{:label "Reads" :stats (get-op :store-read)}
                 {:label "Writes" :stats (get-op :store-write)}]})))

       ;; Model calls
       (when (has-operations? basic-stats [:model-call])
         ($ stat-card
            {:data-id "model-calls"}
            ($ multi-value-display
               {:values
                [{:label "Model Calls" :stats (get-op :model-call)}]})))

       ;; Tokens
       (when (and total-tokens (pos? total-tokens))
         ($ stat-card
            {:data-id "tokens"}
            ($ multi-value-display
               {:title "Tokens"
                :values
                [{:label "Input"
                  :value (str (.toLocaleString (or input-tokens 0)))}
                 {:label "Output"
                  :value (str (.toLocaleString (or output-tokens 0)))}
                 {:label "Total"
                  :value (str (.toLocaleString total-tokens))}]})))

       ;; Other operations (expandable)
       (.log js/console "STATS" (pr-str basic-stats))
       (when (has-operations?
              basic-stats
              [:tool-call :agent-call :human-input :other])
         (.log js/console "IN OTHER")
         ($ stat-card
            {:data-id "other-operations"}
            ($ :div
               ($ :button
                  {:className "flex items-center gap-2 w-full text-left text-sm font-medium text-gray-700 mb-2"
                   :data-id   "other-operations-toggle"
                   :onClick   #(set-nested-ops-expanded! not)}
                  (if nested-ops-expanded?
                    ($ ChevronDownIcon {:className "h-4 w-4"})
                    ($ ChevronRightIcon {:className "h-4 w-4"}))
                  "Other Operations")
               (when nested-ops-expanded?
                 ($ :div
                    {:className "space-y-2"
                     :data-id   "other-operations-list"}
                    ($ op-stat-row
                       {:label "Tool Calls" :stats (get-op :tool-call)})
                    ($ op-stat-row
                       {:label "Agent Calls" :stats (get-op :agent-call)})
                    ($ op-stat-row
                       {:label "Human Inputs" :stats (get-op :human-input)})
                    ($ op-stat-row
                       {:label "Other" :stats (get-op :other)}))))))

       ;; Node stats (expandable)
       (when (seq node-stats-map)
         ($ stat-card
            {:data-id "node-stats"}
            ($ :div
               ($ :button
                  {:className "flex items-center gap-2 w-full text-left text-sm font-medium text-gray-700 mb-2"
                   :data-id   "node-stats-toggle"
                   :onClick   #(set-node-stats-expanded! not)}
                  (if node-stats-expanded?
                    ($ ChevronDownIcon {:className "h-4 w-4"})
                    ($ ChevronRightIcon {:className "h-4 w-4"}))
                  "Node Stats")
               (when node-stats-expanded?
                 ($ :div
                    {:className "space-y-2"
                     :data-id   "node-stats-list"}
                    (map (fn [[node-name stats]]
                           ($ op-stat-row
                              {:key   node-name
                               :label node-name
                               :stats stats}))
                         (sort-by first node-stats-map)))))))

       ;; Fallback when no stats
       (when-not has-stats?
         ($ :div
            {:className "text-sm text-gray-500 italic"}
            "No operations tracked")))))

(defui info
  "Displays analytics and statistics for a trace execution.
   Subscribes to invocation data and renders overall statistics."
  [{:keys [invoke-id]}]
  (let [[subagent-expanded? set-subagent-expanded!] (uix/use-state false)

        ;; Subscribe to invocation data for reactive updates
        invocation-state (state/use-sub [:invocations-data invoke-id])
        summary-data     (:summary invocation-state)

        retry-count          (:retry-num summary-data)
        stats                (:stats summary-data)
        basic-stats          (:basic-stats stats)
        aggregated-stats     (:aggregated-stats stats)

        total-execution-time (unchecked-subtract
                              (or (:finish-time-millis summary-data)
                                  (:start-time-millis summary-data))
                              (:start-time-millis summary-data))

        ;; Sub-agent stats
        subagent-stats-map   (:subagent-stats stats)

        ;; Check if trace is complete (has finish time)
        is-complete?         (some? (:finish-time-millis summary-data))]

    ($ :div
       {:className "space-y-2"
        :data-id   "trace-analytics"}

       ;; Overall Stats heading with optional spinner
       ($ :div {:className "text-sm font-medium text-gray-700 pt-2 border-t border-gray-200 flex items-center gap-2"
                :data-id "overall-stats-section"}
          ($ :span "Overall Stats")
          (when-not is-complete?
            ($ common/spinner {:size :small})))

       ;; Main agent statistics - use aggregated stats
       ($ agent-stats-display
          {:basic-stats    aggregated-stats
           :execution-time total-execution-time
           :retry-count    retry-count})

       ;; By agent stats (expandable) - always present
       ($ stat-card
          {:data-id "subagent-stats"}
          ($ :div
             ($ :button
                {:className "flex items-center gap-2 w-full text-left text-sm font-medium text-gray-700 mb-2"
                 :data-id   "subagent-stats-toggle"
                 :onClick   #(set-subagent-expanded! not)}
                (if subagent-expanded?
                  ($ ChevronDownIcon {:className "h-4 w-4"})
                  ($ ChevronRightIcon {:className "h-4 w-4"}))
                (str "By agent (" (inc (count (or subagent-stats-map {}))) ")"))
               (when subagent-expanded?
                 ($ :div
                    {:className "space-y-2 mt-2"
                     :data-id   "subagent-stats-list"}
                    ;; Top-level node entry
                    ($ :div
                       {:key       "top-level"
                        :className "space-y-2"
                        :data-id   "subagent-top-level"}
                       ($ :div
                          {:className "flex items-baseline gap-2"}
                          ($ :div
                             {:className "text-sm font-semibold text-gray-800"}
                             "Top-level")
                          ($ :div {:className "text-xs text-gray-500"}
                             "(main agent)"))
                       ($ agent-stats-display
                          {:basic-stats basic-stats}))

                    ;; Sub-agent entries
                    (mapv (fn [[agent-ref sa-stats]]
                           (let [module-name    (:module-name agent-ref)
                                 agent-name     (:agent-name agent-ref)
                                 count          (:count sa-stats)
                                 sa-basic-stats (:basic-stats sa-stats)]
                             ($ :<>
                                {:key (str module-name "/" agent-name)}
                                ($ :div
                                   {:className "border-t border-gray-300 my-2"})
                                ($ :div
                                   {:className "space-y-2"
                                    :data-id   (str "subagent-" agent-name)}
                                   ($ :div
                                      {:className "space-y-1"}
                                      ($ :div
                                         {:className "text-sm font-semibold text-gray-800 overflow-hidden"
                                          :style     {:direction    "rtl"
                                                      :whiteSpace   "nowrap"
                                                      :textOverflow "ellipsis"
                                                      :maxWidth     "100%"}
                                          :title     (str module-name "/" agent-name)}
                                         (str module-name "/" agent-name))
                                      (let [total-time (reduce + 0 (map :total-time-millis (vals (:node-stats sa-basic-stats))))
                                            avg-time   (if (pos? count) (/ total-time count) 0)]
                                        ($ :div
                                           {:className "text-xs text-gray-500 text-right"
                                            :title (str "Avg: " (common/format-duration-ms avg-time))}
                                           (str count " call" (when (not= count 1) "s")
                                                " · "
                                                (common/format-duration-ms total-time)))))
                                   ($ agent-stats-display
                                      {:basic-stats sa-basic-stats})))))
                         subagent-stats-map))))))))
