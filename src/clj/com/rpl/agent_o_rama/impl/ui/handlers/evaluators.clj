(ns com.rpl.agent-o-rama.impl.ui.handlers.evaluators
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/get-all
  [{:keys [manager filters]} uid]
  (let [evals-pstate (:evals-pstate (aor-types/underlying-objects manager))
        all-builders-query (:all-eval-builders-query (aor-types/underlying-objects manager))
        all-builders (foreign-invoke-query all-builders-query)
        all-evals (foreign-select [ALL] evals-pstate)]
    (->> all-evals
         (map (fn [[name info]]
                (let [builder-info (get all-builders (:builder-name info))]
                  (assoc info :name name :type (:type builder-info)))))
         ;; Optional filtering by type
         (filter (if-let [types (:types filters)]
                   #(contains? types (:type %))
                   (constantly true)))
         (into []))))

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