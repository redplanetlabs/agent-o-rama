(ns com.rpl.agent.langchain4j-agent
  "Demonstrates LangChain4j chat model integration with agent-o-rama.

  Features demonstrated:
  - OpenAI chat model configuration and usage
  - Message handling with UserMessage, AiMessage, SystemMessage
  - Chat request customization with temperature and tokens
  - Conversation state management across nodes"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    AiMessage
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
   (or (System/getenv "OPENAI_API_KEY") "demo-key"))

  ;; Build OpenAI chat model with configuration
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         (.maxTokens 500)
         .build)))

  (->
    topology
    (aor/new-agent "LangChain4jAgent")

    ;; Initial chat node that starts conversation
    (aor/node
     "start-chat"
     "continue-chat"
     (fn [agent-node {:keys [system-prompt user-message]}]
       (println "Starting chat conversation...")

       (let [model         (aor/get-agent-object agent-node "openai-model")
             messages      [(SystemMessage. system-prompt)
                            (UserMessage. user-message)]

             ;; Send chat request to OpenAI
             response      (lc4j/chat model
                                      (lc4j/chat-request messages
                                                         {:temperature 0.7
                                                          :max-output-tokens
                                                          200}))
             ai-message    (.aiMessage response)
             response-text (.text ai-message)]

         (println "AI Response:" response-text)

         ;; Continue conversation with context
         (aor/emit! agent-node
                    "continue-chat"
                    {:conversation-history (conj messages ai-message)
                     :last-response        response-text
                     :turn-count           1}))))

    ;; Continue conversation with follow-up
    (aor/node
     "continue-chat"
     "analyze-conversation"
     (fn [agent-node {:keys [conversation-history last-response turn-count]}]
       (println (format "Continuing conversation (turn %d)..."
                        (inc turn-count)))

       (let [model             (aor/get-agent-object agent-node "openai-model")
             follow-up-message (UserMessage.
                                "Can you provide more details or an example?")
             updated-history   (conj conversation-history follow-up-message)

             ;; Send follow-up with full conversation history
             response          (lc4j/chat model
                                          (lc4j/chat-request updated-history
                                                             {:temperature 0.5
                                                              :max-output-tokens
                                                              300}))
             ai-message        (.aiMessage response)
             response-text     (.text ai-message)]

         (println "Follow-up Response:" response-text)

         (aor/emit! agent-node
                    "analyze-conversation"
                    {:full-conversation  (conj updated-history ai-message)
                     :initial-response   last-response
                     :follow-up-response response-text
                     :total-turns        (inc turn-count)}))))

    ;; Analyze and summarize conversation
    (aor/node
     "analyze-conversation"
     nil
     (fn [agent-node
          {:keys [full-conversation initial-response
                  follow-up-response total-turns]}]
       (println "Analyzing conversation...")

       (let [model           (aor/get-agent-object agent-node "openai-model")
             ;; Create analysis prompt
             analysis-prompt (str
                              "Summarize this conversation in one sentence: "
                              initial-response
                              " ... " follow-up-response)

             ;; Get summary
             summary-response (lc4j/chat model
                                         (lc4j/chat-request
                                          [(UserMessage. analysis-prompt)]
                                          {:temperature       0.3
                                           :max-output-tokens 50}))
             summary         (.text (.aiMessage summary-response))]

         (println "Conversation Summary:" summary)

         (aor/result! agent-node
                      {:action               "chat-complete"
                       :total-turns          (inc total-turns)
                       :message-count        (count full-conversation)
                       :initial-response     initial-response
                       :follow-up-response   follow-up-response
                       :conversation-summary summary
                       :model-used           "gpt-4o-mini"
                       :processed-at         (System/currentTimeMillis)}))))))

(defn -main
  "Run the LangChain4j agent example"
  [& _args]
  (if (System/getenv "OPENAI_API_KEY")
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc LangChain4jAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        LangChain4jAgentModule))
            agent   (aor/agent-client manager "LangChain4jAgent")]

        (println "LangChain4j Agent Example:")
        (println "Demonstrating multi-turn conversation with OpenAI\n")

        (let
          [result
           (aor/agent-invoke
            agent
            {:system-prompt
             "You are a helpful assistant that explains technical concepts clearly and concisely."
             :user-message "What is functional programming?"})]

          (println "\n=== Final Results ===")
          (println "  Action:" (:action result))
          (println "  Total turns:" (:total-turns result))
          (println "  Total messages:" (:message-count result))
          (println "  Model used:" (:model-used result))
          (println "\n  Initial response snippet:"
                   (subs (:initial-response result)
                         0
                         (min 100 (count (:initial-response result))))
                   "...")
          (println "\n  Follow-up response snippet:"
                   (subs (:follow-up-response result)
                         0
                         (min 100 (count (:follow-up-response result))))
                   "...")
          (println "\n  Summary:" (:conversation-summary result)))

        (println "\nNotice how:")
        (println
         "- OpenAI model is configured with temperature and token limits")
        (println "- Conversation history is maintained across nodes")
        (println "- Different parameters can be used for each request")
        (println "- Full LangChain4j message types are supported")))

    (do
      (println "LangChain4j Agent Example:")
      (println "OPENAI_API_KEY environment variable not set.")
      (println "Please set your OpenAI API key to run this example:")
      (println "  export OPENAI_API_KEY=your-api-key-here"))))
