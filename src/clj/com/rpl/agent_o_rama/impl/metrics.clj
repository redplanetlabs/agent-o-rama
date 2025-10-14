(ns com.rpl.agent-o-rama.impl.metrics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]))


(def ALL-METRICS {})

(aor-types/defaorrecord MetricDefinition
  [id :- [clojure.lang.Keyword]
   target :- (s/enum :root :nodes)
   status-filter :- STATUS-FILTER-SCHEMA
   filter :- (s/protocol RuleFilter)
   ;; (data map) -> {:type <:numeric, :categorical>, :values <values>})
   ;;  - values for :categorical is map of category string -> count
   ;;  - values for :numeric is list of numbers
   value-fn :- clojure.lang.IFn])

;; TODO: <<<<>>>> for numeric, needs to know what to display
;;  - it should be part of UI definition for each ID
;;    - for evals, it can just be fixed display type for numeric or catgegorical
;;      - or can be chosen dynamically by user somehow, or configured on the online eval rule?
;;        - would want that editable
(defmacro defmetric
  [name info-map]
  `(let [info-map# (merge {:status-filter :all :filter (->AndFilter [])} ~info-map)
         metric#   (->valid-MetricDefinition
                    (:id info-map#)
                    (:target info-map#)
                    (:status-filter info-map#)
                    (:filter info-map#)
                    (:value-fn info-map#))]
     (alter-var-root #'ALL-METRICS assoc (:id info-map#) metric#)
     (def ~name metric#)))

;; TODO: <<<<>>>>
;;  - success/failure should also be an implicit metadata selection
;;    - "aor/status"


(defmetric
 AgentInvokeCount
 {:id       [:agent :invoke-count]
  :target   :root
  :value-fn
  (fn [data-map]
    {:type   :numeric
     :values [1]})})


(defn run-success?
  [{:keys [run-type result finish-time-millis] :as _data-map}]
  (if (= :agent run-type)
    (not (:failure? result))
    (some? finish-time-millis)))

(aor-types/defmetric
 AgentSuccessRate
 {:id       [:agent :success-rate]
  :target   :root
  :value-fn
  (fn [data-map]
    {:type   :numeric
     :values [(if (run-success? data-map) 1 0)]})})

(aor-types/defmetric
 AgentLatency
 {:id       [:agent :latency]
  :target   :root
  :value-fn
  (fn [{:keys [start-time-millis finish-time-millis]}]
    {:type   :numeric
     :values (if (and start-time-millis finish-time-millis)
               [(- finish-time-millis start-time-millis)])})})


; - LLM call counts (look at nested ops on $$nodes)
;   - numeric count of nested-ops with :model-call
;     - what about having nested-op type counts chart (could be separate one)
; - input/output/total token counts
;   - look at stats on root
;   - separate chart, or dimensions?
;     - separate chart if nothing else needs dimensions
; - LLM success rate
;   - one nodes
;   - categorical
;   - maybe category results should be category -> count
;     - this is success-models -> count, failure-models -> count
;     - and empty map for no update
; - LLM latency
;   - numeric, but multiple data points in one piece of data
;     - so numeric should be a list of numbers
; - trace latency
;   - on root
;   - numeric
; - tokens / second
;   - this is just view of "total tokens"
;   - but how to get rate?
;     - it's just the total count
;   - maybe category charts can have "rate" switch to show category / second
; - LLM calls / trace
;   - numeric on root
;   - this is just nested-op stats on trace stats
; - streaming metrics:
;   - agent time to first token
;     - on root, numeric
;   - LLM time to first token
;     - on nodes, numeric
; - tools:
;   - run count by tool name
;   - error rate by tool name
;   - latency by tool name
;   - top 5 for each
; - feedback graphs
;   - will work differently with human feedback
