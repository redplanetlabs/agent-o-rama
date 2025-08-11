(ns com.rpl.tools-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [jsonista.core :as j]
   [meander.epsilon :as m])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]
   [dev.langchain4j.data.message
    ToolExecutionResultMessage]))

(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "add"
     (lj/object
      {"a" (lj/number "first number")
       "b" (lj/number "second number")})
     "Add two numbers together")
    (fn [args] (+ (get args "a") (get args "b"))))
   (tools/tool-info
    (tools/tool-specification
     "math-with-context"
     (lj/object
      {"a" (lj/number "first number")
       "b" (lj/number "second number")
       "c" (lj/number "third number")})
     "(a-1)*(b+1)*caller-data+c")
    (fn [agent-node caller-data args]
      (aor/record-nested-op!
       agent-node
       :other
       10
       11
       {"caller-data" caller-data
        "args"        args})
      (+ (get args "c")
         (* (-> args
                (get "a")
                dec)
            (-> args
                (get "b")
                inc)
            caller-data)))
    {:include-context? true})
   (tools/tool-info
    (tools/tool-specification
     "throw"
     (lj/object
      {"type" (lj/string)}))
    (fn [args]
      (let [type (get args "type")]
        (condp = type
          "arith" (throw (ArithmeticException. "intentional"))
          "ex-info" (throw (ex-info "ex-info" {}))
          (throw (ClassCastException. "cce"))
        ))))
  ])

(defn mk-request
  [tool-name id args]
  (-> (ToolExecutionRequest/builder)
      (.id id)
      (.arguments (j/write-value-as-string args))
      (.name tool-name)
      .build))

(defn res=
  [^ToolExecutionResultMessage res id name text]
  (let [t (.text res)]
    (and (= id (.id res))
         (= name (.toolName res))
         (if (string? text)
           (= text t)
           (some? (re-matches text t)
           )))))

(deftest tools-test
  (with-redefs [anode/log-node-error (fn [& args])
                aor-types/get-config (max-retries-override 0)]
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
               (fn [agent-node tools-agent-name caller-data requests]
                 (let [tools (aor/agent-client agent-node tools-agent-name)]
                   (aor/result!
                    agent-node
                    (if caller-data
                      (aor/agent-invoke tools requests caller-data)
                      (aor/agent-invoke tools requests)))
                 ))))
          (tools/new-tools-agent topology "tools1" TOOLS)
          (tools/new-tools-agent topology
                                 "tools2"
                                 TOOLS
                                 {:error-handler
                                  (tools/error-handler-static-string "blah")})
          (tools/new-tools-agent topology
                                 "tools3"
                                 TOOLS
                                 {:error-handler
                                  (tools/error-handler-rethrow)})
          (tools/new-tools-agent topology
                                 "tools4"
                                 TOOLS
                                 {:error-handler
                                  (tools/error-handler-static-string-by-type
                                   [[ArithmeticException "ae"]
                                    [clojure.lang.ExceptionInfo "ei"]])})
         ))
       (bind module-name (get-module-name module))
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind sort-res
         (fn [res]
           (sort-by #(.id ^ToolExecutionResultMessage %) res)))

       (bind requests
         [(mk-request "add" "id1" {"a" 1 "b" 3})
          (mk-request "math-with-context" "id2" {"a" 6 "b" 3 "c" 5})
          (mk-request "throw" "id3" {"type" "arith"})])
       (bind [r1 r2 r3 :as res]
         (sort-res (aor/agent-invoke foo "tools1" 11 requests)))
       (is (= 3 (count res)))
       (is (res= r1 "id1" "add" "4"))
       (is (res= r2 "id2" "math-with-context" "225"))
       (is
        (res= r3
              "id3"
              "throw"
              #"java.lang.ArithmeticException: intentional[\s\S]*"))

       (bind requests
         [(mk-request "blah" "id1" {"a" 1 "b" 3})
          (mk-request "add" "id2" {"a" 9 "b" 100})])
       (bind [r1 r2 :as res]
         (sort-res (aor/agent-invoke foo "tools1" 11 requests)))
       (is (= 2 (count res)))
       (is
        (res=
         r1
         "id1"
         "blah"
         #"com.rpl.agentorama.InvalidToolNameException: Invalid tool name[\s\S]*"))
       (is (res= r2 "id2" "add" "109"))

       (bind [r1 :as res]
         (aor/agent-invoke foo "tools2" nil [(mk-request "abc" "id1" {})]))
       (is (= 1 (count res)))
       (is (res= r1 "id1" "abc" "blah"))

       (is (thrown?
            Exception
            (aor/agent-invoke foo "tools3" nil [(mk-request "abc" "id1" {})])))


       (bind [r1 :as res]
         (aor/agent-invoke foo
                           "tools4"
                           nil
                           [(mk-request "throw" "id11" {"type" "arith"})]))
       (is (= 1 (count res)))
       (is (res= r1 "id11" "throw" "ae"))

       (bind [r1 :as res]
         (aor/agent-invoke foo
                           "tools4"
                           nil
                           [(mk-request "throw" "id11" {"type" "ex-info"})]))
       (is (= 1 (count res)))
       (is (res= r1 "id11" "throw" "ei"))

       (is (thrown? Exception
                    (aor/agent-invoke
                     foo
                     "tools4"
                     nil
                     [(mk-request "throw" "id11" {"type" "none"})])))

       ;; TODO: <<<<>>>>
       ;; - test all the nested ops tracing cases
       ;;   - success
       ;;   - invalid tool call
       ;;   - exception rethrow
       ;;   - a new exception during error handling
       ;;     - could give static string of "" to induce a langchain4j error for
       ;;     a different exception type
      ))))

;; TODO: <<<<>>>>
;;  - add test using OpenAI with tools
