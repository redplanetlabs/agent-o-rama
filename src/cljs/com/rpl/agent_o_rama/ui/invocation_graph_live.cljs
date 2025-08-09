(ns com.rpl.agent-o-rama.ui.invocation-graph-live
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.events] ;; ensure side-effecting events are registered
   [com.rpl.agent-o-rama.ui.invocation-graph :as ig] ;; Import the main graph component
   ["wouter" :as wouter :refer [useParams]]))

(defui graph
  "Live invocation graph that reacts to server pushes.
   Reuses the main invocation-graph component with live data."
  [{:keys [module-id agent-name invoke-id]}]
  (println "Live graph" module-id agent-name invoke-id)
  (let [;; Check if we're connected first
        connected? (state/use-sub [:sente :connected?])
        ;; Subscribe to the nodes for this specific invocation
        nodes (state/use-sub [:invocations-data invoke-id :graph :nodes])
        current-invoke-id (state/use-sub [:current-invocation :invoke-id])
        
        ;; Convert nodes map to invokes-map format expected by the main graph
        invokes-map (when nodes
                      (into {} 
                        (for [[node-id node-data] nodes]
                          [node-id node-data])))
        
        ;; Create mock data structure that the main graph component expects
        mock-data (when invokes-map
                    {:invokes-map invokes-map
                     :summary {:result nil} ;; Will be populated as nodes complete
                     :implicit-edges []
                     :next-task-invoke-pairs []})]
    
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
      
      ;; Use the main graph component with live data
      ($ :div
         ($ :div.mb-4.bg-blue-50.border.border-blue-200.rounded-lg.p-4
            ($ :div.flex.items-center.justify-between
               ($ :div.flex.items-center.gap-2
                  ($ :div.h-3.w-3.bg-green-500.rounded-full.animate-pulse)
                  ($ :span.text-sm.font-medium.text-blue-700 "Live Mode")
                  ($ :span.text-sm.text-blue-600 
                     (str "Viewing: " (or invoke-id "..."))))
               ($ :div.text-xs.text-blue-500
                  (str (count nodes) " nodes loaded"))))
         
         ;; Render the main graph component with our live data
         (if mock-data
           ($ ig/graph {:module-id module-id
                       :agent-name agent-name
                       :invoke-id invoke-id
                       :initial-data mock-data
                       :live-mode? true})
           ($ :div.flex.items-center.justify-center.p-8
              ($ :div.text-gray-500 "Waiting for nodes...")))))))