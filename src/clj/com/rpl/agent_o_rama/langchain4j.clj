(ns com.rpl.agent-o-rama.langchain4j
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [dev.langchain4j.agent.tool
    ToolSpecification]
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
  [^ChatModel model ^String prompt]
  (.chat model prompt))

(defn chat
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
  [name schema]
  (-> (ResponseFormat/builder)
      (.type ResponseFormatType/JSON)
      (.jsonSchema
       (-> (JsonSchema/builder)
           (.name name)
           (.rootElement schema)
           .build))
      .build))

(defn tool-specification
  ([name parameters-json-schema]
   (tool-specification name parameters-json-schema nil))
  ([name parameters-json-schema description]
   (-> (ToolSpecification/builder)
       (.name name)
       (.parameters parameters-json-schema)
       (.description description)
       .build)))

(defn chat-request
  ([messages] (chat-request messages nil))
  ([messages
    {:keys [frequency-penalty max-output-tokens model-name presence-penalty
            response-format stop-sequences temperature tool-choice
            tool-specifications top-k top-p]}]
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
         (.toolSpecifications ^List tool-specifications)
         (.topK (if top-k (int top-k)))
         (.topP top-p)
         .build))))
