(ns com.rpl.agent.chatbot
  "An agent to perform one turn in a chatbot.
  Provides summarisation and memory."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    AgentComplete]
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiStreamingChatModel]))

(def MAPPER (j/object-mapper {:decode-key-fn keyword}))

(defn- chat-and-get-text
  ^String [model request]
  (-> (lc4j/chat model request)
      .aiMessage
      .text))

(def ^:private agent-name "ChatbotAgent")
(def ^:private openai-key-name "openai-api-key")
(def ^:private store-name "$$kv-store")


(aor/defagentmodule ChatbotModule
  [topology]
  (aor/declare-agent-object
   topology
   openai-key-name
   (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup openai-key-name))
         (.modelName "gpt-4o-mini")
         .build)))

  (aor/declare-key-value-store topology store-name Long Object)

  (->
   topology
   (aor/new-agent agent-name)

   (aor/node
    "chat"
    ["summarize"]
    (fn chat-node [agent-node messages {:keys [thread-id] :as config}]
      (let [openai        (aor/get-agent-object agent-node "openai")
            store         (aor/get-store agent-node store-name)
            checkpoint    (store/get store thread-id)
            summary       (:summary checkpoint)
            chat-messages (into
                           []
                           cat
                           [(when summary
                              [(SystemMessage.
                                (format
                                 "Summary of conversation earlier: %s"
                                 summary))])
                            (:messages checkpoint)
                            messages])
            response      (lc4j/chat openai (lc4j/chat-request chat-messages))
            ai-message    (.aiMessage response)
            new-messages  (into [] cat
                                [(:messages checkpoint)
                                 messages
                                 [ai-message]])]
        (if (> (count new-messages) 6)
          (aor/emit! agent-node
                     "summarize"
                     summary
                     new-messages
                     ai-message
                     config)
          (do
            (store/put!
             store
             thread-id
             {:messages new-messages :summary summary })
            (aor/result! agent-node {:messages [ai-message]}))))))

   (aor/node
    "summarize"
    []
    (fn summarize-node
      [agent-node summary messages ai-message {:keys [thread-id]}]
      (let [openai        (aor/get-agent-object agent-node "openai")
            store         (aor/get-store agent-node store-name)
            chat-messages (conj
                           messages
                           (UserMessage.
                            (if summary
                              (format
                               "This is summary of the conversation to date: %s

                               Extend the summary by taking into account the new messages above."
                               summary)
                              "Create a summary of the conversation above.")))
            response (lc4j/chat openai (lc4j/chat-request chat-messages))

            new-summary  (.text (.aiMessage response))
            new-messages (vec (drop (- (count messages) 2) messages))]
        (store/put!
         store
         thread-id
         {:messages new-messages :summary new-summary})
        (aor/result! agent-node {:messages [ai-message]}))))))

(def inputs
  ["hi! I'm Lance"
   "what's my name?"
   "I like The 49'ers"
   "Who was their greatest player of all time?"
   "which team do I like?"])

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ChatbotModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name ChatbotModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager agent-name)
          thread-id     0]
      (loop [inputs inputs]
        (when inputs
          (let [agent-invoke (aor/agent-initiate
                              agent
                              [(UserMessage. (first inputs))]
                              {:thread-id thread-id})
                step         (aor/agent-next-step agent agent-invoke)
                result       (:result step)]
            (assert (instance? AgentComplete step))
            (doseq [msg (:messages result)]
              (println msg))
            (recur (next inputs)))))
      (let [store-pstate (rama/foreign-pstate ipc module-name store-name)]
        (assert
         (:summary
          (rama/foreign-select-one (path/keypath thread-id) store-pstate))))
      (rtest/destroy-module! ipc module-name))))
