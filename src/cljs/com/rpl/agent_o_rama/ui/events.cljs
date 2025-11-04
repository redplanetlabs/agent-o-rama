(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]
            [clojure.string :as str]))

;; Orchestration events that perform side-effects using sente helpers,
;; keeping React components pure.

;; =============================================================================
;; UNIFIED GRAPH PROCESSING
;; =============================================================================

(defn build-drawable-graph
  "Traverses the raw graph data to produce a coherent, drawable graph.
   It builds the set of reachable nodes, real edges, and implicit edges in a single pass.
   
   Filters out incomplete nodes (nodes without a :node field) which can occur due to
   backend race conditions where PState is queried before node execution populates all fields."
  [raw-nodes root-invoke-id historical-graph]
  (if (or (empty? raw-nodes) (not (get raw-nodes root-invoke-id)))
    {:nodes {} :edges [] :implicit-edges []}
    (loop [to-visit #{root-invoke-id}
           drawable-nodes {}
           real-edges []
           implicit-edges []
           visited #{}]
      (if (empty? to-visit)
        {:nodes drawable-nodes :edges real-edges :implicit-edges implicit-edges}
        (let [current-id (first to-visit)
              remaining-to-visit (disj to-visit current-id)]

          (if (visited current-id)
            (recur remaining-to-visit drawable-nodes real-edges implicit-edges visited)

            (let [node-data (get raw-nodes current-id)
                  node-name (:node node-data)]

              ;; Skip incomplete nodes (race condition: node started but :node field not yet populated)
              (if-not node-name
                (recur remaining-to-visit drawable-nodes real-edges implicit-edges (conj visited current-id))

                (let [static-info (get-in historical-graph [:node-map node-name])

                      ;; 1. FIND REAL EDGES & CHILDREN
                      ;; Filter out incomplete nodes (no :node field) from children
                      emitted-ids (set (map :invoke-id (:emits node-data)))
                      drawable-children (filter (fn [child-id]
                                                  (and (contains? raw-nodes child-id)
                                                       (:node (get raw-nodes child-id))))
                                                emitted-ids)

                      new-real-edges (for [child-id drawable-children]
                                       {:id (str "real-" current-id "-" child-id)
                                        :source (str current-id)
                                        :target (str child-id)})

                      ;; 2. FIND IMPLICIT EDGES (for aggregation contexts)
                      agg-context (:agg-context static-info)
                      is-agg-start? (and agg-context (not (:node-task-id node-data)))

                      {implicit-targets :targets
                       implicit-edge-list :edges}
                      (if-not is-agg-start?
                        {:targets [] :edges []}
                        (let [potential-outputs (:output-nodes static-info)
                              agg-node-invoke-id (:agg-invoke-id node-data)]
                          (reduce
                           (fn [acc out-node-name]
                             (let [is-agg-node? (= :agg-node (get-in historical-graph [:node-map out-node-name :node-type]))
                                   agg-node-data (get raw-nodes agg-node-invoke-id)]
                               ;; Only create implicit edge if:
                               ;; - It's an agg node
                               ;; - The agg node exists and is complete (has :node field)
                               ;; - We haven't visited it yet
                               ;; - There's no explicit emit to it already
                               (if (and is-agg-node?
                                        agg-node-invoke-id
                                        agg-node-data
                                        (:node agg-node-data) ; ← Filter incomplete nodes
                                        (not (contains? visited agg-node-invoke-id))
                                        (not (contains? emitted-ids agg-node-invoke-id)))
                                 {:targets (conj (:targets acc) agg-node-invoke-id)
                                  :edges (conj (:edges acc)
                                               {:id (str "implicit-" current-id "-" agg-node-invoke-id)
                                                :source (str current-id)
                                                :target (str agg-node-invoke-id)
                                                :implicit? true})}
                                 acc)))
                           {:targets [] :edges []}
                           (or potential-outputs []))))]

                  (recur (into remaining-to-visit (concat drawable-children implicit-targets))
                         (assoc drawable-nodes current-id node-data)
                         (into real-edges new-real-edges)
                         (into implicit-edges implicit-edge-list)
                         (conj visited current-id)))))))))))

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
                                     :agent-name agent-name}])
                   nil))

;; =============================================================================
;; UNIFIED STREAMING LOOP
;; =============================================================================

;; Kick off or continue fetching a page of graph data
(state/reg-event :invocation/fetch-graph-page
                 (fn [db {:keys [invoke-id module-id agent-name]}]
                   (sente/request!
                    [:invocations/get-graph-page
                     {:invoke-id invoke-id
                      :module-id module-id
                      :agent-name agent-name}]
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
                   (let [{:keys [nodes summary historical-graph root-invoke-id
                                 task-id is-complete]} page-data
                         current-invocation (get-in db [:current-invocation])]

                     ;; Update summary data
                     (when summary
                       (let [{:keys [forks fork-of]} summary
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

                     ;; ATOMIC UPDATE: Merge nodes AND set is-complete in single dispatch
                     ;; This prevents React from rendering between updates
                     (when (and nodes (seq nodes))
                       (state/dispatch [:invocation/merge-nodes-and-complete
                                        invoke-id
                                        nodes
                                        root-invoke-id
                                        is-complete]))

                     ;; If no nodes but we have is-complete, update it
                     (when (and (not (seq nodes)) (contains? page-data :is-complete))
                       (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] is-complete]))

                     ;; SIMPLIFIED POLLING: If not complete, schedule a simple poll
                     (when-not is-complete
                       (js/setTimeout
                        (fn []
                          (when-not (get-in @state/app-db [:invocations-data invoke-id :is-complete])
                            (println "[POLLING-SIMPLIFIED] Polling for updates...")
                            (state/dispatch [:invocation/fetch-graph-page current-invocation])))
                        1000))

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

(state/reg-event :invocation/merge-nodes-and-complete
                 (fn [db invoke-id new-nodes-map root-invoke-id-from-payload is-complete]
                   (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
                         current-raw-nodes (get-in db [:invocations-data invoke-id :graph :raw-nodes])
                         merged-raw-nodes (merge current-raw-nodes new-nodes-map)
                         root-invoke-id (or root-invoke-id-from-payload
                                            (get-in db [:invocations-data invoke-id :root-invoke-id]))

                         {:keys [nodes edges implicit-edges]}
                         (build-drawable-graph merged-raw-nodes root-invoke-id historical-graph)]

                     ;; ATOMIC: Update both graph nodes AND is-complete in single transformation
                     [:invocations-data invoke-id
                      (s/multi-path
                       [:graph :raw-nodes (s/terminal-val merged-raw-nodes)]
                       [:graph :nodes (s/terminal-val nodes)]
                       [:graph :edges (s/terminal-val edges)]
                       [:implicit-edges (s/terminal-val implicit-edges)]
                       [:is-complete (s/terminal-val is-complete)])])))

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
                   (state/dispatch [:db/set-value [:ui :hitl :submitting (:invoke-id request)] true])

                   (sente/request!
                    [:invocations/provide-human-input
                     {:module-id module-id
                      :agent-name agent-name
                      :invoke-id invoke-id
                      :request request
                      :response response}]
                    5000
                    (fn [reply]
                      (state/dispatch [:db/set-value [:ui :hitl :submitting (:invoke-id request)] false])
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
                      [:config/set {:module-id module-id :agent-name agent-name :key key :value value}]
                      10000 ;; 10 second timeout
                      (fn [reply]
                        (if (:success reply)
                          (do
                            (println "Config update success for" key)
                            (state/dispatch [:db/set-value state-path {:submitting? false :error nil}]))
                          (do
                            (js/console.error "Config update failed:" (:error reply))
                            (state/dispatch [:db/set-value state-path {:submitting? false :error (:error reply)}])
                            (when on-error (on-error (:error reply))))))))
                   nil))

(state/reg-event :config/submit-global-change
                 (fn [db {:keys [module-id key value on-success on-error]}]
                   (let [state-path [:ui :global-config-page (keyword key)]]
                     ;; Set loading state for this specific config item
                     (state/dispatch [:db/set-value state-path {:submitting? true :error nil}])

                     (sente/request!
                      [:config/set-global {:module-id module-id :key key :value value}]
                      10000 ;; 10 second timeout
                      (fn [reply]
                        (if (:success reply)
                          (do
                            (println "Global config update success for" key)
                            (state/dispatch [:db/set-value state-path {:submitting? false :error nil}]))
                          (do
                            (js/console.error "Global config update failed:" (:error reply))
                            (state/dispatch [:db/set-value state-path {:submitting? false :error (:error reply)}])
                            (when on-error (on-error (:error reply))))))))
                   nil))

 ;; =============================================================================
;; DATASET FORM EVENTS
;; =============================================================================

(state/reg-event :dataset/edit-example
                 (fn [db {:keys [module-id dataset-id snapshot-name example-id form-fields]}]
                   (let [input (get form-fields :input "")
                         output (get form-fields :output "")
                         form-id :edit-example]

                     (try
                       (when-not (str/blank? input) (js/JSON.parse input))
                       (when-not (str/blank? output) (js/JSON.parse output))

                       (sente/request!
                        [:datasets/edit-example {:module-id module-id
                                                 :dataset-id dataset-id
                                                 :snapshot-name snapshot-name
                                                 :example-id example-id
                                                 :input input
                                                 :output output}]
                        10000
                        (fn [reply]
                          (state/dispatch [:form/set-submitting form-id false])
                          (if (:success reply)
                            (do
                              (state/dispatch [:modal/hide])
                              (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                              (state/dispatch [:form/clear form-id]))
                            (state/dispatch [:form/set-error form-id (or (:error reply) "An unknown server error occurred.")]))))
                       (catch js/Error e
                         (state/dispatch [:form/set-submitting form-id false])
                         (state/dispatch [:form/set-error form-id (str "Invalid JSON: " (.-message e))]))))
                   nil))
;; =============================================================================
;; BULK OPERATION EVENTS
;; =============================================================================

(state/reg-event :dataset/delete-selected
                 (fn [db {:keys [module-id dataset-id snapshot-name example-ids]}]
                   (sente/request!
                    [:datasets/delete-examples {:module-id module-id
                                                :dataset-id dataset-id
                                                :snapshot-name snapshot-name
                                                :example-ids (vec example-ids)}]
                    15000
                    (fn [reply]
                      (if (:success reply)
                        (do
                          (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])
                          (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}]))
                        (js/alert (str "Failed to delete examples: " (:error reply))))))
                   nil))
