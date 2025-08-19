(ns com.rpl.datasets-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
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
   [com.rpl.test-common :as tc])
  (:import
   (com.networknt.schema ValidationMessage)))


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
       (bind depot (foreign-depot ipc module-name (po/datasets-depot-name)))
       (bind pstate
         (foreign-pstate ipc module-name (po/datasets-task-global-name)))
       (bind page-query
         (foreign-query ipc
                        module-name
                        (queries/get-datasets-page-query-name)))
       (bind search-query
         (foreign-query ipc
                        module-name
                        (queries/search-datasets-name)))

       ;; so the UUID7s have separate timestamps
       (bind append-and-wait!
         (fn [depot record]
           (foreign-append! depot record)
           (Thread/sleep 2)))

       (bind ds-id1 (h/random-uuid7))
       (bind ds-id2 (h/random-uuid7))
       (bind ds-id3 (h/random-uuid7))
       (bind ds-id4 (h/random-uuid7))
       (bind ds-id5 (h/random-uuid7))
       (bind ds-id6 (h/random-uuid7))
       (bind ds-id7 (h/random-uuid7))
       (bind ds-id8 (h/random-uuid7))

       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id1
                                         "Dataset 1 is a dataset"
                                         "this is a dataset"))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id2 "Dataset sample 2" nil))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id3
                                         "Dataset 3 – sample of inputs"
                                         "this is a description"))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id4
                                         "Dataset 4 sample of movies"
                                         "a description"))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id5
                                         "Dataset 5 sample of books"
                                         nil))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id6
                                         "Dataset 6 sampleof vaudeville"
                                         "a description 6"))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id7
                                         "Dataset 7 is another dataset"
                                         "a description"))
       (append-and-wait!
        depot
        (aor-types/->valid-CreateDataset ds-id8
                                         "Dataset 8"
                                         nil))

       (doseq [[s query-amt amt] [["dataset" 3 3]
                                  ["is a" 3 2]
                                  ["SAMPLEof" 1000 1]
                                  ["sample" 6 5]]]
         (let [res (foreign-invoke-query search-query s query-amt)]
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
       (is (= (setval [ALL :task-id] NONE items)
              [{:dataset-id  ds-id8
                :name        "Dataset 8"
                :description nil}
               {:dataset-id  ds-id7
                :name        "Dataset 7 is another dataset"
                :description "a description"}
               {:dataset-id  ds-id6
                :name        "Dataset 6 sampleof vaudeville"
                :description "a description 6"}
               {:dataset-id  ds-id5
                :name        "Dataset 5 sample of books"
                :description nil}
               {:dataset-id  ds-id4
                :name        "Dataset 4 sample of movies"
                :description "a description"}
               {:dataset-id  ds-id3
                :name        "Dataset 3 – sample of inputs"
                :description "this is a description"}
               {:dataset-id  ds-id2
                :name        "Dataset sample 2"
                :description nil}
               {:dataset-id  ds-id1
                :name        "Dataset 1 is a dataset"
                :description "this is a dataset"}
              ]))



       ;; TODO: <<<<>>>>
       ;; (defn get-dataset-properties [datasets-pstate dataset-id]
       ;; (defn get-dataset-snapshot-names [datasets-pstate dataset-id]
       ;; (defn get-dataset-examples-page
       ;;   [datasets-pstate dataset-id snapshot-name amt pagination-params]
       ;; - UpdateDatasetProperty
       ;; - DestroyDataset
       ;; - AddDatasetExample
       ;; - UpdateDatasetExample
       ;; - RemoveDatasetExample
       ;; - AddDatasetExampleTag
       ;; - RemoveDatasetExampleTag
       ;; - DatasetSnapshot
       ;; - RemoveDatasetSnapshot
       ;; - declare-get-datasets-page-topology
      ))))

; (drp/defrecord+ UpdateDatasetProperty
;   [dataset-id :- UUID
;    key :- clojure.lang.Keyword
;    value :- Object])
;
; (drp/defrecord+ DestroyDataset
;   [dataset-id :- UUID])
;
; (drp/defrecord+ AddDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    input :- [Object]
;    reference-output :- Object
;    tags :- (s/maybe #{String})
;   ])
;
; (drp/defrecord+ UpdateDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    key :- clojure.lang.Keyword
;    value :- Object])
;
; (drp/defrecord+ RemoveDatasetExample
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID])
;
; (drp/defrecord+ AddDatasetExampleTag
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    tag :- String])
;
; (drp/defrecord+ RemoveDatasetExampleTag
;   [dataset-id :- UUID
;    snapshot-name :- (s/maybe String)
;    example-id :- UUID
;    tag :- String])
;
; (drp/defrecord+ DatasetSnapshot
;   [dataset-id :- UUID
;    from-snapshot-name :- (s/maybe String)
;    to-snapshot-name :- String])
;
; (drp/defrecord+ RemoveDatasetSnapshot
;   [dataset-id :- UUID
;    snapshot-name :- String])
