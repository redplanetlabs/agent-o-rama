(ns com.rpl.agent-o-rama.impl.ui.handlers.analytics-filters
  "Pure filter conversion helpers shared by RPC and tests (no transport)."
  (:require
   ;; Loads `extend-protocol RuleFilter` so composite filters validate (tests and RPC).
   [com.rpl.agent-o-rama.impl.analytics]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import [java.util.regex Pattern]))

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
