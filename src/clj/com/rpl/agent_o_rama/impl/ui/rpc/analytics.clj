(ns com.rpl.agent-o-rama.impl.ui.rpc.analytics
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:import
   [java.util.regex Pattern]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn comparator-spec->ui [spec]
  (when spec
    {:comparator (:comparator spec)
     :value (:value spec)}))

(defn filter->ui [filter-obj]
  (when filter-obj
    (cond
      (instance? com.rpl.agent_o_rama.impl.types.ErrorFilter filter-obj)
      {:type :error}
      (instance? com.rpl.agent_o_rama.impl.types.LatencyFilter filter-obj)
      {:type :latency
       :comparator-spec (comparator-spec->ui (:comparator-spec filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.FeedbackFilter filter-obj)
      {:type :feedback
       :rule-name (:rule-name filter-obj)
       :feedback-key (:feedback-key filter-obj)
       :comparator-spec (comparator-spec->ui (:comparator-spec filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.InputMatchFilter filter-obj)
      {:type :input-match
       :json-path (:json-path filter-obj)
       :regex (str (:regex filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.OutputMatchFilter filter-obj)
      {:type :output-match
       :json-path (:json-path filter-obj)
       :regex (str (:regex filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.TokenCountFilter filter-obj)
      {:type :token-count
       :token-type (:type filter-obj)
       :comparator-spec (comparator-spec->ui (:comparator-spec filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.AndFilter filter-obj)
      {:type :and
       :filters (mapv filter->ui (:filters filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.OrFilter filter-obj)
      {:type :or
       :filters (mapv filter->ui (:filters filter-obj))}
      (instance? com.rpl.agent_o_rama.impl.types.NotFilter filter-obj)
      {:type :not
       :filter (filter->ui (:filter filter-obj))}
      :else nil)))

(defn fetch-rules!!
  [system {:keys [module-id agent-name names-only? filter-by-action]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)]
    (when manager
      (let [agent-client (aor/agent-client manager decoded-agent-name)
            agent-rules-pstate (:agent-rules-pstate
                                (aor-types/underlying-objects agent-client))
            rules (ana/fetch-agent-rules agent-rules-pstate)
            filtered-rules (if filter-by-action
                             (into {}
                                   (filter (fn [[_rule-name rule-info]]
                                             (= filter-by-action
                                                (get-in rule-info [:definition :action-name])))
                                           rules))
                             rules)]
        (if names-only?
          (vec (keys filtered-rules))
          (into {}
                (map (fn [[rule-name rule-info]]
                       (let [definition (:definition rule-info)]
                         [rule-name
                          (-> definition
                              (dissoc :node-invoke)
                              (select-keys
                               [:name :agent-name :node-name :action-name
                                :action-params :filter :sampling-rate
                                :start-time-millis :status-filter])
                              (update :filter filter->ui))]))
                     filtered-rules)))))))

(defn all-action-builders!!
  [system {:keys [module-id]}]
  (let [manager (get-manager system module-id)]
    (when manager
      (let [all-action-builders-query (:all-action-builders-query
                                       (aor-types/underlying-objects manager))]
        (foreign-invoke-query all-action-builders-query)))))

(defn fetch-action-log!!
  [system {:keys [module-id agent-name rule-name page-size pagination-params]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)]
    (when manager
      (let [agent-client (aor/agent-client manager decoded-agent-name)
            action-log-query (:action-log-query
                              (aor-types/underlying-objects agent-client))
            result (foreign-invoke-query action-log-query
                                         rule-name
                                         (or page-size 50)
                                         pagination-params)]
        result))))

(defn search-metadata!!
  [system {:keys [module-id agent-name search-string]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)]
    (when manager
      (let [agent-client (aor/agent-client manager decoded-agent-name)
            {:keys [search-metadata-query]} (aor-types/underlying-objects agent-client)]
        (when-not search-metadata-query
          (throw (ex-info "search-metadata-query missing" {:agent decoded-agent-name})))
        (foreign-invoke-query search-metadata-query (or search-string "") 200 nil)))))

(defn fetch-telemetry!!
  [system {:keys [module-id agent-name granularity metric-id start-time-millis end-time-millis metrics-set metadata-key]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)
        agent-client (aor/agent-client manager decoded-agent-name)
        {:keys [telemetry-pstate]} (aor-types/underlying-objects agent-client)]
    (ana/select-telemetry telemetry-pstate
                          decoded-agent-name
                          granularity
                          metric-id
                          start-time-millis
                          end-time-millis
                          (vec metrics-set)
                          metadata-key)))

(defn fetch-all-metrics!!
  [system {:keys [module-id agent-name]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)
        agent-client (aor/agent-client manager decoded-agent-name)
        {:keys [all-agent-metrics-query]} (aor-types/underlying-objects agent-client)]
    (foreign-invoke-query all-agent-metrics-query)))

;; =============================================================================
;; FILTER CONVERSION HELPERS (for mutations)
;; =============================================================================

(defn- ui-comparator-spec->comparator-spec [{:keys [comparator value]}]
  (aor-types/->valid-ComparatorSpec comparator value))

(declare ui-filter->filter)

(defn- ui-filter->filter [{:keys [type] :as filter-map}]
  (case type
    :error (aor-types/->valid-ErrorFilter)
    :latency (aor-types/->valid-LatencyFilter (ui-comparator-spec->comparator-spec filter-map))
    :feedback (aor-types/->valid-FeedbackFilter
               (:rule-name filter-map)
               (:feedback-key filter-map)
               (ui-comparator-spec->comparator-spec (:comparator-spec filter-map)))
    :input-match (aor-types/->valid-InputMatchFilter
                  (:json-path filter-map)
                  (Pattern/compile (:regex filter-map)))
    :output-match (aor-types/->valid-OutputMatchFilter
                   (:json-path filter-map)
                   (Pattern/compile (:regex filter-map)))
    :token-count (aor-types/->valid-TokenCountFilter
                  (:token-type filter-map)
                  (ui-comparator-spec->comparator-spec (:comparator-spec filter-map)))
    :and (aor-types/->valid-AndFilter (mapv ui-filter->filter (:filters filter-map)))
    :or (aor-types/->valid-OrFilter (mapv ui-filter->filter (:filters filter-map)))
    :not (aor-types/->valid-NotFilter (ui-filter->filter (:filter filter-map)))
    (throw (ex-info "Unknown filter type" {:type type}))))

(defn- convert-ui-filter [{:keys [type filters] :as filter-structure}]
  (if (and (= type :and) (seq filters))
    (aor-types/->valid-AndFilter (mapv ui-filter->filter filters))
    (ui-filter->filter filter-structure)))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn add-rule!!
  [system {:keys [module-id agent-name rule-name rule-spec]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)]
    (when manager
      (let [{:keys [global-actions-depot]} (aor-types/underlying-objects manager)
            converted-filter (convert-ui-filter (:filter rule-spec))
            converted-rule-spec (-> rule-spec
                                    (update :sampling-rate double)
                                    (assoc :filter converted-filter))]
        (ana/add-rule! global-actions-depot
                       rule-name
                       decoded-agent-name
                       converted-rule-spec)
        {:status :ok}))))

(defn delete-rule!!
  [system {:keys [module-id agent-name rule-name]}]
  (let [manager (get-manager system module-id)
        decoded-agent-name (common/url-decode agent-name)]
    (when manager
      (let [{:keys [global-actions-depot]} (aor-types/underlying-objects manager)]
        (ana/delete-rule! global-actions-depot decoded-agent-name rule-name)
        {:status :ok}))))
