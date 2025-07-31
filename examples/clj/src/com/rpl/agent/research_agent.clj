(ns com.rpl.agent.research-agent
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j]
   [org.httpkit.client :as http])
  (:import
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.message
    AiMessage
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

(defn section-writer-instructions
  [persona]
  (format SECTION-WRITER-INSTRUCTIONS persona))

(def REPORT-WRITER-INSTRUCTIONS
  "You are a technical writer creating a report on this overall topic:

%s

You have a team of analysts. Each analyst has done two things:

1. They conducted an interview with an expert on a specific sub-topic.
2. They write up their finding into a memo.

Your task:

1. You will be given a collection of memos from your analysts.
2. Think carefully about the insights from each memo.
3. Consolidate these into a crisp overall summary that ties together the central ideas from all of the memos.
4. Summarize the central points in each memo into a cohesive single narrative.

To format your report:

1. Use markdown formatting.
2. Include no pre-amble for the report.
3. Use no sub-heading.
4. Start your report with a single title header: ## Insights
5. Do not mention any analyst names in your report.
6. Preserve any citations in the memos, which will be annotated in brackets, for example [1] or [2].
7. Create a final, consolidated list of sources and add to a Sources section with the `## Sources` header.
8. List your sources in order and do not repeat.

[1] Source 1
[2] Source 2

Here are the memos from your analysts to build your report from:

%s")

(defn report-writer-instructions
  [topic sections]
  (format REPORT-WRITER-INSTRUCTIONS topic sections))

(def INTRO-CONCLUSION-INSTRUCTIONS
  "You are a technical writer finishing a report on %s

You will be given all of the sections of the report.

You job is to write a crisp and compelling introduction or conclusion section.

The user will instruct you whether to write the introduction or conclusion.

Include no pre-amble for either section.

Target around 100 words, crisply previewing (for introduction) or recapping (for conclusion) all of the sections of the report.

Use markdown formatting.

For your introduction, create a compelling title and use the # header for the title.

For your introduction, use ## Introduction as the section header.

For your conclusion, use ## Conclusion as the section header.

Here are the sections to reflect on for writing: %s")

(defn intro-conclusion-instructions
  [topic sections]
  (format INTRO-CONCLUSION-INSTRUCTIONS topic sections))

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

(defn user-message-name
  [^UserMessage m]
  (.name m))

(defn extract-interview
  [messages]
  (reduce
   (fn [curr m]
     (str curr
          (cond
            (instance? UserMessage m)
            (str (if (= (user-message-name m) "expert")
                   "Expert: "
                   "Human: ")
                 (.singleText ^UserMessage m)
                 "\n\n")

            (instance? AiMessage m)
            (str "AI: " (.text ^AiMessage m) "\n\n")

            :else
            (throw (ex-info "Unexpected message" {:message m}))
          )))
   ""
   messages))

(defn chat-and-get-text
  ^String [model request]
  (-> (lc4j/chat model request)
      .aiMessage
      .text))

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
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (aor/declare-agent-object-builder
   topology
   "openai-non-streaming"
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
    (aor/new-agent "researcher")
    (aor/node
     "create-analysts"
     "questions"
     (fn [agent-node topic human-feedback options]
       (let [{:keys [max-analysts max-turns]}
             (merge {:max-analysts 4 :max-turns 2} options)
             ;; - JSON schemas not supported by streaming model, so have to use
             ;; non-streaming here
             openai (aor/get-agent-object agent-node "openai-non-streaming")
             res    (-> openai
                        (chat-and-get-text
                         (lc4j/chat-request
                          [(analyst-instructions topic
                                                 human-feedback
                                                 max-analysts)]
                          {:response-format (lc4j/json-response-format
                                             "analysts"
                                             ANALYST-RESPONSE-SCHEMA)}))
                        (j/read-value MAPPER))]
         (aor/emit! agent-node
                    "questions"
                    (:analysts res)
                    {:topic topic :max-turns max-turns})
       )))
    (aor/agg-start-node
     "questions"
     "generate-question"
     (fn [agent-node analysts {:keys [topic max-turns :as config]}]
       (doseq [analyst analysts]
         (aor/emit! agent-node
                    "generate-question"
                    (analyst-persona analyst)
                    []
                    max-turns))
       config))
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
             context      (str/join "\n---\n" searches)
             instr        (answer-instructions persona context)
             answer       (chat-and-get-text openai
                                             (concat [(SystemMessage. instr)]
                                                     messages))

             new-messages (conj messages (UserMessage. "expert" answer))
             num-turns    (count
                           (filter
                            (fn [m]
                              (and (instance? UserMessage m)
                                   (= "expert" (.name ^UserMessage m))))
                            new-messages))]
         (if (>= num-turns max-turns)
           (aor/emit! agent-node
                      "write-section"
                      persona
                      new-messages
                      context)
           (aor/emit! agent-node
                      "generate-question"
                      persona
                      new-messages
                      max-turns))
       )))
    (aor/node
     "write-section"
     "agg-sections"
     (fn [agent-node persona messages context]
       (let [openai    (aor/get-agent-object agent-node "openai")
             interview (extract-interview messages)
             instr     (section-writer-instructions persona)
             section   (chat-and-get-text
                        openai
                        (concat
                         [(SystemMessage. instr)]
                         [(UserMessage. (str "Here is the interview:\n"
                                             interview))
                          (UserMessage. (str "Here are the sources:\n"
                                             context))]))]
         (aor/emit! agent-node "agg-sections" section)
       )))
    (aor/agg-node
     "agg-sections"
     "begin-report"
     aggs/+vec-agg
     (fn [agent-node sections {:keys [topic]}]
       (aor/emit! agent-node "begin-report" sections topic)))
    (aor/agg-start-node
     "begin-report"
     ["write-report" "write-intro" "write-conclusion"]
     (fn [agent-node sections topic]
       (let [sections (str/join "\n\n" sections)]
         (doseq [n ["write-report" "write-intro" "write-conclusion"]]
           (aor/emit! agent-node n sections topic)))))
    (aor/node
     "write-report"
     "finish-report"
     (fn [agent-node sections topic]
       (let [openai (aor/get-agent-object agent-node "openai")
             instr  (report-writer-instructions topic sections)
             text   (chat-and-get-text
                     openai
                     (concat
                      [(SystemMessage. instr)]
                      [(UserMessage.
                        "Write a report based upon these memos.")]))]
         (aor/emit! agent-node "finish-report" :report text)
       )))
    (aor/node
     "write-intro"
     "finish-report"
     (fn [agent-node sections topic]
       (let [openai (aor/get-agent-object agent-node "openai")
             instr  (intro-conclusion-instructions topic sections)
             text   (chat-and-get-text
                     openai
                     (concat
                      [(SystemMessage. instr)]
                      [(UserMessage.
                        "Write the report introduction")]))]
         (aor/emit! agent-node "finish-report" :intro text)
       )))
    (aor/node
     "write-conclusion"
     "finish-report"
     (fn [agent-node sections topic]
       (let [openai (aor/get-agent-object agent-node "openai")
             instr  (intro-conclusion-instructions topic sections)
             text   (chat-and-get-text
                     openai
                     (concat
                      [(SystemMessage. instr)]
                      [(UserMessage.
                        "Write the report conclusion")]))]
         (aor/emit! agent-node "finish-report" :conclusion text)
       )))
    (aor/agg-node
     "finish-report"
     nil
     aggs/+map-agg
     (fn [agent-node {:keys [report intro conclusion]} _]
       (let [report           (str/replace report #"## Insights" "")
             [report sources] (str/split report #"## Sources")
             combined         (str intro
                                   "\n\n---\n" report
                                   "\n---\n\n" conclusion)
             final            (if sources
                                (str combined "\n\n## Sources" sources)
                                combined)]
         (aor/result! agent-node final)
       )))
  ))

(defn run-research-agent
  []
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ResearchAgentModule {:tasks 4 :threads 2})
    (let [module-name   (get-module-name ResearchAgentModule)
          agent-manager (aor/agent-manager ipc module-name)
          researcher    (aor/agent-client agent-manager "researcher")
          _ (println "Enter a topic:")
          topic         (read-line)
          _ (println "Elaborate on research direction:")
          elaboration   (read-line)
          inv           (aor/agent-initiate researcher topic elaboration {})]
      (println (aor/agent-result researcher inv))
    )))
