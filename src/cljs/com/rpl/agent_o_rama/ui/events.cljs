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
                   (state/dispatch [:invocation/set-current {:invoke-id invoke-id
                                                             :module-id module-id
                                                             :agent-name agent-name}])

                   (let [is-complete? (get-in db [:invocations-data invoke-id :is-complete])]

                     (when-not is-complete?
                       (state/dispatch [:invocation/fetch-graph-page
                                        {:invoke-id invoke-id
                                         :module-id module-id
                                         :agent-name agent-name
                                         :leaves []
                                         :initial? true}]))
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
                        nil)))
                   nil))

(state/reg-event :invocation/process-graph-page
                 (fn [db invoke-id page-data]
                   (let [{:keys [nodes next-leaves summary historical-graph root-invoke-id
                                 task-id is-complete]} page-data
                         current-invocation (get-in db [:current-invocation])
                         was-incomplete? (not (get-in db [:invocations-data invoke-id :is-complete]))]

                     (when summary
                       (state/dispatch [:db/set-values
                                        [[:invocations-data invoke-id :summary] summary]
                                        [[:invocations-data invoke-id :historical-graph] historical-graph]
                                        [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]
                                        [[:invocations-data invoke-id :task-id] task-id]]))

                     (when (contains? page-data :is-complete)
                       (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] is-complete]))

                     (when (and nodes (seq nodes))
                       (state/dispatch [:invocation/merge-nodes invoke-id nodes]))

                     (cond
                       (and is-complete was-incomplete?)
                       (do
                         (println "[POLLING-STATELESS] Agent is complete. Fetching final summary for" invoke-id)
                         (state/dispatch [:invocation/fetch-graph-page
                                          (assoc current-invocation :leaves [] :initial? true)]))

                       is-complete
                       (do (println "[POLLING-STATELESS] Loop ended.") nil)

                       (seq next-leaves)
                       (do
                         (println "[POLLING-STATELESS] Fast pagination: continuing...")
                         (state/dispatch [:invocation/fetch-graph-page
                                          (assoc current-invocation :leaves (vec next-leaves) :initial? false)]))

                       :else
                       (do
                         (println "[POLLING-STATELESS] Scheduling delayed re-poll...")
                         (js/setTimeout
                          (fn []
                            (let [current-db @state/app-db
                                  is-still-incomplete? (not (get-in current-db [:invocations-data invoke-id :is-complete]))
                                  current-leaves (state/get-unfinished-leaves current-db invoke-id)]

                              (if-not is-still-incomplete?
                                (println "[POLLING-STATELESS] Delayed re-poll cancelled.")
                                (do
                                  (state/dispatch [:db/update-value [:invocations-data invoke-id :idle-polls] (fnil inc 0)])
                                  (println "[POLLING-STATELESS] Delayed re-poll executing.")
                                  (state/dispatch [:invocation/fetch-graph-page
                                                   (assoc current-invocation :leaves current-leaves :initial? false)])))))
                          2000)))
                     nil)))

(state/reg-event :invocation/merge-nodes
                 (fn [db invoke-id new-nodes-map]
                   (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
                         current-raw-nodes (get-in db [:invocations-data invoke-id :graph :raw-nodes])
                         merged-raw-nodes (merge current-raw-nodes new-nodes-map)

                         root-invoke-id (get-in db [:invocations-data invoke-id :root-invoke-id])

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