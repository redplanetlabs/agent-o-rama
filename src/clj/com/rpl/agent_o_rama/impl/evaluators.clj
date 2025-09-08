(ns com.rpl.agent-o-rama.impl.evaluators
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.string :as str]
   [clojure.spec.alpha :as spec]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama.ops :as ops]
   [expound.alpha :as expound]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    AgentNode
    AgentObjectFetcher]
   [com.rpl.agent_o_rama.impl.types
    AddEvaluator
    RemoveEvaluator]
   [dev.langchain4j.data.message
    AiMessage
    SystemMessage
    TextContent
    ToolExecutionResultMessage
    UserMessage]
   [dev.langchain4j.model.chat.request.json
    JsonRawSchema]))

(spec/def ::description string?)
(spec/def ::default string?)
(def ^:private allowed-entry-keys #{:description :default})

(spec/def ::param-entry
  (spec/and
   (spec/keys :opt-un [::description ::default])
   (fn [m]
     (and (map? m)
          (every? allowed-entry-keys (keys m))))))

(spec/def ::params
  (spec/map-of string? (spec/nilable ::param-entry)))

(defn validate-params!
  [params]
  (when-not (spec/valid? ::params params)
    (throw (h/ex-info (str "Invalid params declaration"
                           (expound/expound-str ::params params))
                      {}))))

(defprotocol MessageLength
  (message-length [this]))

(extend-protocol MessageLength
  nil
  (message-length [this] 0)

  String
  (message-length [this] (count this))

  AiMessage
  (message-length [this]
    (-> this
        .text
        count))

  SystemMessage
  (message-length [this]
    (-> this
        .text
        count))

  ToolExecutionResultMessage
  (message-length [this]
    (-> this
        .text
        count))

  UserMessage
  (message-length [this]
    (let [contents (filter #(instance? TextContent %) (.contents this))]
      (reduce
       +
       0
       (mapv
        (fn [^TextContent tc]
          (-> tc
              .text
              count))
        contents)))))

(def DEFAULT-LLM-OUTPUT-SCHEMA
  "{
  \"type\": \"object\",
  \"properties\": {
    \"score\": {
      \"type\": \"integer\",
      \"minimum\": 0,
      \"maximum\": 10
    }
  },
  \"required\": [\"score\"],
  \"additionalProperties\": false
}")

(def DEFAULT-LLM-PROMPT
  "You are an impartial evaluator. Your task is to judge the quality of a model's output.

- **Input**: %input
- **Reference Output**: %referenceOutput
- **Model Output**: %output

Evaluate whether the Model Output correctly and completely answers the Input, matching the meaning of the Reference Output.
Be strict: minor wording differences are acceptable, but factual errors, omissions, or contradictions are not.")

(def BUILT-IN
  {"aor/llm-judge"
   {:type :regular
    :builder-fn
    (fn [params]
      (let [temperature (Double/parseDouble (get params "temperature"))
            prompt-template (get params "prompt")
            model-name (get params "model")
            output-schema (get params "outputSchema")]
        (fn [fetcher input ref-output output]
          (let [model (.getAgentObject ^AgentObjectFetcher fetcher model-name)
                prompt (-> prompt-template
                           (str/replace "%input" input)
                           (str/replace "%output" output)
                           (str/replace "%referenceOutput" ref-output))]
            (-> model
                (lc4j/chat
                 (lc4j/chat-request
                  [prompt]
                  {:temperature temperature
                   :response-format
                   (lc4j/json-response-format
                    "Evaluation"
                    (JsonRawSchema/from output-schema))}))
                .aiMessage
                .text
                j/read-value)))))
    :description
    "Define an LLM judge with customizable prompt, model, temperature, and output schema. By configuring the output schema with multiple keys, the judge can return scores for multiple evaluations at once."
    :options
    {:params
     {"prompt"
      {:description
       "Prompt for the LLM. %input, %output, and %referenceOutput can be used as variables in the prompt"
       :default DEFAULT-LLM-PROMPT}

      "model"
      {:description
       "Model to use. This refers to a model declared as an agent object in the module."}

      "temperature"
      {:description
       "Floating-point temperature of the LLM"
       :default "0.0"}
      "outputSchema"
      {:description
       "JSON schema for the output of the LLM. Each key of the output is a separate evaluation score."
       :default DEFAULT-LLM-OUTPUT-SCHEMA}}
     ;; All paths enabled by default (flags omitted = true)
     }}
   "aor/conciseness"
   {:type :regular
    :builder-fn
    (fn [params]
      (let [len (Long/parseLong (get params "threshold"))]
        (fn [fetcher input ref-output output]
          {"concise?" (<= (message-length output) len)})))
    :description
    "Boolean evaluator on whether the output's length is below a threshold. Works on strings or Langchain4j message types. User message length is calculated as the sum of the lengths of text contents within, with other types of content ignored."
    :options
    {:params
     {"threshold"
      {:description
       "Threshold length in terms of number of characters for a message to be concise"
       :default "300"}}
     :input-path? false
     :reference-output-path? false
     ;; output-path? defaults to true
     }}
   "aor/f1-score"
   {:type :summary
    :builder-fn
    (fn [{:strs [positiveValue]}]
      (fn [fetcher example-runs]
        (let [{:keys [tp fp fn]}
              (reduce
               (fn [{:keys [tp fp fn] :as acc}
                    {:keys [output reference-output]}]
                 (cond
                   (and (= output positiveValue)
                        (= reference-output positiveValue))
                   (assoc acc :tp (inc tp))

                   (and (= output positiveValue)
                        (not= reference-output positiveValue))
                   (assoc acc :fp (inc fp))

                   (and (not= output positiveValue)
                        (= reference-output positiveValue))
                   (assoc acc :fn (inc fn))

                   :else acc))
               {:tp 0 :fp 0 :fn 0}
               example-runs)
              precision (if (pos? (+ tp fp)) (/ tp (+ tp fp)) 0.0)
              recall (if (pos? (+ tp fn)) (/ tp (+ tp fn)) 0.0)]
          {"score"
           (if (pos? (+ precision recall))
             (double (/ (* 2 precision recall) (+ precision recall)))
             0.0)
           "precision" (double precision)
           "recall" (double recall)})))
    :description
    "Compute F1, precision, and recall scores on a list of runs using the provided 'positiveValue' param to determine true positives, false positives, and false negatives."
    :options
    {:params
     {"positiveValue"
      {:description
       "Value considered a positive classification"}}
     :input-path? false
     ;; output-path? and reference-output-path? default to true
     }}})
(defn invalid-json-path
  [json-path]
  (if (empty? json-path)
    nil
    (try
      (h/compile-json-path json-path)
      nil
      (catch Throwable t
        (h/throwable->str t)))))

(defn try-make-evaluator
  [{:keys [builder-fn]} params]
  (try
    (builder-fn params)
    nil
    (catch Throwable t
      (format "Error making evaluator\n\n%s" (h/throwable->str t)))))

(defn all-evaluator-builders
  []
  (let [declared-objects (po/agent-declared-objects-task-global)]
    (merge BUILT-IN
           (.getEvaluatorBuilders declared-objects))))

(defn verify-evaluator-add
  [{:keys [builder-name params input-json-path output-json-path
           reference-output-json-path]}]
  (let [builder-info (get (all-evaluator-builders) builder-name)
        declared-params (-> builder-info
                            :options
                            :params)
        declared-set (-> declared-params
                         keys
                         set)
        provided-set (-> params
                         keys
                         set)]
    (cond
      (nil? builder-info)
      (format "Evaluator builder does not exist: %s" builder-name)

      (not= declared-set provided-set)
      (format "Mismatched params (declared vs. provided): %s vs. %s"
              declared-set
              provided-set)

      (invalid-json-path input-json-path)
      (format "Invalid input JSON path: %s\n\n%s"
              input-json-path
              (invalid-json-path input-json-path))

      (invalid-json-path output-json-path)
      (format "Invalid output JSON path: %s\n\n%s"
              output-json-path
              (invalid-json-path output-json-path))

      (invalid-json-path reference-output-json-path)
      (format "Invalid reference output JSON path: %s\n\n%s"
              reference-output-json-path
              (invalid-json-path reference-output-json-path))

      :else
      (try-make-evaluator builder-info params))))

(deframaop handle-evaluators-op
  [*data]
  (<<with-substitutions
   [$$evals (po/evaluators-task-global)]
   (<<subsource *data
                (case> AddEvaluator
                       :> {:keys [*name *builder-name *params *description
                                  *input-json-path *output-json-path
                                  *reference-output-json-path]})
                (local-select> (view contains? *name) $$evals :> *exists?)
                (ifexpr *exists?
                        "Evaluator already exists"
                        (verify-evaluator-add *data)
                        :> *error-str)
                (<<if (some? *error-str)
                      (ack-return> *error-str)
                      (else>)
                      (local-transform>
                       [(keypath *name)
                        (termval {:builder-name *builder-name
                                  :builder-params *params
                                  :description *description
                                  :input-json-path *input-json-path
                                  :output-json-path *output-json-path
                                  :reference-output-json-path *reference-output-json-path})]
                       $$evals))

                (case> RemoveEvaluator :> {:keys [*name]})
                (local-transform> [(keypath *name) NONE>] $$evals))))

(defn try-evaluator-impl
  [evals-pstate try-eval-query all-eval-builders-query name type params]
  (let [{:keys [builder-name builder-params]} (foreign-select-one (keypath name)
                                                                  evals-pstate)
        all-builders (foreign-invoke-query all-eval-builders-query)
        actual-type (-> all-builders
                        (get builder-name)
                        :type)]
    (when (nil? builder-name)
      (throw (h/ex-info "Evaluator does not exist" {:name name})))
    (when-not (contains? all-builders builder-name)
      (throw (h/ex-info "Builder for evaluator no longer exists"
                        {:name name :builder-name builder-name})))
    (when (not= type actual-type)
      (throw (h/ex-info "Evaluator type mismatch"
                        {:actual actual-type :expected type})))
    (foreign-invoke-query try-eval-query
                          name
                          type
                          builder-name
                          builder-params
                          params)))
