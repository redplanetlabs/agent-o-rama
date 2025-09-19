(ns com.rpl.datasets-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.databind
    ObjectMapper
    JsonNode]
   [com.networknt.schema
    JsonSchema
    ValidationMessage]
   [com.rpl.agentorama
    AddDatasetExampleOptions]
   [dev.langchain4j.data.message
    UserMessage]))


(defrecord Person [name age])

(defn msgs
  [errs]
  (mapv #(.getMessage ^ValidationMessage %) errs))

(deftest x-javaType-basic-pojo
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties" {"p" {"x-javaType" "com.rpl.datasets_test.Person"}}
            "required"   ["p"]})]
    (is (empty? (datasets/validate S {"p" (->Person "Alice" 30)})))
    (is (= ["x-javaType: $.p — expected com.rpl.datasets_test.Person"]
           (msgs (datasets/validate S {"p" {"name" "Bob" "age" 40}}))))
    (is (= ["x-javaType: $.p — expected com.rpl.datasets_test.Person"]
           (msgs (datasets/validate S {"p" "not a person"}))))
    (is (= ["$: required property 'p' not found"]
           (msgs (datasets/validate S {}))))))

(deftest x-javaType-scalars
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties" {"s"  {"x-javaType" "java.lang.String"}
                          "i"  {"x-javaType" "java.lang.Integer"}
                          "l"  {"x-javaType" "java.lang.Long"}
                          "d"  {"x-javaType" "java.lang.Double"}
                          "bd" {"x-javaType" "java.math.BigDecimal"}
                          "b"  {"x-javaType" "java.lang.Boolean"}}
            "required"   ["s" "i" "l" "d" "bd" "b"]})]
    ;; happy path (note: (int 3) for Integer, (long 3) for Long)
    (is (empty? (datasets/validate S
                                   {"s"  "ok"
                                    "i"  (int 3)
                                    "l"  (long 3)
                                    "d"  (double 1.5)
                                    "bd" (bigdec "2.50")
                                    "b"  false})))
    ;; mismatch examples
    (is (= ["x-javaType: $.i — expected java.lang.Integer"]
           (msgs (datasets/validate S
                                    {"s"  "ok"
                                     "i"  3
                                     "l"  (long 3)
                                     "d"  (double 1.5)
                                     "bd" (bigdec "2.50")
                                     "b"  false}))))
    (is (= ["x-javaType: $.s — expected java.lang.String"]
           (msgs (datasets/validate S
                                    {"s"  100
                                     "i"  (int 1)
                                     "l"  (long 1)
                                     "d"  (double 1.0)
                                     "bd" (bigdec "1.0")
                                     "b"  true}))))))


(deftest x-javaType-collections
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties" {"m"  {"x-javaType" "java.util.Map"}
                          "xs" {"x-javaType" "java.util.List"}}
            "required"   ["m" "xs"]})]
    (is (empty? (datasets/validate S {"m" {"k" "v"} "xs" ["a" "b" "c"]})))
    (is (= ["x-javaType: $.m — expected java.util.Map"]
           (msgs (datasets/validate S {"m" "not-a-map" "xs" []}))))
    (is (= ["x-javaType: $.xs — expected java.util.List"]
           (msgs (datasets/validate S {"m" {} "xs" "not-a-list"}))))))

(deftest x-javaType-nested-and-items
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties"
            {"tags" {"type"  "array"
                     "items" {"x-javaType" "java.lang.String"}}
             "meta" {"type"       "object"
                     "properties" {"count" {"x-javaType" "java.lang.Integer"}
                                   "owner" {"x-javaType"
                                            "com.rpl.datasets_test.Person"}}
                     "required"   ["count" "owner"]}}
            "required"   ["tags" "meta"]})]
    (is (empty? (datasets/validate S
                                   {"tags" ["a" "b"]
                                    "meta" {"count" (int 5)
                                            "owner" (->Person "Zed" 41)}})))
    (is (= ["x-javaType: $.tags[0] — expected java.lang.String"]
           (msgs (datasets/validate S
                                    {"tags" [42]
                                     "meta" {"count" (int 1)
                                             "owner" (->Person "ok" 1)}}))))
    (is (= ["x-javaType: $.meta.owner — expected com.rpl.datasets_test.Person"]
           (msgs (datasets/validate S
                                    {"tags" []
                                     "meta" {"count" (int 2)
                                             "owner" {"name"
                                                      "map-not-person"}}}))))))

(deftest accepts-json-null
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties" {"n" {"anyOf" [{"x-javaType" "java.lang.String"}
                                        {"type" "null"}]}}
            "required"   ["n"]})]
    (is (empty? (datasets/validate S {"n" nil})))     ;; null is allowed
    (let [errs (msgs (datasets/validate S {"n" 42}))] ;; neither string nor null
      (is (some #{"x-javaType: $.n — expected java.lang.String"} errs))
      (is (some #(re-find #"null expected" %) errs)))))

(deftest x-javaType-items-and-required-propagation
  ;; array items checked; missing required still from base validator
  (let [S (datasets/build-schema
           {"$schema"    datasets/META
            "type"       "object"
            "properties" {"xs" {"type"  "array"
                                "items" {"x-javaType" "java.lang.Integer"}}}
            "required"   ["xs"]})]
    (is (empty? (datasets/validate S {"xs" [(int 1) (int 2)]})))
    (is (= ["x-javaType: $.xs[1] — expected java.lang.Integer"]
           (msgs (datasets/validate S {"xs" [(int 1) 2]}))))
    (is (= ["$: required property 'xs' not found"]
           (msgs (datasets/validate S {}))))))

(deftest json-schema-mixed
  (let [schema
        {"$schema"    datasets/META
         "$defs"
         {"address" {"type"       "object"
                     "properties" {"street" {"type" "string" "minLength" 1}
                                   "zip"    {"type"    "string"
                                             "pattern" "^[0-9]{5}$"}}
                     "required"   ["street" "zip"]
                     "additionalProperties" false}}
         "type"       "object"
         "properties"
         {"id"      {"x-javaType" "java.util.UUID"}
          "name"    {"type" "string" "minLength" 1}
          "age"     {"type" "integer" "minimum" 0 "maximum" 130}
          "tags"    {"type"        "array"
                     "items"       {"type" "string"}
                     "minItems"    1
                     "uniqueItems" true}
          "contact" {"anyOf" [{"$ref" "#/$defs/address"} {"type" "null"}]}
          ;; 2020-12 tuple form:
          "scores"  {"type"        "array"
                     "prefixItems" [{"type" "number"}
                                    {"x-javaType" "java.lang.Double"}]
                     "items"       false}
          "prefs"   {"type" "object"
                     "patternProperties" {"^feature_[a-z]+$" {"type" "boolean"}}
                     "additionalProperties" false}}
         "required"   ["id" "name" "age" "contact"]
         "additionalProperties" false}
        S      (datasets/build-schema schema)]

    ;; -------- VALID CASE --------
    (testing "valid instance passes"
      (is (empty?
           (datasets/validate
            S
            {"id"      (h/random-uuid7)
             "name"    "Alice"
             "age"     (int 30)
             "tags"    ["a" "b"]
             "contact" {"street" "Main" "zip" "12345"}
             "scores"  [1.25 (double 3.5)]
             "prefs"   {"feature_dark" true "feature_beta" false}}))))

    ;; -------- INVALIDS (regular JSON Schema) --------
    (testing "required property"
      (let [errs (msgs (datasets/validate
                        S
                        {"id"      (h/random-uuid7)
                         "age"     (int 20)
                         "contact" nil}))] ; name missing
        (is (some #(re-find #"required property 'name' not found" %) errs))))

    (testing "minimum / maximum on integer"
      (let [errs (msgs (datasets/validate
                        S
                        {"id"      (h/random-uuid7)
                         "name"    "A"
                         "age"     (int -1)
                         "contact" nil}))]
        (is (some #(re-find #"minimum" %) errs))))

    (testing "uniqueItems on array"
      (let [errs (msgs (datasets/validate
                        S
                        {"id"      (h/random-uuid7)
                         "name"    "A"
                         "age"     (int 20)
                         "tags"    ["dup" "dup"]
                         "contact" nil}))]
        (is (some #(re-find #"unique" %) errs))))

    (testing "pattern on ZIP"
      (let [errs (msgs (datasets/validate
                        S
                        {"id"      (h/random-uuid7)
                         "name"    "A"
                         "age"     (int 20)
                         "tags"    ["x"]
                         "contact" {"street" "Main"
                                    "zip"    "12A45"}}))]
        (is (some #(re-find #"pattern" %) errs))))

    (testing "tuple arity (additionalItems: false)"
      (let [errs (msgs (datasets/validate
                        S
                        {"id"      (h/random-uuid7)
                         "name"    "A"
                         "age"     (int 20)
                         "tags"    ["x"]
                         "contact" nil
                         "scores"  [1.0 (double 2.0) 99]}))] ; too many  items
        (is (not (empty? errs))))) ; don't depend on exact text

    (testing "patternProperties + additionalProperties=false"
      (let [errs (msgs (datasets/validate S
                                          {"id"      (h/random-uuid7)
                                           "name"    "A"
                                           "age"     (int 20)
                                           "tags"    ["x"]
                                           "contact" nil
                                           "prefs"   {"feature_dark" true
                                                      "dark"         false}}))] ; "dark"
                                                                                ; not
                                                                                ; allowed
        (is (not (empty? errs)))))

    ;; -------- INVALIDS (x-javaType interaction) --------
    (testing "x-javaType(UUID) failure"
      (let [errs (msgs (datasets/validate S
                                          {"id"      "not-a-uuid"
                                           "name"    "A"
                                           "age"     (int 20)
                                           "tags"    ["x"]
                                           "contact" nil}))]
        (is (some #{"x-javaType: $.id — expected java.util.UUID"} errs))))

    (testing "x-javaType in tuple items"
      (let [errs (msgs (datasets/validate S
                                          {"id"      (h/random-uuid7)
                                           "name"    "A"
                                           "age"     (int 20)
                                           "tags"    ["x"]
                                           "contact" nil
                                           "scores"  [1.0 2.0]}))] ; second must
                                                                   ; be
                                                                   ; java.lang.Double;
                                                                   ; 2.0 is
                                                                   ; fine, try
                                                                   ; bad
        ;; Force a bad second element: a string
        (let [errs2 (msgs (datasets/validate S
                                             {"id"      (h/random-uuid7)
                                              "name"    "A"
                                              "age"     (int 20)
                                              "tags"    ["x"]
                                              "contact" nil
                                              "scores"  [1.0 "NaN"]}))]
          (is (some #{"x-javaType: $.scores[1] — expected java.lang.Double"}
                    errs2)))))))

(def ^ObjectMapper OM datasets/MAPPER)

(defn ok? [x] (string? x))
(defn err? [x] (and (map? x) (contains? x :error)))
(defn ->json ^JsonNode [^String s] (.readTree OM s))
(defn to-json
  [o]
  (.writeValueAsString OM o))

(deftest normalize-json-schema*-invalid-json
  (testing "empty / malformed JSON"
    (is (err? (datasets/normalize-json-schema* "")))
    (is (err? (datasets/normalize-json-schema* "{")))
    (is (err? (datasets/normalize-json-schema* "null")))
    (is (err? (datasets/normalize-json-schema* "[]")))) ; non-object root
  (testing "error messages are present"
    (let [res (datasets/normalize-json-schema* "{")]
      (is (re-find #"Invalid JSON" (:error res))))))

(deftest normalize-json-schema*-forbidden-meta-keys
  (testing "reject user $schema"
    (let [res (datasets/normalize-json-schema*
               "{\"$schema\":\"https://wrong\",\"type\":\"object\"}")]
      (is (err? res))
      (is (re-find #"\$schema" (:error res)))))
  (testing "reject user $vocabulary"
    (let [res (datasets/normalize-json-schema*
               "{\"$vocabulary\":{\"x\":true},\"type\":\"object\"}")]
      (is (err? res))
      (is (re-find #"\$vocabulary" (:error res))))))

(deftest normalize-json-schema*-invalid-json-schema
  (testing "draft 2020-12: tuple via items[] is invalid"
    (let
      [s
       "{\"type\":\"array\",\"items\":[{\"type\":\"string\"},{\"type\":\"number\"}]}"
       res (datasets/normalize-json-schema* s)]
      (is (err? res))
      (is (re-find #"Invalid JSON schema" (:error res))))))

(deftest normalize-json-schema*-basic-success
  (let
    [input
     "{\"type\":\"object\",\"properties\":{\"x\":{\"type\":\"string\"}},\"additionalProperties\":false}"
     norm  (datasets/normalize-json-schema* input)]
    (is (ok? norm))
    (let [jn (->json norm)]
      ;; $schema injected and equals our metaschema IRI
      (is (= datasets/META (.asText (.get jn "$schema"))))
      ;; still a valid JSON object with our original content
      (is (= "object" (.asText (.get jn "type"))))
      ;; round-trip: compile with our factory to ensure it’s truly valid
      (is (instance? JsonSchema (datasets/build-schema jn))))))

(deftest normalize-json-schema*-with-extension-and-standard
  (testing "mix of x-javaType and standard keywords"
    (let
      [input
       (str
        "{"
        "\"type\":\"object\","
        "\"properties\":{"
        "  \"id\":{\"x-javaType\":\"java.util.UUID\"},"
        "  \"name\":{\"type\":\"string\",\"minLength\":1},"
        "  \"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"uniqueItems\":true},"
        "  \"contact\":{\"anyOf\":["
        "    {\"type\":\"object\",\"properties\":{\"zip\":{\"pattern\":\"^[0-9]{5}$\"}},\"additionalProperties\":true},"
        "    {\"type\":\"null\"}"
        "  ]},"
        "  \"scores\":{\"type\":\"array\",\"prefixItems\":[{\"type\":\"number\"},{\"x-javaType\":\"java.lang.Double\"}],\"items\":false}"
        "},"
        "\"required\":[\"id\",\"name\",\"contact\"],"
        "\"additionalProperties\":false"
        "}")]
      (let [norm (datasets/normalize-json-schema* input)]
        (is (ok? norm))
        ;; ensure it compiles
        (let [S (datasets/build-schema (->json norm))]
          ;; a valid instance should pass
          (is (empty?
               (datasets/validate S
                                  {"id"      (h/random-uuid7)
                                   "name"    "Alice"
                                   "contact" nil
                                   "tags"    ["a" "b"]
                                   "scores"  [1.0 (double 2.5)]})))
          ;; a few failure modes should yield non-empty errors
          (is (not (empty?
                    (datasets/validate S
                                       {"id"      "not-a-uuid"
                                        "name"    "A"
                                        "contact" nil
                                        "tags"    []
                                        "scores"  [1.0 "NaN"]}))))
          (is (not (empty?
                    (datasets/validate S
                                       {"name" "A" "contact" nil})))))))))

(deftest normalize-json-schema*-preserves-user-structure-except-meta
  (let
    [input
     "{\"$id\":\"https://example.com/s\",\"type\":\"object\",\"properties\":{\"n\":{\"type\":\"integer\"}}}"
     norm  (datasets/normalize-json-schema* input)]
    (is (ok? norm))
    (let [jn (->json norm)]
      ;; user $id is untouched
      (is (= "https://example.com/s" (.asText (.get jn "$id"))))
      ;; our $schema is present
      (is (= datasets/META (.asText (.get jn "$schema")))))))

(deftest normalize-json-schema*-clear-error-boundaries
  (testing "differentiate JSON parse error vs schema compile error"
    (let [bad-json (datasets/normalize-json-schema* "{")]
      (is (err? bad-json))
      (is (re-find #"Invalid JSON" (:error bad-json))))
    (let [bad-schema (datasets/normalize-json-schema*
                      "{\"type\":\"array\",\"items\":[{\"type\":\"string\"}]}")]
      (is (err? bad-schema))
      (is (re-find #"Invalid JSON schema" (:error bad-schema))))))


(deftest validate-with-schema*-happy-path
  (let [schema (datasets/normalize-json-schema*
                (str
                 "{"
                 "\"type\":\"object\","
                 "\"properties\":{"
                 "  \"id\":{\"x-javaType\":\"java.util.UUID\"},"
                 "  \"name\":{\"type\":\"string\"},"
                 "  \"owner\":{\"x-javaType\":\"com.rpl.datasets_test.Person\"}"
                 "},"
                 "\"required\":[\"id\",\"name\",\"owner\"]"
                 "}"))
        ; normalize returns a JSON string; ensure it is so
        _ (is (string? schema))
        result (datasets/validate-with-schema*
                schema
                {"id"    (h/random-uuid7)
                 "name"  "ok"
                 "owner" (->Person "A" 1)})]
    (is (nil? result))))

(deftest validate-with-schema*-invalid-json-schema_string
  (let [err (datasets/validate-with-schema* "{" {"x" 1})]
    (is (string? err))
    (is (h/contains-string? err "Invalid JSON schema"))))

(deftest validate-with-schema*-compile-error
  ;; 2020-12: tuple via items[] is invalid → compile-time schema error
  (let
    [bad
     (datasets/normalize-json-schema*
      "{\"type\":\"array\",\"items\":[{\"type\":\"string\"},{\"type\":\"number\"}]}")]
    (is (map? bad)) ; normalize should already reject, but if it slipped
                    ; through:
    (let [err (datasets/validate-with-schema*
               "{\"type\":\"array\",\"items\":[{\"type\":\"string\"}]}"
               ["a" "b"])]
      (is (string? err))
      (is (h/contains-string? err "Failed to compile or apply schema")))))

(deftest validate-with-schema*-validation-errors
  ;; mix regular JSON Schema + x-javaType errors; expect non-nil error string
  (let
    [schema
     (datasets/normalize-json-schema*
      (str
       "{"
       "\"type\":\"object\","
       "\"properties\":{"
       "  \"id\":{\"x-javaType\":\"java.util.UUID\"},"
       "  \"age\":{\"type\":\"integer\",\"minimum\":0},"
       "  \"tags\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"uniqueItems\":true}"
       "},"
       "\"required\":[\"id\",\"age\"]"
       "}"))
     err    (datasets/validate-with-schema*
             schema
             {"id"   "not-a-uuid"
              "age"  (int -1)
              "tags" ["dup" "dup"]})]
    (is (string? err))
    (is (h/contains-string? err "x-javaType: $.id"))
    (is (h/contains-string? err "minimum"))
    (is (h/contains-string? err "unique"))))

(deftest validate-with-schema*-pojo-vs-json
  ;; Ensure POJO leaf is validated via x-javaType, while JSON types still work
  (let [schema
        (datasets/normalize-json-schema*
         (str
          "{"
          "\"type\":\"object\","
          "\"properties\":{"
          "  \"owner\":{\"x-javaType\":\"com.rpl.datasets_test.Person\"},"
          "  \"name\":{\"type\":\"string\"}"
          "},"
          "\"required\":[\"owner\",\"name\"]"
          "}"))]
    (is (nil? (datasets/validate-with-schema*
               schema
               {"owner" (->Person "Zed" 42)
                "name"  "ok"})))
    (let [err (datasets/validate-with-schema*
               schema
               {"owner" {"name" "map-not-person"}
                "name"  "ok"})]
      (is (string? err))
      (is (h/contains-string? err "x-javaType: $.owner")))))

(deftest dataset-operations-test
  (with-redefs [queries/search-pagination-size (constantly 2)]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node]
                 (aor/result! agent-node "done")
               )))
         ))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind manager (aor/agent-manager ipc module-name))
       (bind pstate
         (foreign-pstate ipc module-name (po/datasets-task-global-name)))
       (bind page-query
         (foreign-query ipc
                        module-name
                        (queries/get-datasets-page-query-name)))
       (bind search-examples-query
         (foreign-query ipc module-name (queries/search-examples-name)))
       (bind multi-examples-query
         (foreign-query ipc
                        module-name
                        (queries/multi-examples-name)))


       (bind schema1
         {"type"       "object"
          "properties"
          {"p1" {"x-javaType" "java.util.List"}
           "p2" {"type" "string"}}
          "required"   ["p1"]})
       (bind schema-str {"type" "string"})
       (bind to-internal-json
         (fn [s]
           (to-json (assoc s "$schema" datasets/META))))

       ;; so they have separate timestamps
       (bind create-and-wait!
         (fn [& args]
           (Thread/sleep 2)
           (apply aor/create-dataset! args)))

       (bind ds-id1
         (create-and-wait! manager
                           "Dataset 1 is a dataset"
                           {:description "this is a dataset"}))
       (bind ds-id2
         (create-and-wait! manager
                           "Dataset sample 2"))
       (bind ds-id3
         (create-and-wait! manager
                           "Dataset 3 – sample of inputs"
                           {:description        "this is a description"
                            :input-json-schema  (to-json schema1)
                            :output-json-schema (to-json schema-str)}))
       (bind ds-id4
         (create-and-wait! manager
                           "Dataset 4 sample of movies"
                           {:description       "a description"
                            :input-json-schema (to-json schema-str)}))
       (bind ds-id5
         (create-and-wait! manager
                           "Dataset 5 sample of books"))
       (bind ds-id6
         (create-and-wait! manager
                           "Dataset 6 sampleof vaudeville"
                           {:description       "a description 6"
                            :input-json-schema (to-json schema-str)}))
       (bind ds-id7
         (create-and-wait! manager
                           "Dataset 7 is another dataset"
                           {:description "a description"}))
       (bind ds-id8
         (create-and-wait! manager
                           "Dataset 8"))

       (try
         (aor/create-dataset! manager "bad schema" {:input-json-schema "><"})
         (is false)
         (catch clojure.lang.ExceptionInfo e
           (is (h/contains-string? (ex-message e) "Error creating dataset"))
           (is (h/contains-string? (-> e
                                       ex-data
                                       :info)
                                   "Invalid JSON"))))
       (try
         (aor/create-dataset! manager
                              "bad schema"
                              {:output-json-schema (to-json {"type" "blah"})})
         (is false)
         (catch clojure.lang.ExceptionInfo e
           (is (h/contains-string? (ex-message e) "Error creating dataset"))
           (is (h/contains-string? (-> e
                                       ex-data
                                       :info)
                                   "Invalid JSON"))
           (is (h/contains-string?
                (-> e
                    ex-data
                    :info)
                "$.type: does not have a value in the enumeration"))))

       (is (not= ds-id1 ds-id2 ds-id3 ds-id4 ds-id5 ds-id6 ds-id7 ds-id8))

       (doseq [[s query-amt amt] [["dataset" 3 3]
                                  ["is a" 3 2]
                                  ["SAMPLEof" 1000 1]
                                  ["sample" 6 5]]]
         (let [res (aor/search-datasets manager s query-amt)]
           (is (= (count res) amt))
           (doseq [v (vals res)]
             (is (h/contains-string? (str/lower-case v) (str/lower-case s))))
         ))


       (bind pages
         (loop [ret    []
                params nil]
           (let [{:keys [datasets pagination-params]}
                 (foreign-invoke-query page-query 3 params)
                 ret (conj ret datasets)]
             (if (every? nil? (vals pagination-params))
               ret
               (recur ret pagination-params)
             ))))

       (is (> (count pages) 1))
       (bind items (vec (apply concat pages)))
       (is
        (= (setval
            [ALL
             (multi-path :task-id
                         :created-at
                         :modified-at
                         [:input-json-schema nil?]
                         [:output-json-schema nil?])]
            NONE
            items)
           [{:dataset-id  ds-id8
             :name        "Dataset 8"
             :description nil}
            {:dataset-id  ds-id7
             :name        "Dataset 7 is another dataset"
             :description "a description"}
            {:dataset-id  ds-id6
             :name        "Dataset 6 sampleof vaudeville"
             :description "a description 6"
             :input-json-schema (to-internal-json schema-str)}
            {:dataset-id  ds-id5
             :name        "Dataset 5 sample of books"
             :description nil}
            {:dataset-id  ds-id4
             :name        "Dataset 4 sample of movies"
             :description "a description"
             :input-json-schema (to-internal-json schema-str)}
            {:dataset-id  ds-id3
             :name        "Dataset 3 – sample of inputs"
             :description "this is a description"
             :input-json-schema (to-internal-json schema1)
             :output-json-schema (to-internal-json schema-str)}
            {:dataset-id  ds-id2
             :name        "Dataset sample 2"
             :description nil}
            {:dataset-id  ds-id1
             :name        "Dataset 1 is a dataset"
             :description "this is a dataset"}
           ]))

       (bind res (queries/get-dataset-properties pstate ds-id3))
       (is (= (dissoc res :created-at :modified-at)
              {:name "Dataset 3 – sample of inputs"
               :description "this is a description"
               :input-json-schema (to-internal-json schema1)
               :output-json-schema (to-internal-json schema-str)}))
       (is (some? (:created-at res)))
       (is (= (:created-at res) (:modified-at res)))

       (bind add-example-and-wait!
         (fn [& args]
           (Thread/sleep 2)
           (apply aor/add-dataset-example! args)))

       (bind add-example-and-wait-java!
         (fn [^com.rpl.agentorama.AgentManager manager dataset-id snapshot-name
              input reference-output tags]
           (Thread/sleep 2)
           (let [options (AddDatasetExampleOptions.)]
             (set! (.snapshotName options) snapshot-name)
             (set! (.referenceOutput options) reference-output)
             (set! (.tags options) tags)
             (.addDatasetExample manager
                                 dataset-id
                                 input
                                 options))))

       (bind add-example-and-wait-async!
         (fn [& args]
           (Thread/sleep 2)
           (.get ^java.util.concurrent.CompletableFuture
                 (apply aor/add-dataset-example-async! args))))

       (bind examples-cleaned
         (fn [examples]
           (setval
            [ALL
             (multi-path :created-at
                         :modified-at
                         :id
                         [:source nil?])]
            NONE
            examples)))

       (bind check-times!
         (fn [created-at created-at2 modified-at modified-at2]
           (when-not (= created-at created-at2)
             (throw (ex-info "created-at mismatch"
                             {:time1 created-at :time2 created-at2})))
           (when-not (> modified-at2 modified-at)
             (throw (ex-info "modified-at mismatch"
                             {:time1 modified-at :time2 modified-at2})))))

       (bind verified-dataset-times
         (fn [dataset-id afn]
           (letlocals
            (bind {:keys [created-at modified-at]}
              (queries/get-dataset-properties pstate dataset-id))
            (Thread/sleep 2)
            (afn)
            (bind {created-at2 :created-at modified-at2 :modified-at}
              (queries/get-dataset-properties pstate dataset-id))
            (check-times! created-at created-at2 modified-at modified-at2)
           )))

       (bind verified-example-times
         (fn [dataset-id snapshot-name example-id afn]
           (letlocals
            (bind {:keys [created-at modified-at]}
              (foreign-select-one
               [(keypath dataset-id :snapshots snapshot-name example-id)]
               pstate))
            (verified-dataset-times
             dataset-id
             afn)
            (bind {created-at2 :created-at modified-at2 :modified-at}
              (foreign-select-one
               [(keypath dataset-id :snapshots snapshot-name example-id)]
               pstate))
            (check-times! created-at created-at2 modified-at modified-at2)
           )))


       (verified-dataset-times
        ds-id8
        #(aor/set-dataset-name! manager ds-id8 "8 set data"))
       (is (= "8 set data"
              (:name (queries/get-dataset-properties pstate ds-id8))))
       (verified-dataset-times
        ds-id8
        #(aor/set-dataset-description! manager ds-id8 "88812"))
       (is (= "88812"
              (:description (queries/get-dataset-properties pstate ds-id8))))

       (verified-dataset-times
        ds-id1
        #(add-example-and-wait! manager ds-id1 "example1-1"))
       (bind {:keys [created-at modified-at]}
         (foreign-select-one
          [(keypath ds-id1 :snapshots nil)
           MAP-VALS]
          pstate))
       (is (some? created-at))
       (is (= created-at modified-at))
       (bind ar-source
         (aor-types/->AgentRunSourceImpl "foo.Module" "agent1" (aor-types/->AgentInvokeImpl 1 2)))
       (binding [aor-types/OPERATION-SOURCE ar-source]
         (add-example-and-wait!
          manager
          ds-id1
          "example1-2"
          {:reference-output "output1-2"
           :tags #{"tag1" "tag2"}}))

       (bind get-examples-page
         (fn [ds-id snapshot limit page-key]
           (foreign-invoke-query search-examples-query
                                 ds-id
                                 snapshot
                                 nil
                                 limit
                                 page-key)))

       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 nil 10 nil))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  "example1-1"
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  "example1-2"
                :reference-output "output1-2"
                :tags   #{"tag1" "tag2"}
                :source ar-source}]))
       (verified-dataset-times
        ds-id1
        #(aor/snapshot-dataset! manager ds-id1 nil "snapshot1"))
       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 "snapshot1" 10 nil))
       (is (nil? pagination-params))
       (is
        (=
         (examples-cleaned examples)
         [{:input "example1-1" :reference-output nil :tags #{} :source (aor-types/->ApiSourceImpl)}
          {:input  "example1-2"
           :reference-output "output1-2"
           :tags   #{"tag1" "tag2"}
           :source ar-source}]))
       (add-example-and-wait-java! manager
                                   ds-id1
                                   "snapshot1"
                                   "examples1-1"
                                   nil
                                   nil)
       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 "snapshot1" 10 nil))
       (is (nil? pagination-params))
       (is
        (=
         (examples-cleaned examples)
         [{:input "example1-1" :reference-output nil :tags #{} :source (aor-types/->ApiSourceImpl)}
          {:input  "example1-2"
           :reference-output "output1-2"
           :tags   #{"tag1" "tag2"}
           :source ar-source}
          {:input  "examples1-1"
           :reference-output nil
           :tags   #{}
           :source (aor-types/->ApiSourceImpl)}]))


       ;; verify original isn't affected
       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 nil 10 nil))
       (is (nil? pagination-params))
       (is
        (=
         (examples-cleaned examples)
         [{:input "example1-1" :reference-output nil :tags #{} :source (aor-types/->ApiSourceImpl)}
          {:input  "example1-2"
           :reference-output "output1-2"
           :tags   #{"tag1" "tag2"}
           :source ar-source}]))

       (aor/snapshot-dataset! manager ds-id1 "snapshot1" "snapshot2")
       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 "snapshot1" 10 nil))
       (is (nil? pagination-params))
       (is
        (=
         (examples-cleaned examples)
         [{:input "example1-1" :reference-output nil :tags #{} :source (aor-types/->ApiSourceImpl)}
          {:input  "example1-2"
           :reference-output "output1-2"
           :tags   #{"tag1" "tag2"}
           :source ar-source}
          {:input  "examples1-1"
           :reference-output nil
           :tags   #{}
           :source (aor-types/->ApiSourceImpl)}]))


       (bind [id1 id2 id3] (mapv :id examples))
       (verified-example-times
        ds-id1
        nil
        id1
        #(aor/set-dataset-example-input! manager ds-id1 id1 "!!example-1"))
       (aor/set-dataset-example-input! manager
                                       ds-id1
                                       id1
                                       "snapshot-example-1"
                                       {:snapshot "snapshot1"})
       (aor/set-dataset-example-reference-output! manager ds-id1 id1 "out1")

       (verified-example-times
        ds-id1
        "snapshot1"
        id1
        #(aor/set-dataset-example-reference-output! manager
                                                    ds-id1
                                                    id1
                                                    "snap-out-1"
                                                    {:snapshot "snapshot1"}))
       (verified-example-times
        ds-id1
        nil
        id1
        #(aor/add-dataset-example-tag! manager ds-id1 id1 "foo"))
       (aor/add-dataset-example-tag! manager ds-id1 id1 "bar")
       (verified-example-times
        ds-id1
        nil
        id1
        #(aor/remove-dataset-example-tag! manager ds-id1 id1 "foo"))
       (aor/add-dataset-example-tag! manager
                                     ds-id1
                                     id1
                                     "a"
                                     {:snapshot "snapshot1"})
       (aor/add-dataset-example-tag! manager
                                     ds-id1
                                     id1
                                     "b"
                                     {:snapshot "snapshot1"})
       (aor/add-dataset-example-tag! manager
                                     ds-id1
                                     id1
                                     "c"
                                     {:snapshot "snapshot1"})
       (aor/remove-dataset-example-tag! manager
                                        ds-id1
                                        id1
                                        "b"
                                        {:snapshot "snapshot1"})
       (aor/remove-dataset-example-tag! manager
                                        ds-id1
                                        id1
                                        "notatag"
                                        {:snapshot "snapshot1"})
       (verified-dataset-times
        ds-id1
        #(aor/remove-dataset-example! manager ds-id1 id2))
       (aor/remove-dataset-example! manager ds-id1 id3 {:snapshot "snapshot1"})


       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 nil 10 nil))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  "!!example-1"
                :reference-output "out1"
                :tags   #{"bar"}
                :source (aor-types/->ApiSourceImpl)}]))

       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 "snapshot1" 10 nil))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  "snapshot-example-1"
                :reference-output "snap-out-1"
                :tags   #{"a" "c"}
                :source (aor-types/->ApiSourceImpl)}
               {:input  "example1-2"
                :reference-output "output1-2"
                :tags   #{"tag1" "tag2"}
                :source ar-source}]))

       (is (= #{"snapshot1" "snapshot2"}
              (queries/get-dataset-snapshot-names pstate ds-id1)))
       (is (= #{}
              (queries/get-dataset-snapshot-names pstate ds-id2)))

       (verified-dataset-times
        ds-id1
        #(aor/remove-dataset-snapshot! manager ds-id1 "snapshot1"))
       (is (= #{"snapshot2"}
              (queries/get-dataset-snapshot-names pstate ds-id1)))
       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id1 "snapshot1" 10 nil))
       (is (nil? pagination-params))
       (is (empty? examples))


       ;; now verify schema checking
       (add-example-and-wait-async! manager
                                    ds-id3
                                    {"p1" [1 2 3]}
                                    {:reference-output "xyz"})
       (add-example-and-wait-async! manager
                                    ds-id3
                                    {"p1" []
                                     "p2" "abc"})


       (try
         (add-example-and-wait! manager ds-id3 {"p1" #{1 2 3}})
         (is false)
         (catch Exception e
           (is
            (h/contains-string? (-> e
                                    .getCause
                                    ex-data
                                    :info)
                                "x-javaType: $.p1 — expected java.util.List"))))

       (try
         (add-example-and-wait! manager ds-id3 {"p1" [] "p2" 3})
         (is false)
         (catch Exception e
           (is
            (h/contains-string? (-> e
                                    .getCause
                                    ex-data
                                    :info)
                                "$.p2: integer found, string expected"))))

       (try
         (add-example-and-wait! manager
                                ds-id3
                                {"p1" []}
                                {:reference-output 3})
         (is false)
         (catch Exception e
           (is
            (h/contains-string? (-> e
                                    .getCause
                                    ex-data
                                    :info)
                                "$: integer found, string expected"))))


       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id3 nil 10 nil))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  {"p1" [1 2 3]}
                :reference-output "xyz"
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  {"p1" []
                         "p2" "abc"}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}]))

       (bind [id1 id2] (mapv :id examples))

       (aor/set-dataset-example-input! manager ds-id3 id1 {"p1" [10]})
       (aor/set-dataset-example-reference-output! manager ds-id3 id1 "ww")

       (try
         (aor/set-dataset-example-input! manager ds-id3 id2 [1 2 3])
         (is false)
         (catch clojure.lang.ExceptionInfo e
           (is
            (h/contains-string? (-> e
                                    ex-data
                                    :info)
                                "$: array found, object expected"))))

       (try
         (aor/set-dataset-example-reference-output! manager ds-id3 id2 1)
         (is false)
         (catch clojure.lang.ExceptionInfo e
           (is
            (h/contains-string? (-> e
                                    ex-data
                                    :info)
                                "$: integer found, string expected"))))

       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id3 nil 10 nil))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  {"p1" [10]}
                :reference-output "ww"
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  {"p1" []
                         "p2" "abc"}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}]))


       (add-example-and-wait! manager
                              ds-id3
                              {"p1" [7]})
       (add-example-and-wait! manager
                              ds-id3
                              {"p1" [8]})
       (add-example-and-wait! manager
                              ds-id3
                              {"p1" [9]})

       (bind {:keys [examples pagination-params]}
         (get-examples-page ds-id3 nil 3 nil))
       (is (some? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  {"p1" [10]}
                :reference-output "ww"
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  {"p1" []
                         "p2" "abc"}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  {"p1" [7]}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
              ]))

       (bind {:keys [examples pagination-params]}
         (get-examples-page
          ds-id3
          nil
          3
          pagination-params))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  {"p1" [8]}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
               {:input  {"p1" [9]}
                :reference-output nil
                :tags   #{}
                :source (aor-types/->ApiSourceImpl)}
              ]))

       (aor/destroy-dataset! manager ds-id1)
       (is (nil? (queries/get-dataset-properties pstate ds-id1)))

       (bind page (foreign-invoke-query page-query 1000 nil))
       (is (= "Dataset sample 2"
              (-> page
                  :datasets
                  last
                  :name)))


       ;; now test bulk upload
       (bind tmpdir
         (java.nio.file.Files/createTempDirectory
          "aor-jsonl-test"
          (make-array java.nio.file.attribute.FileAttribute 0)))
       (.deleteOnExit (.toFile tmpdir))
       (bind jsonl-path (.resolve tmpdir "examples.jsonl"))
       (bind jsonl-file (.toFile jsonl-path))
       (.deleteOnExit jsonl-file)

       (bind l1 "{\"input\":\"a\",\"output\":\"x\",\"tags\":[\"t1\",\"t2\"]}")
       (bind l3 "{\"input\":\"bad\"") ; malformed JSON
       (bind l4 "{\"input\":\"c\",\"output\":\"z\",\"tags\":5}") ; invalid tags
       (bind l5 "{\"input\":\"d\",\"output\":\"w\",\"tags\":[\"ok\",7]}") ; invalid
       (bind l6 "{\"input\":4,\"output\":\"w\",\"tags\":[\"ok\",7]}") ; invalid
       (bind l2 "{\"input\":\"b\"}")
       (bind lines [l1 l3 l4 l5 l6 l2])

       (with-open [w (io/writer jsonl-file :encoding "UTF-8")]
         (doseq [l lines]
           (.write w ^String l)
           (.write w "\n")))

       (bind failures* (atom []))
       (datasets/upload-jsonl-examples!
        manager
        ds-id4
        nil
        (.toString jsonl-path)
        (fn [line ex]
          (swap! failures* conj [line ex])))
       (is (= 4 (count @failures*))
           "Should record exactly 3 per-line failures")

       (is (= #{l3 l4 l5 l6} (set (map first @failures*))))
       (doseq [[line ex] @failures*]
         (is (string? line))
         (is (instance? Throwable ex)))

       (bind {:keys [examples pagination-params]}
         (get-examples-page
          ds-id4
          nil
          100
          pagination-params))
       (is (nil? pagination-params))
       (is (= (examples-cleaned examples)
              [{:input  "a"
                :reference-output "x"
                :tags   #{"t1" "t2"}
                :source (aor-types/->BulkUploadSourceImpl)}
               {:input  "b"
                :reference-output nil
                :tags   #{}
                :source (aor-types/->BulkUploadSourceImpl)}
              ]))

       (is (= (into {} (for [e examples] [(:id e) (dissoc e :id)]))
              (foreign-invoke-query multi-examples-query
                                    ds-id4
                                    nil
                                    (mapv :id examples))))

       (bind less-examples (butlast examples))
       (is (= (into {} (for [e less-examples] [(:id e) (dissoc e :id)]))
              (foreign-invoke-query multi-examples-query
                                    ds-id4
                                    nil
                                    (mapv :id less-examples))))

       ;; test download-jsonl-examples! pagination
       (bind ds-id-pagination
         (create-and-wait! manager
                           "Pagination test dataset"
                           {:input-json-schema (to-json schema-str)}))

       ;; Add 3 examples to force pagination with batch size 2
       (add-example-and-wait! manager
                              ds-id-pagination
                              "example1"
                              {:reference-output "output1" :tags #{"tag1"}})
       (add-example-and-wait! manager ds-id-pagination "example2" {:tags #{"tag2"}})
       (add-example-and-wait! manager
                              ds-id-pagination
                              "example3"
                              {:reference-output "output3" :tags #{"tag1" "tag3"}})

       (bind pagination-download-path (.resolve tmpdir "pagination-test.jsonl"))
       (bind pagination-download-file (.toFile pagination-download-path))
       (.deleteOnExit pagination-download-file)

       (bind download-failures* (atom []))

       ;; Test with batch size 2 to force pagination across 3 examples
       (with-redefs [datasets/download-jsonl-batch-size (constantly 2)]
         (datasets/download-jsonl-examples!
          manager
          ds-id-pagination
          nil
          (.toString pagination-download-path)
          (fn [example-id ex]
            (swap! download-failures* conj [example-id ex]))))

       (is (empty? @download-failures*) "Should have no download failures")

       ;; verify all 3 examples were downloaded despite pagination
       (bind downloaded-lines
         (with-open [r (io/reader pagination-download-file :encoding "UTF-8")]
           (vec (line-seq r))))

       (is (= 3 (count downloaded-lines))
           "Should have downloaded exactly 3 examples with pagination")

       ;; parse and check all examples are present
       (bind parsed-lines
         (mapv #(j/read-value %) downloaded-lines))

       (is (= [{"input" "example1" "output" "output1" "tags" #{"tag1"}}
               {"input" "example2" "tags" #{"tag2"}}
               {"input" "example3" "output" "output3" "tags" #{"tag1" "tag3"}}]
              (mapv #(update % "tags" set) parsed-lines))
           "All examples should be downloaded correctly with pagination")

       ;; test search with filters
       (bind human-source (aor-types/->HumanSourceImpl "user"))
       (bind ai-source (aor-types/->AiSourceImpl))

       (binding [aor-types/OPERATION-SOURCE human-source]
         (add-example-and-wait! manager
                                ds-id5
                                "hello how are you"
                                {:reference-output "abc"
                                 :tags #{"a" "b"}}))
       (binding [aor-types/OPERATION-SOURCE ai-source]
         (add-example-and-wait! manager
                                ds-id5
                                "how are you"
                                {:tags #{"a"}}))
       (add-example-and-wait! manager
                              ds-id5
                              "apple banana")
       (add-example-and-wait! manager
                              ds-id5
                              "hello banana")
       (binding [aor-types/OPERATION-SOURCE human-source]
         (add-example-and-wait! manager
                                ds-id5
                                "the man said apple"
                                {:reference-output "children"
                                 :tags #{"a"}}))
       (binding [aor-types/OPERATION-SOURCE ai-source]
         (add-example-and-wait! manager
                                ds-id5
                                (UserMessage. "apple")
                                {:reference-output (UserMessage. "grOUcho")}))


       (bind {:keys [examples pagination-params]}
         (foreign-invoke-query
          search-examples-query
          ds-id5
          nil
          {:tag "a"}
          2
          nil
         ))
       (is (= (examples-cleaned examples)
              [{:input  "hello how are you"
                :reference-output "abc"
                :tags   #{"a" "b"}
                :source human-source}
               {:input  "how are you"
                :reference-output nil
                :tags   #{"a"}
                :source ai-source}
              ]))
       (bind {:keys [examples pagination-params]}
         (foreign-invoke-query
          search-examples-query
          ds-id5
          nil
          {:tag "a"}
          2
          pagination-params
         ))
       (is (= (examples-cleaned examples)
              [{:input  "the man said apple"
                :reference-output "children"
                :tags   #{"a"}
                :source human-source}
              ]))
       (is (nil? pagination-params))

       (bind {:keys [examples pagination-params]}
         (foreign-invoke-query
          search-examples-query
          ds-id5
          nil
          {:tag "a" :source "human"}
          3
          nil
         ))
       (is (= (examples-cleaned examples)
              [{:input  "hello how are you"
                :reference-output "abc"
                :tags   #{"a" "b"}
                :source human-source}
               {:input  "the man said apple"
                :reference-output "children"
                :tags   #{"a"}
                :source human-source}
              ]))
       (is (nil? pagination-params))


       (bind {:keys [examples pagination-params]}
         (foreign-invoke-query
          search-examples-query
          ds-id5
          nil
          {:search-string "GROUCHO"}
          3
          nil
         ))
       (is (= (examples-cleaned examples)
              [{:input  (UserMessage. "apple")
                :reference-output (UserMessage. "grOUcho")
                :tags   #{}
                :source ai-source}
              ]))
       (is (nil? pagination-params))
      ))))
