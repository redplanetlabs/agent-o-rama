(ns com.rpl.agent-o-rama.ui.agent-graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   [goog.i18n.DateTimeFormat :as dtf]
   [goog.date.UtcDateTime    :as utc-dt]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle]]
   ["@dagrejs/dagre" :as Dagre]))

(defn process-graph-data [{:keys [graph]}]
  (println "data!!" graph)
  {:nodes [] :edges []})

(defui graph [{:keys [initial-data]}]
  (let [[selected-node set-selected-node] (uix/use-state nil)
        ;; Process current graph data
        {:keys [nodes edges]} (process-graph-data initial-data)
        
        ;; Use React Flow's state management hooks
        [flow-nodes set-nodes on-nodes-change] (useNodesState (clj->js nodes))
        [flow-edges set-edges on-edges-change] (useEdgesState (clj->js edges))]
    

    
    ($ :div {:style {:width "100%" :height "500px"}}
       ($ ReactFlow {:nodes flow-nodes 
                     :edges flow-edges
                     :onNodesChange on-nodes-change
                     :onEdgesChange on-edges-change
                     :proOptions (clj->js {:hideAttribution true})
                     :nodeTypes
                     (clj->js {"custom"
                               (uix.core/as-react
                                (fn [{:keys [data id]}]
                                  (let [data (js->clj data :keywordize-keys true)
                                        label (:label data)
                                        node-id (:node-id data)
                                        selected (= (when selected-node (.-id selected-node)) id)
                                        base-classes (cond
                                                       :else
                                                       ["bg-white" "text-gray-800" "border-2" "border-gray-300"])
                                        selection-classes (if selected
                                                            ["ring-4" "ring-blue-400" "ring-opacity-75" "shadow-2xl" "transform" "scale-105"]
                                                            ["shadow-lg"])
                                        common-classes ["p-3" "rounded-md" "transition-all" "duration-200"]
                                        node-className (str/join " " (concat base-classes selection-classes common-classes))]
                                    ($ :div {:className "relative"}
                                       ($ :div {:className node-className
                                                :style {:width "170px" :height "40px"}}
                                          label)
                                       ($ Handle {:type "target" :position "top"})
                                       ($ Handle {:type "source" :position "bottom"})))))})
                     :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}}
                     :onNodeClick (fn [_ node] (set-selected-node node))}
          ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
          ($ Controls {:className "fill-gray-500 stroke-gray-500"})))))
