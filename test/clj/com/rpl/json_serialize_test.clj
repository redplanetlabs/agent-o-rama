(ns com.rpl.json-serialize-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama.impl.json-serialize :as jser]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.test-common :as tc])
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

(defn- jroundtrip
  [obj]
  (jser/json-thaw (jser/json-freeze obj)))

(defn jser=
  [obj]
  (= obj (jroundtrip obj)))

(deftest json-ser-test
  (is (jser= (-> (ToolExecutionRequest/builder)
                 (.id "id1")
                 (.name "foo")
                 (.arguments "abcde")
                 .build)))
  (is (jser= (-> (ToolExecutionRequest/builder)
                 (.name "foo")
                 (.arguments "abcde")
                 .build)))
  (is (jser= (-> (ToolExecutionRequest/builder)
                 .build)))
  (is (jser= (ToolExecutionResultMessage. "a" "bb" "ccc")))
  (is (jser= (ToolExecutionResultMessage. "a" nil "ccc")))
  (is (jser= (ToolExecutionResultMessage. nil nil "1")))
  (is (jser= (Document/document "abcde" (Metadata. {"a" (int 1) "b" (int 2)}))))
  (is (jser= (Document/document "abcde" (Metadata. {}))))
  (is (jser= (tc/embedding 0.1 0.2 0.3 0.4)))
  (is (jser= (tc/embedding)))
  (is (jser= (AiMessage/aiMessage "abc"
                                  [(-> (ToolExecutionRequest/builder)
                                       (.name "foo")
                                       (.arguments "abcde")
                                       .build)
                                   (-> (ToolExecutionRequest/builder)
                                       (.name "foo2")
                                       (.arguments "abc")
                                       .build)])))
  (is (jser= (AiMessage/aiMessage "abc")))
  (is (jser= (CustomMessage. {"a" (int 1) "b" (int 10)})))
  (is (jser= (CustomMessage. {})))
  (is (jser= (CustomMessage. nil)))
  (is (jser= (SystemMessage. "abc")))
  (is (jser= (TextContent. "abcz")))
  (let [^java.util.List l [(TextContent. "abc") (TextContent. "def")]]
    (is (jser= (UserMessage. "aa" l)))
    (is (jser= (UserMessage. l)))
    (is (jser= (UserMessage. "abcdz"))))
  (is (jser= (TextSegment. "abcz" (Metadata. {}))))
  (is (jser= (TextSegment. "abcz" (Metadata. {"a" (int 1)}))))
  (is (jser= (ContainsString. "k" "v")))
  (is (jser= (ContainsString. "k2" "")))
  (is (jser= (IsEqualTo. "k" "v")))
  (is (jser= (IsEqualTo. "k2" "")))
  (is (jser= (IsGreaterThan. "k" "v")))
  (is (jser= (IsGreaterThan. "k2" "")))
  (is (jser= (IsGreaterThan. "k2" (int 10))))
  (is (jser= (IsGreaterThanOrEqualTo. "k" "v")))
  (is (jser= (IsGreaterThanOrEqualTo. "k2" "")))
  (is (jser= (IsGreaterThanOrEqualTo. "k2" (int 10))))
  (is (jser= (IsLessThan. "k" "v")))
  (is (jser= (IsLessThan. "k2" "")))
  (is (jser= (IsLessThan. "k2" (int 10))))
  (is (jser= (IsLessThanOrEqualTo. "k" "v")))
  (is (jser= (IsLessThanOrEqualTo. "k2" "")))
  (is (jser= (IsLessThanOrEqualTo. "k2" (int 10))))
  (is (jser= (IsNotEqualTo. "k" "v")))
  (is (jser= (IsNotEqualTo. "k2" "")))
  (is (jser= (IsNotEqualTo. "k2" (int 10))))
  (is (jser= (IsIn. "k2" ["a" "b"])))
  (is (jser= (IsIn. "k2" #{"a"})))
  (is (jser= (IsNotIn. "k2" ["a" "b"])))
  (is (jser= (IsNotIn. "k2" #{"a"})))
  (is (jser= (And. (IsEqualTo. "k" "a") (IsEqualTo. "k2" "b"))))
  (is (jser= (Or. (IsEqualTo. "k" "a") (IsEqualTo. "k2" "b"))))
  (is (jser= (Not. (IsNotIn. "k2" ["a" "b"]))))
  (is (jser= (TokenUsage. (int 1) (int 2) (int 3))))
  (is (jser= (TokenUsage. (int 1) (int 2))))
  (is (jser= (TokenUsage. (int 11))))
  (is (jser= (TokenUsage.)))
  (is (jser= (-> (ToolExecution/builder)
                 (.request (-> (ToolExecutionRequest/builder)
                               (.id "id1")
                               (.name "foo")
                               (.arguments "abcde")
                               .build))
                 (.result "abcd")
                 .build)))
  (is (jser= FinishReason/STOP))
  (is (jser= FinishReason/LENGTH))
  (is (jser= FinishReason/OTHER))
  (is (jser= (-> (ChatResponse/builder)
                 (.aiMessage (AiMessage/aiMessage "foo"))
                 (.finishReason FinishReason/STOP)
                 (.id "aaa")
                 (.modelName "aor-model")
                 (.tokenUsage (TokenUsage. (int 1)))
                 .build)))
  (is (jser= (-> (ChatResponse/builder)
                 (.aiMessage (AiMessage/aiMessage "bar"))
                 .build)))
  (is (jser= (-> (ToolSpecification/builder)
                 (.description "abc")
                 (.name "atool")
                 (.parameters (lj/object {"a" (lj/string)}))
                 .build)))
  (is (jser= (-> (ToolSpecification/builder)
                 (.name "atool2")
                 (.parameters (lj/object {"a" (lj/string)}))
                 .build)))
  (is (jser= (lj/any-of [(lj/string) (lj/int)])))
  (is (jser= (lj/any-of "abc" [(lj/string) (lj/int)])))
  (is (jser= (lj/array (lj/string "aaa"))))
  (is (jser= (lj/array "desc" (lj/string "aaa"))))
  (is (jser= (lj/boolean)))
  (is (jser= (lj/boolean "cbf")))
  (is (jser= (lj/int)))
  (is (jser= (lj/int "a")))
  ;; since it doesn't implement .equals
  (is (instance? JsonNullSchema (jroundtrip (lj/null))))
  (is (jser= (lj/number)))
  (is (jser= (lj/number "zyxw")))
  (is (jser= (lj/string)))
  (is (jser= (lj/string "a string desc")))
  (is (jser= (lj/enum ["a" "b"])))
  (is (jser= (lj/enum "d q c" ["a" "b" "c"])))
  (is (jser= (lj/reference "r")))
  (is (jser= (lj/object {"a" (lj/string) "b" (lj/int)})))
  (is (jser= (lj/object
              {:description "abc"
               :required    ["a"]
               :definitions {"rrr" (lj/int)}
               :additional-properties? true}
              {"a" (lj/string) "b" (lj/int)}))))


(deftest compound-test
  (letlocals
   (bind ^java.util.List content [(TextContent. "abc") (TextContent. "def")])
   (bind obj
     [[(SystemMessage. "abc")
       (UserMessage. "aa" content)
       (AiMessage/aiMessage "abc"
                            [(-> (ToolExecutionRequest/builder)
                                 (.name "foo")
                                 (.arguments "abcde")
                                 .build)
                             (-> (ToolExecutionRequest/builder)
                                 (.name "foo2")
                                 (.arguments "abc")
                                 .build)])]
      {"a" [(SystemMessage. "abc") (AiMessage/aiMessage "xyz")]
       "b" FinishReason/STOP
       "c" (int 45)
       "d" "some data"}])
   (bind json (jser/json-freeze obj))
   (is (string? json))
   (is (= obj (jser/json-thaw json)))
  ))

(deftest unhandled-test
  (letlocals
   (bind es
     (EmbeddingSearchResult.
      [(EmbeddingMatch. 2.0 "a" (tc/embedding 1.1 1.2) nil)
       (EmbeddingMatch. 2.1 "bb" (tc/embedding 1.1 1.3) "foo")]))

   (bind s (jser/json-freeze es))
   (is (.startsWith s
                    "\"dev.langchain4j.store.embedding.EmbeddingSearchResult@"))


   (bind em (EmbeddingMatch. 2.0 "a" (tc/embedding 1.1 1.2) nil))
   (bind s (jser/json-freeze em))
   (is (and (.contains s "embedding = Embedding { vector = [1.1, 1.2] }")
            (.contains s "EmbeddingMatch")
            (.contains s "score = 2.0")))

   (try
     (jser/json-thaw "{\"_aor-type\": \"foo\"}")
     (is false)
     (catch clojure.lang.ExceptionInfo e
       (is (= (ex-message e)
              "No deserializer found for AOR type {:aor-type \"foo\"}"))
     ))
  ))
