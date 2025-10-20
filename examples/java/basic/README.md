# Basic Agent Examples

This directory demonstrates the fundamental concepts of the agent-o-rama framework with Java agent implementations.

## Examples

### BasicAgent
A simple single-node agent that processes user names and returns welcome messages.

### MultiNodeAgent
An agent with multiple connected nodes demonstrating inter-node emissions and data flow through a greeting workflow.

### ProvidedEvaluatorBuildersExample
Demonstrates the three built-in evaluator builders (aor/llm-judge, aor/conciseness, aor/f1-score) for evaluating agent performance.

### RamaModuleAgent
Shows how to implement a Rama module directly (not extending AgentModule) to access full Rama features like depots and stream processing alongside agents.

## Overview

These examples show how to:
- Define agent modules extending `AgentModule`
- Create single and multi-node agent topologies
- Implement node functions using `RamaVoidFunction2` and `RamaVoidFunction3`
- Use `emit()` to pass data between nodes
- Deploy and invoke agents using `InProcessCluster`
- Test agent functionality

## Project Structure

```
basic/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/rpl/agent/basic/
│   │           ├── BasicAgent.java     # Single-node example
│   │           └── MultiNodeAgent.java # Multi-node example
│   └── test/
│       └── java/
│           └── com/rpl/agent/basic/
│               └── BasicAgentTest.java # Unit tests
└── pom.xml                             # Maven build configuration
```

## Running the Examples

### Prerequisites

- Java 21 or newer
- Maven 3.6+

### Build and Run

```bash
# Compile the examples
mvn clean compile

# Run the basic agent example
mvn exec:java -Dexec.mainClass="com.rpl.agent.basic.BasicAgent"

# Run the multi-node agent example
mvn exec:java -Dexec.mainClass="com.rpl.agent.basic.MultiNodeAgent"

# Run the provided evaluator builders example
mvn exec:java -Dexec.mainClass="com.rpl.agent.basic.ProvidedEvaluatorBuildersExample"

# Run the Rama module agent example
mvn exec:java -Dexec.mainClass="com.rpl.agent.basic.RamaModuleAgent"

# Run tests
mvn test
```

## Key Concepts

### Single-Node Agent (BasicAgent)

The `BasicModule` class extends `AgentModule` and defines a simple topology:

```java
public static class BasicModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("BasicAgent").node("process", null, new ProcessFunction());
  }
}
```

### Multi-Node Agent (MultiNodeAgent)

The `MultiNodeModule` demonstrates connected nodes with data flow:

```java
topology
  .newAgent("MultiNodeAgent")
  .node("receive", "personalize", new ReceiveFunction())
  .node("personalize", "finalize", new PersonalizeFunction())
  .node("finalize", null, new FinalizeFunction());
```

### Node Functions

Single parameter functions use `RamaVoidFunction2<AgentNode, String>`:

```java
public void invoke(AgentNode agentNode, String userName) {
  agentNode.result("Welcome to agent-o-rama, " + userName + "!");
}
```

Multiple parameter functions use `RamaVoidFunction3<AgentNode, String, String>`:

```java
public void invoke(AgentNode agentNode, String userName, String greeting) {
  String result = greeting + " Welcome to agent-o-rama! Thanks for joining us, " + userName + ".";
  agentNode.result(result);
}
```

### Inter-Node Communication

Use `emit()` to pass data between nodes:

```java
// Emit single value
agentNode.emit("nextNode", userName);

// Emit multiple values
agentNode.emit("nextNode", userName, greeting);
```

## Example Output

### BasicAgent
```
Starting Basic Agent Example...
Basic Agent Results:
User: "Alice" -> Result: Welcome to agent-o-rama, Alice!
User: "Bob" -> Result: Welcome to agent-o-rama, Bob!
```

### MultiNodeAgent
```
Starting Multi-Node Agent Example...
Multi-Node Agent Results:

--- Greeting Alice ---
Result: Hello, Alice! Welcome to agent-o-rama! Thanks for joining us, Alice.

--- Greeting Bob ---
Result: Hello, Bob! Welcome to agent-o-rama! Thanks for joining us, Bob.

--- Greeting Charlie ---
Result: Hello, Charlie! Welcome to agent-o-rama! Thanks for joining us, Charlie.
```

## Testing

The example includes a test class that demonstrates:
- Setting up an in-process cluster for testing
- Deploying agents in test environments
- Asserting on agent results

Note: Tests may take a minute to run due to InProcessCluster initialization.

## Next Steps

These examples serve as the foundation for more complex agent implementations. Consider exploring:
- Agent state management with stores
- Integration with AI models (see the React example)
- Streaming and asynchronous operations
- Human input workflows
