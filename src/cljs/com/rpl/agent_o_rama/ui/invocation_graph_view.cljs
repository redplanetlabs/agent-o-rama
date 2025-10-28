(ns com.rpl.agent-o-rama.ui.invocation-graph-view
  (:require
   [clojure.string :as str]
   [clojure.pprint]
   [goog.i18n.DateTimeFormat :as dtf]
   [goog.date.UtcDateTime :as utc-dt]

   [uix.core :as uix :refer [defui defhook $]]

   [com.rpl.specter :as s]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.trace-analytics :as trace-analytics]
   [com.rpl.agent-o-rama.ui.feedback :as feedback]
   [com.rpl.agent-o-rama.ui.components.conversation :as conversation]

   ["react" :refer [useState useCallback useEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle MiniMap]]
   ["@dagrejs/dagre" :as Dagre]
   ["@heroicons/react/24/outline" :refer [ExclamationTriangleIcon ArrowPathIcon ArrowTopRightOnSquareIcon PencilIcon XMarkIcon]]))

(defui ExceptionDetailModal [{:keys [title content]}]
  ($ :div.p-6.space-y-4
     ($ :pre.text-xs.bg-gray-50.p-3.rounded.border.overflow-auto.max-h-80.font-mono
        content)))

(defn format-ms [ms]
  (let [date (js/Date. ms)
        formatter (js/Intl.DateTimeFormat.
                   "en-US"
                   #js {:year "numeric"
                        :month "short"
                        :day "numeric"
                        :hour "2-digit"
                        :minute "2-digit"
                        :second "2-digit"
                        :hour12 false})
        base (str/replace (.format formatter date) "," "")
        millis (.padStart (str (.getMilliseconds date)) 3 "0")]
    (str base "." millis)))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))

(defui node-status-bar
  "Renders a horizontal status bar showing all active node states.
   Props:
   - :in-progress? - Node is currently processing
   - :is-stuck? - Node terminated due to max retries
   - :has-changes - Node has been modified for forking
   - :has-human-request - Node is waiting for human input
   - :has-exceptions - Node has exceptions
   - :has-result - Node completed successfully"
  [{:keys [in-progress? is-stuck? has-changes has-human-request has-exceptions has-result]}]
  (let [;; Collect all active status indicators
        indicators (cond-> []
                     ;; In-progress or stuck
                     (and in-progress? (not is-stuck?))
                     (conj {:type :spinner
                            :title "Processing..."})

                     is-stuck?
                     (conj {:type :stuck
                            :title "Node terminated due to max retries"})

                     ;; Has changes (forking mode)
                     has-changes
                     (conj {:type :changed
                            :title "Modified for fork"})

                     ;; Human request
                     has-human-request
                     (conj {:type :human
                            :title "Awaiting human input"})

                     ;; Exceptions (only if not stuck)
                     (and has-exceptions (not is-stuck?))
                     (conj {:type :exception
                            :title "Has exceptions"})

                     ;; Successful result
                     has-result
                     (conj {:type :success
                            :title "Completed successfully"}))]

    ;; Render the status bar if there are any indicators
    (when (seq indicators)
      ($ :div {:className (common/cn "absolute -top-1 -right-1 flex items-center gap-0.5 rounded-full px-0.5 py-0.5 bg-white border border-gray-200")}
         (for [{:keys [type title]} indicators]
           ($ :div {:key type
                    :className (common/cn "w-3 h-3 flex items-center justify-center")
                    :title title}
              (case type
                :spinner ($ common/spinner {:size :small})
                :stuck ($ :div {:className (common/cn "w-3 h-3 bg-red-500 rounded-full flex items-center justify-center")}
                          ($ :svg {:className (common/cn "w-2 h-2 text-white") :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                             ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :strokeWidth 3 :d "M6 18L18 6M6 6l12 12"})))
                :changed ($ :div {:className (common/cn "w-3 h-3 bg-orange-400 rounded-full")})
                :human ($ :div {:className (common/cn "w-3 h-3 flex items-center justify-center text-xs")} "🙋")
                :exception ($ :div {:className (common/cn "w-3 h-3 bg-yellow-500 rounded-full flex items-center justify-center")}
                              ($ ExclamationTriangleIcon {:className "w-2 h-2 text-white"}))
                :success ($ :div {:className (common/cn "w-3 h-3 bg-green-500 rounded-full")})
                nil)))))))

(defn pretty-format [item]
  "Format data structure with proper indentation and formatting using pprint"
  (if (string? item)
    item
    (with-out-str (clojure.pprint/pprint item))))

(defui expandable-item-component [{:keys [item color title truncate-length]
                                   :or {truncate-length 50}}]
  (let [item-str (if (string? item) item (pr-str item))
        pretty-str (pretty-format item)
        is-long? (> (count item-str) truncate-length)
        truncated-str (if is-long?
                        (str (subs item-str 0 (- truncate-length 3)) "...")
                        item-str)]
    ($ :div {:className (str "text-" color "-500")}
       ($ :span {:className (str "break-words cursor-pointer hover:bg-" color "-100 px-1 py-0.5 rounded")
                 :onClick (fn [e]
                            (.stopPropagation e)
                            (state/dispatch [:modal/show :expandable-content
                                             {:title title
                                              :component ($ common/ContentDetailModal {:title title :content pretty-str})}]))
                 :title "Click to expand"}
          truncated-str))))

;; Declare generic-data-viewer first to avoid circular dependency
(declare generic-data-viewer)

(defui expandable-list-component [{:keys [items color title-singular truncate-length depth]
                                   :or {truncate-length 50 depth 0}}]
  (let [[show-all-items set-show-all-items] (useState false)
        has-many-items? (> (count items) 5)
        displayed-items (if (or show-all-items (not has-many-items?))
                          items
                          (take 5 items))]
    ($ :div {:className (str "text-" color "-500 space-y-1 rounded-sm")
             :style {:background-color "rgba(0, 0, 0, 0.02)"}}
       (for [[idx item] (map-indexed vector displayed-items)]
         ($ :div {:key idx
                  :className (common/cn "flex items-start gap-2 min-w-0")}
            ($ :span {:className (str "text-" color "-400 text-xs flex-shrink-0")}
               (str (inc idx) "."))
            ;; Recursively render each item using the generic viewer
            ($ :div {:className (common/cn "flex-1 min-w-0 overflow-hidden")}
               ($ generic-data-viewer {:data item
                                       :color color
                                       :truncate-length truncate-length
                                       :depth depth}))))
       ;; Show all/less button at the bottom
       (when has-many-items?
         ($ :div {:className (common/cn "mt-2 pl-2")}
            ($ :button {:className (common/cn "text-xs text-blue-600 hover:underline cursor-pointer")
                        :onClick #(set-show-all-items (not show-all-items))}
               (if show-all-items
                 "Show less"
                 (str "... show all (" (count items) ")"))))))))

(defui generic-data-viewer [{:keys [data color truncate-length depth]
                             :or {truncate-length 80 depth 0}}]
  (let [max-depth 3
        next-depth (inc depth)]
    (cond
      ;; Handle nil explicitly
      (nil? data)
      ($ :span {:className (str "text-" color "-500 italic")} "nil")

      ;; Handle empty values (string, map, list, etc.)
      (or (and (string? data) (empty? data))
          (and (map? data) (empty? data))
          (and (sequential? data) (empty? data)))
      ($ :span {:className "text-gray-400 italic text-xs"} "(empty)")

      ;; Check if data is a conversation (before other sequential checks)
      (conversation/conversation? data)
      ($ conversation/conversation-display
         {:messages data
          :color color
          :preview-text (conversation/conversation-preview-text data)})

      ;; If we've hit max depth, fall back to expandable components
      (>= depth max-depth)
      (cond
        (map? data)
        ($ expandable-item-component {:item data
                                      :color color
                                      :title "Map Details"
                                      :truncate-length truncate-length})
        (sequential? data)
        ($ expandable-item-component {:item data
                                      :color color
                                      :title "List Details"
                                      :truncate-length truncate-length})
        :else
        ($ expandable-item-component {:item data
                                      :color color
                                      :title "Value Details"
                                      :truncate-length truncate-length}))

      ;; Case 1: The data is a map. Render its key-value pairs.
      (map? data)
      ($ :div {:className "mt-1 space-y-1 pl-2 border-l border-gray-200 rounded-sm"
               :style {:background-color "rgba(0, 0, 0, 0.02)"}}
         (for [[k v] (sort-by (comp str key) data)]
           ($ :div {:key (str k)}
              ($ :div {:className "flex items-start gap-1 min-w-0"}
                 ($ :span {:className "text-gray-500 font-medium flex-shrink-0"} (str (name k) ":"))
           ;; Wrap value in a div with flex constraints to enable truncation
                 ($ :div {:className "flex-1 min-w-0 overflow-hidden"}
                    ($ generic-data-viewer {:data v
                                            :color color
                                            :truncate-length truncate-length
                                            :depth next-depth}))))))

      ;; Case 2: The data is a list or vector. Use the existing list component.
      (sequential? data)
      ($ expandable-list-component {:items data
                                    :color color
                                    :title-singular "Item"
                                    :truncate-length truncate-length
                                    :depth next-depth})

      ;; Case 3: The data is a scalar value (string, number, bool, etc.).
      ;; Use the existing item component.
      :else
      ($ expandable-item-component {:item data
                                    :color color
                                    :title "Value Details"
                                    :truncate-length truncate-length}))))

(defn transform-node-data-for-dataset
  "Transform node data from app-db to simplified format for dataset.
   Returns the result value, or an array of emit instructions.
   If there's a result, return its value.
   If there are emits, return array of {\"node\" node-name, \"args\" args-vec} instructions."
  [raw-node-data node-name]
  (let [result (:result raw-node-data)
        emits (:emits raw-node-data)]
    (cond
      ;; If there's a result, use its value
      result (:val result)
      ;; If there are emits, return array of emit instructions with full args vectors
      (seq emits) (mapv (fn [emit]
                          {"node" (:node-name emit)
                           "args" (:args emit)})
                        emits)
      ;; Default case if no result or emits
      :else [])))

(defn transform-node-input-for-dataset
  "Transform node input data from app-db to simplified format for dataset.
   Returns {\"node\" node-name, \"args\" [...]} with string keys.
   Uses the actual input arguments that were passed to the node."
  [raw-node-data node-name]
  (let [input (:input raw-node-data)]
    {"node" node-name "args" (if (vector? input) input [input])}))

(defui hitl-request-panel [{:keys [hr hr-invoke-id hitl-response submitting? module-id agent-name invoke-id]}]
  (when hr
    ($ :div {:className "bg-amber-50 p-3 rounded-md mt-4 border border-amber-200"}
       ($ :div {:className "text-sm font-medium text-amber-800 mb-2"} "Human input required")
       ($ :div {:className "text-sm text-amber-700 mb-3 whitespace-pre-wrap"} (:prompt hr))
       ($ :div
          ($ :textarea {:className "w-full border rounded p-2 text-sm resize-y"
                        :rows 3
                        :placeholder "Type your response..."
                        :value (or hitl-response "")
                        :disabled submitting?
                        :onChange #(state/dispatch [:db/set-value
                                                    [:ui :hitl :responses hr-invoke-id]
                                                    (.. % -target -value)])})
          ($ :button {:className (common/cn "mt-2 px-3 py-2 rounded text-sm font-medium transition-colors"
                                            {"bg-gray-400 text-gray-600 cursor-not-allowed" submitting?
                                             "bg-blue-600 hover:bg-blue-700 text-white" (not submitting?)})
                      :disabled (or submitting? (empty? (str/trim (or hitl-response ""))))
                      :onClick #(when (and (not submitting?)
                                           (not (empty? (str/trim (or hitl-response "")))))
                                  (state/dispatch [:hitl/submit
                                                   {:module-id module-id
                                                    :agent-name agent-name
                                                    :invoke-id invoke-id
                                                    :request hr
                                                    :response (str/trim hitl-response)}])
                                  ;; Clear the response after submission
                                  (state/dispatch [:db/set-value [:ui :hitl :responses hr-invoke-id] ""]))}
             (if submitting? "Submitting..." "Submit Response"))))))

(defui node-info-panel [{:keys [node-id node-name graph-data module-id]}]
  ($ :div {:className "bg-indigo-50 p-3 rounded-md mt-4"}
     ($ :div {:className "flex justify-between items-center"}
        ($ :span {:className "text-sm font-medium text-indigo-700"} "Node")
        ($ :span {:className "text-sm text-indigo-600 font-mono"} node-name))
     ($ :div {:className "flex justify-between items-center mt-1"}
        ($ :span {:className "text-sm font-medium text-indigo-700"} "ID")
        ($ :span {:className "text-xs text-indigo-500 font-mono"} (str node-id)))
     ;; Add to Dataset button for individual node
     ($ :div {:className "mt-3"}
        ($ :button
           {:className "text-sm font-medium py-1 px-3 rounded-md transition-colors bg-white text-black hover:bg-indigo-200 cursor-pointer"
            :onClick (fn [e]
                       (.stopPropagation e)
                       (let [raw-node-data (get graph-data node-id)
                             input-data (transform-node-input-for-dataset raw-node-data node-name)
                             output-data (transform-node-data-for-dataset raw-node-data node-name)]
                         (state/dispatch [:modal/show-form :add-from-trace
                                          {:module-id module-id
                                           :title (str "Add Node '" node-name "' to Dataset")
                                           :source-type :node
                                           :source-args input-data
                                           :source-emits output-data}])))}
           "Add node to Dataset"))))

(defui node-result-panel [{:keys [result]}]
  (when result
    ($ :div {:className "bg-indigo-50 p-3 rounded-md mt-4"}
       ($ :div {:className "text-sm font-medium text-indigo-700 mb-1"} "Result")
       ($ generic-data-viewer {:data result
                               :color "indigo"
                               :truncate-length 100
                               :depth 0}))))

(defui node-exceptions-panel [{:keys [exceptions]}]
  (when (seq exceptions)
    ($ :div {:className "bg-red-50 p-3 rounded-md mt-4 border border-red-200"}
       ($ :div {:className (common/cn "text-sm font-medium text-red-700 mb-2 flex items-center gap-2")}
          ($ ExclamationTriangleIcon {:className (common/cn "w-5 h-5")})
          (str "Exceptions (" (count exceptions) ")"))
       ($ :div {:className "space-y-2"}
          (for [[idx exc-str] (map-indexed vector exceptions)]
            (let [first-line (first (str/split-lines exc-str))]
              ($ :div {:key idx
                       :className "bg-white p-2 rounded border border-red-100 cursor-pointer hover:bg-red-50 transition-colors"
                       :onClick (fn [e]
                                  (.stopPropagation e)
                                  (state/dispatch [:modal/show :exception-detail
                                                   {:title (str "Exception " (inc idx))
                                                    :component ($ ExceptionDetailModal {:title (str "Exception " (inc idx)) :content exc-str})}]))
                       :title "Click to view full exception"}
                 ($ :div {:className "text-xs font-mono text-red-800"}
                    first-line))))))))

(defui node-timing-panel [{:keys [start-time finish-time duration]}]
  (when (and start-time finish-time)
    ($ :div {:className "p-3 bg-indigo-50 rounded-md mt-4"}
       ($ :div {:className "text-sm font-medium text-indigo-700 mb-2"} "Timing")
       ($ :div {:className "space-y-1"}
          ($ :div {:className "flex justify-between"}
             ($ :span {:className "text-xs text-indigo-700"} "Duration")
             ($ :span {:className "text-xs font-mono text-indigo-600"
                       :title (str "Started: " (format-ms start-time) "\nFinished: " (format-ms finish-time))}
                (str duration "ms")))
          ($ :div {:className "flex justify-between"}
             ($ :span {:className "text-xs text-indigo-700"} "Started")
             ($ :span {:className "text-xs font-mono text-indigo-600"}
                (format-ms start-time)))
          ($ :div {:className "flex justify-between"}
             ($ :span {:className "text-xs text-indigo-700"} "Finished")
             ($ :span {:className "text-xs font-mono text-indigo-600"}
                (format-ms finish-time)))))))

(defui node-input-panel [{:keys [input]}]
  (when input
    ($ :div {:className "bg-indigo-50 p-3 rounded-md mt-4"}
       ($ :div {:className "text-sm font-medium text-indigo-700 mb-1"} "Input")
       ($ generic-data-viewer {:data input
                               :color "indigo"
                               :truncate-length 100
                               :depth 0}))))

(defui node-operations-panel [{:keys [data]}]
  (when (not (empty? (:nested-ops data)))
    ($ :div {:className "bg-indigo-50 p-3 rounded-md mt-4"}
       ($ :div {:className "text-sm font-medium text-indigo-700 mb-2"}
          (str "Operations (" (count (:nested-ops data)) ")"))
       ($ :div {:className "space-y-2"}
          (for [op (:nested-ops data)]
            (let [info (:info op)
                  op-type (:type op)
                  start-time (:start-time-millis op)
                  finish-time (:finish-time-millis op)
                  duration (when (and start-time finish-time)
                             (str (- finish-time start-time)))]
              ($ :div {:key (str (str start-time) "-" (str finish-time))
                       :className "bg-white p-3 rounded border border-indigo-200"}

                 ;; Header
                 ($ :div {:className (common/cn "flex justify-between items-start mb-2")}
                    ($ :div {:className "flex-1"}
                       ($ :div {:className "flex items-center gap-2"}
                          ($ :span {:className "text-sm font-medium text-indigo-800 bg-indigo-100 px-2 py-1 rounded"}
                             op-type)
                          (when (:objectName info)
                            ($ :span {:className "text-sm font-mono text-indigo-700"}
                               (:objectName info)))))
                    ($ :div {:className "flex items-center gap-2"}
                       (when duration
                         ($ :div {:className "text-xs text-indigo-500 font-mono"
                                  :title (str "Started: " (format-ms start-time) "\nFinished: " (format-ms finish-time))}
                            (str duration "ms")))
                       ;; Add navigation button for agent-call operations
                       (when (= (keyword op-type) :agent-call)
                         (let [invoke-data (if (= (str (:op info)) "initiate")
                                             (:result info)
                                             (:agent-invoke info))
                               task-id (:task-id invoke-data)
                               agent-invoke-id (:agent-invoke-id invoke-data)
                               module-id (:agent-module-name info)
                               agent-name (:agent-name info)
                               can-navigate? (and (not (nil? task-id)) (not (nil? agent-invoke-id)) module-id agent-name)
                               target-url (when can-navigate?
                                            (str "/agents/" (common/url-encode module-id)
                                                 "/agent/" (common/url-encode agent-name)
                                                 "/invocations/" task-id "-" agent-invoke-id))]
                           (when target-url
                             ($ :button {:onClick (fn [] (js/window.open target-url "_blank"))
                                         :className "inline-flex items-center gap-1 px-2 py-1 text-xs font-semibold text-white bg-indigo-600 rounded hover:bg-indigo-700 transition-colors cursor-pointer shadow-sm"}
                                "View"
                                ($ ArrowTopRightOnSquareIcon {:className "h-3 w-3"})))))))

                 ;; Body
                 ($ :div {:className "text-xs text-indigo-600 mt-1"}
                    ($ generic-data-viewer {:data info :color "indigo" :depth 0})))))))))

(defui node-emits-panel [{:keys [emits graph-data flow-nodes on-select-node on-paginate-node]}]
  (when (and emits (> (count emits) 0))
    ($ :div {:className "mt-4 bg-indigo-50 p-3 rounded-md"}
       ($ :div {:className "text-sm font-medium text-indigo-700 mb-2"}
          (str "Emits (" (count emits) ")"))
       ($ :div {:className "space-y-2"}
          (for [[idx emit] (map-indexed vector (js->clj emits :keywordize-keys true))]
            (let [emit-id (str (:invoke-id emit))
                  is-loaded (contains? graph-data (:invoke-id emit))
                  border-class (if is-loaded "border-indigo-200" "border-dashed border-indigo-300")
                  cursor-class "cursor-pointer"
                  bg-class (if is-loaded "bg-gray-50" "bg-white hover:bg-indigo-50")]
              ($ :div {:key (str "emit-" idx)
                       :className (str bg-class " p-2 rounded border " border-class " " cursor-class " transition-colors")
                       :onClick (fn [e]
                                  (.stopPropagation e)
                                  (if is-loaded
                                    ;; Find and select the loaded node
                                    (let [nodes (js->clj flow-nodes :keywordize-keys true)
                                          target-node (->> nodes
                                                           (filter #(= (-> % :data :node-id) (:invoke-id emit)))
                                                           first)]
                                      (when (and target-node on-select-node)
                                        (on-select-node (:invoke-id emit))))
                                    ;; Load the unloaded node
                                    (when on-paginate-node
                                      (on-paginate-node emit-id))))}
                 ($ :div {:className "text-xs text-indigo-600"}
                    ($ :div (str "→ " (:node-name emit)))
                    (when (:args emit)
                      ($ generic-data-viewer {:data (:args emit)
                                              :color "indigo"
                                              :truncate-length 60
                                              :depth 0}))
                    ($ :div {:className "text-indigo-400 mt-1 font-mono text-xs"}
                       (str "ID: " emit-id))))))))))

(defui node-details-info-panel [{:keys [data hr hr-invoke-id hitl-response submitting? module-id agent-name invoke-id
                                        node-id node-name result exceptions start-time finish-time duration input
                                        emits graph-data flow-nodes on-select-node on-paginate-node]}]
  ($ :<>
     ($ hitl-request-panel {:hr hr
                            :hr-invoke-id hr-invoke-id
                            :hitl-response hitl-response
                            :submitting? submitting?
                            :module-id module-id
                            :agent-name agent-name
                            :invoke-id invoke-id})

     ($ node-info-panel {:node-id node-id
                         :node-name node-name
                         :graph-data graph-data
                         :module-id module-id})

     ($ node-result-panel {:result result})

     ($ node-exceptions-panel {:exceptions exceptions})

     ($ node-timing-panel {:start-time start-time
                           :finish-time finish-time
                           :duration duration})

     ($ node-input-panel {:input input})

     ($ node-operations-panel {:data data})

     ($ node-emits-panel {:emits emits
                          :graph-data graph-data
                          :flow-nodes flow-nodes
                          :on-select-node on-select-node
                          :on-paginate-node on-paginate-node})))

(defui selected-node-component [{:keys [selected-node graph-data on-paginate-node on-select-node flow-nodes module-id agent-name invoke-id]}]
  (let [data (when selected-node
               (js->clj (.-data selected-node) :keywordize-keys true))
        node-id (:node-id data)
        node-name (:node data)
        input (:input data)
        exceptions (:exceptions data)
        result (:result data)
        start-time (:start-time-millis data)
        finish-time (:finish-time-millis data)
        duration (when (and start-time finish-time)
                   (str (- finish-time start-time)))
        emits (:emits data)
        has-paginated (:has-paginated-children data)
        feedback (:feedback data)

        hr (:human-request data)

        hr-invoke-id (when hr (:invoke-id hr))
        hitl-response (state/use-sub (if hr-invoke-id
                                       [:ui :hitl :responses hr-invoke-id]
                                       [:ui :hitl :responses :placeholder]))
        submitting? (state/use-sub (if hr-invoke-id
                                     [:ui :hitl :submitting hr-invoke-id]
                                     [:ui :hitl :submitting :placeholder]))

        active-tab (state/use-sub [:ui :node-details :active-tab])]

    ;; Default to :info tab
    (uix/use-effect
     (fn []
       (when (nil? active-tab)
         (state/dispatch [:db/set-value [:ui :node-details :active-tab] :info])))
     [active-tab])

    (when selected-node
      ($ :div {:className (common/cn "mt-6 bg-white shadow-lg rounded-lg border border-gray-200 max-w-4xl")
               :data-id "node-invoke-details-panel"}
         ;; Tab header
         ($ :div {:className (common/cn "border-b border-gray-200 p-4")}
            ($ :div {:className (common/cn "flex space-x-1 bg-gray-100 rounded-lg p-1")}
               ($ :button {:className (common/cn "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors"
                                                 {"bg-white text-gray-900 shadow-sm" (= active-tab :info)
                                                  "text-gray-600 hover:text-gray-900" (not= active-tab :info)})
                           :data-id "node-info-tab"
                           :onClick #(state/dispatch [:db/set-value [:ui :node-details :active-tab] :info])}
                  "Info")
               ($ :button {:className (common/cn "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors"
                                                 {"bg-white text-gray-900 shadow-sm" (= active-tab :feedback)
                                                  "text-gray-600 hover:text-gray-900" (not= active-tab :feedback)})
                           :data-id "node-feedback-tab"
                           :onClick #(state/dispatch [:db/set-value [:ui :node-details :active-tab] :feedback])}
                  "Feedback")))

         ;; Tab content
         ($ :div {:className "p-6"}
            (case active-tab
              :info
              ($ node-details-info-panel
                 {:data data
                  :hr hr
                  :hr-invoke-id hr-invoke-id
                  :hitl-response hitl-response
                  :submitting? submitting?
                  :module-id module-id
                  :agent-name agent-name
                  :invoke-id invoke-id
                  :node-id node-id
                  :node-name node-name
                  :result result
                  :exceptions exceptions
                  :start-time start-time
                  :finish-time finish-time
                  :duration duration
                  :input input
                  :emits emits
                  :graph-data graph-data
                  :flow-nodes flow-nodes
                  :on-select-node on-select-node
                  :on-paginate-node on-paginate-node})

              :feedback
              ($ :div {:data-id "node-feedback-container"}
                 (when feedback
                   ($ feedback/feedback-list
                      {:feedback-data feedback
                       :module-id module-id})))

              ;; Default case
              nil))))))

(defui forking-input-component [{:keys [selected-node changed-nodes on-change-node-input affected-nodes]}]
  (let [data (when selected-node
               (js->clj (.-data selected-node) :keywordize-keys true))
        node-id (:node-id data)
        node-name (:node data)
        original-input (:input data)

        ;; Function to pretty-print ClojureScript data to a JSON string
        to-pretty-json (fn [val] (js/JSON.stringify (clj->js val) nil 2))

        ;; Initial text for the textarea
        current-input (get changed-nodes node-id (to-pretty-json original-input))
        [input-text set-input-text] (uix/use-state current-input)

        ;; Check if the current input is valid JSON
        is-valid-json? (try (js/JSON.parse input-text) true (catch :default _ false))

        is-affected (contains? affected-nodes node-id)]

    ;; Update input text when selected node changes
    (uix/use-effect
     (fn []
       (when selected-node
         (let [data (js->clj (.-data selected-node) :keywordize-keys true)
               node-id (:node-id data)
               original-input (:input data)
               current-input (get changed-nodes node-id (to-pretty-json original-input))]
           (set-input-text current-input))))
     [selected-node changed-nodes])

    (when selected-node
      ($ :div {:className (common/cn "mt-6 bg-white shadow-lg rounded-lg border border-gray-200 max-w-4xl")}
         ($ :div {:className "p-6"}
            ($ :div {:className "mb-4"}
               ($ :h3 {:className "text-lg font-medium text-gray-800 mb-2"}
                  (str (if is-affected "Affected Node: " "Editing Input for: ") node-name))
               ($ :div {:className "text-sm text-gray-600 mb-2"}
                  (str "Node ID: " node-id)))

            (if is-affected
              ;; Show disabled state for affected nodes
              ($ :div {:className "bg-gray-50 border-2 border-dashed border-gray-300 rounded-lg p-6 text-center"}
                 ($ :div {:className "text-gray-500 mb-2"}
                    "🚫 This node is affected by upstream changes")
                 ($ :div {:className "text-sm text-gray-600 mb-4"}
                    "This node's execution will be re-determined when the fork is executed.")
                 ($ :div {:className "text-xs text-gray-500"}
                    ($ :span {:className "font-medium"} "Current input: ")
                    ($ generic-data-viewer {:data original-input
                                            :color "gray"
                                            :truncate-length 80
                                            :depth 0}))))

            ;; Normal editing interface for unaffected nodes
            ($ :div {:className "space-y-4"}
               ($ :div
                  ($ :div {:className "flex justify-between items-center mb-2"}
                     ($ :label {:className "block text-sm font-medium text-gray-700"}
                        "New Input (JSON format):")
                     ;; Show validation status
                     (if is-valid-json?
                       ($ :span {:className "text-xs font-medium text-green-600 bg-green-100 px-2 py-1 rounded-full"} "Valid JSON")
                       ($ :span {:className "text-xs font-medium text-red-600 bg-red-100 px-2 py-1 rounded-full"} "Invalid JSON")))

                  ($ :textarea {:className (common/cn "w-full h-32 p-3 border rounded-md font-mono text-sm resize-y transition-colors"
                                                      {"border-gray-300 focus:ring-blue-500 focus:border-blue-500" is-valid-json?
                                                       "border-red-500 ring-2 ring-red-300 focus:ring-red-500 focus:border-red-500" (not is-valid-json?)})
                                :value input-text
                                :onChange (fn [e]
                                            (let [new-value (.-value (.-target e))]
                                              (set-input-text new-value)
                                              (when on-change-node-input
                                                (on-change-node-input node-id new-value))))
                                :placeholder "Enter new input value as JSON..."})
                  ($ :div {:className "text-xs text-gray-500"}
                     ($ :span {:className "font-medium"} "Original (formatted as JSON): ")
                     ($ :pre {:className "mt-1 p-2 bg-gray-100 rounded text-gray-700 whitespace-pre-wrap"}
                        (to-pretty-json original-input))))))))))

(defui lineage-panel [{:keys [module-id agent-name task-id forks fork-of]}]
  (let [has-lineage? (or (seq forks) (some? fork-of))
        [show-all-forks set-show-all-forks] (useState false)
        sorted-forks (sort forks)
        has-many-forks? (> (count sorted-forks) 5)
        displayed-forks (if (or show-all-forks (not has-many-forks?))
                          sorted-forks
                          (take 5 sorted-forks))]
    (when has-lineage?
      ($ :div {:className "bg-gray-50 p-4 rounded-lg border border-gray-200 mb-4"
               :data-id "lineage-panel"}
         ($ :h4 {:className "text-md font-semibold text-gray-700 mb-2"} "Lineage")
         ($ :div {:className "space-y-2"}

            ;; Parent Link
            (when fork-of
              ($ :div {:className "flex items-center gap-2"}
                 ($ :span {:className "text-sm font-medium text-gray-600"} "Fork of:")
                 (let [parent-id (get fork-of :parent-agent-id)
                       url (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" task-id "-" parent-id)]
                   ($ :a {:href url
                          :className "font-mono text-sm text-blue-600 hover:underline"}
                      (str task-id "-" parent-id)))))

            ;; Children Links
            (when (seq forks)
              ($ :div {:className "flex flex-col items-start gap-1"}
                 ($ :span {:className "text-sm font-medium text-gray-600 mb-1"}
                    (str "Forks (" (count forks) "):"))
                 ($ :ul {:className "list-disc list-inside pl-4"}
                    (for [fork-id displayed-forks]
                      ($ :li {:key fork-id}
                         (let [url (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" task-id "-" fork-id)]
                           ($ :a {:href url
                                  :className "font-mono text-sm text-blue-600 hover:underline"}
                              (str task-id "-" fork-id))))))
                 ;; Show all/less button at the bottom
                 (when has-many-forks?
                   ($ :div {:className "pl-4 mt-1"}
                      ($ :button {:className "text-xs text-blue-600 hover:underline cursor-pointer"
                                  :onClick #(set-show-all-forks (not show-all-forks))}
                         (if show-all-forks
                           "Show less"
                           (str "... show all (" (count forks) ")"))))))))))))

(defui exceptions-panel [{:keys [summary-data graph-data on-select-node]}]
  (let [exceptions (get-in summary-data [:exception-summaries])]
    (when (seq exceptions)
      ($ :div {:className (common/cn "bg-red-50 p-3 rounded-lg border border-red-200")
               :data-id "exceptions-panel"}
         ($ :div {:className "text-sm font-medium text-red-700 mb-2 flex items-center gap-2"}
            ($ ExclamationTriangleIcon {:className "w-5 h-5"})
            (str "Exceptions (" (count exceptions) ")"))
         ($ :div {:className "space-y-2"}
            (for [[idx exc] (map-indexed vector exceptions)]
              (let [invoke-id (:invoke-id exc)
                    node-name (:node exc)
                    throwable-str (:throwable-str exc)
                    first-line (first (str/split-lines throwable-str))
                    is-loaded? (contains? graph-data invoke-id)]
                ($ :div {:key idx
                         :className "bg-white p-2 rounded border border-red-100"}
                   ;; Stack vertically instead of horizontal flex to prevent overflow
                   ($ :div {:className "space-y-2"}
                      ($ :div
                         ($ :div {:className "font-semibold text-red-800 text-sm"} node-name)
                         ;; Make the exception text clickable with hover effect
                         ($ :div {:className "text-xs font-mono text-red-600 mt-1 break-words cursor-pointer hover:bg-red-100 px-1 py-0.5 rounded transition-colors"
                                  :onClick (fn [e]
                                             (.stopPropagation e)
                                             (state/dispatch [:modal/show :exception-detail
                                                              {:title (str "Exception in " node-name)
                                                               :component ($ ExceptionDetailModal {:title (str "Exception in " node-name) :content throwable-str})}]))
                                  :title "Click to view full exception"}
                            first-line))
                      ($ :div {:className "flex items-center gap-2 justify-end"}
                         ;; Button to navigate to the node in the graph
                         ($ :button {:className (str "text-xs font-medium px-2 py-1 rounded "
                                                     (if is-loaded?
                                                       "bg-blue-100 text-blue-700 hover:bg-blue-200"
                                                       "bg-gray-200 text-gray-500 cursor-not-allowed"))
                                     :onClick (fn [e]
                                                (.stopPropagation e)
                                                (when is-loaded? (on-select-node invoke-id)))
                                     :disabled (not is-loaded?)
                                     :title (if is-loaded?
                                              "Go to node"
                                              "Node not loaded (pagination)")}
                            "Go to Node")))))))))))
(defui metadata-row [{:keys [m-key m-val module-id agent-name invoke-id on-change is-live?]}]
  (let [[editing? set-editing!] (uix/use-state false)
        [edit-value set-edit-value!] (uix/use-state "")
        [is-saving? set-is-saving!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)

        handle-edit #(do
                       (set-editing! true)
                       (set-edit-value! (common/pp-json m-val))
                       (set-error! nil))

        handle-cancel #(set-editing! false)

        handle-save (fn []
                      (set-is-saving! true)
                      (set-error! nil)
                      (try
                        ;; Test if it's valid JSON
                        (js/JSON.parse edit-value)
                        (com.rpl.agent-o-rama.ui.sente/request!
                         [:invocations/set-metadata {:module-id module-id
                                                     :agent-name agent-name
                                                     :invoke-id invoke-id
                                                     :key m-key
                                                     :value-str edit-value}]
                         10000
                         (fn [reply]
                           (set-is-saving! false)
                           (if (:success reply)
                             (do
                               (set-editing! false)
                               (on-change))
                             (set-error! (or (:error reply) "Save failed.")))))
                        (catch :default e
                          (set-is-saving! false)
                          (set-error! (str "Invalid JSON: " (.-message e))))))

        handle-delete (fn []
                        (when (js/confirm (str "Are you sure you want to remove the metadata key '" m-key "'?"))
                          (com.rpl.agent-o-rama.ui.sente/request!
                           [:invocations/remove-metadata {:module-id module-id
                                                          :agent-name agent-name
                                                          :invoke-id invoke-id
                                                          :key m-key}]
                           10000
                           (fn [reply]
                             (if (:success reply)
                               (on-change)
                               (js/alert (str "Failed to remove metadata: " (:error reply))))))))]

    ($ :div.py-2.sm:grid.sm:grid-cols-3.sm:gap-4.sm:px-0
       ($ :dt.text-sm.font-medium.leading-6.text-gray-900.font-mono.truncate {:title m-key} m-key)
       (if editing?
         ($ :dd.mt-1.text-sm.leading-6.text-gray-700.sm:col-span-2.sm:mt-0
            ($ :div.space-y-2
               ($ :textarea.w-full.p-2.border.border-gray-300.rounded-md.font-mono.text-sm
                  {:rows 4
                   :value edit-value
                   :onChange #(set-edit-value! (.. % -target -value))})
               (when error
                 ($ :p.text-xs.text-red-600 error))
               ($ :div.flex.gap-2
                  ($ :button.px-3.py-1.text-sm.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:bg-gray-400
                     {:onClick handle-save
                      :disabled is-saving?}
                     (if is-saving? "Saving..." "Save"))
                  ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.text-gray-700.rounded-md.hover:bg-gray-50
                     {:onClick handle-cancel}
                     "Cancel"))))
         ($ :dd.mt-1.text-sm.leading-6.text-gray-700.sm:col-span-2.sm:mt-0.flex.justify-between.items-start.group
            ($ :div.flex-1.min-w-0
               ($ generic-data-viewer {:data m-val :color "gray" :truncate-length 100 :depth 0}))
            (when-not is-live?
              ($ :div.flex.items-center.gap-2.pl-2.opacity-0.group-hover:opacity-100.transition-opacity
                 ($ :button.p-1.text-gray-500.hover:text-blue-600 {:title "Edit value" :onClick handle-edit}
                    ($ PencilIcon {:className "h-4 w-4"}))
                 ($ :button.p-1.text-gray-500.hover:text-red-600 {:title "Remove key" :onClick handle-delete}
                    ($ XMarkIcon {:className "h-4 w-4"})))))))))

(defui metadata-panel [{:keys [summary-data module-id agent-name invoke-id on-change is-live?]}]
  (let [metadata (get-in summary-data [:metadata])]
    ($ :div.bg-gray-50.p-4.rounded-lg.border.border-gray-200.mb-4
       ($ :h4.text-md.font-semibold.text-gray-700.mb-2 "Metadata")
       (if (seq metadata)
         ($ :dl.divide-y.divide-gray-200
            (for [[k v] (sort-by key metadata)]
              ($ metadata-row {:key (str k)
                               :m-key (str k)
                               :m-val v
                               :module-id module-id
                               :agent-name agent-name
                               :invoke-id invoke-id
                               :on-change on-change
                               :is-live? is-live?})))
         ($ :p.text-sm.text-gray-500.italic "No metadata exists")))))

(defui result-panel [{:keys [result summary-data module-id]}]
  (when result
    (let [failure? (:failure? result)
          result-val (:val result)]
      ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200 min-w-0"
               :data-id "final-result-section"}
         ($ :div {:className "flex justify-between items-center mb-2"}
            ($ :div {:className "text-sm font-medium text-gray-700"} "Final Result")
            (if failure?
              ($ :span {:className "px-2 py-1 bg-red-100 text-red-800 rounded-full text-xs font-medium"} "Failed")
              ($ :span {:className "px-2 py-1 bg-green-100 text-green-800 rounded-full text-xs font-medium"} "Success")))
         ($ :div {:className "min-w-0"}
            ($ common/ExpandableContent {:content result-val
                                         :color (if failure? "red" "green")
                                         :modal-title "Final Result Details"
                                         :truncate-length 200
                                         :on-expand (fn [{:keys [title content]}]
                                                      (state/dispatch [:modal/show :content-detail
                                                                       {:title title
                                                                        :component ($ common/ContentDetailModal {:title title :content content})}]))}))
         ($ :div {:className "mt-4"}
            ($ :button
               {:className "w-full text-sm font-medium py-2 px-4 rounded-md transition-colors bg-green-100 text-green-800 hover:bg-green-200"
                :onClick (fn []
                           (let [input-data (:invoke-args summary-data)
                                 output-data (:val (:result summary-data))]
                             (state/dispatch [:modal/show-form :add-from-trace
                                              {:module-id module-id
                                               :title "Add Agent Invocation to Dataset"
                                               :source-type :agent
                                               :source-args input-data
                                               :source-result output-data}])))}
               "Add to Dataset"))))))

(defui info-panel [{:keys [graph-data summary-data on-select-node module-id agent-name task-id forks fork-of invoke-id]}]
  (let [result (:result summary-data)
        feedback (:feedback summary-data)]

    ($ :div {:className "space-y-4"}

       ($ lineage-panel {:module-id module-id
                         :agent-name agent-name
                         :task-id task-id
                         :forks forks
                         :fork-of fork-of})

       ($ result-panel {:result result
                        :summary-data summary-data
                        :module-id module-id})

       ($ exceptions-panel {:summary-data summary-data
                            :graph-data graph-data
                            :on-select-node on-select-node})

       ($ feedback/feedback-panel {:feedback feedback})

       ($ trace-analytics/info {:invoke-id invoke-id})
       ($ metadata-panel {:summary-data summary-data
                          :module-id module-id
                          :agent-name agent-name
                          :invoke-id invoke-id
                          :on-change
                          (fn []
                            (state/dispatch [:invocation/start-graph-loading
                                             {:invoke-id invoke-id
                                              :module-id module-id
                                              :agent-name agent-name}]))
                          :is-live? (not (:finish-time-millis summary-data))}))))

(defui fork-panel [{:keys [changed-nodes graph-data affected-nodes flow-nodes on-select-node on-remove-node-change on-execute-fork on-clear-fork]}]
  (if (empty? changed-nodes)
    ($ :div {:className "text-gray-500 text-center py-8"
             :data-id "fork-empty-state"}
       "No changes yet. Select a node to edit its input.")

    ($ :div {:className "space-y-3"
             :data-id "fork-content"}
       ;; Changed nodes list
       ($ :div {:data-id "changed-nodes-list"}
          (for [[node-id new-input] changed-nodes]
            (let [node-data (get graph-data node-id)
                  node-name (:node node-data)
                  is-overridden (contains? affected-nodes node-id)
                  handle-select-node (fn [e]
                                       (.stopPropagation e)
                                       ;; Find the corresponding flow node and select it
                                       (let [nodes (js->clj flow-nodes :keywordize-keys true)
                                             target-node (->> nodes
                                                              (filter #(= (-> % :data :node-id) node-id))
                                                              first)]
                                         (when (and target-node on-select-node)
                                           (on-select-node node-id))))]

              ($ :div {:key node-id
                       :className (common/cn "border rounded-lg p-3 cursor-pointer hover:shadow-md transition-shadow"
                                             {"bg-yellow-50 border-yellow-300 hover:bg-yellow-100" is-overridden
                                              "bg-gray-50 border-gray-200 hover:bg-gray-100" (not is-overridden)})
                       :onClick handle-select-node}
                 ($ :div {:className "flex justify-between items-start mb-2"}
                    ($ :div
                       ($ :div {:className "font-medium text-gray-800 text-sm flex items-center gap-2"}
                          node-name)
                       ($ :div {:className "text-xs text-gray-500 font-mono"} (str "ID: " node-id))
                       (when is-overridden
                         ($ :div {:className "bg-yellow-200 text-yellow-800 text-xs px-2 py-1 rounded mt-1 font-medium"}
                            "⚠️ This change will not be reached")))
                    ($ :button {:className "cursor-pointer text-red-500 hover:text-red-700 text-sm"
                                :onClick (fn [e]
                                           (.stopPropagation e)
                                           (when on-remove-node-change
                                             (on-remove-node-change node-id)))}
                       "Remove"))

                 ($ :div {:className "text-xs"}
                    ($ :div {:className "text-gray-600 mb-1"} "New input:")
                    ($ :div {:className "bg-white p-2 rounded border font-mono text-gray-800 break-all"}
                       (if (> (count new-input) 100)
                         (str (subs new-input 0 100) "...")
                         new-input)))))))

       ;; Action buttons
       ($ :div {:className "pt-4 border-t border-gray-200 space-y-2"
                :data-id "fork-action-buttons"}
          ($ :button {:className "w-full font-medium py-2 px-4 rounded-md transition-colors bg-blue-600 hover:bg-blue-700 text-white"
                      :data-id "execute-fork-button"
                      :onClick on-execute-fork}
             (str "Execute Fork (" (count changed-nodes) " changes)"))
          ($ :button {:className "w-full bg-gray-300 hover:bg-gray-400 text-gray-700 font-medium py-2 px-4 rounded-md transition-colors"
                      :data-id "clear-fork-button"
                      :onClick on-clear-fork}
             "Clear All Changes")))))

(defui right-panel [{:keys [graph-data summary-data changed-nodes on-remove-node-change affected-nodes flow-nodes on-select-node on-execute-fork on-clear-fork forking-mode? on-toggle-forking-mode is-live
                            module-id agent-name task-id forks fork-of invoke-id]}]
  (let [active-tab (state/use-sub [:ui :active-tab])]

    ;; Update forking mode when tab changes
    (uix/use-effect
     (fn []
       (when on-toggle-forking-mode
         (let [should-be-forking (= active-tab :fork)]
           (when (not= forking-mode? should-be-forking)
             (on-toggle-forking-mode)))))
     [active-tab forking-mode? on-toggle-forking-mode])

    ;; Ensure we switch back to Info tab when a new invocation starts (live)
    ;; or when fork changes are cleared
    (uix/use-effect
     (fn []
       (when (or is-live (empty? changed-nodes))
         (state/dispatch [:db/set-value [:ui :active-tab] :info])))
     [is-live changed-nodes])

    ($ :div {:className (common/cn "fixed right-0 top-32 h-[calc(100vh-8rem)] w-80 bg-white shadow-lg border-l border-gray-200 flex flex-col z-40")
             :data-id "agent-info-panel"}
       ;; Tab header
       ($ :div {:className (common/cn "border-b border-gray-200 p-4 flex-shrink-0")}
          ($ :div {:className (common/cn "flex space-x-1 bg-gray-100 rounded-lg p-1")}
             ($ :button {:className (common/cn "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors"
                                               {"bg-white text-gray-900 shadow-sm" (= active-tab :info)
                                                "text-gray-600 hover:text-gray-900" (not= active-tab :info)})
                         :data-id "info-tab"
                         :onClick #(state/dispatch [:db/set-value [:ui :active-tab] :info])}
                "Info")
             ($ :button {:className (common/cn "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors"
                                               {"bg-white text-gray-900 shadow-sm" (= active-tab :feedback)
                                                "text-gray-600 hover:text-gray-900" (not= active-tab :feedback)})
                         :data-id "feedback-tab"
                         :onClick #(state/dispatch [:db/set-value [:ui :active-tab] :feedback])}
                "Feedback")
             ;; Only show Fork tab when not in live mode
             (when-not is-live
               ($ :button {:className (common/cn "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors"
                                                 {"bg-white text-gray-900 shadow-sm" (= active-tab :fork)
                                                  "text-gray-600 hover:text-gray-900" (not= active-tab :fork)})
                           :data-id "fork-tab"
                           :onClick #(state/dispatch [:db/set-value [:ui :active-tab] :fork])}
                  (str "Fork" (when (> (count changed-nodes) 0) (str " (" (count changed-nodes) ")")))))))

       ;; Tab content
       ($ :div {:className "p-4 flex-1 overflow-y-auto"}
          (case active-tab
            :info ($ info-panel {:graph-data graph-data
                                 :summary-data summary-data
                                 :on-select-node on-select-node
                                 :module-id module-id
                                 :agent-name agent-name
                                 :task-id task-id
                                 :forks forks
                                 :fork-of fork-of
                                 :invoke-id invoke-id})

            :feedback ($ feedback/feedback-list {:feedback-data (:feedback summary-data)
                                                 :module-id module-id})

            :fork ($ fork-panel {:changed-nodes changed-nodes
                                 :graph-data graph-data
                                 :affected-nodes affected-nodes
                                 :flow-nodes flow-nodes
                                 :on-select-node on-select-node
                                 :on-remove-node-change on-remove-node-change
                                 :on-execute-fork on-execute-fork
                                 :on-clear-fork on-clear-fork}))))))

(defn process-graph-data
  "Applies Dagre layout to pre-processed nodes and edges."
  [nodes-map real-edges implicit-edges]
  (let [g (new (.. Dagre -graphlib -Graph))

        nodes (->> nodes-map
                   (map (fn [[id data]]
                          {:id (str id)
                           :type "custom"
                           :draggable false
                           :data (assoc data
                                        :label (str (:node data))
                                        :node-id id)})))

        all-edges (concat real-edges implicit-edges)]

    (.setDefaultEdgeLabel ^js g (fn [] #js {}))
    (.setGraph ^js g #js {})

    (doseq [edge all-edges] (.setEdge ^js g (:source edge) (:target edge)))
    (doseq [node nodes]
      (.setNode ^js g (:id node) (clj->js (merge node {:width 170 :height 40}))))

    (Dagre/layout g)

    (let [nodes-with-layout (for [node nodes
                                  :let [position (.node g (:id node))]]
                              (assoc node :position position))]
      {:nodes nodes-with-layout
       :edges all-edges})))

(defn find-downstream-nodes
  "Find all nodes that are downstream from the given set of modified node IDs.
   This includes nodes that are both modified AND downstream (overridden nodes)."
  [graph-data modified-node-ids]
  (let [;; For each modified node, find all nodes downstream from it
        get-downstream-from-node (fn [start-node-id]
                                   (loop [to-visit #{start-node-id}
                                          visited #{}
                                          downstream #{}]
                                     (if (empty? to-visit)
                                       downstream
                                       (let [current (first to-visit)
                                             remaining (disj to-visit current)]
                                         (if (visited current)
                                           (recur remaining visited downstream)
                                           (let [node-data (get graph-data current)
                                                 emitted-ids (set (map :invoke-id (:emits node-data)))
                                                 ;; Add emitted nodes to downstream (but not the starting node)
                                                 new-downstream (if (= current start-node-id)
                                                                  downstream
                                                                  (conj downstream current))
                                                 new-to-visit (into remaining emitted-ids)]
                                             (recur new-to-visit
                                                    (conj visited current)
                                                    new-downstream)))))))]
    ;; Collect downstream nodes from all modified nodes
    (reduce (fn [all-downstream modified-node-id]
              (into all-downstream (get-downstream-from-node modified-node-id)))
            #{}
            modified-node-ids)))

(defui graph-view [{:keys [module-id agent-name invoke-id task-id
                           forks fork-of
                           graph-data real-edges summary-data implicit-edges
                           is-complete is-live connected?
                           selected-node-id forking-mode? changed-nodes
                           on-select-node on-execute-fork on-clear-fork
                           on-change-node-input on-remove-node-change
                           on-toggle-forking-mode on-paginate-node]}]
  (let [;; Convert selected-node-id to actual node object when needed
        [selected-node set-selected-node-internal] (uix/use-state nil)

        affected-nodes (when forking-mode?
                         (find-downstream-nodes graph-data (set (keys changed-nodes))))

        ;; Use React Flow's state management hooks
        [flow-nodes set-nodes on-nodes-change] (useNodesState (clj->js []))
        [flow-edges set-edges on-edges-change] (useEdgesState (clj->js []))

        ;; Update React Flow nodes/edges when graph data changes
        _ (uix/use-effect
           (fn []
             (when (not (empty? graph-data))
               (let [{:keys [nodes edges]} (process-graph-data graph-data (or real-edges []) (or implicit-edges []))]
                 (println "Updating flow with nodes:" (count nodes) "and edges:" (count edges))
                 (set-nodes (clj->js nodes))
                 (set-edges (clj->js (for [edge edges]
                                       (if (:implicit? edge)
                                         (assoc edge :style #js {:strokeDasharray "5 5"
                                                                 :stroke "#aaa"})
                                         edge)))))))
           [graph-data real-edges implicit-edges])

        ;; Update selected node when selected-node-id changes
        _ (uix/use-effect
           (fn []
             (when selected-node-id
               (let [nodes (js->clj flow-nodes :keywordize-keys true)
                     target-node (->> nodes
                                      (filter #(= (-> % :data :node-id) selected-node-id))
                                      first)]
                 (when target-node
                   (set-selected-node-internal (clj->js target-node))))))
           [selected-node-id flow-nodes])

        ;; Use callbacks passed as props
        handle-select-node-click (fn [node]
                                   (when on-select-node
                                     (let [node-data (js->clj (.-data node) :keywordize-keys true)]
                                       (on-select-node (:node-id node-data)))))]

    (if (empty? graph-data)
      ($ :div.flex.justify-center.items-center.py-8
         ($ :div.text-gray-500 "No graph data available"))
      ($ :<>
         ;; Main content area with right margin for the stats panel
         ($ :div {:className "mr-80"
                  :data-id "agent-graph-panel"}
            ($ :div {:style {:width "100%" :height "500px"}}
               ($ ReactFlow {:nodes flow-nodes
                             :edges flow-edges
                             :onNodesChange on-nodes-change
                             :onEdgesChange on-edges-change
                             :proOptions (clj->js {:hideAttribution true})
                             :nodeTypes (clj->js {"custom"
                                                  (uix.core/as-react
                                                   (fn [{:keys [data id]}]
                                                     (let [data (js->clj data :keywordize-keys true)
                                                           label (:label data)
                                                           node-id (:node-id data)
                                                           selected (= (when selected-node (.-id selected-node)) id)
                                                           has-changes (contains? changed-nodes node-id)
                                                           is-affected (and forking-mode? (contains? affected-nodes node-id))
                                                           ;; Check if node is in progress
                                                           in-progress? (and (:start-time-millis data)
                                                                             (not (:finish-time-millis data)))
                                                           ;; NEW: Check if the node is stuck (in-progress but the whole agent has finished)
                                                           is-stuck? (and in-progress? is-complete)
                                                           base-classes (cond
                                                                          is-affected
                                                                          ["bg-gray-300" "text-gray-500" "border-2" "border-gray-400"]

                                                                          has-changes
                                                                          ["bg-orange-500" "text-white" "border-2" "border-orange-600"]

                                                                          (agg-node? data)
                                                                          ["bg-yellow-500" "text-white" "border-2" "border-yellow-600"]

                                                                          (starter-node? data)
                                                                          ["bg-green-500" "text-white" "border-2" "border-green-600"]

                                                                          :else
                                                                          ["bg-white" "text-gray-800" "border-2" "border-gray-300"])
                                                           selection-classes (if selected
                                                                               ["ring-4" "ring-blue-400" "ring-opacity-75" "shadow-2xl" "transform" "scale-105"]
                                                                               ["shadow-lg"])
                                                           common-classes ["p-3" "rounded-md" "transition-all" "duration-200"]
                                                           node-className (common/cn base-classes selection-classes common-classes)
                                                           has-human-request (:human-request data)
                                                           has-exceptions (seq (:exceptions data))]
                                                       ($ :div {:className "relative"}
                                                          ($ :div {:className node-className
                                                                   :style {:width "170px" :height "40px" :opacity (if is-affected "0.6" "1.0")}}
                                                             ($ :div {:className "truncate" :title label}
                                                                label))
                                                          ;; Consolidated status indicator bar in top-right corner
                                                          ($ node-status-bar {:in-progress? in-progress?
                                                                              :is-stuck? is-stuck?
                                                                              :has-changes has-changes
                                                                              :has-human-request has-human-request
                                                                              :has-exceptions has-exceptions
                                                                              :has-result (:result data)})
                                                          ($ Handle {:type "target" :position "top"})
                                                          ($ Handle {:type "source" :position "bottom"})))))

                                                  "phantom"
                                                  (uix.core/as-react
                                                   (fn [{:keys [data]}]
                                                     (let [data (js->clj data :keywordize-keys true)
                                                           missing-node-id (:missing-node-id data)]
                                                       ($ :div {:className "relative cursor-pointer"
                                                                :onClick (fn [e]
                                                                           (.stopPropagation e)
                                                                           (println "phantom data" data)
                                                                           (on-paginate-node missing-node-id))}
                                                          ($ :div {:className "bg-gray-100 text-gray-600 p-3 rounded-md shadow-lg border-2 border-dashed border-gray-400 hover:bg-gray-200 transition-colors"
                                                                   :style {:width "170px" :height "40px"}}
                                                             ($ :div {:className "truncate" :title (:label data)}
                                                                (:label data)))
                                                          ($ Handle {:type "target" :position "top"})))))})
                             :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}}
                             :onNodeClick (fn [_ node] (handle-select-node-click node))}
                  ($ MiniMap {:position "bottom-right" :pannable true :zoomable true})
                  ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
                  ($ Controls {:className "fill-gray-500 stroke-gray-500"})))

            ;; Show selected node details or forking input component
            (when selected-node
              (if forking-mode?
                ($ forking-input-component {:selected-node selected-node
                                            :changed-nodes changed-nodes
                                            :on-change-node-input on-change-node-input
                                            :affected-nodes affected-nodes})
                ($ selected-node-component {:selected-node selected-node
                                            :graph-data graph-data
                                            :on-paginate-node on-paginate-node
                                            :on-select-node on-select-node
                                            :flow-nodes flow-nodes
                                            :module-id module-id
                                            :agent-name agent-name
                                            :invoke-id invoke-id}))))

         ;; Always-visible right panel with tabs
         ($ right-panel {:graph-data graph-data
                         :summary-data summary-data
                         :changed-nodes changed-nodes
                         :on-remove-node-change on-remove-node-change
                         :affected-nodes affected-nodes
                         :flow-nodes flow-nodes
                         :on-select-node on-select-node
                         :on-execute-fork on-execute-fork
                         :on-clear-fork on-clear-fork
                         :forking-mode? forking-mode?
                         :on-toggle-forking-mode on-toggle-forking-mode
                         :is-live is-live
                         :module-id module-id
                         :agent-name agent-name
                         :task-id task-id
                         :forks forks
                         :fork-of fork-of
                         :invoke-id invoke-id})))))
