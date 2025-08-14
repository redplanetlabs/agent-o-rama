(ns com.rpl.serialize-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.test-common :as tc]
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
   [dev.langchain4j.model.chat.request
    ChatRequest]
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
  (is (ser= (And. (IsEqualTo. "k" "a") (IsEqualTo. "k2" "b"))))
  (is (ser= (Or. (IsEqualTo. "k" "a") (IsEqualTo. "k2" "b"))))
  (is (ser= (Not. (IsNotIn. "k2" ["a" "b"]))))
  (is (ser= (TokenUsage. (int 1) (int 2) (int 3))))
  (is (ser= (TokenUsage. (int 1) (int 2))))
  (is (ser= (TokenUsage. (int 11))))
  (is (ser= (TokenUsage.)))
  (is (ser= (-> (ToolExecution/builder)
                (.request (-> (ToolExecutionRequest/builder)
                              (.id "id1")
                              (.name "foo")
                              (.arguments "abcde")
                              .build))
                (.result "abcd")
                .build)))
  (is (ser= FinishReason/STOP))
  (is (ser= FinishReason/LENGTH))
  (is (ser= FinishReason/OTHER))
  (let [r (Result. "abc"
                   (TokenUsage. (int 1))
                   [(TextContent. "foo")]
                   FinishReason/STOP
                   [(-> (ToolExecution/builder)
                        (.request (-> (ToolExecutionRequest/builder)
                                      (.id "id1")
                                      (.name "foo")
                                      (.arguments "abcde")
                                      .build))
                        (.result "abcd")
                        .build)])
        ^Result r* (roundtrip r)]
    (is (= (.content r) (.content r*)))
    (is (= (.tokenUsage r) (.tokenUsage r*)))
    (is (= (.sources r) (.sources r*)))
    (is (= (.finishReason r) (.finishReason r*)))
    (is (= (.toolExecutions r) (.toolExecutions r*))))
  (is (ser= (-> (ChatResponse/builder)
                (.aiMessage (AiMessage/aiMessage "foo"))
                (.finishReason FinishReason/STOP)
                (.id "aaa")
                (.modelName "aor-model")
                (.tokenUsage (TokenUsage. (int 1)))
                .build)))
  (is (ser= (-> (ChatResponse/builder)
                (.aiMessage (AiMessage/aiMessage "bar"))
                .build)))
  (is (ser= (-> (ToolSpecification/builder)
                (.description "abc")
                (.name "atool")
                (.parameters (lj/object {"a" (lj/string)}))
                .build
            )))
  (is (ser= (-> (ToolSpecification/builder)
                (.name "atool2")
                (.parameters (lj/object {"a" (lj/string)}))
                .build
            )))
  (is (ser= (lj/any-of [(lj/string) (lj/int)])))
  (is (ser= (lj/any-of "abc" [(lj/string) (lj/int)])))
  (is (ser= (lj/array (lj/string "aaa"))))
  (is (ser= (lj/array "desc" (lj/string "aaa"))))
  (is (ser= (lj/boolean)))
  (is (ser= (lj/boolean "cbf")))
  (is (ser= (lj/int)))
  (is (ser= (lj/int "a")))
  ;; since it doesn't implement .equals
  (is (instance? JsonNullSchema (roundtrip (lj/null))))
  (is (ser= (lj/number)))
  (is (ser= (lj/number "zyxw")))
  (is (ser= (lj/string)))
  (is (ser= (lj/string "a string desc")))
  (is (ser= (lj/enum ["a" "b"])))
  (is (ser= (lj/enum "d q c" ["a" "b" "c"])))
  (is (ser= (lj/reference "r")))
  (is (ser= (lj/object {"a" (lj/string) "b" (lj/int)})))
  (is (ser= (lj/object
             {:description "abc"
              :required    ["a"]
              :definitions {"rrr" (lj/int)}
              :additional-properties? true}
             {"a" (lj/string) "b" (lj/int)})))
)
