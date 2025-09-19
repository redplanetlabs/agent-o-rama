# Agent-o-rama User Guide

Framework for building parallel, scalable, stateful AI [agents](glossary.md#agent) using Clojure, built on Red Planet Labs' Rama with LangChain4j integration.

See the [glossary](glossary.md) for detailed explanations of agent-o-rama concepts and the [terms documentation](terms/) for comprehensive guides to individual concepts.

## Core Concepts

**[Agent](glossary.md#agent)**: Distributed, stateful computational unit that executes directed graphs of nodes to solve complex AI workflows across cluster infrastructure.

**[Agent Module](glossary.md#agent-module)**: Complete agent system definition packaged as deployable Rama module. Contains agent definitions, stores, agent objects, and evaluators.

**[Agent Graph](glossary.md#agent-graph)**: Directed graph defining execution flow through interconnected nodes. Supports cycles and parallel execution patterns.

**[Agent Node](glossary.md#agent-node)**: Individual execution unit performing computation. Can emit to other nodes, return results, stream data, or request human input.

**[Agents Topology](glossary.md#agents-topology)**: Top-level container for defining agents, stores, and objects within a module.

**[Agent Objects](glossary.md#agent-objects)**: Shared resources (AI models, APIs, tools) with managed lifecycles accessible across all agent nodes.

**Stores**: Persistent storage for agent state:
- **[Key-Value Store](glossary.md#key-value-store)**: Typed storage for simple key-value pairs
- **[Document Store](glossary.md#document-store)**: Schema-flexible storage for complex nested data
- **[PState Store](glossary.md#pstate-store)**: Rama's native persistent state with distributed access

**[Dataset](glossary.md#dataset)**: Managed collection of input/output examples for agent testing and evaluation.

**[Evaluators](glossary.md#evaluators)**: Functions for measuring agent performance against datasets.

**[Experiment](glossary.md#experiment)**: Structured test run comparing agent performance across datasets with specific evaluators.

**[Aggregation](glossary.md#aggregation)**: Distributed computation pattern for scatter-gather operations and parallel processing.

## Basic Agent Structure

```clojure
(aor/defagentmodule MyModule
  [topology]

  ;; Static agent objects
  (aor/declare-agent-object topology "api-key" (System/getenv "API_KEY"))

  ;; Builder agent objects for complex resources
  (aor/declare-agent-object-builder topology "openai"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (aor/get-agent-object setup "api-key"))
          (.modelName "gpt-4o-mini")
          .build)))

  ;; Multiple store types
  (aor/declare-key-value-store topology "simple-store" String Object)
  (aor/declare-document-store topology "complex-store"
    "user-id" String
    "profile" Object
    "preferences" Object
    "history" Object)

  ;; Agent graph with multiple node types
  (-> topology
      (aor/new-agent "MyAgent")
      (aor/node "start" "process"
                (fn [agent-node input]
                  (let [store (aor/get-store agent-node "simple-store")]
                    (store/put! store "last-input" input)
                    (aor/emit! agent-node "process" input))))
      (aor/node "process" "end"
                (fn [agent-node data]
                  (let [model (aor/get-agent-object agent-node "openai")]
                    (aor/emit! agent-node "end" (process-with-ai model data)))))
      (aor/node "end" nil
                (fn [agent-node result]
                  (aor/result! agent-node result)))))
```

**Node Operations**:
- `(aor/emit! agent-node "next-node" data)`: Send data to another node
- `(aor/result! agent-node value)`: Return final result and terminate
- `(aor/get-store agent-node "store-name")`: Access persistent stores
- `(aor/get-agent-object agent-node "object-name")`: Access shared resources
- `(aor/stream-chunk! agent-node data)`: Stream intermediate data for real-time consumption
- `(aor/get-human-input agent-node "prompt")`: Request human input (blocks execution)
- `(aor/agent-client agent-node "agent-name")`: Get client for sub-agent invocation
- `(aor/agent-invoke client args)`: Invoke sub-agent and wait for result

## Store Operations

```clojure
;; Key-Value Store Operations
(let [kv-store (aor/get-store agent-node "simple-store")]
  (store/get kv-store key)                    ; Get value by key
  (store/get kv-store key default-value)      ; Get with default
  (store/put! kv-store key value)             ; Store key-value pair
  (store/delete! kv-store key)                ; Remove key
  (store/contains? kv-store key))             ; Check existence

;; Document Store Operations
(let [doc-store (aor/get-store agent-node "complex-store")]
  (store/get doc-store "user123" "profile")   ; Get specific field
  (store/put! doc-store "user123" "profile" profile-data)
  (store/select doc-store "user123")          ; Get all fields for key
  (store/update! doc-store "user123" "preferences" update-fn))

;; PState Store Operations
(let [pstate-store (aor/get-store agent-node "pstate-store")]
  (store/pstate-select ["users" user-id] pstate-store)
  (store/pstate-transform! ["users" user-id "score"] pstate-store inc)
  (store/pstate-transform! ["counters"] pstate-store
    (fn [counters] (update counters "total" (fnil inc 0)))))
```

## Tools Integration

```clojure
;; Define tools with JSON schema
(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "web-search"
     (lj/object
      {:description "Search the web for information"
       :required ["terms"]}
      {"terms" (lj/string "The search terms")})
     "Search the web using Tavily")
    (fn [agent-node _ arguments]
      (let [tavily (aor/get-agent-object agent-node "tavily")
            terms (get arguments "terms")
            results (.search tavily (WebSearchRequest/from terms 3))]
        (str/join "\n---\n" (map #(.text %) (.toDocuments results)))))
    {:include-context? true})])

;; In module definition - create tools sub-agent
(tools/new-tools-agent topology "tools" TOOLS)

;; In agent node - invoke tools
(aor/node "chat" "chat"
  (fn [agent-node messages]
    (let [model (aor/get-agent-object agent-node "openai")
          tools-client (aor/agent-client agent-node "tools")
          response (lc4j/chat model (lc4j/chat-request messages {:tools TOOLS}))
          ai-message (.aiMessage response)
          tool-calls (vec (.toolExecutionRequests ai-message))]
      (if (seq tool-calls)
        ;; Execute tools and continue conversation
        (let [results (aor/agent-invoke tools-client tool-calls)
              next-messages (into (conj messages ai-message) results)]
          (aor/emit! agent-node "chat" next-messages))
        ;; No tools needed, return response
        (aor/result! agent-node (.text ai-message))))))
```

## Human Input Integration

```clojure
;; Agent node requesting human input
(aor/node "approval" "execute"
  (fn [agent-node action-data]
    (let [approval (aor/get-human-input agent-node
                     (str "Approve action: " (:description action-data) "?"))]
      (if (= "yes" (str/lower-case approval))
        (aor/emit! agent-node "execute" action-data)
        (aor/result! agent-node "Action cancelled by user")))))

;; Client-side step-by-step handling
(let [agent-invoke (aor/agent-initiate client "MyAgent" input)]
  (loop [step (aor/agent-next-step client agent-invoke)]
    (case (type step)
      HumanInputRequest
      (let [user-input (get-user-input (.getPrompt step))]
        (aor/provide-human-input client agent-invoke (.getId step) user-input)
        (recur (aor/agent-next-step client agent-invoke)))

      AgentComplete
      (println "Final result:" (.getResult step))

      ;; Handle other step types
      (recur (aor/agent-next-step client agent-invoke)))))

;; Async handling
(aor/agent-next-step-async client agent-invoke
  (fn [step]
    (handle-step step)
    (when-not (instance? AgentComplete step)
      (schedule-next-step))))
```

## Aggregation Patterns

```clojure
;; Basic aggregation pattern
(-> topology
    (aor/new-agent "AggAgent")

    ;; Start aggregation - scatter phase
    (aor/agg-start-node "scatter" "gather"
      (fn [agent-node items]
        (doseq [item items]
          (aor/emit! agent-node "gather" item))
        ;; Return data available to aggregation node
        {:total-items (count items)}))

    ;; Process items in parallel
    (aor/node "process" "gather"
      (fn [agent-node item]
        (let [processed (expensive-computation item)]
          (aor/emit! agent-node "gather" processed))))

    ;; Collect and combine results
    (aor/agg-node "gather" nil aggs/+vector
      (fn [agent-node collected-results scatter-data]
        (aor/result! agent-node
          {:results collected-results
           :metadata scatter-data}))))

;; Multi-aggregation with custom logic
(-> topology
    (aor/new-agent "MultiAggAgent")
    (aor/agg-start-node "start" "stats"
      (fn [agent-node data-points]
        (doseq [point data-points]
          (aor/emit! agent-node "stats" point))))

    (aor/multi-agg
      :init {:sum 0 :count 0 :max Integer/MIN_VALUE}

      :on "stats"
      (fn [acc value]
        (-> acc
            (update :sum + value)
            (update :count inc)
            (update :max max value)))

      :on "final"
      (fn [acc]
        (assoc acc :average (/ (:sum acc) (:count acc))))))
```

## Streaming

```clojure
;; Stream data from nodes
(aor/node "streaming-node" "next"
  (fn [agent-node input]
    (let [data-stream (process-large-dataset input)]
      ;; Stream chunks as they're processed
      (doseq [chunk data-stream]
        (aor/stream-chunk! agent-node {:chunk chunk :timestamp (System/currentTimeMillis)}))
      ;; Continue to next node with summary
      (aor/emit! agent-node "next" {:status "complete" :chunks-sent (count data-stream)}))))

;; Client-side streaming consumption
(let [agent-invoke (aor/agent-initiate client "StreamingAgent" input)
      stream (aor/agent-stream client agent-invoke "streaming-node")]
  ;; Process chunks as they arrive
  (doseq [chunk stream]
    (println "Received chunk:" chunk))
  ;; Get final result
  (let [result (aor/agent-result client agent-invoke)]
    (println "Final result:" result)))

;; Stream specific node outputs
(let [specific-stream (aor/agent-stream-specific client agent-invoke "streaming-node" "data-type")]
  (process-typed-chunks specific-stream))

;; Stream all outputs from agent
(let [all-streams (aor/agent-stream-all client agent-invoke)]
  (monitor-all-outputs all-streams))
```

## Dataset Management

```clojure
;; Create dataset with metadata
(aor/create-dataset! manager "customer-support-v1"
  {:description "Customer service scenarios"
   :tags ["support" "qa"]
   :version "1.0"})

;; Add examples with rich context
(aor/add-dataset-example! manager "customer-support-v1"
  {:input {:message "How do I reset my password?"
           :user-type "premium"
           :context {:logged-in false :previous-attempts 2}}
   :reference-output {:action "password-reset"
                      :response "I'll help you reset your password..."
                      :steps ["verify-email" "send-reset-link"]}
   :metadata {:difficulty "easy"
              :category "authentication"
              :tags ["password" "reset"]}})

;; Bulk add examples
(aor/bulk-add-examples! manager "customer-support-v1" example-collection)

;; Search examples with filters
(aor/search-examples manager "customer-support-v1"
  {:query "password"
   :filters {:category "authentication" :difficulty "easy"}
   :limit 50
   :offset 0})

;; Get dataset statistics
(aor/dataset-stats manager "customer-support-v1")
;; Returns: {:count 1500 :categories ["auth" "billing"] :avg-difficulty 3.2}
```

## Evaluators

```clojure
;; Declare evaluator builder in module
(aor/declare-evaluator-builder
  topology "accuracy"
  "Measures response accuracy using semantic similarity"
  (fn [params]
    (fn [fetcher input reference-output output]
      (let [similarity (calculate-similarity reference-output output)
            exact-match (= reference-output output)]
        {:score similarity
         :exact-match exact-match
         :details {:reference (str reference-output)
                   :actual (str output)
                   :similarity similarity}}))))

;; Custom evaluator with AI model
(aor/declare-evaluator-builder
  topology "ai-judge"
  "AI-powered evaluation using GPT-4"
  (fn [{:keys [model-name criteria]}]
    (fn [fetcher input reference-output output]
      (let [model (get-ai-model model-name)
            prompt (format "Evaluate response quality...")
            evaluation (ai-evaluate model prompt)]
        {:score (:score evaluation)
         :reasoning (:explanation evaluation)
         :criteria-met (:criteria evaluation)}))))

;; Create evaluators with configuration
(aor/create-evaluator! manager "accuracy-eval" "accuracy" {} "Basic accuracy")
(aor/create-evaluator! manager "ai-eval" "ai-judge"
  {:model-name "gpt-4o"
   :criteria ["helpfulness" "accuracy" "clarity"]}
  "AI-powered evaluation")

;; Run single evaluation
(aor/try-evaluator manager "accuracy-eval" input expected-output actual-output)

;; Run experiment with multiple evaluators
(aor/run-experiment! manager
  {:agent-name "CustomerSupportAgent"
   :dataset-name "support-scenarios-v1"
   :evaluators ["accuracy-eval" "ai-eval"]
   :config {:max-parallel 10 :timeout-ms 30000}})
```

## Execution Patterns

**Synchronous Execution**:
```clojure
;; Simple synchronous call - blocks until complete
(let [result (aor/agent-invoke agent input)]
  (println "Result:" result))
```

**Asynchronous Execution**:
```clojure
;; Initiate agent and handle result separately
(let [agent-invoke (aor/agent-initiate agent input)
      ;; Do other work while agent runs
      other-results (do-other-work)
      ;; Get agent result when ready
      result (aor/agent-result agent agent-invoke)]
  {:agent-result result :other-results other-results})
```

**Step-by-Step Execution** ([Agent Step](glossary.md#agent-step)):
```clojure
;; Manual step control for debugging or human interaction
(let [agent-invoke (aor/agent-initiate agent input)]
  (loop [step (aor/agent-next-step agent agent-invoke)
         step-count 0]
    (println "Step" step-count ":" (type step))
    (if (instance? AgentComplete step)
      (.getResult step)
      (recur (aor/agent-next-step agent agent-invoke) (inc step-count)))))
```

**Forking Execution** ([Fork](glossary.md#fork)):
```clojure
;; Create execution branches with modified parameters
(let [original-invoke (aor/agent-initiate agent input)
      ;; Fork with different parameters for specific nodes
      fork1 (aor/agent-fork agent original-invoke {"node-id" ["variant-1"]})
      fork2 (aor/agent-fork agent original-invoke {"node-id" ["variant-2"]})
      ;; Run forks asynchronously
      result1 (future (aor/agent-result agent fork1))
      result2 (future (aor/agent-result agent fork2))]
  {:original (aor/agent-result agent original-invoke)
   :variant1 @result1
   :variant2 @result2})
```

**Streaming Execution**:
```clojure
;; Monitor real-time agent outputs
(let [agent-invoke (aor/agent-initiate agent input)
      stream (aor/agent-stream agent agent-invoke "processing-node")]
  ;; Process streaming data
  (future
    (doseq [chunk stream]
      (update-ui-with-progress chunk)))
  ;; Get final result
  (aor/agent-result agent agent-invoke))
```

## Complete Example: ReAct Agent

```clojure
(ns my-app.react
  (:require [clojure.string :as str]
            [com.rpl.agent-o-rama :as aor]
            [com.rpl.agent-o-rama.langchain4j :as lc4j]
            [com.rpl.agent-o-rama.langchain4j.json :as lj]
            [com.rpl.agent-o-rama.tools :as tools]
            [com.rpl.rama :as rama]
            [com.rpl.rama.test :as rtest])
  (:import [dev.langchain4j.data.document Document]
           [dev.langchain4j.data.message SystemMessage UserMessage]
           [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.web.search WebSearchRequest]
           [dev.langchain4j.web.search.tavily TavilyWebSearchEngine]))

;; Tool implementations
(defn- tavily-web-search-engine [api-key]
  (-> (TavilyWebSearchEngine/builder)
      (.apiKey api-key)
      (.excludeDomains ["en.wikipedia.org"])
      .build))

(defn- mk-tavily-search [{:keys [max-results] :or {max-results 3}}]
  (fn [agent-node _ arguments]
    (let [terms (get arguments "terms")
          tavily (aor/get-agent-object agent-node "tavily")
          search-request (WebSearchRequest/from terms (int max-results))
          results (.search ^TavilyWebSearchEngine tavily search-request)]
      (str/join "\n---\n"
        (mapv #(.text ^Document %)
              (.toDocuments results))))))

;; Tool specifications
(def TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "tavily"
     (lj/object
      {:description "Map containing the terms to search for"
       :required ["terms"]}
      {"terms" (lj/string "The terms to search for")})
     "Search the web")
    (mk-tavily-search {:max-results 3})
    {:include-context? true})])

;; Agent module definition
(aor/defagentmodule ReActModule
  [topology]

  ;; API keys as static objects
  (aor/declare-agent-object topology "openai-api-key" (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object topology "tavily-api-key" (System/getenv "TAVILY_API_KEY"))

  ;; AI model builder
  (aor/declare-agent-object-builder topology "openai"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (aor/get-agent-object setup "openai-api-key"))
          (.modelName "gpt-4o-mini")
          (.temperature 0.7)
          .build)))

  ;; Search engine builder
  (aor/declare-agent-object-builder topology "tavily"
    (fn [setup]
      (tavily-web-search-engine (aor/get-agent-object setup "tavily-api-key"))))

  ;; Tools sub-agent
  (tools/new-tools-agent topology "tools" TOOLS)

  ;; Main ReAct agent with reasoning loop
  (-> topology
      (aor/new-agent "ReActAgent")
      (aor/node "chat" "chat"
        (fn [agent-node messages]
          (let [model (aor/get-agent-object agent-node "openai")
                tools-client (aor/agent-client agent-node "tools")
                response (lc4j/chat model (lc4j/chat-request messages {:tools TOOLS}))
                ai-message (.aiMessage response)
                tool-calls (vec (.toolExecutionRequests ai-message))]
            (if (seq tool-calls)
              ;; Tools needed - execute and continue reasoning
              (let [tool-results (aor/agent-invoke tools-client tool-calls)
                    updated-messages (into (conj messages ai-message) tool-results)]
                (aor/emit! agent-node "chat" updated-messages))
              ;; No tools needed - return final answer
              (aor/result! agent-node (.text ai-message))))))))

;; Usage functions
(defn run-react
  "Run a single ReAct agent interaction"
  []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc ReActModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ReActModule))
          agent (aor/agent-client manager "ReActAgent")
          system-msg (SystemMessage/from "You are a helpful research assistant.")
          user-msg (UserMessage. "What's the current weather in San Francisco?")]
      (aor/agent-invoke agent [system-msg user-msg]))))

(defn run-interactive-react
  "Run an interactive ReAct session with step-by-step monitoring"
  []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc ReActModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ReActModule))
          agent (aor/agent-client manager "ReActAgent")
          messages [(SystemMessage/from "You are a helpful research assistant.")]
          agent-invoke (aor/agent-initiate agent messages)]
      ;; Monitor execution with streaming
      (future
        (let [stream (aor/agent-stream-all agent agent-invoke)]
          (doseq [chunk stream]
            (println "Stream:" chunk))))
      ;; Get final result
      (aor/agent-result agent agent-invoke))))
```

## Running Agents

```clojure
(defn run-agent
  "Basic agent execution with monitoring UI"
  []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc {:port 8080})]
    (rtest/launch-module! ipc MyModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name MyModule))
          agent (aor/agent-client manager "MyAgent")]
      (aor/agent-invoke agent input))))

(defn run-with-datasets
  "Run agent with dataset evaluation"
  []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc MyModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name MyModule))]
      ;; Create test dataset
      (aor/create-dataset! manager "test-cases" {:description "Basic test cases"})
      (aor/add-dataset-example! manager "test-cases"
        {:input "test input" :reference-output "expected output"})

      ;; Run experiment
      (aor/run-experiment! manager
        {:agent-name "MyAgent"
         :dataset-name "test-cases"
         :evaluators ["accuracy"]}))))

(defn production-setup
  "Production deployment with proper configuration"
  [cluster-config]
  (let [ipc (rama/client cluster-config)]
    (rama/launch-module! ipc MyModule {:tasks 8 :threads 4})
    (let [manager (aor/agent-manager ipc (rama/get-module-name MyModule))]
      ;; Return manager for application use
      manager)))
```

## Monitoring and Debugging

The built-in UI is available at `http://localhost:8080` (or configured port) and provides:

- **Agent Execution Visualization**: Real-time agent graph traversal
- **[Agent Trace](glossary.md#agent-trace)** Monitoring: Node-level execution details
- **Performance Metrics**: Execution times, throughput, error rates
- **Dataset Management**: Browse examples, view statistics
- **Experiment Results**: Evaluation scores, comparative analysis
- **Error Analysis**: Failed executions, retry patterns
- **Resource Utilization**: Memory, CPU, store usage

## Best Practices

- **State Management**: Use appropriate store types for your data patterns
- **Resource Sharing**: Leverage agent objects for expensive resources like AI models
- **Error Handling**: Implement graceful degradation and retry logic
- **Testing**: Use datasets and evaluators for systematic agent validation
- **Monitoring**: Utilize built-in tracing and metrics for production systems
- **Performance**: Consider aggregation patterns for parallel processing
- **Security**: Secure API keys and sensitive data through environment variables
