(ns com.rpl.agent-o-rama.ui.invocations.react-flow-view
  (:require
   [re-frame.core :as rf]
   [uix.re-frame :refer [use-subscribe]]
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]
   [com.rpl.agent-o-rama.ui.invocations.graph-status :refer [node-status-bar]]
   ["@xyflow/react" :refer [ReactFlow Background Controls Handle MiniMap]]))

(defui react-flow-view
  [{:keys [invoke-id on-select-node on-paginate-node]}]
  (let [flow-data (use-subscribe [:invocation/flow-nodes-and-edges invoke-id])
        selected-node-id (use-subscribe [:invocation/selected-node-id invoke-id])
        changed-nodes (use-subscribe [:invocation/changed-nodes invoke-id])
        forking-mode? (use-subscribe [:invocation/forking-mode? invoke-id])
        affected-nodes (use-subscribe [:invocation/affected-nodes invoke-id])
        is-complete (use-subscribe [:invocation/is-complete invoke-id])
        {:keys [nodes edges]} (or flow-data {:nodes [] :edges []})
        flow-nodes (clj->js nodes)
        flow-edges (clj->js (for [edge edges]
                              (if (:implicit? edge)
                                (assoc edge :style #js {:strokeDasharray "5 5"
                                                        :stroke "#aaa"})
                                edge)))
        handle-select-node-click (fn [node]
                                   (when on-select-node
                                     (let [node-data (js->clj (aget node "data") :keywordize-keys true)]
                                       (on-select-node (:node-id node-data)))))]
    ($ :div {:style {:width "100%" :height "500px"}}
       ($ ReactFlow {:nodes flow-nodes
                     :edges flow-edges
                     :proOptions (clj->js {:hideAttribution true})
                     :nodeTypes (clj->js {"custom"
                                          (uix.core/as-react
                                           (fn [{:keys [data id]}]
                                             (let [data (js->clj data :keywordize-keys true)
                                                   label (:label data)
                                                   node-id (:node-id data)
                                                   selected (= (str selected-node-id) id)
                                                   has-changes (contains? changed-nodes node-id)
                                                   is-affected (and forking-mode? (contains? affected-nodes node-id))
                                                   in-progress? (and (:start-time-millis data)
                                                                     (not (:finish-time-millis data)))
                                                   is-stuck? (and in-progress? is-complete)
                                                   base-classes (cond
                                                                  is-affected
                                                                  ["bg-gray-300" "text-gray-500" "border-2" "border-gray-400"]
                                                                  has-changes
                                                                  ["bg-orange-500" "text-white" "border-2" "border-orange-600"]
                                                                  (gn/agg-node? data)
                                                                  ["bg-yellow-500" "text-white" "border-2" "border-yellow-600"]
                                                                  (gn/starter-node? data)
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
                                                     ($ :div {:className "truncate" :title label} label))
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
                                                                   (when on-paginate-node
                                                                     (on-paginate-node missing-node-id)))}
                                                  ($ :div {:className "bg-gray-100 text-gray-600 p-3 rounded-md shadow-lg border-2 border-dashed border-gray-400 hover:bg-gray-200 transition-colors"
                                                           :style {:width "170px" :height "40px"}}
                                                     ($ :div {:className "truncate" :title (:label data)}
                                                        (:label data)))
                                                  ($ Handle {:type "target" :position "top"})))))})
                     :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}}
                     :onNodeClick (fn [_ node] (handle-select-node-click node))}
          ($ MiniMap {:position "bottom-right" :pannable true :zoomable true})
          ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
          ($ Controls {:className "fill-gray-500 stroke-gray-500"})))))
