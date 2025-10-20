(ns com.rpl.langchain4j-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    AiMessage
    ToolExecutionResultMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiStreamingChatModel]))

(deftest openai-agent-test
  (when (some? (System/getenv "OPENAI_API_KEY"))
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-agent-object topology
                                    "openai-api-key"
                                    (System/getenv "OPENAI_API_KEY"))
          (aor/declare-agent-object-builder
           topology
           "openai"
           (fn [setup]
             (-> (OpenAiStreamingChatModel/builder)
                 (.apiKey (aor/get-agent-object setup "openai-api-key"))
                 (.modelName "gpt-4o-mini")
                 .build)))
          (->
            topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node prompt]
               (let [openai (aor/get-agent-object agent-node "openai")]
                 (aor/result! agent-node (lc4j/basic-chat openai prompt))
               )))
          )))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind res (aor/agent-invoke foo "What is 11*13?"))
       (is (str/includes? res "143"))
      ))))


(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "divide"
     (lj/object
      {"numerator"   (lj/number
                      "The number to be divided (top of the fraction)")
       "denominator" (lj/number "The number to divide by; must not be zero")})
     "Performs division: numerator ÷ denominator. Returns a floating-point result. Denominator must be nonzero.")
    (fn [args]
      (double
       (/ (get args "numerator")
          (get args "denominator")))))])



(deftest openai-agent-tools-test
  (when (some? (System/getenv "OPENAI_API_KEY"))
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-agent-object topology
                                    "openai-api-key"
                                    (System/getenv "OPENAI_API_KEY"))
          (aor/declare-agent-object-builder
           topology
           "openai"
           (fn [setup]
             (-> (OpenAiChatModel/builder)
                 (.apiKey (aor/get-agent-object setup "openai-api-key"))
                 (.modelName "gpt-4o-mini")
                 .build)))
          (->
            topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node ^String prompt]
               (let [openai (aor/get-agent-object agent-node "openai")
                     tools  (aor/agent-client agent-node "tools")
                     m      (-> openai
                                (lc4j/chat
                                 (lc4j/chat-request
                                  [(UserMessage. prompt)]
                                  {:tools TOOLS}))
                                .aiMessage)
                     [r :as tool-results] (aor/agent-invoke
                                           tools
                                           (.toolExecutionRequests m))]
                 (when-not (= 1 (count tool-results))
                   (throw (ex-info "Failed" {})))
                 (aor/result! agent-node (.text ^ToolExecutionResultMessage r))
               ))))
          (tools/new-tools-agent topology "tools" TOOLS)
         ))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind res
         (aor/agent-invoke
          foo
          "Use a tool to give the floating-point result of dividing 123 by 37"))
       (is (= (double (/ 123 37.0)) (Double/parseDouble res)))
      ))))
