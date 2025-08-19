(ns com.rpl.agent.rag-research
  "A RAG-based research agent that can index documents, retrieve relevant
  information, and conduct multi-step research with synthesis."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    AgentComplete]
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]
   [dev.langchain4j.data.document
    Document]
   [dev.langchain4j.data.document.splitter
    DocumentSplitters]
   [dev.langchain4j.data.embedding
    Embedding]
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.embedding
    EmbeddingModel]
   [dev.langchain4j.model.openai
    OpenAiChatModel
    OpenAiEmbeddingModel]
   [dev.langchain4j.store.embedding
    EmbeddingMatch
    EmbeddingSearchRequest
    EmbeddingStore]
   [dev.langchain4j.store.embedding.inmemory
    InMemoryEmbeddingStore]))

;; Configuration
(def ^:const DOCUMENTS-STORE "$$documents")
(def ^:const RESEARCH-STORE "$$research")

;; System messages for different agent roles
(def QUERY-CLASSIFIER-PROMPT
  "You are a query classifier for a research agent. Analyze the user's query and
  determine:

1. ROUTING DECISION: Choose the best approach:
   - 'simple_retrieval': For straightforward questions that can be answered with
     direct document lookup
   - 'research_required': For complex questions requiring multi-step research
     and synthesis
   - 'langchain_specific': For questions specifically about LangChain framework,
     tools, or concepts
   - 'out_of_scope': For queries unrelated to the knowledge base

2. REASONING: Explain why you chose this routing decision

3. KEYWORDS: Extract 2-4 key terms that would be useful for document retrieval

Respond with your analysis focusing on the routing decision.")

(def RESEARCH-PLANNER-PROMPT
  "You are a research planner for a RAG-based research agent. Given a complex
query, create a comprehensive research plan.

Your research plan should:

1. BREAK DOWN THE QUERY: Decompose the complex query into 3-5 specific, focused
sub-questions that:
   - Build upon each other logically
   - Cover different aspects of the main query
   - Are specific enough to retrieve relevant documents
   - Progress from basic concepts to more complex analysis

2. RESEARCH STRATEGY: Define an overall approach that explains:
   - The logical sequence for investigating the sub-questions
   - How the answers will be synthesized into a final response
   - What type of analysis is needed (comparison, explanation, synthesis, etc.)

3. EXPECTED SOURCES: Identify the types of documents/information that would be
most valuable:
   - Conceptual documentation and definitions
   - Technical implementation details
   - Examples and use cases
   - Comparative analysis materials

Create a structured research plan that will enable comprehensive investigation
of the query.")

(def DOCUMENT-RETRIEVER-PROMPT
  "You are a document retriever. Given a specific question, identify the most
relevant documents from the knowledge base and extract key information.")

(def RESEARCH-SYNTHESIZER-PROMPT
  "You are a research synthesizer. Given multiple pieces of retrieved
information and sub-question answers, synthesize a comprehensive response to the
original query.  Ensure your response is well-structured, accurate, and
addresses all aspects of the question.")

;; JSON schemas for structured responses
(def QueryClassification
  (lj/object
   {:description "Classification of a user query with routing decision"}
   {"routing_decision" (lj/enum
                        "Routing decision for query processing"
                        ["simple_retrieval" "research_required"
                         "langchain_specific" "out_of_scope"])
    "reasoning"        (lj/string "Explanation of the routing decision")
    "keywords"         (lj/array
                        "Key terms for document retrieval"
                        (lj/string "A keyword or key phrase"))}))

(def ResearchPlan
  (lj/object
   {:description "A structured research plan"}
   {"sub_questions"     (lj/array
                         "List of specific questions to research"
                         (lj/string "A specific research question"))
    "research_strategy" (lj/string "Overall strategy for the research")
    "expected_sources"  (lj/array
                         "Types of information sources needed"
                         (lj/string "A type of source or information"))}))

(def DocumentChunk
  (lj/object
   {:description "A chunk of document content with metadata"}
   {"content"         (lj/string "The text content of the chunk")
    "source"          (lj/string "Source document identifier")
    "relevance_score" (lj/number "Relevance score to the query")
    "summary"         (lj/string "Brief summary of the chunk's key points")}))

(def SynthesizedResponse
  (lj/object
   {:description "Final synthesized research response"}
   {"answer"           (lj/string
                        "Comprehensive answer to the original query")
    "key_findings"     (lj/array
                        "Main findings from the research"
                        (lj/string "A key finding"))
    "sources_used"     (lj/array
                        "Sources referenced in the response"
                        (lj/string "Source identifier"))
    "confidence_level" (lj/enum
                        "Confidence in the response"
                        ["high" "medium" "low"])}))

;; Document processing functions
(defn split-document
  "Split a document into chunks for embedding"
  [text]
  (let [splitter (DocumentSplitters/recursive 500 50)]
    (mapv #(.text ^Document %) (.split splitter (Document/from text)))))

(defn create-embedding
  "Create an embedding for the given text"
  ^Embedding [agent-node ^String text]
  (let [embedding-model (aor/get-agent-object agent-node "embedding-model")
        response        (.embed ^EmbeddingModel embedding-model text)]
    (.content response)))

(defn store-document-chunks
  "Store document chunks with embeddings in pstate structure"
  [agent-node doc-id content]
  (let [chunks     (split-document content)
        docs-store (aor/get-store agent-node DOCUMENTS-STORE)
        ^EmbeddingStore embedding-store
        (aor/get-agent-object agent-node "vector-store")]
    (doseq [[idx chunk] (map-indexed vector chunks)]
      (let [chunk-id  (str doc-id "-" idx)
            embedding (create-embedding agent-node chunk)]
        ;; Store document content
        (store/put!
         docs-store
         chunk-id
         {:content chunk :source doc-id :index idx})
        ;; Store embedding in pstate with chunk-id as key and vector as value
        (.add embedding-store embedding doc-id)))))

;; Similarity calculation (cosine similarity)
(defn calculate-similarity-vectors
  "Calculate cosine similarity between two vectors"
  ^double [vec1 vec2]
  (let [dot-product (reduce + (map * vec1 vec2))
        magnitude1  (Math/sqrt (reduce + (map #(* % %) vec1)))
        magnitude2  (Math/sqrt (reduce + (map #(* % %) vec2)))]
    (/ dot-product (* magnitude1 magnitude2))))

(defn calculate-similarity
  "Calculate cosine similarity between two embeddings"
  [^Embedding embedding1 ^Embedding embedding2]
  (let [vec1        (.vector embedding1)
        vec2        (.vector embedding2)
        dot-product (reduce + (map * vec1 vec2))
        magnitude1  (Math/sqrt (reduce + (map #(* % %) vec1)))
        magnitude2  (Math/sqrt (reduce + (map #(* % %) vec2)))]
    (/ dot-product (* magnitude1 magnitude2))))

;; Agent tools
(defn index-document-tool
  "Tool to index a new document"
  [agent-node config arguments]
  (let [doc-id  (get arguments "document_id")
        content (get arguments "content")]
    (store-document-chunks agent-node doc-id content)
    (str "Indexed document: " doc-id)))

(defn search-documents-tool
  "Tool to search for relevant documents"
  [agent-node config arguments]
  (let [query           (get arguments "query")
        max-results     (get arguments "max_results" 5)
        query-embedding (create-embedding agent-node query)
        ^EmbeddingStore embedding-store
        (aor/get-agent-object agent-node "vector-store")
        docs-store      (aor/get-store agent-node DOCUMENTS-STORE)
        ;; Search using pstate embeddings
        all-matches     (.matches
                         (.search
                          embedding-store
                          (-> (EmbeddingSearchRequest/builder)
                              (.maxResults max-results)
                              (.queryEmbedding query-embedding)
                              (.build))))]
    (mapv (fn [^EmbeddingMatch match]
            (let [doc-id   (.embedded match)
                  score    (.score match)
                  doc-data (store/get docs-store doc-id)]
              {:doc-id          doc-id
               :content         (:content doc-data)
               :source          (:source doc-data)
               :relevance-score score}))
          all-matches)))

;; Helper functions
(defn create-tool-execution-request
  "Create a ToolExecutionRequest for agent invocation"
  ^ToolExecutionRequest [tool-name arguments-map]
  (-> (ToolExecutionRequest/builder)
      (.name tool-name)
      (.arguments (j/write-value-as-string arguments-map))
      .build))

;; Agent workflow functions
(defn classify-query
  "Classify the user query for routing"
  [agent-node query]
  (let [chat-model   (aor/get-agent-object agent-node "chat-model")
        system-msg   (SystemMessage. QUERY-CLASSIFIER-PROMPT)
        user-msg     (UserMessage. (str "Classify this query: " query))
        chat-options {:response-format
                      (lc4j/json-response-format
                       "QueryClassification"
                       QueryClassification)}
        response     (lc4j/chat
                      chat-model
                      (lc4j/chat-request [system-msg user-msg] chat-options))]
    (j/read-value (.text (.aiMessage response)))))

(defn create-research-plan
  "Create a research plan for complex queries"
  [agent-node query]
  (let [chat-model   (aor/get-agent-object agent-node "chat-model")
        system-msg   (SystemMessage. RESEARCH-PLANNER-PROMPT)
        user-msg     (UserMessage. (str "Create a research plan for: " query))
        chat-options {:response-format
                      (lc4j/json-response-format "ResearchPlan" ResearchPlan)}
        response     (lc4j/chat
                      chat-model
                      (lc4j/chat-request [system-msg user-msg] chat-options))]
    (j/read-value (.text (.aiMessage response)))))

(defn retrieve-for-question
  "Retrieve relevant documents for a specific question"
  [agent-node question]
  (let [search-tools (aor/agent-client agent-node "search-tools")
        results      (aor/agent-invoke
                      search-tools
                      [(create-tool-execution-request
                        "SearchDocuments"
                        {:query question :max_results 3})]
                      {})]
    results))

(defn parallel-retrieve
  "Retrieve documents for multiple questions in parallel"
  [agent-node questions]
  (let [search-tools (aor/agent-client agent-node "search-tools")]
    (->> questions
         (pmap (fn [question]
                 {:question question
                  :results  (aor/agent-invoke
                             search-tools
                             [(create-tool-execution-request
                               "SearchDocuments"
                               {:query question :max_results 3})]
                             {})}))
         (into []))))

(defn synthesize-response
  "Synthesize final response from research results"
  [agent-node original-query research-results]
  (let [chat-model   (aor/get-agent-object agent-node "chat-model")
        context      (str
                      "Original query: "
                      original-query
                      "\n\nResearch results:\n"
                      (str/join "\n" (map str research-results)))
        system-msg   (SystemMessage. RESEARCH-SYNTHESIZER-PROMPT)
        user-msg     (UserMessage. context)
        chat-options {:response-format
                      (lc4j/json-response-format
                       "SynthesizedResponse"
                       SynthesizedResponse)}
        response     (lc4j/chat
                      chat-model
                      (lc4j/chat-request [system-msg user-msg] chat-options))]
    (j/read-value (.text (.aiMessage response)))))

;; Tool definitions
(def RESEARCH-TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "IndexDocument"
     (lj/object
      {:description "Index a document for future retrieval"}
      {"document_id" (lj/string "Unique identifier for the document")
       "content"     (lj/string "Full text content of the document")})
     "Index a document into the knowledge base")
    index-document-tool
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "SearchDocuments"
     (lj/object
      {:description "Search for relevant documents"}
      {"query"       (lj/string "Search query")
       "max_results" (lj/int "Maximum number of results to return")})
     "Search the knowledge base for relevant documents")
    search-documents-tool
    {:include-context? true})])

;; Main agent module
(aor/defagentmodule RagResearchModule
  [topology]

  ;; Declare AI models
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "vector-store"
   ;; NOTE InMemoryEmbeddingStore will only work for a single worker
   (fn [_setup] (InMemoryEmbeddingStore.))
   {:thread-safe? true})

  (aor/declare-agent-object-builder
   topology
   "chat-model"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.1)
         .build)))

  (aor/declare-agent-object-builder
   topology
   "embedding-model"
   (fn [setup]
     (-> (OpenAiEmbeddingModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "text-embedding-3-small")
         .build)))

  ;; Declare stores
  (aor/declare-key-value-store topology DOCUMENTS-STORE String Object)
  (aor/declare-key-value-store topology RESEARCH-STORE String Object)

  ;; Main research agent
  (->
    topology
    (aor/new-agent "RagResearchAgent")

    (aor/node
     "query-router"
     ["simple-retrieval"
      "complex-research"
      "langchain-research"]
     (fn query-router-node [agent-node query config]
       (let [classification   (classify-query agent-node query)
             routing-decision (:routing_decision classification)]
         (case routing-decision
           "out_of_scope"
           (aor/result!
            agent-node
            {:response
             "I can only help with queries related to my knowledge base."
             :classification classification})
           "simple_retrieval" (aor/emit!
                               agent-node
                               "simple-retrieval"
                               query
                               config)
           "research_required" (aor/emit!
                                agent-node
                                "complex-research"
                                query
                                config)
           "langchain_specific" (aor/emit!
                                 agent-node
                                 "langchain-research"
                                 query
                                 config)
           ;; Default fallback
           (aor/emit!
            agent-node
            "simple-retrieval"
            query
            config)))))

    (aor/node
     "simple-retrieval"
     nil
     (fn simple-retrieval-node [agent-node query config]
       (let [search-tools (aor/agent-client agent-node "search-tools")
             results      (aor/agent-invoke
                           search-tools
                           [(create-tool-execution-request
                             "SearchDocuments"
                             {:query query :max_results 5})]
                           config)
             response     (synthesize-response agent-node query results)]
         (aor/result! agent-node {:response response :type "simple"}))))

    (aor/node
     "complex-research"
     "research-execution"
     (fn complex-research-node [agent-node query config]
       (let [research-plan (create-research-plan agent-node query)]
         (aor/emit!
          agent-node
          "research-execution"
          query
          research-plan
          config))))

    (aor/node
     "langchain-research"
     "research-execution"
     (fn langchain-research-node [agent-node query config]
       (let [;; Create a LangChain-focused research plan
             langchain-plan
             {:sub_questions
              [(str "What is LangChain in the context of: " query)
               (str "How does LangChain relate to: " query)
               (str "What are the key LangChain concepts for: " query)]
              :research_strategy
              "Focus on LangChain-specific information and concepts"
              :expected_sources  ["LangChain documentation"
                                  "LangChain examples"]}]
         (aor/emit!
          agent-node
          "research-execution"
          query
          langchain-plan
          config))))

    (aor/node
     "research-execution"
     nil
     (fn research-execution-node [agent-node query research-plan config]
       (let [sub-questions    (:sub_questions research-plan)
             ;; Use parallel retrieval for better performance
             research-results (parallel-retrieve agent-node sub-questions)
             final-response   (synthesize-response
                               agent-node
                               query
                               research-results)]
         (aor/result! agent-node
                      {:response      final-response
                       :type          "complex"
                       :research_plan research-plan
                       :sub_results   research-results})))))

  ;; Tools agent
  (tools/new-tools-agent topology "search-tools" RESEARCH-TOOLS))

;;; Example Invocation

;; Sample documents for testing
(def sample-documents
  {"langchain-intro"
   "LangChain is a framework for developing applications powered by language models. It enables applications to be context-aware and reason about their environment. LangChain provides chains, agents, and tools for building LLM applications. Key components include prompt templates, memory, and document loaders."

   "langchain-chains"
   "LangChain chains are sequences of calls to LLMs or other utilities. Simple chains handle single tasks, while complex chains can route between different paths. Sequential chains pass outputs from one step as inputs to the next. LangChain provides built-in chains like ConversationChain and SQLDatabaseChain."

   "langchain-agents"
   "LangChain agents use LLMs to decide which actions to take and in what order. Agents have access to tools and can dynamically determine the best sequence of actions. Common agent types include ReAct agents, Plan-and-Execute agents, and tool-calling agents."

   "agent-o-rama-guide"
   "Agent-o-rama is a framework for building parallel, scalable, and stateful AI agents in Java or Clojure, built on Red Planet Labs' Rama distributed computing platform. It provides agent orchestration, state management, and integrates with LangChain4j for AI model interactions."

   "rag-concepts"
   "Retrieval-Augmented Generation (RAG) combines the power of large language models with external knowledge retrieval to provide more accurate and contextual responses. RAG systems first retrieve relevant documents, then use those documents as context for LLM generation."

   "langchain-rag"
   "LangChain provides extensive RAG capabilities including document loaders, text splitters, vector stores, and retrievers. The RAG chain in LangChain combines retrieval with generation. LangChain supports various vector databases like Chroma, Pinecone, and FAISS for document storage and retrieval."})

(defn run-agent
  "Run the RAG research agent with test queries.

  Environment Requirements:
  - OPENAI_API_KEY must be set in the shell environment
  - Requires OpenAI API access for both chat completion (gpt-4o-mini) and
    embeddings (text-embedding-3-small)

  The agent demonstrates sophisticated RAG capabilities including:
  - Smart query routing (simple retrieval vs complex research vs
    LangChain-specific)
  - Parallel document retrieval for improved performance
  - Multi-step research planning and execution
  - Vector-based semantic document search with cosine similarity
  - Structured JSON response generation and synthesis

  Usage:
    (run-agent)                    ; Run with default test queries
    (run-agent [\"custom query\"])  ; Run with custom queries

  The function will:
  1. Start the agent-o-rama module with UI
  2. Index sample documents (LangChain, Agent-o-rama, RAG concepts)
  3. Process each query through the intelligent routing system
  4. Display results including research plans and classifications"
  ([]
   (run-agent
    ["What is LangChain and how does it work?" ;; LangChain-specific
     "How do LangChain agents differ from simple chains?" ;; LangChain-specific
     "Compare and contrast LangChain with Agent-o-rama frameworks" ;; Complex
     "What is RAG?" ;; Simple retrieval
     "Explain the complete LangChain ecosystem for building AI apps" ;; Complex
     "How do you implement retrieval in LangChain?"])) ;; LangChain-specific
  ([queries]
   (with-open [ipc (rtest/create-ipc)
               _ (aor/start-ui ipc)]
     (rtest/launch-module! ipc RagResearchModule {:tasks 4 :threads 2})
     (let [module-name   (rama/get-module-name RagResearchModule)
           agent-manager (aor/agent-manager ipc module-name)]

       ;; Index sample documents
       (with-open [search-tools (aor/agent-client agent-manager "search-tools")]
         (doseq [[doc-id content] sample-documents]
           (aor/agent-invoke
            search-tools
            [(create-tool-execution-request
              "IndexDocument"
              {:document_id doc-id :content content})]
            {})))

       ;; Run queries
       (with-open [agent (aor/agent-client agent-manager "RagResearchAgent")]
         (doseq [query queries]
           (println "\n=== Query:" query "===")
           (try
             (let [agent-invoke (aor/agent-initiate agent query {})
                   step         (aor/agent-next-step agent agent-invoke)
                   result       (:result step)]
               (assert (instance? AgentComplete step))
               (println "Response:" (:response result))
               (when (:research_plan result)
                 (println "Research Plan:" (:research_plan result)))
               (when (:classification result)
                 (println "Classification:" (:classification result))))
             (catch Exception e
               (println "Error:" (.getMessage e))))))))))
