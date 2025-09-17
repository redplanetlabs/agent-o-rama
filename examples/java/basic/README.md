# Basic Agent Example

This example demonstrates the fundamental concepts of the agent-o-rama framework with a simple Java agent implementation.

## Overview

The BasicAgent example shows how to:
- Define an agent module extending `AgentsModule`
- Create a single-node agent topology
- Implement node functions using `RamaVoidFunction2`
- Deploy and invoke agents using `InProcessCluster`
- Test agent functionality

## Project Structure

```
basic/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── com/rpl/agent/basic/
│   │           └── BasicAgent.java    # Main example implementation
│   └── test/
│       └── java/
│           └── com/rpl/agent/basic/
│               └── BasicAgentTest.java # Unit tests
└── pom.xml                             # Maven build configuration
```

## Running the Example

### Prerequisites

- Java 21 or newer
- Maven 3.6+

### Build and Run

```bash
# Compile the example
mvn clean compile

# Run the main example
mvn exec:java

# Run tests
mvn test
```

## Key Concepts

### Agent Module

The `BasicModule` class extends `AgentsModule` and defines the agent topology:

```java
public static class BasicModule extends AgentsModule {
  @Override
  protected void defineAgents(AgentsTopology topology) {
    topology.newAgent("BasicAgent").node("process", null, new ProcessFunction());
  }
}
```

### Node Function

The `ProcessFunction` implements `RamaVoidFunction2<AgentNode, String>` to process input:

```java
public static class ProcessFunction implements RamaVoidFunction2<AgentNode, String> {
  @Override
  public void invoke(AgentNode agentNode, String userName) {
    String result = "Welcome to agent-o-rama, " + userName + "!";
    agentNode.result(result);
  }
}
```

### Agent Invocation

```java
// Create and launch the module
InProcessCluster ipc = InProcessCluster.create();
BasicModule module = new BasicModule();
ipc.launchModule(module, new LaunchConfig(1, 1));

// Get agent client and invoke
AgentManager manager = AgentManager.create(ipc, module.getModuleName());
AgentClient agent = manager.getAgentClient("BasicAgent");
String result = (String) agent.invoke("Alice");
```

## Example Output

```
Starting Basic Agent Example...
Basic Agent Results:
User: "Alice" -> Result: Welcome to agent-o-rama, Alice!
User: "Bob" -> Result: Welcome to agent-o-rama, Bob!
```

## Testing

The example includes a test class that demonstrates:
- Setting up an in-process cluster for testing
- Deploying agents in test environments
- Asserting on agent results

Note: Tests may take a minute to run due to InProcessCluster initialization.

## Next Steps

This example serves as the foundation for more complex agent implementations. Consider exploring:
- Multi-node agent topologies
- Agent state management with stores
- Integration with AI models (see the React example)
- Streaming and asynchronous operations