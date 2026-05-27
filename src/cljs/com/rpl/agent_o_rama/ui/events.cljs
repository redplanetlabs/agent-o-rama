(ns com.rpl.agent-o-rama.ui.events
  (:require [com.rpl.agent-o-rama.ui.rpc :as rpc]
            [com.rpl.agent-o-rama.ui.forms :as forms]
            [com.rpl.agent-o-rama.ui.invocations.subs :as inv-subs]
            [com.rpl.agent-o-rama.ui.invocations.graph-node :as graph-node]
            [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]
            [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
            [com.rpl.agent-o-rama.impl.ui.rpc.config :as rpc-config]
            [re-frame.core :as rf]
            [re-frame.db :as rdb]
            [clojure.string :as str]))

(def ^:private poll-timeout-ids (atom {}))

(defn- read-local-storage [key default]
  (try
    (let [item (js/localStorage.getItem key)]
      (if (some? item) (js/JSON.parse item) default))
    (catch js/Error _ default)))

(defn- write-local-storage [key value]
  (try
    (js/localStorage.setItem key (js/JSON.stringify (clj->js value)))
    (catch js/Error e
      (.error js/console "Error saving to localStorage:" e))))

(rf/reg-fx :invocation/poll-schedule
           (fn [{:keys [invoke-id delay-ms dispatch-event]}]
             (when-let [old-id (get @poll-timeout-ids invoke-id)]
               (js/clearTimeout old-id))
             (let [timeout-id (js/setTimeout #(rf/dispatch dispatch-event) delay-ms)]
               (swap! poll-timeout-ids assoc invoke-id timeout-id))))

(rf/reg-fx :invocation/poll-cancel
           (fn [invoke-id]
             (when-let [timeout-id (get @poll-timeout-ids invoke-id)]
               (js/clearTimeout timeout-id)
               (swap! poll-timeout-ids dissoc invoke-id))))

(rf/reg-fx :invocation/persist-ui-preference
           (fn [{:keys [storage-key value]}]
             (write-local-storage storage-key value)))

(defn- invoke-data-key [invoke-id]
  (str invoke-id))

(defn- page-data-get
  [page-data k]
  (or (get page-data k) (get page-data (name k))))

(defn invocation-graph-received?
  "True once at least one graph-page payload has been merged into :graph :raw-nodes."
  [db invoke-id]
  (some? (get-in db [:invocations-data (invoke-data-key invoke-id) :graph :raw-nodes])))

(defn graph-has-in-progress-nodes?
  [db invoke-id]
  (some (fn [node-data]
          (and (:start-time-millis node-data)
               (not (:finish-time-millis node-data))))
        (vals (get-in db [:invocations-data (invoke-data-key invoke-id) :graph :nodes] {}))))

(defn should-schedule-poll?
  [db invoke-id page-is-complete]
  (or (not (invocation-graph-received? db invoke-id))
      (not page-is-complete)
      (graph-has-in-progress-nodes? db invoke-id)))

(defn- init-invocation-ui-from-storage [db invoke-id]
  (let [ui-path (inv-subs/invocation-ui-path invoke-id)
        trace-mode (read-local-storage "invocation-trace-view-mode" "graph")
        sidebar-width (read-local-storage "graph-sidebar-width" 320)]
    (update-in db ui-path merge {:trace-view-mode trace-mode
                                 :sidebar-width sidebar-width})))

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
  (let [root-key (graph-node/resolve-graph-key raw-nodes root-invoke-id)]
    (if (or (empty? raw-nodes) (not root-key))
      {:nodes {} :edges [] :implicit-edges []}
      (loop [to-visit       #{root-key}
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

              (let [node-data (graph-node/graph-node-data raw-nodes current-id)
                    emitted-ids (set (map :invoke-id (:emits node-data)))
                    node-name (:node node-data)]

                (if-not node-name
                  (let [child-keys (keep #(graph-node/resolve-graph-key raw-nodes %) emitted-ids)
                        drawable-child-keys (filter #(-> (graph-node/graph-node-data raw-nodes %)
                                                         :node)
                                                    child-keys)
                        bridge-edges (for [child-id drawable-child-keys]
                                       {:id     (str "real-" current-id "-" child-id)
                                        :source (str current-id)
                                        :target (str child-id)})]
                    (recur (into remaining-to-visit child-keys)
                           drawable-nodes
                           (into real-edges bridge-edges)
                           implicit-edges
                           (conj visited current-id)))

                  (let [static-info (get-in historical-graph [:node-map node-name])
                        drawable-children (keep (fn [child-id]
                                                  (when-let [child-key (graph-node/resolve-graph-key raw-nodes child-id)]
                                                    (when (:node (graph-node/graph-node-data raw-nodes child-key))
                                                      child-key)))
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
                                agg-node-invoke-id (:agg-invoke-id node-data)
                                agg-key (when agg-node-invoke-id
                                          (graph-node/resolve-graph-key raw-nodes agg-node-invoke-id))]
                            (reduce
                             (fn [acc out-node-name]
                               (let [is-agg-node?  (= :agg-node
                                                      (get-in historical-graph
                                                              [:node-map out-node-name :node-type]))
                                     agg-node-data (when agg-key
                                                     (graph-node/graph-node-data raw-nodes agg-key))]
                                 (if (and is-agg-node?
                                          agg-key
                                          agg-node-data
                                          (:node agg-node-data)
                                          (not (contains? visited agg-key))
                                          (not (contains? emitted-ids agg-node-invoke-id)))
                                   {:targets (conj (:targets acc) agg-key)
                                    :edges   (conj (:edges acc)
                                                   {:id        (str "implicit-" current-id
                                                                    "-" agg-key)
                                                    :source    (str current-id)
                                                    :target    (str agg-key)
                                                    :implicit? true})}
                                   acc)))
                             {:targets [] :edges []}
                             (or potential-outputs []))))]

                    (recur (into remaining-to-visit (concat drawable-children implicit-targets))
                           (assoc drawable-nodes current-id node-data)
                           (into real-edges new-real-edges)
                           (into implicit-edges implicit-edge-list)
                           (conj visited current-id))))))))))))

(defn- nodes->map [nodes]
  (let [m (cond
            (nil? nodes) {}
            (map? nodes) nodes
            (sequential? nodes) (into {} (keep (fn [n] (when-let [id (:invoke-id n)] [id n])) nodes))
            (and (object? nodes) (not (array? nodes)))
            (js->clj nodes :keywordize-keys false)
            :else {})]
    (or (graph-node/normalize-raw-nodes-map m) {})))

(defn- apply-summary-kvps [db invoke-id page-data]
  (let [id (invoke-data-key invoke-id)
        summary (page-data-get page-data :summary)
        task-id (page-data-get page-data :task-id)
        forks (page-data-get page-data :forks)
        fork-of (page-data-get page-data :fork-of)
        historical-graph (page-data-get page-data :historical-graph)
        root-invoke-id (page-data-get page-data :root-invoke-id)]
    (if-not summary
      db
      (let [kvps (cond-> [[[:invocations-data id :summary] summary]
                          [[:invocations-data id :task-id] task-id]
                          [[:invocations-data id :forks] forks]
                          [[:invocations-data id :fork-of] fork-of]]
                   (some? historical-graph)
                   (conj [[:invocations-data id :historical-graph] historical-graph])

                   (some? root-invoke-id)
                   (conj [[:invocations-data id :root-invoke-id] root-invoke-id]))]
        (reduce (fn [d [p v]] (assoc-in d p v)) db kvps)))))

(defn- merge-nodes-and-complete-into-db [db invoke-id new-nodes-map root-invoke-id-from-payload is-complete]
  (let [id (invoke-data-key invoke-id)
        historical-graph (get-in db [:invocations-data id :historical-graph])
        current-raw-nodes (get-in db [:invocations-data id :graph :raw-nodes])
        merged-raw-nodes (merge current-raw-nodes new-nodes-map)
        root-invoke-id (or root-invoke-id-from-payload
                           (get-in db [:invocations-data id :root-invoke-id]))
        {:keys [nodes edges implicit-edges]}
        (build-drawable-graph merged-raw-nodes root-invoke-id historical-graph)]
    (-> db
        (assoc-in [:invocations-data id :graph :raw-nodes] merged-raw-nodes)
        (assoc-in [:invocations-data id :graph :nodes] nodes)
        (assoc-in [:invocations-data id :graph :edges] edges)
        (assoc-in [:invocations-data id :implicit-edges] implicit-edges)
        (assoc-in [:invocations-data id :is-complete] is-complete)
        (assoc-in [:invocations-data id :status] :success))))

(rf/reg-event-fx :invocation/start-graph-loading
                 (fn [{:keys [db]} [_ {:keys [invoke-id module-id agent-name]}]]
                   {:db (-> db
                            (assoc :current-invocation {:invoke-id invoke-id
                                                        :module-id module-id
                                                        :agent-name agent-name})
                            (assoc-in [:invocations-data (invoke-data-key invoke-id) :status] :loading)
                            (init-invocation-ui-from-storage invoke-id))
                    :fx [[:invocation/poll-cancel invoke-id]]
                    :dispatch [:invocation/fetch-graph-page
                               {:invoke-id invoke-id
                                :module-id module-id
                                :agent-name agent-name}]}))

(rf/reg-event-fx :invocation/set-trace-view-mode
                 (fn [{:keys [db]} [_ invoke-id mode]]
                   {:db (assoc-in db (conj (inv-subs/invocation-ui-path invoke-id) :trace-view-mode) mode)
                    :fx [[:invocation/persist-ui-preference {:storage-key "invocation-trace-view-mode"}
                          :value mode]]}))

(rf/reg-event-fx :invocation/set-sidebar-width
                 (fn [{:keys [db]} [_ invoke-id width]]
                   {:db (assoc-in db (conj (inv-subs/invocation-ui-path invoke-id) :sidebar-width) width)
                    :fx [[:invocation/persist-ui-preference {:storage-key "graph-sidebar-width"}
                          :value width]]}))

(rf/reg-event-db :invocation/select-node
                 (fn [db [_ invoke-id node-id]]
                   (assoc-in db (conj (inv-subs/invocation-ui-path invoke-id) :selected-node-id) node-id)))

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
                       (assoc-in [:invocations-data (invoke-data-key invoke-id) :status] :error)
                       (assoc-in [:invocations-data (invoke-data-key invoke-id) :error] error-info))))

(rf/reg-event-db :invocation/merge-nodes-and-complete
                 (fn [db [_ invoke-id new-nodes-map root-invoke-id-from-payload is-complete]]
                   (merge-nodes-and-complete-into-db db invoke-id new-nodes-map root-invoke-id-from-payload is-complete)))

(rf/reg-event-fx :invocation/process-graph-page
                 (fn [{:keys [db]} [_ invoke-id page-data current-invocation]]
                   (let [id (invoke-data-key invoke-id)
                         nodes (page-data-get page-data :nodes)
                         is-complete (page-data-get page-data :is-complete)
                         root-invoke-id (page-data-get page-data :root-invoke-id)
                         db-after-summary (apply-summary-kvps db invoke-id page-data)
                         new-nodes-map (nodes->map nodes)
                         db-after-graph (cond
                           (seq new-nodes-map)
                           (merge-nodes-and-complete-into-db db-after-summary invoke-id new-nodes-map root-invoke-id is-complete)

                           (and (not (seq new-nodes-map)) (some? is-complete))
                           (assoc-in db-after-summary [:invocations-data id :is-complete] is-complete)

                           :else db-after-summary)
                         continue-poll? (should-schedule-poll? db-after-graph invoke-id is-complete)]
                     (when continue-poll?
                       (println "[POLLING] Scheduling poll for" invoke-id
                                (if is-complete "(drain: in-progress nodes remain)" "")))
                     {:db db-after-graph
                      :fx (cond-> []
                            continue-poll?
                            (conj [:invocation/poll-schedule
                                   {:invoke-id invoke-id
                                    :delay-ms 1000
                                    :dispatch-event [:invocation/poll-tick current-invocation]}]))})))

(rf/reg-event-fx :invocation/poll-tick
                 (fn [{:keys [db]} [_ current-invocation]]
                   (let [{:keys [invoke-id]} current-invocation
                         still-active? (= invoke-id (get-in db [:current-invocation :invoke-id]))]
                     (if still-active?
                       {:dispatch [:invocation/fetch-graph-page current-invocation]}
                       {:fx [[:invocation/poll-cancel invoke-id]]}))))

(rf/reg-event-fx :invocation/cleanup
                 (fn [{:keys [db]} [_ {:keys [invoke-id]}]]
                   {:db (-> db
                            (update-in [:ui :invocations] dissoc invoke-id)
                            (update-in [:invocations-data] dissoc (invoke-data-key invoke-id))
                            (assoc-in [:ui :active-tab] :info)
                            (assoc-in [:ui :hitl :responses] {}))
                    :fx [[:invocation/poll-cancel invoke-id]]}))

(rf/reg-event-db :ui/clear-fork-state
                 (fn [db [_ invoke-id]]
                   (let [ui-path (inv-subs/invocation-ui-path invoke-id)]
                     (-> db
                         (assoc-in (conj ui-path :changed-nodes) {})
                         (assoc-in (conj ui-path :selected-node-id) nil)
                         (assoc-in (conj ui-path :forking-mode?) false)
                         (assoc-in [:ui :active-tab] :info)
                         (assoc-in [:ui :hitl :responses] {})))))

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
