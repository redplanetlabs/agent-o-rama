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
   [com.rpl.test-common :as tc]))

(deftest evaluator-operations-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-evaluator-builder
         topology
         "concise-10"
         "Concise 10 limit"
         (fn [params]
           (fn [{:strs [input output referenceOutput]}]
             (let [len (+ (count input) (count output) (count referenceOutput))]
               {"concise?" (< len 10)}
             ))))
        (aor/declare-evaluator-builder
         topology
         "concise-x"
         "Concise X limit"
         (fn [params]
           (let [target (Long/parseLong (get params "len"))]
             (fn [{:strs [input output referenceOutput]}]
               (let [len (+ (count input)
                            (count output)
                            (count referenceOutput))]
                 {"concise?"     (< len target)
                  "not-concise?" (>= len target)}
               ))))
         {:params       {"len" "the target length"}
          :input-path?  true
          :output-path? false
          :reference-output-path? false})
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


     (is (= {"concise?" false "not-concise?" true}
            (aor/try-evaluator manager "x1 def" {"output" "..."})))
     (is (= {"concise?" true "not-concise?" false}
            (aor/try-evaluator manager "x1 def" {"output" "...."})))



     (aor/remove-evaluator! manager "not-an-eval")
     (aor/remove-evaluator! manager "x1 def")
     (is (= #{"abc2 def"} (aor/search-evaluators manager "def")))

     ;; TODO: <<<<>>>>>
     ;; - complete clojure API for declare and client methods for create,
     ;; delete, search
     ;; - and java API declareEvaluatorBuilder
     ;; - need to verify building/caching on each task is working
     ;;   - does it need a lock? how is it actually accessed for experiments?

     ;; TODO: <<<<>>>> use query topology to try out an evaluator
     ;;   - runs on virtual thread with agent node so can access objects
     ;;     - but shouldn't be able to emit... maybe instead of agent node, it's
     ;;     just fetcher
     ;;       - and make that interface public
     ;;       - still need to make sure to release those objects...
     ;;       - shoudl sub-agents be accessible? seems like yes
     ;;     - expose new interface that's union of those two interfaces
     ;;       - still separate interfaces since setup only has one of them
     ;;     - UI should look up evaluator builder to determine what
     ;;     input/output/refOutput to prompt for


     ;; TODO: <<<<>>>> tests that default evals work
     ;;   - can make a mock chat model

    )))
