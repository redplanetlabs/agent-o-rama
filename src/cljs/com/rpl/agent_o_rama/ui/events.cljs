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
    (loop [to-visit #{root-invoke-id} ; A queue of nodes to process
           drawable-nodes {} ; The final map of nodes to render
           real-edges [] ; The final list of real edges
           implicit-edges [] ; The final list of implicit edges
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
                  extra-visits-vol (volatile! #{})
                  new-implicit-edges (when (and agg-context
                                                (not (contains? node-data :node-task-id)))
                                       (->> potential-outputs
                                            (filter #(= :agg-node (get-in historical-graph [:node-map % :node-type])))
                                            (mapcat (fn [out-agg-node-name]
                                                      (let [agg-node-invoke-id (:agg-invoke-id node-data)]
                                                        ;; Check if a real emit to this agg node already exists
                                                        (when (and agg-node-invoke-id
                                                                   (not (contains? emitted-ids agg-node-invoke-id))
                                                                   (contains? raw-nodes agg-node-invoke-id))
                                                          
                                                          (when (not (contains? visited agg-node-invoke-id))
                                                              (vswap! extra-visits-vol conj agg-node-invoke-id))
                                                          
                                                          [{:id (str "implicit-" current-id "-" agg-node-invoke-id)
                                                            :source (str current-id)
                                                            :target (str agg-node-invoke-id)
                                                            :implicit? true}]))))
                                            (filter some?)
                                            (vec)))]

              ;; 3. RECURSE
              (recur (into remaining-to-visit (concat drawable-children @extra-visits-vol))
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
                   (state/dispatch [:invocation/set-current {:invoke-id invoke-id
                                                             :module-id module-id
                                                             :agent-name agent-name}])

                   ;; Always fetch data on navigation to ensure fresh data
                   ;; Set status to loading immediately to prevent stale data display
                   (state/dispatch [:db/set-value [:invocations-data invoke-id :status] :loading])
                   (state/dispatch [:invocation/fetch-graph-page
                                    {:invoke-id invoke-id
                                     :module-id module-id
                                     :agent-name agent-name
                                     :leaves []
                                     :initial? true}])
                   nil))

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
                        (state/dispatch [:invocation/fetch-graph-error invoke-id (:error reply)]))))
                   nil))

(state/reg-event :invocation/fetch-graph-error
                 (fn [db invoke-id error-info]
                   [:invocations-data invoke-id (s/multi-path
                                                 [:status (s/terminal-val :error)]
                                                 [:error (s/terminal-val error-info)])]))

(state/reg-event :invocation/process-graph-page
                 (fn [db invoke-id page-data]
                   (let [{:keys [nodes next-leaves summary historical-graph root-invoke-id
                                 task-id is-complete]} page-data
                         current-invocation (get-in db [:current-invocation])]

                     ;; Always update the summary and completion status first.
                     (when summary
                       (let [{:keys [forks fork-of]} summary
                             ;; Build key-value pairs conditionally
                             kvps (cond-> [[[:invocations-data invoke-id :summary] summary]
                                           [[:invocations-data invoke-id :task-id] task-id]
                                           [[:invocations-data invoke-id :forks] forks]
                                           [[:invocations-data invoke-id :fork-of] fork-of]
                                           [[:invocations-data invoke-id :status] :success]]
                                    (some? historical-graph)
                                    (conj [[:invocations-data invoke-id :historical-graph] historical-graph])

                                    (some? root-invoke-id)
                                    (conj [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]))]
                         (state/dispatch (into [:db/set-values] kvps))))

                     (when (contains? page-data :is-complete)
                       (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] is-complete]))

                     ;; Then merge the new nodes into the existing graph.
                     (when (and nodes (seq nodes))
                       (state/dispatch [:invocation/merge-nodes invoke-id nodes root-invoke-id]))

                     ;; Now, decide on the next action based on the server response.
                     (cond
                       ;; If the agent is complete, simply stop.
                       is-complete
                       (do (println "[POLLING-STATELESS] Loop ended.") nil)

                       ;; If there are immediate next leaves, fast-poll.
                       (seq next-leaves)
                       (do
                         (println "[POLLING-STATELESS] Fast pagination: continuing...")
                         (state/dispatch [:invocation/fetch-graph-page
                                          (assoc current-invocation :leaves (vec next-leaves) :initial? false)]))

                       ;; Otherwise, schedule a slow poll.
                       :else
                       (do
                         (println "[POLLING-STATELESS] Scheduling delayed re-poll...")
                         (js/setTimeout
                          (fn []
                            (let [current-db @state/app-db
                                  is-still-incomplete? (not (get-in current-db [:invocations-data invoke-id :is-complete]))
                                  current-leaves (state/get-unfinished-leaves current-db invoke-id)]

                              (if-not is-still-incomplete?
                                (println "[POLLING-STATELESS] Delayed re-poll cancelled (agent completed).")
                                (do
                                  (state/dispatch [:db/update-value [:invocations-data invoke-id :idle-polls] (fnil inc 0)])
                                  (println "[POLLING-STATELESS] Delayed re-poll executing.")
                                  (state/dispatch [:invocation/fetch-graph-page
                                                   (assoc current-invocation :leaves current-leaves :initial? false)])))))
                          2000)))

                     nil)))

(state/reg-event :invocation/merge-nodes
                 (fn [db invoke-id new-nodes-map root-invoke-id-from-payload]
                   (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
                         current-raw-nodes (get-in db [:invocations-data invoke-id :graph :raw-nodes])
                         merged-raw-nodes (merge current-raw-nodes new-nodes-map)
                         ;; Prioritize the ID from the payload, fallback to the one in db.
                         root-invoke-id (or root-invoke-id-from-payload
                                            (get-in db [:invocations-data invoke-id :root-invoke-id]))

                         {:keys [nodes edges implicit-edges]}
                         (build-drawable-graph merged-raw-nodes root-invoke-id historical-graph)]

                     [:invocations-data invoke-id
                      (s/multi-path
                       [:graph :raw-nodes (s/terminal-val merged-raw-nodes)]
                       [:graph :nodes (s/terminal-val nodes)]
                       [:graph :edges (s/terminal-val edges)]
                       [:implicit-edges (s/terminal-val implicit-edges)])])))

(state/reg-event :invocation/cleanup
                 (fn [db {:keys [invoke-id]}]
                   (state/dispatch [:ui/clear-fork-state])
                   [:ui :selected-node-id (s/terminal-val nil)]))

(state/reg-event :ui/clear-fork-state
                 (fn [db]
                   [:ui (s/multi-path
                         [:changed-nodes (s/terminal-val {})]
                         [:selected-node-id (s/terminal-val nil)]
                         [:forking-mode? (s/terminal-val false)]
                         [:active-tab (s/terminal-val :info)]
                         [:hitl :responses (s/terminal-val {})])]))

;; =============================================================================
;; HUMAN-IN-THE-LOOP (HITL) EVENTS
;; =============================================================================

(state/reg-event :hitl/submit
                 (fn [db {:keys [module-id agent-name invoke-id request response]}]
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
                      (state/dispatch [:db/set-value [:ui :hitl :submitting (s/keypath (:invoke-id request))] false])
                      (if (:success reply)
                        (println "HITL response submitted successfully. Polling loop will automatically pick up new nodes.")
                        (js/console.error "HITL submit failed" (:error reply)))))
                   nil))

;; =============================================================================
;; CONFIGURATION EVENTS
;; =============================================================================

(state/reg-event :config/submit-change
                 (fn [db {:keys [module-id agent-name key value on-success on-error]}]
                   (let [state-path [:ui :config-page (keyword key)]]
                     ;; Set loading state for this specific config item
                     (state/dispatch [:db/set-value state-path {:submitting? true :error nil}])

                     (sente/request!
                      [:api/set-agent-config {:module-id module-id :agent-name agent-name :key key :value value}]
                      10000 ;; 10 second timeout
                      (fn [reply]
                        (if (:success reply)
                          (do
                            (println "Config update success for" key)
                            (state/dispatch [:db/set-value state-path {:submitting? false :error nil}])
                            (when on-success (on-success)))
                          (do
                            (js/console.error "Config update failed:" (:error reply))
                            (state/dispatch [:db/set-value state-path {:submitting? false :error (:error reply)}])
                            (when on-error (on-error (:error reply))))))))
                   nil)) ; No immediate state change
