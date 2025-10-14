(ns com.rpl.agent-o-rama.impl.metrics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.stats :as stats]))

(defn run-success?
  [{:keys [run-type result finish-time-millis] :as _data-map}]
  (if (= :agent run-type)
    (not (:failure? result))
    (some? finish-time-millis)))

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
;;    - this also means that it needs to be indexed for every single one...


(defmetric
 AgentInvokeCount
 {:id       [:agent :invoke-count]
  :target   :root
  :value-fn
  (fn [data-map]
    {:type   :numeric
     :values [1]
    })})

(defmetric
 AgentSuccessRate
 {:id       [:agent :success-rate]
  :target   :root
  :value-fn
  (fn [data-map]
    {:type   :numeric
     :values [(if (run-success? data-map) 1 0)]
    })})

(defmetric
 AgentLatency
 {:id       [:agent :latency]
  :target   :root
  :value-fn
  (fn [{:keys [start-time-millis finish-time-millis]}]
    {:type   :numeric
     :values (if (and start-time-millis finish-time-millis)
               [(- finish-time-millis start-time-millis)])
    })})

;; this is used for LLM call count (sum) as well as LLM call count / trace (percentiles)
(defmetric
 ModelCallCount
 {:id       [:agent :model-call-count]
  :target   :root
  :value-fn
  (fn [{:keys [stats]}]
    (let [basic-stats (stats/aggregated-basic-stats stats)
          count       (-> basic-stats
                          :nested-op-stats
                          :model-call
                          (get :count 0))]
      {:type   :numeric
       :values [count]
      }))})

;; these token count metrics power:
;;  - token count (sum)
;;  - token count / trace (percentiles)
(defmetric
 InputTokenCount
 {:id       [:agent :input-token-count]
  :target   :root
  :value-fn
  (fn [{:keys [stats]}]
    (let [basic-stats (stats/aggregated-basic-stats stats)]
      {:type   :numeric
       :values [(:input-token-count basic-stats)]
      }))})

(defmetric
 OutputTokenCount
 {:id       [:agent :output-token-count]
  :target   :root
  :value-fn
  (fn [{:keys [stats]}]
    (let [basic-stats (stats/aggregated-basic-stats stats)]
      {:type   :numeric
       :values [(:output-token-count basic-stats)]
      }))})

(defmetric
 TotalTokenCount
 {:id       [:agent :total-token-count]
  :target   :root
  :value-fn
  (fn [{:keys [stats]}]
    (let [basic-stats (stats/aggregated-basic-stats stats)]
      {:type   :numeric
       :values [(:total-token-count basic-stats)]
      }))})

(defmetric
 ModelSuccessRate
 {:id       [:agent :model-success-rate]
  :target   :nodes
  :value-fn
  (fn [data-map]
    (let [model-info-maps (select [:nested-ops (selected? :type (pred= :model-call)) :info-map]
                                  data-map)
          fcount (count (filter #(contains? % "failure") model-info-maps))]
      {:type   :categorical
       :values {"success" (- (count model-calls) fcount)
                "failure" fcount}
      }))})

(defmetric
 ModelLatency
 {:id       [:agent :model-latencyu]
  :target   :nodes
  :value-fn
  (fn [data-map]
    (let [model-calls (select [:nested-ops (selected? :type (pred= :model-call))] data-map)]
      {:type   :numeric
       :values (mapv #(- (:finish-time-millis %) (:start-time-millis %)) model-calls)
      }))})



; - store/database call counts/latency stats
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
