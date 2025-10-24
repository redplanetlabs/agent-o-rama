
### Streaming Data

Agents can stream data to clients in real-time, enabling progressive results and better user experience for long-running operations.

#### Java API

```java
public class StreamingAgentModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.newAgent("StreamingAgent")
            .node("process", null, (AgentNode agentNode, String input) -> {
                String[] words = input.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    agentNode.streamChunk(Map.of("word", words[i], "index", i, "total", words.length));
                }
                agentNode.result(Map.of("total-words", words.length, "status", "complete"));
            });
    }
}
```

#### Clojure API

```clojure
(aor/defagentmodule StreamingAgentModule
  [topology]
  (-> (aor/new-agent topology "StreamingAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [words (str/split input #"\\s+")]
           (doseq [[word index] (map-indexed vector words)]
             (aor/stream-chunk! agent-node {:word word :index index :total (count words)}))
           (aor/result! agent-node {:total-words (count words) :status "complete"}))))))
```
