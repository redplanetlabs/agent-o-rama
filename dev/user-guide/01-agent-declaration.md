# Agent Declaration

You start your Agent-O-Rama journey here: declaring agents. This chapter covers the fundamental building blocks - [agent modules](../terms/agent-module.md), [topology](../terms/agents-topology.md), [graphs](../terms/agent-graph.md), and [nodes](../terms/agent-node.md).

> **Reference**: See [Agent Module](../terms/agent-module.md) and [Agent Graph](../terms/agent-graph.md) documentation for comprehensive details.

## Agent Module: Your Container

An [agent module](../terms/agent-module.md) packages agents, stores, and resources into a deployable unit. It's your entry point into AOR:

**Clojure:**
```clojure
(aor/defagentmodule BasicAgentModule
  [topology]
  ;; Your agents go here
  )
```

**Java:**
```java
public class BasicAgentModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        // Your agents go here
    }
}
```

The module receives a [topology](../terms/agents-topology.md) - your canvas for defining agents and their resources.

## Your First Agent

Let's build a real agent from the basic_agent.clj example:

**Clojure:**
```clojure
(ns com.rpl.agent.basic.basic-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule BasicAgentModule
  [topology]

  ;; Create agent with single node that processes input and returns result
  (-> topology
      (aor/new-agent "BasicAgent")
      (aor/node
       "process"      ; node name
       nil            ; no next node (terminal)
       (fn [agent-node user-name]
         (let [result (str "Welcome to agent-o-rama, " user-name "!")]
           (aor/result! agent-node result))))))
```

**Java:**
```java
public class BasicAgentModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        topology.newAgent("BasicAgent")
            .node("process", null, (agentNode, userName) -> {
                String result = "Welcome to agent-o-rama, " + userName + "!";
                agentNode.result(result);
            });
    }
}
```

You've declared:
1. An **agent module** named `BasicAgentModule`
2. An **agent** named `BasicAgent`
3. A **node** named `process` that creates a welcome message
4. A **result** that terminates the agent execution

## Agent Graph Structure

An [agent graph](../terms/agent-graph.md) defines your agent's execution flow as connected [nodes](../terms/agent-node.md). Each node:
- Has a unique name
- Specifies its next node (or `nil` for terminal nodes)
- Contains a function that executes your logic

The graph starts at the first defined node and flows through connections until reaching a terminal node or `result!` call.

## Node Declaration

[Agent node declarations](../terms/agent-node-declaration.md) are blueprints for your computation units. The declaration pattern:

```clojure
(aor/node
 "node-name"           ; unique identifier
 "next-node"           ; where to emit (or nil)
 node-function)        ; your logic
```

The [agent node declaration](../terms/agent-node-declaration.md) specifies:
- Node name (unique identifier)
- Target node (or nil for terminal nodes)
- [Node function](../terms/agent-node-function.md) containing your logic

The [node function](../terms/agent-node-function.md) receives:
- `agent-node` - runtime [agent node](../terms/agent-node.md) with access to framework services
- Additional arguments from invocation or emissions

## Agent Result

Every agent execution ends with an [agent result](../terms/agent-result.md). Call `result!` to:
- Return a value to the caller
- Terminate the agent execution
- Signal successful completion

```clojure
(aor/result! agent-node return-value)
```

## Complete Example

Here's the full basic_agent.clj showing module, agent, node, and result:

```clojure
(aor/defagentmodule BasicAgentModule
  [topology]

  (-> topology
      (aor/new-agent "BasicAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node user-name]
         (let [result (str "Welcome to agent-o-rama, " user-name "!")]
           (aor/result! agent-node result))))))

(defn -main [& _args]
  ;; Create in-process cluster (we'll cover this in Chapter 2)
  (with-open [ipc (rtest/create-ipc)]
    ;; Launch the module
    (rtest/launch-module! ipc BasicAgentModule {:tasks 1 :threads 1})

    ;; Get client and invoke (we'll cover this in Chapter 3)
    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name BasicAgentModule))
          agent   (aor/agent-client manager "BasicAgent")]

      (println "Result:" (aor/agent-invoke agent "Alice"))
      ;; => Result: Welcome to agent-o-rama, Alice!
      )))
```

## Key Concepts

You've learned the declaration hierarchy:

1. **[Agent Module](../terms/agent-module.md)**: Top-level container for your system
2. **[Agent Topology](../terms/agents-topology.md)**: Configuration context passed to modules
3. **[Agent Graph](../terms/agent-graph.md)**: Directed flow of execution through nodes
4. **[Agent Node Declaration](../terms/agent-node-declaration.md)**: Blueprint for computation units
5. **[Agent Node Function](../terms/agent-node-function.md)**: Your business logic
6. **[Agent Node](../terms/agent-node.md)**: Runtime execution context
7. **[Agent Result](../terms/agent-result.md)**: Final output and termination

These seven concepts form the foundation of every AOR agent. Master these, and you're ready to build complex distributed systems.

## What's Next

You've declared your first agent. Next, learn about the [Rama Infrastructure](02-rama-infrastructure.md) that runs your agents across distributed clusters.