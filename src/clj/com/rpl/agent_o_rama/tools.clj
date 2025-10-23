(ns com.rpl.agent-o-rama.tools
  "Tools integration for AI agents using LangChain4j tool specifications.

  This namespace provides utilities for creating tool specifications and tool agents
  that can be used with AI models for function calling. Tools allow AI agents to
  interact with external systems, perform calculations, and execute custom logic
  during conversation.

  Key concepts:
  - Tool specifications define the interface for tools (name, parameters, description)
  - Tool info combines specifications with implementation functions
  - Tool agents execute tool calls and return results to AI models
  - Error handlers control how tool execution failures are handled

  Example:
    (def calculator-tool
      (tool-info
        (tool-specification
          \"add\"
          (lj/object {\"a\" (lj/number \"first number\")
                     \"b\" (lj/number \"second number\")})
          \"Add two numbers together\")
        (fn [args] (+ (get args \"a\") (get args \"b\")))))

    (new-tools-agent topology \"calculator\" [calculator-tool])"
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.tools-impl :as tools-impl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs])
  (:import
   [dev.langchain4j.agent.tool
    ToolSpecification]))

(defn tool-specification
  "Creates a tool specification that defines the interface for a tool.

  Tool specifications describe how AI models should call tools, including the
  tool name, parameter schema, and description. They are used with LangChain4j
  to enable function calling in AI conversations.

  Args:
    name - String name of the tool (must be unique within a tool agent)
    parameters-json-schema - JSON schema defining the tool's parameters
    description - String description of what the tool does (optional)

  Returns:
    ToolSpecification - LangChain4j tool specification instance

  Example:
    (tool-specification
      \"calculate\"
      (lj/object {\"expression\" (lj/string \"mathematical expression to evaluate\")})
      \"Evaluates a mathematical expression\")"
  ([name parameters-json-schema]
   (tool-specification name parameters-json-schema nil))
  ([name parameters-json-schema description]
   (-> (ToolSpecification/builder)
       (.name name)
       (.parameters parameters-json-schema)
       (.description description)
       .build)))

(defn tool-info
  "Creates a tool info that combines a tool specification with its implementation function.

  Tool info is the complete definition of a tool, including both its interface
  (specification) and implementation (function). Tools can optionally include
  context from the agent node for advanced functionality.

  Args:
    tool-specification - ToolSpecification instance created with [[tool-specification]]
    tool-fn - Function that implements the tool logic. Takes either:
              - (args) - Just the parsed arguments map
              - (agent-node caller-data args) - Agent node, caller data, and arguments
    options - Optional map with configuration:
      :include-context? - Boolean, whether to pass agent-node and caller-data to tool-fn (default false)

  Returns:
    ToolInfo - Complete tool definition for use with [[new-tools-agent]]

  Example:
    (tool-info
      (tool-specification \"add\" params \"Add two numbers\")
      (fn [args] (+ (get args \"a\") (get args \"b\"))))

    ;; With context access
    (tool-info
      (tool-specification \"context-aware\" params \"Uses agent context\")
      (fn [agent-node caller-data args]
        (let [store (aor/get-store agent-node \"$$cache\")]
          (aor/put! store \"key\" (get args \"value\"))))
      {:include-context? true})"
  ([tool-specification tool-fn]
   (tool-info tool-specification tool-fn nil))
  ([tool-specification tool-fn options]
   (let [options (merge {:include-context? false} options)]
     (h/validate-options! tool-specification
                          options
                          {:include-context? h/boolean-spec})
     (when-not (ifn? tool-fn)
       (throw (h/ex-info "Invalid tool function" {:type (class tool-fn)})))
     (when-not (instance? ToolSpecification tool-specification)
       (throw (h/ex-info "Invalid tool specification"
                         {:type (class tool-specification)})))
     (aor-types/->ToolInfoImpl tool-specification
                               tool-fn
                               (:include-context? options))
   )))

(defn error-handler-static-string
  "Creates an error handler that always returns a static string for any exception.

  This is useful for providing user-friendly error messages back to a model when tool execution
  fails, rather than exposing technical exception details.

  Args:
    s - String to return for any tool execution error

  Returns:
    Function - Error handler that takes an exception and returns the string

  Example:
    (new-tools-agent topology \"calculator\" tools
      {:error-handler (error-handler-static-string \"Something went wrong. Please try again.\")})"
  [s]
  (constantly s))

(defn error-handler-rethrow
  "Creates an error handler that re-throws exceptions without modification.

  This is useful when you want tool execution errors to propagate up to the
  calling agent, allowing it to handle the error in its own logic.

  Returns:
    Function - Error handler that re-throws any exception

  Example:
    (new-tools-agent topology \"calculator\" tools
      {:error-handler (error-handler-rethrow)})"
  []
  (fn [e] (throw e)))

(defn error-handler-default
  "Creates the default error handler that formats exceptions as user-friendly messages.

  This handler converts exceptions to readable error messages with a standard
  format: \"Error: <exception details>\nPlease fix your mistakes.\"

  Returns:
    Function - Error handler that formats exceptions as strings

  Example:
    (new-tools-agent topology \"calculator\" tools
      {:error-handler (error-handler-default)})"
  []
  (fn [e]
    (tools-impl/tool-error-string (h/throwable->str e))))

(defn error-handler-by-type
  "Creates an error handler that handles different exception types differently.

  This handler matches exceptions by type and applies the corresponding handler
  function. If no type matches, the exception is re-thrown.

  Args:
    tuples - Vector of [exception-type handler-function] pairs

  Returns:
    Function - Error handler that dispatches by exception type

  Example:
    (new-tools-agent topology \"calculator\" tools
      {:error-handler (error-handler-by-type
                        [[ArithmeticException (fn [e] \"Math error occurred\")]
                         [IllegalArgumentException (fn [e] \"Invalid input provided\")]])})"
  [tuples]
  (fn [e]
    (if-let [ret (reduce
                  (fn [_ [ex-type afn]]
                    (when (instance? ex-type e)
                      (reduced (afn e))))
                  nil
                  tuples)]
      ret
      (throw e)
    )))

(defn error-handler-static-string-by-type
  "Creates an error handler that returns static strings for different exception types.

  This is a convenience function that combines [[error-handler-by-type]] with
  [[error-handler-static-string]] to provide simple string responses for
  different exception types.

  Args:
    tuples - Vector of [exception-type string] pairs

  Returns:
    Function - Error handler that returns strings based on exception type

  Example:
    (new-tools-agent topology \"calculator\" tools
      {:error-handler (error-handler-static-string-by-type
                        [[ArithmeticException \"Math error occurred\"]
                         [IllegalArgumentException \"Invalid input provided\"]
                         [ClassCastException \"Type conversion failed\"]])})"
  [tuples]
  (let [tuples (transform [(view vec) ALL LAST] (fn [s] (constantly s)) tuples)]
    (error-handler-by-type tuples)))

(defn new-tools-agent
  "Creates a tools agent that can execute tool calls from AI models.

  A tools agent is a special type of agent designed to execute tool calls
  requested by AI models. It processes batches of tool execution requests,
  executes the corresponding tool functions, and returns results back to
  the calling agent.

  The agent uses aggregation to collect results from parallel tool executions
  and returns them as a vector of ToolExecutionResultMessage objects.

  Args:
    topology - agent topology instance
    name - String name for the tools agent
    tools - Collection of ToolInfo instances created with [[tool-info]]
    options - Optional map with configuration:
      :error-handler - Function that handles tool execution errors (default: [[error-handler-default]])

  Example:
    (let [calculator-tool
          (tool-info
            (tool-specification
              \"add\"
              (lj/object {\"a\" (lj/number \"first number\")
                         \"b\" (lj/number \"second number\")})
              \"Add two numbers together\")
            (fn [args] (+ (get args \"a\") (get args \"b\"))))]
      (new-tools-agent topology \"calculator\" [calculator-tool]))

    ;; With custom error handling
    (new-tools-agent topology \"robust-calculator\" tools
      {:error-handler (error-handler-static-string \"Calculation failed\")})"
  ([topology name tools]
   (new-tools-agent topology name tools nil))
  ([topology name tools options]
   (tools-impl/hook:new-tools-agent-options name options)
   (let [options (merge {:error-handler (error-handler-default)}
                        options)]
     (h/validate-options! name
                          options
                          {:error-handler h/fn-spec})
     (-> topology
         (c/new-agent name)
         (c/agg-start-node
          "begin"
          "tool"
          (fn begin
            ([agent-node requests]
             (begin agent-node requests nil))
            ([agent-node requests caller-data]
             (doseq [r requests]
               (c/emit! agent-node "tool" r caller-data)))))
         (c/node
          "tool"
          "agg-results"
          (tools-impl/mk-tool-fn tools (:error-handler options)))
         (c/agg-node
          "agg-results"
          nil
          aggs/+vec-agg
          (fn [agent-node agg-state _]
            (c/result! agent-node agg-state)))
     ))))