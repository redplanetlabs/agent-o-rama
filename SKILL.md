# agent-o-rama

> Red Planet Labs LLM Agent Platform with Rama-powered storage, tracing, and deployment

**Version**: 0.7.0  
**Trit**: +1 (PLUS - generative agent orchestration)  
**Platform**: [Rama](https://redplanetlabs.com/) distributed systems  
**APIs**: Java, Clojure (feature parity)

## Overview

Agent-o-rama is an end-to-end LLM agent platform for building, tracing, testing, and monitoring agents with integrated storage and one-click deployment. Built on Rama's distributed dataflow substrate, it provides 100x developer productivity for backend systems with first-class observability.

## Core Capabilities

### 1. Agent Definition (Graph of Functions)

```clojure
(aor/defagentmodule MyAgentModule [topology]
  (aor/declare-agent-object topology "model" (build-model))
  (-> topology
      (aor/new-agent "my-agent")
      (aor/node "start" nil
        (fn [node prompt]
          (aor/result! node (invoke-model node prompt))))))
```

```java
public class MyAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("my-agent")
            .node("start", null, (node, prompt) -> {
              node.result(invokeModel(node, prompt));
            });
  }
}
```

### 2. Tracing & Observability

- Full trace of every agent invoke
- Node timings, model calls, token counts
- Database read/write latencies
- Tool calls and subagent invokes
- Fork any invoke to replay with modified inputs

### 3. Datasets & Experiments

```clojure
;; Run experiment on dataset
(aor/run-experiment agent-manager
  {:dataset-id dataset-id
   :evaluators ["accuracy" "latency"]
   :targets [{:agent "my-agent" :params {:model "gpt-4o"}}
             {:agent "my-agent" :params {:model "claude-3"}}]})
```

### 4. Online Actions & Telemetry

- Automatic time-series telemetry
- Online evaluation on production runs
- Webhook triggers and dataset augmentation
- Metadata-based filtering and splitting

### 5. Human-in-the-Loop

```clojure
(let [human-input (aor/get-human-input node "Please review this response:")]
  (aor/result! node (incorporate-feedback response human-input)))
```

### 6. Streaming

```clojure
(aor/agent-stream client invoke "output-node"
  (fn [all-chunks new-chunks reset? complete?]
    (println "Streaming:" new-chunks)))
```

## GF(3) Skill Tensor Integration

Agent-o-rama participates in the 3×3×3 skill tensor:

| Layer | Trit | Skills | Role |
|-------|------|--------|------|
| Infrastructure | -1 | rama-gay-clojure, duckdb-temporal-versioning | Storage substrate |
| Orchestration | 0 | bisimulation-game, world-hopping | Trajectory navigation |
| Generation | +1 | **agent-o-rama**, cognitive-superposition | Agent synthesis |

### Required Skill Tensor (3×3)

```
Alpha:  acsets × epistemic-arbitrage × glass-bead-game
Beta:   world-hopping × bisimulation-game × gflownet  
Gamma:  unworld × triad-interleave × cognitive-superposition
```

## Commands

```bash
# Development (IPC mode)
lein with-profile +dev run -m user

# Run tests
lein test

# Build for deployment
lein uberjar

# Deploy to Rama cluster
rama deploy --action launch \
  --jar target/agent-o-rama-0.7.0.jar \
  --module com.rpl.agent-o-rama.MyModule \
  --tasks 32 --threads 8 --workers 4

# Scale
rama scaleExecutors --module MyModule --threads 16 --workers 8
```

## Configuration

```clojure
;; project.clj dependencies
[com.rpl/rama "1.2.0"]
[com.rpl/agent-o-rama "0.7.0"]
[dev.langchain4j/langchain4j "1.8.0"]
[dev.langchain4j/langchain4j-open-ai "1.8.0"]
```

## Integration with Skill Ecosystem

Agent-o-rama agents can invoke any skill in the tensor:

```clojure
(defn agent-with-skills [node input]
  ;; Use epistemic-arbitrage for knowledge gaps
  (let [knowledge-delta (epistemic-arbitrage/find-gaps input)]
    ;; World-hop to closest relevant state
    (world-hopping/navigate-to knowledge-delta)
    ;; Generate via cognitive-superposition collapse
    (cognitive-superposition/collapse-to-response input)))
```

## Web UI

Accessible at `http://localhost:1974` when running:

```clojure
(with-open [ipc (rtest/create-ipc)
            ui (aor/start-ui ipc)]
  ;; UI now available
  )
```

## Related Skills

- `rama-gay-clojure` - Gay.jl 3-coloring for Rama dataflow
- `cognitive-surrogate` - Consumes agent patterns
- `self-validation-loop` - Validates agent outputs
- `duckdb-temporal-versioning` - Time-travel queries for traces
- `bisimulation-game` - Agent behavioral equivalence
- `gflownet` - Reward-proportional trajectory sampling

## Resources

- [Quickstart](https://github.com/redplanetlabs/agent-o-rama/wiki/Quickstart)
- [Full Documentation](https://github.com/redplanetlabs/agent-o-rama/wiki)
- [Javadoc](https://redplanetlabs.com/aor/javadoc/index.html)
- [Clojuredoc](https://redplanetlabs.com/aor/clojuredoc/index.html)
- [Discord](https://discord.gg/RX6UgQNR)

---

**Skill Name**: agent-o-rama  
**Type**: LLM Agent Platform / Distributed Orchestration  
**Trit**: +1 (PLUS)  
**GF(3)**: Conserved via triad integration  
**Platform**: Rama distributed dataflow
