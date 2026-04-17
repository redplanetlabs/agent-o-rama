(ns com.rpl.agent-o-rama.ui.re-frame
  (:require
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.specter :as s]
   [re-frame.core :as rf]
   [re-frame.query :as rfq]))

(def ^:private rpc-fx-key ::rpc)

(rf/reg-fx
 rpc-fx-key
 (fn [{:keys [request on-success on-failure]}]
   (-> (rpc/call (:rpc/id request)
                 (:payload request))
       (.then (fn [data]
                (rf/dispatch (conj on-success data))))
       (.catch (fn [error]
                 (rf/dispatch (conj on-failure error)))))))

(rfq/set-default-effect-fn!
 (fn [request on-success on-failure]
   {rpc-fx-key {:request request
                :on-success on-success
                :on-failure on-failure}}))

;; =============================================================================
;; Default app-db (single source of truth for re-frame)
;; =============================================================================

(def default-app-db
  {:current-invocation {:invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :invocations-data {}
   :invocations {:all-invokes []
                 :pagination-params nil
                 :has-more? true
                 :loading? false}
   :queries {}
   :route nil
   :forms {}
   :invocations-filters {}
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :active-tab :info
        :current-route "/"
        :modal {:active nil
                :data {}
                :form {:submitting? false
                       :error nil}}
        :hitl {:responses {}
               :submitting {}}
        :datasets {:selected-examples {}
                   :selected-snapshot-per-dataset {}}
        :rules {:refetch-trigger {}}
        :node-details {:active-tab :info}}})

;; =============================================================================
;; Subscriptions
;; =============================================================================

(rf/reg-sub ::aor-global-modal
  (fn [db _]
    (get-in db [:ui :modal] {:active nil :data {} :form {}})))

(rf/reg-sub :forms/all
  (fn [db _]
    (:forms db {})))

(rf/reg-sub ::get-in
  (fn [db [_ path]]
    (get-in db path)))

;; =============================================================================
;; Generic path updates (UUID-safe keys in path vectors)
;; =============================================================================

(rf/reg-event-db :db/set-value
  (fn [db [_ path value]]
    (assoc-in db path value)))

(rf/reg-event-db :db/update-value
  (fn [db [_ path f]]
    (update-in db path f)))

(rf/reg-event-db :db/set-values
  (fn [db [_ & path-value-pairs]]
    (reduce (fn [d [p v]] (assoc-in d p v))
            db
            path-value-pairs)))

(rf/reg-event-db :ui/toggle-forking-mode
  (fn [db _]
    (update-in db [:ui :forking-mode?] not)))

(rf/reg-event-db :invocation/update-node
  (fn [db [_ invoke-id node-id node-data]]
    (update-in db [:invocations-data invoke-id :graph :nodes]
               (fn [nodes]
                 (assoc (or nodes {}) node-id node-data)))))

(rf/reg-event-db :form/set-rule-scope-type
  (fn [db [_ form-id new-type]]
    (assoc-in db [:forms form-id :node-name]
              (if (= new-type :agent)
                nil
                ""))))

(rf/reg-event-db :route/navigated
  (fn [db [_ new-match]]
    (assoc db :route
           (s/transform
            [:path-params s/MAP-VALS]
            (comp common/coerce-uuid common/url-decode)
            new-match))))

;; --- Query cache (legacy [:queries ...] shape) -----------------------------

(defn- collect-query-keys
  "All query-key vectors under :queries (leaves contain :status)."
  [m prefix acc]
  (reduce-kv
   (fn [a k v]
     (let [new-prefix (conj prefix k)]
       (cond
         (and (map? v) (contains? v :status))
         (conj a (vec new-prefix))

         (map? v)
         (collect-query-keys v new-prefix a)

         :else a)))
   acc
   m))

(defn- query-key-matches-pattern? [query-key-pattern query-key]
  (cond
    (keyword? query-key-pattern)
    (= (first query-key) query-key-pattern)

    (vector? query-key-pattern)
    (and (>= (count query-key) (count query-key-pattern))
         (= query-key-pattern (subvec query-key 0 (count query-key-pattern))))

    (fn? query-key-pattern)
    (query-key-pattern query-key)

    :else false))

(rf/reg-event-db :query/fetch-start
  (fn [db [_ {:keys [query-key]}]]
    (let [path (into [:queries] query-key)
          current-state (get-in db path)]
      (assoc-in db path
                (let [has-data? (some? (:data current-state))]
                  (-> current-state
                      (assoc :error nil
                             :fetching? true)
                      (cond-> (not has-data?)
                        (assoc :status :loading))))))))

(rf/reg-event-db :query/fetch-success
  (fn [db [_ {:keys [query-key data]}]]
    (assoc-in db (into [:queries] query-key)
              {:status :success
               :data data
               :error nil
               :fetching? false})))

(rf/reg-event-db :query/fetch-error
  (fn [db [_ {:keys [query-key error]}]]
    (update-in db (into [:queries] query-key)
               (fn [current-state]
                 (-> current-state
                     (assoc :error error
                            :fetching? false)
                     (cond-> (nil? (:data current-state))
                       (assoc :status :error)))))))

(rf/reg-event-db :query/invalidate
  (fn [db [_ {:keys [query-key-pattern]}]]
    (let [all-keys (collect-query-keys (:queries db) [] [])
          matching-keys (filter #(query-key-matches-pattern? query-key-pattern %) all-keys)]
      (reduce (fn [d query-key]
                (assoc-in d (conj (into [:queries] query-key) :should-refetch?) true))
              db
              matching-keys))))

(rf/reg-event-fx :query/invalidate-bridge
  (fn [_ [_ invalidation-map]]
    {:dispatch [:query/invalidate invalidation-map]}))

(rf/reg-event-db :datasets/clear-selection
  (fn [db [_ {:keys [dataset-id]}]]
    (update-in db [:ui :datasets :selected-examples] dissoc dataset-id)))

(rf/reg-event-db :datasets/set-selected-snapshot
  (fn [db [_ {:keys [dataset-id snapshot-name]}]]
    (assoc-in db [:ui :datasets :selected-snapshot-per-dataset dataset-id] snapshot-name)))

(defn invalidate!
  "Invalidate legacy [:queries] entries and rfq tags."
  [{:keys [query-key-pattern rfq-tags]}]
  (when query-key-pattern
    (rf/dispatch [:query/invalidate {:query-key-pattern query-key-pattern}]))
  (when (seq rfq-tags)
    (rf/dispatch [:re-frame.query/invalidate-tags rfq-tags])))
