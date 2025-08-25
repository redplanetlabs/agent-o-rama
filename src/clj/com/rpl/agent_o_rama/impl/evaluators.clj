(ns com.rpl.agent-o-rama.impl.evaluators
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agent_o_rama.impl.types
    AddEvaluator
    RemoveEvaluator]))

(defn invalid-json-path?
  [json-path]
  (if (empty? json-path)
    false
    (try
      (h/compile-json-path json-path)
      false
      (catch Throwable t
        true))))

(defn verify-evaluator-add
  [{:keys [builder-name params input-json-path output-json-path
           reference-output-json-path]}]
  (let [builder-info    (-> (po/agent-declared-objects-task-global)
                            .getEvaluatorBuilders
                            (get builder-name))
        declared-params (-> builder-info
                            :options
                            :params)
        declared-set    (-> declared-params
                            keys
                            set)
        provided-set    (-> params
                            keys
                            set)]
    (cond
      (nil? builder-info)
      (format "Evaluator builder does not exist: %s" builder-name)

      (not= declared-set provided-set)
      (format "Mismatched params (declared vs. provided): %s vs. %s"
              declared-set
              provided-set)

      (invalid-json-path? input-json-path)
      (format "Invalid input JSON path: %s" input-json-path)

      (invalid-json-path? output-json-path)
      (format "Invalid output JSON path: %s" output-json-path)

      (invalid-json-path? reference-output-json-path)
      (format "Invalid reference output JSON path: %s"
              reference-output-json-path)

      :else
      nil)))

(deframaop handle-evaluators-op
  [*data]
  (<<with-substitutions
   [$$evals (po/evaluators-task-global)]
   (<<subsource *data
    (case> AddEvaluator
           :> {:keys [*name *builder-name *params *description
                       *input-json-path *output-json-path
                       *reference-output-json-path]})
     (verify-evaluator-add *data :> *error-str)
     (<<if (some? *error-str)
       (ack-return> *error-str)
      (else>)
       (local-transform>
        [(keypath *name)
         (termval {:builder-name     *builder-name
                   :builder-params   *params
                   :description      *description
                   :input-json-path  *input-json-path
                   :output-json-path *output-json-path
                   :reference-output-json-path *reference-output-json-path})]
        $$evals))

    (case> RemoveEvaluator :> {:keys [*name]})
     (local-transform> [(keypath *name) NONE>] $$evals)
   )))
