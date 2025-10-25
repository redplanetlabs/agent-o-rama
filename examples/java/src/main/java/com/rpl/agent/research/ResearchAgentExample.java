package com.rpl.agent.research;

import com.rpl.agentorama.*;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

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
         BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
      try (AutoCloseable ui = UI.start(ipc)) {
        ResearchAgentModule module = new ResearchAgentModule();
        ipc.launchModule(module, new LaunchConfig(4, 2));

        String moduleName = module.getModuleName();
        AgentManager manager = AgentManager.create(ipc, moduleName);
        AgentClient researcher = manager.getAgentClient("researcher");

        System.out.print("Enter a topic: ");
        System.out.flush();
        String topic = reader.readLine();
        System.out.println();

        Map<String, Object> input = new HashMap<>();
        input.put("topic", topic);
        AgentInvoke invoke = researcher.initiate("", input);

        Object step = researcher.nextStep(invoke);
        String finalResult = null;
        while (step != null) {
          if (step instanceof HumanInputRequest) {
            HumanInputRequest request = (HumanInputRequest) step;
            System.out.println(request.getPrompt());
            System.out.print(">> ");
            System.out.flush();

            String response = reader.readLine();
            researcher.provideHumanInput(request, response);
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
}
