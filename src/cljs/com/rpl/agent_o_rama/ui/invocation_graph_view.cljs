(ns com.rpl.agent-o-rama.ui.invocation-graph-view
  (:require
   [clojure.string :as str]
   [clojure.pprint]
   [goog.i18n.DateTimeFormat :as dtf]
   [goog.date.UtcDateTime    :as utc-dt]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect]]
   ["react-dom" :refer [createPortal]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle MiniMap]]
   ["@dagrejs/dagre" :as Dagre]))

(defn format-ms [ms]
  (let [date (js/Date. ms)
        formatter (js/Intl.DateTimeFormat.
                   "en-US"
                   #js {:year          "numeric"
                        :month         "short"
                        :day           "numeric"
                        :hour          "2-digit"
                        :minute        "2-digit"
                        :second        "2-digit"
                        :hour12 false})
        base (.format formatter date)
        millis (.padStart (str (.getMilliseconds date)) 3 "0")]
    (str base "." millis)))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))

(defui expandable-popup-modal [{:keys [content content-index title on-close]}]
  (createPortal
    ($ :div {:className "fixed inset-0 flex items-center justify-center z-50"
             :style {:backgroundColor "rgba(0, 0, 0, 0.5)"}
             :onClick (fn [e]
                        (.preventDefault e)
                        (.stopPropagation e)
                        (on-close))}
       ($ :div {:className "bg-white rounded-lg shadow-xl max-w-4xl max-h-[80vh] overflow-hidden"
                :onClick (fn [e] 
                           (.preventDefault e)
                           (.stopPropagation e))}
          ($ :div {:className "p-4 border-b border-gray-200 flex justify-between items-center"}
             ($ :h3 {:className "text-lg font-medium text-gray-800"}
                title)
             ($ :button {:className "text-gray-400 hover:text-gray-600 text-xl font-bold cursor-pointer"
                         :onClick (fn [e]
                                    (.preventDefault e)
                                    (.stopPropagation e)
                                    (on-close))}
                "×"))
          ($ :div {:className "p-4 overflow-auto max-h-96"}
             ($ :pre {:className "text-sm font-mono text-gray-800 whitespace-pre-wrap break-all"}
                content))))
    (.-body js/document)))


(defn pretty-format [item]
  "Format data structure with proper indentation and formatting using pprint"
  (if (string? item)
    item
    (with-out-str (clojure.pprint/pprint item))))

(defui expandable-item-component [{:keys [item color title truncate-length]
                                   :or {truncate-length 50}}]
  (let [[show-modal set-show-modal] (uix/use-state false)
        item-str (if (string? item) item (pr-str item))
        pretty-str (pretty-format item)
        is-long? (> (count item-str) truncate-length)
        truncated-str (if is-long?
                        (str (subs item-str 0 (- truncate-length 3)) "...")
                        item-str)]
    ($ :<>
       ($ :div {:className (str "text-" color "-500")}
          ($ :span {:className (str "break-words cursor-pointer hover:bg-" color "-100 px-1 py-0.5 rounded")
                    :onClick (fn [e]
                               (.stopPropagation e)
                               (set-show-modal true))
                    :title "Click to expand"}
             truncated-str))
       
       ;; Popup modal with pretty formatting
       (when show-modal
         ($ expandable-popup-modal {:content pretty-str
                                    :title title
                                    :on-close #(set-show-modal false)})))))

;; Declare generic-data-viewer first to avoid circular dependency
(declare generic-data-viewer)

(defui expandable-list-component [{:keys [items color title-singular truncate-length depth]
                                   :or {truncate-length 50 depth 0}}]
  ($ :div {:className (str "text-" color "-500 mt-1 space-y-1")}
     (for [[idx item] (map-indexed vector items)]
       ($ :div {:key idx
                :className "flex items-start gap-2"}
          ($ :span {:className (str "text-" color "-400 text-xs flex-shrink-0 mt-0.5")}
             (str (inc idx) "."))
          ;; Recursively render each item using the generic viewer
          ($ :div {:className "flex-1"}
             ($ generic-data-viewer {:data item 
                                     :color color 
                                     :truncate-length truncate-length
                                     :depth depth}))))))

(defui generic-data-viewer [{:keys [data color truncate-length depth]
                             :or {truncate-length 80 depth 0}}]
  (let [max-depth 3
        next-depth (inc depth)]
    (cond
      ;; Handle nil explicitly
      (nil? data)
      ($ :span {:className (str "text-" color "-500 italic")} "nil")
      
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
      ($ :div {:className "mt-1 space-y-1 pl-2 border-l border-gray-200"}
         (for [[k v] (sort-by key data)]
           ($ :div {:key (str k)}
              ($ :div {:className "flex items-start gap-1"}
                 ($ :span {:className "text-gray-500 font-medium"} (str (name k) ":"))
                 ;; Recursive call to render the value, whatever its type.
                 ($ generic-data-viewer {:data v 
                                         :color color 
                                         :truncate-length truncate-length
                                         :depth next-depth})))))

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

(defui selected-node-component [{:keys [selected-node graph-data on-paginate-node on-select-node flow-nodes]}]
  (let [data (when selected-node 
               (js->clj (.-data selected-node) :keywordize-keys true))
        node-id (str (:node-id data))
        node-name (:node data)
        input (:input data)
        result (:result data)
        start-time (:start-time-millis data)
        finish-time (:finish-time-millis data)
        duration (when (and start-time finish-time)
                   (- finish-time start-time))
        emits (:emits data)
        has-paginated (:has-paginated-children data)]
    
    (when selected-node
      ($ :div {:className "mt-6 bg-white shadow-lg rounded-lg border border-gray-200 max-w-4xl"}
         ($ :div {:className "p-6"}
            ;; Node Info Section
            ($ :div {:className "bg-indigo-50 p-3 rounded-md"}
               ($ :div {:className "flex justify-between items-center"}
                  ($ :span {:className "text-sm font-medium text-indigo-700"} "Node")
                  ($ :span {:className "text-sm text-indigo-600 font-mono"} node-name))
               ($ :div {:className "flex justify-between items-center mt-1"}
                  ($ :span {:className "text-sm font-medium text-indigo-700"} "ID")
                  ($ :span {:className "text-xs text-indigo-500 font-mono"} node-id)))
            
            (when result
              ($ :div {:className "bg-blue-50 p-3 rounded-md mt-4"}
                 ($ :div {:className "text-sm font-medium text-blue-700 mb-1"} "Result")
                 ($ generic-data-viewer {:data result
                                         :color "blue"
                                         :truncate-length 100
                                         :depth 0})))
            (when (and start-time finish-time)
              ($ :div {:className "bg-yellow-50 p-3 rounded-md mt-4"}
                 ($ :div {:className "text-sm font-medium text-yellow-700 mb-2"} "Timing")
                 ($ :div {:className "space-y-1"}
                    ($ :div {:className "flex justify-between"}
                       ($ :span {:className "text-xs text-yellow-600"} "Duration")
                       ($ :span {:className "text-xs text-yellow-600 font-mono"
                                 :title (str "Started: " (format-ms start-time) "\nFinished: " (format-ms finish-time))} 
                          (str duration "ms")))
                    ($ :div {:className "flex justify-between"}
                       ($ :span {:className "text-xs text-yellow-600"} "Started")
                       ($ :span {:className "text-xs text-yellow-600 font-mono"} 
                          (format-ms start-time)))
                    ($ :div {:className "flex justify-between"}
                       ($ :span {:className "text-xs text-yellow-600"} "Finished")
                       ($ :span {:className "text-xs text-yellow-600 font-mono"} 
                          (format-ms finish-time))))))
            (when input
              ($ :div {:className "bg-green-50 p-3 rounded-md mt-4"}
                 ($ :div {:className "text-sm font-medium text-green-700 mb-1"} "Input")
                 ($ generic-data-viewer {:data input
                                         :color "green"
                                         :truncate-length 100
                                         :depth 0})))
            
            (when (not (empty? (:nested-ops data)))
              ($ :div {:className "bg-sky-50 p-3 rounded-md mt-4"}
                 ($ :div {:className "text-sm font-medium text-sky-700 mb-2"} 
                    (str "Operations (" (count (:nested-ops data)) ")"))
                 ($ :div {:className "space-y-2"}
                    (for [op (:nested-ops data)]
                      (let [info (:info op)
                            op-type (:type op)
                            start-time (:start-time-millis op)
                            finish-time (:finish-time-millis op)
                            duration (when (and start-time finish-time)
                                       (- finish-time start-time))]
                        ($ :div {:key (str start-time "-" finish-time)
                                 :className "bg-white p-3 rounded border border-sky-200"}
                           
                           ;; 1. The Header: Keep this part to display consistent op-level info
                           ($ :div {:className "flex justify-between items-start mb-2"}
                              ($ :div {:className "flex-1"}
                                 ($ :div {:className "flex items-center gap-2"}
                                    ($ :span {:className "text-sm font-medium text-sky-800 bg-sky-100 px-2 py-1 rounded"} 
                                       op-type)
                                    ;; The generic viewer will show objectName, so this is optional, but nice for a header
                                    (when (:objectName info)
                                      ($ :span {:className "text-sm font-mono text-sky-700"} 
                                         (:objectName info)))))
                              ;; Always display the duration
                              (when duration
                                ($ :div {:className "text-xs text-sky-500 font-mono"
                                         :title (str "Started: " (format-ms start-time) "\nFinished: " (format-ms finish-time))}
                                   (str duration "ms"))))

                           ;; 2. The Body: Replace all specific logic with the generic viewer
                           ($ :div {:className "text-xs text-sky-600 mt-1"}
                              ($ generic-data-viewer {:data info :color "sky" :depth 0})))))))))
            
               ;; Emits Section (full width)
            (when (and emits (> (count emits) 0))
              ($ :div {:className "mt-4 bg-purple-50 p-3 rounded-md"}
                 ($ :div {:className "text-sm font-medium text-purple-700 mb-2"} 
                    (str "Emits (" (count emits) ")"))
                 ($ :div {:className "space-y-2"}
                    (for [[idx emit] (map-indexed vector (js->clj emits :keywordize-keys true))]
                      (let [emit-id (str (:invoke-id emit))
                            is-loaded (contains? graph-data (:invoke-id emit))
                            ;; We no longer track loading state locally
                            border-class (if is-loaded "border-purple-200" "border-dashed border-purple-300")
                            cursor-class "cursor-pointer"
                            bg-class (if is-loaded "bg-gray-50" "bg-white hover:bg-purple-50")]
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
                           ($ :div {:className "text-xs text-purple-600"}
                              ($ :div (str "→ " (:node-name emit)))
                              (when (:args emit)
                                ($ generic-data-viewer {:data (:args emit)
                                                        :color "purple"
                                                        :truncate-length 60
                                                        :depth 0}))
                              ($ :div {:className "text-purple-400 mt-1 font-mono text-xs"}
                                 (str "ID: " emit-id)))))))))
            
               ))))

(defui forking-input-component [{:keys [selected-node changed-nodes on-change-node-input affected-nodes]}]
  (let [data (when selected-node 
               (js->clj (.-data selected-node) :keywordize-keys true))
        node-id (:node-id data)
        node-name (:node data)
        original-input (:input data)
        current-input (get changed-nodes node-id (str original-input))
        [input-text set-input-text] (uix/use-state current-input)
        is-affected (contains? affected-nodes node-id)]
    
    ;; Update input text when selected node changes
    (uix/use-effect
     (fn []
       (when selected-node
         (let [data (js->clj (.-data selected-node) :keywordize-keys true)
               node-id (:node-id data)
               original-input (:input data)
               current-input (get changed-nodes node-id (str original-input))]
           (set-input-text current-input))))
     [selected-node changed-nodes])
    
    (when selected-node
      ($ :div {:className "mt-6 bg-white shadow-lg rounded-lg border border-gray-200 max-w-4xl"}
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
              
              ;; Show normal editing interface for unaffected nodes
              ($ :div {:className "space-y-4"}
                 ($ :div
                    ($ :label {:className "block text-sm font-medium text-gray-700 mb-2"}
                       "New Input:")
                    ($ :textarea {:className "w-full h-32 p-3 border border-gray-300 rounded-md font-mono text-sm resize-y"
                                  :value input-text
                                  :onChange (fn [e]
                                              (let [new-value (.-value (.-target e))]
                                                (set-input-text new-value)
                                                (when on-change-node-input
                                                  (on-change-node-input node-id new-value))))
                                  :placeholder "Enter new input value..."}))
                 
                 ($ :div {:className "text-xs text-gray-500"}
                    ($ :span {:className "font-medium"} "Original: ")
                    ($ generic-data-viewer {:data original-input
                                            :color "gray"
                                            :truncate-length 80
                                            :depth 0}))))))))


(defui info-panel [{:keys [graph-data summary-data]}]
  (let [result (:result summary-data)
        failure? (:failure? result)
        result-val (:val result)]
    
    ($ :div {:className "space-y-4"}
       
       ;; NEW: Final Result Panel
       (when result
         ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
            ($ :div {:className "flex justify-between items-center mb-2"}
               ($ :div {:className "text-sm font-medium text-gray-700"} "Final Result")
               (if failure?
                 ($ :span {:className "px-2 py-1 bg-red-100 text-red-800 rounded-full text-xs font-medium"} "Failed")
                 ($ :span {:className "px-2 py-1 bg-green-100 text-green-800 rounded-full text-xs font-medium"} "Success")))
            ($ generic-data-viewer {:data result-val
                                    :color (if failure? "red" "green")
                                    :truncate-length 100
                                    :depth 0})))

       ($ :div {:className "text-sm font-medium text-gray-700 pt-2 border-t border-gray-200"} "Overall Stats")

       ;; Metrics grid
       (let [total-nodes (count graph-data)
             ;; Dummy values for now
             total-execution-time 2347
             total-tokens 45892
             store-reads 127
             store-writes 23
             model-calls 156]
         ($ :div {:className "grid grid-cols-1 gap-3"}
          ;; Execution time
          ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
             ($ :div {:className "flex justify-between items-center"}
                ($ :div
                   ($ :div {:className "text-sm font-medium text-gray-700"} "Execution Time"))
                ($ :div {:className "text-right"}
                   ($ :div {:className "text-lg font-bold text-gray-800"} (str (.toLocaleString total-execution-time) "ms")))))
          
          
          ;; Store operations
          ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
             ($ :div
                ($ :div {:className "text-sm font-medium text-gray-700 mb-2"} "Store Operations")
                ($ :div {:className "flex justify-between items-center"}
                   ($ :div
                      ($ :div {:className "text-xs text-gray-600"} "Reads")
                      ($ :div {:className "text-lg font-bold text-gray-800"} store-reads))
                   ($ :div
                      ($ :div {:className "text-xs text-gray-600"} "Writes") 
                      ($ :div {:className "text-lg font-bold text-gray-800"} store-writes)))))
          
          ;; Model calls
          ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
             ($ :div {:className "flex justify-between items-center"}
                ($ :div
                   ($ :div {:className "text-sm font-medium text-gray-700"} "Model Calls")
                   )
                ($ :div {:className "text-right"}
                   ($ :div {:className "text-lg font-bold text-gray-800"} model-calls))))

          ;; Tokens
          ($ :div {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
             ($ :div {:className "flex justify-between items-center"}
                ($ :div
                   ($ :div {:className "text-sm font-medium text-gray-700"} "Tokens"))
                ($ :div {:className "text-right"}
                   ($ :div {:className "text-lg font-bold text-gray-800"} (str (.toLocaleString total-tokens)))))))))))

(defui right-panel [{:keys [graph-data summary-data changed-nodes on-remove-node-change affected-nodes flow-nodes on-select-node on-execute-fork on-clear-fork forking-mode? on-toggle-forking-mode is-live]}]
  (let [[active-tab set-active-tab] (uix/use-state :info)]
    
    ;; Update forking mode when tab changes
    (uix/use-effect
     (fn []
       (when on-toggle-forking-mode
         (let [should-be-forking (= active-tab :fork)]
           (when (not= forking-mode? should-be-forking)
             (on-toggle-forking-mode)))))
     [active-tab forking-mode? on-toggle-forking-mode])
    
    ($ :div {:className "fixed right-0 top-32 h-[calc(100vh-8rem)] w-80 bg-white shadow-lg border-l border-gray-200 overflow-hidden z-40"}
       ;; Tab header
       ($ :div {:className "border-b border-gray-200 p-4"}
          ($ :div {:className "flex space-x-1 bg-gray-100 rounded-lg p-1"}
             ($ :button {:className (str "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors "
                                         (if (= active-tab :info)
                                           "bg-white text-gray-900 shadow-sm"
                                           "text-gray-600 hover:text-gray-900"))
                         :onClick #(set-active-tab :info)}
                "Info")
             ;; Only show Fork tab when not in live mode
             (when-not is-live
               ($ :button {:className (str "flex-1 py-2 px-3 text-sm font-medium rounded-md transition-colors "
                                           (if (= active-tab :fork)
                                             "bg-white text-gray-900 shadow-sm"
                                             "text-gray-600 hover:text-gray-900"))
                           :onClick #(set-active-tab :fork)}
                  (str "Fork" (when (> (count changed-nodes) 0) (str " (" (count changed-nodes) ")")))))))
       
       ;; Tab content
       ($ :div {:className "p-4 h-full overflow-y-auto"}
          (case active-tab
            :info ($ info-panel {:graph-data graph-data :summary-data summary-data})
            
            :fork (if (empty? changed-nodes)
                    ($ :div {:className "text-gray-500 text-center py-8"}
                       "No changes yet. Select a node to edit its input.")
                    
                    ($ :div {:className "space-y-3"}
                       ;; Changed nodes list
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
                                    :className (str "border rounded-lg p-3 cursor-pointer hover:shadow-md transition-shadow " 
                                                    (if is-overridden 
                                                      "bg-yellow-50 border-yellow-300 hover:bg-yellow-100" 
                                                      "bg-gray-50 border-gray-200 hover:bg-gray-100"))
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
                                      new-input))))))
                       
                       ;; Action buttons
                       ($ :div {:className "pt-4 border-t border-gray-200 space-y-2"}
                          ($ :button {:className "w-full font-medium py-2 px-4 rounded-md transition-colors bg-blue-600 hover:bg-blue-700 text-white"
                                      :onClick on-execute-fork}
                             (str "Execute Fork (" (count changed-nodes) " changes)"))
                          ($ :button {:className "w-full bg-gray-300 hover:bg-gray-400 text-gray-700 font-medium py-2 px-4 rounded-md transition-colors"
                                      :onClick on-clear-fork}
                             "Clear All Changes")))))))))


(defn process-graph-data
  "Process raw graph data into nodes and edges for React Flow"
  [invokes-map implicit-edges]
  (let [g (new (.. Dagre -graphlib -Graph))

        nodes (s/select [s/ALL
                         (s/selected? s/LAST (s/must :emits))
                         (s/view (fn [[id data]]
                                   {:id (str id)
                                    :type "custom"
                                    :draggable false
                                    :data (assoc data
                                                 :label (str (:node data))
                                                 :node-id id
                                                 :is-phantom false)}))]
                        invokes-map)

        real-edges (for [[from [idx to]]
                         (s/select [s/ALL
                                    (s/collect-one s/FIRST)
                                    s/LAST
                                    (s/must :emits)
                                    s/INDEXED-VALS] invokes-map)]
                     {:id (str from (:invoke-id to) idx)
                      :source (str from)
                      :target (str (:invoke-id to))
                      :implicit? false})

        ;; All other nodes (phantoms etc.) from your original implementation...
        get-missing-children (fn [node-id]
                              (when-let [node-data (get invokes-map node-id)]
                                 (let [emitted-ids (set (map :invoke-id (:emits node-data)))
                                       node-ids (set (keys invokes-map))]
                                   (filter #(not (contains? node-ids %)) emitted-ids))))
        phantom-nodes (for [node nodes
                            :let [node-id (-> node :data :node-id)
                                  missing-children (get-missing-children node-id)]
                            :when (seq missing-children)]
                        (for [missing-child-id missing-children]
                          {:id (str "phantom-" node-id "-" missing-child-id)
                           :type "phantom"
                           :data {:label "Click to paginate"
                                  :parent-node-id node-id
                                  :missing-node-id missing-child-id
                                  :is-phantom true}}))
        phantom-nodes (apply concat phantom-nodes)
        phantom-edges (for [phantom phantom-nodes
                            :let [parent-id (-> phantom :data :parent-node-id)]]
                        {:id (str parent-id "->" (:id phantom))
                         :source (str parent-id)
                         :target (:id phantom)
                         :implicit? true ; Let's consider phantom edges as implicit too
                         })

        all-nodes (concat nodes phantom-nodes)
        ;; Combine real, implicit, and phantom edges
        all-edges (concat real-edges implicit-edges phantom-edges)]

    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {})

    (doall (for [edge all-edges] (.setEdge g (:source edge) (:target edge))))
    (doall (for [node all-nodes]
             (.setNode g (:id node) (clj->js
                                     (merge node {:width 170 :height 40})))))

    (Dagre/layout g)

    (let [nodes-with-layout (for [node all-nodes
                                  :let [position (.node g (:id node))]]
                              (assoc node
                                     :position position))]
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

(defui graph-view [{:keys [module-id agent-name invoke-id 
                            graph-data summary-data implicit-edges
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
               (let [{:keys [nodes edges]} (process-graph-data graph-data (or implicit-edges []))]
                 (println "Updating flow with nodes:" (count nodes) "and edges:" (count edges))
                 (set-nodes (clj->js nodes))
                 (set-edges (clj->js (for [edge edges]
                                       (if (:implicit? edge)
                                         (assoc edge :style #js {:strokeDasharray "5 5"
                                                                 :stroke "#aaa"})
                                         edge)))))))
           [graph-data implicit-edges])
        
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
                                       (on-select-node (:node-id node-data)))))
]
    
    (if (empty? graph-data)
      ($ :div.flex.justify-center.items-center.py-8
         ($ :div.text-gray-500 "No graph data available"))
      ($ :<>
         ;; Main content area with right margin for the stats panel
         ($ :div {:className "mr-80"}
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
                                                           node-className (str/join " " (concat base-classes selection-classes common-classes))]
                                                       ($ :div {:className "relative"}
                                                          ($ :div {:className node-className
                                                                   :style {:width "170px" :height "40px" :opacity (if is-affected "0.6" "1.0")}}
                                                             label)
                                                          (when (and (:result data) (not is-affected))
                                                            ($ :div {:className "absolute -top-1 -right-1 w-3 h-3 bg-green-500 rounded-full border-2 border-white shadow-sm"}))
                                                          (when has-changes
                                                            ($ :div {:className "absolute -top-1 -left-1 w-3 h-3 bg-orange-400 rounded-full border-2 border-white shadow-sm"}))
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
                                                             (:label data))
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
                                            :flow-nodes flow-nodes}))))
         
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
                         :is-live is-live})))))

;; =============================================================================
;; AGENT RUNNER AND HITL COMPONENTS
;; =============================================================================

(defui agent-runner-panel [{:keys [module-id agent-name on-run-agent]}]
  (let [[args-text set-args-text] (uix/use-state "[]")
        [stream-node set-stream-node] (uix/use-state "")
        [is-expanded set-is-expanded] (uix/use-state false)]
    
    ($ :div {:className "fixed top-4 right-4 z-50 bg-white shadow-lg rounded-lg border border-gray-200 max-w-sm"}
       ;; Header
       ($ :div {:className "p-3 border-b border-gray-200 cursor-pointer"
                :onClick #(set-is-expanded not)}
          ($ :div {:className "flex items-center justify-between"}
             ($ :div {:className "text-sm font-medium text-gray-700"} "🤖 Run Agent")
             ($ :div {:className (str "transform transition-transform " (if is-expanded "rotate-180" ""))}
                "▼")))
       
       ;; Expandable content
       (when is-expanded
         ($ :div {:className "p-3 space-y-3"}
            ($ :div
               ($ :label {:className "block text-xs font-medium text-gray-600 mb-1"} "Arguments (JSON)")
               ($ :textarea {:className "w-full h-16 p-2 text-xs border border-gray-300 rounded font-mono"
                             :value args-text
                             :onChange #(set-args-text (.-value (.-target %)))
                             :placeholder "[\"arg1\", \"arg2\"]"}))
            
            ($ :div
               ($ :label {:className "block text-xs font-medium text-gray-600 mb-1"} "Stream Node (optional)")
               ($ :input {:className "w-full p-2 text-xs border border-gray-300 rounded"
                          :value stream-node
                          :onChange #(set-stream-node (.-value (.-target %)))
                          :placeholder "node-name"}))
            
            ($ :button {:className "w-full bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium py-2 px-3 rounded transition-colors"
                        :onClick (fn []
                                   (try
                                     (let [parsed-args (js->clj (js/JSON.parse args-text))]
                                       (on-run-agent parsed-args (when-not (empty? stream-node) stream-node)))
                                     (catch :default e
                                       (js/alert "Invalid JSON in arguments"))))}
               "🚀 Run Agent"))))))

(defui human-input-modal [{:keys [request on-provide-input on-cancel]}]
  (let [{:keys [prompt node node-invoke-id]} request
        [response set-response] (uix/use-state "")]
    
    ($ :div {:className "fixed inset-0 flex items-center justify-center z-50"
             :style {:backgroundColor "rgba(0, 0, 0, 0.5)"}}
       ($ :div {:className "bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[80vh] overflow-hidden"}
          ;; Header
          ($ :div {:className "p-4 border-b border-gray-200"}
             ($ :h3 {:className "text-lg font-medium text-gray-800"} "🙋 Human Input Required")
             ($ :div {:className "text-sm text-gray-600 mt-1"} 
                (str "Node: " node " (" node-invoke-id ")")))
          
          ;; Content
          ($ :div {:className "p-4 space-y-4"}
             ;; Prompt
             ($ :div
                ($ :label {:className "block text-sm font-medium text-gray-700 mb-2"} "Prompt:")
                ($ :div {:className "bg-gray-50 p-3 rounded border text-sm"} prompt))
             
             ;; Response input
             ($ :div
                ($ :label {:className "block text-sm font-medium text-gray-700 mb-2"} "Your Response:")
                ($ :textarea {:className "w-full h-24 p-3 border border-gray-300 rounded text-sm resize-y"
                              :value response
                              :onChange #(set-response (.-value (.-target %)))
                              :placeholder "Type your response here..."
                              :autoFocus true})))
          
          ;; Actions
          ($ :div {:className "p-4 border-t border-gray-200 flex justify-end space-x-3"}
             ($ :button {:className "px-4 py-2 text-sm font-medium text-gray-700 bg-gray-100 hover:bg-gray-200 rounded transition-colors"
                         :onClick on-cancel}
                "Cancel")
             ($ :button {:className "px-4 py-2 text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 rounded transition-colors"
                         :onClick #(when-not (empty? response) (on-provide-input response))
                         :disabled (empty? response)}
                "✅ Send Response"))))))

(defui agent-status-banner [{:keys [status error]}]
  (when (or (= status :running) (= status :failed))
    ($ :div {:className (str "fixed top-0 left-0 right-0 z-40 p-3 text-center text-sm font-medium "
                             (case status
                               :running "bg-blue-100 text-blue-800"
                               :failed "bg-red-100 text-red-800"
                               "bg-gray-100 text-gray-800"))}
       (case status
         :running "🔄 Agent is running..."
         :failed (str "❌ Agent failed: " error)
         status))))

(defui token-stream-display [{:keys [streams selected-node-id]}]
  (when (and streams selected-node-id)
    (let [node-streams (get streams selected-node-id)]
      (when (seq node-streams)
        ($ :div {:className "mt-4 bg-gray-50 border rounded-lg p-3"}
           ($ :div {:className "text-sm font-medium text-gray-700 mb-2"} "🔄 Live Token Stream")
           ($ :div {:className "bg-white border rounded p-2 max-h-32 overflow-y-auto"}
              ($ :pre {:className "text-xs text-gray-800 whitespace-pre-wrap"}
                 (str/join "" node-streams))))))))

