(ns com.rpl.agent-o-rama.ui.invocation-graph-live
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.events] ;; ensure side-effecting events are registered
   ["wouter" :as wouter :refer [useParams]]))

(defui node-row [{:keys [node-id node-data]}]
  ($ :div.flex.items-start.justify-between.py-2.border-b.border-gray-100
     ($ :div
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
  (println "graph" module-id agent-name invoke-id)
  (let [;; Check if we're connected first
        connected? (state/use-sub [:sente :connected?])
        ;; Subscribe to the nodes for this specific invocation
        nodes (state/use-sub [:invocations-data invoke-id :graph :nodes])
        current-invoke-id (state/use-sub [:current-invocation :invoke-id])]
    
    ;; Effect for managing subscription
    (uix/use-effect
     (fn []
       (when (and connected? module-id agent-name invoke-id)
         ;; Only subscribe when connected and params are valid
         (println "Component requesting invocation:" invoke-id "current:" current-invoke-id "connected:" connected?)
         (state/dispatch [:invocation/view-live
                          {:module-id module-id
                           :agent-name agent-name
                           :invoke-id invoke-id}]))
       nil) ;; No cleanup here - let state manage switching between invocations
     ;; Re-run when any of these change
     #js [connected? module-id agent-name invoke-id])
    
    ;; Separate effect for cleanup when component fully unmounts
    (uix/use-effect
     (fn []
       ;; Return cleanup that only runs on unmount
       (fn []
         (println "Component fully unmounting, checking if we should stop subscription")
         ;; Check app-db directly in cleanup
         (let [db @state/app-db
               connected? (get-in db [:sente :connected?])
               active-sub (get-in db [:sente :active-subscription])]
           (when (and connected? active-sub)
             (println "Stopping subscription on unmount")
             (state/dispatch [:invocation/stop-live])))))
     []) ;; Empty deps - only run on mount/unmount

    ;; Don't render the live view until we're connected
    (if-not connected?
      ($ :div.flex.items-center.justify-center.p-8
         ($ :div.text-gray-500 "Connecting to server..."))
      
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
           ($ :div.text-gray-500 "Waiting for node updates..."))))))