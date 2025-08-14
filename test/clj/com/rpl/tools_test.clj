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
       {"caller-data" caller-data})
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
                                    [clojure.lang.ExceptionInfo "ei"]
                                    [ClassCastException ""]])})
         ))
       (bind module-name (get-module-name module))
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind foo-root
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "foo")))
       (bind foo-nodes
         (foreign-pstate ipc
                         module-name
                         (po/agent-node-task-global-name "foo")))
       (bind tmap
         (into
          {}
          (for [i (range 1 5)]
            (let [n (str "tools" i)]
              [n
               {:root
                (foreign-pstate ipc
                                module-name
                                (po/agent-root-task-global-name n))
                :nodes
                (foreign-pstate ipc
                                module-name
                                (po/agent-node-task-global-name n))
               }]))))

       (bind sort-res
         (fn [res]
           (sort-by #(.id ^ToolExecutionResultMessage %) res)))

       (bind tool-nested-ops
         (fn [{:keys [task-id agent-invoke-id]}]
           (letlocals
            (bind root-id
              (foreign-select-one [(keypath agent-invoke-id)
                                   :root-invoke-id]
                                  foo-root
                                  {:pkey task-id}))
            (bind [agent-name {:keys [task-id agent-invoke-id]}]
              (foreign-select
               [(keypath root-id) :nested-ops FIRST :info
                (multi-path "agent-name" "result")]
               foo-nodes
               {:pkey task-id}
              ))

            (bind pstates (get tmap agent-name))
            (bind root-id
              (foreign-select-one [(keypath agent-invoke-id)
                                   :root-invoke-id]
                                  (:root pstates)
                                  {:pkey task-id}))

            (bind emits
              (foreign-select-one
               [(keypath root-id) :emits]
               (:nodes pstates)
               {:pkey task-id}
              ))

            (bind nested-ops
              (vec (apply concat
                    (for [{:keys [target-task-id invoke-id]} emits]
                      (foreign-select-one
                       [(keypath invoke-id) :nested-ops]
                       (:nodes pstates)
                       {:pkey target-task-id}
                      )))))
            (sort-by #(-> %
                          :info
                          (get "id"))
                     nested-ops)
           )))

       (bind requests
         [(mk-request "add" "id1" {"a" 1 "b" 3})
          (mk-request "math-with-context" "id2" {"a" 6 "b" 3 "c" 5})
          (mk-request "throw" "id3" {"type" "arith"})])
       (bind inv (aor/agent-initiate foo "tools1" 11 requests))
       (bind [r1 r2 r3 :as res]
         (sort-res (aor/agent-result foo inv)))
       (is (= 3 (count res)))
       (is (res= r1 "id1" "add" "4"))
       (is (res= r2 "id2" "math-with-context" "225"))
       (is
        (res=
         r3
         "id3"
         "throw"
         #"Error: java.lang.ArithmeticException: intentional[\s\S]*\nPlease fix your mistakes."))
       (bind n (tool-nested-ops inv))
       ;; also captures record-nested-op call in the math-with-context tool
       (is (= 4 (count n)))
       (is (every? #(= :tool-call (:type %)) (rest n)))
       (is (= :other
              (-> n
                  first
                  :type)))
       (is (= {"caller-data" 11}
              (-> n
                  first
                  :info)))
       (is (= {"id"     "id1"
               "name"   "add"
               "args"   {"a" 1 "b" 3}
               "type"   "success"
               "result" 4}
              (-> n
                  second
                  :info)))
       (is (= {"id"     "id2"
               "name"   "math-with-context"
               "args"   {"a" 6 "b" 3 "c" 5}
               "type"   "success"
               "result" 225}
              (-> n
                  (nth 2)
                  :info)))
       (bind info (:info (nth n 3)))
       (is (= {"id"   "id3"
               "name" "throw"
               "args" {"type" "arith"}
               "type" "failure"}
              (select-keys info ["id" "name" "args" "type"])))
       (is (re-matches #"java.lang.ArithmeticException: intentional[\s\S]*"
                       (get info "exception")))
       (is
        (re-matches
         #"Error: java.lang.ArithmeticException: intentional[\s\S]*\nPlease fix your mistakes."
         (get info "result")))

       (bind requests
         [(mk-request "blah" "id1" {"a" 1 "b" 3})
          (mk-request "add" "id2" {"a" 9 "b" 100})])
       (bind inv (aor/agent-initiate foo "tools1" 11 requests))
       (bind [r1 r2 :as res]
         (sort-res (aor/agent-result foo inv)))
       (is (= 2 (count res)))
       (is
        (res=
         r1
         "id1"
         "blah"
         "Error: blah is not a valid tool, try one of [add, math-with-context, throw]."))
       (is (res= r2 "id2" "add" "109"))
       (bind n (tool-nested-ops inv))
       (is (= 2 (count n)))
       (is (every? #(= :tool-call (:type %)) n))
       (is (= {"id" "id1" "name" "blah" "args" {"a" 1 "b" 3} "type" "invalid"}
              (-> n
                  first
                  :info)))
       (is (= {"id"     "id2"
               "name"   "add"
               "args"   {"a" 9 "b" 100}
               "type"   "success"
               "result" 109}
              (-> n
                  second
                  :info)))

       (bind [r1 r2 :as res]
         (sort-res
          (aor/agent-invoke foo
                            "tools2"
                            nil
                            [(mk-request "abc" "id1" {})
                             (mk-request "throw" "id3" {"type" "ex-info"})])))
       (is (= 2 (count res)))
       (is
        (res=
         r1
         "id1"
         "abc"
         "Error: abc is not a valid tool, try one of [add, math-with-context, throw]."))
       (is (res= r2 "id3" "throw" "blah"))


       (bind inv
         (aor/agent-initiate foo
                             "tools3"
                             nil
                             [(mk-request "throw" "id1" {"type" "arith"})]))
       (is (thrown?
            Exception
            (aor/agent-result foo inv)))
       (bind n (tool-nested-ops inv))
       (is (= 1 (count n)))
       (is (every? #(= :tool-call (:type %)) n))
       (bind info
         (-> n
             first
             :info))
       (is (= {"id"   "id1"
               "name" "throw"
               "args" {"type" "arith"}
               "type" "throw"}
              (select-keys info ["id" "name" "args" "type"])))
       (is (= 5 (count info)))
       (is (re-matches
            #"java.lang.ArithmeticException: intentional[\s\S]*"
            (get info "exception")))

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

       (bind inv
         (aor/agent-initiate
          foo
          "tools4"
          nil
          [(mk-request "throw" "id11" {"type" "none"})]))
       (is (thrown? Exception (aor/agent-result foo inv)))
       (bind n (tool-nested-ops inv))
       (is (= 1 (count n)))
       (bind o (first n))
       (is (= :tool-call (:type o)))
       (bind info (:info o))
       (is (= 6 (count info)))
       (is (= {"id"   "id11"
               "name" "throw"
               "args" {"type" "none"}
               "type" "throw"}
              (select-keys info ["id" "name" "args" "type"])))
       (is (re-matches
            #"java.lang.ClassCastException: cce[\s\S]*"
            (get info "exception1")))
       ;; this is from trying to construct tool result with blank string, which
       ;; isn't allowed by langchain4j
       (is
        (re-matches
         #"java.lang.IllegalArgumentException: text cannot be null or blank[\s\S]*"
         (get info "exception2")))
      ))))
