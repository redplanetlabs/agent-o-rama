(ns com.rpl.agent-o-rama.ui.feedback-test-agent
  "Test agent module for feedback display E2E tests.

  Provides a configurable agent with evaluators and actions that generate
  feedback with varying numbers of scores, both at agent and node levels."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

;;; Evaluator builders

(defn single-score-evaluator
  "Evaluator that returns a single score"
  [params]
  (fn [fetcher input ref-output output]
    {"quality" (if (string? output)
                 (>= (count output) 5)
                 false)}))

(defn dual-score-evaluator
  "Evaluator that returns two scores"
  [params]
  (fn [fetcher input ref-output output]
    {"length-ok" (if (string? output)
                   (>= (count output) 3)
                   false)
     "has-prefix" (if (string? output)
                    (.startsWith ^String output "test-")
                    false)}))

(defn triple-score-evaluator
  "Evaluator that returns three scores"
  [params]
  (fn [fetcher input ref-output output]
    {"short" (if (string? output)
               (< (count output) 10)
               false)
     "medium" (if (string? output)
                (and (>= (count output) 10)
                     (< (count output) 20))
                false)
     "long" (if (string? output)
              (>= (count output) 20)
              false)}))

(defn numeric-scores-evaluator
  "Evaluator that returns numeric scores"
  [params]
  (fn [fetcher input ref-output output]
    (let [length (if (string? output) (count output) 0)]
      {"length" length
       "score" (double (/ length 10))
       "rating" (min 5 (int (/ length 2)))})))

;;; Action builder

(defn feedback-collector-action
  "Action that collects feedback from evaluators"
  [params]
  (fn [fetcher input output run-info]
    (let [feedback-results (get-in run-info [:feedback :results] [])]
      {"collected-count" (count feedback-results)
       "feedback-summary" (mapv #(select-keys % [:source :scores])
                                feedback-results)
       "input" input
       "output" output})))

;;; Test agent implementation

(defn feedback-test-agent-impl
  "Agent implementation that generates output for evaluation"
  [agent-node input]
  (let [mode (if (map? input) (get input "mode") "default")
        text (if (map? input) (get input "text") (str input))]
    (case mode
      "short"
      (aor/result! agent-node "ok")

      "medium"
      (aor/result! agent-node "test-medium-result")

      "long"
      (aor/result! agent-node "test-this-is-a-very-long-result-string-for-testing")

      "prefixed"
      (aor/result! agent-node (str "test-" text))

      ;; default
      (aor/result! agent-node (str "test-" text)))))

;;; Helper function to setup evaluators and rules

(defn setup-feedback-testing!
  "Creates evaluators and adds rules to generate feedback at various levels.

  Options:
  - agent-manager: The agent manager instance
  - global-actions-depot: The global actions depot
  - agent-name: Name of the agent to attach rules to (default: 'FeedbackTestAgent')
  - include-node-rules?: Whether to include node-specific rules (default: true)

  Returns a map with evaluator and rule information."
  ([agent-manager global-actions-depot]
   (setup-feedback-testing! agent-manager global-actions-depot {}))
  ([agent-manager global-actions-depot opts]
   (let [agent-name (get opts :agent-name "FeedbackTestAgent")
         include-node-rules? (get opts :include-node-rules? true)]

     ;; Create evaluators with different score counts
     (aor/create-evaluator! agent-manager
                            "single-eval"
                            "single-score"
                            {}
                            "Single score evaluator")

     (aor/create-evaluator! agent-manager
                            "dual-eval"
                            "dual-score"
                            {}
                            "Dual score evaluator")

     (aor/create-evaluator! agent-manager
                            "triple-eval"
                            "triple-score"
                            {}
                            "Triple score evaluator")

     (aor/create-evaluator! agent-manager
                            "numeric-eval"
                            "numeric-score"
                            {}
                            "Numeric scores evaluator")

     ;; Add agent-level evaluation rules (node-name: nil)
     (ana/add-rule! global-actions-depot
                    "agent-single-eval"
                    agent-name
                    {:node-name         nil
                     :action-name       "aor/eval"
                     :action-params     {"name" "single-eval"}
                     :filter            (aor-types/->AndFilter [])
                     :sampling-rate     1.0
                     :start-time-millis 0
                     :status-filter     :success})

     (ana/add-rule! global-actions-depot
                    "agent-dual-eval"
                    agent-name
                    {:node-name         nil
                     :action-name       "aor/eval"
                     :action-params     {"name" "dual-eval"}
                     :filter            (aor-types/->AndFilter [])
                     :sampling-rate     1.0
                     :start-time-millis 0
                     :status-filter     :success})

     ;; Add node-level evaluation rules
     (when include-node-rules?
       (ana/add-rule! global-actions-depot
                      "node-triple-eval"
                      agent-name
                      {:node-name         "process"
                       :action-name       "aor/eval"
                       :action-params     {"name" "triple-eval"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success})

       (ana/add-rule! global-actions-depot
                      "node-numeric-eval"
                      agent-name
                      {:node-name         "process"
                       :action-name       "aor/eval"
                       :action-params     {"name" "numeric-eval"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success}))

     ;; Add action rule to collect feedback
     (ana/add-rule! global-actions-depot
                    "collect-feedback"
                    agent-name
                    {:node-name         nil
                     :action-name       "feedback-collector"
                     :action-params     {}
                     :filter            (aor-types/->AndFilter [])
                     :sampling-rate     1.0
                     :start-time-millis 0
                     :status-filter     :success})

     {:evaluators ["single-eval" "dual-eval" "triple-eval" "numeric-eval"]
      :agent-rules ["agent-single-eval" "agent-dual-eval" "collect-feedback"]
      :node-rules (when include-node-rules?
                    ["node-triple-eval" "node-numeric-eval"])})))

;;; Test agent module

(aor/defagentmodule FeedbackTestAgentModule
  [topology]

  ;; Declare evaluator builders
  (aor/declare-evaluator-builder
   topology
   "single-score"
   "Evaluator that returns a single score"
   single-score-evaluator)

  (aor/declare-evaluator-builder
   topology
   "dual-score"
   "Evaluator that returns two scores"
   dual-score-evaluator)

  (aor/declare-evaluator-builder
   topology
   "triple-score"
   "Evaluator that returns three scores"
   triple-score-evaluator)

  (aor/declare-evaluator-builder
   topology
   "numeric-score"
   "Evaluator that returns numeric scores"
   numeric-scores-evaluator)

  ;; Declare action builder
  (aor/declare-action-builder
   topology
   "feedback-collector"
   "Action that collects and summarizes feedback"
   feedback-collector-action)

  ;; Main test agent
  (-> topology
      (aor/new-agent "FeedbackTestAgent")
      (aor/node
       "process"
       nil
       feedback-test-agent-impl)))
