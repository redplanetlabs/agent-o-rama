(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.rpc :as rpc]
            [com.rpl.agent-o-rama.ui.forms :as forms]
            [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]
            [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
            [com.rpl.agent-o-rama.impl.ui.rpc.config :as rpc-config]
            [re-frame.core :as rf]
            [re-frame.db :as rdb]
            [clojure.string :as str]))

;; Orchestration events that perform side-effects (HTTP RPC),
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
    (loop [to-visit       #{root-invoke-id}
           drawable-nodes {}
           real-edges     []
           implicit-edges []
           visited        #{}]
      (if (empty? to-visit)
        {:nodes drawable-nodes :edges real-edges :implicit-edges implicit-edges}
        (let [current-id         (first to-visit)
              remaining-to-visit (disj to-visit current-id)]

          (if (visited current-id)
            (recur remaining-to-visit drawable-nodes real-edges implicit-edges visited)

            (let [node-data (get raw-nodes current-id)
                  node-name (:node node-data)]

              (if-not node-name
                (recur remaining-to-visit
                       drawable-nodes
                       real-edges
                       implicit-edges
                       (conj visited current-id))

                (let [static-info (get-in historical-graph [:node-map node-name])
                      emitted-ids (set (map :invoke-id (:emits node-data)))
                      drawable-children (filter (fn [child-id]
                                                  (and (contains? raw-nodes child-id)
                                                       (:node (get raw-nodes child-id))))
                                                emitted-ids)

                      new-real-edges (for [child-id drawable-children]
                                       {:id     (str "real-" current-id "-" child-id)
                                        :source (str current-id)
                                        :target (str child-id)})

                      agg-context (:agg-context static-info)
                      is-agg-start? (and agg-context (not (:incomplete? node-data)))

                      {implicit-targets   :targets
                       implicit-edge-list :edges}
                      (if-not is-agg-start?
                        {:targets [] :edges []}
                        (let [potential-outputs  (:output-nodes static-info)
                              agg-node-invoke-id (:agg-invoke-id node-data)]
                          (reduce
                           (fn [acc out-node-name]
                             (let [is-agg-node?  (= :agg-node
                                                    (get-in historical-graph
                                                            [:node-map out-node-name :node-type]))
                                   agg-node-data (get raw-nodes agg-node-invoke-id)]
                               (if (and is-agg-node?
                                        agg-node-invoke-id
                                        agg-node-data
                                        (:node agg-node-data)
                                        (not (contains? visited agg-node-invoke-id))
                                        (not (contains? emitted-ids agg-node-invoke-id)))
                                 {:targets (conj (:targets acc) agg-node-invoke-id)
                                  :edges   (conj (:edges acc)
                                                 {:id        (str "implicit-" current-id
                                                                  "-" agg-node-invoke-id)
                                                  :source    (str current-id)
                                                  :target    (str agg-node-invoke-id)
                                                  :implicit? true})}
                                 acc)))
                           {:targets [] :edges []}
                           (or potential-outputs []))))]

                  (recur (into remaining-to-visit (concat drawable-children implicit-targets))
                         (assoc drawable-nodes current-id node-data)
                         (into real-edges new-real-edges)
                         (into implicit-edges implicit-edge-list)
                         (conj visited current-id)))))))))))

(defn- nodes->map [nodes]
  (cond
    (map? nodes) nodes
    (sequential? nodes) (into {} (map (juxt :invoke-id identity) nodes))
    :else {}))

(defn- apply-summary-kvps [db invoke-id page-data]
  (let [{:keys [summary task-id forks fork-of historical-graph root-invoke-id]} page-data]
    (if-not summary
      db
      (let [kvps (cond-> [[[:invocations-data invoke-id :summary] summary]
                          [[:invocations-data invoke-id :task-id] task-id]
                          [[:invocations-data invoke-id :forks] forks]
                          [[:invocations-data invoke-id :fork-of] fork-of]
                          [[:invocations-data invoke-id :status] :success]]
                   (some? historical-graph)
                   (conj [[:invocations-data invoke-id :historical-graph] historical-graph])

                   (some? root-invoke-id)
                   (conj [[:invocations-data invoke-id :root-invoke-id] root-invoke-id]))]
        (reduce (fn [d [p v]] (assoc-in d p v)) db kvps)))))

(defn- merge-nodes-and-complete-into-db [db invoke-id new-nodes-map root-invoke-id-from-payload is-complete]
  (let [historical-graph (get-in db [:invocations-data invoke-id :historical-graph])
        current-raw-nodes (get-in db [:invocations-data invoke-id :graph :raw-nodes])
        merged-raw-nodes (merge current-raw-nodes new-nodes-map)
        root-invoke-id (or root-invoke-id-from-payload
                           (get-in db [:invocations-data invoke-id :root-invoke-id]))
        {:keys [nodes edges implicit-edges]}
        (build-drawable-graph merged-raw-nodes root-invoke-id historical-graph)]
    (-> db
        (assoc-in [:invocations-data invoke-id :graph :raw-nodes] merged-raw-nodes)
        (assoc-in [:invocations-data invoke-id :graph :nodes] nodes)
        (assoc-in [:invocations-data invoke-id :graph :edges] edges)
        (assoc-in [:invocations-data invoke-id :implicit-edges] implicit-edges)
        (assoc-in [:invocations-data invoke-id :is-complete] is-complete))))

(rf/reg-event-fx :invocation/start-graph-loading
  (fn [{:keys [db]} [_ {:keys [invoke-id module-id agent-name]}]]
    {:db (-> db
             (assoc :current-invocation {:invoke-id invoke-id
                                         :module-id module-id
                                         :agent-name agent-name})
             (assoc-in [:invocations-data invoke-id :status] :loading))
     :dispatch [:invocation/fetch-graph-page
                {:invoke-id invoke-id
                 :module-id module-id
                 :agent-name agent-name}]}))

(rf/reg-event-fx :invocation/fetch-graph-page
  (fn [_ [_ {:keys [invoke-id module-id agent-name] :as current-invocation}]]
    (-> (rpc/call ::rpc-invocations/get-graph-page!!
                  {:invoke-id invoke-id
                   :module-id module-id
                   :agent-name agent-name})
        (.then (fn [data]
                  (rf/dispatch [:invocation/process-graph-page invoke-id data current-invocation])))
        (.catch (fn [err]
                  (rf/dispatch [:invocation/fetch-graph-error
                                invoke-id
                                (if (map? err) (or (:error err) (str err)) (str err))]))))
    {}))

(rf/reg-event-db :invocation/fetch-graph-error
  (fn [db [_ invoke-id error-info]]
    (-> db
        (assoc-in [:invocations-data invoke-id :status] :error)
        (assoc-in [:invocations-data invoke-id :error] error-info))))

(rf/reg-event-db :invocation/merge-nodes-and-complete
  (fn [db [_ invoke-id new-nodes-map root-invoke-id-from-payload is-complete]]
    (merge-nodes-and-complete-into-db db invoke-id new-nodes-map root-invoke-id-from-payload is-complete)))

(rf/reg-event-fx :invocation/process-graph-page
  (fn [{:keys [db]} [_ invoke-id page-data current-invocation]]
    (let [{:keys [nodes is-complete root-invoke-id]} page-data
          db-after-summary (apply-summary-kvps db invoke-id page-data)
          new-nodes-map (nodes->map nodes)
          db-after-graph (cond
                           (and nodes (seq new-nodes-map))
                           (merge-nodes-and-complete-into-db db-after-summary invoke-id new-nodes-map root-invoke-id is-complete)

                           (and (not (seq new-nodes-map)) (contains? page-data :is-complete))
                           (assoc-in db-after-summary [:invocations-data invoke-id :is-complete] is-complete)

                           :else db-after-summary)]
      (when-not is-complete
        (js/setTimeout
         (fn []
           (when-not (get-in @rdb/app-db [:invocations-data invoke-id :is-complete])
             (println "[POLLING-SIMPLIFIED] Polling for updates...")
             (rf/dispatch [:invocation/fetch-graph-page current-invocation])))
         1000))
      {:db db-after-graph})))

(rf/reg-event-db :invocation/cleanup
  (fn [db [_ _]]
    (-> db
        (assoc-in [:ui :changed-nodes] {})
        (assoc-in [:ui :selected-node-id] nil)
        (assoc-in [:ui :forking-mode?] false)
        (assoc-in [:ui :active-tab] :info)
        (assoc-in [:ui :hitl :responses] {}))))

(rf/reg-event-db :ui/clear-fork-state
  (fn [db _]
    (-> db
        (assoc-in [:ui :changed-nodes] {})
        (assoc-in [:ui :selected-node-id] nil)
        (assoc-in [:ui :forking-mode?] false)
        (assoc-in [:ui :active-tab] :info)
        (assoc-in [:ui :hitl :responses] {}))))

(rf/reg-event-fx :hitl/submit
  (fn [{:keys [db]} [_ {:keys [module-id agent-name invoke-id request response]}]]
    (let [rid (:invoke-id request)]
      (-> (rpc/call ::rpc-invocations/provide-human-input!!
                    {:module-id module-id
                     :agent-name agent-name
                     :invoke-id invoke-id
                     :request request
                     :response response})
          (.then (fn [_]
                   (rf/dispatch [:db/set-value [:ui :hitl :submitting rid] false])
                   (println "HITL response submitted successfully.")))
          (.catch (fn [err]
                    (rf/dispatch [:db/set-value [:ui :hitl :submitting rid] false])
                    (js/console.error "HITL submit failed" (if (map? err) (:error err) (str err))))))
      {:db (assoc-in db [:ui :hitl :submitting rid] true)})))

(rf/reg-event-fx :config/submit-change
  (fn [{:keys [db]} [_ {:keys [module-id agent-name key value on-error]}]]
    (let [state-path [:ui :config-page (keyword key)]]
      (-> (rpc/call ::rpc-config/set!!
                    {:module-id module-id :agent-name agent-name :key key :value value})
          (.then (fn [_]
                   (println "Config update success for" key)
                   (rf/dispatch [:db/set-value state-path {:submitting? false :error nil}])))
          (.catch (fn [err]
                    (let [msg (if (map? err) (or (:error err) (str err)) (str err))]
                      (js/console.error "Config update failed:" msg)
                      (rf/dispatch [:db/set-value state-path {:submitting? false :error msg}])
                      (when on-error (on-error msg))))))
      {:db (assoc-in db state-path {:submitting? true :error nil})})))

(rf/reg-event-fx :config/submit-global-change
  (fn [{:keys [db]} [_ {:keys [module-id key value on-error]}]]
    (let [state-path [:ui :global-config-page (keyword key)]]
      (-> (rpc/call ::rpc-config/set-global!!
                    {:module-id module-id :key key :value value})
          (.then (fn [_]
                   (println "Global config update success for" key)
                   (rf/dispatch [:db/set-value state-path {:submitting? false :error nil}])))
          (.catch (fn [err]
                    (let [msg (if (map? err) (or (:error err) (str err)) (str err))]
                      (js/console.error "Global config update failed:" msg)
                      (rf/dispatch [:db/set-value state-path {:submitting? false :error msg}])
                      (when on-error (on-error msg))))))
      {:db (assoc-in db state-path {:submitting? true :error nil})})))

(rf/reg-event-fx :dataset/edit-example
  (fn [_ [_ {:keys [module-id dataset-id snapshot-name example-id form-fields]}]]
    (let [input (get form-fields :input "")
          output (get form-fields :output "")
          form-id :edit-example]
      (try
        (when-not (str/blank? input) (js/JSON.parse input))
        (when-not (str/blank? output) (js/JSON.parse output))
        (-> (rpc/call ::rpc-datasets/edit-example!!
                      {:module-id module-id
                       :dataset-id dataset-id
                       :snapshot-name snapshot-name
                       :example-id example-id
                       :input input
                       :reference-output output})
            (.then (fn [_]
                     (forms/set-submitting! form-id false)
                     (rf/dispatch [:modal/hide])
                     (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                     (rf/dispatch [:re-frame.query/invalidate-tags [[:fetch-example module-id dataset-id example-id] [:dataset-examples module-id dataset-id snapshot-name]]])
                     (forms/clear-form! form-id)))
            (.catch (fn [err]
                      (forms/set-submitting! form-id false)
                      (forms/set-error! form-id (if (map? err) (or (:error err) "An unknown server error occurred.") (str err))))))
        (catch js/Error e
          (forms/set-submitting! form-id false)
          (forms/set-error! form-id (str "Invalid JSON: " (.-message e))))))
    {}))

(rf/reg-event-fx :dataset/delete-selected
  (fn [_ [_ {:keys [module-id dataset-id snapshot-name example-ids]}]]
    (-> (rpc/call ::rpc-datasets/delete-examples!!
                  {:module-id module-id
                   :dataset-id dataset-id
                   :snapshot-name snapshot-name
                   :example-ids (vec example-ids)})
        (.then (fn [_]
                 (rf/dispatch [:datasets/clear-selection {:dataset-id dataset-id}])
                 (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                 (rf/dispatch [:re-frame.query/invalidate-tags [[:dataset-examples module-id dataset-id snapshot-name]]])))
        (.catch (fn [err]
                  (js/alert (str "Failed to delete examples: " (if (map? err) (or (:error err) (str err)) (str err)))))))
    {}))

(rf/reg-event-db :stream/update
  (fn [db [_ {:keys [stream-id new-chunks reset? complete?]}]]
    (let [update-path [:streaming :buffers stream-id]]
      (if reset?
        (-> db
            (assoc-in (conj update-path :chunks) new-chunks)
            (update-in (conj update-path :reset-count) (fn [c] (inc (or c 0))))
            (assoc-in (conj update-path :complete?) complete?))
        (-> db
            (update-in (conj update-path :chunks) (fn [ch] (into (or ch []) new-chunks)))
            (assoc-in (conj update-path :complete?) complete?))))))

(rf/reg-event-db :stream/cleanup
  (fn [db [_ {:keys [stream-id]}]]
    (update-in db [:streaming :buffers] dissoc stream-id)))
