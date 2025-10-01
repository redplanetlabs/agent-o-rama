# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

agent-o-rama is a framework for building parallel, scalable, and stateful AI agents in Java or Clojure. It's built on top of Red Planet Labs' Rama distributed computing platform and integrates with LangChain4j for AI model interactions.

@dev/glossary.md

## Architecture

The codebase is structured as a multi-language project:

- **Clojure Backend** (`src/clj/`): Core agent orchestration, state management, and Rama integration
- **ClojureScript Frontend** (`src/cljs/`): Web UI for monitoring and visualizing agent execution
- **Java API** (`src/java/`): Java bindings and interfaces for the agent framework
- **Examples** (`examples/clj/`): Sample agent implementations including todo management, research agents, and ReAct patterns

### Key Components

1. **Agent Topology** (`src/clj/com/rpl/agent_o_rama.clj`): Main entry point defining the agent system topology
2. **Agent Execution** (`src/clj/com/rpl/agent_o_rama/impl/`): Core implementation including:
   - `agent-node.clj`: Individual agent node execution
   - `graph.clj`: Agent graph structure and navigation
   - `topology.clj`: Distributed topology management
   - `client.clj`: Client interface for agent interaction
3. **UI System** (`src/cljs/com/rpl/agent_o_rama/ui/`): React-based monitoring interface
4. **Store Abstractions** (`src/clj/com/rpl/agent_o_rama/store.clj`): Persistent state management

## Development Commands

### Build and Setup
```bash
# Install frontend dependencies
npm i

# Compile ClojureScript frontend
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm compile :frontend

# Install to local Maven repository
lein install

# Complete build (all steps)
./scripts/build.sh
```

### Development Workflow
```bash
# Start REPL
lein repl

# Run with UI profile for frontend development
lein with-profile +ui repl

# Start development server with ClojureScript compilation
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm watch :frontend
```

### Testing
```bash
# Run Clojure tests
lein test

# Test specific namespace
lein test com.rpl.agent-o-rama.test-namespace

# Run ClojureScript tests
lein with-profile +ui run -m shadow.cljs.devtools.cli compile :test

# Watch and auto-run ClojureScript tests
lein with-profile +ui run -m shadow.cljs.devtools.cli watch :test
```
- running an ipc test can take several minutes
- ClojureScript tests use Node.js with jsdom for DOM support

## Key Dependencies

- **Rama**: Distributed computing platform (primary runtime)
- **LangChain4j**: AI model integration and tool calling
- **Shadow-CLJS**: ClojureScript compilation and development
- **React/UIX**: Frontend UI framework
- **Transit**: Data serialization between Clojure/ClojureScript


## Requirements

- requires jdk 21 or newer
- the implementation uses java interfaces and classes

## Agent Development Patterns

### Basic Agent Structure
```clojure
(aor/defagentmodule MyAgentModule
  [topology]

  ; Declare shared objects and stores
  (aor/declare-agent-object topology "my-object" value)
  (aor/declare-key-value-store topology "my-store" String Object)

  ; Define agent graph
  (-> topology
      (aor/new-agent "MyAgent")
      (aor/node "start-node" "next-node"
                (fn [agent-node & args]
                  ; Node logic here
                  (aor/emit! agent-node "next-node" result)))
      (aor/node "next-node" nil
                (fn [agent-node & args]
                  (aor/result! agent-node final-result)))))
```

### Using Stores
```clojure
; In agent node functions:
(let [store (aor/get-store agent-node "store-name")]
  (store/get store key)
  (store/put! store key value))
```

### LangChain4j Integration
```clojure
; Declare OpenAI model
(aor/declare-agent-object-builder
  topology
  "openai-model"
  (fn [setup]
    (-> (OpenAiChatModel/builder)
        (.apiKey api-key)
        (.modelName "gpt-4o-mini")
        .build)))

; Use in agent node
(let [model (aor/get-agent-object agent-node "openai-model")
      response (lc4j/chat model (lc4j/chat-request messages options))]
  ; Process response
  )
```

## UI Development

The web UI is built with ClojureScript and React, providing real-time monitoring of agent execution, state visualization, and debugging tools. Key UI components are located in `src/cljs/com/rpl/agent_o_rama/ui/`.

To start the UI development server:
```bash
lein with-profile +ui run -m shadow.cljs.devtools.cli --npm watch :frontend
```

The UI will be available at `http://localhost:8080` when running agents with `(aor/start-ui ipc)`.

@dev/ui.md

## Working with Examples

The `examples/clj/` directory contains fully functional agent implementations:

- **todo.clj**: Conversational todo management with long-term memory
- **research_agent.clj**: Multi-step research and analysis agent
- **react.clj**: ReAct pattern implementation with tool calling

To run an example:
```clojure
; In the example namespace
(run-agent)  ; Most examples provide this function
```
- the project task list is in .claude/TASKS.md
