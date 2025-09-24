# Provided Evaluator Builders

Built-in evaluator builder functions available in agent-o-rama for common evaluation tasks, providing ready-to-use assessment capabilities for agent performance measurement.

## Purpose

Evaluator builders solve fundamental challenges in AI agent assessment:

- **Standardized Metrics**: Provide common evaluation patterns (F1-score, conciseness, LLM judging) without custom implementation
- **Configurable Assessment**: Enable parameterized evaluation through builder patterns with customizable thresholds and models
- **Multi-Modal Evaluation**: Support different evaluation types (regular, comparative, summary) for diverse assessment needs
- **Production-Ready Quality**: Offer battle-tested evaluation logic for real-world agent performance monitoring

## Available Builders

### aor/llm-judge
AI-powered evaluation using large language models to assess agent output quality.

**Parameters:**
- `prompt` - Evaluation prompt template with %input, %output, %referenceOutput variables (default: comprehensive quality assessment)
- `model` - Agent object name for the LLM model to use for judging
- `temperature` - LLM temperature setting for evaluation consistency (default: "0.0")
- `outputSchema` - JSON schema defining evaluation output structure (default: score 0-10)

**Usage:**
```clojure
(aor/create-evaluator! manager "quality-judge" "aor/llm-judge"
  {"model" "gpt-4o"
   "temperature" "0.2"
   "prompt" "Rate the helpfulness of this response..."})
```

### aor/conciseness
Boolean evaluator assessing whether outputs meet length constraints.

**Parameters:**
- `threshold` - Maximum character count for output to be considered concise (default: "300")

**Features:**
- Works with strings and LangChain4j message types
- Calculates UserMessage length as sum of TextContent lengths
- Ignores non-text content in message evaluation

**Usage:**
```clojure
(aor/create-evaluator! manager "brief-check" "aor/conciseness"
  {"threshold" "150"})
```

### aor/f1-score
Classification metrics calculator providing F1-score, precision, and recall for binary classification tasks.

**Parameters:**
- `positiveValue` - Value considered a positive classification for metric calculation

**Output:**
- `score` - F1-score (harmonic mean of precision and recall)
- `precision` - True positives / (true positives + false positives)
- `recall` - True positives / (true positives + false negatives)

**Usage:**
```clojure
(aor/create-evaluator! manager "sentiment-f1" "aor/f1-score"
  {"positiveValue" "positive"})
```

## Integration

### Builder System
Evaluator builders implement a functional factory pattern where configuration parameters produce evaluation functions that process agent inputs and outputs.

### Manager-Level Creation
Evaluators are created at the agent manager level, separate from agent execution, enabling centralized evaluation management:

```clojure
(let [manager (aor/agent-manager ipc module-name)]
  (aor/create-evaluator! manager "eval-name" "builder-name" params description))
```

### Evaluation Types
- **Regular**: Single input/output pair evaluation via `aor/try-evaluator`
- **Comparative**: Multiple output ranking via `aor/try-comparative-evaluator`
- **Summary**: Dataset-wide aggregation via `aor/try-summary-evaluator`

## Extension

Custom evaluator builders can be declared alongside provided ones using:
- `aor/declare-evaluator-builder` - Regular evaluation functions
- `aor/declare-comparative-evaluator-builder` - Output comparison functions
- `aor/declare-summary-evaluator-builder` - Cross-example aggregation functions

The provided builders serve as reference implementations and cover the most common evaluation scenarios in agent-o-rama deployments.

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/provided_evaluator_builders_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/ProvidedEvaluatorBuildersAgent.java`
