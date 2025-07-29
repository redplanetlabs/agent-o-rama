(ns com.rpl.serialize-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.test-common :as tc]
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
    DataOutput]))

(defn- roundtrip
  [obj]
  (nippy/thaw (nippy/freeze obj)))

(defn ser=
  [obj]
  (= obj (roundtrip obj)))

(deftest ser-test
  (is (ser= (-> (ToolExecutionRequest/builder)
                (.id "id1")
                (.name "foo")
                (.arguments "abcde")
                .build)))
  (is (ser= (-> (ToolExecutionRequest/builder)
                (.name "foo")
                (.arguments "abcde")
                .build)))
  (is (ser= (-> (ToolExecutionRequest/builder)
                .build)))
  (is (ser= (ToolExecutionResultMessage. "a" "bb" "ccc")))
  (is (ser= (ToolExecutionResultMessage. "a" nil "ccc")))
  (is (ser= (ToolExecutionResultMessage. nil nil "1")))
  (is (ser= (Document/document "abcde" (Metadata. {"a" 1 "b" 2}))))
  (is (ser= (tc/embedding 0.1 0.2 0.3 0.4)))
  (is (ser= (tc/embedding)))
)

;; TODO: <<<<>>>>
; AiMessage
; CustomMessage
; SystemMessage
; TextContent
; UserMessage
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
