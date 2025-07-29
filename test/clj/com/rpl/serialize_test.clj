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
  (is (ser= (Document/document "abcde" (Metadata. {}))))
  (is (ser= (tc/embedding 0.1 0.2 0.3 0.4)))
  (is (ser= (tc/embedding)))
  (is (ser= (AiMessage/aiMessage "abc"
                                 [(-> (ToolExecutionRequest/builder)
                                      (.name "foo")
                                      (.arguments "abcde")
                                      .build)
                                  (-> (ToolExecutionRequest/builder)
                                      (.name "foo2")
                                      (.arguments "abc")
                                      .build)])))
  (is (ser= (AiMessage/aiMessage "abc")))
  (is (ser= (CustomMessage. {"a" 1 "b" 10})))
  (is (ser= (CustomMessage. {})))
  (is (ser= (CustomMessage. nil)))
  (is (ser= (SystemMessage. "abc")))
  (is (ser= (TextContent. "abcz")))
  (let [^java.util.List l [(TextContent. "abc") (TextContent. "def")]]
    (is (ser= (UserMessage. "aa" l)))
    (is (ser= (UserMessage. l)))
    (is (ser= (UserMessage. "abcdz"))))
  (is (ser= (TextSegment. "abcz" (Metadata. {}))))
  (is (ser= (TextSegment. "abcz" (Metadata. {"a" 1}))))
  (is (ser= (EmbeddingMatch. 2.0 "aaa" (tc/embedding 1.1 1.2) "hello")))
  (is (ser= (EmbeddingMatch. 2.0 "a" (tc/embedding 1.1 1.2) nil)))
  ;; EmbeddingSearchResult doesn't implement equals
  (let [r (EmbeddingSearchResult.
           [(EmbeddingMatch. 2.0 "a" (tc/embedding 1.1 1.2) nil)
            (EmbeddingMatch. 2.1 "bb" (tc/embedding 1.1 1.3) "foo")])
        ^EmbeddingSearchResult r* (roundtrip r)

        r2 (EmbeddingSearchResult. [])
        ^EmbeddingSearchResult r2* (roundtrip r2)]
    (is (= (.matches r) (.matches r*)))
    (is (= (.matches r2) (.matches r2*))))
  (is (ser= (ContainsString. "k" "v")))
  (is (ser= (ContainsString. "k2" "")))
  (is (ser= (IsEqualTo. "k" "v")))
  (is (ser= (IsEqualTo. "k2" "")))
  (is (ser= (IsGreaterThan. "k" "v")))
  (is (ser= (IsGreaterThan. "k2" "")))
  (is (ser= (IsGreaterThan. "k2" 10)))
  (is (ser= (IsGreaterThanOrEqualTo. "k" "v")))
  (is (ser= (IsGreaterThanOrEqualTo. "k2" "")))
  (is (ser= (IsGreaterThanOrEqualTo. "k2" 10)))
  (is (ser= (IsLessThan. "k" "v")))
  (is (ser= (IsLessThan. "k2" "")))
  (is (ser= (IsLessThan. "k2" 10)))
  (is (ser= (IsLessThanOrEqualTo. "k" "v")))
  (is (ser= (IsLessThanOrEqualTo. "k2" "")))
  (is (ser= (IsLessThanOrEqualTo. "k2" 10)))
  (is (ser= (IsNotEqualTo. "k" "v")))
  (is (ser= (IsNotEqualTo. "k2" "")))
  (is (ser= (IsNotEqualTo. "k2" 10)))
  (is (ser= (IsIn. "k2" ["a" "b"])))
  (is (ser= (IsIn. "k2" #{"a"})))
  (is (ser= (IsNotIn. "k2" ["a" "b"])))
  (is (ser= (IsNotIn. "k2" #{"a"})))


)

;; TODO: <<<<>>>>
; And
; Not
; Or
; ChatRequest
; ChatResponse
