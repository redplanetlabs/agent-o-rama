(ns com.rpl.agent.basic.structured-langchain4j-agent
  "Demonstrates LangChain4j structured output with JSON response format.

  Features demonstrated:
  - JSON schema definition with lc4j/object and field types
  - Structured response format with :response-format option
  - OpenAI chat model integration with structured outputs
  - Single-node agent returning structured data"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;;; JSON schema for structured response
(def QuestionAnalysis
  (lj/object
   {:description "Analysis of a user question with structured breakdown"}
   {"question_type" (lj/enum
                     "Type of question being asked"
                     ["factual" "analytical" "creative" "technical" "personal"])
    "complexity"    (lj/enum
                     "Complexity level of the question"
                     ["simple" "moderate" "complex"])
    "main_topics"   (lj/array
                     "Key topics covered in the question"
                     (lj/string "A main topic or concept"))
    "answer"        (lj/string "Direct answer to the user's question")
    "confidence"    (lj/enum
                     "Confidence level in the response"
                     ["high" "medium" "low"])}))

;;; Agent module demonstrating structured LangChain4j output
(aor/defagentmodule StructuredLangChain4jModule
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
         (.temperature 0.3)
         (.maxTokens (int 300))
         .build)))

  (->
    (aor/new-agent topology "StructuredLangChain4jAgent")

    ;; Single node that analyzes user question and returns structured response
    (aor/node
     "analyze-question"
     nil
     (fn [agent-node ^String user-question]
       (let
         [model      (aor/get-agent-object agent-node "openai-model")
          system-msg
          (SystemMessage.
           "You are an intelligent question analyzer. Analyze the user's question and provide a structured response that categorizes the question type, assesses its complexity, identifies main topics, provides a direct answer, and indicates your confidence level.")
          user-msg   (UserMessage. user-question)
          ;; Configure structured JSON response
          response   (lc4j/chat
                      model
                      (lc4j/chat-request
                       [system-msg user-msg]
                       {:response-format
                        (lc4j/json-response-format
                         "QuestionAnalysis"
                         QuestionAnalysis)}))]

         ;; Parse and return structured response
         (aor/result! agent-node
                      (j/read-value (.text (.aiMessage response)))))))))

(defn -main
  "Run the structured LangChain4j agent example"
  [& _args]
  (if (System/getenv "OPENAI_API_KEY")
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc StructuredLangChain4jModule {:tasks 1 :threads 1})

      (let [module-name (rama/get-module-name StructuredLangChain4jModule)
            manager     (aor/agent-manager ipc module-name)
            agent       (aor/agent-client manager "StructuredLangChain4jAgent")]

        (println "Structured LangChain4j Agent Example:")
        (println "Analyzing questions with structured output...\n")

        ;; Test with different types of questions
        (doseq [question ["What is artificial intelligence?"
                          "How can I improve my programming skills?"
                          "Write a creative story about a robot"]]
          (println "Question:" question)
          (let [result (aor/agent-invoke agent question)]
            (println "Analysis:")
            (println "  Type:" (get result "question_type"))
            (println "  Complexity:" (get result "complexity"))
            (println "  Topics:" (get result "main_topics"))
            (println "  Answer:" (get result "answer"))
            (println "  Confidence:" (get result "confidence"))
            (println)))

        (println "Notice how:")
        (println "- JSON schema defines the exact structure expected")
        (println "- :response-format ensures structured output")
        (println "- Different question types are automatically categorized")
        (println "- Response includes metadata about the analysis")))

    (do
      (println "Structured LangChain4j Agent Example:")
      (println "OPENAI_API_KEY environment variable not set.")
      (println "Please set your OpenAI API key to run this example:")
      (println "  export OPENAI_API_KEY=your-api-key-here"))))
