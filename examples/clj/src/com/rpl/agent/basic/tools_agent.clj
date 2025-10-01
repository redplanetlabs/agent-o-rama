(ns com.rpl.agent.basic.tools-agent
  "Demonstrates LangChain4j tools integration with OpenAI chat models.

  Features demonstrated:
  - new-tools-agent: Create specialized agent for tool execution
  - tool-specification: Define tool schemas for LangChain4j
  - OpenAI model with tool calling capabilities
  - Natural language to tool execution workflow"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    ToolExecutionResultMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;;; Tool function definitions
(defn calculate-tool
  "Simple calculator tool that performs basic arithmetic"
  [args]
  (let [operation (args "operation")
        a         (args "a")
        b         (args "b")
        result    (case operation
                    "add" (+ a b)
                    "subtract" (- a b)
                    "multiply" (* a b)
                    "divide" (if (zero? b)
                               "Error: Division by zero"
                               (/ a b))
                    "Error: Unknown operation")]
    (str result)))

(defn string-tool
  "String manipulation tool for text processing"
  [args]
  (let [text      (args "text")
        operation (args "operation")]
    (case operation
      "uppercase" (str/upper-case text)
      "lowercase" (str/lower-case text)
      "reverse" (str/reverse text)
      "length" (str (count text))
      "Error: Unknown string operation")))

;;; Tool specifications for LangChain4j
(def CALCULATOR-TOOL
  (tools/tool-info
   (tools/tool-specification
    "calculator"
    (lj/object
     {:description "Parameters for calculator operations"
      :required    ["operation" "a" "b"]}
     {"operation" (lj/enum "The arithmetic operation to perform"
                           ["add" "subtract" "multiply" "divide"])
      "a"         (lj/number "The first number")
      "b"         (lj/number "The second number")})
    "Performs basic arithmetic operations on two numbers")
   calculate-tool))

(def STRING-TOOL
  (tools/tool-info
   (tools/tool-specification
    "string-processor"
    (lj/object
     {:description "Parameters for string manipulation operations"
      :required    ["text" "operation"]}
     {"text"      (lj/string "The text to process")
      "operation" (lj/enum "The string operation to perform"
                           ["uppercase" "lowercase" "reverse" "length"])})
    "Performs string manipulation operations")
   string-tool))

;;; Agent module demonstrating tools functionality
(aor/defagentmodule ToolsAgentModule
  [topology]

  ;; Declare OpenAI model
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "fake-key-for-demo"))
         (.modelName "gpt-4o-mini")
         .build)))

  ;; Create tools agent with our tool definitions
  (tools/new-tools-agent
   topology
   "ToolsAgent"
   [CALCULATOR-TOOL STRING-TOOL])

  ;; Create a coordinator agent that uses OpenAI with tools
  (->
    topology
    (aor/new-agent "ToolsCoordinator")

    ;; Node that sends natural language prompts to OpenAI and processes tool calls
    (aor/node
     "chat-with-tools"
     nil
     (fn chat-with-tools-fn [agent-node prompts]
       (let [model       (aor/get-agent-object agent-node "openai-model")
             tools-agent (aor/agent-client agent-node "ToolsAgent")
             results     (atom [])]

         (doseq [^String prompt prompts]
           ;; Send prompt to OpenAI model with tools
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. prompt)]
                                        {:tools [CALCULATOR-TOOL STRING-TOOL]}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               ;; Execute tools and get results
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (swap! results conj
                   {:prompt       prompt
                    :tool-calls   (count tool-calls)
                    :tool-results tool-results}))
               (swap! results conj
                 {:prompt     prompt
                  :response   (.text ai-message)
                  :tool-calls 0}))))

         (aor/result! agent-node
                      {:prompts-count (count prompts)
                       :results       @results}))))))

(defn -main
  "Run the tools agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToolsAgentModule {:tasks 1 :threads 1})
    (let [manager     (aor/agent-manager
                       ipc
                       (rama/get-module-name ToolsAgentModule))
          coordinator (aor/agent-client manager "ToolsCoordinator")
          prompts     ["What is 15 plus 25?"
                       "Calculate 7 times 8"
                       "Divide 100 by 4"
                       "Convert 'Hello World' to uppercase"
                       "Reverse the text 'ReverseMe'"
                       "How many characters are in 'Count Characters'?"]
          result      (aor/agent-invoke coordinator prompts)]

      ;; Create natural language prompts that will trigger tool usage
      (println "Prompts processed:" (:prompts-count result))

      (println "\nDetailed results:")
      (doseq [[idx prompt-result] (map-indexed vector (:results result))]
        (println (format "\n[%d] Prompt: \"%s\"" (inc idx) (:prompt prompt-result)))
        (if (> (:tool-calls prompt-result 0) 0)
          (do
            (println (format "    Tool calls: %d" (:tool-calls prompt-result)))
            (println "    Tool results:"
                     (mapv
                      #(.text ^ToolExecutionResultMessage %)
                      (:tool-results prompt-result))))
          (println "    Direct response:" (:response prompt-result)))))))

(comment
  (-main))
