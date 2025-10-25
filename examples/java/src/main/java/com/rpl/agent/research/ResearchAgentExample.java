package com.rpl.agent.research;

import com.rpl.agentorama.*;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.*;

/**
 * Example demonstrating the Research Agent Module.
 *
 * This example shows how to use the ResearchAgentModule to conduct
 * multi-step research with analyst personas, web search, and report generation.
 */
public class ResearchAgentExample {

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Research Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create();
         AutoCloseable ui = UI.start(ipc)) {
      // Launch the research agent module
      ResearchAgentModule module = new ResearchAgentModule();
      ipc.launchModule(module, new LaunchConfig(4, 2));

      // Get the agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient researcher = manager.getAgentClient("researcher");

      System.out.println("Research Agent Example");
      System.out.println("=====================");
      System.out.println();

      // Example research topic
      String topic = "Artificial Intelligence in Healthcare";

      System.out.println("Research Topic: " + topic);
      System.out.println();


      Map input = new HashMap();
      input.put("topic", topic);
      // Initiate the research process
      AgentInvoke invoke = researcher.initiate("", input);

      // Process the research workflow
      Object step = researcher.nextStep(invoke);
      String finalResult = null;
      while (step != null) {
        if (step instanceof HumanInputRequest) {
          HumanInputRequest request = (HumanInputRequest) step;
          System.out.println("Human Input Request:");
          System.out.println(request.getPrompt());
          System.out.println();

          // Provide a simple response for demonstration
          String response = "no"; // Default response for analyst feedback
          researcher.provideHumanInput(request, response);
          System.out.println("Response: " + response);
          System.out.println();
        } else {
          System.out.println("Final Research Report:");
          System.out.println("====================");
          System.out.println(((AgentComplete) step).getResult());
          break;
        }

        step = researcher.nextStep(invoke);
      }
    }
  }
}
