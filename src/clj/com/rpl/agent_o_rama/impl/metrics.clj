(ns com.rpl.agent-o-rama.impl.metrics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [rpl.schema.core :as s]))

(defn run-success?
  [{:keys [run-type result finish-time-millis] :as _data-map}]
  (if (= :agent run-type)
    (not (:failure? result))
    (some? finish-time-millis)))

(def ALL-METRICS {})
(defn all-metrics [] ALL-METRICS)

(aor-types/defaorrecord MetricDefinition
  [target :- (s/enum :root :nodes)
   ;; (data map) -> {metric-id [{:type <:numeric, :categorical>, :values <values>} ...]}
   metrics-fn :- clojure.lang.IFn])


(defn value-fn->metrics-fn
  [id value-fn]
  (fn [data-map]
    {id [(value-fn data-map)]}))

;; info-map has:
;;   - :id
;;   - :target
;;   - :value-fn
;;    (data map) -> {:type <:numeric, :categorical>, :values <values>})
;;      - values for :categorical is map of category string -> count
;;      - values for :numeric is list of numbers
(defmacro defmetric
  [name info-map]
  `(let [info-map# ~info-map
         metric#   (->valid-MetricDefinition
                    (:target info-map#)
                    (value-fn->metrics-fn (:id info-map#) (:value-fn info-map#)))]
     (alter-var-root #'ALL-METRICS assoc (:id info-map#) metric#)
     (def ~name metric#)))

;; this powers:
;;  - agent invoke count (just the count in the NumberStats)
;   - success rate (sum over the count)
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
     :values (if (and start-time-millis finish-time-millis) ; defensive, shouldn't be necessary
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
 TokenCounts
 {:id       [:agent :token-counts]
  :target   :root
  :value-fn
  (fn [{:keys [stats]}]
    (let [basic-stats (stats/aggregated-basic-stats stats)]
      {:type   :categorical
       :values {"input"  (:input-token-count basic-stats)
                "output" (:output-token-count basic-stats)
                "total"  (:total-token-count basic-stats)}
      }))})

(defmetric
 ModelSuccessRate
 {:id       [:agent :model-success-rate]
  :target   :nodes
  :value-fn
  (fn [{:keys [nested-ops]}]
    (let [model-info-maps (select [ALL (selected? :type (pred= :model-call)) :info] nested-ops)
          fcount (count (filter #(contains? % "failure") model-info-maps))]
      {:type   :categorical
       :values {"success" (- (count model-info-maps) fcount)
                "failure" fcount}
      }))})

(defn op-latency-fn
  [op-type]
  (fn [{:keys [nested-ops]}]
    (let [ops (select [ALL (selected? :type (pred= op-type))] nested-ops)]
      {:type   :numeric
       :values (mapv #(- (:finish-time-millis %) (:start-time-millis %)) ops)
      })))

;; these can be used for:
;;  - op counts (count)
;;  - op latencies (percentiles)

(defmetric
 ModelLatency
 {:id       [:agent :model-latency]
  :target   :nodes
  :value-fn (op-latency-fn :model-call)})

(defmetric
 StoreReadLatency
 {:id       [:agent :store-read-latency]
  :target   :nodes
  :value-fn (op-latency-fn :store-read)})

(defmetric
 StoreWriteLatency
 {:id       [:agent :store-write-latency]
  :target   :nodes
  :value-fn (op-latency-fn :store-write)})

(defmetric
 DatabaseReadLatency
 {:id       [:agent :db-read-latency]
  :target   :nodes
  :value-fn (op-latency-fn :db-read)})

(defmetric
 DatabaseWriteLatency
 {:id       [:agent :db-write-latency]
  :target   :nodes
  :value-fn (op-latency-fn :db-write)})

(defmetric
 AgentFirstTokenTime
 {:id       [:agent :first-token-time]
  :target   :root
  :value-fn
  (fn [{:keys [start-time-millis first-token-time-millis]}]
    {:type   :numeric
     :values (if first-token-time-millis [(- first-token-time-millis start-time-millis)])
    })})

(defmetric
 ModelFirstTokenTime
 {:id       [:agent :model-first-token-time]
  :target   :nodes
  :value-fn
  (fn [{:keys [nested-ops]}]
    (let [ops (select [ALL
                       (selected? :type (pred= :model-call))
                       (selected? :info (must "firstTokenTimeMillis") number?)]
                      nested-ops)]
      {:type   :numeric
       :values (mapv (fn [{:keys [start-time-millis info]}]
                       (- (get info "firstTokenTimeMillis") start-time-millis))
                     ops)
      }))})

(defmetric
 AgentNodeLatencies
 {:id       [:agent :node-latencies]
  :target   :nodes
  :value-fn
  (fn [{:keys [node start-time-millis finish-time-millis]}]
    {:type   :categorical
     :values (when (and node start-time-millis finish-time-millis)
               {node (- finish-time-millis start-time-millis)})
    })})
