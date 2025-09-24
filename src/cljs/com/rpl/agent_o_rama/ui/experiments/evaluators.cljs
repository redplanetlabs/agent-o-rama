(ns com.rpl.agent-o-rama.ui.experiments.evaluators)

(defn identifier-title [value]
  (cond
    (keyword? value) (name value)
    (symbol? value) (name value)
    :else (str value)))

(defn metric-title [metric-key]
  (identifier-title metric-key))

(defn normalize-entries
  "Normalize evaluator metric data into a flat sequence of [eval-name metrics] pairs.
  Accepts maps, map entries, nested sequences, or nil."
  [entries]
  (letfn [(normalize-entry [entry]
            (cond
              (nil? entry) []
              (map-entry? entry) [entry]
              (and (vector? entry) (= 2 (count entry))) [entry]
              (map? entry) (mapcat normalize-entry (seq entry))
              (sequential? entry) (mapcat normalize-entry entry)
              (coll? entry) (mapcat normalize-entry (seq entry))
              :else []))]
    (normalize-entry entries)))

(defn- entries->metrics-by-evaluator [entries]
  (reduce (fn [acc [eval-name metrics]]
            (if eval-name
              (update acc eval-name
                      (fnil into #{})
                      (keys (or metrics {})))
              acc))
          {}
          entries))

(defn- metrics->evaluators [metrics-by-evaluator]
  (reduce-kv (fn [acc eval-name metric-keys]
               (reduce (fn [m metric-key]
                         (update m metric-key (fnil conj #{}) eval-name))
                       acc
                       metric-keys))
             {}
             metrics-by-evaluator))

(defn collect-column-metadata
  "Given evaluator metric data, compute column metadata including disambiguated
  labels when multiple evaluators share the same metric. Accepts any input
  shape supported by `normalize-entries`.

  Optional opts:
  - :metric->label – function mapping a metric key to its base label string."
  ([entries]
   (collect-column-metadata entries {}))
  ([entries {:keys [metric->label]}]
   (let [metric->label (or metric->label metric-title)
         normalized-entries (normalize-entries entries)
         metrics-by-evaluator (entries->metrics-by-evaluator normalized-entries)
         sorted-evals (sort (keys metrics-by-evaluator))
         metric->evals (metrics->evaluators metrics-by-evaluator)
         ambiguous-metrics (->> metric->evals
                                (keep (fn [[metric-key eval-names]]
                                        (when (> (count eval-names) 1)
                                          metric-key)))
                                set)
         columns (->> sorted-evals
                      (mapcat (fn [eval-name]
                                (let [metric-keys (sort (seq (get metrics-by-evaluator eval-name)))]
                                  (for [metric-key metric-keys
                                        :let [metric-label (metric->label metric-key)
                                              ambiguous? (contains? ambiguous-metrics metric-key)
                                              eval-label (identifier-title eval-name)
                                              column-label (if (and ambiguous? eval-label)
                                                             (str eval-label "/" metric-label)
                                                             metric-label)]]
                                    {:column-key (str eval-name "::" metric-label)
                                     :eval-name eval-name
                                     :metric-key metric-key
                                     :metric-label metric-label
                                     :label column-label
                                     :ambiguous? ambiguous?}))))
                      vec)
         labels-by-eval-metric (into {}
                                     (map (fn [{:keys [eval-name metric-key] :as column}]
                                            [[eval-name metric-key] column])
                                          columns))]
     {:columns columns
      :columns-by-eval (group-by :eval-name columns)
      :ambiguous-metrics ambiguous-metrics
      :metrics-by-evaluator metrics-by-evaluator
      :labels-by-eval-metric labels-by-eval-metric
      :metric->label metric->label})))

(defn columns-for-evaluator
  "Return ordered column metadata for a given evaluator name using the
  structure returned by `collect-column-metadata`."
  [{:keys [columns-by-eval metrics-by-evaluator metric->label]} eval-name]
  (let [metric->label (or metric->label metric-title)]
    (or (seq (get columns-by-eval eval-name))
        (let [metric-keys (sort (seq (get metrics-by-evaluator eval-name)))]
          (for [metric-key metric-keys
                :let [metric-label (metric->label metric-key)
                      eval-label (identifier-title eval-name)
                      column-label (if eval-label
                                     (str eval-label "/" metric-label)
                                     metric-label)]]
            {:column-key (str eval-name "::" metric-label)
             :eval-name eval-name
             :metric-key metric-key
             :metric-label metric-label
             :label column-label
             :ambiguous? false})))))

(defn label-for
  "Return label metadata for a specific evaluator/metric pair using the result
  from `collect-column-metadata`."
  [{:keys [labels-by-eval-metric ambiguous-metrics metric->label]} eval-name metric-key]
  (let [metric->label (or metric->label metric-title)
        ambiguous-metrics (set ambiguous-metrics)
        metric-label (metric->label metric-key)
        eval-label (identifier-title eval-name)
        ambiguous? (contains? ambiguous-metrics metric-key)
        fallback-label (if (and ambiguous? eval-label)
                         (str eval-label "/" metric-label)
                         metric-label)]
    (if-let [{:keys [label metric-label ambiguous?]} (get labels-by-eval-metric [eval-name metric-key])]
      {:label label
       :metric-label metric-label
       :ambiguous? ambiguous?}
      {:label fallback-label
       :metric-label metric-label
       :ambiguous? ambiguous?})))
