(ns com.rpl.agent-o-rama.langchain4j.json
  "JSON schema builders for LangChain4j structured outputs and tool specifications.\n
\n
This namespace provides Clojure-friendly functions for building JSON schemas used throughout LangChain4j for structured outputs, tool parameter definitions, and response formatting. These schemas ensure models return data in predictable formats and enable type-safe tool calling."
  (:refer-clojure :exclude [boolean int])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [jsonista.core :as j])
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

(def ^:private unsupported-schema-features
  "JSON schema features not yet supported by the from-json-string parser."
  [:minimum :maximum :minLength :maxLength
   :pattern :format :allOf :oneOf
   :not :const :default])

(defn any-of
  "Creates a JSON schema that accepts any of the provided schema types.\n
\n
This is useful for union types where a field can be one of several different types or values.\n
\n
Args:\n
  - elems - Collection of JsonSchema objects that are valid alternatives
  - description - Optional string description of the schema
\n
Returns:\n
  - JsonAnyOfSchema - Schema that accepts any of the provided types
\n
Example:\n
<pre>
;; Field can be either string or number
(lj/any-of \"ID can be string or number\"
           [(lj/string \"String identifier\")
            (lj/number \"Numeric identifier\")])
;; Field can be any of several enum values
(lj/any-of [(lj/enum \"Status\" [\"active\" \"inactive\"])
            (lj/null)])
</pre>"
  ([elems] (any-of nil elems))
  ([description elems]
   (-> (JsonAnyOfSchema/builder)
       (.description description)
       (.anyOf ^List elems)
       .build)))

(defn array
  "Creates a JSON schema for arrays with a specific item type.\n
\n
Args:\n
  - item-schema - JsonSchema object defining the type of array elements
  - description - Optional string description of the array
\n
Returns:\n
  - JsonArraySchema - Schema for arrays with the specified item type
\n
Example:\n
<pre>
;; Array of strings
(lj/array \"List of tags\" (lc4j/string \"A tag\"))
;; Array of objects
(lj/array \"List of users\"
          (lj/object {\"name\" (lc4j/string)
                     \"age\" (lc4j/int)}))
</pre>"
  ([item-schema] (array nil item-schema))
  ([description item-schema]
   (-> (JsonArraySchema/builder)
       (.description description)
       (.items item-schema)
       .build)))

(defn boolean
  "Creates a JSON schema for boolean values.\n
\n
Args:\n
  - description - Optional string description of the boolean field
\n
Returns:\n
  - JsonBooleanSchema - Schema for boolean values
\n
Example:\n
<pre>
(lj/boolean \"Whether the feature is enabled\")
(lj/boolean)  ; No description
</pre>"
  ([] (boolean nil))
  ([description]
   (-> (JsonBooleanSchema/builder)
       (.description description)
       .build)))

(defn enum
  "Creates a JSON schema for enumerated values.\n
\n
Args:\n
  - vals - Collection of valid string values
  - description - Optional string description of the enum
\n
Returns:\n
  - JsonEnumSchema - Schema that accepts only the specified values
\n
Example:\n
<pre>
;; Status field with specific values
(lj/enum \"User status\" [\"active\" \"inactive\" \"pending\"])
;; Priority levels
(lj/enum [\"low\" \"medium\" \"high\" \"critical\"])
</pre>"
  ([vals] (enum nil vals))
  ([description vals]
   (-> (JsonEnumSchema/builder)
       (.description description)
       (.enumValues ^List vals)
       .build)))

(defn int
  "Creates a JSON schema for integer values.\n
\n
Args:\n
  - description - Optional string description of the integer field
\n
Returns:\n
  - JsonIntegerSchema - Schema for integer values
\n
Example:\n
<pre>
(lj/int \"Number of retries\")
(lj/int)  ; No description
</pre>"
  ([] (int nil))
  ([description]
   (-> (JsonIntegerSchema/builder)
       (.description description)
       .build)))

(defn null
  "Creates a JSON schema for null values.\n
\n
Returns:\n
  - JsonNullSchema - Schema that only accepts null
\n
Example:\n
<pre>
;; Optional field that can be null
(lj/any-of \"Optional field\"
           [(lj/string \"String value\")
            (lj/null)])
</pre>"
  []
  (JsonNullSchema.))

(defn number
  "Creates a JSON schema for numeric values (integers and floats).\n
\n
Args:\n
  - description - Optional string description of the number field
\n
Returns:\n
  - JsonNumberSchema - Schema for numeric values
\n
Example:\n
<pre>
(lj/number \"Price in dollars\")
(lj/number)  ; No description
</pre>"
  ([] (number nil))
  ([description]
   (-> (JsonNumberSchema/builder)
       (.description description)
       .build)))

(defn object
  "Creates a JSON schema for objects with defined properties.\n
\n
This is the most commonly used schema type for structured data.\n
\n
Args:\n
  - name->schema - Map from property names (strings) to their JsonSchema definitions
  - options - Optional configuration map or string description:
    - :description - String description of the object
    - :required - Collection of required property names
    - :definitions - Map of reusable schema definitions
    - :additional-properties? - Boolean, whether additional properties are allowed
\n
Returns:\n
  - JsonObjectSchema - Schema for objects with the specified properties
\n
Example:\n
<pre>
;; Simple object
(lj/object {\"name\" (lj/string \"User name\")
            \"age\" (lj/int \"User age\")})
;; Complex object with options
(lj/object
  {:description \"User profile with required fields\"
   :required [\"id\" \"name\"]
   :additional-properties? false}
  {\"id\" (lj/string \"Unique identifier\")
   \"name\" (lj/string \"Full name\")
   \"email\" (lj/string \"Email address\")
   \"preferences\" (lj/object {\"theme\" (lj/enum [\"light\" \"dark\"])
                             \"notifications\" (lj/boolean)})})
;; With string description
(lj/object \"Simple user object\"
           {\"id\" (lj/string)
            \"name\" (lj/string)})
</pre>"
  ([name->schema]
   (object nil name->schema))
  ([options name->schema]
   (let [{:keys [description required definitions additional-properties?]
          :as   options}
         (if (string? options) {:description options} options)]
     (h/validate-options! nil
                          options
                          {:description h/string-spec
                           :required    h/list-spec
                           :additional-properties? h/boolean-spec
                           :definitions h/map-spec})
     (-> (JsonObjectSchema/builder)
         (.addProperties name->schema)
         (.additionalProperties (clojure.core/boolean additional-properties?))
         (.definitions definitions)
         (.description description)
         (.required ^List required)
         .build))))

(defn reference
  "Creates a JSON schema reference to a definition.\n
\n
References are used to avoid duplicating schema definitions and enable recursive schemas.\n
\n
Args:\n
  - ref - String reference path (e.g., \"#/$defs/User\")
\n
Returns:\n
  - JsonReferenceSchema - Schema that references another definition
\n
Example:\n
<pre>
;; Reference to a definition
(lj/reference \"#/$defs/User\")
;; Self-reference for recursive structures
(lj/object {\"value\" (lj/string)
            \"children\" (lj/array (lj/reference \"#\")})
</pre>"
  [ref]
  (-> (JsonReferenceSchema/builder)
      (.reference ref)
      .build))

(defn string
  "Creates a JSON schema for string values.\n
\n
Args:\n
  - description - Optional string description of the string field
\n
Returns:\n
  - JsonStringSchema - Schema for string values
\n
Example:\n
<pre>
(lj/string \"User's full name\")
(lj/string)  ; No description
</pre>"
  ([] (string nil))
  ([description]
   (-> (JsonStringSchema/builder)
       (.description description)
       .build)))

;;; JSON Schema Parsing

(defn- check-unsupported-features!
  "Throws exception if schema contains unsupported features."
  [schema path]
  (doseq [k unsupported-schema-features]
    (when (contains? schema k)
      (throw (ex-info (str "Unsupported JSON schema feature: " (name k) " at " path)
                      {:feature k
                       :path path
                       :schema schema})))))

(defn- extend-path
  "Extends a schema path with a key or index segment.

  Converts keyword keys to strings."
  [path segment]
  (str path "/" (if (keyword? segment) (name segment) segment)))

(defn- parse-schema
  "Recursively converts a JSON schema map to a JsonSchema object.

  Path is used for error reporting.

  Note: This implementation intentionally deviates from the JSON Schema spec
  for additionalProperties. When additionalProperties is undefined (nil), this
  function defaults to false (reject additional properties) rather than true
  (allow additional properties) as specified in JSON Schema. This provides a
  more restrictive and safer default behavior."
  [schema path]
  (when-not (map? schema)
    (throw (ex-info (str "Schema must be a map at " path)
                    {:path path :value schema})))

  (check-unsupported-features! schema path)

  (let [schema-type (get schema :type)
        description (get schema :description)
        enum-vals (get schema :enum)
        ref (get schema :$ref)
        any-of-schemas (get schema :anyOf)]

    (cond
      ;; Handle $ref references
      ref
      (reference ref)

      ;; Handle enum types
      enum-vals
      (enum description enum-vals)

      ;; Handle anyOf combinator
      any-of-schemas
      (let [parsed-schemas (vec (map-indexed
                                  (fn [idx schema]
                                    (parse-schema schema (extend-path path (str "anyOf[" idx "]"))))
                                  any-of-schemas))]
        (any-of description parsed-schemas))

      ;; Handle types
      (= schema-type "object")
      (let [properties (get schema :properties {})
            required (get schema :required [])
            additional-props? (get schema :additionalProperties)
            opts (cond-> {}
                   description (assoc :description description)
                   (seq required) (assoc :required required)
                   true (assoc :additional-properties?
                               (if (nil? additional-props?)
                                 false
                                 (clojure.core/boolean additional-props?))))]
        (object
         opts
         (into {}
               (map (fn [[k v]]
                      (let [k-str (if (keyword? k) (name k) k)]
                        [k-str (parse-schema v (extend-path path k))]))
                    properties))))

      (= schema-type "array")
      (let [items (get schema :items)]
        (when-not items
          (throw (ex-info (str "Array schema missing 'items' at " path)
                          {:path path :schema schema})))
        (if (string? description)
          (array description (parse-schema items (extend-path path "items")))
          (array (parse-schema items (extend-path path "items")))))

      (= schema-type "string")
      (if (string? description)
        (string description)
        (string))

      (= schema-type "integer")
      (if (string? description)
        (int description)
        (int))

      (= schema-type "number")
      (if (string? description)
        (number description)
        (number))

      (= schema-type "boolean")
      (if (string? description)
        (boolean description)
        (boolean))

      :else
      (throw (ex-info (str "Unknown or missing schema type at " path)
                      {:path path
                       :type schema-type
                       :schema schema})))))

(defn from-json-string
  "Parses a JSON schema string and constructs a JsonObjectSchema.

  Supports JSON Schema features:
  - Object types with properties, required fields, and additionalProperties
  - Primitive types: string, integer, number, boolean
  - Array types with item schemas
  - Enum types
  - References using $ref
  - anyOf combinator for union types
  - Descriptions for all schema types
  - Nested objects and arrays

  Unsupported features (will throw exceptions):
  - Constraints: minimum, maximum, minLength, maxLength, pattern, format
  - Logical combinators: allOf, oneOf, not
  - Other: const, default

  Args:
    json-str - JSON string containing the schema definition

  Returns:
    JsonSchema object (typically JsonObjectSchema)

  Throws:
    ExceptionInfo if JSON is invalid or contains unsupported features

  Example:
  <pre>
  (from-json-string
    \"{\\\"type\\\": \\\"object\\\",
      \\\"properties\\\": {
        \\\"name\\\": {\\\"type\\\": \\\"string\\\", \\\"description\\\": \\\"User name\\\"},
        \\\"age\\\": {\\\"type\\\": \\\"integer\\\"}
      },
      \\\"required\\\": [\\\"name\\\"]}\")
  </pre>"
  [json-str]
  (try
    (let [schema (j/read-value json-str j/keyword-keys-object-mapper)]
      (parse-schema schema "$"))
    (catch com.fasterxml.jackson.core.JsonParseException e
      (throw (ex-info "Invalid JSON in schema string"
                      {:error (.getMessage e)}
                      e)))))
