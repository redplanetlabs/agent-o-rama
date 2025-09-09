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
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]
   [dev.langchain4j.web.search
    WebSearchRequest]
   [dev.langchain4j.web.search.tavily
    TavilyWebSearchEngine]))

(defn- tavily-web-search-engine
  [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      (.excludeDomains ["en.wikipedia.org"])
      .build))

(defn- mk-tavily-search
  [{:keys [max-results] :or {max-results 3}}]
  (fn [agent-node _ arguments]
    (let [terms          (get arguments "terms")
          tavily         (aor/get-agent-object agent-node "tavily")
          search-results (WebSearchRequest/from terms (int max-results))]
      (str/join
       "\n---\n"
       (mapv
        (fn [^Document doc] (.text doc))
        (.toDocuments
         (.search ^TavilyWebSearchEngine tavily search-results)))))))

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

(aor/defagentmodule ReActModule
  [topology]
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object
   topology
   "tavily-api-key"
   (System/getenv "TAVILY_API_KEY"))
  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (aor/declare-agent-object-builder
   topology
   "tavily"
   (fn [setup]
     (tavily-web-search-engine (aor/get-agent-object setup "tavily-api-key"))))
  (tools/new-tools-agent topology "tools" TOOLS)
  (->
    topology
    (aor/new-agent "ReActAgent")
    (aor/node
     "chat"
     "chat"
     (fn [agent-node messages]
       (let [openai     (aor/get-agent-object agent-node "openai")
             tools      (aor/agent-client agent-node "tools")
             response   (lc4j/chat
                         openai
                         (lc4j/chat-request messages {:tools TOOLS}))
             ai-message (.aiMessage response)
             tool-calls (vec (.toolExecutionRequests ai-message))]
         (if (not-empty tool-calls)
           (let [tool-results  (aor/agent-invoke tools tool-calls)
                 next-messages (into (conj messages ai-message) tool-results)]
             (aor/emit! agent-node "chat" next-messages))
           (aor/result! agent-node (.text ai-message))))))))

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _ (aor/start-ui ipc)]
    (rtest/launch-module! ipc ReActModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name ReActModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "ReActAgent")
          _ (print "Ask your question (agent has web search access): ")
          _ (flush)
          ^String user-input (read-line)
          result        (aor/agent-invoke
                         agent
                         [(SystemMessage/from
                           (format
                            "You are a helpful AI assistant. System time: %s"
                            (.toString (java.time.Instant/now))))
                          (UserMessage. user-input)])]
      (println result))))
