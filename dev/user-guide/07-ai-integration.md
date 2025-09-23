# AI Integration

Connect your agents to AI models and external tools using [LangChain4j integration](../terms/langchain4j-integration.md), [tool calling](../terms/tool-calling.md), and [tools sub-agents](../terms/tools-sub-agent.md). This chapter covers chat models, structured outputs, streaming, and tool execution.

> **Reference**: See [LangChain4j Integration](../terms/langchain4j-integration.md) and [Tool Calling](../terms/tool-calling.md) documentation for comprehensive details.

## LangChain4j Integration

[LangChain4j integration](../terms/langchain4j-integration.md) provides seamless access to AI models, structured output parsing, and tool calling within your agent execution flows.

### Basic Chat Model

Set up an AI model as an agent object:

```clojure
(aor/defagentmodule AIAgentModule
  [topology]

  ;; API key as static object
  (aor/declare-agent-object topology "openai-api-key"
    (System/getenv "OPENAI_API_KEY"))

  ;; AI model as builder object
  (aor/declare-agent-object-builder topology "openai-model"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (aor/get-agent-object setup "openai-api-key"))
          (.modelName "gpt-4o-mini")
          (.temperature 0.7)
          .build)))

  ;; Agent using the AI model
  (-> topology
      (aor/new-agent "ChatAgent")
      (aor/node "chat" nil
        (fn [agent-node messages]
          (let [model (aor/get-agent-object agent-node "openai-model")
                response (lc4j/chat model (lc4j/chat-request messages))]
            (aor/result! agent-node (.text (.aiMessage response))))))))
```

### Structured Output

Generate structured data with JSON schemas:

```clojure
(aor/node "structured-response" nil
  (fn [agent-node user-query]
    (let [model (aor/get-agent-object agent-node "openai-model")
          ;; Define JSON schema for structured output
          schema (lj/object
                   {:description "User analysis result"}
                   {"intent" (lj/string "The user's intent")
                    "confidence" (lj/number "Confidence score 0-1")
                    "entities" (lj/array "Extracted entities" (lj/string))})
          response (lc4j/chat model
                             (lc4j/chat-request
                               [(SystemMessage/from "Analyze user queries")
                                (UserMessage/from user-query)]
                               {:response-format (lj/json-schema schema)}))]
      (aor/result! agent-node (.text (.aiMessage response))))))
```

### Streaming AI Responses

Stream AI model responses in real-time using streaming_langchain4j_agent pattern:

```clojure
(aor/node "streaming-chat" nil
  (fn [agent-node messages]
    (let [model (aor/get-agent-object agent-node "streaming-model")
          ;; Create streaming request
          response (lc4j/chat model (lc4j/chat-request messages {:stream true}))]

      ;; Stream chunks as they arrive
      (doseq [token (get-streaming-tokens response)]
        (aor/stream-chunk! agent-node
          {:type "token"
           :content token
           :timestamp (System/currentTimeMillis)}))

      ;; Return complete response
      (aor/result! agent-node (.text (.aiMessage response))))))
```

## Tool Calling

[Tool calling](../terms/tool-calling.md) enables AI models to execute external functions and APIs based on their decisions.

### Define Tools

Tools are functions with JSON schema specifications:

```clojure
(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "web-search"
     (lj/object
      {:description "Search the web for information"
       :required ["terms"]}
      {"terms" (lj/string "The search terms")})
     "Search the web using Tavily")
    (fn [agent-node _ arguments]
      (let [tavily (aor/get-agent-object agent-node "tavily")
            terms (get arguments "terms")
            results (.search tavily (WebSearchRequest/from terms 3))]
        (str/join "\\n---\\n" (map #(.text %) (.toDocuments results)))))
    {:include-context? true})])
```

### Tools Sub-Agent

[Tools sub-agent](../terms/tools-sub-agent.md) executes tool functions with automatic aggregation:

```clojure
(aor/defagentmodule ToolsModule
  [topology]

  ;; Declare tools sub-agent
  (tools/new-tools-agent topology "tools" TOOLS)

  ;; Main agent that uses tools
  (-> topology
      (aor/new-agent "ReActAgent")
      (aor/node "chat" "chat"
        (fn [agent-node messages]
          (let [model (aor/get-agent-object agent-node "openai")
                tools-client (aor/agent-client agent-node "tools")
                response (lc4j/chat model (lc4j/chat-request messages {:tools TOOLS}))
                ai-message (.aiMessage response)
                tool-calls (vec (.toolExecutionRequests ai-message))]

            (if (seq tool-calls)
              ;; Tools needed - execute and continue reasoning
              (let [tool-results (aor/agent-invoke tools-client tool-calls)
                    updated-messages (into (conj messages ai-message) tool-results)]
                (aor/emit! agent-node "chat" updated-messages))
              ;; No tools needed - return final answer
              (aor/result! agent-node (.text ai-message))))))))
```

### Tool Execution Flow

1. AI model decides to call tools
2. Tool specifications are sent to tools sub-agent
3. Tools sub-agent executes the functions
4. Results are returned to the main agent
5. AI model processes results and continues

## Complete AI Integration Example

Here's a complete ReAct agent with web search:

```clojure
(ns my-app.react-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools])
  (:import
   [dev.langchain4j.data.message SystemMessage UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]
   [dev.langchain4j.web.search.tavily TavilyWebSearchEngine]))

;; Tool implementations
(defn- tavily-search [agent-node _ arguments]
  (let [terms (get arguments "terms")
        tavily (aor/get-agent-object agent-node "tavily")
        results (.search tavily (WebSearchRequest/from terms 3))]
    (str/join "\\n---\\n" (mapv #(.text %) (.toDocuments results)))))

;; Tool specifications
(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "tavily"
     (lj/object
      {:description "Search the web for current information"
       :required ["terms"]}
      {"terms" (lj/string "The terms to search for")})
     "Search the web")
    tavily-search
    {:include-context? true})])

(aor/defagentmodule ReActModule
  [topology]

  ;; API keys
  (aor/declare-agent-object topology "openai-api-key"
    (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object topology "tavily-api-key"
    (System/getenv "TAVILY_API_KEY"))

  ;; AI model
  (aor/declare-agent-object-builder topology "openai"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (aor/get-agent-object setup "openai-api-key"))
          (.modelName "gpt-4o-mini")
          (.temperature 0.7)
          .build)))

  ;; Search engine
  (aor/declare-agent-object-builder topology "tavily"
    (fn [setup]
      (-> (TavilyWebSearchEngine/builder)
          (.apiKey (aor/get-agent-object setup "tavily-api-key"))
          .build)))

  ;; Tools sub-agent
  (tools/new-tools-agent topology "tools" TOOLS)

  ;; Main ReAct agent
  (-> topology
      (aor/new-agent "ReActAgent")
      (aor/node "chat" "chat"
        (fn [agent-node messages]
          (let [model (aor/get-agent-object agent-node "openai")
                tools-client (aor/agent-client agent-node "tools")
                response (lc4j/chat model (lc4j/chat-request messages {:tools TOOLS}))
                ai-message (.aiMessage response)
                tool-calls (vec (.toolExecutionRequests ai-message))]

            (if (seq tool-calls)
              ;; Execute tools and continue reasoning
              (let [tool-results (aor/agent-invoke tools-client tool-calls)
                    updated-messages (into (conj messages ai-message) tool-results)]
                (aor/emit! agent-node "chat" updated-messages))
              ;; Return final answer
              (aor/result! agent-node (.text ai-message))))))))
```

## Advanced Patterns

### Multi-Model Agents
```clojure
;; Use different models for different tasks
(aor/node "analyze-and-generate" nil
  (fn [agent-node input]
    (let [analyzer (aor/get-agent-object agent-node "claude-model")
          generator (aor/get-agent-object agent-node "gpt-model")
          analysis (lc4j/chat analyzer (analyze-prompt input))
          generation (lc4j/chat generator (generate-prompt analysis))]
      (aor/result! agent-node {:analysis analysis :generation generation}))))
```

### Context Management
```clojure
;; Maintain conversation context across agent executions
(aor/node "contextual-chat" nil
  (fn [agent-node user-message]
    (let [store (aor/get-store agent-node "conversations")
          user-id (:user-id user-message)
          context (store/get store user-id [])
          model (aor/get-agent-object agent-node "openai")
          messages (conj context (UserMessage/from (:text user-message)))
          response (lc4j/chat model (lc4j/chat-request messages))
          updated-context (conj messages (.aiMessage response))]
      ;; Store updated context
      (store/put! store user-id updated-context)
      (aor/result! agent-node (.text (.aiMessage response))))))
```

### Error Handling
```clojure
;; Handle AI model errors gracefully
(aor/node "robust-ai-call" nil
  (fn [agent-node prompt]
    (try
      (let [model (aor/get-agent-object agent-node "openai")
            response (lc4j/chat model (lc4j/chat-request [(UserMessage/from prompt)]))]
        (aor/result! agent-node (.text (.aiMessage response))))
      (catch Exception e
        (aor/result! agent-node
          {:error "AI model unavailable"
           :fallback "Please try again later"})))))
```

## Key Concepts

You've learned AI integration patterns:

1. **[LangChain4j Integration](../terms/langchain4j-integration.md)**: AI model library integration
2. **[Tool Calling](../terms/tool-calling.md)**: External function execution
3. **[Tools Sub-Agent](../terms/tools-sub-agent.md)**: Tool execution agents
4. **Structured Output**: JSON schema-based responses
5. **Streaming**: Real-time AI model responses

These patterns enable sophisticated AI-driven agent workflows.

## What's Next

You can integrate AI models and tools into your agents. Finally, learn [Experimentation](08-experimentation.md) to test, evaluate, and improve your agents with datasets and evaluators.