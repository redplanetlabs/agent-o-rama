(ns com.rpl.agent.basic.human-input-agent
  "Demonstrates human input requests and handling within agent nodes.

  Features demonstrated:
  - get-human-input: Request input from human users
  - agent-next-step: Handle human input requests in execution flow
  - provide-human-input: Supply responses to human input requests
  - pending-human-inputs: List all pending human input requests
  - human-input-request?: Check if a step is a human input request
  - agent-invoke-complete?: Check if an agent invocation has completed
  - Human-in-the-loop agent execution patterns"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiStreamingChatModel]))

(defn human-helpful?
  "Ask user if the response was helpful and loop until valid y/n answer."
  [agent-node response]
  (loop [res (aor/get-human-input
              agent-node
              (str "AI Response: "
                   response
                   "\n\nWas this response helpful? (y/n): "))]
    (cond (= res "y") true
          (= res "n") false
          :else (recur (aor/get-human-input
                        agent-node
                        "Please answer 'y' or 'n'.")))))

(aor/defagentmodule HumanInputAgentModule
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
   (aor/new-agent "HumanInputAgent")
   (aor/node
    "chat"
    nil
    (fn [agent-node ^String user-message]
      (let [openai (aor/get-agent-object agent-node "openai")
            response (-> (lc4j/chat openai [(UserMessage. user-message)])
                         .aiMessage
                         .text)
            helpful? (human-helpful? agent-node response)]
        (aor/result! agent-node
                     {:response response
                      :helpful helpful?}))))))

(defn -main
  "Run the human input agent example"
  [& _args]
  (if (System/getenv "OPENAI_API_KEY")
    (with-open [ipc (rtest/create-ipc)
                ui (aor/start-ui ipc)]
      (rtest/launch-module! ipc HumanInputAgentModule {:tasks 4 :threads 2})
      (let [module-name (rama/get-module-name HumanInputAgentModule)
            agent-manager (aor/agent-manager ipc module-name)
            chat-agent (aor/agent-client agent-manager "HumanInputAgent")
            _ (print "Enter your message: ")
            _ (flush)
            user-message (read-line)
            inv (aor/agent-initiate chat-agent user-message)]
        (println)
        (println user-message)
        (println (format "\nAgent invoke complete? %s" (aor/agent-invoke-complete? chat-agent inv)))
        (loop [step (aor/agent-next-step chat-agent inv)]
          (if (aor/human-input-request? step)
            (do
              (let [pending (aor/pending-human-inputs chat-agent inv)]
                (when (> (count pending) 1)
                  (println (format "\n[%d pending human input requests]" (count pending)))))
              (println (:prompt step))
              (print ">> ")
              (flush)
              (aor/provide-human-input chat-agent step (read-line))
              (println)
              (recur (aor/agent-next-step chat-agent inv)))
            (do
              (println "Final result:")
              (println (:result step))
              (println (format "\nAgent invoke complete? %s" (aor/agent-invoke-complete? chat-agent inv))))))

        (println "\nNotice how:")
        (println "- Agents can request human input during execution")
        (println "- human-input-request? checks if a step is a human input request")
        (println "- agent-invoke-complete? checks if an agent invocation has completed")
        (println "- pending-human-inputs lists all pending requests")
        (println "- Input validation and defaults are handled gracefully")
        (println "- Human responses influence the final result")))

    (do
      (println "Human Input Agent Example:")
      (println "OPENAI_API_KEY environment variable not set.")
      (println "Please set your OpenAI API key to run this example:")
      (println "  export OPENAI_API_KEY=your-api-key-here"))))

(comment
  (-main))
