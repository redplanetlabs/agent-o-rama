# Streaming in Agent-o-rama

Streaming enables agents to provide real-time feedback to users as they process requests, rather than waiting for the entire invoke to complete. This is useful for creating responsive applications, especially when working with LLMs that can take seconds or minutes to generate complete responses.

## Table of Contents

1. [Manual Streaming](#manual-streaming)
2. [Automatically Streamed Models](#automatically-streamed-models)
3. [Consuming Streams from Agent Clients](#consuming-streams-from-agent-clients)
4. [Streaming Analytics](#streaming-analytics)

## Manual Streaming

You can manually stream data from any agent node using the `streamChunk` method on `AgentNode`. This is useful for providing progress updates, incremental results, or any real-time feedback during agent execution.

### Java API

```java
import com.rpl.agentorama.*;

public class StreamingAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("StreamingAgent")
            .node("process", null, (AgentNode agentNode, Integer numChunks) -> {
              // Stream chunks one at a time
              for (int i = 0; i < numChunks; i++) {
                Thread.sleep(100); // Simulate work
                agentNode.streamChunk("chunk" + i);
              }
              // Return final result
              agentNode.result("done");
            });
  }
}
```

### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor])

(aor/defagentmodule StreamingAgentModule
  [topology]
  (-> (aor/new-agent topology "StreamingAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node num-chunks]
         ;; Stream chunks one at a time
         (doseq [i (range num-chunks)]
           (Thread/sleep 100) ; Simulate work
           (aor/stream-chunk! agent-node (str "chunk" i)))
         ;; Return final result
         (aor/result! agent-node "done")))))
```

### Key Points

- **streamChunk** can be called any number of times from a node
- Chunks can contain any serializable data
- Streaming happens in real-time as the agent executes

## Automatic Streaming Models

When you declare a `StreamingChatModel` from LangChain4j as an agent object, Agent-o-rama automatically captures the streaming tokens and forwards them as chunks. This means you get real-time streaming of LLM responses without any manual `streamChunk` calls.

### How It Works

1. **Declare the model as StreamingChatModel** in your topology
2. **Fetch it as ChatModel** in your node (Agent-o-rama wraps it automatically)
3. **Use it normally** – streaming happens automatically
4. **Clients receive tokens** in real-time as the model generates them

### Java API

```java
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.ChatModel;

public class StreamingLLMModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    // Declare API key as static agent object
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

    // Declare as StreamingChatModel
    topology.declareAgentObjectBuilder("openai-model", setup -> {
      String apiKey = setup.getAgentObject("openai-api-key");
      return OpenAiStreamingChatModel.builder()
                                     .apiKey(apiKey)
                                     .modelName("gpt-4")
                                     .build();
    });

    topology.newAgent("StreamingLLMAgent")
            .node("chat", null, (AgentNode agentNode, String userMessage) -> {
              // Fetch as ChatModel (not StreamingChatModel!)
              ChatModel model = agentNode.getAgentObject("openai-model");

              // Use it normally – streaming happens automatically
              String response = model.chat(userMessage);

              // Return final complete response
              agentNode.result(response);
            });
  }
}
```

### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor])
(import '[dev.langchain4j.model.openai OpenAiStreamingChatModel])

(aor/defagentmodule StreamingLLMModule
  [topology]
  ;; Declare API key as static agent object
  (aor/declare-agent-object topology "openai-api-key" (System/getenv "OPENAI_API_KEY"))

  ;; Declare as StreamingChatModel
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4")
         .build)))

  (-> (aor/new-agent topology "StreamingLLMAgent")
      (aor/node
       "chat"
       nil
       (fn [agent-node user-message]
         ;; Fetch as ChatModel (not StreamingChatModel!)
         (let [model (aor/get-agent-object agent-node "openai-model")]
           ;; Use it normally - streaming happens automatically
           (let [response (aor/chat model user-message)]
             ;; Return final complete response
             (aor/result! agent-node response)))))))
```

## Consuming Streams from Agent Clients

Agent clients can subscribe to streaming data in two ways: `stream` for the first invocation of a node, or `streamAll` for all invocations of a node.

Use `stream` to subscribe to chunks from the first time a specific node is invoked during an agent execution. This is the most common case – if your node is only called once, or you only care about the first call, use `stream`.

The `stream` method can be called with or without a callback. The callback is invoked each time new chunks arrive.

Either way, the returned stream object can be queried at any time:
- **Java**: Call `.get()` to get the current list of chunks
- **Clojure**: Call `deref` or `@` to get the current list of chunks

The callback receives four arguments:
1. **allChunks**: Complete list of all chunks received so far from this node invocation
2. **newChunks**: List of only the newly received chunks since the last callback
3. **reset**: Boolean indicating the node failed and retried, resetting the chunks to empty when it restarted
4. **complete**: Boolean indicating the node execution has finished and there will be no more streaming chunks

#### Java API

```java
import com.rpl.agentorama.*;
import java.util.List;

public class StreamingConsumer {
  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch module and get agent client
      StreamingAgentModule module = new StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(4, 2));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      // Start async agent execution
      AgentInvoke invoke = agent.initiate(5);

      // Option 1: Subscribe with callback
      AgentStream stream = agent.stream(
        invoke,
        "process",  // Node name to stream from
        (List allChunks, List newChunks, boolean reset, boolean complete) -> {
          for (Object chunk : newChunks) {
            System.out.println("New chunk: " + chunk);
          }
          if (reset) {
            System.out.println("Stream reset due to node retry");
          }
          if (complete) {
            System.out.println("Streaming complete!");
          }
        });

      // Option 2: Poll without callback
      AgentStream stream2 = agent.stream(invoke, "process");
      List currentChunks = stream2.get();  // Get current chunks at any time

      // Wait for final result
      String result = agent.result(invoke);
      System.out.println("Final result: " + result);
    }
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor]
         '[com.rpl.rama.test :as rtest])

(with-open [ipc (rtest/create-ipc)]
  ;; Launch module and get agent client
  (rtest/launch-module! ipc StreamingAgentModule {:tasks 4 :threads 2})
  (let [manager (aor/agent-manager ipc (rama/get-module-name StreamingAgentModule))
        agent (aor/agent-client manager "StreamingAgent")]

    ;; Start async agent execution
    (let [invoke (aor/agent-initiate agent 5)]

      ;; Option 1: Subscribe with callback
      (let [stream (aor/agent-stream
                    agent
                    invoke
                    "process"  ; Node name to stream from
                    (fn [all-chunks new-chunks reset? complete?]
                      (doseq [chunk new-chunks]
                        (println "New chunk:" chunk))
                      (when reset?
                        (println "Stream reset due to node retry"))
                      (when complete?
                        (println "Streaming complete!"))))]

        ;; Option 2: Poll without callback
        (let [stream2 (aor/agent-stream agent invoke "process")
              current-chunks @stream2]  ; Deref to get current chunks at any time
          (println "Current chunks:" current-chunks))

        ;; Wait for final result
        (let [result (aor/agent-result agent invoke)]
          (println "Final result:" result))))))
```

### Streaming from All Node Invocations

Use `streamAll` to subscribe to chunks from all invocations of a specific node. This is useful when a node is called multiple times (e.g. in parallel within an aggregation subgraph) and you want to track all of them.

Like `stream`, `streamAll` can be called with or without a callback. The key difference is that `streamAll` tracks multiple node invocations:

The returned stream object can be queried at any time:
- **Java**: Call `.get()` to get a map from node invoke ID to list of chunks
- **Clojure**: Call `deref` or `@` to get a map from node invoke ID to list of chunks

The callback receives four arguments, but with different types than `stream`:
1. **invokeIdToAllChunks**: Map from node invoke ID to complete list of all chunks for that invocation
2. **invokeIdToNewChunks**: Map from node invoke ID to newly received chunks for that invocation
3. **resetInvokeIds**: Set of node invoke IDs that were reset due to retry since the last callback
4. **complete**: Boolean indicating the agent has finished and thus all possible invokes of this node are complete

#### Java API

```java
import com.rpl.agentorama.*;
import java.util.*;

public class StreamAllConsumer {
  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ParallelAgent");

      // Start agent execution that will invoke a node multiple times
      AgentInvoke invoke = agent.initiate(List.of(1, 2, 3, 4, 5));

      // Option 1: Subscribe with callback
      AgentStreamByInvoke stream = agent.streamAll(
        invoke,
        "process-item",
        (Map<UUID, List> invokeIdToAllChunks,
         Map<UUID, List> invokeIdToNewChunks,
         Set<UUID> resetInvokeIds,
         boolean complete) -> {
          // Process new chunks for each node invocation
          for (Map.Entry<UUID, List> entry : invokeIdToNewChunks.entrySet()) {
            UUID nodeInvokeId = entry.getKey();
            List newChunks = entry.getValue();

            for (Object chunk : newChunks) {
              System.out.printf("Node invoke %s: %s\n", nodeInvokeId, chunk);
            }
          }
          if (!resetInvokeIds.isEmpty()) {
            System.out.println("Some node invocations were reset: " + resetInvokeIds);
          }
          if (complete) {
            System.out.println("All node invocations complete!");
          }
        });

      // Option 2: Poll without callback
      AgentStreamByInvoke stream2 = agent.streamAll(invoke, "process-item");
      Map<UUID, List> currentChunks = stream2.get();  // Get map of all chunks at any time

      // Wait for final result
      Object result = agent.result(invoke);
      System.out.println("Final result: " + result);
    }
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor])

(let [agent (aor/agent-client manager "ParallelAgent")
      ;; Start agent execution that will invoke a node multiple times
      invoke (aor/agent-initiate agent [1 2 3 4 5])]

  ;; Option 1: Subscribe with callback
  (let [stream (aor/agent-stream-all
                agent
                invoke
                "process-item"
                (fn [invoke-id->all-chunks invoke-id->new-chunks reset-invoke-ids complete?]
                  ;; Process new chunks for each node invocation
                  (doseq [[node-invoke-id new-chunks] invoke-id->new-chunks]
                    (doseq [chunk new-chunks]
                      (println (format "Node invoke %s: %s" node-invoke-id chunk))))
                  (when (seq reset-invoke-ids)
                    (println "Some node invocations were reset:" reset-invoke-ids))
                  (when complete?
                    (println "All node invocations complete!"))))]

    ;; Option 2: Poll without callback
    (let [stream2 (aor/agent-stream-all agent invoke "process-item")
          current-chunks @stream2]  ; Deref to get map of all chunks at any time
      (println "Current chunks by invoke ID:" current-chunks))

    ;; Wait for final result
    (let [result (aor/agent-result agent invoke)]
      (println "Final result:" result))))
```

## Streaming Analytics

Agent-o-rama automatically tracks streaming performance metrics that are displayed in the web UI. These metrics help you understand the responsiveness of your agents.

### Time to First Token Metrics

Two key metrics are tracked for streaming:

#### 1. Time to First Token (Agent)

Measures the time from when the agent invocation starts until the first chunk is streamed to the client. This includes:
- Agent initialization time
- Time to reach the first `streamChunk` call
- Any processing before streaming begins

This metric tells you how quickly your agent starts providing feedback to users.

#### 2. Time to First Token (Model)

This measures the time from when the model call starts until the first token is received from the LLM. This is tracked automatically for all `StreamingChatModel` calls.

This metric tells you how quickly the underlying LLM starts generating responses.

### Viewing Streaming Analytics

Streaming metrics are available in the Agent-o-rama web UI on the analytics section for the agent. Here's an example of what it looks like:

TODO
