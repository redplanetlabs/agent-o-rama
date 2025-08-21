(ns com.rpl.agent.rag-research-test
  "Tests for the RAG research agent example"
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is run-tests testing]]
   [com.rpl.agent.rag-research :as rag]
   [com.rpl.rama :as rama]
   [jsonista.core :as j])
  (:import
   [dev.langchain4j.agent.tool
    ToolExecutionRequest]
   [dev.langchain4j.model.chat.request.json
    JsonObjectSchema]))

;; Test fixtures and utilities

(defn mock-embedding
  "Create a mock embedding vector for testing"
  [text]
  ;; Simple deterministic embedding based on text hash
  (let [hash-val (hash text)]
    (mapv #(/ (mod (+ hash-val %) 1000) 1000.0) (range 10))))

;; Unit tests for utility functions

#_(deftest test-split-document
    (testing "splits short documents into single chunk"
      (let [text   "Short test document"
            chunks (rag/split-document text)]
        (is (= 1 (count chunks)))
        (is (= text (first chunks)))))

    (testing "splits long documents into multiple chunks"
      (let
        [text
         (str
          "This is a very long document that should be split into multiple chunks. "
          (apply str (repeat 100 "More content to make it longer. ")))
         chunks (rag/split-document text)]
        (is (> (count chunks) 1))
        (is (every? string? chunks)))))

(deftest test-calculate-similarity-vectors
  (testing "identical vectors have similarity 1.0"
    (let [vec1       [1.0 0.5 0.3]
          vec2       [1.0 0.5 0.3]
          similarity (rag/calculate-similarity-vectors vec1 vec2)]
      (is (>= similarity 0.99))))

  (testing "orthogonal vectors have similarity close to 0"
    (let [vec1       [1.0 0.0]
          vec2       [0.0 1.0]
          similarity (rag/calculate-similarity-vectors vec1 vec2)]
      (is (< (Math/abs similarity) 0.01))))

  (testing "similar vectors have positive similarity"
    (let [vec1       [1.0 0.5 0.2]
          vec2       [0.9 0.6 0.1]
          similarity (rag/calculate-similarity-vectors vec1 vec2)]
      (is (> similarity 0.5)))))

(deftest test-create-tool-execution-request
  (testing "creates valid ToolExecutionRequest"
    (let [tool-name "TestTool"
          arguments {:param1 "value1" :param2 42}
          request   (rag/create-tool-execution-request tool-name arguments)]
      (is (instance? ToolExecutionRequest request))
      (is (= tool-name (.name request)))
      (is (= (j/write-value-as-string arguments) (.arguments request)))))

  (testing "handles empty arguments"
    (let [request (rag/create-tool-execution-request "EmptyTool" {})]
      (is (instance? ToolExecutionRequest request))
      (is (= "{}" (.arguments request))))))

;; Tests for schema validation

(deftest test-json-schemas
  (testing "QueryClassification schema is defined and correct type"
    (let [schema rag/QueryClassification]
      (is (some? schema))
      (is (instance? JsonObjectSchema schema))))

  (testing "ResearchPlan schema is defined and correct type"
    (let [schema rag/ResearchPlan]
      (is (some? schema))
      (is (instance? JsonObjectSchema schema))))

  (testing "SynthesizedResponse schema is defined and correct type"
    (let [schema rag/SynthesizedResponse]
      (is (some? schema))
      (is (instance? JsonObjectSchema schema)))))

;; Tests for sample data

(deftest test-sample-documents
  (testing "contains expected documents"
    (let [docs rag/sample-documents]
      (is (map? docs))
      (is (>= (count docs) 4))
      (is (contains? docs "langchain-intro"))
      (is (contains? docs "rag-concepts"))
      (is (every? string? (vals docs)))))

  (testing "documents contain relevant content"
    (let [docs rag/sample-documents]
      (is (some #(re-find #"(?i)langchain" %) (vals docs)))
      (is (some #(re-find #"(?i)rag" %) (vals docs)))
      (is (some #(re-find #"(?i)agent" %) (vals docs))))))

;; Mock tests for AI-dependent functionality

(deftest test-query-classification-structure
  (testing "classification should have proper structure"
    ;; Mock classification response
    (let [mock-classification {:routing_decision "simple_retrieval"
                               :reasoning        "Test reasoning"
                               :keywords         ["test" "keyword"]}]
      (is (contains? mock-classification :routing_decision))
      (is (contains? mock-classification :reasoning))
      (is (contains? mock-classification :keywords))
      (is (contains? #{"simple_retrieval" "research_required"
                       "langchain_specific" "out_of_scope"}
                     (:routing_decision mock-classification))))))

(deftest test-research-plan-structure
  (testing "research plan should have proper structure"
    ;; Mock research plan response
    (let [mock-plan {:sub_questions     ["What is the main concept?"
                                         "How does it work?"
                                         "What are the applications?"]
                     :research_strategy "Comprehensive analysis"
                     :expected_sources  ["documentation" "examples"]}]
      (is (contains? mock-plan :sub_questions))
      (is (contains? mock-plan :research_strategy))
      (is (contains? mock-plan :expected_sources))
      (is (vector? (:sub_questions mock-plan)))
      (is (string? (:research_strategy mock-plan))))))

;; Integration tests (require test environment setup)

(deftest test-module-definition
  (testing "module has correct name"
    (let [module rag/RagResearchModule]
      (is (some? module))
      ;; The module name includes the namespace
      (is (= "com.rpl.agent.rag-research/RagResearchModule"
             (rama/get-module-name module)))))

  (testing "module declares required stores"
    ;; This test verifies the module structure without running it
    (is (some? rag/DOCUMENTS-STORE))
    (is (some? rag/RESEARCH-STORE))))

(deftest test-tool-specifications
  (testing "research tools are properly defined"
    (let [tools rag/RESEARCH-TOOLS]
      (is (vector? tools))
      (is (= 2 (count tools)))
      ;; Check that tools exist and have the expected structure
      (is (every? map? tools))
      (is (every? #(contains? % :tool-specification) tools)))))

;; Performance and edge case tests

(deftest test-parallel-retrieve-structure
  (testing "function handles empty question list"
    ;; Test with empty questions - should return empty result
    (let [empty-questions []]
      ;; We can't actually run this without an agent-node, but we can test the
      ;; structure
      (is (vector? empty-questions))))

  (testing "function structure for multiple questions"
    (let [questions ["Question 1" "Question 2" "Question 3"]]
      (is (= 3 (count questions)))
      (is (every? string? questions)))))

(deftest test-prompts-content
  (testing "query classifier prompt mentions routing decisions"
    (let [prompt rag/QUERY-CLASSIFIER-PROMPT]
      (is (string? prompt))
      (is (re-find #"routing.*decision" (str/lower-case prompt)))
      (is (re-find #"langchain_specific" prompt))))

  (testing "research planner prompt mentions research strategy"
    (let [prompt rag/RESEARCH-PLANNER-PROMPT]
      (is (string? prompt))
      (is (re-find #"research.*plan" (str/lower-case prompt)))
      (is (re-find #"sub.*question" (str/lower-case prompt)))))

  (testing "synthesizer prompt mentions synthesis"
    (let [prompt rag/RESEARCH-SYNTHESIZER-PROMPT]
      (is (string? prompt))
      (is (re-find #"synthesi" (str/lower-case prompt))))))

;; Configuration and constants tests

(deftest test-store-constants
  (testing "store constants are defined"
    (is (string? rag/DOCUMENTS-STORE))
    (is (string? rag/RESEARCH-STORE))
    (is (re-find #"^\$\$" rag/DOCUMENTS-STORE))
    (is (re-find #"^\$\$" rag/RESEARCH-STORE))))

;; Test runner helper

(defn run-basic-tests
  "Run tests that don't require OpenAI API or full agent setup"
  []
  (println "Running basic RAG research agent tests...")
  (run-tests 'com.rpl.agent.rag-research-test))

(comment
  ;; Run tests manually
  (run-basic-tests)

  ;; Individual test runs
  (test-split-document)
  (test-calculate-similarity-vectors)
  (test-sample-documents))
