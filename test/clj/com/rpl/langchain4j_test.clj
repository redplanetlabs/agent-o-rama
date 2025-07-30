(ns com.rpl.langchain4j-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path]
        [rpl.rama.util.helpers :refer [atomic-println]])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j]
   [org.httpkit.client :as http])
  (:import
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.message
    SystemMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiStreamingChatModel]
   [dev.langchain4j.web.search
    WebSearchRequest]
   [dev.langchain4j.web.search.tavily
    TavilyWebSearchEngine]))

(def ANALYST-INSTRUCTIONS
  "You are tasked with creating a set of AI analyst personas. Follow these instructions carefully:

1. First, review the research topic: %s

2. Examine any editorial feedback that has been optionally provided to guide creation of the analysts:

%s

3. Determine the most interesting themes based upon documents and / or feedback above.

4. Pick the top %s themes.

5. Assign one analyst to each theme.")

(defn analyst-instructions
  [topic human-feedback max-analysts]
  (format ANALYST-INSTRUCTIONS topic human-feedback max-analysts))

(def ANALYST-RESPONSE-SCHEMA
  (lj/object
   {"analysts"
    (lj/array
     "Comprehensive list of analysts with their roles and affiliations."
     (lj/object
      "Properties of an analyst"
      {"affiliation" (lj/string "Primary affiliation of the analyst.")
       "name"        (lj/string "Name of the analyst.")
       "role"        (lj/string
                      "Role of the analyst in the context of the topic.")
       "description"
       (lj/string "Description of the analyst focus, concerns, and motives.")
      })
    )}))

(defn analyst-persona
  [{:keys [name role affiliation description]}]
  (format
   "Name: %s\nRole: %s\nAffiliation: %s\nDescription: %s"
   name
   role
   affiliation
   description
  ))

(def GENERATE-QUESTION-INSTRUCTIONS
  "You are an analyst tasked with interviewing an expert to learn about a specific topic.

Your goal is boil down to interesting and specific insights related to your topic.

1. Interesting: Insights that people will find surprising or non-obvious.

2. Specific: Insights that avoid generalities and include specific examples from the expert.

Here is your topic of focus and set of goals: %s

Begin by introducing yourself using a name that fits your persona, and then ask your question.

Continue to ask questions to drill down and refine your understanding of the topic.

When you are satisfied with your understanding, complete the interview with: \"Thank you so much for your help!\"

Remember to stay in character throughout your response, reflecting the persona and goals provided to you.")

(defn generate-question-instructions
  [analyst]
  (format GENERATE-QUESTION-INSTRUCTIONS (analyst-persona analyst)))

(def SEARCH-INSTRUCTIONS
  "You will be given a conversation between an analyst and an expert.

Your goal is to generate a well-structured query for use in retrieval and / or web-search related to the conversation.

First, analyze the full conversation.

Pay particular attention to the final question posed by the analyst.

Convert this final question into a well-structured web search query no more than 400 characters.")

(def DOCUMENT-TEMPLATE
  "<Document href=\"%s\">
%s
</Document>")

(def MAPPER (j/object-mapper {:decode-key-fn keyword}))

(defn wiki-search
  [^String query]
  (let [url (str "https://en.wikipedia.org/w/api.php"
                 "?action=query&list=search&format=json&srsearch="
                 (java.net.URLEncoder/encode query "UTF-8"))
        {:keys [status body]} @(http/get url)]
    (if (= status 200)
      (let [data (j/read-value body MAPPER)]
        (mapv :title (get-in data [:query :search])))
      (throw (ex-info "Wikipedia search failed" {:status status})))))

(defn wiki-extract
  [^String title]
  (let [url (str
             "https://en.wikipedia.org/w/api.php"
             "?action=query&prop=extracts&explaintext=true&format=json&titles="
             (java.net.URLEncoder/encode title "UTF-8"))
        {:keys [status body]} @(http/get url)]
    (if (= status 200)
      (let [data    (j/read-value body MAPPER)
            pages   (vals (get-in data [:query :pages]))
            extract (-> pages
                        first
                        :extract)]
        (if extract extract ""))
      (throw (ex-info "Wikipedia extract failed" {:status status})))))

(defn wikipedia-loader
  [query max-docs]
  (let [titles (take max-docs (wiki-search query))]
    (mapv wiki-extract titles)))

(defn tavily-web-search-engine
  [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      .build))

(defn tavily-search
  [^TavilyWebSearchEngine tavily terms max-results]
  (.toDocuments
   (.search tavily
            (WebSearchRequest/from terms (int max-results)))))

(aor/defagentmodule OpenAIModule
  [topology]
  (aor/declare-agent-object topology
                            "openai-api-key"
                            (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object topology
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
  (->
    topology
    (aor/new-agent "foo")
    (aor/node
     "create-analysts"
     "questions"
     (fn [agent-node topic human-feedback max-analysts]
       (let [openai (aor/get-agent-object agent-node "openai")
             res    (-> openai
                        (lc4j/chat
                         (lc4j/chat-request
                          [(analyst-instructions topic
                                                 human-feedback
                                                 max-analysts)]
                          {:response-format (lc4j/json-response-format
                                             "analysts"
                                             ANALYST-RESPONSE-SCHEMA)}))
                        .aiMessage
                        .text
                        (j/read-value MAPPER))]
         (aor/emit! agent-node "questions" (:analysts res))
       )))
    (aor/agg-start-node
     "questions"
     "generate-question"
     (fn [agent-node analysts]
       (doseq [analyst analysts]
         (aor/emit! agent-node "generate-question" analyst []))))
    (aor/agg-start-node
     "generate-question"
     ["search-web" "search-wikipedia"]
     (fn [agent-node analyst messages]
       (let [openai       (aor/get-agent-object agent-node "openai")
             instr        (generate-question-instructions analyst)
             question     (-> (lc4j/chat openai
                                         (concat [(SystemMessage. instr)]
                                                 messages))
                              .aiMessage)
             new-messages (conj messages question)
             search-query (-> (lc4j/chat openai
                                         (concat [(SystemMessage.
                                                   SEARCH-INSTRUCTIONS)]
                                                 new-messages))
                              .aiMessage
                              .text)]
         (aor/emit! agent-node "search-web" search-query)
         (aor/emit! agent-node "search-wikipedia" search-query)
         new-messages
       )))
    (aor/node
     "search-web"
     "agg-research"
     (fn [agent-node search-query]
       (let [tavily    (aor/get-agent-object agent-node "tavily")
             docs      (tavily-search tavily search-query 3)
             formatted (str/join "\n\n---\n\n"
                                 (for [^Document doc docs]
                                   (format DOCUMENT-TEMPLATE
                                           (-> doc
                                               .metadata
                                               (.getString "url"))
                                           (.text doc))))]
         (aor/emit! agent-node "agg-research" formatted)
       )))
    (aor/node
     "search-wikipedia"
     "agg-research"
     (fn [agent-node search-query]
         ;; TODO: <<<<>>>>
     ))
    (aor/agg-node
     "agg-research"
     ["generate-question" "agg-analysts"]
     aggs/+vec-agg
     (fn [agent-node searches messages]
         ;; TODO: <<<<>>>>
     ))
    (aor/agg-node
     "agg-analysts"
     "write-section"
     aggs/+vec-agg
     (fn [agent-node agg-state node-start-res]
       ;; TODO: <<<<>>>>
       (aor/emit! agent-node "write-section" nil)
     ))
    (aor/node
     "write-section"
     nil
     (fn [agent-node data]
       ;; TODO: <<<<>>>>
       (aor/result! agent-node "done")
     ))


  ))

(deftest openai-agent-test
  (when (some? (System/getenv "OPENAI_API_KEY"))
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (rtest/launch-module! ipc OpenAIModule {:tasks 4 :threads 2})
       (bind module-name (get-module-name OpenAIModule))

       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind root-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "foo")))
       (bind traces-query
         (foreign-query ipc
                        module-name
                        (queries/tracing-query-name "foo")))


       (bind inv
         (aor/agent-initiate
          foo
          "The movie Touch of Evil"
          "Want to understand the impact of this movie on the film industry"
          4))

       (bind agent-task-id (.getTaskId inv))
       (bind agent-id (.getAgentInvokeId inv))
       (bind root-invoke-id
         (foreign-select-one [(keypath agent-id) :root-invoke-id]
                             root-pstate
                             {:pkey agent-task-id}))
       (println "RESULT:")
       (clojure.pprint/pprint (aor/agent-result foo inv))

       (println "\nTRACE:")
       (bind res
         (foreign-invoke-query traces-query
                               agent-task-id
                               [[agent-task-id root-invoke-id]]
                               10000))
       (clojure.pprint/pprint res)
      ))))
