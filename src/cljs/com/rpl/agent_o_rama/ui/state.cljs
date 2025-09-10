(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

;; =============================================================================
;; FORM VALIDATORS (defined here to avoid circular dependencies)
;; =============================================================================

(def required
  "Validator for required fields"
  (fn [value]
    (when (str/blank? value)
      "This field is required")))

(def valid-json
  "Validator for JSON strings"
  (fn [value]
    (when-not (str/blank? value)
      (try
        (js/JSON.parse value)
        nil ; Valid JSON
        (catch js/Error e
          (str "Invalid JSON: " (.-message e)))))))

(def initial-db
  {:current-invocation {:invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :invocations-data {} ;; Keyed by invoke-id -> {:graph {:raw-nodes {} :nodes {} :edges []} :implicit-edges [] :summary ... :root-invoke-id ... :task-id ... :is-complete false}
   :invocations {:all-invokes []
                 :pagination-params nil ;; Next pagination params from server
                 :has-more? true
                 :loading? false}
   :queries {} ; New map to store all query states
   :route nil ; Current route match from reitit
   :forms {} ; Form states keyed by form-id -> {:fields {} :valid? false :submitting? false :error nil :submit-event [...]}
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :active-tab :info
        :current-route "/"
        :breadcrumbs []
        :modal {:active nil ;; nil or modal type keyword
                :data {} ;; modal-specific data
                :form {:submitting? false
                       :error nil}}
        :hitl {:responses {} ;; Keyed by invoke-id -> response text
               :submitting {}}
        :datasets {:selected-examples {}}} ;; Keyed by dataset-id -> set of example-ids 
   :sente {:connected? false
           :connection-state {}}
   :session {:user-id nil
             :preferences {}}})

(defonce app-db (atom initial-db))

;; TODO disable with shado-cljs, for performance
(add-watch app-db :console-logger
           (fn [key atom old-state new-state]
             ;; This runs on EVERY state change
             (aset js/window "db" (clj->js new-state {:keyword-fn (fn [k] (str/replace (name k) "-" "_"))}
             ))))

;; =============================================================================
;; EVENT SYSTEM
;; =============================================================================

;; Registry for event handlers
(defonce event-handlers (atom {}))

(defn reg-event
  "Register an event handler. Handler should return a Specter path (navigator)
   that will be applied to the current app-db via s/multi-transform. Handlers
   may return nil to indicate no state change is needed."
  [event-id handler-fn]
  (if (contains? @event-handlers event-id)
    (println "⚠️ Event handler already registered for event:" event-id)
    (swap! event-handlers assoc event-id handler-fn)))

(defn dispatch
  "Dispatch an event to update app-db. Event is a vector [event-id & args].
   The handler must return a Specter path navigator suitable for s/multi-transform."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (if handler
      (try
        (let [current-db @app-db
              specter-path (apply handler current-db event-args)]
          ;; Allow handlers to return nil to indicate they handled the update themselves
          (when specter-path
            (swap! app-db #(s/multi-transform specter-path %))))
        (catch :default e
          (println "💥 Error in event handler" event-id ":" e)))
      (println "⚠️ No handler registered for event:" event-id))))

;; =============================================================================
;; SUBSCRIPTIONS (REACTIVE STATE ACCESS)
;; =============================================================================

(defn use-sub
  "Subscribe to a value at the given Specter path in app-db.
   Component will re-render only when the value at that path changes."
  [specter-path]
  (let [;; Memoize the extractor function to have stable reference
        extract-value (uix/use-callback
                       (fn [db] (s/select-one specter-path db))
                       [specter-path])
        [value set-value] (uix/use-state (fn [] (extract-value @app-db)))]

    (uix/use-effect
     (fn []
       (let [watch-key (gensym "sub-")]
         (add-watch app-db watch-key
                    (fn [_ _ old-db new-db]
                      (let [old-val (extract-value old-db)
                            new-val (extract-value new-db)]
                        (when (not= old-val new-val)
                          (set-value new-val)))))
         ;; Cleanup function
         (fn []
           (remove-watch app-db watch-key))))
     [extract-value]) ; Include extract-value as dependency

    value))

;; =============================================================================
;; SELECTORS
;; =============================================================================

(defn get-unfinished-leaves
  "Find all unfinished leaf nodes for a given invoke-id.
   Returns a vector of unique [task-id node-id] pairs that can be used for pagination."
  [db invoke-id]
  (let [nodes-map (s/select-one [:invocations-data invoke-id :graph :nodes] db)]
    (->> (s/select [s/ALL ;; Use ALL to get [key value] pairs
                    (s/selected? s/LAST ;; Check the value (node-data)
                                 (s/must :node-task-id)
                                 (s/pred #(not (:finish-time-millis %))))
                    (s/view (fn [[node-id node-data]] ;; Destructure [key value]
                              [(:node-task-id node-data)
                               (or (:invoke-id node-data) ;; Use invoke-id from data
                                   node-id)]))] ;; Or the map key as fallback
                   (or nodes-map {}))
         ;; Remove duplicates
         distinct
         vec)))

;; =============================================================================
;; CORE EVENT HANDLERS
;; =============================================================================

;; UI Events - Only keep complex or toggle events
(reg-event :ui/toggle-forking-mode
           (fn [db]
             [:ui :forking-mode? (s/terminal not)]))

;; Note: Simple setters should use :db/set-value
;; Examples:
;; (dispatch [:db/set-value [:ui :selected-node-id] node-id])
;; (dispatch [:db/set-value [:ui :current-route] route])
;; (dispatch [:db/set-value [:ui :changed-nodes node-id] changes])
;; (dispatch [:db/set-value [:ui :changed-nodes] {}])

;; Note: Sente connection events should use :db/set-value
;; Examples:
;; (dispatch [:db/set-value [:sente :connection-state] new-state])
;; (dispatch [:db/set-value [:sente :connected?] connected?])

;; Current Invocation Events
(reg-event :invocation/set-current
           (fn [db {:keys [invoke-id module-id agent-name]}]
    ;; Simply set the current invocation context
    ;; Data is stored separately under invocations-data
             [:current-invocation (s/terminal-val {:invoke-id invoke-id
                                                   :module-id module-id
                                                   :agent-name agent-name})]))

(reg-event :invocation/load-graph-success
           (fn [db invoke-id graph-data]
             [:invocations-data invoke-id :graph (s/terminal-val graph-data)]))

(reg-event :invocation/load-summary-success
           (fn [db invoke-id summary-data]
             [:invocations-data invoke-id :summary (s/terminal-val summary-data)]))

(reg-event :invocation/update-node
           (fn [db invoke-id node-id node-data]
             [:invocations-data invoke-id :graph :nodes
              (s/terminal (fn [nodes]
                            (assoc (or nodes {}) node-id node-data)))]))

;; Generic state update events
;; Usage: (dispatch [:db/set-value [:some :path] value])
(reg-event :db/set-value
           (fn [db path value]
    ;; Build a Specter navigator that sets the value at the given path
             (into path [(s/terminal-val value)])))

;; Usage: (dispatch [:db/update-value [:some :path] update-fn])
(reg-event :db/update-value
           (fn [db path update-fn]
             (into path [(s/terminal update-fn)])))

;; Usage: (dispatch [:db/set-values [[:path1] v1] [[:path2 :k] v2] ...])
(reg-event :db/set-values
           (fn [db & path-value-pairs]
             (apply s/multi-path
                    (map (fn [[path value]]
                           (into path [(s/terminal-val value)]))
                         path-value-pairs))))

;; Specific complex events that do more than just setting a value
(reg-event :invocations/append
           (fn [db invokes]
             [:invocations :all-invokes (s/terminal #(concat % invokes))]))

(reg-event :invocations/set-loading
           (fn [db loading?]
             [:invocations :loading? (s/terminal-val loading?)]))

(reg-event :invocations/set-pagination
           (fn [db {:keys [pagination-params has-more?]}]
             [:invocations (s/terminal #(assoc %
                                               :pagination-params pagination-params
                                               :has-more? has-more?))]))

(reg-event :invocations/reset
           (fn [db]
             [:invocations (s/terminal-val {:all-invokes []
                                            :pagination-params nil
                                            :has-more? true
                                            :loading? false})]))

;; =============================================================================
;; GENERIC QUERY HANDLERS - For useSenteQuery hook
;; =============================================================================

(reg-event :query/fetch-start
           (fn [db {:keys [query-key]}]
             (into (into [:queries] query-key)
                   [(s/terminal (fn [current-state]
                                  (let [has-data? (some? (:data current-state))]
                                    (-> current-state
                                        (assoc :error nil
                                               :fetching? true)
                                        (cond-> (not has-data?)
                                          (assoc :status :loading))))))])))

(reg-event :query/fetch-success
           (fn [db {:keys [query-key data]}]
             (into (into [:queries] query-key)
                   [(s/terminal (fn [_]
                                  {:status :success
                                   :data data
                                   :error nil
                                   :fetching? false}))])))

(reg-event :query/fetch-error
           (fn [db {:keys [query-key error]}]
             (into (into [:queries] query-key)
                   [(s/terminal (fn [current-state]
                                  (-> current-state
                                      (assoc :error error
                                             :fetching? false)
                                      (cond-> (nil? (:data current-state))
                                        (assoc :status :error)))))])))

(reg-event :query/invalidate
           (fn [db {:keys [query-key-pattern]}]
             ;; Find all query keys that match the pattern and mark them for refetch
             ;; Supports nested query-key vectors stored under :queries as nested maps
             (let [queries-path [:queries]
                   current-queries (get-in @app-db queries-path {})
                   ;; Collect all full query-key vectors under :queries (leaf maps contain :status)
                   all-query-keys (letfn [(collect-keys [m prefix acc]
                                            (reduce-kv
                                             (fn [a k v]
                                               (let [new-prefix (conj prefix k)]
                                                 (cond
                                                   (and (map? v) (contains? v :status))
                                                   (conj a (vec new-prefix))

                                                   (map? v)
                                                   (collect-keys v new-prefix a)
                                                   :else a)))
                                             acc
                                             m))]
                                    (collect-keys current-queries [] []))
                   matching-keys (filter
                                  (fn [query-key]
                                    (cond
                                      ;; Case 1: Pattern is a keyword: match first segment
                                      (keyword? query-key-pattern)
                                      (= (first query-key) query-key-pattern)

                                      ;; Case 2: Pattern is a vector: prefix match
                                      (vector? query-key-pattern)
                                      (and (>= (count query-key) (count query-key-pattern))
                                           (= query-key-pattern (subvec query-key 0 (count query-key-pattern))))

                                      ;; Case 3: Pattern is a function (for complex logic)
                                      (fn? query-key-pattern)
                                      (query-key-pattern query-key)

                                      :else false))
                                  all-query-keys)]
               ;; Mark matching queries as stale by setting a flag (:should-refetch?)
               (when (seq matching-keys)
                 (apply s/multi-path
                        (map (fn [query-key]
                               (into (into queries-path query-key)
                                     [:should-refetch? (s/terminal-val true)]))
                             matching-keys))))))

;; =============================================================================
;; FORM STATE MANAGEMENT EVENTS
;; =============================================================================
(defn required [value] (when (str/blank? value) "This field is required"))

(defn min-length
  "Validator for minimum string length"
  [n]
  (fn [value]
    (when (and (string? value) (< (count value) n))
      (str "Must be at least " n " characters long"))))

(defn max-length
  "Validator for maximum string length"
  [n]
  (fn [value]
    (when (and (string? value) (> (count value) n))
      (str "Must be no more than " n " characters long"))))

(defn valid-json
  "Validator for JSON strings"
  [value]
  (when-not (str/blank? value)
    (try
      (js/JSON.parse value)
      nil ; Valid JSON
      (catch js/Error e
        (str "Invalid JSON: " (.-message e))))))
(defn- validate-form-fields
  "Validate fields against validators. Returns a map {:valid? boolean :errors {field-key error-str-or-nil}}"
  [fields validators]
  (let [field-keys (set (concat (keys fields) (keys validators)))
        errors (into {}
                     (for [k field-keys]
                       (let [value (get fields k "")
                             field-validators (get validators k)
                             first-error (when (seq field-validators)
                                           (some #(% value) field-validators))]
                         [k first-error])))]
    {:errors errors
     :valid? (every? nil? (vals errors))}))

(reg-event :form/update-field
           (fn [db form-id field-key value]
             [:forms form-id
              (s/terminal
               (fn [form-state]
                 (let [updated-fields (assoc (:fields form-state) field-key value)
                       form-spec (@forms/form-specs form-id)
                       current-step-key (:current-step form-state)
                       step-spec (get form-spec current-step-key form-spec)
                       validators (:validators step-spec)
                       {:keys [valid? errors]} (validate-form-fields updated-fields validators)]
                   (assoc form-state
                          :fields updated-fields
                          :field-errors errors
                          :valid? valid?))))]))

(reg-event :form/next-step
           (fn [db form-id]
             (let [form-state (get-in db [:forms form-id])
                   form-spec (@forms/form-specs form-id)
                   {:keys [valid? current-step steps]} form-state]
               (when valid?
                 (let [current-idx (.indexOf steps current-step)
                       next-step (get steps (inc current-idx))]
                   (when next-step
                     [:forms form-id :current-step (s/terminal-val next-step)]))))))

(reg-event :form/prev-step
           (fn [db form-id]
             (let [form-state (get-in db [:forms form-id])
                   {:keys [current-step steps]} form-state
                   current-idx (.indexOf steps current-step)
                   prev-step (get steps (dec current-idx))]
               (when prev-step
                 [:forms form-id :current-step (s/terminal-val prev-step)]))))

(reg-event :form/submit
           (fn [db form-id]
             (let [form-state (get-in db [:forms form-id])
                   form-spec (@forms/form-specs form-id)
                   on-submit-handler (:on-submit form-spec)]
               (when (and (:valid? form-state) on-submit-handler)
                 ;; Set submitting state and then dispatch the submission handler as an effect
                 (dispatch [:db/set-value [:forms form-id] (-> form-state
                                                               (assoc :submitting? true :error nil))])
                 ;; The on-submit handler is responsible for the actual side-effect
                 (on-submit-handler db {:form-id form-id
                                        :form-fields (:fields form-state)
                                        :props (:props form-state)})))
             nil))

(reg-event :form/clear
           (fn [db form-id]
             [:forms (s/terminal #(dissoc % form-id))]))


;; =============================================================================
;; NEW MODAL AND FORM INTEGRATION EVENTS
;; =============================================================================

(reg-event :modal/show-form
           (fn [db form-id props]
             (let [form-spec (get @forms/form-specs form-id)]
               (if-not form-spec
                 (do (js/console.error "No form spec registered for" form-id) nil)
                 (let [is-wizard? (boolean (:steps form-spec))
                       initial-step (when is-wizard? (first (:steps form-spec)))
                       step-spec (if is-wizard? (get form-spec initial-step) form-spec)
                       initial-fields-fn (:initial-fields step-spec)
                       initial-fields (if (fn? initial-fields-fn)
                                        (initial-fields-fn props)
                                        (or initial-fields-fn {}))
                       validators (:validators step-spec)
                       {:keys [valid? errors]} (validate-form-fields initial-fields validators)
                       modal-data (if is-wizard?
                                    (get-in form-spec [initial-step :modal-props] {})
                                    (:modal-props form-spec {}))]
                   ;; 1. Initialize the form state
                   (dispatch [:db/set-value [:forms form-id] {:fields initial-fields
                                                              :validators validators
                                                              :field-errors errors
                                                              :valid? valid?
                                                              :submitting? false
                                                              :error nil
                                                              :props props
                                                              :steps (:steps form-spec)
                                                              :current-step initial-step}])
                   ;; 2. Show the modal
                   (dispatch [:modal/show form-id (assoc modal-data :form-id form-id)]))))
             nil))

(reg-event :modal/show
           (fn [db modal-type modal-data]
             [:ui :modal (s/terminal-val {:active modal-type
                                          :data modal-data})]))

(reg-event :modal/hide
           (fn [db]
             [:ui :modal (s/terminal-val {:active nil
                                          :data {}})]))




;; =============================================================================
;; ROUTING EVENTS
;; =============================================================================

(reg-event :route/navigated
           (fn [db new-match]
             [:route
              (s/terminal-val
               (s/transform
                [:path-params s/MAP-VALS]
                common/url-decode
                new-match))]))

 ;; =============================================================================
;; DEBUGGING HELPERS
;; =============================================================================

(defn get-db [] @app-db)

(defn reset-db!
  "Reset app-db to initial state. Useful for development."
  []
  (reset! app-db initial-db))

(defn debug-state
  "Print current app-db state to console. Optionally filter by path."
  ([]
   (js/console.log "Current app-db:" (clj->js @app-db)))
  ([specter-path]
   (js/console.log "Value at path" specter-path ":"
                   (clj->js (s/select-one specter-path @app-db)))))

 ;; Dataset selection event handlers
(reg-event :datasets/toggle-selection
           (fn [db {:keys [dataset-id example-id]}]
             [:ui :datasets :selected-examples dataset-id
              (s/terminal #(if (contains? % example-id)
                             (disj % example-id)
                             (conj (or % #{}) example-id)))]))

(reg-event :datasets/toggle-all-selection
           (fn [db {:keys [dataset-id example-ids-on-page select-all?]}]
             [:ui :datasets :selected-examples dataset-id
              (s/terminal #(if select-all?
                             (into (or % #{}) example-ids-on-page)
                             (apply disj (or % #{}) example-ids-on-page)))]))

(reg-event :datasets/clear-selection
           (fn [db {:keys [dataset-id]}]
             [:ui :datasets :selected-examples (s/terminal #(dissoc % dataset-id))]))
