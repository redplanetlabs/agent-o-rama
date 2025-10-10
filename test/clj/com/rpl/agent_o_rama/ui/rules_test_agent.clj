(ns com.rpl.agent-o-rama.ui.rules-test-agent
  "Test agent module for rules E2E tests.

  Provides a configurable agent that generates various types of runs to test
  different filter types (error, latency, token-count, feedback, input-match,
  output-match) and rule execution."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j :as lc4j])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

(defn rules-test-agent
  "Main agent implementation that generates runs based on mode.

  Supported modes:
  - :success        - Simple successful run
  - :error          - Run that throws an error
  - :slow           - Run with high latency (>500ms)
  - :fast           - Run with low latency (<100ms)
  - :chat           - Run with model call (generates token counts)
  - :input-match    - Run with specific input pattern
  - :output-match   - Run with specific output pattern
  - :node-specific  - Run that goes through multiple nodes"
  [agent-node {:strs [mode input delay-ms]}]
  (let [mode     (if (string? mode) (keyword mode) mode)
        input    (or input "test")
        delay-ms (or delay-ms 0)]

    (when (pos? delay-ms)
      (Thread/sleep (long delay-ms)))

    (case mode
      :success
      (aor/result!
       agent-node
       {"status" "success" "result" (str "Processed: " input)})

      :error
      (throw (ex-info "Intentional test error" {:input input}))

      :slow
      (do
        (Thread/sleep 600)
        (aor/result!
         agent-node
         {"status" "success" "latency" "slow" "result" input}))

      :fast
      (aor/result!
       agent-node
       {"status" "success" "latency" "fast" "result" input})

      :chat
      (let [model    (aor/get-agent-object agent-node "openai-model")
            messages [(SystemMessage. "You are a helpful assistant.")
                      (UserMessage. (str input))]
            response (lc4j/chat model (lc4j/chat-request messages {}))
            text     (.text (.aiMessage response))]
        (aor/result!
         agent-node
         {"status" "success" "mode" "chat" "response" text}))

      :input-match
      (aor/result!
       agent-node
       {"status" "success" "input" input "matched" "input"})

      :output-match
      (aor/result!
       agent-node
       {"status" "success" "output" "test-output-pattern" "matched" "output"})

      :node-specific
      (aor/emit! agent-node "process-node" {"input" input})

      ;; Default
      (aor/result!
       agent-node
       {"status" "success" "mode" "default" "result" input}))))

(defn process-node
  "Secondary node for testing node-specific rules"
  [agent-node input]
  (aor/result! agent-node
               {"status" "success" "node" "process-node" "result" (str "Processed: " input)}))

(defn counting-action
  "Test action that increments a counter"
  [params]
  (fn [fetcher input output run-info]
    {"action"    "counting"
     "timestamp" (System/currentTimeMillis)
     "input"     (str input)
     "output"    (str output)}))

(defn logging-action
  "Test action that logs the run"
  [params]
  (fn [fetcher input output run-info]
    {"action"     "logging"
     "rule-name"  (:rule-name run-info)
     "agent-name" (:agent-name run-info)
     "node-name"  (:node-name run-info)
     "timestamp"  (System/currentTimeMillis)}))

(defn setup-rules-testing!
  "Creates actions and adds rules with various filter types for testing.

  Options:
  - agent-manager: The agent manager instance
  - global-actions-depot: The global actions depot
  - agent-name: Name of the agent to attach rules to (default: 'RulesTestAgent')

  Returns a map with action and rule information."
  ([agent-manager global-actions-depot]
   (setup-rules-testing! agent-manager global-actions-depot {}))
  ([agent-manager global-actions-depot opts]
   (let [agent-name (get opts :agent-name "RulesTestAgent")]

     ;; Error filter rule - triggers on error status
     (ana/add-rule!
      global-actions-depot
      "error-rule"
      agent-name
      {:node-name         nil
       :action-name       "counting-action"
       :action-params     {}
       :filter            (aor-types/->valid-AndFilter [])
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :fail})

     (ana/add-rule!
      global-actions-depot
      "success-rule"
      "RulesTestAgent"
      {:node-name         nil
       :action-name       "logging-action"
       :action-params     {}
       :filter            (aor-types/->valid-AndFilter [])
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     (ana/add-rule!
      global-actions-depot
      "error-filter-rule"
      "RulesTestAgent"
      {:node-name         nil
       :action-name       "counting-action"
       :action-params     {}
       :filter            (aor-types/->ErrorFilter)
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :all})

     ;; Latency filter rule - triggers when latency > 1000ms
     (ana/add-rule!
      global-actions-depot
      "latency-rule"
      agent-name
      {:node-name         nil
       :action-name       "counting-action"
       :action-params     {}
       :filter            (aor-types/->valid-LatencyFilter
                           (aor-types/->valid-ComparatorSpec :>= 500))
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Token count filter rule - triggers when token count > 50
     (ana/add-rule!
      global-actions-depot
      "token-count-rule"
      agent-name
      {:node-name         nil
       :action-name       "logging-action"
       :action-params     {}
       :filter            (aor-types/->valid-TokenCountFilter
                           :total
                           (aor-types/->valid-ComparatorSpec :>= 1))
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Feedback filter rule - triggers when feedback score < 0.5
     (ana/add-rule!
      global-actions-depot
      "feedback-rule"
      agent-name
      {:node-name         nil
       :action-name       "logging-action"
       :action-params     {}
       :filter            (aor-types/->valid-FeedbackFilter
                           "a-rule"
                           "quality"
                           (aor-types/->valid-ComparatorSpec :< 0.5))
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Input match filter rule - triggers when input contains "error"
     (ana/add-rule!
      global-actions-depot
      "input-match-rule"
      agent-name
      {:node-name         nil
       :action-name       "counting-action"
       :action-params     {}
       :filter            (aor-types/->valid-InputMatchFilter
                           "$[0].mode"
                           #"success")
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Output match filter rule - triggers when output contains "success"
     (ana/add-rule!
      global-actions-depot
      "output-match-rule"
      agent-name
      {:node-name         nil
       :action-name       "logging-action"
       :action-params     {}
       :filter            (aor-types/->valid-OutputMatchFilter
                           "$.status"
                           #"success")
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Node-specific rule - triggers on the "process" node
     (ana/add-rule!
      global-actions-depot
      "node-specific-rule"
      agent-name
      {:node-name         "process-node"
       :action-name       "counting-action"
       :action-params     {}
       :filter            (aor-types/->valid-AndFilter [])
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     ;; Complex AND filter rule - latency > 500ms AND token count > 30
     (ana/add-rule!
      global-actions-depot
      "complex-and-rule"
      agent-name
      {:node-name         nil
       :action-name       "logging-action"
       :action-params     {}
       :filter            (aor-types/->valid-AndFilter
                           [(aor-types/->valid-LatencyFilter
                             (aor-types/->valid-ComparatorSpec :> 500))
                            (aor-types/->valid-TokenCountFilter
                             :input
                             (aor-types/->valid-ComparatorSpec :>= 30))])
       :sampling-rate     1.0
       :start-time-millis 0
       :status-filter     :success})

     {:actions ["counting-action" "logging-action"]
      :rules   ["error-rule" "latency-rule" "token-count-rule" "feedback-rule"
                "input-match-rule" "output-match-rule" "node-specific-rule"
                "complex-and-rule"]})))

(aor/defagentmodule RulesTestAgentModule
  [topology]

  ;; OpenAI model for :chat mode
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "fake-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         (.maxTokens (int 50))
         .build)))

  ;; Declare test action builders
  (aor/declare-action-builder
   topology
   "counting-action"
   "Test action that counts invocations"
   counting-action)

  (aor/declare-action-builder
   topology
   "logging-action"
   "Test action that logs run information"
   logging-action)

  ;; Main test agent with mode-based execution
  (-> topology
      (aor/new-agent "RulesTestAgent")
      (aor/node
       "start"
       "process-node"
       (fn [agent-node m]
         (rules-test-agent agent-node m)))
      (aor/node
       "process-node"
       nil
       process-node)))
