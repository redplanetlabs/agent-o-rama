(ns com.rpl.agent-o-rama.impl.json-serialize
  (:use [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [jsonista.core :as j])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest
    ToolSpecification]
   [dev.langchain4j.data.document
    DefaultDocument
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
    List
    Map]))

(defn to-float-array
  ^floats [v]
  (float-array (mapv float v)))

(def MAPPER (j/object-mapper {:decode-key-fn str}))

(defprotocol JSONFreeze
  (json-freeze* [this]))

(defn json-freeze*-with-type
  [x]
  (let [m (json-freeze* x)]
    (when-not (map? m)
      (throw (ex-info "json-freeze* must return a map"
                      {:value x :returned m})))
    (assoc
     (setval [MAP-VALS nil?] NONE m)
     "_aor-type"
     (-> x
         class
         .getName))))

(defn- freeze-walk
  [x]
  (if (satisfies? JSONFreeze x)
    (json-freeze*-with-type x)
    (cond
      (instance? Map x)
      (transform MAP-VALS freeze-walk (into {} x))

      (instance? List x)
      (transform ALL freeze-walk (into [] x))

      :else
      (try
        (j/write-value-as-string x MAPPER)
        x

        (catch Throwable t
          (str x))))))

(defn json-freeze
  ^String [obj]
  (j/write-value-as-string (freeze-walk obj) MAPPER))

(defmulti json-thaw*
  (fn [obj]
    (if (and (map? obj) (contains? obj "_aor-type"))
      (get obj "_aor-type")
    )))

(defmethod json-thaw* :default
  [obj]
  (if (and (map? obj) (contains? obj "_aor-type"))
    (throw (h/ex-info "No deserializer found for AOR type"
                      {:aor-type (get obj "_aor-type")}))
    obj))

(defn walk-json-thaw*
  [obj]
  (let [obj2 (json-thaw* obj)]
    (if-not (identical? obj obj2)
      obj2
      (cond (map? obj)
            (transform MAP-VALS walk-json-thaw* obj)

            (sequential? obj)
            (transform ALL walk-json-thaw* obj)

            :else
            obj
      ))))

(defn json-thaw
  [str]
  (let [obj (j/read-value str MAPPER)]
    (walk-json-thaw* obj)))


(extend-protocol JSONFreeze
  ToolExecutionRequest
  (json-freeze* [this]
    {"id"        (.id this)
     "name"      (.name this)
     "arguments" (.arguments this)}))


(defmethod json-thaw* (.getName ToolExecutionRequest)
  [m]
  (-> (ToolExecutionRequest/builder)
      (.id (get m "id"))
      (.name (get m "name"))
      (.arguments (get m "arguments"))
      .build))

(defn maybe-json-freeze*
  [o]
  (if o (json-freeze*-with-type o) o))

(defn maybe-json-thaw*
  [o]
  (if o (json-thaw* o) o))

(defn maybe-mapv-json-freeze*
  [o]
  (if o (mapv json-freeze*-with-type o) o))

(defn maybe-mapv-json-thaw*
  [o]
  (if o (mapv json-thaw* o) o))

(extend-protocol JSONFreeze
  ToolSpecification
  (json-freeze* [this]
    {"name"        (.name this)
     "parameters"  (maybe-json-freeze* (.parameters this))
     "description" (.description this)}))

(defmethod json-thaw* (.getName ToolSpecification)
  [m]
  (-> (ToolSpecification/builder)
      (.name (get m "name"))
      (.parameters (maybe-json-thaw* (get m "parameters")))
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  DefaultDocument
  (json-freeze* [this]
    {"text"     (.text this)
     "metadata" (maybe-json-freeze* (.metadata this))}))

(defmethod json-thaw* (.getName DefaultDocument)
  [m]
  (DefaultDocument.
   (get m "text")
   (maybe-json-thaw* (get m "metadata"))))

(extend-protocol JSONFreeze
  Metadata
  (json-freeze* [this]
    (into {} (.toMap this))))

(defmethod json-thaw* (.getName Metadata)
  [m]
  (Metadata. (dissoc m "_aor-type")))

(extend-protocol JSONFreeze
  Embedding
  (json-freeze* [this]
    {"vector" (into [] (.vector this))}))

(defmethod json-thaw* (.getName Embedding)
  [m]
  (Embedding. (to-float-array (get m "vector"))))

(extend-protocol JSONFreeze
  AiMessage
  (json-freeze* [this]
    {"text"       (.text this)
     "toolExecutionRequests" (maybe-mapv-json-freeze*
                              (.toolExecutionRequests this))
     "thinking"   (.thinking this)
     "attributes" (into {} (.attributes this))}))

(defmethod json-thaw* (.getName AiMessage)
  [m]
  (-> (AiMessage/builder)
      (.text (get m "text"))
      (.toolExecutionRequests (maybe-mapv-json-thaw*
                               (get m "toolExecutionRequests")))
      (.thinking (get m "thinking"))
      (.attributes (get m "attributes"))
      .build))

(extend-protocol JSONFreeze
  CustomMessage
  (json-freeze* [this]
    {"attributes" (into {} (.attributes this))}))

(defmethod json-thaw* (.getName CustomMessage)
  [m]
  (CustomMessage. (get m "attributes")))

(extend-protocol JSONFreeze
  SystemMessage
  (json-freeze* [this]
    {"text" (.text this)}))

(defmethod json-thaw* (.getName SystemMessage)
  [m]
  (SystemMessage. (get m "text")))

(extend-protocol JSONFreeze
  TextContent
  (json-freeze* [this]
    {"text" (.text this)}))

(defmethod json-thaw* (.getName TextContent)
  [m]
  (TextContent. (get m "text")))

(extend-protocol JSONFreeze
  ToolExecutionResultMessage
  (json-freeze* [this]
    {"id"       (.id this)
     "toolName" (.toolName this)
     "text"     (.text this)}))

(defmethod json-thaw* (.getName ToolExecutionResultMessage)
  [m]
  (ToolExecutionResultMessage.
   (get m "id")
   (get m "toolName")
   (get m "text")))

(extend-protocol JSONFreeze
  UserMessage
  (json-freeze* [this]
    {"name"     (.name this)
     "contents" (maybe-mapv-json-freeze* (.contents this))}))

(defmethod json-thaw* (.getName UserMessage)
  [m]
  (UserMessage. ^String (get m "name")
                ^java.util.List (maybe-mapv-json-thaw* (get m "contents"))))

(extend-protocol JSONFreeze
  TextSegment
  (json-freeze* [this]
    {"text"     (.text this)
     "metadata" (maybe-json-freeze* (.metadata this))}))

(defmethod json-thaw* (.getName TextSegment)
  [m]
  (TextSegment. (get m "text") (maybe-json-thaw* (get m "metadata"))))

(extend-protocol JSONFreeze
  JsonAnyOfSchema
  (json-freeze* [this]
    {"anyOf"       (maybe-mapv-json-freeze* (.anyOf this))
     "description" (.description this)}))

(defmethod json-thaw* (.getName JsonAnyOfSchema)
  [m]
  (-> (JsonAnyOfSchema/builder)
      (.anyOf ^java.util.List (maybe-mapv-json-thaw* (get m "anyOf")))
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonArraySchema
  (json-freeze* [this]
    {"items"       (maybe-json-freeze* (.items this))
     "description" (.description this)}))

(defmethod json-thaw* (.getName JsonArraySchema)
  [m]
  (-> (JsonArraySchema/builder)
      (.items (maybe-json-thaw* (get m "items")))
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonBooleanSchema
  (json-freeze* [this]
    {"description" (.description this)}))

(defmethod json-thaw* (.getName JsonBooleanSchema)
  [m]
  (-> (JsonBooleanSchema/builder)
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonEnumSchema
  (json-freeze* [this]
    {"enumValues"  (into [] (.enumValues this))
     "description" (.description this)}))

(defmethod json-thaw* (.getName JsonEnumSchema)
  [m]
  (-> (JsonEnumSchema/builder)
      (.enumValues ^java.util.List (get m "enumValues"))
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonIntegerSchema
  (json-freeze* [this]
    {"description" (.description this)}))

(defmethod json-thaw* (.getName JsonIntegerSchema)
  [m]
  (-> (JsonIntegerSchema/builder)
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonNullSchema
  (json-freeze* [this]
    {}))

(defmethod json-thaw* (.getName JsonNullSchema)
  [_]
  (JsonNullSchema.))

(extend-protocol JSONFreeze
  JsonNumberSchema
  (json-freeze* [this]
    {"description" (.description this)}))

(defmethod json-thaw* (.getName JsonNumberSchema)
  [m]
  (-> (JsonNumberSchema/builder)
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  JsonObjectSchema
  (json-freeze* [this]
    {"description"          (.description this)
     "additionalProperties" (.additionalProperties this)
     "definitions"          (->>
                             (.definitions this)
                             (into {})
                             (transform MAP-VALS maybe-json-freeze*))
     "properties"           (->>
                             (.properties this)
                             (into {})
                             (transform MAP-VALS maybe-json-freeze*))
     "required"             (into [] (.required this))}))

(defmethod json-thaw* (.getName JsonObjectSchema)
  [m]
  (-> (JsonObjectSchema/builder)
      (.description (get m "description"))
      (.additionalProperties (get m "additionalProperties"))
      (.definitions (transform MAP-VALS maybe-json-thaw* (get m "definitions")))
      (.addProperties
       (transform MAP-VALS maybe-json-thaw* (get m "properties")))
      (.required ^java.util.List (get m "required"))
      .build))

(extend-protocol JSONFreeze
  JsonReferenceSchema
  (json-freeze* [this]
    {"reference" (.reference this)}))

(defmethod json-thaw* (.getName JsonReferenceSchema)
  [m]
  (-> (JsonReferenceSchema/builder)
      (.reference (get m "reference"))
      .build))

(extend-protocol JSONFreeze
  JsonStringSchema
  (json-freeze* [this]
    {"description" (.description this)}))

(defmethod json-thaw* (.getName JsonStringSchema)
  [m]
  (-> (JsonStringSchema/builder)
      (.description (get m "description"))
      .build))

(extend-protocol JSONFreeze
  ChatResponse
  (json-freeze* [this]
    {"aiMessage"    (maybe-json-freeze* (.aiMessage this))
     "finishReason" (maybe-json-freeze* (.finishReason this))
     "id"           (.id this)
     "modelName"    (.modelName this)
     "tokenUsage"   (maybe-json-freeze* (.tokenUsage this))}))

(defmethod json-thaw* (.getName ChatResponse)
  [m]
  (-> (ChatResponse/builder)
      (.aiMessage (maybe-json-thaw* (get m "aiMessage")))
      (.finishReason (maybe-json-thaw* (get m "finishReason")))
      (.id (get m "id"))
      (.modelName (get m "modelName"))
      (.tokenUsage (maybe-json-thaw* (get m "tokenUsage")))
      .build))

(extend-protocol JSONFreeze
  FinishReason
  (json-freeze* [this]
    {"name" (.name this)}))

(defmethod json-thaw* (.getName FinishReason)
  [m]
  (FinishReason/valueOf (get m "name")))

(extend-protocol JSONFreeze
  TokenUsage
  (json-freeze* [this]
    {"inputTokenCount"  (.inputTokenCount this)
     "outputTokenCount" (.outputTokenCount this)
     "totalTokenCount"  (.totalTokenCount this)}))

(defmethod json-thaw* (.getName TokenUsage)
  [m]
  (TokenUsage.
   (get m "inputTokenCount")
   (get m "outputTokenCount")
   (get m "totalTokenCount")))

(extend-protocol JSONFreeze
  ToolExecution
  (json-freeze* [this]
    {"request" (maybe-json-freeze* (.request this))
     "result"  (.result this)}))

(defmethod json-thaw* (.getName ToolExecution)
  [m]
  (-> (ToolExecution/builder)
      (.request ^ToolExecutionRequest (maybe-json-thaw* (get m "request")))
      (.result ^String (get m "result"))
      (.build)))

(extend-protocol JSONFreeze
  ContainsString
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName ContainsString)
  [m]
  (ContainsString. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsEqualTo
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsEqualTo)
  [m]
  (IsEqualTo. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsGreaterThan
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsGreaterThan)
  [m]
  (IsGreaterThan. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsGreaterThanOrEqualTo
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsGreaterThanOrEqualTo)
  [m]
  (IsGreaterThanOrEqualTo. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsLessThan
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsLessThan)
  [m]
  (IsLessThan. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsLessThanOrEqualTo
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsLessThanOrEqualTo)
  [m]
  (IsLessThanOrEqualTo. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsNotEqualTo
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValue" (.comparisonValue this)}))

(defmethod json-thaw* (.getName IsNotEqualTo)
  [m]
  (IsNotEqualTo. (get m "key") (get m "comparisonValue")))

(extend-protocol JSONFreeze
  IsIn
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValues" (.comparisonValues this)}))

(defmethod json-thaw* (.getName IsIn)
  [m]
  (IsIn. (get m "key") (get m "comparisonValues")))

(extend-protocol JSONFreeze
  IsNotIn
  (json-freeze* [this]
    {"key" (.key this)
     "comparisonValues" (.comparisonValues this)}))

(defmethod json-thaw* (.getName IsNotIn)
  [m]
  (IsNotIn. (get m "key") (get m "comparisonValues")))

(extend-protocol JSONFreeze
  And
  (json-freeze* [this]
    {"left"  (maybe-json-freeze* (.left this))
     "right" (maybe-json-freeze* (.right this))}))

(defmethod json-thaw* (.getName And)
  [m]
  (And. (maybe-json-thaw* (get m "left")) (maybe-json-thaw* (get m "right"))))

(extend-protocol JSONFreeze
  Not
  (json-freeze* [this]
    {"expression" (maybe-json-freeze* (.expression this))}))

(defmethod json-thaw* (.getName Not)
  [m]
  (Not. (maybe-json-thaw* (get m "expression"))))

(extend-protocol JSONFreeze
  Or
  (json-freeze* [this]
    {"left"  (maybe-json-freeze* (.left this))
     "right" (maybe-json-freeze* (.right this))}))

(defmethod json-thaw* (.getName Or)
  [m]
  (Or. (maybe-json-thaw* (get m "left")) (maybe-json-thaw* (get m "right"))))
