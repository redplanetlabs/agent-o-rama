(ns com.rpl.agent.basic.streaming-langchain4j-agent
  "Demonstrates LangChain4j streaming chat model integration with agent-o-rama.

  Features demonstrated:
  - OpenAI streaming chat model configuration as agent object
  - Automatic streaming chunk emission from OpenAI streaming responses
  - agent-stream subscription to receive streaming tokens in real-time
  - Single-node streaming chat completion"
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
    OpenAiStreamingChatModel]))

;;; Agent module demonstrating streaming LangChain4j integration
(aor/defagentmodule StreamingLangChain4jAgentModule
  [topology]

  ;; Declare OpenAI API key as agent object
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  ;; Build OpenAI streaming chat model with configuration
  (aor/declare-agent-object-builder
   topology
   "openai-streaming-model"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         .build)))

  (-> (aor/new-agent topology "StreamingLangChain4jAgent")

      ;; Single node that sends user message to streaming OpenAI model
      (aor/node
       "streaming-chat"
       nil
       (fn [agent-node ^String user-message]
         (let [model         (aor/get-agent-object agent-node "openai-streaming-model")
               messages      [(SystemMessage. "You are a helpful assistant.")
                              (UserMessage. user-message)]

               ;; Send chat request to streaming OpenAI model
               ;; Streaming chunks are automatically emitted by agent-o-rama
               response      (lc4j/chat
                              model
                              (lc4j/chat-request
                               messages
                               {:temperature 0.7 :max-output-tokens 200}))
               response-text (.text (.aiMessage response))]

           (aor/result! agent-node response-text))))))

(defn -main
  "Run the streaming LangChain4j agent example"
  [& _args]
  (if (System/getenv "OPENAI_API_KEY")
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc StreamingLangChain4jAgentModule {:tasks 1 :threads 1})

      (let [module-name (rama/get-module-name StreamingLangChain4jAgentModule)
            manager     (aor/agent-manager ipc module-name)
            agent       (aor/agent-client manager "StreamingLangChain4jAgent")]

        (println "Streaming LangChain4j Agent Example:")
        (println "Asking OpenAI a question with real-time streaming...\n")

        ;; Start async agent execution
        (let [invoke (aor/agent-initiate agent "Explain what machine learning is in simple terms")
              streaming-chunks (atom [])]

          (println "User: Explain what machine learning is in simple terms")
          (println "\nAssistant (streaming): ")

          ;; Subscribe to streaming chunks as they arrive from OpenAI
          (aor/agent-stream
           agent
           invoke
           "streaming-chat"
           (fn [all-chunks new-chunks reset? complete?]
             (doseq [chunk new-chunks]
               ;; Print each streaming chunk as it arrives
               (print chunk)
               (flush)
               (swap! streaming-chunks conj chunk))))

          ;; Wait for final complete response
          (let [final-result (aor/agent-result agent invoke)]
            (println "\n\nFinal complete response:")
            (println final-result)
            (println "\nStreaming chunks received:" (count @streaming-chunks)))

          (println "\nNotice how:")
          (println "- OpenAI streaming model automatically emits chunks")
          (println "- agent-stream receives tokens in real-time as they're generated")
          (println "- Final result contains the complete response")
          (println "- No manual stream-chunk! calls needed"))))

    (do
      (println "Streaming LangChain4j Agent Example:")
      (println "OPENAI_API_KEY environment variable not set.")
      (println "Please set your OpenAI API key to run this example:")
      (println "  export OPENAI_API_KEY=your-api-key-here"))))

(comment
  (-main))
