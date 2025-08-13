(ns com.rpl.agent-o-rama.impl.json-serialize
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest
    ToolSpecification]
   [dev.langchain4j.data.document
    Document
    Metadata]
   [dev.langchain4j.data.embedding
    Embedding]
   [dev.langchain4j.data.message
    AiMessage
    CustomMessage
    SystemMessage
    TextContent
    ToolExecutionResultMessage
    UserMessage]
   [dev.langchain4j.data.segment
    TextSegment]
   [dev.langchain4j.model.chat.request.json
    JsonAnyOfSchema
    JsonArraySchema
    JsonBooleanSchema
    JsonEnumSchema
    JsonIntegerSchema
    JsonNullSchema
    JsonNumberSchema
    JsonObjectSchema
    JsonReferenceSchema
    JsonStringSchema]
   [dev.langchain4j.model.chat.response
    ChatResponse]
   [dev.langchain4j.model.output
    FinishReason
    TokenUsage]
   [dev.langchain4j.service
    Result]
   [dev.langchain4j.service.tool
    ToolExecution]
   [dev.langchain4j.store.embedding
    EmbeddingMatch
    EmbeddingSearchResult]
   [dev.langchain4j.store.embedding.filter.comparison
    ContainsString
    IsEqualTo
    IsGreaterThan
    IsGreaterThanOrEqualTo
    IsIn
    IsLessThan
    IsLessThanOrEqualTo
    IsNotEqualTo
    IsNotIn]
   [dev.langchain4j.store.embedding.filter.logical
    And
    Not
    Or]
   [java.util
    List]))

(defprotocol JSONFreeze
  (json-freeze* [this]))

(defn json-freeze
  [obj]
  (let [res (json-freeze* obj)]
    (if (map? res)
      (assoc
       map
       "_aor-type"
       (-> obj
           class
           .getName))
      res)))

(defmulti json-thaw
  (fn [m]
    (if (and (map? m) (contains? m "_aor-type"))
      (get m "_aor-type")
      ::error
    )))

(defmethod json-thaw ::error
  [obj]
  (throw (h/ex-info "Could not deserialize string into first-class type"
                    {:value obj})))

(extend-protocol JSONFreeze
  Object
  ;; fallback case to render as plain strings – these will not be deserializable
  ;; if modified
  (json-freeze* [this] (str this)))

(extend-protocol JSONFreeze
  ToolExecutionRequest
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ToolExecutionRequest)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  ToolSpecification
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ToolSpecification)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Document
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Document)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Metadata
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Metadata)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Embedding
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Embedding)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  AiMessage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName AiMessage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  CustomMessage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName CustomMessage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  SystemMessage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName SystemMessage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  TextContent
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName TextContent)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  ToolExecutionResultMessage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ToolExecutionResultMessage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  UserMessage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName UserMessage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  TextSegment
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName TextSegment)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonAnyOfSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonAnyOfSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonArraySchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonArraySchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonBooleanSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonBooleanSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonEnumSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonEnumSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonIntegerSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonIntegerSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonNullSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonNullSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonNumberSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonNumberSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonObjectSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonObjectSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonReferenceSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonReferenceSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  JsonStringSchema
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName JsonStringSchema)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  ChatResponse
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ChatResponse)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  FinishReason
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName FinishReason)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  TokenUsage
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName TokenUsage)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Result
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Result)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  ToolExecution
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ToolExecution)
  [obj]
  ;; TODO: <<<<>>>>
)


(extend-protocol JSONFreeze
  EmbeddingMatch
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName EmbeddingMatch)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  EmbeddingSearchResult
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName EmbeddingSearchResult)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  ContainsString
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName ContainsString)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsEqualTo
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsEqualTo)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsGreaterThan
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsGreaterThan)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsGreaterThanOrEqualTo
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsGreaterThanOrEqualTo)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsIn
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsIn)
  [obj]
  ;; TODO: <<<<>>>>
)


(extend-protocol JSONFreeze
  IsLessThan
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsLessThan)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsLessThanOrEqualTo
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsLessThanOrEqualTo)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsNotEqualTo
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsNotEqualTo)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  IsNotIn
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName IsNotIn)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  And
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName And)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Not
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Not)
  [obj]
  ;; TODO: <<<<>>>>
)

(extend-protocol JSONFreeze
  Or
  (json-freeze* [this]
                ;; TODO:
  ))

(defmethod json-thaw (.getName Or)
  [obj]
  ;; TODO: <<<<>>>>
)
