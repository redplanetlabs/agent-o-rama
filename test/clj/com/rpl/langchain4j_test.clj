(ns com.rpl.langchain4j-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
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
  (mapv #(.text ^Document %)
        (.toDocuments
         (.search tavily
                  (WebSearchRequest/from terms (int max-results))))))

(aor/defagentmodule OpenAIModule
  [topology]
  (aor/declare-agent-object topology "api-key" (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "api-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (->
    topology
    (aor/new-agent "foo")
    (aor/node
     "create-analysts"
     nil
     (fn [agent-node topic human-feedback max-analysts]
       (let [openai (aor/get-agent-object agent-node "openai")]
         (println "RES"
                  (lc4j/chat
                   openai
                   (lc4j/chat-request
                    [(analyst-instructions topic human-feedback max-analysts)]
                    {:response-format (lc4j/json-response-format
                                       "analysts"
                                       ANALYST-RESPONSE-SCHEMA)})))
         (aor/result! agent-node 123)
       )))))

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
       (println "RESULT:" (aor/agent-result foo inv))
       (bind res
         (foreign-invoke-query traces-query
                               agent-task-id
                               [[agent-task-id root-invoke-id]]
                               10000))
       (clojure.pprint/pprint res)
      ))))
