(ns com.rpl.evaluators-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
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
   [com.rpl.aortest
    TestSnippets]
   [dev.langchain4j.data.message
    AiMessage
    SystemMessage
    ToolExecutionResultMessage
    UserMessage]
   [dev.langchain4j.model.chat
    ChatModel]
   [dev.langchain4j.model.chat.request.json
    JsonRawSchema]
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]))

(defn- raw-schema
  [^JsonRawSchema s]
  (.schema s))

(defrecord MockChatModel []
  ChatModel
  (doChat [this request]
    (let [^UserMessage m (-> request
                             .messages
                             last)]
      (-> (ChatResponse$Builder.)
          (.aiMessage (AiMessage. (j/write-value-as-string
                                   {"temperature"  (.temperature request)
                                    "message"      (.singleText m)
                                    "outputSchema" (-> request
                                                       .responseFormat
                                                       .jsonSchema
                                                       .rootElement
                                                       raw-schema)
                                   })))
          .build))))

(deftest evaluator-operations-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-agent-object-builder
         topology
         "my-model"
         (fn [setup] (->MockChatModel)))
        (aor/declare-evaluator-builder
         topology
         "concise-10"
         "Concise 10 limit"
         (fn [params]
           (fn [fetcher input ref-output output]
             (let [len (+ (count input) (count output) (count ref-output))]
               {"concise?" (< len 10)}
             ))))
        (aor/declare-evaluator-builder
         topology
         "concise-x"
         "Concise X limit"
         (fn [params]
           (let [target (Long/parseLong (get params "len"))]
             (fn [fetcher input ref-output output]
               (let [len (+ (count input)
                            (count output)
                            (count ref-output))]
                 {"concise?"     (<= len target)
                  "not-concise?" (> len target)}
               ))))
         {:params       {"len" {:description "the target length"}}
          :input-path?  true
          :output-path? false
          :reference-output-path? false})

        (aor/declare-comparative-evaluator-builder
         topology
         "compare1"
         "A comparator"
         (fn [params]
           (fn [fetcher input ref-output outputs]
             (let [v (cond (< input ref-output)
                           (nth outputs 0)

                           (> input ref-output)
                           (nth outputs 2)

                           :else
                           (nth outputs 1))]
               {"res" v}))))
        (aor/declare-comparative-evaluator-builder
         topology
         "compare2"
         "Another comparator"
         (fn [{:strs [extra]}]
           (fn [fetcher input ref-output outputs]
             (let [v (cond (< input ref-output)
                           (nth outputs 0)

                           (> input ref-output)
                           (nth outputs 2)

                           :else
                           (nth outputs 1))]
               {"res"   v
                "extra" extra})))
         {:params {"extra" {:description "extra value"}}})


        (aor/declare-summary-evaluator-builder
         topology
         "sum1"
         "Summary comparator"
         (fn [params]
           (fn [fetcher example-runs]
             {"res"
              (reduce
               (fn [res {:keys [input reference-output output]}]
                 (+ res input reference-output output))
               0
               example-runs)}
           )))
        (aor/declare-summary-evaluator-builder
         topology
         "sum2"
         "Summary comparator"
         (fn [{:strs [extra]}]
           (fn [fetcher example-runs]
             {"res"
              (reduce
               (fn [res {:keys [input reference-output output]}]
                 (+ res input reference-output output))
               (parse-long extra)
               example-runs)}))
         {:params {"extra" {:description "extra value"}}})

        (TestSnippets/declareEvaluatorBuilders topology)
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
     (bind builders-query
       (foreign-query ipc module-name (queries/all-evaluator-builders-name)))

     (bind builders
       (foreign-invoke-query builders-query))
     (is (contains? builders "aor/llm-judge"))
     (is (contains? builders "aor/conciseness"))
     (is (contains? builders "concise-x"))
     (is (contains? builders "concise-10"))
     (is (contains? builders "jeb1"))
     (is (contains? builders "jeb2"))


     (aor/create-evaluator! manager "abc" "concise-10" {} "my eval 1")
     (aor/create-evaluator! manager
                            "abc2 def"
                            "concise-10"
                            {}
                            "my eval 2"
                            {:input-json-path  "$.a"
                             :output-json-path "$.b"
                             :reference-output-json-path "$"})
     (aor/create-evaluator! manager
                            "x1 def"
                            "concise-x"
                            {"len" "3"}
                            "my eval 3")


     (try
       (aor/create-evaluator! manager "abc" "concise-10" {} "invalid")
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is (h/contains-string? (ex-message e) "Evaluator already exists"))
       ))
     (try
       (aor/create-evaluator! manager
                              "invalid-x"
                              "concise-x"
                              {"len" "abc"}
                              "invalid")
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is (h/contains-string? (ex-message e) "NumberFormatException"))
       ))
     (try
       (aor/create-evaluator! manager
                              "invalid"
                              "concise-10" {}
                              ""
                              {:input-json-path "$$"})
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is (h/contains-string? (ex-message e) "Invalid input JSON path"))
       ))
     (try
       (aor/create-evaluator! manager
                              "invalid"
                              "concise-10" {}
                              ""
                              {:output-json-path "$$"})
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is (h/contains-string? (ex-message e) "Invalid output JSON path"))
       ))
     (try
       (aor/create-evaluator! manager
                              "invalid"
                              "concise-10" {}
                              ""
                              {:reference-output-json-path "$$"})
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is (h/contains-string? (ex-message e)
                                 "Invalid reference output JSON path"))
       ))

     (is (= #{"abc2 def" "x1 def"} (aor/search-evaluators manager "def")))
     (is (= #{"x1 def"} (aor/search-evaluators manager "x1")))
     (is (= #{"abc2 def" "abc"} (aor/search-evaluators manager "abc")))
     (is (= #{} (aor/search-evaluators manager "invalid")))


     (is (= {"concise?" true "not-concise?" false}
            (aor/try-evaluator manager "x1 def" nil nil "...")))
     (is (= {"concise?" false "not-concise?" true}
            (aor/try-evaluator manager "x1 def" nil nil "....")))


     (aor/remove-evaluator! manager "not-an-eval")
     (aor/remove-evaluator! manager "x1 def")
     (is (= #{"abc2 def"} (aor/search-evaluators manager "def")))


     (aor/create-evaluator! manager "j1" "jeb1" {} "my java eval")
     (aor/create-evaluator! manager
                            "j2"
                            "jeb2"
                            {"foo1" "10" "foo2" "100"}
                            "my java eval 2")

     (is (= {"score" 56} (aor/try-evaluator manager "j1" "a" 50 "abcde")))
     (is (= {"score" 166} (aor/try-evaluator manager "j2" "a" 50 "abcde")))


     ;; verify cache gets reset since params changed
     (aor/create-evaluator! manager
                            "x1 def"
                            "concise-x"
                            {"len" "2"}
                            "my eval 3")
     (is (= {"concise?" true "not-concise?" false}
            (aor/try-evaluator manager "x1 def" nil nil "..")))
     (is (= {"concise?" false "not-concise?" true}
            (aor/try-evaluator manager "x1 def" nil nil "...")))


     ;; verify default evals
     (aor/create-evaluator! manager
                            "aconcise6"
                            "aor/conciseness"
                            {"threshold" "6"}
                            "built-in")

     (is (= {"concise?" true}
            (aor/try-evaluator manager "aconcise6" nil nil ".....")))
     (is (= {"concise?" true}
            (aor/try-evaluator manager "aconcise6" nil nil "......")))
     (is (= {"concise?" false}
            (aor/try-evaluator manager "aconcise6" nil nil ".......")))
     (is (= {"concise?" true}
            (aor/try-evaluator manager "aconcise6" nil nil nil)))
     (is
      (= {"concise?" true}
         (aor/try-evaluator manager "aconcise6" nil nil (AiMessage. "......"))))
     (is
      (=
       {"concise?" false}
       (aor/try-evaluator manager "aconcise6" nil nil (AiMessage. "......."))))
     (is
      (= {"concise?" true}
         (aor/try-evaluator manager
                            "aconcise6"
                            nil
                            nil
                            (SystemMessage. "......"))))
     (is
      (=
       {"concise?" false}
       (aor/try-evaluator manager
                          "aconcise6"
                          nil
                          nil
                          (SystemMessage. "......."))))
     (is
      (= {"concise?" true}
         (aor/try-evaluator
          manager
          "aconcise6"
          nil
          nil
          (ToolExecutionResultMessage. "id" "name" "......"))))
     (is
      (=
       {"concise?" false}
       (aor/try-evaluator manager
                          "aconcise6"
                          nil
                          nil
                          (ToolExecutionResultMessage. "id" "name" "......."))))
     (is
      (=
       {"concise?" true}
       (aor/try-evaluator manager "aconcise6" nil nil (UserMessage. "......"))))
     (is
      (=
       {"concise?" false}
       (aor/try-evaluator manager
                          "aconcise6"
                          nil
                          nil
                          (UserMessage. "......."))))


     (bind os
       "{
 \"type\": \"object\",
 \"properties\": {
   \"aaa\": { \"type\": \"string\" }
 },
 \"required\": [\"aaa\"],
 \"additionalProperties\": false
}")

     (aor/create-evaluator! manager
                            "ajudge"
                            "aor/llm-judge"
                            {"prompt"
                             "1 %input 2 %referenceOutput 3 %output 4 %input"
                             "model"        "my-model"
                             "temperature"  "1.2"
                             "outputSchema" os
                            }
                            "a judge")

     (is
      (= {"message" "1 AB 2 CD 3 EF 4 AB" "temperature" 1.2 "outputSchema" os}
         (aor/try-evaluator manager
                            "ajudge"
                            "AB"
                            "CD"
                            "EF")))

     (try
       (aor/create-evaluator! manager
                              "ajudge"
                              "compare1"
                              {}
                              "my comparator")
       (is false)
       (catch clojure.lang.ExceptionInfo e
         (is
          (h/contains-string? (ex-message e) "Evaluator already exists"))))

     (aor/create-evaluator! manager
                            "myc"
                            "compare1"
                            {}
                            "my comparator")
     (aor/create-evaluator! manager
                            "myc2"
                            "compare2"
                            {"extra" "99"}
                            "my comparator")

     (is (thrown? clojure.lang.ExceptionInfo
                  (aor/try-evaluator manager "myc" 1 2 [:a :b :c])))

     (is (= {"res" :a}
            (aor/try-comparative-evaluator manager "myc" 1 2 [:a :b :c])))
     (is (= {"res" :b}
            (aor/try-comparative-evaluator manager "myc" 1 1 [:a :b :c])))
     (is (= {"res" :c}
            (aor/try-comparative-evaluator manager "myc" 2 1 [:a :b :c])))
     (is (= {"res" :a "extra" "99"}
            (aor/try-comparative-evaluator manager "myc2" 1 2 [:a :b :c])))
     (is (= {"res" :b "extra" "99"}
            (aor/try-comparative-evaluator manager "myc2" 1 1 [:a :b :c])))
     (is (= {"res" :c "extra" "99"}
            (aor/try-comparative-evaluator manager "myc2" 2 1 [:a :b :c])))


     (is (thrown? clojure.lang.ExceptionInfo
                  (aor/create-evaluator! manager
                                         "ajudge"
                                         "sum1"
                                         {}
                                         "my summer")))

     (aor/create-evaluator! manager
                            "sum1"
                            "sum1"
                            {}
                            "my summer")

     (aor/create-evaluator! manager
                            "sum2"
                            "sum2"
                            {"extra" "10"}
                            "my summer 2")

     (is (thrown? clojure.lang.ExceptionInfo
                  (aor/try-evaluator manager "sum1" 1 2 [:a :b :c])))

     (is (= {"res" 21}
            (aor/try-summary-evaluator manager
                                       "sum1"
                                       [(aor/mk-example-run 1 2 3)
                                        (aor/mk-example-run 4 5 6)])))
     (is (= {"res" 31}
            (aor/try-summary-evaluator manager
                                       "sum2"
                                       [(aor/mk-example-run 1 2 3)
                                        (aor/mk-example-run 4 5 6)])))



     (aor/create-evaluator! manager
                            "myjc"
                            "jcompare1"
                            {}
                            "my comparator")
     (aor/create-evaluator! manager
                            "myjc2"
                            "jcompare2"
                            {"extra" "99"}
                            "my comparator")

     (is (= {"res" :a}
            (aor/try-comparative-evaluator manager "myjc" 1 2 [:a :b :c])))
     (is (= {"res" :b}
            (aor/try-comparative-evaluator manager "myjc" 1 1 [:a :b :c])))
     (is (= {"res" :c}
            (aor/try-comparative-evaluator manager "myjc" 2 1 [:a :b :c])))
     (is (= {"res" :a "extra" "99"}
            (aor/try-comparative-evaluator manager "myjc2" 1 2 [:a :b :c])))
     (is (= {"res" :b "extra" "99"}
            (aor/try-comparative-evaluator manager "myjc2" 1 1 [:a :b :c])))
     (is (= {"res" :c "extra" "99"}
            (aor/try-comparative-evaluator manager "myjc2" 2 1 [:a :b :c])))



     (aor/create-evaluator! manager
                            "jsum1"
                            "jsum1"
                            {}
                            "my summer")

     (aor/create-evaluator! manager
                            "jsum2"
                            "jsum2"
                            {"extra" "10"}
                            "my summer 2")


     (is (= {"res" 21}
            (aor/try-summary-evaluator manager
                                       "jsum1"
                                       [(aor/mk-example-run 1 2 3)
                                        (aor/mk-example-run 4 5 6)])))
     (is (= {"res" 31}
            (aor/try-summary-evaluator manager
                                       "jsum2"
                                       [(aor/mk-example-run 1 2 3)
                                        (aor/mk-example-run 4 5 6)])))


     (aor/create-evaluator! manager
                            "myf1"
                            "aor/f1-score"
                            {"positiveValue" "+"}
                            "my f1 score")

     (is (= {"score" 1.0 "precision" 1.0 "recall" 1.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "+" "+")
                                        (aor/mk-example-run nil "-" "-")
                                        (aor/mk-example-run nil "+" "+")])))
     (is (= {"score" (double (/ 2 3)) "precision" 0.5 "recall" 1.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "+" "+")
                                        (aor/mk-example-run nil "-" "+")])))
     (is (= {"score" 0.0 "precision" 0.0 "recall" 0.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "+" "-")])))
     (is (= {"score" 0.0 "precision" 0.0 "recall" 0.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "-" "-")
                                        (aor/mk-example-run nil "-" "-")
                                        (aor/mk-example-run nil "-" "-")])))
     (is (= {"score" 0.5 "precision" 0.5 "recall" 0.5}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "+" "+")
                                        (aor/mk-example-run nil "-" "+")
                                        (aor/mk-example-run nil "+" "-")])))
     (is (= {"score" 0.0 "precision" 0.0 "recall" 0.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [])))
     (is (= {"score" 0.0 "precision" 0.0 "recall" 0.0}
            (aor/try-summary-evaluator manager
                                       "myf1"
                                       [(aor/mk-example-run nil "-" "+")
                                        (aor/mk-example-run nil "-" "+")
                                        (aor/mk-example-run nil "-" "+")])))
    )))

(deftest evaluators-search-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-comparative-evaluator-builder
         topology
         "compare"
         ""
         (fn [params]
           (fn [fetcher input ref-output outputs]
             {"res" "done"})))
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
     (bind search
       (foreign-query ipc module-name (queries/search-evaluators-name)))

     (bind regular!
       (fn [name]
         (aor/create-evaluator! manager
                                name
                                "aor/conciseness"
                                {"threshold" "6"}
                                "")))

     (bind comparative!
       (fn [name]
         (aor/create-evaluator! manager
                                name
                                "compare"
                                {}
                                "")))

     (bind summary!
       (fn [name]
         (aor/create-evaluator! manager
                                name
                                "aor/f1-score"
                                {"positiveValue" "+"}
                                "")))

     (bind page-matches?
       (fn [{:keys [items pagination-params]} expected last-key]
         (let [items (transform ALL #(select-keys % [:name :type]) items)]
           (and (= items expected) (= pagination-params last-key))
         )))


     (summary! "f1")
     (regular! "hello")
     (comparative! "my compare hello")
     (regular! "my first eval hello")
     (regular! "my second eval")
     (summary! "nnn f1 my")
     (comparative! "ooo")
     (comparative! "p compare")
     (summary! "q sum hello")
     (regular! "zzz my hello")

     (bind res (foreign-invoke-query search nil 3 nil))
     (is (page-matches?
          res
          [{:name "f1" :type :summary}
           {:name "hello" :type :regular}
           {:name "my compare hello" :type :comparative}]
          "my compare hello"))
     (bind res (foreign-invoke-query search nil 3 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "my first eval hello" :type :regular}
           {:name "my second eval" :type :regular}
           {:name "nnn f1 my" :type :summary}
          ]
          "nnn f1 my"))
     (bind res (foreign-invoke-query search nil 3 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "ooo" :type :comparative}
           {:name "p compare" :type :comparative}
           {:name "q sum hello" :type :summary}
          ]
          "q sum hello"))
     (bind res (foreign-invoke-query search nil 3 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "zzz my hello" :type :regular}
          ]
          nil))

     (bind filters {:search-string "hello"})
     (bind res (foreign-invoke-query search filters 2 nil))
     (is (page-matches?
          res
          [{:name "hello" :type :regular}
           {:name "my compare hello" :type :comparative}
           {:name "my first eval hello" :type :regular}
          ]
          "my first eval hello"))
     (bind res (foreign-invoke-query search filters 2 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "q sum hello" :type :summary}
           {:name "zzz my hello" :type :regular}]
          "zzz my hello"))
     (bind res (foreign-invoke-query search filters 2 (:pagination-params res)))
     (is (page-matches?
          res
          []
          nil))


     (bind filters {:search-string "hello" :types #{:regular :comparative}})
     (bind res (foreign-invoke-query search filters 2 nil))
     (is (page-matches?
          res
          [{:name "hello" :type :regular}
           {:name "my compare hello" :type :comparative}
           {:name "my first eval hello" :type :regular}
          ]
          "my first eval hello"))
     (bind res (foreign-invoke-query search filters 2 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "zzz my hello" :type :regular}]
          nil))

     (bind filters {:types #{:summary}})
     (bind res (foreign-invoke-query search filters 2 nil))
     (is (page-matches?
          res
          [{:name "f1" :type :summary}
           {:name "nnn f1 my" :type :summary}
          ]
          "nnn f1 my"))
     (bind res (foreign-invoke-query search filters 2 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "q sum hello" :type :summary}]
          nil))

     (bind filters {:types #{:regular}})
     (bind res (foreign-invoke-query search filters 3 nil))
     (is (page-matches?
          res
          [{:name "hello" :type :regular}
           {:name "my first eval hello" :type :regular}
           {:name "my second eval" :type :regular}
          ]
          "nnn f1 my"))
     (bind res (foreign-invoke-query search filters 3 (:pagination-params res)))
     (is (page-matches?
          res
          [{:name "zzz my hello" :type :regular}
          ]
          nil))
    )))
