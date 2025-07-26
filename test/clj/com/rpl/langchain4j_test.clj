(ns com.rpl.langchain4j-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
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
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiStreamingChatModel]))

(aor/defagentmodule OpenAIModule
  [topology]
  (aor/declare-agent-object topology "api-key" (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "api-key"))
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
         (aor/result! agent-node (lc4j/chat openai prompt)))))))

(deftest openai-agent-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (rtest/launch-module! ipc OpenAIModule {:tasks 4 :threads 2})
     (bind module-name (get-module-name OpenAIModule))

     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))


     (bind inv
       (aor/agent-initiate
        foo
        "What is 11 * 17?"))
     (aor/agent-stream foo
                       inv
                       "start"
                       (fn [all new reset? complete?]
                         (println "STREAM" (pr-str new) reset? complete?)))

     (bind agent-task-id (.getTaskId inv))
     (bind agent-id (.getAgentInvokeId inv))
     (bind root-invoke-id
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (println "RESULT:" (aor/agent-result foo inv))
     (bind res
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root-invoke-id]]
                             10000))
     (clojure.pprint/pprint res)
    )))
