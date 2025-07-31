(ns com.rpl.langchain4j-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
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
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind res (aor/agent-invoke foo "What is 11*13?"))
       (is (str/includes? res "143"))
      ))))
