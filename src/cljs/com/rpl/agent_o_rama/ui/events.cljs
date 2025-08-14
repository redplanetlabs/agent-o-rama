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
;; UNIFIED INVOCATION LOADING
;; =============================================================================

;; Main entry point for loading any invocation (live or historical)
(state/reg-event :invocation/start-graph-loading

  (fn [db {:keys [invoke-id module-id agent-name]}]
    ;; Check if we already have data for this invocation
    (let [existing-summary (get-in db [:invocations-data invoke-id :summary])]
      (when-not existing-summary
        ;; Start unified streaming loop directly - first request includes summary
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

;; Process a page response, merge nodes, and loop if needed
(state/reg-event :invocation/process-graph-page
  (fn [db invoke-id page-data]
    (let [{:keys [nodes next-leaves has-more-leaves? 
                  summary historical-graph root-invoke-id 
                  task-id is-complete]} page-data]
      
      ;; If this is the first page (contains summary), store all metadata
      (when summary
        (state/dispatch [:db/set-values
                         [[:invocations-data invoke-id :summary] summary]
                         [[:invocations-data invoke-id :historical-graph] historical-graph]
                         [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]
                         [[:invocations-data invoke-id :task-id] task-id]
                         [[:invocations-data invoke-id :is-complete] is-complete]])) 
      
      ;; Merge new nodes and recompute implicit edges via unified event
      (when (and nodes (seq nodes))
        (state/dispatch [:invocation/merge-nodes invoke-id nodes]))

      ;; Only continue the loop if server indicates there are more leaves
      ;; The :is-complete flag from summary is the source of truth for agent completion
      (when has-more-leaves?
        (let [current (get-in db [:current-invocation])]
          (state/dispatch [:invocation/fetch-graph-page
                           (assoc current :leaves (or next-leaves []))])))
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
          (println "HITL response submitted successfully")
          (js/console.error "HITL submit failed" (:error reply)))))
    nil))