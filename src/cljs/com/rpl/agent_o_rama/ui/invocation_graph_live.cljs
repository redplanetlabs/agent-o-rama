(ns com.rpl.agent-o-rama.ui.invocation-graph-live
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.events] ;; ensure side-effecting events are registered
   [com.rpl.agent-o-rama.ui.invocation-graph :as ig] ;; Import the main graph component
   ["wouter" :as wouter :refer [useParams]]))

(defui graph
  "Live invocation graph that uses client-driven polling for updates.
   Reuses the main invocation-graph component with live data."
  [{:keys [module-id agent-name invoke-id]}]
  (println "Live graph" module-id agent-name invoke-id)
  (let [;; Check if we're connected first
        connected? (state/use-sub [:sente :connected?])
        ;; Subscribe to the nodes for this specific invocation
        nodes (state/use-sub [:invocations-data invoke-id :graph :nodes])
        ;; Get the next leaves for pagination
        next-leaves (state/use-sub [:invocations-data invoke-id :next-leaves])
        ;; Check if the graph is complete
        is-complete (state/use-sub [:invocations-data invoke-id :is-complete])
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
    
    ;; Polling effect for fetching updates
    (uix/use-effect
     (fn []
       (when (and connected? (not is-complete) invoke-id)
         (let [leaves (or next-leaves [])
               ;; Define the polling function
               poll-fn (fn []
                        (println "Polling for updates with leaves:" leaves)
                        (com.rpl.agent-o-rama.ui.sente/push! 
                         [:live/get-updates 
                          {:module-id module-id
                           :agent-name agent-name
                           :invoke-id invoke-id
                           :leaves leaves}]))
               ;; Start the poller with 2 second interval
               interval-id (js/setInterval poll-fn 2000)]
           
           ;; Initial fetch immediately
           (poll-fn)
           
           ;; Cleanup function to stop the poller
           (fn [] 
             (js/clearInterval interval-id)))))
     ;; Re-run when these change
     #js [invoke-id connected? is-complete next-leaves module-id agent-name])
    
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