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
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]))

(defrecord MockChatModel []
  ChatModel
  (doChat [this request]
    (let [^UserMessage m (-> request
                             .messages
                             last)]
      (-> (ChatResponse$Builder.)
          (.aiMessage (AiMessage. (j/write-value-as-string
                                   {"temperature" (.temperature request)
                                    "message"     (.singleText m)
                                    ;; TODO: <<<<>>>> add
                                    ;; responseFormat().jsonSchema() here
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



     (aor/create-evaluator! manager
                            "ajudge"
                            "aor/llm-judge"
                            {"prompt"
                             "1 %input 2 %referenceOutput 3 %output 4 %input"
                             "model"        "my-model"
                             "temperature"  "1.2"
                             ;; TODO: <<<<>>>> add output schema
                             "outputSchema" "{}"
                            }
                            "a judge")

     (is (= {"message" "1 AB 2 CD 3 EF 4 AB" "temperature" 1.2}
            (aor/try-evaluator manager
                               "ajudge"
                               "AB"
                               "CD"
                               "EF")))

     ;; TODO: <<<<>>>> test summary and comparative declarations and tries (both
     ;; clojure and Java for declarations)
    )))
