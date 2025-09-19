(ns com.rpl.agent.basic.langchain4j-agent
  "Demonstrates LangChain4j chat model integration with agent-o-rama.

  Features demonstrated:
  - OpenAI chat model configuration as agent object
  - Message handling with SystemMessage and UserMessage
  - Chat request with temperature and token limits
  - Simple single-node chat completion"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;;; Agent module demonstrating LangChain4j integration
(aor/defagentmodule LangChain4jAgentModule
  [topology]

  ;; Declare OpenAI API key as agent object
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  ;; Build OpenAI chat model with configuration
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         (.maxTokens (int 500))
         .build)))

  (-> (aor/new-agent topology "LangChain4jAgent")

      ;; Single node that sends user message to OpenAI and returns response
      (aor/node
       "chat"
       nil
       (fn [agent-node ^String user-message]
         (let [model         (aor/get-agent-object agent-node "openai-model")
               messages      [(SystemMessage. "You are a helpful assistant.")
                              (UserMessage. user-message)]

               ;; Send chat request to OpenAI
               response      (lc4j/chat
                              model
                              (lc4j/chat-request
                               messages
                               {:temperature 0.7 :max-output-tokens 200}))
               response-text (.text (.aiMessage response))]

           (aor/result! agent-node response-text))))))

(defn -main
  "Run the LangChain4j agent example"
  [& _args]
  (if (System/getenv "OPENAI_API_KEY")
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc LangChain4jAgentModule {:tasks 1 :threads 1})

      (let [module-name (rama/get-module-name LangChain4jAgentModule)
            manager     (aor/agent-manager ipc module-name)
            agent       (aor/agent-client manager "LangChain4jAgent")]

        (println "LangChain4j Agent Example:")
        (println "Sending message to OpenAI...\n")

        (let [result (aor/agent-invoke agent "What is agent-o-rama?")]
          (println "User: What is agent-o-rama?")
          (println "\nAssistant:" result))

        (println "\nNotice how:")
        (println "- OpenAI model is configured as an agent object")
        (println "- Single node handles the complete chat interaction")
        (println "- Temperature and token limits are customizable")))

    (do
      (println "LangChain4j Agent Example:")
      (println "OPENAI_API_KEY environment variable not set.")
      (println "Please set your OpenAI API key to run this example:")
      (println "  export OPENAI_API_KEY=your-api-key-here"))))
