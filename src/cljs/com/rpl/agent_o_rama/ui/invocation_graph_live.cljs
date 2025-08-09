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
   Expects node updates via [:graph/node-update {:node-id ... :node-data ...}]."
  [{:keys [module-id agent-name invoke-id]}]
  (let [{:strs [module-id p-agent-name p-invoke-id]}
        (js->clj (useParams))
        module-id (or module-id module-id)
        agent-name (or agent-name p-agent-name)
        invoke-id (or invoke-id p-invoke-id)

        nodes (state/use-sub [:current-invocation :graph :nodes])]

    ;; Initialize current invocation context only; polling managed in state event
    (uix/use-effect
     (fn []
       (when (and module-id agent-name invoke-id)
         (state/dispatch [:invocation/set-current
                          {:module-id module-id
                           :agent-name agent-name
                           :invoke-id invoke-id}]))
       (constantly nil))
     #js [module-id agent-name invoke-id])

    ;; No start/stop here; handled by :invocation/set-current in state.cljs

    ($ :div
       ($ :div.flex.items-center.justify-between.mb-3
          ($ :h3.text-lg.font-semibold.text-gray-700 "Live Invocation")
          ($ :div.font-mono.text-xs.text-gray-500
             (str (or invoke-id ""))))

       (if (and nodes (seq nodes))
         ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.divide-y.divide-gray-100
            (for [[nid ndata] nodes]
              ($ node-row {:key (str nid)
                           :node-id nid
                           :node-data ndata})))
         ($ :div.text-gray-500 "Waiting for node updates...")))))


