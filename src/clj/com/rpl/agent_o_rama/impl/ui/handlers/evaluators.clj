(ns com.rpl.agent-o-rama.impl.ui.handlers.evaluators
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import [java.util UUID])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/get-all-builders
  [{:keys [manager]} uid]
  (foreign-invoke-query (:all-eval-builders-query (aor-types/underlying-objects manager))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/get-all-instances
  [{:keys [manager module-id filters]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-evals-query underlying-objects)
        search-string (get filters :search-string)
        types (get filters :types)]
    ;; Invoke the search query with optional search string and types filters
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string)

                            (seq types)
                            (assoc :types types))
                          1000 ; limit
                          nil ; no pagination key
                          )))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/create
  [{:keys [manager module-id builder-name name description params input-json-path output-json-path reference-output-json-path]} uid]
  (let [path-options (cond-> {}
                       (not (str/blank? input-json-path))
                       (assoc :input-json-path input-json-path)

                       (not (str/blank? output-json-path))
                       (assoc :output-json-path output-json-path)

                       (not (str/blank? reference-output-json-path))
                       (assoc :reference-output-json-path reference-output-json-path))]
    (aor/create-evaluator! manager name builder-name params description path-options)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/delete
  [{:keys [manager name]} uid]
  (aor/remove-evaluator! manager name)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/run
  [{:keys [manager name type run-data]} uid]
  (let [eval-fn (case type
                  :regular aor/try-evaluator
                  :comparative aor/try-comparative-evaluator
                  :summary aor/try-summary-evaluator
                  (throw (ex-info "Invalid evaluator type" {:type type})))

        ;; No parsing needed for input, output, or outputs.
        ;; They are already Clojure data structures.
        input (:input run-data)
        ref-output (:referenceOutput run-data)
        output (:output run-data)
        outputs (:outputs run-data)]

    (case type
      :regular
      (eval-fn manager name input ref-output output)

      :comparative
      (eval-fn manager name input ref-output outputs)

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
                                    nil))) ; Actual output is not available for summary
                               example-ids)]
        (eval-fn manager name example-runs)))))
