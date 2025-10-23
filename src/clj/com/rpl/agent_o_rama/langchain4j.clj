(ns com.rpl.agent-o-rama.langchain4j
  "Convenience functions for working with LangChain4j model APIs in agent nodes.
  
  This namespace provides a small subset of common functionality
  wrapped in Clojure-friendly functions. It focuses on the most frequently used
  operations for chat models, tool calling, and JSON response formatting.
  
  For advanced LangChain4j features not covered here, you can directly use
  the LangChain4j Java API within your agent node functions."
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [dev.langchain4j.data.message
    UserMessage]
   [dev.langchain4j.model.chat
    ChatModel]
   [dev.langchain4j.model.chat.request
    ChatRequest
    ResponseFormat
    ResponseFormatType
    ToolChoice]
   [dev.langchain4j.model.chat.response
    ChatResponse]
   [dev.langchain4j.model.chat.request.json
    JsonSchema]
   [java.util
    List]))

(defn basic-chat
  "Performs a simple chat interaction with a model using a string prompt.
  
  This is the simplest way to interact with a chat model. The prompt is
  automatically converted to a UserMessage and sent to the model.
  
  Args:
    model - ChatModel instance (obtained from [[get-agent-object]])
    prompt - String prompt to send to the model
  
  Returns:
    String - The model's response text
  
  Example:
    (let [model (aor/get-agent-object agent-node \"openai-model\")]
      (lc4j/basic-chat model \"What is the capital of France?\"))
    ;; => \"The capital of France is Paris.\")"
  [^ChatModel model ^String prompt]
  (.chat model prompt))

(defn chat
  "Performs a chat interaction with a model using a structured request.
  
  This function provides more control over the chat interaction, supporting
  both simple message sequences and full ChatRequest objects with advanced
  configuration options.
  
  Args:
    model - ChatModel instance (obtained from [[get-agent-object]])
    request - Either:
              - Sequential collection of messages (strings or message objects)
              - ChatRequest object with full configuration
  
  Returns:
    ChatResponse - Full response object with metadata and tool calls
  
  Example:
    (let [model (aor/get-agent-object agent-node \"openai-model\")]
      ;; With message sequence
      (lc4j/chat model [(UserMessage. \"Hello\") (UserMessage. \"How are you?\")])
      
      ;; With ChatRequest
      (lc4j/chat model (lc4j/chat-request
                        [(UserMessage. \"Calculate 2+2\")]
                        {:tools [calculator-tool]
                         :temperature 0.1})))"
  ^ChatResponse [^ChatModel model request]
  (cond
    (sequential? request)
    (.chat model ^java.util.List request)

    (instance? ChatRequest request)
    (.chat model ^ChatRequest request)

    :else
    (throw (h/ex-info "Unknown request type" {:type (class request)}))))


(def ^:private
     TOOL-CHOICES
  {:auto     ToolChoice/AUTO
   :required ToolChoice/REQUIRED})

(defn json-response-format
  "Creates a JSON response format configuration for structured model outputs.
  
  This function configures the model to return responses in a specific JSON
  format, useful for structured data extraction and API integrations.
  
  Args:
    name - String name for the JSON schema
    schema - JSON schema object defining the expected response structure. The `com.rpl.agent-o-rama.langchain4j.json` namespace provides helpers for creating JSON schema objects.
  
  Returns:
    ResponseFormat - Configuration object for use in [[chat-request]]
  
  Example:
    (let [math-schema (lj/object
                        {\"result\" (lj/number \"The calculated result\")
                         \"steps\" (lj/array (lj/string) \"Calculation steps\")})
          response-format (lc4j/json-response-format \"math-calc\" math-schema)]
      (lc4j/chat model
        (lc4j/chat-request
          [(UserMessage. \"Calculate 15 * 23\")]
          {:response-format response-format})))"
  [name schema]
  (-> (ResponseFormat/builder)
      (.type ResponseFormatType/JSON)
      (.jsonSchema
       (-> (JsonSchema/builder)
           (.name name)
           (.rootElement schema)
           .build))
      .build))

(defn chat-request
  "Creates a ChatRequest object for advanced model interactions.
  
  This function builds a structured request with full control over model
  parameters, tool usage, and response formatting. String messages are
  automatically converted to UserMessage objects.
  
  Args:
    messages - Collection of messages (strings or message objects)
    options - Optional map with configuration:
      :frequency-penalty - Number, penalty for frequent tokens
      :max-output-tokens - Integer, maximum tokens to generate
      :model-name - String, specific model to use
      :presence-penalty - Number, penalty for presence of tokens
      :response-format - ResponseFormat object for structured outputs, such as with [[json-response-format]]
      :stop-sequences - Collection of strings that stop generation
      :temperature - Number, randomness in generation (0.0-2.0)
      :tool-choice - Keyword, tool usage strategy (:auto or :required)
      :tools - Collection of tool info created with [[tool-info]]
      :top-k - Integer, top-k sampling parameter
      :top-p - Number, nucleus sampling parameter (0.0-1.0)
  
  Returns:
    ChatRequest - Request object for use with [[chat]]
  
  Example:
    (lc4j/chat-request
      [(UserMessage. \"Calculate the area of a circle with radius 5\")]
      {:tools [calculator-tool geometry-tool]
       :temperature 0.1
       :tool-choice :required
       :response-format (lc4j/json-response-format \"calculation\" math-schema)
       :max-output-tokens 500})"
  ([messages] (chat-request messages nil))
  ([messages
    {:keys [frequency-penalty max-output-tokens model-name presence-penalty
            response-format stop-sequences temperature tool-choice
            tools top-k top-p]}]
   (let [messages (mapv #(if (string? %) (UserMessage. ^String %) %) messages)]
     (-> (ChatRequest/builder)
         (.messages ^List messages)
         (.frequencyPenalty frequency-penalty)
         (.maxOutputTokens (if max-output-tokens (int max-output-tokens)))
         (.modelName model-name)
         (.presencePenalty presence-penalty)
         (.responseFormat response-format)
         (.stopSequences stop-sequences)
         (.temperature temperature)
         (.toolChoice (get TOOL-CHOICES tool-choice))
         (.toolSpecifications ^List (mapv :tool-specification tools))
         (.topK (if top-k (int top-k)))
         (.topP top-p)
         .build))))
