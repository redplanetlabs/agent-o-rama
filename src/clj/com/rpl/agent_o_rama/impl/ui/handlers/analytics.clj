(ns com.rpl.agent-o-rama.impl.ui.handlers.analytics
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.ui.sente :as sente])
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:import
   [java.util.regex Pattern]))

(defn comparator-spec->ui
  "Convert ComparatorSpec record to UI map."
  [spec]
  (when spec
    {:comparator (:comparator spec)
     :value (:value spec)}))

(defn filter->ui
  "Convert filter record to UI-compatible map with explicit type field."
  [filter-obj]
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

      :else
      nil)))

(defmethod sente/-event-msg-handler :analytics/fetch-rules
  [{:keys [manager decoded-agent-name names-only? filter-by-action]} uid]
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
                   filtered-rules))))))

(defn ui-comparator-spec->comparator-spec
  "Convert a UI comparator spec map to a ComparatorSpec record."
  [{:keys [comparator value]}]
  (aor-types/->valid-ComparatorSpec comparator value))

(declare ui-filter->filter)

(defn ui-filter->filter
  "Convert a UI filter map to the appropriate typed filter record."
  [{:keys [type] :as filter-map}]
  (case type
    :error
    (aor-types/->valid-ErrorFilter)

    :latency
    (aor-types/->valid-LatencyFilter
     (ui-comparator-spec->comparator-spec filter-map))

    :feedback
    (aor-types/->valid-FeedbackFilter
     (:rule-name filter-map)
     (:feedback-key filter-map)
     (ui-comparator-spec->comparator-spec (:comparator-spec filter-map)))

    :input-match
    (aor-types/->valid-InputMatchFilter
     (:json-path filter-map)
     (Pattern/compile (:regex filter-map)))

    :output-match
    (aor-types/->valid-OutputMatchFilter
     (:json-path filter-map)
     (Pattern/compile (:regex filter-map)))

    :token-count
    (aor-types/->valid-TokenCountFilter
     (:token-type filter-map)
     (ui-comparator-spec->comparator-spec (:comparator-spec filter-map)))

    :and
    (aor-types/->valid-AndFilter
     (mapv ui-filter->filter (:filters filter-map)))

    :or
    (aor-types/->valid-OrFilter
     (mapv ui-filter->filter (:filters filter-map)))

    :not
    (aor-types/->valid-NotFilter
     (ui-filter->filter (:filter filter-map)))

    (throw (ex-info "Unknown filter type" {:type type}))))

(defn convert-ui-filter
  "Convert the top-level UI filter structure.
  The UI wraps all filters in an implicit AND filter."
  [{:keys [type filters] :as filter-structure}]
  (if (and (= type :and) (seq filters))
    (aor-types/->valid-AndFilter
     (mapv ui-filter->filter filters))
    (ui-filter->filter filter-structure)))

(defmethod sente/-event-msg-handler :analytics/add-rule
  [{:keys [manager decoded-agent-name rule-name rule-spec]} uid]
  (when manager
    (let [{:keys [global-actions-depot]} (aor-types/underlying-objects manager)
          converted-filter (convert-ui-filter (:filter rule-spec))
          converted-rule-spec (-> rule-spec
                                  (update :sampling-rate double)
                                  (assoc :filter converted-filter))]
      (try
        (ana/add-rule! global-actions-depot
                       rule-name
                       decoded-agent-name
                       converted-rule-spec)
        (catch Exception e
          (cljlogging/error
           e "Failed to add rule" {:agent-name decoded-agent-name})
          (throw e)))
      {:status :ok})))

(defmethod sente/-event-msg-handler :analytics/delete-rule
  [{:keys [manager decoded-agent-name rule-name]} uid]
  (when manager
    (let [{:keys [global-actions-depot]} (aor-types/underlying-objects manager)]
      (ana/delete-rule! global-actions-depot
                        decoded-agent-name
                        rule-name)
      {:status :ok})))

(defmethod sente/-event-msg-handler :analytics/all-action-builders
  [{:keys [manager]} uid]
  (when manager
    (let [all-action-builders-query (:all-action-builders-query
                                     (aor-types/underlying-objects manager))
          result (foreign-invoke-query all-action-builders-query)]
      result)))

(defmethod sente/-event-msg-handler :analytics/fetch-action-log
  [{:keys [manager decoded-agent-name rule-name page-size pagination-params]} uid]
  (when manager
    (let [agent-client (aor/agent-client manager decoded-agent-name)
          action-log-query (:action-log-query
                            (aor-types/underlying-objects agent-client))
          result (foreign-invoke-query action-log-query
                                       rule-name
                                       (or page-size 50)
                                       pagination-params)]
      result)))
