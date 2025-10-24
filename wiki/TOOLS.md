# Tools

Tools agents are specialized agents that execute LangChain4j tool functions with automatic parallel execution and result aggregation. They enable AI models to interact with external functions, APIs, and services through structured tool calling.

## Table of Contents

1. [What are Tools Agents?](#what-are-tools-agents)
2. [Creating Tools Agents](#creating-tools-agents)
3. [Invoking Tools Agents](#invoking-tools-agents)
4. [Parallel Tool Execution](#parallel-tool-execution)
5. [Error Handling](#error-handling)

## What are Tools Agents?

Tools agents are a special type of agent designed specifically for executing LangChain4j tools. When an AI model (like GPT-4) decides it needs to call one or more tools to answer a query, the tools agent:

1. **Receives tool execution requests** from the AI model
2. **Executes tools in parallel** if multiple tools are requested
3. **Aggregates results** automatically
4. **Returns structured responses** that can be sent back to the AI model

Tools agents are invoked as subagents from other agents.

## Creating Tools Agents

To create a tools agent, you define your tools with specifications and implementations, then use `newToolsAgent` / `new-tools-agent`.

### Defining Tools

Each tool needs:
1. **Tool specification**: Describes the tool's name, parameters, and purpose (for the AI model)
2. **Implementation function**: The actual code that executes when the tool is called

#### Java API

```java
import com.rpl.agentorama.*;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolParameters;
import dev.langchain4j.agent.tool.JsonSchemaProperty;

public class ToolsModule extends AgentModule {
  // Define calculator tool specification
  static ToolSpecification calcSpec = ToolSpecification.builder()
      .name("calculator")
      .description("Performs basic arithmetic operations")
      .parameters(ToolParameters.builder()
          .required("operation", "a", "b")
          .properties(Map.of(
              "operation", JsonSchemaProperty.enums("add", "subtract", "multiply", "divide"),
              "a", JsonSchemaProperty.number("First number"),
              "b", JsonSchemaProperty.number("Second number")
          ))
          .build())
      .build();

  // Create tool info combining spec and implementation
  static ToolInfo calcTool = ToolInfo.create(calcSpec, (Map args) -> {
    String op = (String) args.get("operation");
    double a = ((Number) args.get("a")).doubleValue();
    double b = ((Number) args.get("b")).doubleValue();

    double result = switch (op) {
      case "add" -> a + b;
      case "subtract" -> a - b;
      case "multiply" -> a * b;
      case "divide" -> b != 0 ? a / b : Double.NaN;
      default -> Double.NaN;
    };

    return "" + result;
  });

  static List allTools = Arrays.asList(calcTool);


  @Override
  protected void defineAgents(AgentTopology topology) {
    // Create tools agent
    topology.newToolsAgent("ToolsAgent", allTools);
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor]
         '[com.rpl.agent-o-rama.tools :as tools]
         '[com.rpl.agent-o-rama.langchain4j.json :as lj])

(defn calculate-tool
  "Calculator implementation function"
  [args]
  (let [operation (args "operation")
        a (args "a")
        b (args "b")
        result (case operation
                 "add" (+ a b)
                 "subtract" (- a b)
                 "multiply" (* a b)
                 "divide" (if (zero? b) "Error: Division by zero" (/ a b))
                 "Error: Unknown operation")]
    (str result)))

;; Define tool specification
(def CALCULATOR-TOOL
  (tools/tool-info
   (tools/tool-specification
    "calculator"
    (lj/object
     {:description "Parameters for calculator operations"
      :required ["operation" "a" "b"]}
     {"operation" (lj/enum "The arithmetic operation to perform"
                           ["add" "subtract" "multiply" "divide"])
      "a" (lj/number "The first number")
      "b" (lj/number "The second number")})
    "Performs basic arithmetic operations on two numbers")
   calculate-tool))

(def TOOLS [CALCULATOR-TOOL])

(aor/defagentmodule ToolsModule
  [topology]
  ;; Create tools agent
  (tools/new-tools-agent
   topology
   "ToolsAgent"
   TOOLS))
```

### Multiple Tools

You can define multiple tools in a single tools agent. Each tool is independent and can be called by the AI model as needed.

#### Java API

```java
// Define multiple tools
ToolInfo calcTool = ToolInfo.create(calcSpec, calcImpl);
ToolInfo stringTool = ToolInfo.create(stringSpec, stringImpl);
ToolInfo weatherTool = ToolInfo.create(weatherSpec, weatherImpl);
List allTools = Arrays.asList(calcTool, stringTool, weatherTool);

// Create tools agent with all tools
topology.newToolsAgent("ToolsAgent", allTools);
```

#### Clojure API

```clojure
(def TOOLS [CALCULATOR-TOOL STRING-TOOL WEATHER-TOOL])

;; Create tools agent with multiple tools
(tools/new-tools-agent
 topology
 "ToolsAgent"
 TOOLS)
```

## Invoking Tools Agents

Tools agents are invoked as subagents from other agents. The typical pattern is:

1. **Main agent** receives user input and calls AI model with tools registered in the request
2. **AI model** processes input and decides which tools to call
3. **Main agent** sends tool execution request from the AI model to the tools agent
4. **Tools agent** executes the requested tools
5. **Results** are sent back to the AI model for the next response, possibly looping for additional tool calls

### Basic Invocation Pattern

#### Java API

```java
public class MyAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    // ... declare tools agent ...

    topology.newAgent("Coordinator")
            .node("process", null, (AgentNode agentNode, String userQuery) -> {
              // Get AI model
              ChatModel model = agentNode.getAgentObject("openai-model");

              // Send query to AI model with tool specifications
              ChatResponse response = model.chat(
                  ChatRequest.builder()
                      .messages(List.of(new UserMessage(userQuery)))
                      .toolSpecifications(calcSpec, stringSpec)
                      .build());

              // Check if AI wants to call tools
              List<ToolExecutionRequest> toolCalls =
                  response.aiMessage().toolExecutionRequests();

              if (!toolCalls.isEmpty()) {
                // Get tools agent client
                AgentClient toolsAgent = agentNode.getAgentClient("ToolsAgent");

                // Execute tools (they run in parallel automatically)
                List<ToolExecutionResultMessage> results =
                    (List<ToolExecutionResultMessage>) toolsAgent.invoke(toolCalls);

                // Build message list with original query, AI response, and tool results
                List messages = new ArrayList();
                messages.add(new UserMessage(userQuery));
                messages.add(response.aiMessage());
                messages.addAll(results);

                // Send results back to AI model for final response
                ChatResponse finalResponse = model.chat(
                    ChatRequest.builder()
                        .messages(messages)
                        .build());

                agentNode.result(finalResponse.aiMessage().text());
              } else {
                // AI didn't need tools, return direct response
                agentNode.result(response.aiMessage().text());
              }
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule MyAgentModule
  [topology]
  ;; ... declare tools agent ...

  (-> topology
      (aor/new-agent "Coordinator")
      (aor/node
       "process"
       nil
       (fn [agent-node user-query]
         ;; Get AI model
         (let [model (aor/get-agent-object agent-node "openai-model")

               ;; Send query to AI model with tool specifications
               response (lc4j/chat model
                                   (lc4j/chat-request
                                    [(UserMessage. user-query)]
                                    ;; same argument here as provided to the tools agent
                                    {:tools TOOLS}))
               ai-message (.aiMessage response)
               tool-calls (vec (.toolExecutionRequests ai-message))]

           (if (seq tool-calls)
             ;; AI wants to call tools
             (let [tools-agent (aor/agent-client agent-node "ToolsAgent")

                   ;; Execute tools (they run in parallel automatically)
                   results (aor/agent-invoke tools-agent tool-calls)

                   ;; Send results back to AI model for final response
                   final-response (lc4j/chat model
                                             (lc4j/chat-request
                                              (concat
                                              [(UserMessage. user-query) ai-message]
                                               results)))]
               (aor/result! agent-node (.text (.aiMessage final-response))))

             ;; AI didn't need tools, return direct response
             (aor/result! agent-node (.text ai-message))))))))
```

## Error Handling

Tools agents support custom error handling for tool execution failures. You can configure how tool errors should be handled, such as returning a static error message to the AI model, re-throwing the error back to the invoking agent, or providing different responses based on exception type.

By default, if a tool throws an exception, a formatted error message is returned as the tool result.

### Error Handler Options

Agent-o-rama provides several built-in error handlers:

1. **Default Handler**: Formats exceptions as user-friendly error messages (used if no handler specified)
2. **Static String**: Returns a fixed string for any error
3. **Rethrow**: Re-throws the exception to the calling agent
4. **Static String by Type**: Returns different strings based on exception type
5. **Function by Type**: Applies different handler functions based on exception type


### Static String Handler

Returns a fixed error message for any tool execution failure. Useful for providing a simple, user-friendly message to the AI model.

#### Java API

```java
// Return static string for all errors
topology.newToolsAgent(
    "ToolsAgent",
    allTools,
    ToolsAgentOptions.errorHandlerStaticString("Tool execution failed. Please try again."));
```

#### Clojure API

```clojure
(tools/new-tools-agent
 topology
 "ToolsAgent"
 TOOLS
 {:error-handler (tools/error-handler-static-string
                  "Tool execution failed. Please try again.")})
```

### Rethrow Handler

Re-throws exceptions, allowing the agent to handle the error in its own logic.

#### Java API

```java
// Rethrow errors to calling agent
topology.newToolsAgent(
    "ToolsAgent",
    allTools,
    ToolsAgentOptions.errorHandlerRethrow());
```

#### Clojure API

```clojure
(tools/new-tools-agent
 topology
 "ToolsAgent"
 TOOLS
 {:error-handler (tools/error-handler-rethrow)})
```

### Static String by Type Handler

Returns different static strings based on exception type. If the exception type doesn't match any handler, it is re-thrown.

#### Java API

```java
import com.rpl.agentorama.ToolsAgentOptions.StaticStringHandler;

// Different messages for different exception types
topology.newToolsAgent(
    "ToolsAgent",
    allTools,
    ToolsAgentOptions.errorHandlerStaticStringByType(
        StaticStringHandler.create(ArithmeticException.class, "Math error occurred"),
        StaticStringHandler.create(IllegalArgumentException.class, "Invalid input provided"),
        StaticStringHandler.create(ClassCastException.class, "Type conversion failed")
    ));
```

#### Clojure API

```clojure
(tools/new-tools-agent
 topology
 "ToolsAgent"
 TOOLS
 {:error-handler (tools/error-handler-static-string-by-type
                  [[ArithmeticException "Math error occurred"]
                   [IllegalArgumentException "Invalid input provided"]
                   [ClassCastException "Type conversion failed"]])})
```

### Function by Type Handler

Applies different handler functions based on exception type. Each handler receives the exception and returns a string. If the exception type doesn't match any handler, it is re-thrown.

#### Java API

```java
import com.rpl.agentorama.ToolsAgentOptions.FunctionHandler;

// Custom logic for different exception types
topology.newToolsAgent(
    "ToolsAgent",
    allTools,
    ToolsAgentOptions.errorHandlerByType(
        FunctionHandler.create(
            ArithmeticException.class,
            (ArithmeticException e) -> "Math error: " + e.getMessage()),
        FunctionHandler.create(
            IllegalArgumentException.class,
            (IllegalArgumentException e) -> "Invalid: " + e.getMessage())
    ));
```

#### Clojure API

```clojure
(tools/new-tools-agent
 topology
 "ToolsAgent"
 TOOLS
 {:error-handler (tools/error-handler-by-type
                  [[ArithmeticException (fn [e] (str "Math error: " (.getMessage e)))]
                   [IllegalArgumentException (fn [e] (str "Invalid: " (.getMessage e)))]])})
```
