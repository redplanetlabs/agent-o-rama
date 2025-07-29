(ns com.rpl.agent-o-rama.langchain4j.json
  (:refer-clojure :exclude [boolean int])
  (:import
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
   [java.util
    List]))

(defn any-of
  ([elems] (any-of nil elems))
  ([description elems]
   (-> (JsonAnyOfSchema/builder)
       (.description description)
       (.anyOf ^List elems)
       .build)))

(defn array
  ([item-schema] (array nil item-schema))
  ([description item-schema]
   (-> (JsonArraySchema/builder)
       (.description description)
       (.items item-schema)
       .build)))

(defn boolean
  ([] (boolean nil))
  ([description]
   (-> (JsonBooleanSchema/builder)
       (.description description)
       .build)))

(defn enum
  ([vals] (enum nil vals))
  ([description vals]
   (-> (JsonEnumSchema/builder)
       (.description description)
       (.enumValues ^List vals)
       .build)))

(defn int
  ([] (int nil))
  ([description]
   (-> (JsonIntegerSchema/builder)
       (.description description)
       .build)))

(defn null [] (JsonNullSchema.))

(defn number
  ([] (number nil))
  ([description]
   (-> (JsonNumberSchema/builder)
       (.description description)
       .build)))

(defn object
  ([name->schema]
   (object nil name->schema))
  ([options name->schema]
   (let [{:keys [description required definitions additional-properties?]
          :as   options}
         (if (string? options) {:description options} options)]
     (-> (JsonObjectSchema/builder)
         (.addProperties name->schema)
         (.additionalProperties (clojure.core/boolean additional-properties?))
         (.definitions definitions)
         (.description description)
         (.required ^List required)
         .build))))

(defn reference
  [ref]
  (-> (JsonReferenceSchema/builder)
      (.reference ref)
      .build))

(defn string
  ([] (string nil))
  ([description]
   (-> (JsonStringSchema/builder)
       (.description description)
       .build)))
