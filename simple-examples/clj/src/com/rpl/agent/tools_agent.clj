(ns com.rpl.agent.tools-agent
  "Demonstrates LangChain4j tools integration with new-tools-agent.

  Features demonstrated:
  - new-tools-agent: Create specialized agent for tool execution
  - tool-specification: Define tool schemas for LangChain4j
  - Tool function implementation and error handling
  - Parallel tool execution with aggregation"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]))

;;; Tool function definitions
(defn calculate-tool
  "Simple calculator tool that performs basic arithmetic"
  [request]
  (let [args      (.arguments request)
        operation (.get args "operation")
        a         (Double/parseDouble (.get args "a"))
        b         (Double/parseDouble (.get args "b"))
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
  [request]
  (let [args      (.arguments request)
        text      (.get args "text")
        operation (.get args "operation")]
    (case operation
      "uppercase" (.toUpperCase text)
      "lowercase" (.toLowerCase text)
      "reverse" (str/reverse text)
      "length" (str (count text))
      "Error: Unknown string operation")))

(defn info-tool
  "Information tool that provides system information"
  [request]
  (let [args      (.arguments request)
        info-type (.get args "type")]
    (case info-type
      "time" (str (System/currentTimeMillis))
      "memory" (str "Total: " (.totalMemory (Runtime/getRuntime)) " bytes")
      "java-version" (System/getProperty "java.version")
      "Error: Unknown info type")))

;;; Tool specifications for LangChain4j
(def CALCULATOR-TOOL
  (tools/tool-info
   "calculator"
   calculate-tool
   (tools/tool-specification
    "calculator"
    "{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\",\"enum\":[\"add\",\"subtract\",\"multiply\",\"divide\"]},\"a\":{\"type\":\"number\"},\"b\":{\"type\":\"number\"}},\"required\":[\"operation\",\"a\",\"b\"]}"
    "Performs basic arithmetic operations on two numbers")))

(def STRING-TOOL
  (tools/tool-info
   "string-processor"
   string-tool
   (tools/tool-specification
    "string-processor"
    "{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"},\"operation\":{\"type\":\"string\",\"enum\":[\"uppercase\",\"lowercase\",\"reverse\",\"length\"]}},\"required\":[\"text\",\"operation\"]}"
    "Performs string manipulation operations")))

(def INFO-TOOL
  (tools/tool-info
   "system-info"
   info-tool
   (tools/tool-specification
    "system-info"
    "{\"type\":\"object\",\"properties\":{\"type\":{\"type\":\"string\",\"enum\":[\"time\",\"memory\",\"java-version\"]}},\"required\":[\"type\"]}"
    "Provides system information")))

;;; Agent module demonstrating tools functionality
(aor/defagentmodule ToolsAgentModule
  [topology]

  ;; Create tools agent with our tool definitions
  (tools/new-tools-agent topology
                         "ToolsAgent"
                         [CALCULATOR-TOOL STRING-TOOL INFO-TOOL])

  ;; Create a coordinator agent that uses tools
  (->
    topology
    (aor/new-agent "ToolsCoordinator")

    ;; Node that prepares and sends tool requests
    (aor/node
     "coordinate-tools"
     nil
     (fn [agent-node requests]
       (let [tools-agent (aor/agent-client agent-node "ToolsAgent")]

         (println (format "Executing %d tool requests" (count requests)))

         ;; Send requests to tools agent and get results
         (let [results (aor/agent-invoke tools-agent requests)]

           (println (format "Received %d tool results" (count results)))

           (aor/result! agent-node
                        {:action         "tools-execution-complete"
                         :requests-count (count requests)
                         :results-count  (count results)
                         :results        results
                         :processed-at   (System/currentTimeMillis)})))))))

(defn create-tool-request
  "Helper to create ToolExecutionRequest objects"
  [tool-name args]
  (ToolExecutionRequest/builder
   (.name tool-name)
   (.arguments args)
   (.build)))

(defn -main
  "Run the tools agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToolsAgentModule {:tasks 2 :threads 2})

    (let [manager     (aor/agent-manager ipc
                                         (rama/get-module-name
                                          ToolsAgentModule))
          coordinator (aor/agent-client manager "ToolsCoordinator")]

      (println "Tools Agent Example:")
      (println "Executing multiple tools in parallel with result aggregation")

      ;; Create tool execution requests
      (let [requests [;; Calculator requests
                      (create-tool-request "calculator"
                                           {"operation" "add"
                                            "a"         "15"
                                            "b"         "25"})
                      (create-tool-request "calculator"
                                           {"operation" "multiply"
                                            "a"         "7"
                                            "b"         "8"})
                      (create-tool-request "calculator"
                                           {"operation" "divide"
                                            "a"         "100"
                                            "b"         "4"})

                      ;; String processing requests
                      (create-tool-request "string-processor"
                                           {"text"      "Hello World"
                                            "operation" "uppercase"})
                      (create-tool-request "string-processor"
                                           {"text"      "ReverseMe"
                                            "operation" "reverse"})
                      (create-tool-request "string-processor"
                                           {"text"      "Count Characters"
                                            "operation" "length"})

                      ;; System info requests
                      (create-tool-request "system-info" {"type" "time"})
                      (create-tool-request "system-info" {"type" "memory"})
                      (create-tool-request "system-info"
                                           {"type" "java-version"})]]

        (let [result (aor/agent-invoke coordinator requests)]
          (println "\nResults:")
          (println "  Action:" (:action result))
          (println "  Requests processed:" (:requests-count result))
          (println "  Results received:" (:results-count result))

          (println "\nDetailed results:")
          (doseq [[idx tool-result] (map-indexed vector (:results result))]
            (println (format "  [%d] %s" (inc idx) tool-result)))))

      (println "\nNotice how:")
      (println "- Tools execute in parallel and results are aggregated")
      (println "- Each tool has its own schema and validation")
      (println "- Error handling is built into the tools framework")
      (println "- Complex tool orchestration is simplified"))))
