# Agent-o-rama User Guide

Framework for building parallel, scalable, stateful AI agents using Clojure, built on Red Planet Labs' Rama with LangChain4j integration.

## Core Concepts

**Agent Module**: Complete agent system definition packaged as deployable Rama module. Contains agent definitions, stores, agent objects, and evaluators.

**Agent Graph**: Directed graph defining execution flow through interconnected nodes. Supports cycles and parallel execution.

**Agent Node**: Individual execution unit performing computation. Can emit to other nodes or return results.

**Agents Topology**: Top-level container for defining agents, stores, and objects within a module.

**Agent Objects**: Shared resources (AI models, APIs, tools) accessible across all agent nodes.

**Stores**: Persistent storage for agent state:
- Key-Value: Simple persistence
- Document: Structured multi-field storage
- PState: Rama's native persistent state

**Dataset**: Managed collection of input/output examples for agent evaluation and testing.

**Evaluators**: Functions for measuring agent performance against datasets.

## Basic Agent Structure

```clojure
(aor/defagentmodule MyModule
  [topology]

  ;; Agent objects
  (aor/declare-agent-object topology "api-key" (System/getenv "API_KEY"))
  
  ;; Stores
  (aor/declare-key-value-store topology "my-store" String Object)
  
  ;; Agent graph
  (-> topology
      (aor/new-agent "MyAgent")
      (aor/node "start" "end"
                (fn [agent-node input]
                  (aor/emit! agent-node "end" processed-input)))
      (aor/node "end" nil
                (fn [agent-node result]
                  (aor/result! agent-node result)))))
```

**Node Operations**:
- `(aor/emit! agent-node "next-node" data)`: Send to another node
- `(aor/result! agent-node value)`: Return final result
- `(aor/get-store agent-node "store-name")`: Access stores
- `(aor/get-agent-object agent-node "object-name")`: Access shared objects
- `(aor/stream-chunk! agent-node data)`: Stream intermediate data
- `(aor/get-human-input agent-node "prompt")`: Request human input

## Store Operations

```clojure
(let [store (aor/get-store agent-node "store-name")]
  ;; Key-value operations
  (store/get store key)
  (store/put! store key value)
  (store/update! store key update-fn)
  
  ;; PState operations
  (store/pstate-select [path] store partition-key)
  (store/pstate-transform! [path operation] store partition-key))
```

## Tools Integration

```clojure
;; Define tools
(def TOOLS
  [(tools/tool-info
    (tools/tool-specification "search" schema "Description")
    (fn [agent-node _ arguments] 
      ;; Tool implementation
      )
    {:include-context? true})])

;; In module definition
(tools/new-tools-agent topology "tools" TOOLS)

;; In agent node
(let [tools-client (aor/agent-client agent-node "tools")
      results (aor/agent-invoke tools-client tool-calls)]
  ;; Process results
  )
```

## Human Input Integration

```clojure
(aor/node "interactive" "next"
          (fn [agent-node context]
            (let [response (aor/get-human-input agent-node "What should I do?")]
              (aor/emit! agent-node "next" response))))

;; Client-side handling
(let [agent-invoke (aor/agent-initiate agent input)]
  (loop [step (aor/agent-next-step agent agent-invoke)]
    (if (instance? HumanInputRequest step)
      (do
        (aor/provide-human-input agent step user-response)
        (recur (aor/agent-next-step agent agent-invoke)))
      (:result step))))
```

## Aggregation Patterns

```clojure
;; Fan-out/fan-in processing
(-> topology
    (aor/new-agent "AggAgent")
    
    ;; Start aggregation
    (aor/agg-start-node "start" "process"
                        (fn [agent-node items]
                          (doseq [item items]
                            (aor/emit! agent-node "process" item))))
    
    ;; Process items in parallel
    (aor/node "process" "collect"
              (fn [agent-node item]
                ;; Process individual item
                (aor/emit! agent-node "collect" processed-item)))
    
    ;; Collect results
    (aor/agg-node "collect" "finish" 
                  aggs/+vec-agg
                  (fn [agent-node results]
                    (aor/result! agent-node results))))
```

## Streaming

```clojure
;; Stream data from nodes
(aor/node "streaming-node" "next"
          (fn [agent-node input]
            (doseq [chunk (process-streaming input)]
              (aor/stream-chunk! agent-node chunk))
            (aor/emit! agent-node "next" final-result)))

;; Client-side streaming
(let [stream (aor/agent-stream agent agent-invoke "streaming-node")]
  ;; Process streaming chunks
  )
```

## Dataset Management

```clojure
;; Create dataset
(aor/create-dataset! manager "my-dataset" "Description")

;; Add examples
(aor/add-dataset-example! manager dataset-id
                          {:input "question"
                           :reference-output "expected answer"
                           :tags #{"test"}})

;; Search examples
(aor/search-examples manager dataset-id "query" {:limit 10})
```

## Evaluators

```clojure
;; Declare evaluator builder in module
(aor/declare-evaluator-builder 
 topology "accuracy"
 "Measures response accuracy"
 (fn [params]
   (fn [fetcher input reference-output output]
     ;; Evaluation logic
     {"score" 0.85 "details" "..."})))

;; Create and use evaluator
(aor/create-evaluator! manager "eval1" "accuracy" {} "Test evaluator")
(aor/try-evaluator manager "eval1" input reference-output actual-output)
```

## Execution Patterns

**Synchronous**:
```clojure
(let [result (aor/agent-invoke agent input)]
  result)
```

**Asynchronous**:
```clojure
(let [agent-invoke (aor/agent-initiate agent input)
      result (aor/agent-result agent agent-invoke)]
  result)
```

**Forking**:
```clojure
(let [fork (aor/agent-fork agent original-invoke 
                           {"node-id" ["new-args"]})]
  (aor/agent-result agent fork))
```

## Complete Example: ReAct Agent

```clojure
(ns my-app.react
  (:require [com.rpl.agent-o-rama :as aor]
            [com.rpl.agent-o-rama.langchain4j :as lc4j]
            [com.rpl.agent-o-rama.tools :as tools])
  (:import [dev.langchain4j.model.openai OpenAiChatModel]
           [dev.langchain4j.web.search.tavily TavilyWebSearchEngine]
           [dev.langchain4j.message SystemMessage UserMessage]))

(def TOOLS
  [(tools/tool-info
    (tools/tool-specification 
     "search" 
     (lj/object {:required ["terms"]} {"terms" (lj/string "Search terms")})
     "Search the web")
    (fn [agent-node _ arguments]
      (let [tavily (aor/get-agent-object agent-node "tavily")
            results (.search tavily (get arguments "terms"))]
        (str results)))
    {:include-context? true})])

(aor/defagentmodule ReactModule
  [topology]
  
  (aor/declare-agent-object topology "openai-key" (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object topology "tavily-key" (System/getenv "TAVILY_API_KEY"))
  
  (aor/declare-agent-object-builder
   topology "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  
  (aor/declare-agent-object-builder
   topology "tavily" 
   (fn [setup]
     (-> (TavilyWebSearchEngine/builder)
         (.apiKey (aor/get-agent-object setup "tavily-key"))
         .build)))
  
  (tools/new-tools-agent topology "tools" TOOLS)
  
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
                      (let [results (aor/agent-invoke tools-client tool-calls)
                            next-messages (into (conj messages ai-message) results)]
                        (aor/emit! agent-node "chat" next-messages))
                      (aor/result! agent-node (.text ai-message))))))))

(defn run-react []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc ReactModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ReactModule))
          agent (aor/agent-client manager "ReActAgent")]
      (aor/agent-invoke agent [(SystemMessage/from "You are a helpful assistant")
                               (UserMessage. "What's the weather?")]))))
```

## Running Agents

```clojure
(defn run-agent []
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc MyModule {:tasks 4 :threads 2})
    (let [manager (aor/agent-manager ipc (rama/get-module-name MyModule))
          agent (aor/agent-client manager "MyAgent")]
      (aor/agent-invoke agent input))))
```

The UI is available at `http://localhost:8080` for monitoring agent execution, viewing traces, and managing datasets.
