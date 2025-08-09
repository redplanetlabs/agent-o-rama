(ns com.rpl.agent-o-rama.ui.invocation-graph-live
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.events] ;; ensure side-effecting events are registered
   ["wouter" :as wouter :refer [useParams]]))

(defui node-row [{:keys [node-id node-data]}]
  ($ :div.flex.items-start.justify-between.py-2.border-b.border-gray-100
     ($ :div.flex-1
        ($ :div.font-mono.text-xs.text-gray-500 (str node-id))
        ($ :div.mt-1.text-sm.text-gray-800
           (common/pp (dissoc node-data :chunks :stream)))))
     ($ :div.ml-4.text-xs
        (cond
          (:failure? node-data) ($ :span.px-2.py-1.bg-red-100.text-red-700.rounded "Failed")
          (:success? node-data) ($ :span.px-2.py-1.bg-green-100.text-green-700.rounded "Success")
          :else ($ :span.px-2.py-1.bg-blue-100.text-blue-700.rounded "Running"))))

(defui graph
  "Minimal live invocation graph that reacts to server pushes.
   Pure view component - subscription management happens in state layer."
  [{:keys [module-id agent-name invoke-id]}]
  (let [ ;; Just subscribe to the data we need
        nodes (state/use-sub [:current-invocation :graph :nodes])
        current-invoke-id (state/use-sub [:current-invocation :invoke-id])]
    
    ;; Super simple: just tell state what we want to view
    ;; State layer handles all subscription lifecycle
    (uix/use-effect
     (fn []
       (when (and module-id agent-name invoke-id)
         ;; Only dispatch if we're changing to a different invocation
         (when (not= current-invoke-id final-invoke-id)
           (state/dispatch [:invocation/view-live
                            {:module-id module-id
                             :agent-name agent-name
                             :invoke-id invoke-id}])))
       ;; No cleanup - state manages everything
       nil)
     ;; Minimal deps - just the invocation ID we want to view
     #js [final-invoke-id])

    ($ :div
       ($ :div.flex.items-center.justify-between.mb-3
          ($ :h3.text-lg.font-semibold.text-gray-700 "Live Invocation")
          ($ :div.font-mono.text-xs.text-gray-500
             (str (or final-invoke-id ""))))

       (if (and nodes (seq nodes))
         ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.divide-y.divide-gray-100
            (for [[nid ndata] nodes]
              ($ node-row {:key (str nid)
                           :node-id nid
                           :node-data ndata})))
         ($ :div.text-gray-500 "Waiting for node updates...")))))