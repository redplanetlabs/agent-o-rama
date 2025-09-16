# System Features

Take control of your [agent](../terms/agent.md) systems. [Fork](../glossary.md#fork) executions for exploration. Build [datasets](../terms/dataset.md) for testing. [Evaluate](../glossary.md#evaluators) performance systematically.

> **Reference**: See [Dataset](../terms/dataset.md) and [Experiment](../terms/experiment.md) documentation for comprehensive evaluation details.

## Forking: Branching Execution Paths

[Forking](../glossary.md#fork) creates new execution branches from existing [agent invocations](../glossary.md#agent-invoke) with modified parameters. Explore alternatives, test variations, or implement speculative execution from any point in [agent graph](../glossary.md#agent-graph) execution.

### Basic Forking

Create alternate execution paths by [forking](../glossary.md#fork) from existing [agent invokes](../glossary.md#agent-invoke) to explore different parameter sets or strategies:

**Clojure:**
```clojure
(aor/defagentmodule ExplorationModule
  [topology]
  
  (-> topology
      (aor/new-agent "Explorer")
      
      ;; Initial analysis
      (aor/node "analyze" "explore-options"
                (fn [agent-node data]
                  (let [initial-analysis (analyze data)]
                    (aor/emit! agent-node "explore-options" 
                              initial-analysis))))
      
      ;; Fork to explore different strategies
      (aor/node "explore-options" nil
                (fn [agent-node analysis]
                  ;; This node won't complete immediately
                  ;; Client will fork from here
                  (aor/stream-chunk! agent-node 
                    {:status "ready-to-fork"
                     :options ["aggressive" "conservative" "balanced"]
                     :analysis analysis})))))

;; Client-side forking
(let [client (aor/agent-client manager "Explorer")
      ;; Start initial execution
      main-invoke (aor/agent-initiate client data)]
  
  ;; Wait for fork point
  (Thread/sleep 100)
  
  ;; Fork into multiple strategies
  (let [aggressive-fork (aor/agent-fork main-invoke 
                          {:strategy "aggressive"})
        conservative-fork (aor/agent-fork main-invoke
                            {:strategy "conservative"})
        balanced-fork (aor/agent-fork main-invoke
                         {:strategy "balanced"})]
    
    ;; Get results from all forks
    {:aggressive (aor/agent-result aggressive-fork)
     :conservative (aor/agent-result conservative-fork)
     :balanced (aor/agent-result balanced-fork)}))
```

**Java:**
```java
public class ExplorationModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("Explorer")
            
            // Initial analysis
            .node("analyze", "explore-options", (agentNode, data) -> {
                Map<String, Object> initialAnalysis = analyze(data);
                agentNode.emit("explore-options", initialAnalysis);
            })
            
            // Fork to explore different strategies
            .node("explore-options", null, (agentNode, analysis) -> {
                // This node won't complete immediately
                // Client will fork from here
                agentNode.streamChunk(Map.of(
                    "status", "ready-to-fork",
                    "options", List.of("aggressive", "conservative", "balanced"),
                    "analysis", analysis
                ));
            });
    }
}

// Client-side forking
AgentClient client = manager.agentClient("Explorer");
// Start initial execution
AgentInvoke mainInvoke = client.initiate(data);

// Wait for fork point
Thread.sleep(100);

// Fork into multiple strategies
AgentInvoke aggressiveFork = mainInvoke.fork(
    Map.of("strategy", "aggressive")
);
AgentInvoke conservativeFork = mainInvoke.fork(
    Map.of("strategy", "conservative")
);
AgentInvoke balancedFork = mainInvoke.fork(
    Map.of("strategy", "balanced")
);

// Get results from all forks
Map<String, Object> results = Map.of(
    "aggressive", aggressiveFork.result(),
    "conservative", conservativeFork.result(),
    "balanced", balancedFork.result()
);
```

### Advanced Forking Patterns

[Fork](../glossary.md#fork) recursively from [agent invocations](../glossary.md#agent-invoke) for tree exploration and decision analysis with nested execution branches:

**Clojure:**
```clojure
(aor/defagentmodule DecisionTreeModule
  [topology]
  
  (-> topology
      (aor/new-agent "DecisionTree")
      
      ;; Evaluate current position
      (aor/router-node "evaluate"
                       (fn [agent-node state depth]
                         (cond
                           ;; Reached max depth
                           (>= depth 3)
                           (aor/result! agent-node 
                             {:score (score-position state)})
                           
                           ;; Terminal state
                           (terminal? state)
                           (aor/result! agent-node
                             {:score (final-score state)})
                           
                           ;; Continue exploring
                           :else
                           (let [moves (generate-moves state)]
                             (aor/stream-chunk! agent-node
                               {:fork-point true
                                :moves moves
                                :depth depth})
                             ;; Wait for fork
                             (Thread/sleep Long/MAX_VALUE)))))))

;; Recursive forking client
(defn explore-tree [invoke depth]
  (if (>= depth 3)
    (aor/agent-result invoke)
    (let [;; Get available moves
          stream-data (first (aor/get-stream-chunks invoke))
          moves (:moves stream-data)]
      ;; Fork for each move
      (for [move moves]
        (let [fork (aor/agent-fork invoke {:move move :depth (inc depth)})]
          {:move move
           :result (explore-tree fork (inc depth))})))))
```

**Java:**
```java
public class DecisionTreeModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("DecisionTree")
            
            // Evaluate current position
            .routerNode("evaluate", (agentNode, state, depth) -> {
                int d = (int) depth;
                
                if (d >= 3) {
                    // Reached max depth
                    agentNode.result(Map.of("score", scorePosition(state)));
                } else if (isTerminal(state)) {
                    // Terminal state
                    agentNode.result(Map.of("score", finalScore(state)));
                } else {
                    // Continue exploring
                    List<Object> moves = generateMoves(state);
                    agentNode.streamChunk(Map.of(
                        "fork-point", true,
                        "moves", moves,
                        "depth", d
                    ));
                    // Wait for fork
                    Thread.sleep(Long.MAX_VALUE);
                }
            });
    }
}

// Recursive forking client
public List<Map<String, Object>> exploreTree(AgentInvoke invoke, int depth) {
    if (depth >= 3) {
        return List.of(invoke.result());
    }
    
    // Get available moves
    Map<String, Object> streamData = invoke.getStreamChunks().get(0);
    List<Object> moves = (List<Object>) streamData.get("moves");
    
    // Fork for each move
    return moves.stream()
        .map(move -> {
            AgentInvoke fork = invoke.fork(Map.of(
                "move", move,
                "depth", depth + 1
            ));
            return Map.of(
                "move", move,
                "result", exploreTree(fork, depth + 1)
            );
        })
        .collect(Collectors.toList());
}
```

## Datasets: Testing and Validation

[Datasets](../terms/dataset.md) are managed collections of input/output examples used for [agent](../terms/agent.md) testing, evaluation, and performance tracking. Create test cases, validate outputs, measure performance systematically.

### Creating Datasets

Build test suites for your [agents](../terms/agent.md) using [datasets](../terms/dataset.md) with structured examples for systematic evaluation:

**Clojure:**
```clojure
(aor/defagentmodule TestableModule
  [topology]
  
  (-> topology
      (aor/new-agent "Calculator")
      (aor/node "calculate" nil
                (fn [agent-node expression]
                  (let [result (evaluate-expression expression)]
                    (aor/result! agent-node result))))))

;; Create test dataset
(let [manager (aor/agent-manager cluster "TestableModule")
      ;; Create dataset
      dataset-id (aor/create-dataset manager "math-tests"
                   {:description "Basic math operations"
                    :version "1.0"})]
  
  ;; Add test cases
  (doseq [[input expected] [["2 + 2" 4]
                            ["10 * 5" 50]
                            ["100 / 4" 25]
                            ["7 - 3" 4]]]
    (aor/add-dataset-entry manager dataset-id
      {:input input
       :expected-output expected
       :tags ["arithmetic" "basic"]}))
  
  ;; Run tests
  (let [client (aor/agent-client manager "Calculator")]
    (for [entry (aor/get-dataset-entries manager dataset-id)]
      (let [result (aor/agent-invoke client (:input entry))]
        {:input (:input entry)
         :expected (:expected-output entry)
         :actual result
         :passed? (= result (:expected-output entry))}))))
```

**Java:**
```java
public class TestableModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("Calculator")
            .node("calculate", null, (agentNode, expression) -> {
                double result = evaluateExpression((String) expression);
                agentNode.result(result);
            });
    }
}

// Create test dataset
AgentManager manager = new AgentManager(cluster, "TestableModule");

// Create dataset
String datasetId = manager.createDataset("math-tests", Map.of(
    "description", "Basic math operations",
    "version", "1.0"
));

// Add test cases
List<List<Object>> testCases = List.of(
    List.of("2 + 2", 4.0),
    List.of("10 * 5", 50.0),
    List.of("100 / 4", 25.0),
    List.of("7 - 3", 4.0)
);

for (List<Object> testCase : testCases) {
    manager.addDatasetEntry(datasetId, Map.of(
        "input", testCase.get(0),
        "expected-output", testCase.get(1),
        "tags", List.of("arithmetic", "basic")
    ));
}

// Run tests
AgentClient client = manager.agentClient("Calculator");
List<Map<String, Object>> results = new ArrayList<>();

for (Map<String, Object> entry : manager.getDatasetEntries(datasetId)) {
    Object result = client.invoke(entry.get("input"));
    results.add(Map.of(
        "input", entry.get("input"),
        "expected", entry.get("expected-output"),
        "actual", result,
        "passed", result.equals(entry.get("expected-output"))
    ));
}
```

### Complex Dataset Scenarios

Test conversational [agents](../terms/agent.md) with multi-turn [datasets](../terms/dataset.md) for complex interaction scenarios and stateful conversations:

**Clojure:**
```clojure
(aor/defagentmodule ConversationalTestModule
  [topology]
  
  ;; Declare conversation store
  (aor/declare-pstate-store topology "conversations" String)
  
  (-> topology
      (aor/new-agent "Assistant")
      
      ;; Handle conversation
      (aor/node "chat" nil
                (fn [agent-node conv-id message]
                  (let [convs (aor/get-store agent-node "conversations")
                        history (or (store/get convs conv-id) [])
                        ;; Add message to history
                        updated (conj history {:user message})
                        ;; Generate response based on history
                        response (generate-response updated)]
                    ;; Update conversation
                    (store/put! convs conv-id 
                      (conj updated {:assistant response}))
                    (aor/result! agent-node response))))))

;; Multi-turn conversation dataset
(let [manager (aor/agent-manager cluster "ConversationalTestModule")
      dataset-id (aor/create-dataset manager "conversations"
                   {:type "multi-turn"})]
  
  ;; Add conversation scenarios
  (aor/add-dataset-entry manager dataset-id
    {:scenario "greeting-flow"
     :turns [{:input ["conv-1" "Hello!"]
              :expected-contains ["Hi" "Hello" "Greetings"]}
             {:input ["conv-1" "What's your name?"]
              :expected-contains ["assistant" "AI" "help"]}
             {:input ["conv-1" "Goodbye"]
              :expected-contains ["Bye" "farewell" "later"]}]})
  
  ;; Test conversation flow
  (let [client (aor/agent-client manager "Assistant")
        scenario (first (aor/get-dataset-entries manager dataset-id))]
    (for [turn (:turns scenario)]
      (let [result (apply aor/agent-invoke client (:input turn))
            passed? (some #(string/includes? 
                           (string/lower-case result)
                           (string/lower-case %))
                         (:expected-contains turn))]
        {:turn (:input turn)
         :response result
         :passed? passed?}))))
```

## Evaluators: Systematic Performance Measurement

[Evaluators](../glossary.md#evaluators) are functions for measuring [agent](../terms/agent.md) performance against [datasets](../terms/dataset.md). Define metrics, run [experiments](../terms/experiment.md), track performance systematically across test scenarios.

### Basic Evaluator

Create [evaluators](../glossary.md#evaluators) for automatic testing against [datasets](../terms/dataset.md) with custom scoring functions:

**Clojure:**
```clojure
(aor/defagentmodule EvaluationModule
  [topology]
  
  (-> topology
      (aor/new-agent "TextClassifier")
      (aor/node "classify" nil
                (fn [agent-node text]
                  (let [classification (classify-text text)]
                    (aor/result! agent-node classification))))))

;; Define evaluator
(let [manager (aor/agent-manager cluster "EvaluationModule")
      
      ;; Create evaluator function
      evaluator (aor/create-evaluator 
                  "accuracy-evaluator"
                  (fn [expected actual]
                    {:correct? (= expected actual)
                     :expected expected
                     :actual actual}))
      
      ;; Create test dataset
      dataset-id (aor/create-dataset manager "classification-tests" {})
      
      ;; Add test data
      _ (doseq [[text label] [["I love this!" "positive"]
                              ["This is terrible" "negative"]
                              ["It's okay" "neutral"]]]
          (aor/add-dataset-entry manager dataset-id
            {:input text :expected label}))
      
      ;; Run evaluation
      results (aor/run-evaluation manager
                "TextClassifier"
                dataset-id
                evaluator)]
  
  ;; Calculate metrics
  (let [correct (count (filter :correct? results))
        total (count results)]
    {:accuracy (/ correct total)
     :correct correct
     :total total
     :details results}))
```

**Java:**
```java
public class EvaluationModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("TextClassifier")
            .node("classify", null, (agentNode, text) -> {
                String classification = classifyText((String) text);
                agentNode.result(classification);
            });
    }
}

// Define evaluator
AgentManager manager = new AgentManager(cluster, "EvaluationModule");

// Create evaluator function
Evaluator evaluator = manager.createEvaluator(
    "accuracy-evaluator",
    (expected, actual) -> Map.of(
        "correct", expected.equals(actual),
        "expected", expected,
        "actual", actual
    )
);

// Create test dataset
String datasetId = manager.createDataset("classification-tests", Map.of());

// Add test data
List<List<Object>> testData = List.of(
    List.of("I love this!", "positive"),
    List.of("This is terrible", "negative"),
    List.of("It's okay", "neutral")
);

for (List<Object> data : testData) {
    manager.addDatasetEntry(datasetId, Map.of(
        "input", data.get(0),
        "expected", data.get(1)
    ));
}

// Run evaluation
List<Map<String, Object>> results = manager.runEvaluation(
    "TextClassifier",
    datasetId,
    evaluator
);

// Calculate metrics
long correct = results.stream()
    .filter(r -> (boolean) r.get("correct"))
    .count();
int total = results.size();

Map<String, Object> metrics = Map.of(
    "accuracy", (double) correct / total,
    "correct", correct,
    "total", total,
    "details", results
);
```

### Advanced Evaluation Patterns

Multi-metric [evaluations](../glossary.md#evaluators) for comprehensive testing with complex scoring algorithms and statistical analysis:

**Clojure:**
```clojure
(defn comprehensive-evaluator [expected actual]
  (let [exp-set (set expected)
        act-set (set actual)
        intersection (clojure.set/intersection exp-set act-set)]
    {:precision (if (empty? act-set) 0 
                  (/ (count intersection) (count act-set)))
     :recall (if (empty? exp-set) 0
               (/ (count intersection) (count exp-set)))
     :f1-score (let [p (:precision)
                     r (:recall)]
                 (if (zero? (+ p r)) 0
                   (/ (* 2 p r) (+ p r))))
     :exact-match? (= expected actual)
     :partial-match? (not-empty intersection)}))

;; Use comprehensive evaluator
(let [manager (aor/agent-manager cluster "Module")
      evaluator (aor/create-evaluator "comprehensive" 
                  comprehensive-evaluator)
      results (aor/run-evaluation manager 
                "Agent" dataset-id evaluator)]
  
  ;; Aggregate metrics
  {:avg-precision (avg (map :precision results))
   :avg-recall (avg (map :recall results))
   :avg-f1 (avg (map :f1-score results))
   :exact-matches (count (filter :exact-match? results))
   :partial-matches (count (filter :partial-match? results))})
```

## Putting It All Together

Here's a complete example using all system features: [forking](../glossary.md#fork), [datasets](../terms/dataset.md), [evaluators](../glossary.md#evaluators), and distributed state management:

**Clojure:**
```clojure
(aor/defagentmodule MLPipelineModule
  [topology]
  
  ;; Stores for pipeline state
  (aor/declare-pstate-store topology "experiments" String)
  (aor/declare-document-store topology "results" String
    {:accuracy Double :precision Double :recall Double 
     :parameters Object :timestamp Long})
  
  (-> topology
      (aor/new-agent "MLPipeline")
      
      ;; Train model with parameters
      (aor/node "train" "evaluate"
                (fn [agent-node dataset-id parameters]
                  (let [model (train-model dataset-id parameters)]
                    (aor/stream-chunk! agent-node 
                      {:status "training-complete"
                       :parameters parameters})
                    (aor/emit! agent-node "evaluate" model dataset-id))))
      
      ;; Evaluate model
      (aor/node "evaluate" nil
                (fn [agent-node model dataset-id]
                  (let [metrics (evaluate-model model dataset-id)
                        results (aor/get-store agent-node "results")]
                    ;; Store results
                    (store/put-multiple! results (str (random-uuid))
                      (assoc metrics 
                        :parameters (:parameters model)
                        :timestamp (System/currentTimeMillis)))
                    (aor/result! agent-node metrics))))))

;; Hyperparameter search with forking
(let [manager (aor/agent-manager cluster "MLPipelineModule")
      client (aor/agent-client manager "MLPipeline")
      
      ;; Create training dataset
      dataset-id (create-training-dataset manager)
      
      ;; Parameter grid
      param-grid [{:learning-rate 0.001 :batch-size 32}
                  {:learning-rate 0.01 :batch-size 32}
                  {:learning-rate 0.001 :batch-size 64}
                  {:learning-rate 0.01 :batch-size 64}]
      
      ;; Start base execution
      base-invoke (aor/agent-initiate client dataset-id {})
      
      ;; Fork for each parameter set
      forks (for [params param-grid]
              {:params params
               :invoke (aor/agent-fork base-invoke params)})
      
      ;; Collect results
      results (for [{:keys [params invoke]} forks]
                (assoc (aor/agent-result invoke) 
                  :parameters params))
      
      ;; Find best parameters
      best (apply max-key :accuracy results)]
  
  ;; Create evaluator for best model
  (let [evaluator (aor/create-evaluator "best-model"
                    (fn [expected actual]
                      (= expected (predict best actual))))]
    ;; Run final evaluation
    (aor/run-evaluation manager "MLPipeline" 
      test-dataset-id evaluator)))
```

## What You've Learned

You now command the full power of Agent-O-Rama:

- **Core Concepts**: Building blocks of [agents](../terms/agent.md), [nodes](../glossary.md#agent-node), and [graphs](../glossary.md#agent-graph)
- **State Management**: [Key-value stores](../terms/key-value-store.md) and distributed storage patterns
- **Communication**: [Streaming chunks](../glossary.md#streaming-chunk) and [human input](../glossary.md#human-input-request)
- **Advanced Patterns**: [Aggregation](../terms/aggregation.md) and [agent object](../terms/agent-objects.md) integration
- **System Features**: [Forking](../glossary.md#fork), [datasets](../terms/dataset.md), and [evaluators](../glossary.md#evaluators)

You're ready to build production [agent](../terms/agent.md) systems that scale, adapt, and deliver distributed AI solutions.