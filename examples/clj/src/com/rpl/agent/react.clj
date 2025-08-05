(ns com.rpl.agent.react
  "This defines a custom reasoning and action agent graph.
  It invokes tools in a simple loop."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama AgentComplete]
   [dev.langchain4j.agent.tool ToolExecutionRequest]
   [dev.langchain4j.data.document Document]
   [dev.langchain4j.data.message SystemMessage
    ToolExecutionResultMessage
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
    [agent-node arguments]
    (let [terms                         (:terms arguments)
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

(defn- tavily-tool-spec []
  (lc4j/tool-specification
   "tavily"
   (lj/object
    {:description "Map containing the terms to search for"
     :required    ["terms"]}
    {"terms" (lj/string "The terms to search for")})
   "Search the web"))

(defn- tool-specs []
  [(tavily-tool-spec)])

(def ^:private TOOL-CALL-ERROR-TEMPLATE
  "Error: %s\n Please fix your mistakes.")

(defn tool-error-message
  [e]
  (format TOOL-CALL-ERROR-TEMPLATE e))

(defn- wrap-tool-fn
  [tool-fn]
  (fn wrapped-tool-fn [id tool-name agent-node arguments]
    (try
      (ToolExecutionResultMessage.
       id
       tool-name
       (tool-fn agent-node arguments))
      (catch Exception e
        ;; NOTE we don't seem able to set the `status` field to `error`
        (ToolExecutionResultMessage.
         id
         tool-name
         (tool-error-message e))))))

(def tool-fns {"tavily" (wrap-tool-fn (mk-tavily-search {:max-results 3}))})

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
    ["tools" "chat"]
    (fn chat-fn [agent-node messages]
      (let [openai       (aor/get-agent-object agent-node "openai")
            chat-options {:tool-specifications (tool-specs)}
            response     (lc4j/chat
                          openai
                          (lc4j/chat-request messages chat-options))
            ai-message   (.aiMessage response)
            tool-calls   (vec (.toolExecutionRequests ai-message))
            messages     (conj messages ai-message)]
        (if (seq tool-calls)
          (aor/emit! agent-node
                     "tools"
                     tool-calls
                     messages)
          (aor/result! agent-node {:messages messages})))))

   (aor/agg-start-node
    ;; Handle all LLM tool calls from an AiResponse
    "tools"
    "tool"
    (fn tools-fn [agent-node tool-calls messages]
      (doseq [^ToolExecutionRequest tool-exec-req tool-calls]
        (let [id          (.id tool-exec-req)
              tool-name   (.name tool-exec-req)
              args-string (.arguments tool-exec-req)]
          (aor/emit!
           agent-node
           "tool"
           id
           tool-name
           args-string)))
      {:messages messages}))

   (aor/node
    ;; Handle one LLM tool execution request
    "tool"
    "tool-results-agg"
    (fn tool-node [agent-node id tool-name arguments]
      (let [start-time-millis                      (h/current-time-millis)
            data                                   (j/read-value
                                                    arguments
                                                    MAPPER)
            tool-fn                                (tool-fns tool-name)
            ^ToolExecutionResultMessage result-msg (tool-fn
                                                    id
                                                    tool-name
                                                    agent-node
                                                    data)
            end-time-millis                        (h/current-time-millis)]
        (aor/record-nested-op!
         agent-node
         :other
         start-time-millis
         end-time-millis
         {"tool-call-id" id
          "tool-name"    tool-name
          "arguments"    data
          "result"       (.text result-msg)})
        (aor/emit!
         agent-node
         "tool-results-agg"
         result-msg))))

   (aor/agg-node
    "tool-results-agg"
    ["chat"]
    aggs/+vec-agg
    (fn [agent-node results {:keys [messages]}]
      (aor/emit!
       agent-node
       "chat"
       (into messages results))))))

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)]
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
