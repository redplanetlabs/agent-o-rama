(ns com.rpl.agent-o-rama.impl.serialize
  (:require
   [com.rpl.ramaspecter.defrecord-plus.serialise :as ser]
   [taoensso.nippy :as nippy])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]
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
   [dev.langchain4j.model.chat.request
    ChatRequest]
   [dev.langchain4j.model.chat.response
    ChatResponse]
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
 (nippy/freeze-to-out! out (.toMap obj)))

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


;; have to do this to avoid serializing type returned by List.of, which doesn't
;; exist in Java 8 – so the serializer can't be included by default in Rama
(defn empty-coll
  [coll]
  (if-not (empty? coll)
    coll))

(ser/extend-8-byte-freeze
 AiMessage
 [^AiMessage obj out]
 (nippy/freeze-to-out! out (.text obj))
 (nippy/freeze-to-out! out (empty-coll (.toolExecutionRequests obj))))

(ser/extend-8-byte-thaw
 AiMessage
 [in]
 (AiMessage/aiMessage (nippy/thaw-from-in! in) (nippy/thaw-from-in! in)))

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

; (ser/extend-8-byte-freeze
;  Embedding
;  [^Embedding obj out]
;  )
;
; (ser/extend-8-byte-thaw
;  Embedding
;  [in]
;  )

;; TODO: <<<<>>>>
; TextSegment
; ChatRequest
; ChatResponse
; EmbeddingMatch
; EmbeddingSearchResult
; ContainsString
; IsEqualTo
; IsGreaterThan
; IsGreaterThanOrEqualTo
; IsIn
; IsLessThan
; IsLessThanOrEqualTo
; IsNotEqualTo
; IsNotIn
; And
; Not
; Or
