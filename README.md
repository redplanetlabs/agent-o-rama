# Agent-o-rama

Agent-o-rama is a library for building scalable and stateful AI agents in Java or Clojure. Agents are defined as simple graphs of pure Java or Clojure functions, and Agent-o-rama automatically captures detailed traces and provides facilities and a web UI for offline experimentation, online evaluation, time-series telemetry (e.g. latencies, token usage), and much more. Agent-o-rama is heavily inspired by [LangGraph](https://www.langchain.com/langgraph) and [LangSmith](https://smith.langchain.com/).


TODO: image gallery
 - trace
 - telemetry
 - datasets
 - experiment results
 - forking UI

LLMs are powerful but inherently unpredictable, so building applications with LLMS that are helpful and performant with minimal hallucination requires being rigorous about testing and monitoring. Agent-o-rama addresses this by making evaluation and observability a first-class part of the development process, not an afterthought.

Agent-o-rama is deployed onto your own infrastructure and is free to use. Agent-o-rama applications are deployed on [Rama](https://redplanetlabs.com/) clusters, which is also free to use. Every part of Agent-o-rama is built-in and requires no other dependency besides Rama, including high-performance built-in storage of any data model that can be used as part of agents. Agent-o-rama also integrates seamlessly with any other tool, such as databases, vector stores, external APIs, or anything else.

Rama can be downloaded [here](https://redplanetlabs.com/download), and instructions for setting up a cluster are [here](https://redplanetlabs.com/docs/~/operating-rama.html#_setting_up_a_rama_cluster). A cluster can be [as small as one node](https://redplanetlabs.com/docs/~/operating-rama.html#_running_single_node_cluster) or as big as thousands of nodes. There's also one-click deploys [for AWS](https://github.com/redplanetlabs/rama-aws-deploy) and [for Azure](https://github.com/redplanetlabs/rama-azure-deploy).

Development of Agent-o-rama applications is done with "in-process cluster" (IPC), which simulates Rama clusters in a single process. IPC is great for unit testing or experimentation at a REPL. Here's an example of defining and running an agent with IPC (for both Java and Clojure):

### Java Example

```java
public BasicAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));
    topology.declareAgentObjectBuilder(
      "openai-model",
      setup -> {
        String apiKey = setup.getAgentObject("openai-api-key");
        return OpenAiStreamingChatModel.builder()
            .apiKey(apiKey)
            .modelName("gpt-4o-mini")
            .build();
      });
    topology.newAgent("basic-agent")
            .node("chat",
                  null,
                  (AgentNode node, String prompt) -> {
                    ChatModel model = node.getAgentObject("openai-model");
                    node.result(model.chat(prompt));
                  });
  }
}

try (InProcessCluster ipc = InProcessCluster.create();
     Object ui = UI.start(ipc)) {
  BasicAgentModule module = new BasicAgentModule();
  ipc.launchModule(module, new LaunchConfig(1, 1));
  String moduleName = module.getModuleName();
  AgentManager manager = AgentManager.create(ipc, moduleName);
  AgentClient agent = manager.getAgentClient("basic-agent");

  String result = agent.invoke("What are use cases for AI agents?");
  System.out.println("Result: " + result);
}
```

### Clojure Example

```clojure
(aor/defagentmodule BasicAgentModule
  [topology]
  (aor/declare-agent-object topology "openai-api-key" (System/getenv "OPENAI_API_KEY"))
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (-> topology
      (aor/new-agent "basic-agent")
      (aor/node
       "start"
       nil
       (fn [agent-node prompt]
         (let [openai (aor/get-agent-object agent-node "openai-model")]
           (aor/result! agent-node (lc4j/basic-chat openai prompt))
         )))))

(with-open [ipc (rtest/create-ipc)
            ui (aor/start-ui ipc)]
  (rtest/launch-module! ipc BasicAgentModule {:tasks 4 :threads 2})
  (let [module-name (rama/get-module-name BasicAgentModule)
        agent-manager (aor/agent-manager ipc module-name)
        agent (aor/agent-client agent-manager "basic-agent")]
    (println "Result:" (aor/agent-invoke agent "What are use cases for AI agents?"))
    ))
```

This also launches the Agent-o-rama UI locally at `http://localhost:1974`.

The following are the key similarities and differences between Agent-o-rama and LangGraph/LangSmith:

### Key Similarities with LangGraph/LangSmith

- **Graph-based agent definitions:**  
  Agents are defined as explicit graphs of regular Java or Clojure functions, with named nodes and edges, similar in spirit to LangGraph's approach to structured agent workflows.

- **Structured execution traces:**  
  Every agent invocation is captured as a trace with detailed stats on every aspect of execution including latency, token usage, model calls, tool invocations, and database calls.

- **Streaming at the node level:**  
  Nodes can emit intermediate chunks before completing, allowing fine-grained, real-time streaming. LLM calls are automatically streamed, and the AOR API includes methods to explicitly stream chunks from a node. A first-class client API can register a callbacks to receive all chunks from a node.

- **Forking and versioning:**  
  Any agent or node can be forked and modified independently, useful for testing prompt or logic variations without disrupting production agents.

- **Datasets and snapshots:**  
  Inputs and outputs can be captured into versioned datasets, making it easy to replay examples and benchmark changes.

- **Experiments (agent-wide or per-node):**  
  Test entire agents or individual nodes (e.g., a new prompt or model) against datasets. Results are evaluated using any number of user-defined evaluators, whether custom functions or using LLMs to score.

- **Online evaluation and actions:**
  Actions are user-defined hooks that run on a sampled subset of live agent or node executions. They can be used for real-time evaluation, dataset capture, triggering webhooks, or any custom logic. Actions can filter on conditions like latency, token usage, errors, or input/output content.

- **Human input integration:**  
  Agents can pause mid-execution to request structured human input, then resume once the input is received.

- **Telemetry:**  
  Detailed, real-time time-series metrics across all agents—including invocation rates, latencies, model and token usage, database access, custom evaluator metrics, and more.

### Key Differences with LangGraph/LangSmith

- **JVM, not Python:**
  AOR is a platform for developing agents on the JVM in Java or Clojure.

- **Distributed, parallel execution model:**  
  AOR agents are compiled to distributed, parallel execution graphs with no central state or coordinator. Each node runs independently, and emit targets are processed concurrently across threads and machines.

- **Built-in, high-performance store:**  
  Built-in, high-performance storage (document stores, KV stores, or any other data model) eliminates the need for external databases in most cases.

- **First-class human input:**  
  Human input is a first-class API rather than based on using exceptions for break points.

- **Unified infrastructure:**  
  Everything is deployed onto your own infrastructure via [Rama](https://redplanetlabs.com/), and there are no hosted services. The full system runs locally, in the cloud, or across clusters with no dependency on external SaaS platforms.


## Tour of Agent-o-rama
