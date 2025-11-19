(ns com.rpl.langchain4j-json-test
  (:use [clojure.test])
  (:require
   [com.rpl.agent-o-rama.langchain4j.json :as lj])
  (:import
   [dev.langchain4j.model.chat.request.json
    JsonAnyOfSchema
    JsonArraySchema
    JsonBooleanSchema
    JsonEnumSchema
    JsonIntegerSchema
    JsonNumberSchema
    JsonObjectSchema
    JsonReferenceSchema
    JsonStringSchema]))

(deftest from-json-string-test
  ;; Tests that from-json-string correctly parses JSON Schema strings into
  ;; JsonObjectSchema instances, supporting all schema features used in the
  ;; codebase: object types with properties/required/additionalProperties,
  ;; primitive types (string/integer/number/boolean), nested objects, arrays,
  ;; enums, $ref references, and anyOf. Also verifies error handling for
  ;; invalid JSON and unsupported schema features.
  (testing "from-json-string"
    (testing "parses simple object with string property"
      (let [^JsonObjectSchema schema (lj/from-json-string
                                       "{\"type\": \"object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\"}
                      }}")
            props (.properties schema)]
        (is (instance? JsonObjectSchema schema))
        (is (= 1 (count props)))
        (is (instance? JsonStringSchema (get props "name")))))

    (testing "parses object with multiple primitive types"
      (let [^JsonObjectSchema schema (lj/from-json-string
                                       "{\"type\": \"object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\"},
                        \"age\": {\"type\": \"integer\"},
                        \"score\": {\"type\": \"number\"},
                        \"active\": {\"type\": \"boolean\"}
                      }}")
            props (.properties schema)]
        (is (= 4 (count props)))
        (is (instance? JsonStringSchema (get props "name")))
        (is (instance? JsonIntegerSchema (get props "age")))
        (is (instance? JsonNumberSchema (get props "score")))
        (is (instance? JsonBooleanSchema (get props "active")))))

    (testing "parses object with descriptions"
      (let [^JsonObjectSchema schema (lj/from-json-string
                                       "{\"type\": \"object\",
                      \"description\": \"User object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\", \"description\": \"User name\"}
                      }}")
            props (.properties schema)
            ^JsonStringSchema name-schema (get props "name")]
        (is (= "User object" (.description schema)))
        (is (= "User name" (.description name-schema)))))

    (testing "parses object with required fields"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"id\": {\"type\": \"string\"},
                        \"name\": {\"type\": \"string\"}
                      },
                      \"required\": [\"id\"]}")]
        (is (= ["id"] (.required schema)))))

    (testing "parses object with additionalProperties true"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\"}
                      },
                      \"additionalProperties\": true}")]
        (is (.additionalProperties schema))))

    (testing "parses object with additionalProperties false"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\"}
                      },
                      \"additionalProperties\": false}")]
        (is (not (.additionalProperties schema)))))

    (testing "defaults additionalProperties to false when not specified"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"name\": {\"type\": \"string\"}
                      }}")]
        (is (not (.additionalProperties schema)))))

    (testing "parses nested object schemas"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"user\": {
                          \"type\": \"object\",
                          \"properties\": {
                            \"name\": {\"type\": \"string\"},
                            \"age\": {\"type\": \"integer\"}
                          }
                        }
                      }}")
            props (.properties schema)
            ^JsonObjectSchema user-schema (get props "user")
            user-props (.properties user-schema)]
        (is (instance? JsonObjectSchema user-schema))
        (is (= 2 (count user-props)))
        (is (instance? JsonStringSchema (get user-props "name")))
        (is (instance? JsonIntegerSchema (get user-props "age")))))

    (testing "parses array schemas"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"tags\": {
                          \"type\": \"array\",
                          \"items\": {\"type\": \"string\"}
                        }
                      }}")
            props (.properties schema)
            ^JsonArraySchema tags-schema (get props "tags")]
        (is (instance? JsonArraySchema tags-schema))
        (is (instance? JsonStringSchema (.items tags-schema)))))

    (testing "parses array with description"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"tags\": {
                          \"type\": \"array\",
                          \"description\": \"List of tags\",
                          \"items\": {\"type\": \"string\"}
                        }
                      }}")
            props (.properties schema)
            ^JsonArraySchema tags-schema (get props "tags")]
        (is (= "List of tags" (.description tags-schema)))))

    (testing "parses array with object items"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"users\": {
                          \"type\": \"array\",
                          \"items\": {
                            \"type\": \"object\",
                            \"properties\": {
                              \"id\": {\"type\": \"string\"}
                            }
                          }
                        }
                      }}")
            props (.properties schema)
            ^JsonArraySchema users-schema (get props "users")
            ^JsonObjectSchema item-schema (.items users-schema)]
        (is (instance? JsonArraySchema users-schema))
        (is (instance? JsonObjectSchema item-schema))
        (is (= 1 (count (.properties item-schema))))))

    (testing "parses enum schemas"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"status\": {
                          \"enum\": [\"active\", \"inactive\"]
                        }
                      }}")
            props (.properties schema)
            ^JsonEnumSchema status-schema (get props "status")]
        (is (instance? JsonEnumSchema status-schema))
        (is (= ["active" "inactive"] (vec (.enumValues status-schema))))))

    (testing "parses enum with description"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"status\": {
                          \"description\": \"User status\",
                          \"enum\": [\"active\", \"inactive\"]
                        }
                      }}")
            props (.properties schema)
            ^JsonEnumSchema status-schema (get props "status")]
        (is (= "User status" (.description status-schema)))))

    (testing "parses $ref references"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"user\": {
                          \"$ref\": \"#/$defs/User\"
                        }
                      }}")
            props (.properties schema)
            ^JsonReferenceSchema user-schema (get props "user")]
        (is (instance? JsonReferenceSchema user-schema))
        (is (= "#/$defs/User" (.reference user-schema)))))

    (testing "parses anyOf with primitive types"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"id\": {
                          \"anyOf\": [
                            {\"type\": \"string\"},
                            {\"type\": \"integer\"}
                          ]
                        }
                      }}")
            props (.properties schema)
            ^JsonAnyOfSchema id-schema (get props "id")
            alternatives (vec (.anyOf id-schema))]
        (is (instance? JsonAnyOfSchema id-schema))
        (is (= 2 (count alternatives)))
        (is (instance? JsonStringSchema (nth alternatives 0)))
        (is (instance? JsonIntegerSchema (nth alternatives 1)))))

    (testing "parses anyOf with description"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"value\": {
                          \"description\": \"String or number\",
                          \"anyOf\": [
                            {\"type\": \"string\"},
                            {\"type\": \"number\"}
                          ]
                        }
                      }}")
            props (.properties schema)
            ^JsonAnyOfSchema value-schema (get props "value")]
        (is (= "String or number" (.description value-schema)))))

    (testing "parses anyOf with complex types"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"properties\": {
                        \"data\": {
                          \"anyOf\": [
                            {\"type\": \"object\",
                             \"properties\": {
                               \"name\": {\"type\": \"string\"}
                             }},
                            {\"type\": \"array\",
                             \"items\": {\"type\": \"string\"}}
                          ]
                        }
                      }}")
            props (.properties schema)
            ^JsonAnyOfSchema data-schema (get props "data")
            alternatives (vec (.anyOf data-schema))]
        (is (instance? JsonAnyOfSchema data-schema))
        (is (= 2 (count alternatives)))
        (is (instance? JsonObjectSchema (nth alternatives 0)))
        (is (instance? JsonArraySchema (nth alternatives 1)))))

    (testing "throws on invalid JSON"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Invalid JSON"
           (lj/from-json-string "{invalid json"))))

    (testing "throws on unsupported features"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported JSON schema feature: minimum"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"age\": {\"type\": \"integer\", \"minimum\": 0}
              }}")))

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported JSON schema feature: maximum"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"age\": {\"type\": \"integer\", \"maximum\": 100}
              }}")))

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported JSON schema feature: pattern"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"email\": {\"type\": \"string\", \"pattern\": \".*@.*\"}
              }}")))

      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unsupported JSON schema feature: allOf"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"data\": {\"allOf\": [{\"type\": \"string\"}]}
              }}"))))

    (testing "throws on missing array items"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Array schema missing 'items'"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"tags\": {\"type\": \"array\"}
              }}"))))

    (testing "throws on unknown schema type"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Unknown or missing schema type"
           (lj/from-json-string
            "{\"type\": \"object\",
              \"properties\": {
                \"data\": {\"type\": \"unknown\"}
              }}"))))

    (testing "throws when schema is not a map"
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           #"Schema must be a map"
           (lj/from-json-string "\"just a string\""))))

    (testing "parses complex nested schema"
      (let [^JsonObjectSchema schema (lj/from-json-string
                    "{\"type\": \"object\",
                      \"description\": \"Evaluation\",
                      \"properties\": {
                        \"score\": {\"type\": \"integer\"},
                        \"feedback\": {\"type\": \"string\"},
                        \"tags\": {
                          \"type\": \"array\",
                          \"items\": {\"type\": \"string\"}
                        },
                        \"metadata\": {
                          \"type\": \"object\",
                          \"properties\": {
                            \"reviewer\": {\"type\": \"string\"},
                            \"timestamp\": {\"type\": \"integer\"}
                          },
                          \"required\": [\"reviewer\"]
                        }
                      },
                      \"required\": [\"score\"]}")
            props (.properties schema)
            ^JsonObjectSchema metadata-schema (get props "metadata")]
        (is (= "Evaluation" (.description schema)))
        (is (= ["score"] (.required schema)))
        (is (= 4 (count props)))
        (is (instance? JsonIntegerSchema (get props "score")))
        (is (instance? JsonStringSchema (get props "feedback")))
        (is (instance? JsonArraySchema (get props "tags")))
        (is (instance? JsonObjectSchema metadata-schema))
        (is (= ["reviewer"] (.required metadata-schema)))))))
