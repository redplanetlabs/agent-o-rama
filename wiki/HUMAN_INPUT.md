
## Human Input

### Human Input Integration

Agents can request human input during execution, enabling human-in-the-loop patterns for tasks that require human judgment or approval.

#### Java API

```java
public class HumanInputAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("HumanInputAgent")
            .node("process", null, (AgentNode agentNode, String input) -> {
              String humanResponse = agentNode.getHumanInput("Please review: " + input + "\nIs this correct? (y/n): ");

              if ("y".equals(humanResponse)) {
                  agentNode.result("Human approved: " + input);
              } else {
                  agentNode.result("Human rejected: " + input);
              }
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule HumanInputAgentModule
  [topology]
  (-> (aor/new-agent topology "HumanInputAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [human-response (aor/get-human-input agent-node
                                                   (str "Please review: " input "\nIs this correct? (y/n): "))]
           (if (= "y" human-response)
             (aor/result! agent-node (str "Human approved: " input))
             (aor/result! agent-node (str "Human rejected: " input))))))))
```
