(ns com.rpl.agent.basic.action-agent
  "Demonstrates using actions to add feedback and observe agent behavior.

  Features demonstrated:
  - declare-action-builder: Define custom actions
  - add-rule!: Add rules to trigger actions based on filters
  - delete-rule!: Remove action rules
  - FeedbackFilter: Filter based on feedback from other rules
  - Rule dependencies: Chain rules so one depends on another's feedback
  - Action logs: Inspect action execution history"
  (:use [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.test :as rtest]))

;;; Action example module
(aor/defagentmodule ActionAgentModule
  [topology]

  ;; Declare a custom action that logs information
  (aor/declare-action-builder
   topology
   "log-action"
   "Logs input and output for inspection"
   (fn [params]
     (fn [fetcher input output run-info]
       {"input"  input
        "output" output
        "params" params})))

  ;; Declare an evaluator for generating feedback
  (aor/declare-evaluator-builder
   topology
   "length-eval"
   "Evaluates if output is short (< 5 chars)"
   (fn [params]
     (fn [fetcher input ref-output output]
       {"short?" (< (count output) 5)})))

  ;; Simple agent that appends exclamation mark
  (-> topology
      (aor/new-agent "TextAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node text]
         (aor/result! agent-node (str text "!"))))))

(defn -main
  "Run the action example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ActionAgentModule {:tasks 1 :threads 1})

    (let [manager    (aor/agent-manager
                      ipc
                      (get-module-name ActionAgentModule))
          agent      (aor/agent-client manager "TextAgent")
          depot      (:global-actions-depot (aor-types/underlying-objects manager))
          action-log (:action-log-query (aor-types/underlying-objects agent))]

      ;; Create evaluator
      (aor/create-evaluator! manager "eval1" "length-eval" {} "")

      ;; Add rule to run evaluator
      (ana/add-rule!
       depot
       "eval-rule"
       "TextAgent"
       {:action-name       "aor/eval"
        :action-params     {"name" "eval1"}
        :filter            (aor-types/->AndFilter [])
        :sampling-rate     1.0
        :start-time-millis 0
        :include-failures? false})

      ;; Add rule that depends on evaluator feedback
      (ana/add-rule!
       depot
       "log-short"
       "TextAgent"
       {:action-name   "log-action"
        :action-params {"note" "short output"}
        :filter        (aor-types/->FeedbackFilter
                        "eval-rule"
                        "short?"
                        (aor-types/->ComparatorSpec := true))
        :sampling-rate 1.0
        :start-time-millis 0
        :include-failures? false})

      (println "\n1. Invoke with short text:")
      (println "   Input: \"Hi\" -> Result:" (aor/agent-invoke agent "Hi"))
      (Thread/sleep 3000)

      (println "\n2. Invoke with long text:")
      (println "   Input: \"Hello World\" -> Result:" (aor/agent-invoke agent "Hello World"))
      (Thread/sleep 3000)

      (println "\n3. Check action logs:")
      (let [{:keys [actions]} (foreign-invoke-query action-log "log-short" 10 nil)]
        (println "   Actions triggered for short text:" (count actions)))

      (println "\n4. Delete log-short rule")
      (ana/delete-rule! depot "TextAgent" "log-short")
      (println "   Rule deleted"))))
