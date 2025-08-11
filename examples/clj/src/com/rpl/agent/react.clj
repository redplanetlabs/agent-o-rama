(ns com.rpl.agent.react
  "This defines a custom reasoning and action agent graph.
  It invokes tools in a simple loop."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama AgentComplete]
   [dev.langchain4j.data.document Document]
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]
   [dev.langchain4j.web.search
    WebSearchRequest]
   [dev.langchain4j.web.search.tavily
    TavilyWebSearchEngine]))

(def MAPPER (j/object-mapper {:decode-key-fn keyword}))

(defn tavily-web-search-engine
  [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      (.excludeDomains ["en.wikipedia.org"])
      .build))

(defn mk-tavily-search [{:keys [max-results] :or {max-results 3}}]
  (fn tavily-search
    [agent-node _ arguments]
    (let [terms                         (get arguments "terms")
          ^TavilyWebSearchEngine tavily (aor/get-agent-object
                                         agent-node
                                         "tavily")
          search-results                (WebSearchRequest/from
                                         terms
                                         (int max-results))]
      (str/join
       "\n---\n"
       (mapv
        (fn [^Document doc]
          (.text doc))
        (.toDocuments
         (.search tavily search-results)))))))

(def TOOLS
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

(aor/defagentmodule ReActModule
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
     ;; NOTE Using non-streaming model as 1.2 seems to have an issue wrapping
     ;; ToolExecutionRequest for the return value from the chat.
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
    ["chat"]
    (fn chat-fn [agent-node messages]
      (let [openai       (aor/get-agent-object agent-node "openai")
            tools-exec   (aor/agent-client agent-node "tools-execution")
            chat-options {:tools TOOLS}
            response     (lc4j/chat
                          openai
                          (lc4j/chat-request messages chat-options))
            ai-message   (.aiMessage response)
            tool-calls   (not-empty (vec (.toolExecutionRequests ai-message)))]
        (if tool-calls
          (let [tool-results  (aor/agent-invoke tools-exec tool-calls)
                _             (prn :tool-results tool-results)
                next-messages (into (conj messages ai-message) tool-results)]
            (prn :results tool-results)
            (aor/emit! agent-node "chat" next-messages))
          (aor/result! agent-node {:messages (conj messages ai-message)}))))))
  (tools/new-tools-agent topology "tools-execution" TOOLS))

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc ReActModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name ReActModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager react-agent-name)
          agent-invoke  (aor/agent-initiate
                         agent
                         [(SystemMessage/from
                           (format
                            SYSTEM-PROMPT
                            (.toString (java.time.Instant/now))))
                          (UserMessage. "Who is the founder of LangChain?")])
          step          (aor/agent-next-step agent agent-invoke)]
      (assert (instance? AgentComplete step))
      (rtest/destroy-module! ipc module-name)
      (assert (str/includes?
               (str/lower-case (last (:messages (:result step))))
               "harrison"))
      (:result step))))
