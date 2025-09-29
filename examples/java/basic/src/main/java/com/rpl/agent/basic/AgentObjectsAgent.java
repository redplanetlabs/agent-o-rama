package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Java example demonstrating agent objects for sharing resources across agent nodes.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>declareAgentObject: Static shared objects
 *   <li>declareAgentObjectBuilder: Dynamic object creation with setup context
 *   <li>getAgentObject: Access shared objects from agent nodes
 *   <li>Thread-unsafe objects: Safely using non-thread-safe objects via pooling
 *   <li>Object sharing across multiple nodes and invocations
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class AgentObjectsAgent {

  /**
   * Thread-unsafe service using volatile for fast, non-thread-safe state.
   *
   * <p>This service demonstrates how non-thread-safe objects can be safely used in agent-o-rama
   * through object pooling.
   */
  public static class MessageService {
    private final String version;
    private int counter;

    public MessageService(String version) {
      this.version = version;
      this.counter = 0;
    }

    public void resetForNewInvocation() {
      this.counter = 0;
    }

    public String useService(String input, String sendTo) {
      this.counter++;
      return String.format("v%s: %s (#%d -> %s)", version, input, counter, sendTo);
    }

    @Override
    public String toString() {
      return String.format("MessageService[version=%s, counter=%d]", version, counter);
    }
  }

  /** Agent Module demonstrating agent objects. */
  public static class AgentObjectsModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Static agent objects - simple values
      topology.declareAgentObject("app-version", "1.2.3");
      topology.declareAgentObject("send-to", "alerts");

      // Dynamic agent object builder - service that uses version and object name
      topology.declareAgentObjectBuilder(
          "message-service",
          setup -> {
            String version = (String) setup.getAgentObject("app-version");
            String objectName = setup.getObjectName();
            System.out.println("Building object: " + objectName + " with version: " + version);
            return new MessageService(version);
          });

      topology.newAgent("AgentObjectsAgent").node("use-service", null, new UseServiceFunction());
    }
  }

  /** Node function that uses shared agent objects including a thread-unsafe service. */
  public static class UseServiceFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String input) {
      MessageService service = (MessageService) agentNode.getAgentObject("message-service");
      String sendTo = (String) agentNode.getAgentObject("send-to");

      // Reset the thread-unsafe service for this new invocation
      service.resetForNewInvocation();

      // Use the thread-unsafe service (safe due to pooling)
      String result = service.useService(input, sendTo);

      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Agent Objects Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      AgentObjectsModule module = new AgentObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AgentObjectsAgent");

      System.out.println("Agent Objects Example:");

      // Multiple concurrent invocations to show shared state
      System.out.println("\n--- Initiating concurrent invocations ---");
      AgentInvoke invoke1 = agent.initiate("Hello");
      AgentInvoke invoke2 = agent.initiate("World");
      AgentInvoke invoke3 = agent.initiate("Again");

      System.out.println("Getting results...");
      String result1 = (String) agent.result(invoke1);
      String result2 = (String) agent.result(invoke2);
      String result3 = (String) agent.result(invoke3);

      System.out.println("Result 1: " + result1);
      System.out.println("Result 2: " + result2);
      System.out.println("Result 3: " + result3);

      System.out.println("\nEach message includes version and send-to from static objects");
      System.out.println("and the counter is always #1 -> alerts as the service is reset");
      System.out.println("at the start of each invocation,");
      System.out.println("and message-service instances are not shared.");
    }
  }
}
