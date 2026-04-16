(ns com.rpl.agent-o-rama.impl.ui.rpc.evaluators
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [clojure.string :as str])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-all-builders!!
  [system {:keys [module-id]}]
  (let [manager (get-manager system module-id)]
    (foreign-invoke-query (:all-eval-builders-query (aor-types/underlying-objects manager)))))

(defn get-all-instances!!
  [system {:keys [module-id pagination filters limit cursor]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-evals-query underlying-objects)
        search-string (get filters :search-string)
        types (get filters :types)
        query-limit (or limit 20)
        pagination' (or pagination cursor)]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string)
                            (seq types)
                            (assoc :types types))
                          query-limit
                          pagination')))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn create!!
  [system {:keys [module-id builder-name name description params input-json-path output-json-path reference-output-json-path]}]
  (let [manager (get-manager system module-id)
        path-options (cond-> {}
                       (not (str/blank? input-json-path))
                       (assoc :input-json-path input-json-path)
                       (not (str/blank? output-json-path))
                       (assoc :output-json-path output-json-path)
                       (not (str/blank? reference-output-json-path))
                       (assoc :reference-output-json-path reference-output-json-path))]
    (aor/create-evaluator! manager name builder-name params description path-options)
    {:status :ok}))

(defn delete!!
  [system {:keys [module-id name]}]
  (let [manager (get-manager system module-id)]
    (aor/remove-evaluator! manager name)
    {:status :ok}))

(defn run!!
  [system {:keys [module-id name type run-data]}]
  (let [manager (get-manager system module-id)
        eval-fn (case type
                  :regular aor/try-evaluator
                  :comparative aor/try-comparative-evaluator
                  :summary aor/try-summary-evaluator
                  (throw (ex-info "Invalid evaluator type" {:type type})))
        input (:input run-data)
        ref-output (:referenceOutput run-data)
        output (:output run-data)
        outputs (:outputs run-data)]
    (case type
      :regular (eval-fn manager name input ref-output output)
      :comparative (eval-fn manager name input ref-output outputs)
      :summary
      (let [underlying-objects (aor-types/underlying-objects manager)
            multi-examples-query (:multi-examples-query underlying-objects)
            dataset-id (:dataset-id run-data)
            example-ids (:example-ids run-data)
            examples-map (foreign-invoke-query multi-examples-query dataset-id nil (vec example-ids))
            example-runs (mapv (fn [example-id]
                                 (let [example-data (get examples-map example-id)]
                                   (aor/mk-example-run
                                    (:input example-data)
                                    (:reference-output example-data)
                                    nil)))
                               example-ids)]
        (eval-fn manager name example-runs)))))
