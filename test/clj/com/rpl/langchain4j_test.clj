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
    SystemMessage
    UserMessage]
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
  [persona]
  (format GENERATE-QUESTION-INSTRUCTIONS persona))

(def SEARCH-INSTRUCTIONS
  "You will be given a conversation between an analyst and an expert.

Your goal is to generate a well-structured query for use in retrieval and / or web-search related to the conversation.

First, analyze the full conversation.

Pay particular attention to the final question posed by the analyst.

Convert this final question into a well-structured web search query no more than 400 characters.")

(def WEB-DOCUMENT-TEMPLATE
  "<Document href=\"%s\">
%s
</Document>")

(def WIKIPEDIA-DOCUMENT-TEMPLATE
  "<Document source=\"%s\" page=\"%s\">
%s
</Document>")

(def ANSWER-INSTRUCTIONS
  "You are an expert being interviewed by an analyst.

Here is analyst area of focus: %s.

You goal is to answer a question posed by the interviewer.

To answer question, use this context:

%s

When answering questions, follow these guidelines:

1. Use only the information provided in the context.

2. Do not introduce external information or make assumptions beyond what is explicitly stated in the context.

3. The context contain sources at the topic of each individual document.

4. Include these sources your answer next to any relevant statements. For example, for source # 1 use [1].

5. List your sources in order at the bottom of your answer. [1] Source 1, [2] Source 2, etc

6. If the source is: <Document source=\"assistant/docs/llama3_1.pdf\" page=\"7\"/>' then just list:

[1] assistant/docs/llama3_1.pdf, page 7

And skip the addition of the brackets as well as the Document source preamble in your citation.")

(defn answer-instructions
  [persona context]
  (format ANSWER-INSTRUCTIONS persona context))

(def SECTION-WRITER-INSTRUCTIONS
  "You are an expert technical writer.

Your task is to create a short, easily digestible section of a report based on a set of source documents.

1. Analyze the content of the source documents:
- The name of each source document is at the start of the document, with the <Document tag.

2. Create a report structure using markdown formatting:
- Use ## for the section title
- Use ### for sub-section headers

3. Write the report following this structure:
a. Title (## header)
b. Summary (### header)
c. Sources (### header)

4. Make your title engaging based upon the focus area of the analyst:
%s

5. For the summary section:
- Set up summary with general background / context related to the focus area of the analyst
- Emphasize what is novel, interesting, or surprising about insights gathered from the interview
- Create a numbered list of source documents, as you use them
- Do not mention the names of interviewers or experts
- Aim for approximately 400 words maximum
- Use numbered sources in your report (e.g., [1], [2]) based on information from source documents

6. In the Sources section:
- Include all sources used in your report
- Provide full links to relevant websites or specific document paths
- Separate each source by a newline. Use two spaces at the end of each line to create a newline in Markdown.
- It will look like:

### Sources
[1] Link or Document name
[2] Link or Document name

7. Be sure to combine sources. For example this is not correct:

[3] https://ai.meta.com/blog/meta-llama-3-1/
[4] https://ai.meta.com/blog/meta-llama-3-1/

There should be no redundant sources. It should simply be:

[3] https://ai.meta.com/blog/meta-llama-3-1/

8. Final review:
- Ensure the report follows the required structure
- Include no preamble before the title of the report
- Check that all guidelines have been followed")

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
        {:content (or extract "")
         :source  (str "https://en.wikipedia.org/wiki/"
                       (.replace title " " "_"))
         :page    title})
      (throw (ex-info "Wikipedia extract failed" {:status status})))))

(defn wikipedia-loader
  [query max-docs]
  (let [titles (take max-docs (wiki-search query))]
    (mapv wiki-extract titles)))

(defn tavily-web-search-engine
  [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      (.excludeDomains ["en.wikipedia.org"])
      .build))

(defn tavily-search
  [^TavilyWebSearchEngine tavily terms max-results]
  (.toDocuments
   (.search tavily
            (WebSearchRequest/from terms (int max-results)))))

(defn generate-search-query*
  [openai messages]
  (-> (lc4j/chat openai
                 (concat [(SystemMessage. SEARCH-INSTRUCTIONS)]
                         messages))
      .aiMessage
      .text))

(defn generate-search-query
  [openai messages]
  (loop [messages messages
         iters    0]
    (when (>= iters 3)
      (throw (ex-info "Failed to generate search query <= 400 chars"
                      {:messages (str messages)})))
    (let [q (generate-search-query* openai messages)]
      (if (< (count q) 400)
        q
        (recur
         (conj
          messages
          (UserMessage.
           (format
            "You last generated: %s\nTry again and keep the query under 400 chars."
            q)))
         (inc iters))
      ))))

(aor/defagentmodule ResearchAgentModule
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
     (fn [agent-node topic human-feedback options]
       (let [{:keys [max-analysts max-turns]}
             (merge {:max-analysts 4 :max-turns 2} options)
             openai (aor/get-agent-object agent-node "openai")
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
         (aor/emit! agent-node "questions" (:analysts res) max-turns)
       )))
    (aor/agg-start-node
     "questions"
     "generate-question"
     (fn [agent-node analysts max-turns]
       (doseq [analyst analysts]
         (aor/emit! agent-node
                    "generate-question"
                    (analyst-persona analyst)
                    []
                    max-turns))))
    (aor/agg-start-node
     "generate-question"
     ["search-web" "search-wikipedia"]
     (fn [agent-node persona messages max-turns]
       (let [openai       (aor/get-agent-object agent-node "openai")
             instr        (generate-question-instructions persona)
             question     (-> (lc4j/chat openai
                                         (concat [(SystemMessage. instr)]
                                                 messages))
                              .aiMessage)
             new-messages (conj messages question)
             search-query (generate-search-query openai new-messages)]
         (aor/emit! agent-node "search-web" search-query)
         (aor/emit! agent-node "search-wikipedia" search-query)
         {:persona persona :messages new-messages :max-turns max-turns}
       )))
    (aor/node
     "search-web"
     "agg-research"
     (fn [agent-node search-query]
       (let [tavily (aor/get-agent-object agent-node "tavily")
             docs   (tavily-search tavily search-query 3)]
         (doseq [^Document doc docs]
           (aor/emit! agent-node
                      "agg-research"
                      (format WEB-DOCUMENT-TEMPLATE
                              (-> doc
                                  .metadata
                                  (.getString "url"))
                              (.text doc))))
       )))
    (aor/node
     "search-wikipedia"
     "agg-research"
     (fn [agent-node search-query]
       (let [docs (wikipedia-loader (str/replace search-query "\"" "") 2)]
         (doseq [doc docs]
           (aor/emit! agent-node
                      "agg-research"
                      (format WIKIPEDIA-DOCUMENT-TEMPLATE
                              (:source doc)
                              (:page doc)
                              (:content doc))))
       )))
    (aor/agg-node
     "agg-research"
     ["generate-question" "write-section"]
     aggs/+vec-agg
     (fn [agent-node searches {:keys [persona messages max-turns]}]
       (let [openai       (aor/get-agent-object agent-node "openai")
             instr        (answer-instructions persona
                                               (str/join "\n---\n" searches))
             answer       (-> (lc4j/chat openai
                                         (concat [(SystemMessage. instr)]
                                                 messages))
                              .aiMessage
                              .text)
             new-messages (conj messages (UserMessage. "expert" answer))
             num-turns    (count
                           (filter
                            (fn [m]
                              (and (instance? UserMessage m)
                                   (= "expert" (.name ^UserMessage m))))
                            new-messages))]
         (atomic-println "PERSONA:" persona)
         (atomic-println "MESSAGES:" new-messages)
         (if (>= num-turns max-turns)
           (aor/emit! agent-node
                      "write-section"
                      persona
                      new-messages
                      searches)
           (aor/emit! agent-node
                      "generate-question"
                      persona
                      new-messages
                      max-turns))
       )))
    (aor/node
     "write-section"
     "agg-sections"
     (fn [agent-node persona messages searches]

     ))
    (aor/agg-node
     "agg-sections"
     nil
     aggs/+vec-agg
     (fn [agent-node sections _]
       ;; TODO: <<<<>>>>
       ;; - input is:
       ;;   - interview, which is messages converted to a string
       ;;   - context, which is all the web searches for the analyst (across all
       ;;   rounds?)
       ;;   - analyst persona
       (aor/result! agent-node nil)
     ))
  ))

(deftest research-agent-test
  (when (some? (System/getenv "OPENAI_API_KEY"))
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (rtest/launch-module! ipc ResearchAgentModule {:tasks 4 :threads 2})
       (bind module-name (get-module-name ResearchAgentModule))

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
          {}))

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
