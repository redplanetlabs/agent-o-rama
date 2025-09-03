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

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/create
  [{:keys [manager module-id builder-name name description params input-json-path output-json-path reference-output-json-path]} uid]
  (let [options (cond-> {}
                  (not (str/blank? description))
                  (assoc :description description)

                  (not (str/blank? input-json-path))
                  (assoc :input-json-path input-json-path)

                  (not (str/blank? output-json-path))
                  (assoc :output-json-path output-json-path)

                  (not (str/blank? reference-output-json-path))
                  (assoc :reference-output-json-path reference-output-json-path)

                  (seq params)
                  (assoc :params params))]

    (aor/create-evaluator! manager builder-name name options)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/delete
  [{:keys [manager name]} uid]
  (aor/remove-evaluator! manager name)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/try
  [{:keys [manager name type run-data]} uid]
  (let [eval-fn (case type
                  :regular aor/try-evaluator
                  :comparative aor/try-comparative-evaluator
                  :summary aor/try-summary-evaluator
                  (throw (ex-info "Invalid evaluator type" {:type type})))
        ;; The data from the UI is JSON strings, we need to parse them on the backend
        parsed-input (when-let [input (:input run-data)]
                       (j/read-value input))
        parsed-ref-output (when-let [ref-output (:referenceOutput run-data)]
                            (j/read-value ref-output))
        parsed-output (when-let [output (:output run-data)]
                        (j/read-value output))]

    (case type
      :regular
      (eval-fn manager name parsed-input parsed-ref-output parsed-output)

      :comparative
      (eval-fn manager name parsed-input parsed-ref-output (:outputs run-data))

      :summary
      (eval-fn manager name (:exampleRuns run-data)))))
