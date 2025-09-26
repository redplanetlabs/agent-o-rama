# Evaluator Builder

## Definition
In topology provider with code for building evaluators from a
client. Functions that create evaluator instances for measuring agent
performance against datasets.

## Architecture Role
Provides the infrastructure for dynamic evaluator creation within the
agent topology, enabling clients to register and instantiate custom
evaluation logic for experiments.

## Operations
Declaration of evaluator builder functions in topology, registration of
builder metadata and parameters, runtime instantiation of evaluators
from client requests.

## Invariants
Evaluator builders are declared once per topology and remain available
for the lifetime of the topology. Builder functions must return valid
evaluator instances.

## Key Clojure API
- Primary functions: `declare-evaluator-builder`,
  `declare-comparative-evaluator-builder`,
  `declare-summary-evaluator-builder`
- Creation: `declare-evaluator-builder` with name, description, and
  builder function
- Access: Via `create-evaluator!` and evaluation operations

## Key Java API
- Primary functions: `declareEvaluatorBuilder`,
  `declareComparativeEvaluatorBuilder`, `declareSummaryEvaluatorBuilder`
- Creation: AgentTopology builder methods with EvaluatorBuilder
  interface and `EvaluatorBuilderOptions` options
- Access: Via AgentManager evaluator creation methods

## Relationships
- Uses: [agent-topology], [evaluator], [experiment]
- Used by: [provided-evaluator-builders], [evaluation], [dataset]

## Dependency graph edges:
    agent-topology -> evaluator-builder
    evaluator -> evaluator-builder
    experiment -> evaluator-builder
    evaluator-builder -> provided-evaluator-builders
    evaluator-builder -> evaluation
    evaluator-builder -> dataset

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/evaluator_builder_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/EvaluatorBuilderAgent.java`
