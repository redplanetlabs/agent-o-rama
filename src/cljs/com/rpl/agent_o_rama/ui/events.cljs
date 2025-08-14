(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.specter :as s]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; =============================================================================
;; UNIFIED GRAPH PROCESSING
;; =============================================================================

(defn build-drawable-graph
  "Traverses the raw graph data to produce a coherent, drawable graph.
   It builds the set of reachable nodes, real edges, and implicit edges in a single pass."
  [raw-nodes root-invoke-id historical-graph]
  (if (or (empty? raw-nodes) (not (get raw-nodes root-invoke-id)))
    ;; We can't start drawing until the root node is available.
    {:nodes {} :real-edges [] :implicit-edges []}
    (loop [to-visit #{root-invoke-id}   ; A queue of nodes to process
           drawable-nodes {}            ; The final map of nodes to render
           real-edges []                ; The final list of real edges
           implicit-edges []            ; The final list of implicit edges
           visited #{}]
      (if (empty? to-visit)
        ;; The traversal is complete.
        {:nodes drawable-nodes :edges real-edges :implicit-edges implicit-edges}
        (let [current-id (first to-visit)
              remaining-to-visit (disj to-visit current-id)]
          
          (if (visited current-id)
            ;; If we've already processed this node, skip it.
            (recur remaining-to-visit drawable-nodes real-edges implicit-edges visited)
            
            (let [node-data (get raw-nodes current-id)
                  node-name (:node node-data)
                  static-info (get-in historical-graph [:node-map node-name])
                  
                  ;; 1. FIND REAL EDGES & CHILDREN
                  emitted-ids (set (map :invoke-id (:emits node-data)))
                  drawable-children (filter #(contains? raw-nodes %) emitted-ids)
                  new-real-edges (map (fn [child-id]
                                        {:id (str "real-" current-id "-" child-id)
                                         :source (str current-id)
                                         :target (str child-id)})
                                      drawable-children)
                                      
                  ;; 2. FIND IMPLICIT EDGES
                  agg-context (:agg-context static-info)
                  potential-outputs (:output-nodes static-info)
                  new-implicit-edges (when agg-context ; Only applies within an agg context
                                       (->> potential-outputs
                                            (filter #(= :agg-node (get-in historical-graph [:node-map % :node-type])))
                                            (mapcat (fn [out-agg-node-name]
                                                      (let [agg-node-invoke-id (:agg-invoke-id node-data)]
                                                        ;; Check if a real emit to this agg node already exists
                                                        (when (and agg-node-invoke-id
                                                                   (not (contains? emitted-ids agg-node-invoke-id))
                                                                   (contains? raw-nodes agg-node-invoke-id))
                                                          [{:id (str "implicit-" current-id "-" agg-node-invoke-id)
                                                            :source (str current-id)
                                                            :target (str agg-node-invoke-id)
                                                            :implicit? true}]))))
                                            (filter some?)
                                            (vec)))]

              ;; 3. RECURSE
              (recur (into remaining-to-visit drawable-children)
                     (assoc drawable-nodes current-id node-data)
                     (into real-edges new-real-edges)
                     (into implicit-edges (or new-implicit-edges []))
                     (conj visited current-id)))))))))



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
    
    ;; Check if the agent is already complete
    (let [is-complete? (get-in db [:invocations-data invoke-id :is-complete])]
      
      ;; Start loading if the agent isn't already marked as complete
      ;; The loop will naturally stop when the server reports completion
      (when-not is-complete?
        ;; Start from root. Mark this explicitly as the initial load.
        (state/dispatch [:invocation/fetch-graph-page
                         {:invoke-id invoke-id
                          :module-id module-id
                          :agent-name agent-name
                          :leaves []
                          :initial? true}]))
      
      ;; Return nil to indicate no immediate state change
      nil)))




;; =============================================================================
;; UNIFIED STREAMING LOOP
;; =============================================================================

;; Kick off or continue fetching a page of graph data
(state/reg-event :invocation/fetch-graph-page
  (fn [db {:keys [invoke-id module-id agent-name leaves initial?]}]
    (sente/request!
      [:api/fetch-graph-page
       {:invoke-id invoke-id
        :module-id module-id
        :agent-name agent-name
        :leaves (or leaves [])
        :initial? (boolean initial?)}]
      10000
      (fn [reply]
        (if (:success reply)
          (state/dispatch [:invocation/process-graph-page invoke-id (:data reply)])
          (println "Failed to fetch graph page:" (:error reply)))))
    nil))

;; Process a page response, merge nodes, and implement robust loop logic
(state/reg-event :invocation/process-graph-page
  (fn [db invoke-id page-data]
    (let [{:keys [nodes next-leaves 
                  summary historical-graph root-invoke-id 
                  task-id is-complete]} page-data
          current-invocation (get-in db [:current-invocation])]
      ;; Diagnostics: log the page payload driving the state machine
      (println "[POLLING-STATELESS] process-graph-page"
               {:invoke-id invoke-id
                :is-complete is-complete
                :has-more-leaves? (some? next-leaves)
                :nodes (when nodes (count nodes))
                :next-leaves (when next-leaves (count next-leaves))
                :has-summary (boolean summary)})
      
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

      ;; STATE MACHINE LOGIC: Simplified
      (cond
        ;; Chain ends when complete
        is-complete
        (do
          (println "[POLLING-STATELESS] Loop naturally ends. Agent complete for" invoke-id)
          nil)

        ;; Fast pagination if we have next leaves in the payload
        (seq next-leaves)
        (do
          (println "[POLLING-STATELESS] Fast pagination: continuing with" (count next-leaves) "leaves")
          (state/dispatch [:invocation/fetch-graph-page
                           (assoc current-invocation :leaves (vec next-leaves) :initial? false)]))

        ;; No immediate leaves; schedule delayed re-poll if still incomplete
        :else
        (do
          (println "[POLLING-STATELESS] Scheduling delayed re-poll...")
          (js/setTimeout
            (fn []
              (let [current-db @state/app-db
                    is-still-incomplete? (not (get-in current-db [:invocations-data invoke-id :is-complete]))
                    current-leaves (state/get-unfinished-leaves current-db invoke-id)
                    prior-idle (or (get-in current-db [:invocations-data invoke-id :idle-polls]) 0)
                    next-idle (inc prior-idle)]
                (println "[POLLING-STATELESS] delayed-check"
                         {:invoke-id invoke-id
                          :is-still-incomplete? is-still-incomplete?
                          :current-leaves (count current-leaves)
                          :idle-polls next-idle})
                (cond
                  (not is-still-incomplete?)
                  (println "[POLLING-STATELESS] Delayed re-poll cancelled. Agent completed in the meantime.")

                  (and (zero? (count current-leaves)) (>= next-idle 3))
                  (do
                    (println "[POLLING-STATELESS] No leaves for multiple cycles; marking complete locally for" invoke-id)
                    (state/dispatch [:db/set-values
                                     [[:invocations-data invoke-id :is-complete] true]
                                     [[:invocations-data invoke-id :idle-polls] 0]]))

                  :else
                  (do
                    (state/dispatch [:db/set-value [:invocations-data invoke-id :idle-polls] next-idle])
                    (println "[POLLING-STATELESS] Delayed re-poll executing for" invoke-id)
                    (state/dispatch [:invocation/fetch-graph-page
                                     (assoc current-invocation :leaves current-leaves :initial? false)])))))
            2000)))
      
      nil)))

;; Unified node merging with automatic implicit edge recalculation
(state/reg-event :invocation/merge-nodes
  (fn [db invoke-id new-nodes-map]
    (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
          ;; First, merge new raw data with existing raw data
          current-raw-nodes (get-in db [:invocations-data invoke-id :graph :raw-nodes])
          merged-raw-nodes (merge current-raw-nodes new-nodes-map)
          
          ;; Get metadata needed for processing
          root-invoke-id (get-in db [:invocations-data invoke-id :root-invoke-id])
          
          ;; Call our new unified function once to get the complete drawable graph state
          {:keys [nodes edges implicit-edges]}
          (build-drawable-graph merged-raw-nodes root-invoke-id historical-graph)]
                          
      ;; Atomically update both nodes and edges
      [:invocations-data invoke-id
       (s/multi-path
         [:graph :raw-nodes (s/terminal-val merged-raw-nodes)] ; Store all data received
         [:graph :nodes (s/terminal-val nodes)]                ; Store drawable nodes
         [:graph :edges (s/terminal-val edges)]                ; Store real drawable edges
         [:implicit-edges (s/terminal-val implicit-edges)])])))

;; Cleanup when leaving an invocation
(state/reg-event :invocation/cleanup
  (fn [db {:keys [invoke-id]}]
    ;; Any pending setTimeout callbacks will now see that
    ;; :is-complete is (or will be) true, or the invocation-data will be gone,
    ;; and will naturally stop themselves.
    ;; The main action is clearing UI state.
    (state/dispatch [:ui/clear-fork-state])
    [:ui :selected-node-id (s/terminal-val nil)]))

;; UI state management for forking
(state/reg-event :ui/clear-fork-state
  (fn [db]
    [:ui (s/multi-path
           [:changed-nodes (s/terminal-val {})]
           [:selected-node-id (s/terminal-val nil)]
           [:forking-mode? (s/terminal-val false)]
           [:hitl :responses (s/terminal-val {})])]))

;; =============================================================================
;; HUMAN-IN-THE-LOOP (HITL) EVENTS
;; =============================================================================

(state/reg-event :hitl/submit
  (fn [db {:keys [module-id agent-name invoke-id request response]}]
    ;; Set submitting flag to disable UI
    (state/dispatch [:db/set-value [:ui :hitl :submitting (s/keypath (:invoke-id request))] true])
    
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
        (state/dispatch [:db/set-value [:ui :hitl :submitting (s/keypath (:invoke-id request))] false])
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
    (println "[POLLING-STATELESS] Restarting data flow after external event for" invoke-id)
    ;; With the stateless approach, we simply trigger a new fetch
    ;; Any existing setTimeout callbacks will see the updated state and act accordingly
    ;; Start fresh from root after a brief delay to let the server settle
    (js/setTimeout 
      (fn []
        (state/dispatch [:invocation/start-graph-loading 
                         {:invoke-id invoke-id 
                          :module-id module-id 
                          :agent-name agent-name}]))
      500) ; 500ms delay to allow server state to settle
    nil))