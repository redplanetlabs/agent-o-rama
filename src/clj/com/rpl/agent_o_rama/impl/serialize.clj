(ns com.rpl.agent-o-rama.impl.serialize
  (:require
   [com.rpl.ramaspecter.defrecord-plus.serialise :as ser]
   [taoensso.nippy :as nippy])
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
   [java.io
    DataOutput]
   [java.util
    List]))

;; - have to do this to avoid serializing type returned by List.of, which
;; doesn't exist in Java 8 – so the serializer can't be included by default in
;; Rama
;; - this is used for various other collections to avoid this problem
(defn empty-coll
  [coll]
  (if-not (empty? coll)
    coll))

;; because some constructors allow empty map but not nil
(defn empty-map
  [coll]
  (if (empty? coll)
    {}
    coll))

(ser/extend-8-byte-freeze
 ToolExecutionRequest
 [^ToolExecutionRequest obj out]
 (nippy/freeze-to-out! out (.id obj))
 (nippy/freeze-to-out! out (.name obj))
 (nippy/freeze-to-out! out (.arguments obj)))

(ser/extend-8-byte-thaw
 ToolExecutionRequest
 [in]
 (-> (ToolExecutionRequest/builder)
     (.id (nippy/thaw-from-in! in))
     (.name (nippy/thaw-from-in! in))
     (.arguments (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 ToolSpecification
 [^ToolSpecification obj out]
 (nippy/freeze-to-out! out (.name obj))
 (nippy/freeze-to-out! out (.parameters obj))
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 ToolSpecification
 [in]
 (-> (ToolSpecification/builder)
     (.name (nippy/thaw-from-in! in))
     (.parameters (nippy/thaw-from-in! in))
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 ToolExecutionResultMessage
 [^ToolExecutionResultMessage obj out]
 (nippy/freeze-to-out! out (.id obj))
 (nippy/freeze-to-out! out (.toolName obj))
 (nippy/freeze-to-out! out (.text obj)))

(ser/extend-8-byte-thaw
 ToolExecutionResultMessage
 [in]
 (ToolExecutionResultMessage. (nippy/thaw-from-in! in)
                              (nippy/thaw-from-in! in)
                              (nippy/thaw-from-in! in)))


(ser/extend-8-byte-freeze
 Document
 [^Document obj out]
 (nippy/freeze-to-out! out (.text obj))
 (nippy/freeze-to-out! out (.metadata obj)))

(ser/extend-8-byte-thaw
 Document
 [in]
 (Document/document (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 Metadata
 [^Metadata obj out]
 (nippy/freeze-to-out! out (empty-map (.toMap obj))))

(ser/extend-8-byte-thaw
 Metadata
 [in]
 (Metadata. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 Embedding
 [^Embedding obj out]
 (nippy/freeze-to-out! out (.vector obj)))

(ser/extend-8-byte-thaw
 Embedding
 [in]
 (Embedding. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 AiMessage
 [^AiMessage obj out]
 (nippy/freeze-to-out! out (.text obj))
 (nippy/freeze-to-out! out (empty-coll (.toolExecutionRequests obj)))
 (nippy/freeze-to-out! out (.thinking obj))
 (nippy/freeze-to-out! out (empty-map (.attributes obj))))

(ser/extend-8-byte-thaw
 AiMessage
 [in]
 (-> (AiMessage/builder)
     (.text (nippy/thaw-from-in! in))
     (.toolExecutionRequests (nippy/thaw-from-in! in))
     (.thinking (nippy/thaw-from-in! in))
     (.attributes (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 CustomMessage
 [^CustomMessage obj out]
 (nippy/freeze-to-out! out (empty-coll (.attributes obj))))

(ser/extend-8-byte-thaw
 CustomMessage
 [in]
 (CustomMessage. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 SystemMessage
 [^SystemMessage obj out]
 (nippy/freeze-to-out! out (.text obj)))

(ser/extend-8-byte-thaw
 SystemMessage
 [in]
 (SystemMessage. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 TextContent
 [^TextContent obj out]
 (nippy/freeze-to-out! out (.text obj)))

(ser/extend-8-byte-thaw
 TextContent
 [in]
 (TextContent. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 UserMessage
 [^UserMessage obj out]
 (nippy/freeze-to-out! out (.name obj))
 (nippy/freeze-to-out! out (.contents obj)))

(ser/extend-8-byte-thaw
 UserMessage
 [in]
 (UserMessage. ^String (nippy/thaw-from-in! in) ^List (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 TextSegment
 [^TextSegment obj out]
 (nippy/freeze-to-out! out (.text obj))
 (nippy/freeze-to-out! out (.metadata obj)))

(ser/extend-8-byte-thaw
 TextSegment
 [in]
 (TextSegment. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 EmbeddingMatch
 [^EmbeddingMatch obj out]
 (nippy/freeze-to-out! out (.score obj))
 (nippy/freeze-to-out! out (.embeddingId obj))
 (nippy/freeze-to-out! out (.embedding obj))
 (nippy/freeze-to-out! out (.embedded obj)))

(ser/extend-8-byte-thaw
 EmbeddingMatch
 [in]
 (EmbeddingMatch. (nippy/thaw-from-in! in)
                  (nippy/thaw-from-in! in)
                  (nippy/thaw-from-in! in)
                  (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 EmbeddingSearchResult
 [^EmbeddingSearchResult obj out]
 (nippy/freeze-to-out! out (.matches obj)))

(ser/extend-8-byte-thaw
 EmbeddingSearchResult
 [in]
 (EmbeddingSearchResult. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 ContainsString
 [^ContainsString obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 ContainsString
 [in]
 (ContainsString. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsEqualTo
 [^IsEqualTo obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsEqualTo
 [in]
 (IsEqualTo. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsGreaterThan
 [^IsGreaterThan obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsGreaterThan
 [in]
 (IsGreaterThan. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsGreaterThanOrEqualTo
 [^IsGreaterThan obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsGreaterThanOrEqualTo
 [in]
 (IsGreaterThanOrEqualTo. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsLessThan
 [^IsLessThan obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsLessThan
 [in]
 (IsLessThan. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsLessThanOrEqualTo
 [^IsLessThanOrEqualTo obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsLessThanOrEqualTo
 [in]
 (IsLessThanOrEqualTo. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsNotEqualTo
 [^IsNotEqualTo obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValue obj)))

(ser/extend-8-byte-thaw
 IsNotEqualTo
 [in]
 (IsNotEqualTo. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsIn
 [^IsIn obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValues obj)))

(ser/extend-8-byte-thaw
 IsIn
 [in]
 (IsIn. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 IsNotIn
 [^IsNotIn obj out]
 (nippy/freeze-to-out! out (.key obj))
 (nippy/freeze-to-out! out (.comparisonValues obj)))

(ser/extend-8-byte-thaw
 IsNotIn
 [in]
 (IsNotIn. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 And
 [^And obj out]
 (nippy/freeze-to-out! out (.left obj))
 (nippy/freeze-to-out! out (.right obj)))

(ser/extend-8-byte-thaw
 And
 [in]
 (And. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 Or
 [^Or obj out]
 (nippy/freeze-to-out! out (.left obj))
 (nippy/freeze-to-out! out (.right obj)))

(ser/extend-8-byte-thaw
 Or
 [in]
 (Or. (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 Not
 [^Not obj out]
 (nippy/freeze-to-out! out (.expression obj)))

(ser/extend-8-byte-thaw
 Not
 [in]
 (Not. (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 TokenUsage
 [^TokenUsage obj out]
 (nippy/freeze-to-out! out (.inputTokenCount obj))
 (nippy/freeze-to-out! out (.outputTokenCount obj))
 (nippy/freeze-to-out! out (.totalTokenCount obj)))

(ser/extend-8-byte-thaw
 TokenUsage
 [in]
 (TokenUsage. (nippy/thaw-from-in! in)
              (nippy/thaw-from-in! in)
              (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 ToolExecution
 [^ToolExecution obj out]
 (nippy/freeze-to-out! out (.request obj))
 (nippy/freeze-to-out! out (.result obj)))

(ser/extend-8-byte-thaw
 ToolExecution
 [in]
 (-> (ToolExecution/builder)
     (.request ^ToolExecutionRequest (nippy/thaw-from-in! in))
     (.result ^String (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 Result
 [^Result obj out]
 (nippy/freeze-to-out! out (.content obj))
 (nippy/freeze-to-out! out (.tokenUsage obj))
 (nippy/freeze-to-out! out (.sources obj))
 (nippy/freeze-to-out! out (.finishReason obj))
 (nippy/freeze-to-out! out (.toolExecutions obj)))

(ser/extend-8-byte-thaw
 Result
 [in]
 (Result. (nippy/thaw-from-in! in)
          (nippy/thaw-from-in! in)
          (nippy/thaw-from-in! in)
          (nippy/thaw-from-in! in)
          (nippy/thaw-from-in! in)))


(ser/extend-8-byte-freeze
 FinishReason
 [^FinishReason obj out]
 (nippy/freeze-to-out! out (.name obj)))

(ser/extend-8-byte-thaw
 FinishReason
 [in]
 (FinishReason/valueOf (nippy/thaw-from-in! in)))

(ser/extend-8-byte-freeze
 ChatResponse
 [^ChatResponse obj out]
 (nippy/freeze-to-out! out (.aiMessage obj))
 (nippy/freeze-to-out! out (.finishReason obj))
 (nippy/freeze-to-out! out (.id obj))
 (nippy/freeze-to-out! out (.modelName obj))
 (nippy/freeze-to-out! out (.tokenUsage obj)))

(ser/extend-8-byte-thaw
 ChatResponse
 [in]
 (-> (ChatResponse/builder)
     (.aiMessage (nippy/thaw-from-in! in))
     (.finishReason (nippy/thaw-from-in! in))
     (.id (nippy/thaw-from-in! in))
     (.modelName (nippy/thaw-from-in! in))
     (.tokenUsage (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonAnyOfSchema
 [^JsonAnyOfSchema obj out]
 (nippy/freeze-to-out! out (.anyOf obj))
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonAnyOfSchema
 [in]
 (-> (JsonAnyOfSchema/builder)
     (.anyOf ^List (nippy/thaw-from-in! in))
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonArraySchema
 [^JsonArraySchema obj out]
 (nippy/freeze-to-out! out (.items obj))
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonArraySchema
 [in]
 (-> (JsonArraySchema/builder)
     (.items (nippy/thaw-from-in! in))
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonBooleanSchema
 [^JsonBooleanSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonBooleanSchema
 [in]
 (-> (JsonBooleanSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonEnumSchema
 [^JsonBooleanSchema obj out]
 (nippy/freeze-to-out! out (.enumValues obj))
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonEnumSchema
 [in]
 (-> (JsonEnumSchema/builder)
     (.enumValues ^List (nippy/thaw-from-in! in))
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonIntegerSchema
 [^JsonIntegerSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonIntegerSchema
 [in]
 (-> (JsonIntegerSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))


(ser/extend-8-byte-freeze
 JsonNullSchema
 [^JsonNullSchema obj out]
)

(ser/extend-8-byte-thaw
 JsonNullSchema
 [in]
 (JsonNullSchema.))

(ser/extend-8-byte-freeze
 JsonNumberSchema
 [^JsonNumberSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonNumberSchema
 [in]
 (-> (JsonNumberSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonObjectSchema
 [^JsonObjectSchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.additionalProperties obj))
 (nippy/freeze-to-out! out (empty-map (.definitions obj)))
 (nippy/freeze-to-out! out (empty-map (.properties obj)))
 (nippy/freeze-to-out! out (empty-coll (.required obj))))

(ser/extend-8-byte-thaw
 JsonObjectSchema
 [in]
 (-> (JsonObjectSchema/builder)
     (.description (nippy/thaw-from-in! in))
     (.additionalProperties (nippy/thaw-from-in! in))
     (.definitions (nippy/thaw-from-in! in))
     (.addProperties (nippy/thaw-from-in! in))
     (.required ^List (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonReferenceSchema
 [^JsonReferenceSchema obj out]
 (nippy/freeze-to-out! out (.reference obj)))

(ser/extend-8-byte-thaw
 JsonReferenceSchema
 [in]
 (-> (JsonReferenceSchema/builder)
     (.reference (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonStringSchema
 [^JsonStringSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonStringSchema
 [in]
 (-> (JsonStringSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))
