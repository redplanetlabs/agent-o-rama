(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.specter :as s]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; =============================================================================
;; GRAPH UTILITIES
;; =============================================================================

(defn generate-implicit-edges
  "Compares the static historical graph with the dynamic invocation trace to find
   paths to aggregation nodes that could have been taken but were not."
  [invokes-map historical-graph]
  (let [;; A map from {agg-node-invoke-id -> #{emitter-invoke-ids}}
        actual-emits-to-aggs (into {}
                                   (for [[id data] invokes-map
                                         :when (:agg-state data)] ; Check if it's an agg-node trace
                                     [id (set (map :invoke-id (:agg-inputs-first-10 data)))]))]
    (->> invokes-map
         (mapcat (fn [[invoke-id invoke-data]]
                   (let [node-name     (:node invoke-data)
                         static-info   (get-in historical-graph [:node-map node-name])
                         agg-context   (:agg-context static-info)
                         potential-outputs (:output-nodes static-info)]
                     
                     ;; Only consider nodes that are inside an aggregation context
                     (when agg-context
                       (for [out-name potential-outputs
                             ;; We only care about potential outputs that ARE aggregation nodes
                             :when (= :agg-node (get-in historical-graph [:node-map out-name :node-type]))]
                         (let [;; This is the invoke-id of the aggregation this node belongs to.
                               agg-node-invoke-id (:agg-invoke-id invoke-data)
                               actual-emitters    (get actual-emits-to-aggs agg-node-invoke-id)
                               did-emit?          (contains? actual-emitters invoke-id)]
                           
                           (when (and (not did-emit?) agg-node-invoke-id)
                             {:source      (str invoke-id)
                              :target      (str agg-node-invoke-id)
                              :id          (str "implicit-" invoke-id "-" agg-node-invoke-id)
                              :implicit?   true})))))))
         (filter some?)
         (vec))))



;; =============================================================================
;; ROBUST STREAMING LOOP WITH STATE MANAGEMENT
;; =============================================================================

;; Main entry point for loading any invocation (live or historical)
;; This is the single entry point to start or restart polling
(state/reg-event :invocation/start-graph-loading
  (fn [db {:keys [invoke-id module-id agent-name]}]
    ;; Set current invocation context first
    (state/dispatch [:invocation/set-current {:invoke-id invoke-id 
                                              :module-id module-id 
                                              :agent-name agent-name}])
    
    ;; Check if polling is already active for this invocation
    (let [polling-active? (get-in db [:invocations-data invoke-id :polling-active?])
          is-complete? (get-in db [:invocations-data invoke-id :is-complete])]
      
      ;; Only start polling if it's not already active and the agent isn't complete
      (when (and (not polling-active?) (not is-complete?))
        ;; Mark polling as active
        (state/dispatch [:db/set-value [:invocations-data invoke-id :polling-active?] true])
        
        ;; Start from root (empty leaves signals "fetch from root")
        (state/dispatch [:invocation/fetch-graph-page
                         {:invoke-id invoke-id
                          :module-id module-id
                          :agent-name agent-name
                          :leaves []}]))
      
      ;; Return nil to indicate no immediate state change
      nil)))



;; =============================================================================
;; UNIFIED STREAMING LOOP
;; =============================================================================

;; Kick off or continue fetching a page of graph data
(state/reg-event :invocation/fetch-graph-page
  (fn [db {:keys [invoke-id module-id agent-name leaves]}]
    (sente/request!
      [:api/fetch-graph-page
       {:invoke-id invoke-id
        :module-id module-id
        :agent-name agent-name
        :leaves (or leaves [])}]
      10000
      (fn [reply]
        (if (:success reply)
          (state/dispatch [:invocation/process-graph-page invoke-id (:data reply)])
          (println "Failed to fetch graph page:" (:error reply)))))
    nil))

;; Process a page response, merge nodes, and implement robust loop logic
(state/reg-event :invocation/process-graph-page
  (fn [db invoke-id page-data]
    (let [{:keys [nodes next-leaves has-more-leaves? 
                  summary historical-graph root-invoke-id 
                  task-id is-complete]} page-data
          current-invocation (get-in db [:current-invocation])]
      
      ;; If this is the first page (contains summary), store all metadata
      (when summary
        (state/dispatch [:db/set-values
                         [[:invocations-data invoke-id :summary] summary]
                         [[:invocations-data invoke-id :historical-graph] historical-graph]
                         [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]
                         [[:invocations-data invoke-id :task-id] task-id]
                         [[:invocations-data invoke-id :is-complete] is-complete]])) 
      
      ;; Always update is-complete status from server response (may change during execution)
      (when (contains? page-data :is-complete)
        (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] is-complete]))
      
      ;; Merge new nodes and recompute implicit edges via unified event
      (when (and nodes (seq nodes))
        (state/dispatch [:invocation/merge-nodes invoke-id nodes]))

      ;; STATE MACHINE LOGIC: Decide how to continue the polling loop
      (cond
        ;; Case 1: Agent is complete - Stop the loop
        is-complete
        (do
          (println "[POLLING] Agent is complete, stopping loop for" invoke-id)
          (state/dispatch [:db/set-value [:invocations-data invoke-id :polling-active?] false]))
        
        ;; Case 2: Fast pagination - More leaves available, continue immediately
        has-more-leaves?
        (do
          (println "[POLLING] Fast pagination: continuing with" (count (or next-leaves [])) "leaves")
          (state/dispatch [:invocation/fetch-graph-page
                           (assoc current-invocation :leaves (or next-leaves []))] ))
        
        ;; Case 3: No more known leaves BUT agent is still running - Delayed re-poll
        (not is-complete)
        (do
          (println "[POLLING] No more leaves but agent still running, scheduling delayed re-poll")
          (js/setTimeout
            (fn []
              ;; Double-check that polling is still active before re-polling
              (let [current-db @state/app-db
                    still-polling? (get-in current-db [:invocations-data invoke-id :polling-active?])
                    still-incomplete? (not (get-in current-db [:invocations-data invoke-id :is-complete]))]
                (when (and still-polling? still-incomplete?)
                  (println "[POLLING] Delayed re-poll executing for" invoke-id)
                  ;; Get current frontier leaves to continue from where we left off
                  (let [unfinished-leaves (state/get-unfinished-leaves current-db invoke-id)]
                    (state/dispatch [:invocation/fetch-graph-page
                                     (assoc current-invocation :leaves unfinished-leaves)])))))
            2000)) ; Poll again in 2 seconds
        
        ;; Case 4: Agent is complete and no more leaves - Stop 
        :else
        (do
          (println "[POLLING] Agent complete and no leaves, stopping loop for" invoke-id)
          (state/dispatch [:db/set-value [:invocations-data invoke-id :polling-active?] false])))
      
      nil)))

;; Unified node merging with automatic implicit edge recalculation
(state/reg-event :invocation/merge-nodes
  (fn [db invoke-id new-nodes-map]
    (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
          ;; Merge new nodes with existing ones
          current-nodes (get-in db [:invocations-data invoke-id :graph :nodes])
          merged-nodes (merge current-nodes new-nodes-map)
          
          ;; Always recalculate implicit edges when we have the historical graph
          implicit-edges (if (and merged-nodes historical-graph)
                          (generate-implicit-edges merged-nodes historical-graph)
                          [])]
                          
      ;; Atomically update both nodes and edges
      [:invocations-data invoke-id
       (s/multi-path
         [:graph :nodes (s/terminal-val merged-nodes)]
         [:implicit-edges (s/terminal-val implicit-edges)])])))

;; Cleanup when leaving an invocation
(state/reg-event :invocation/cleanup
  (fn [db {:keys [invoke-id]}]
    ;; Stop any active polling for this invocation
    (when invoke-id
      (state/dispatch [:db/set-value [:invocations-data invoke-id :polling-active?] false]))
    ;; Clear UI state
    (state/dispatch [:ui/clear-fork-state])
    [:ui :selected-node-id (s/terminal-val nil)]))

;; UI state management for forking
(state/reg-event :ui/clear-fork-state
  (fn [db]
    [:ui (s/multi-path
           [:changed-nodes (s/terminal-val {})]
           [:selected-node-id (s/terminal-val nil)]
           [:forking-mode? (s/terminal-val false)])]))

;; =============================================================================
;; HUMAN-IN-THE-LOOP (HITL) EVENTS
;; =============================================================================

(state/reg-event :hitl/submit
  (fn [db {:keys [module-id agent-name invoke-id request response]}]
    ;; Set submitting flag to disable UI
    (state/dispatch [:db/set-value [:ui :hitl :submitting (:invoke-id request)] true])
    
    (sente/request!
      [:api/provide-human-input
       {:module-id module-id
        :agent-name agent-name
        :invoke-id invoke-id
        :request request
        :response response}]
      5000
      (fn [reply]
        ;; Clear submitting flag
        (state/dispatch [:db/update-value [:ui :hitl :submitting] #(dissoc % (:invoke-id request))])
        (if (:success reply)
          (do
            (println "HITL response submitted successfully")
            ;; CRITICAL: Restart polling to pick up new nodes created after HITL unblock
            ;; This gracefully restarts the loop from the root, finding new state
            (state/dispatch [:invocation/restart-polling 
                             {:invoke-id invoke-id 
                              :module-id module-id 
                              :agent-name agent-name}]))
          (js/console.error "HITL submit failed" (:error reply)))))
    nil))

;; Restart polling after external events (like HITL submission)
(state/reg-event :invocation/restart-polling
  (fn [db {:keys [invoke-id module-id agent-name]}]
    (println "[POLLING] Restarting polling after external event for" invoke-id)
    ;; Stop current polling cleanly
    (state/dispatch [:db/set-value [:invocations-data invoke-id :polling-active?] false])
    ;; Start fresh from root after a brief delay to let the server settle
    (js/setTimeout 
      (fn []
        (state/dispatch [:invocation/start-graph-loading 
                         {:invoke-id invoke-id 
                          :module-id module-id 
                          :agent-name agent-name}]))
      500) ; 500ms delay to allow server state to settle
    nil))