(ns com.rpl.agent.chatbot-human-in-loop
  "A ReAct agent with human-in-the-loop capabilities and web search tools."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama
    HumanInputRequest]
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiStreamingChatModel]
   [dev.langchain4j.web.search
    WebSearchRequest]
   [dev.langchain4j.web.search.tavily
    TavilyWebSearchEngine]))

(defn tavily-web-search-engine
  [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      (.excludeDomains ["en.wikipedia.org"])
      .build))

(defn- mk-tavily-search
  [{:keys [max-results] :or {max-results 3}}]
  (fn tavily-search
    [agent-node _ arguments]
    (let [terms (get arguments "terms")
          ^TavilyWebSearchEngine tavily (aor/get-agent-object
                                         agent-node
                                         "tavily")]
      (str/join
       "\n---\n"
       (mapv
        (fn [^Document doc]
          (.text doc))
        (.toDocuments
         (.search tavily (WebSearchRequest/from terms (int max-results)))))))))

(def ^:private TOOLS
  "Description of available tools"
  [(tools/tool-info
    (tools/tool-specification
     "tavily"
     (lj/object
      {:description "Map containing the terms to search for"
       :required    ["terms"]}
      {"terms" (lj/string "The terms to search for")})
     "Search the web")
    (mk-tavily-search {:max-results 3})
    {:include-context? true})])

(def ^:private SYSTEM-PROMPT "You are a helpful AI assistant.

System time: %s")

(def ^:private react-agent-name "ReActAgent")

(def ^:private openai-key-name "openai-api-key")

(defn- non-blank-input
  [agent-node options]
  (loop []
    (let [input (aor/get-human-input agent-node (:prompt options))]
      (if (str/blank? input)
        (do
          (print (:prompt options))
          (recur))
        input))))

(aor/defagentmodule ChatBotModule
  [topology]
  (aor/declare-agent-object
   topology
   openai-key-name
   (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object
   topology
   "tavily-api-key"
   (System/getenv "TAVILY_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup openai-key-name))
         (.modelName "gpt-4o-mini")
         .build)))

  (aor/declare-agent-object-builder
   topology
   "openai-non-streaming"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup openai-key-name))
         (.modelName "gpt-4o-mini")
         .build)))

  (aor/declare-agent-object-builder
   topology
   "tavily"
   (fn tavily [setup]
     (tavily-web-search-engine (aor/get-agent-object setup "tavily-api-key"))))

  (->
    topology
    (aor/new-agent react-agent-name)

    (aor/node
     "chat"
     "chat"
     (fn chat-fn [agent-node messages options]
       (let [input  (non-blank-input agent-node options)
             openai (aor/get-agent-object agent-node "openai")]
         (println input)
         (if (= "\\bye" (str/trim input))
           (aor/result! agent-node {:messages messages})
           (let [messages   (conj messages (UserMessage. "user" input))
                 tools      (aor/agent-client agent-node "tools")
                 response   (lc4j/chat
                             openai
                             (lc4j/chat-request messages {:tools TOOLS}))
                 ai-message (.aiMessage response)
                 tool-calls (not-empty
                             (vec (.toolExecutionRequests ai-message)))]
             (if tool-calls
               (let [tool-results  (aor/agent-invoke tools tool-calls)
                     next-messages (into (conj messages ai-message)
                                         tool-results)]
                 (doseq [tool-result tool-results]
                   (println (.text tool-result)))
                 (aor/emit! agent-node "chat" next-messages options))
               (do
                 (println (.text ai-message))
                 (aor/emit!
                  agent-node
                  "chat"
                  (conj messages ai-message)
                  options)))))))))

  (tools/new-tools-agent topology "tools" TOOLS))

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _ (aor/start-ui ipc)]
    (rtest/launch-module! ipc ChatBotModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name ChatBotModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager react-agent-name)
          agent-invoke  (aor/agent-initiate
                         agent
                         [(SystemMessage/from
                           (format
                            SYSTEM-PROMPT
                            (.toString (java.time.Instant/now))))]
                         {:prompt "> "})]
      (println)
      (println "Enter \"\bye\" to exit")
      (loop [step (aor/agent-next-step agent agent-invoke)]
        (if (instance? HumanInputRequest step)
          (do
            (print (:prompt step))
            (flush)
            (aor/provide-human-input agent step (read-line))
            (println)
            (recur (aor/agent-next-step agent agent-invoke)))
          (println (:result step)))))))
