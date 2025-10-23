(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.multi-agg :as ma]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama.aggs :as aggs])
  (:import
   [com.rpl.agentorama
    AddDatasetExampleOptions
    AgentClient
    AgentClient$StreamAllCallback
    AgentClient$StreamCallback
    AgentGraph
    AgentInvoke
    AgentManager
    AgentNode
    AgentObjectFetcher
    AgentObjectSetup
    AgentTopology
    AgentStream
    AgentStreamByInvoke
    CreateEvaluatorOptions
    HumanInputRequest
    MultiAgg$Impl
    UpdateMode]
   [com.rpl.agentorama.impl
    IFetchAgentClient]
   [com.rpl.rama
    PState$Declaration
    PState$Schema]
   [com.rpl.rama.module
    StreamTopology]
   [com.rpl.rama.ops
    RamaAccumulatorAgg
    RamaCombinerAgg]
   [java.lang
    AutoCloseable]
   [java.util.concurrent
    CompletableFuture]
   [rpl.rama.generated
    TopologyDoesNotExistException]))

(defn- check-unique-agent-name!
  [agents-vol mirror-agents-vol name]
  (when (or (contains? @agents-vol name)
            (contains? @mirror-agents-vol name))
    (throw (h/ex-info "Agent already exists" {:name name}))))

(defn agent-topology
  "Creates a topology instance for defining agents, stores, and objects within a module. This function is used to add agents to a regular Rama module defined with [defmodule](https://redplanetlabs.com/clojuredoc/com.rpl.rama.html#var-defmodule).\n
\n
The topology provides the configuration context for:\n
  - Declaring agents with [[new-agent]]
  - Declaring stores: [[declare-key-value-store]], [[declare-document-store]], [[declare-pstate-store]]
  - Declaring agent objects: [[declare-agent-object]], [[declare-agent-object-builder]]
  - Declaring evaluators: [[declare-evaluator-builder]], [[declare-comparative-evaluator-builder]], [[declare-summary-evaluator-builder]]
  - Declaring actions: [[declare-action-builder]]
  - Declaring cluster agents: [[declare-cluster-agent]]
\n
Args:\n
  - setup - Rama module setup instance from defmodule parameters
  - topologies - Rama module topologies instance from defmodule parameters
\n
Returns:\n
  - agent topology instance"
  [setup topologies]
  (let [^StreamTopology stream-topology (stream-topology
                                         topologies
                                         aor-types/AGENT-TOPOLOGY-NAME)
        mb-topology            (microbatch-topology
                                topologies
                                aor-types/AGENT-MB-TOPOLOGY-NAME)
        analytics-mb-topology  (microbatch-topology
                                topologies
                                aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
        defined?-vol           (volatile! false)
        agents-vol             (volatile! {})
        mirror-agents-vol      (volatile! {})
        store-info-vol         (volatile! {})
        declared-objects-vol   (volatile! {})
        evaluator-builders-vol (volatile! {})
        action-builders-vol    (volatile! {})]
    (set-launch-topology-dynamic-option! setup
                                         aor-types/AGENT-MB-TOPOLOGY-NAME
                                         "topology.microbatch.phase.timeout.seconds"
                                         60)
    (set-launch-topology-dynamic-option! setup
                                         aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME
                                         "topology.microbatch.phase.timeout.seconds"
                                         60)
    (reify
     AgentTopology
     (newAgent [this name]
       (check-unique-agent-name! agents-vol mirror-agents-vol name)
       (let [ret (graph/mk-agent-graph)]
         (vswap! agents-vol assoc name ret)
         ret))
     (newToolsAgent [this name tools]
       (.newToolsAgent this name tools nil))
     (newToolsAgent [this name tools options]
       (tools/new-tools-agent
        this
        name
        tools
        (if options @options)))
     (getStreamTopology [this] stream-topology)
     (declareKeyValueStore [this name key-class val-class]
       (simpl/declare-store* stream-topology
                             store-info-vol
                             name
                             simpl/KV
                             {key-class val-class}))
     (declareDocumentStore [this name key-class key-val-classes]
       (when-not (-> key-val-classes
                     count
                     even?)
         (throw (h/ex-info
                 "Document store must be given even number of key/val classes"
                 {:count (count key-val-classes)})))
       (simpl/declare-store*
        stream-topology
        store-info-vol
        name
        simpl/DOC
        {key-class (fixed-keys-schema
                    (into {}
                          (mapv vec (partition 2 key-val-classes))))}))
     (^PState$Declaration declarePStateStore [this ^String name ^Class schema]
       (declare-pstate* stream-topology (symbol name) schema))
     (^PState$Declaration declarePStateStore [this ^String name
                                              ^PState$Schema schema]
       (.pstate stream-topology name schema))
     (declareAgentObject [this name o]
       (aor-types/declare-agent-object-builder-internal
        this
        name
        (fn [setup]
          (i/hook:building-plain-agent-object name o)
          o)
        {:thread-safe? true}))
     (declareAgentObjectBuilder [this name jfn]
       (aor-types/declare-agent-object-builder-internal this
                                                        name
                                                        (h/convert-jfn jfn)
                                                        nil))
     (declareAgentObjectBuilder [this name jfn options]
       (aor-types/declare-agent-object-builder-internal
        this
        name
        (h/convert-jfn jfn)
        (i/convert-agent-object-options options)))

     (declareEvaluatorBuilder [this name description builder-jfn]
       (.declareEvaluatorBuilder this name description builder-jfn nil))
     (declareEvaluatorBuilder [this name description builder-jfn options]
       (aor-types/declare-java-evaluator-builder
        this
        :regular
        name
        description
        builder-jfn
        options))
     (declareComparativeEvaluatorBuilder [this name description builder-jfn]
       (.declareComparativeEvaluatorBuilder this
                                            name
                                            description
                                            builder-jfn
                                            nil))
     (declareComparativeEvaluatorBuilder [this name description builder-jfn
                                          options]
       (aor-types/declare-java-evaluator-builder
        this
        :comparative
        name
        description
        builder-jfn
        options))
     (declareSummaryEvaluatorBuilder [this name description builder-jfn]
       (.declareSummaryEvaluatorBuilder this name description builder-jfn nil))
     (declareSummaryEvaluatorBuilder [this name description builder-jfn options]
       (aor-types/declare-java-evaluator-builder
        this
        :summary
        name
        description
        builder-jfn
        options))
     (declareActionBuilder [this name description builder-jfn]
       (.declareActionBuilder this name description builder-jfn nil))
     (declareActionBuilder [this name description builder-jfn options]
       (aor-types/declare-action-builder-internal this
                                                  name
                                                  description
                                                  (aor-types/convert-java-builder-fn builder-jfn)
                                                  (if options @options)))
     (declareClusterAgent [this localName moduleName agentName]
       (check-unique-agent-name! agents-vol mirror-agents-vol localName)
       ;; this connects the modules so a module update removing an agent needed
       ;; by another module fails
       (mirror-depot* setup
                      (gensym (str "*_mirrorAgentDepot" agentName))
                      moduleName
                      (po/agent-depot-name agentName))
       (vswap! mirror-agents-vol assoc localName [moduleName agentName]))
     (define [this]
       (when @defined?-vol
         (throw (h/ex-info "Agent topology already defined" {})))
       (vreset! defined?-vol true)
       (when (i/define-eval-agent?)
         (exp/define-evaluator-agent! this))
       (i/define-agents!
        setup
        topologies
        stream-topology
        mb-topology
        analytics-mb-topology
        @agents-vol
        @mirror-agents-vol
        @store-info-vol
        @declared-objects-vol
        @evaluator-builders-vol
        @action-builders-vol))
     aor-types/AgentTopologyInternal
     (declare-agent-object-builder-internal [this name afn options]
       (when-not (ifn? afn)
         (throw (h/ex-info "Object builder must be a function"
                           {:actual-type (class afn)})))
       (when (contains? @declared-objects-vol name)
         (throw (h/ex-info "Object already declared" {:name name})))
       (let [full-options (merge {:thread-safe?        false
                                  :auto-tracing?       true
                                  :worker-object-limit 1000}
                                 options)]
         (h/validate-options! name
                              full-options
                              {:thread-safe?        h/boolean-spec
                               :auto-tracing?       h/boolean-spec
                               :worker-object-limit h/positive-number-spec})
         (vswap! declared-objects-vol
                 assoc
                 name
                 {"limit"       (:worker-object-limit full-options)
                  "threadSafe"  (:thread-safe? full-options)
                  "autoTracing" (:auto-tracing? full-options)
                  "builderFn"   afn
                 })
       ))
     (declare-evaluator-builder-internal [this type name description builder-fn
                                          options]
       (when (contains? @evaluator-builders-vol name)
         (throw (h/ex-info "Evaluator builder already declared" {:name name})))
       (when (h/contains-string? name "/")
         (throw (h/ex-info "Evaluator builder name may not include '/'"
                           {:name name})))
       (when-not (ifn? builder-fn)
         (throw (h/ex-info "Builder must be a function"
                           {:type (class builder-fn)})))
       (let [full-options (merge {:params       {}
                                  :input-path?  true
                                  :output-path? true
                                  :reference-output-path? true}
                                 options)]
         (h/validate-options! name
                              full-options
                              {:params       h/map-spec
                               :input-path?  h/boolean-spec
                               :output-path? h/boolean-spec
                               :reference-output-path? h/boolean-spec
                              })
         (evals/validate-params! (:params full-options))
         (vswap! evaluator-builders-vol
                 assoc
                 name
                 {:builder-fn  builder-fn
                  :type        type
                  :description description
                  :options     options
                 })
       ))
     (declare-action-builder-internal [this name description builder-fn options]
       (when (contains? @action-builders-vol name)
         (throw (h/ex-info "Action builder already declared" {:name name})))
       (when (h/contains-string? name "/")
         (throw (h/ex-info "Action builder name may not include '/'"
                           {:name name})))
       (when-not (ifn? builder-fn)
         (throw (h/ex-info "Builder must be a function"
                           {:type (class builder-fn)})))
       (let [full-options (merge {:params {}
                                  :limit-concurrency? false}
                                 options)]
         (h/validate-options! name
                              full-options
                              {:params h/map-spec
                               :limit-concurrency? h/boolean-spec})
         ;; params have exact same specification as evals
         (evals/validate-params! (:params full-options))
         (vswap! action-builders-vol
                 assoc
                 name
                 {:builder-fn  builder-fn
                  :description description
                  :options     options
                 })
       ))
    )))

(defn underlying-stream-topology
  "Gets the underlying stream topology from an agent topology.\n
\n
This provides access to the low-level Rama topology for advanced use cases that require direct interaction with Rama's stream processing capabilities.\n

Args:\n
  - at - agent topology instance
\n
Returns:\n
  - the underlying Rama stream topology"
  [^AgentTopology at]
  (.getStreamTopology at))

(defn define-agents!
  "Finalizes the agent topology definition and prepares it for deployment. This is used when adding agents to a regular Rama module with [[agent-topology]].\n
\n
This function must be called after all agents, stores, and objects have been declared on the topology. It validates the configuration and prepares the topology for module launch.\n
\n
Args:\n
  - at - agent topology instance to finalize"
  [^AgentTopology at]
  (.define at))

(defn declare-key-value-store
  "Declares a key-value store in the agent topology.\n
\n
Key-value stores provide simple typed storage for agent state with automatic partitioning and distributed access.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the store that must begin with `$$`(used with [[get-store]])
  - key-class - Class for store keys (e.g., String, Long)
  - val-class - Class for store values (e.g., String, Object)
\n
Example:\n
<pre>
(declare-key-value-store topology \"$$user-cache\" String UserProfile)
</pre>"
  [^AgentTopology agent-topology name key-class val-class]
  (.declareKeyValueStore agent-topology name key-class val-class))

(defn declare-document-store
  "Declares a document store in the agent topology.\n
\n
   Document stores provide schema-flexible storage for complex nested data structures. Each document has a primary key and multiple typed fields that can be accessed independently.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the store that must begin with `$$` (used with [[get-store]])
  - key-class - Class for document primary keys (e.g., String, Long)
  - key-val-classes - Alternating field names (strings) and classes (e.g., \"user-id\" String \"profile\" Object \"preferences\" Map)
\n
Example:\n
<pre>
(declare-document-store topology \"$$user-docs\" String
  :profile UserProfile
  :preferences Map)
</pre>"
  [^AgentTopology agent-topology name key-class & key-val-classes]
  (.declareDocumentStore agent-topology
                         name
                         key-class
                         (into-array Object key-val-classes)))

(defn declare-pstate-store
  "Declares a PState store that directly uses Rama's built-in storage. PState stores are defined as any combination of durable, compound data structures.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the store that must begin with `$$` (used with [[get-store]])
  - schema - Rama PState schema definition
\n
Returns:\n
  - The PState declaration for further configuration
\n
Example:\n
<pre>
(declare-pstate-store topology \"$$user-stats\" {String (fixed-keys-schema {:a String :b (set-schema Long)})})
</pre>"
  [^AgentTopology agent-topology name schema]
  (declare-pstate* (.getStreamTopology agent-topology) (symbol name) schema))

(defn declare-agent-object
  "Declares a static agent object that will be shared across all agent executions.\n
\n
Agent objects are shared resources like AI models, database connections, or API clients that agents can access during execution. Static objects are created once and reused.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the object (used with get-agent-object)
  - val - The object instance to share
\n
Example:\n
<pre>
(declare-agent-object topology \"openai-api-key\" (System/getenv \"OPENAI_API_KEY\"))
</pre>"
  [^AgentTopology agent-topology name val]
  (.declareAgentObject agent-topology name val))

(defn declare-agent-object-builder
  "Declares an agent object builder that creates objects on-demand during agent execution.\n
\n
Builder objects are created lazily when first accessed, allowing for complex initialization logic and dependency injection. When a node gets an object, it gets exclusive access to it. A pool of up to worker-object-limit objects is created on demand, except when thread-safe? is set, in which case one object is created and shared for all usage within agents.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the object (used with [[get-agent-object]])
  - afn - Function that creates the object
  - options - Optional map with configuration:
    - :thread-safe? - Boolean, whether object is thread-safe (default false)
    - :auto-tracing? - Boolean, whether to auto-trace object calls (default true)
    - :worker-object-limit - Number, max objects per worker (default 1000)
\n
Example:\n
<pre>
(declare-agent-object-builder topology \"openai-model\"
  (fn [setup]
    (-> (OpenAiChatModel/builder)
        (.apiKey (get-agent-object setup \"openai-api-key\"))
        (.modelName \"gpt-4o-mini\")
        .build))
  {:thread-safe? true})
</pre>"
  ([agent-topology name afn]
   (declare-agent-object-builder agent-topology name afn nil))
  ([agent-topology name afn options]
   (aor-types/declare-agent-object-builder-internal agent-topology
                                                    name
                                                    afn
                                                    options)))


(defn declare-evaluator-builder
  "Declares an evaluator builder for creating custom evaluation functions for use in experiments or actions.\n
\n
Evaluators measure agent performance and can use AI models, databases, or custom logic to score agent outputs.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the evaluator builder
  - description - String description of what the evaluator measures
  - builder-fn - Function that takes params map and returns evaluator function. The evaluator function takes (fetcher input reference-output output) where fetcher can be used with [[get-agent-object]] to access shared resources. Must return a map of scores: score name (string) to score value (string, boolean, or number)
  - options - Optional map with configuration:
    - :params - Map of parameter definitions for the builder. Each param is a map with:
      - :description - String description of the parameter
      - :default - String default value for the parameter
    - :input-path? - Boolean, whether user must specify JSON path to extract input value (default true)
    - :output-path? - Boolean, whether user must specify JSON path to extract output value (default true)
    - :reference-output-path? - Boolean, whether user must specify JSON path to extract reference output value (default true)
\n
Example:\n
<pre>
(declare-evaluator-builder topology \"length-checker\"
  \"Checks if text meets length criteria\"
  (fn [params]  ; params is Map<String, String>
    (let [max-len (Integer/parseInt (get params \"maxLength\" \"100\"))]
      (fn [fetcher input ref-output output]
        {\"within-limit?\" (<= (count output) max-len)
         \"actual-length\" (count output)})))
  {:params {\"maxLength\" {:description \"Maximum allowed length\" :default \"100\"}}
   :input-path? true
   :output-path? true
   :reference-output-path? false})
</pre>"
  ([agent-topology name description builder-fn]
   (declare-evaluator-builder agent-topology name description builder-fn nil))
  ([agent-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agent-topology
                                                 :regular
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-comparative-evaluator-builder
  "Declares a comparative evaluator builder for comparing multiple agent outputs.\n
\n
Comparative evaluators compare multiple agent outputs against a reference to determine which performs better, useful for A/B testing and model selection.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the evaluator builder
  - description - String description of what the evaluator compares
  - builder-fn - Function that takes params map and returns a comparative evaluator function. The evaluator function takes (fetcher input reference-output outputs) where fetcher can be used with [[get-agent-object]] to access shared resources. Must return a map of scores: score name (string) to score value (string, boolean, or number). If the map contains an \"index\" key, that output will be highlighted as green in the comparative experiment results UI as the better result
  - options - Optional map with configuration:
    - :params - Map of parameter definitions for the builder. Each param is a map with:
      - :description - String description of the parameter
      - :default - String default value for the parameter
    - :input-path? - Boolean, whether user must specify JSON path to extract input value (default true)
    - :output-path? - Boolean, whether user must specify JSON path to extract output value (default true)
    - :reference-output-path? - Boolean, whether user must specify JSON path to extract reference output value (default true)
\n
Example:\n
<pre>
(declare-comparative-evaluator-builder topology \"quality-ranker\"
  \"Ranks outputs by quality metric\"
  (fn [params]  ; params is Map<String, String>
    (let [weight (Double/parseDouble (get params \"weight\" \"1.0\"))]
      (fn [fetcher input reference-output outputs]
        (let [scored (map-indexed #(vector %1 %2 (+ (count %2) (* weight (if (str/includes? %2 \"good\") 10 0)))) outputs)
              best (apply max-key last scored)]
          {:best-index (first best)
           :best-output (second best)
           :best-score (last best)}))))
  {:params {\"weight\" {:description \"Quality weight multiplier\" :default \"1.0\"}}})
</pre>"
  ([agent-topology name description builder-fn]
   (declare-comparative-evaluator-builder agent-topology
                                          name
                                          description
                                          builder-fn
                                          nil))
  ([agent-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agent-topology
                                                 :comparative
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-summary-evaluator-builder
  "Declares a summary evaluator builder for evaluating collections of example runs.\n
\n
Summary evaluators analyze multiple example runs to produce aggregate metrics and insights about agent performance across a dataset.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the evaluator builder
  - description - String description of what the evaluator summarizes
  - builder-fn - Function that takes params map and returns a summary evaluator function. The evaluator function takes (fetcher example-runs) where fetcher can be used with [[get-agent-object]] to access shared resources. Must return a map of scores: score name (string) to score value (string, boolean, or number)
  - options - Optional map with configuration:
    - :params - Map of parameter definitions for the builder. Each param is a map with:
      - :description - String description of the parameter
      - :default - String default value for the parameter
    - :input-path? - Boolean, whether user must specify JSON path to extract input value (default true)
    - :output-path? - Boolean, whether user must specify JSON path to extract output value (default true)
    - :reference-output-path? - Boolean, whether user must specify JSON path to extract reference output value (default true)
\n
Example:\n
<pre>
(declare-summary-evaluator-builder topology \"accuracy-summary\"
  \"Calculates accuracy across multiple examples\"
  (fn [params]  ; params is Map<String, String>
    (let [threshold (Double/parseDouble (get params \"threshold\" \"0.8\"))]
      (fn [fetcher example-runs]
        (let [total (count example-runs)
              correct (count (filter #(= (:reference-output %) (:output %)) example-runs))
              accuracy (if (pos? total) (/ (double correct) total) 0.0)
              pass-rate (if (pos? total) (/ (count (filter #(>= accuracy threshold) example-runs)) total) 0.0)]
          {:total-examples total
           :correct-predictions correct
           :accuracy accuracy
           :pass-rate pass-rate}))))
  {:params {\"threshold\" {:description \"Minimum accuracy threshold\" :default \"0.8\"}}})
</pre>"
  ([agent-topology name description builder-fn]
   (declare-summary-evaluator-builder agent-topology
                                      name
                                      description
                                      builder-fn
                                      nil))
  ([agent-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agent-topology
                                                 :summary
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-action-builder
  "Declares an action builder for creating custom actions that run on agent executions.\n
\n
Actions are hooks that execute on a sampled subset of live agent runs for online evaluation, dataset capture, webhook triggers, or custom logic.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the action builder
  - description - String description of what the action does
  - builder-fn - Function that takes params map and returns an action function
  - options - Optional map with configuration:
    - :params - Map of parameter definitions for the action. Each param is a map with:
      - :description - String description of the parameter
      - :default - String default value for the parameter
     - :limit-concurrency? - Boolean, whether to limit concurrent executions of the action (e.g. to avoid hitting model rate limits). Concurrency is controlled in the UI by the global action max.limited.actions.concurrency setting (default false)
\n
Example:\n
<pre>
(declare-action-builder topology \"telemetry-exporter\"
  \"Exports agent execution metrics to OpenTelemetry\"
  (fn [params]  ; params is Map<String, String>
    (let [service-name (get params \"service-name\" \"agent-o-rama\")
          endpoint (get params \"otlp-endpoint\")]
      (fn [fetcher input output run-info]
        (let [span-data {:service-name service-name
                         :operation-name \"agent-execution\"
                         :duration-ms (:latency-millis run-info)
                         :input-length (count (str input))
                         :output-length (count (str output))}]
          (send-to-otlp! endpoint span-data)))))
  {:params {\"service-name\" {:description \"OpenTelemetry service name\" :default \"agent-o-rama\"}
            \"otlp-endpoint\" {:description \"OTLP collector endpoint\"}}}})
</pre>"
  ([agent-topology name description builder-fn]
   (declare-action-builder agent-topology name description builder-fn nil))
  ([agent-topology name description builder-fn options]
   (aor-types/declare-action-builder-internal agent-topology name description builder-fn options)))

(defn declare-cluster-agent
  "Declares a reference to an agent from another module.\n
\n
Mirror agents enable cross-module agent interactions by creating a local proxy for an agent defined in a different module. This allows agents to invoke other agents across module boundaries.\n
\n
Subagents are fetched inside agent node functions with [[agent-client]]\n
\n
Args:\n
  - agent-topology - agent topology instance
  - local-name - String name for the local mirror agent
  - module-name - String name of the module containing the target agent
  - agent-name - String name of the target agent in the remote module
\n
Example:\n
<pre>
(declare-cluster-agent topology \"remote-chat\" \"chat-module\" \"chat-agent\")
</pre>"
  [^AgentTopology agent-topology local-name module-name agent-name]
  (.declareClusterAgent agent-topology local-name module-name agent-name))

(defn setup-object-name
  "Gets the name of an agent object from its setup context.\n
\n
Used within agent object builder functions to identify which object is being built.\n
\n
Args:\n
  - setup - setup instance from builder function
\n
Returns:\n
  - String - The name of the object being built"
  [^AgentObjectSetup setup]
  (.getObjectName setup))

(defn new-agent
  "Creates a new agent graph builder for defining an agent's execution flow.\n
\n
Returns an object that can be configured with nodes, edges, and execution logic. Agents are defined as directed graphs where nodes represent computation steps and edges define data flow. Graphs can contain loops for iterative processing.\n
\n
Args:\n
  - agent-topology - agent topology instance
  - name - String name for the agent (must be unique within the module)
\n
Returns:\n
  - Builder for configuring the agent's execution graph
\n
Example:\n
<pre>
(-> topology
    (aor/new-agent \"text-processor\")
    (aor/node \"start\" \"process\"
      (fn [agent-node input]
        (let [preprocessed (str/trim (str/upper-case input))]
          (aor/emit! agent-node \"process\" preprocessed))))
    (aor/node \"process\" nil
      (fn [agent-node text]
        (let [processed (str/replace text #\"[^A-Z ]\" \"\")]
          (aor/result! agent-node processed)))))
</pre>"
  [agent-topology name]
  (c/new-agent agent-topology name))

(defn node
  "Adds a node to an agent graph created with [[new-agent]] with specified execution logic.\n
\n
Nodes are the fundamental computation units in agent graphs. Each node receives data from upstream nodes and can emit data to downstream nodes or return a final result.\n
\n
Args:\n
  - agent-graph - agent graph builder instance
  - name - String name for the node (must be unique within the agent)
  - output-nodes-spec - Target node name(s) for emissions, or nil for terminal nodes. Can be a string, vector of strings, or nil. Calls to [[emit!]] inside the node function must target one of these declared nodes.
  - node-fn - Function that implements the node logic. Takes (agent-node & args) where args come from upstream emissions or agent invocation.
\n
Example:\n
<pre>
(node agent-graph \"process\" \"finalize\"
  (fn [agent-node data]
    (let [processed (transform data)]
      (emit! agent-node \"finalize\" processed))))
</pre>"
  [agent-graph name output-nodes-spec node-fn]
  (c/node agent-graph name output-nodes-spec node-fn))

(defn agg-start-node
  "Adds an aggregation start node that scopes aggregation within a subgraph.\n
\n
Aggregation start nodes work like regular nodes but define the beginning of an aggregation subgraph. They must have a corresponding [[agg-node]] downstream. Within the aggregation subgraph, edges must stay within the subgraph and cannot connect to nodes outside of it.\n
\n
The return value of the node function is passed to the downstream [[agg-node]] as its last argument, allowing propagation of non-aggregated information downstream post-aggregation.\n
\n
Args:\n
  - agent-graph - agent graph builder instance
  - name - String name for the node
  - output-nodes-spec - Target node name(s) for emissions, or nil for terminal nodes. Can be a string, vector of strings, or nil. Calls to [[emit!]] inside the node function must target one of these declared nodes.
  - node-fn - Function that implements the node logic. Takes (agent-node & args) where args come from upstream emissions or agent invocation. Return value is passed to downstream [[agg-node]] as last argument.
\n
Example:\n
<pre>
(-> topology
    (aor/new-agent \"data-processor\")
    (aor/agg-start-node \"distribute-work\" \"process-chunk\"
      (fn [agent-node {:keys [data chunk-size]}]
        (let [chunks (partition-all chunk-size data)]
          (doseq [chunk chunks]
            (aor/emit! agent-node \"process-chunk\" chunk)))))
    (aor/node \"process-chunk\" \"collect-results\"
      (fn [agent-node chunk]
        (let [processed (mapv #(* % %) chunk)
              chunk-sum (reduce + 0 processed)]
          (aor/emit! agent-node \"agg-results\" chunk-sum))))
    (aor/agg-node \"agg-results\" nil aggs/+sum
      (fn [agent-node total _]
        (aor/result! agent-node total))))
</pre>"
  [agent-graph name output-nodes-spec node-fn]
  (c/agg-start-node agent-graph name output-nodes-spec node-fn))

(defn agg-node
  "Adds an aggregation node that collects and combines results from multiple sources.\n
\n
Aggregation nodes gather results from parallel processing nodes and combine them using a specified aggregation function. They receive both the collected results and any metadata from the aggregation start node.\n
\n
Args:\n
  - agent-graph - agent graph builder instance
  - name - String name for the node
  - output-nodes-spec - Target node name(s) for emissions, or nil for terminal nodes. Can be a string, vector of strings, or nil. Calls to [[emit!]] inside the node function must target one of these declared nodes.
  - agg - Rama aggregator for combining results. Can be any Rama aggregator from [aggs namespace](https://redplanetlabs.com/clojuredoc/com.rpl.rama.aggs.html) or [custom aggregators](https://redplanetlabs.com/docs/~/clj-dataflow-lang.html#_aggregators)
  - node-fn - Function that processes the aggregated results. Takes (agent-node aggregated-value agg-start-res) where agg-start-res is the return value from the corresponding [[agg-start-node]]
\n
Example:\n
<pre>
(-> topology
    (aor/new-agent \"data-processor\")
    (aor/agg-start-node \"distribute-work\" \"process-chunk\"
      (fn [agent-node {:keys [data chunk-size]}]
        (let [chunks (partition-all chunk-size data)]
          (doseq [chunk chunks]
            (aor/emit! agent-node \"process-chunk\" chunk)))))
    (aor/node \"process-chunk\" \"agg-results\"
      (fn [agent-node chunk]
        (let [processed (mapv #(* % %) chunk)
              chunk-sum (reduce + 0 processed)]
          (aor/emit! agent-node \"agg-results\" chunk-sum))))
    (aor/agg-node \"agg-results\" nil aggs/+sum
      (fn [agent-node total _]
        (aor/result! agent-node total))))
</pre>"
  [agent-graph name output-nodes-spec agg node-fn]
  (c/agg-node agent-graph name output-nodes-spec agg node-fn))

(defn set-update-mode
  "Sets the update mode for an agent graph to control how in-flight agent executions
   are handled after the module is updated.\n
\n
When a module is updated, in-flight agent executions can be handled in three ways:\n
  - :continue - Executions continue where they left off with the new agent definition
  - :restart - Executions restart from the beginning with the new agent definition
  - :drop - In-flight executions are dropped and not processed
\n
Args:\n
  - agent-graph - agent graph builder instance
  - mode - Update mode keyword: :continue, :restart, or :drop
\n
Example:\n
<pre>
(set-update-mode agent-graph :continue)
</pre>"
  [^AgentGraph agent-graph mode]
  (.setUpdateMode
   agent-graph
   (graph/convert-update-mode->java mode)))

(defmacro multi-agg
  "Creates an aggregator for use with [[agg-node]] that supports multiple dispatch targets.\n
\n
The first argument when emitting to the agg node is the dispatch target, which runs the corresponding `on` declaration.\n
\n
Args:\n
  - body - Forms defining the multi-aggregation:
    - <pre>(init [bindings] & body)</pre> - Returns the initial aggregation value
    - <pre>(on dispatch-target [agg-value & additional-args] & body)</pre> - Handler for each dispatch target. Takes the current aggregation value plus additional arguments from the emit! call
\n
Example:\n
<pre>
(multi-agg
  (init [] {:sum 0 :texts []})
  (on \"add\" [acc value]
    (update acc :sum + value))
  (on \"text\" [acc text]
    (update acc :texts conj text)))
</pre>"
  [& body]
  (let [ret-sym (gensym "ret")]
    `(let [~ret-sym (ma/mk-multi-agg)]
       ~@(for [form body]
           (condp = (first form)
             'init
             (let [[_ bindings & body] form]
               (when-not (= [] bindings)
                 (throw (h/ex-info "Invalid binding vector for MultiAgg init"
                                   {:bindings bindings :required []})))
               `(ma/internal-add-init! ~ret-sym (fn [] ~@body)))

             'on
             (let [[_ name & body] form]
               `(ma/internal-add-handler! ~ret-sym ~name (fn ~@body)))
             (throw (h/ex-info "Invalid MultiAgg method"
                               {:method (first form)}))
           ))
       ~ret-sym
     )))

(defn emit!
  "Emits data from the current node to the specified target node.\n
\n
This is the primary mechanism for data flow between nodes in agent graphs. Emissions trigger execution of downstream nodes with the provided arguments.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - node - String name of the target node
  - args - Arguments to pass to the target node
\n
Example:\n
<pre>
(aor/emit! agent-node \"process\" data)
</pre>"
  [agent-node node & args]
  (apply c/emit! agent-node node args))

(defn result!
  "Sets the final result for the agent that will be displayed in the UI and returned for calls to [[agent-result]] and [[agent-invoke]].\n
\n
This function signals completion of the agent execution and returns the final result to the client. If multiple nodes call `result!` in parallel, only the first one will be used as the agent result and others will be dropped (first-one-wins behavior). It is mutually exclusive with emit! - a node should either emit to other nodes or return a result, not both.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - val - The final result value to return to the client
\n
Example:\n
<pre>
(result! agent-node {:status \"success\" :data processed-data})
</pre>"
  [agent-node val]
  (c/result! agent-node val))

(defn get-store
  "Gets a store instance for accessing persistent storage within a node.\n
\n
Stores provide distributed, persistent, replicated storage that agents can use to maintain state across executions.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - name - String name of the store (declared with declare-*-store functions)
\n
Returns:\n
  - Store instance with API methods in the com.rpl.agent-o-rama.store namespace (get, put!, delete!, etc.)
\n
Example:\n
<pre>
(let [store (get-store agent-node \"$$user-cache\")]
  (store/put! store \"user-123\" user-data)
  (store/get store \"user-123\"))
</pre>"
  [^AgentNode agent-node name]
  (.getStore agent-node name))

(defn get-agent-object
  "Gets a shared agent object (AI models, database clients, etc.) within a node, evaluator, or action function.\n
\n
Agent objects are shared resources declared in the topology that can be accessed by any node. They support automatic lifecycle management and connection pooling.\n
\n
Args:\n
  - fetch - object fetcher instance (agent-node or first argument to evaluator or action function)
  - name - String name of the object (declared with declare-agent-object*)
\n
Returns:\n
  - The shared object instance
\n
Example:\n
<pre>
(let [model (get-agent-object agent-node \"openai-model\")]
  (lc4j/chat model messages))
</pre>"
  [^AgentObjectFetcher fetch name]
  (.getAgentObject fetch name))

(defn stream-chunk!
  "Manually streams a chunk of data from the current node for real-time consumption from agent clients via [[agent-stream]] or [[agent-stream-all]].\n
\n
Streaming chunks are separate from the agent's final result and allow for real-time progress updates and incremental data delivery to clients. Chunks are delivered to streaming subscriptions in order.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - chunk - The data chunk to stream (any serializable value)
\n
Example:\n
<pre>
(aor/stream-chunk! agent-node {:progress (count processed) :item item})
</pre>"
  [^AgentNode agent-node chunk]
  (.streamChunk agent-node chunk))

(defn record-nested-op!
  "Records a nested operation for tracing and performance monitoring.\n
\n
This function is used by the framework to track operations like AI model calls, database queries, and external API calls, is viewable in the trace in the UI, and is included in aggregated statistics about agent execution.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - nested-op-type - Keyword type of the operation. Must be one of:
    - :store-read, :store-write, :db-read, :db-write, :model-call,
    - :tool-call, :agent-call, :human-input, :other
  - start-time-millis - Long start time of the operation
  - finish-time-millis - Long finish time of the operation
  - info-map - Map from String to value with additional operation metadata. For :model-call, include \"inputTokenCount\", \"outputTokenCount\", \"totalTokenCount\" for analytics, or \"failure\" with exception string for failures."
  [agent-node nested-op-type start-time-millis finish-time-millis info-map]
  (anode/record-nested-op!-impl agent-node
                                nested-op-type
                                start-time-millis
                                finish-time-millis
                                info-map))

(defn get-human-input
  "Requests human input during agent execution, blocking until response is received.\n
\n
This function pauses agent execution and requests input from a human user. The agent will remain in a waiting state until the human provides a response through the client API or web UI. Since nodes run on virtual threads, this is efficient.\n
\n
Args:\n
  - agent-node - agent node instance from the current node function
  - prompt - String prompt to display to the human user
\n
Returns:\n
  - String - The human's response
\n
Example:\n
<pre>
(defn human-yes?
 [agent-node prompt]
 (loop [res (aor/get-human-input agent-node prompt)]
   (cond (= res \"yes\") true
         (= res \"no\") false
         :else (recur (aor/get-human-input agent-node \"Please answer 'yes' or 'no'.\")))))
</pre>"
  [^AgentNode agent-node prompt]
  (.getHumanInput agent-node prompt))


(defn get-metadata
  "Gets metadata associated with an agent invocation. Can be called from an agent client to get the metadata for that invoke, or can be called from within any agent node function.\n
\n
Metadata allows attaching custom key-value data to agent executions. Metadata is an additional optional parameter to agent execution, and its also used for analytics. Metadata can be accessed anywhere inside agents by calling [[get-metadata]] within node functions.\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke returned by [[agent-initiate]]\n
OR\n
  - agent-node - agent node instance (for accessing within agent execution)
\n
Returns:\n
  - Map - The metadata associated with the invocation or node
\n
Example:\n
<pre>
(get-metadata client agent-invoke)
(get-metadata agent-node)
</pre>"
  ([^AgentClient client agent-invoke]
   (.getMetadata client agent-invoke))
  ([^AgentNode agent-node]
   (.getMetadata agent-node)))

(defn- parse-map-options
  [[arg1 & rest-args :as args]]
  (if (map? arg1) [arg1 rest-args] [{} args]))

(defmacro agentmodule
  "Creates an anonymous agent module for packaging agents, stores, and objects into a deployable unit.\n
\n
An agent module is the top-level container that defines a complete agent system, encapsulating all resources needed for distributed agent execution. It provides the context for defining agents, stores, and shared objects within a Rama module.\n
\n
The topology provides the configuration context for:\n
  - Declaring agents with [[new-agent]]
  - Declaring stores: [[declare-key-value-store]], [[declare-document-store]], [[declare-pstate-store]]
  - Declaring agent objects: [[declare-agent-object]], [[declare-agent-object-builder]]
  - Declaring evaluators: [[declare-evaluator-builder]], [[declare-comparative-evaluator-builder]], [[declare-summary-evaluator-builder]]
  - Declaring actions: [[declare-action-builder]]
  - Declaring cluster agents: [[declare-cluster-agent]]
\n
Args:\n
  - options - Optional map with configuration:
    - :module-name - String name for the module (defaults to auto-generated)
  - agent-topology-sym - Symbol for the agent topology binding in the body
  - body - Forms that define agents, stores, and objects using the topology
\n
Returns:\n
  - Rama module that can be deployed to a cluster
\n
Example:\n
<pre>
(agentmodule
       [topology]
       (-> topology
           (aor/new-agent \"my-agent\")
           (aor/node \"process\" nil
             (fn [agent-node input]
               (aor/result! agent-node (str \"Processed: \" input))))))
</pre>"
  [& args]
  (let [[options [[agent-topology-sym] & body]] (parse-map-options args)]
    `(module ~options
       [setup# topologies#]
       (let [~agent-topology-sym (agent-topology setup# topologies#)]
         ~@body
         (define-agents! ~agent-topology-sym)
       ))))

(defmacro defagentmodule
  "Defines a named agent module for packaging agents, stores, and objects into a deployable unit.\n
\n
This is a convenience macro that creates a def binding for an agent module, automatically setting the module name to the symbol name. It's the primary way to define agent modules in most applications.\n
\n
The topology provides the configuration context for:\n
  - Declaring agents with [[new-agent]]
  - Declaring stores: [[declare-key-value-store]], [[declare-document-store]], [[declare-pstate-store]]
  - Declaring agent objects: [[declare-agent-object]], [[declare-agent-object-builder]]
  - Declaring evaluators: [[declare-evaluator-builder]], [[declare-comparative-evaluator-builder]], [[declare-summary-evaluator-builder]]
  - Declaring actions: [[declare-action-builder]]
  - Declaring cluster agents: [[declare-cluster-agent]]
\n
Args:\n
  - sym - Symbol name for the module (becomes the module name)
  - options - Optional map with configuration to override the module name
  - agent-topology-sym - Symbol for the agent topology binding in the body
  - body - Forms that define agents, stores, and objects using the topology
\n
Returns:\n
  - Defines a Rama module that can be deployed to a cluster
\n
Example:\n
<pre>
(defagentmodule BasicAgentModule
  [topology]
  (-> topology
      (aor/new-agent \"my-agent\")
      (aor/node \"process\" nil
        (fn [agent-node user-name]
          (aor/result! agent-node (str \"Welcome, \" user-name \"!\"))))))
</pre>"
  [sym & args]
  (let [[options args] (parse-map-options args)
        name-default   (str sym)]
    `(def ~sym
       (agentmodule ~(merge {:module-name name-default} options) ~@args))))

(defn agent-manager
  "Creates an agent manager for managing and interacting with deployed agents on a Rama cluster.\n
\n
The agent manager provides access to agent clients, dataset management, and evaluation capabilities for a specific module deployed on a cluster.\n
\n
Args:\n
  - cluster - Rama cluster instance (IPC or remote cluster)
  - module-name - String name of the deployed module
\n
Returns:\n
  - Interface for managing agents and datasets
\n
Example:\n
<pre>
(let [manager (aor/agent-manager ipc \"MyModule\")]
  (aor/agent-client manager \"MyAgent\"))
</pre>"
  [cluster module-name]
  (let [agent-names-query
        (try
          (foreign-query cluster
                         module-name
                         (queries/agent-get-names-query-name))
          (catch TopologyDoesNotExistException e
            (throw (h/ex-info e
                              "Module does not host agents"
                              {:module-name module-name}))
          ))

        datasets-depot            (foreign-depot cluster
                                                 module-name
                                                 (po/datasets-depot-name))

        agent-edit-depot          (foreign-depot cluster module-name (po/agent-edit-depot-name))
        datasets-pstate           (foreign-pstate
                                   cluster
                                   module-name
                                   (po/datasets-task-global-name))
        datasets-page-query       (foreign-query
                                   cluster
                                   module-name
                                   (queries/get-datasets-page-query-name))
        datasets-search-query     (foreign-query
                                   cluster
                                   module-name
                                   (queries/search-datasets-name))

        search-examples-query     (foreign-query cluster
                                                 module-name
                                                 (queries/search-examples-name))
        multi-examples-query      (foreign-query cluster
                                                 module-name
                                                 (queries/multi-examples-name))

        global-actions-depot      (foreign-depot cluster
                                                 module-name
                                                 (po/global-actions-depot-name))
        evals-pstate              (foreign-pstate
                                   cluster
                                   module-name
                                   (po/evaluators-task-global-name))

        try-eval-query            (foreign-query
                                   cluster
                                   module-name
                                   (queries/try-evaluator-name))

        all-eval-builders-query   (foreign-query
                                   cluster
                                   module-name
                                   (queries/all-evaluator-builders-name))

        all-action-builders-query (foreign-query
                                   cluster
                                   module-name
                                   (ana/all-action-builders-name))

        search-evals-query        (foreign-query
                                   cluster
                                   module-name
                                   (queries/search-evaluators-name))
        search-experiments-query  (foreign-query
                                   cluster
                                   module-name
                                   (queries/search-experiments-name))
        experiments-results-query (foreign-query
                                   cluster
                                   module-name
                                   (queries/experiment-results-name))]
    (reify
     AgentManager
     (getAgentNames [this]
       (foreign-invoke-query agent-names-query))
     (getAgentClient [this agentName]
       (let [agents-set              (foreign-invoke-query agent-names-query)
             _ (when-not (contains? agents-set agentName)
                 (throw (h/ex-info "Agent does not exist"
                                   {:available  agents-set
                                    :agent-name agentName})))
             agent-depot             (foreign-depot cluster
                                                    module-name
                                                    (po/agent-depot-name agentName))
             human-depot             (foreign-depot cluster
                                                    module-name
                                                    (po/agent-human-depot-name agentName))
             agent-config-depot      (foreign-depot cluster
                                                    module-name
                                                    (po/agent-config-depot-name agentName))
             config-pstate           (foreign-pstate
                                      cluster
                                      module-name
                                      (po/agent-config-task-global-name agentName))
             root-pstate             (foreign-pstate
                                      cluster
                                      module-name
                                      (po/agent-root-task-global-name agentName))
             stream-shared-pstate    (foreign-pstate
                                      cluster
                                      module-name
                                      (po/agent-stream-shared-task-global-name agentName))
             agent-rules-pstate      (foreign-pstate
                                      cluster
                                      module-name
                                      (po/agent-rules-task-global-name agentName))
             telemetry-pstate        (foreign-pstate
                                      cluster
                                      module-name
                                      (po/agent-telemetry-task-global-name agentName))
             tracing-query           (foreign-query
                                      cluster
                                      module-name
                                      (queries/tracing-query-name agentName))
             invokes-page-query      (foreign-query
                                      cluster
                                      module-name
                                      (queries/agent-get-invokes-page-query-name agentName))
             current-graph-query     (foreign-query
                                      cluster
                                      module-name
                                      (queries/agent-get-current-graph-name agentName))
             action-log-query        (foreign-query cluster
                                                    module-name
                                                    (queries/action-log-page-name agentName))
             search-metadata-query   (foreign-query cluster
                                                    module-name
                                                    (queries/search-metadata-name agentName))
             all-agent-metrics-query (foreign-query cluster
                                                    module-name
                                                    (queries/all-agent-metrics-name agentName))
            ]
         (reify
          AgentClient
          (invoke [this args]
            (.get (.invokeAsync this args)))
          (invokeAsync [this args]
            (.thenCompose
             (.initiateAsync this args)
             (h/cf-function [agent-invoke]
               (.resultAsync this agent-invoke))))
          (initiate [this args]
            (.get (.initiateAsync this args)))
          (initiateAsync [this args]
            (.initiateWithContextAsync this nil args))

          (invokeWithContext [this context args]
            (.get (.invokeWithContextAsync this context args)))
          (invokeWithContextAsync [this context args]
            (aor-types/invoke-with-context-async-internal this @context (into [] args)))
          (initiateWithContext [this context args]
            (.get (.initiateWithContextAsync this context args)))
          (initiateWithContextAsync [this context args]
            (aor-types/initiate-with-context-async-internal this
                                                            (if context @context)
                                                            (into [] args)))
          (fork [this invoke nodeInvokeIdToNewArgs]
            (.get (.forkAsync this invoke nodeInvokeIdToNewArgs)))
          (forkAsync [this invoke nodeInvokeIdToNewArgs]
            (.thenCompose
             (.initiateForkAsync this invoke nodeInvokeIdToNewArgs)
             (h/cf-function [agent-invoke]
               (.resultAsync this agent-invoke))))
          (initiateFork [this invoke nodeInvokeIdToNewArgs]
            (.get (.initiateForkAsync this invoke nodeInvokeIdToNewArgs)))
          (initiateForkAsync [this invoke invokeIdToNewArgs]
            (.thenApply
             (foreign-append-async!
              agent-depot
              (aor-types/->ForkAgentInvoke
               (.getTaskId invoke)
               (.getAgentInvokeId invoke)
               invokeIdToNewArgs))
             (h/cf-function [{[agent-task-id agent-id]
                              aor-types/AGENT-TOPOLOGY-NAME}]
               (aor-types/->AgentInvokeImpl agent-task-id agent-id)
             )))

          (nextStep [this agent-invoke]
            (.get (.nextStepAsync this agent-invoke)))
          (nextStepAsync [this agent-invoke]
            (i/client-wait-for-result
             root-pstate
             agent-invoke
             (fn [{:keys [result human-request exceptions]}]
               (cond
                 result
                 (fn [^CompletableFuture cf]
                   (if (:failure? result)
                     (.completeExceptionally
                      cf
                      (i/mk-failure-exception result exceptions))
                     (.complete
                      cf
                      (aor-types/->AgentCompleteImpl (:val result)))))

                 human-request
                 (fn [^CompletableFuture cf]
                   (.complete cf human-request))
               ))))
          (result [this agent-invoke]
            (.get (.resultAsync this agent-invoke)))
          (resultAsync [this agent-invoke]
            (i/client-wait-for-result
             root-pstate
             agent-invoke
             (fn [{:keys [result exceptions]}]
               (when result
                 (fn [^CompletableFuture cf]
                   (if (:failure? result)
                     (.completeExceptionally
                      cf
                      (i/mk-failure-exception result exceptions))
                     (.complete cf (:val result))))
               ))))


          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^int value]
            (aor-types/set-metadata-internal! this agent-invoke key (long value)))
          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^long value]
            (aor-types/set-metadata-internal! this agent-invoke key value))
          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^float value]
            (aor-types/set-metadata-internal! this agent-invoke key (double value)))
          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^double value]
            (aor-types/set-metadata-internal! this agent-invoke key value))
          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^String value]
            (aor-types/set-metadata-internal! this agent-invoke key value))
          (^void setMetadata [this ^AgentInvoke agent-invoke ^String key ^boolean value]
            (aor-types/set-metadata-internal! this agent-invoke key value))
          (removeMetadata [this {:keys [task-id agent-invoke-id]} key]
            (foreign-append!
             agent-edit-depot
             (aor-types/->valid-EditMetadata agentName task-id agent-invoke-id key nil)))
          (getMetadata [this {:keys [task-id agent-invoke-id]}]
            (foreign-select-one [(keypath agent-invoke-id) :metadata]
                                root-pstate
                                {:pkey task-id}))

          (isAgentInvokeComplete [this agent-invoke]
            (let [agent-task-id (.getTaskId agent-invoke)
                  agent-id      (.getAgentInvokeId agent-invoke)]
              (foreign-select-one [(keypath agent-id) :result (view some?)]
                                  root-pstate
                                  {:pkey agent-task-id})))

          (stream [this agent-invoke node]
            (.stream this agent-invoke node nil))
          (stream [this agent-invoke node stream-callback]
            (aor-types/stream-internal
             this
             agent-invoke
             node
             (when stream-callback
               (fn [all-chunks new-chunks reset? complete?]
                 (.onUpdate ^AgentClient$StreamCallback
                            stream-callback
                            all-chunks
                            new-chunks
                            reset?
                            complete?)))))
          (streamSpecific [this agent-invoke node node-invoke-id]
            (.streamSpecific this agent-invoke node node-invoke-id nil))
          (streamSpecific [this agent-invoke node node-invoke-id
                           stream-callback]
            (aor-types/stream-specific-internal
             this
             agent-invoke
             node
             node-invoke-id
             (when stream-callback
               (fn [all-chunks new-chunks reset? complete?]
                 (.onUpdate ^AgentClient$StreamCallback
                            stream-callback
                            all-chunks
                            new-chunks
                            reset?
                            complete?)))))
          (streamAll [this agent-invoke node]
            (.streamAll this agent-invoke node nil))
          (streamAll [this agent-invoke node stream-all-callback]
            (aor-types/stream-all-internal
             this
             agent-invoke
             node
             (when stream-all-callback
               (fn [all-chunks new-chunks reset-invoke-ids complete?]
                 (.onUpdate ^AgentClient$StreamAllCallback
                            stream-all-callback
                            all-chunks
                            new-chunks
                            reset-invoke-ids
                            complete?)))))

          (pendingHumanInputs [this invoke]
            (.get (.pendingHumanInputsAsync this invoke)))
          (pendingHumanInputsAsync [this invoke]
            (let [agent-task-id (.getTaskId invoke)
                  agent-id      (.getAgentInvokeId invoke)]
              (foreign-select-async
               [(keypath agent-id)
                :human-requests
                (sorted-set-range-from-start 1000)
                ALL]
               root-pstate
               {:pkey agent-task-id}
              )))
          (provideHumanInput [this request response]
            (.get (.provideHumanInputAsync this request response)))
          (provideHumanInputAsync [this request response]
            (foreign-append-async!
             human-depot
             (aor-types/->valid-HumanInput request response)))
          (close [this]
            (close! agent-depot)
            (close! agent-config-depot))
          aor-types/AgentClientInternal
          (set-metadata-internal! [this {:keys [task-id agent-invoke-id]} key value]
            (foreign-append!
             agent-edit-depot
             (aor-types/->valid-EditMetadata agentName task-id agent-invoke-id key value)))
          (invoke-with-context-async-internal [this context args]
            (.thenCompose
             ^CompletableFuture
             (aor-types/initiate-with-context-async-internal this context args)
             (h/cf-function [agent-invoke]
               (.resultAsync this agent-invoke))))
          (initiate-with-context-async-internal [this context args]
            (let [{:keys [metadata] :as context} (merge {:metadata {}} context)]
              (h/validate-options! agentName
                                   context
                                   {:metadata h/map-spec})
              (when-not (every? string? (keys metadata))
                (throw (h/ex-info "Metadata keys must be strings"
                                  {:keys (pr-str (keys metadata))})))
              (when-not (every? aor-types/valid-restricted-map-value? (vals metadata))
                (throw (h/ex-info
                        "Metadata values must be ints, longs, floats, doubles, booleans, or strings"
                        {:vals (pr-str (vals metadata))})))
              (.thenApply
               (foreign-append-async!
                agent-depot
                (aor-types/->valid-AgentInitiate
                 (vec args)
                 aor-types/FORCED-AGENT-TASK-ID
                 aor-types/FORCED-AGENT-INVOKE-ID
                 metadata
                 aor-types/OPERATION-SOURCE))
               (h/cf-function [{[agent-task-id agent-id]
                                aor-types/AGENT-TOPOLOGY-NAME}]
                 (aor-types/->AgentInvokeImpl agent-task-id agent-id)
               ))))
          (stream-internal [this agent-invoke node callback-fn]
            (iclient/agent-stream-impl
             root-pstate
             agent-invoke
             node
             callback-fn))
          (stream-specific-internal [this agent-invoke node node-invoke-id
                                     callback-fn]
            (iclient/agent-stream-specific-impl
             root-pstate
             agent-invoke
             node
             node-invoke-id
             callback-fn))
          (stream-all-internal [this agent-invoke node callback-fn]
            (iclient/agent-stream-all-impl
             root-pstate
             agent-invoke
             node
             callback-fn))
          (subagent-next-step-async [this agent-invoke]
            (i/client-wait-for-result
             root-pstate
             agent-invoke
             (fn [{:keys [result human-request exceptions stats]}]
               (cond
                 result
                 (fn [^CompletableFuture cf]
                   (.complete cf
                              {:stats  stats
                               :result (if (:failure? result)
                                         (i/mk-failure-exception result exceptions)
                                         (aor-types/->AgentCompleteImpl (:val result)))}))
                 human-request
                 (fn [^CompletableFuture cf]
                   (.complete cf human-request))
               ))
             true))
          aor-types/UnderlyingObjects
          (underlying-objects [this]
            {:agent-depot             agent-depot
             :agent-config-depot      agent-config-depot
             :config-pstate           config-pstate
             :root-pstate             root-pstate
             :stream-shared-pstate    stream-shared-pstate
             :agent-rules-pstate      agent-rules-pstate
             :telemetry-pstate        telemetry-pstate
             :tracing-query           tracing-query
             :invokes-page-query      invokes-page-query
             :current-graph-query     current-graph-query
             :action-log-query        action-log-query
             :search-metadata-query   search-metadata-query
             :all-agent-metrics-query all-agent-metrics-query
            })
         )))
     (createDataset [this name description inputJsonSchema outputJsonSchema]
       (let [uuid (h/random-uuid7)

             {error aor-types/AGENT-TOPOLOGY-NAME}
             (foreign-append!
              datasets-depot
              (aor-types/->valid-CreateDataset uuid
                                               name
                                               description
                                               inputJsonSchema
                                               outputJsonSchema))]
         (when error
           (throw (h/ex-info "Error creating dataset" {:info error})))
         uuid))
     (setDatasetName [this datasetId name]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-UpdateDatasetProperty datasetId :name name)))
     (setDatasetDescription [this datasetId description]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-UpdateDatasetProperty datasetId
                                                 :description
                                                 description)))
     (destroyDataset [this datasetId]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-DestroyDataset datasetId)))
     (addDatasetExampleAsync
       [this datasetId input options]
       (let [options (or options (AddDatasetExampleOptions.))
             uuid    (h/random-uuid7)]
         (-> (foreign-append-async!
              datasets-depot
              (aor-types/->valid-AddDatasetExample
               datasetId
               (.snapshotName options)
               uuid
               input
               (.referenceOutput options)
               (into #{} (.tags options))
               (or aor-types/OPERATION-SOURCE (aor-types/->ApiSourceImpl))
              ))
             (.thenApply
              (h/cf-function [{error aor-types/AGENT-TOPOLOGY-NAME}]
                (when error
                  (throw (h/ex-info "Error adding example" {:info error})))
                uuid
              )))))
     (addDatasetExample [this datasetId input options]
       (.get (.addDatasetExampleAsync this
                                      datasetId
                                      input
                                      options)))
     (setDatasetExampleInput [this datasetId snapshotName exampleId input]
       (let [{error aor-types/AGENT-TOPOLOGY-NAME}
             (foreign-append!
              datasets-depot
              (aor-types/->valid-UpdateDatasetExample datasetId
                                                      snapshotName
                                                      exampleId
                                                      :input
                                                      input))]
         (when error
           (throw (h/ex-info "Error updating example" {:info error})))
       ))
     (setDatasetExampleReferenceOutput
       [this datasetId snapshotName exampleId referenceOutput]
       (let [{error aor-types/AGENT-TOPOLOGY-NAME}
             (foreign-append!
              datasets-depot
              (aor-types/->valid-UpdateDatasetExample datasetId
                                                      snapshotName
                                                      exampleId
                                                      :reference-output
                                                      referenceOutput))]
         (when error
           (throw (h/ex-info "Error updating example" {:info error})))
       ))
     (removeDatasetExample [this datasetId snapshotName exampleId]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-RemoveDatasetExample datasetId
                                                snapshotName
                                                exampleId)))
     (addDatasetExampleTag [this datasetId snapshotName exampleId tag]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-AddDatasetExampleTag datasetId
                                                snapshotName
                                                exampleId
                                                tag)))
     (removeDatasetExampleTag [this datasetId snapshotName exampleId tag]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-RemoveDatasetExampleTag datasetId
                                                   snapshotName
                                                   exampleId
                                                   tag)))
     (snapshotDataset [this datasetId fromSnapshotName toSnapshotName]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-DatasetSnapshot datasetId
                                           fromSnapshotName
                                           toSnapshotName)))
     (removeDatasetSnapshot [this datasetId snapshotName]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-RemoveDatasetSnapshot datasetId
                                                 snapshotName)))
     (searchDatasets [this searchString limit]
       (foreign-invoke-query datasets-search-query searchString limit))

     (createEvaluator [this name builderName params description options]
       (let [{error aor-types/AGENT-TOPOLOGY-NAME}
             (foreign-append!
              global-actions-depot
              (aor-types/->valid-AddEvaluator
               name
               builderName
               params
               description
               (.inputJsonPath options)
               (.outputJsonPath options)
               (.referenceOutputJsonPath options)
              ))]
         (when error
           (throw (h/ex-info "Error creating evaluator" {:info error})))
       ))
     (removeEvaluator [this name]
       (foreign-append!
        global-actions-depot
        (aor-types/->valid-RemoveEvaluator name)
       ))
     (searchEvaluators [this searchString]
       (into #{}
             (foreign-select
              [MAP-KEYS
               (selected? (view h/contains-string? searchString) identity)]
              evals-pstate)))
     (tryEvaluator [this name input referenceOutput output]
       (evals/try-evaluator-impl
        evals-pstate
        try-eval-query
        all-eval-builders-query
        name
        :regular
        {"input"  input
         "referenceOutput" referenceOutput
         "output" output}))

     (tryComparativeEvaluator [this name input referenceOutput outputs]
       (evals/try-evaluator-impl
        evals-pstate
        try-eval-query
        all-eval-builders-query
        name
        :comparative
        {"input"           input
         "referenceOutput" referenceOutput
         "outputs"         outputs}))
     (trySummaryEvaluator [this name exampleRuns]
       (evals/try-evaluator-impl
        evals-pstate
        try-eval-query
        all-eval-builders-query
        name
        :summary
        {"exampleRuns" exampleRuns}))
     (close [this]
       (close! datasets-depot))
     aor-types/AgentManagerInternal
     (add-remote-dataset-internal
       [this dataset-id cluster-conductor-host cluster-conductor-port module-name]
       (let [{error aor-types/AGENT-TOPOLOGY-NAME}
             (foreign-append!
              datasets-depot
              (aor-types/->valid-AddRemoteDataset dataset-id
                                                  cluster-conductor-host
                                                  cluster-conductor-port
                                                  module-name))]
         (when error
           (throw (h/ex-info "Error creating dataset" {:info error})))))
     aor-types/UnderlyingObjects
     (underlying-objects [this]
       {:datasets-depot            datasets-depot
        :datasets-pstate           datasets-pstate
        :evals-pstate              evals-pstate
        :global-actions-depot      global-actions-depot
        :datasets-page-query       datasets-page-query
        :search-examples-query     search-examples-query
        :multi-examples-query      multi-examples-query
        :all-eval-builders-query   all-eval-builders-query
        :all-action-builders-query all-action-builders-query
        :search-evals-query        search-evals-query
        :search-experiments-query  search-experiments-query
        :search-datasets-query     datasets-search-query
        :experiments-results-query experiments-results-query
       }))))

(defn agent-client
  "Gets an agent client for interacting with a specific agent either in a client or within an agent node function.\n
\n
Agent clients provide the interface for invoking agents, streaming data, handling human input, and managing agent executions.\n
\n
When called from within an agent node function, this enables subagent execution:\n
  - Can invoke any other agent in the same module (including the current agent)
  - Enables recursive agent execution patterns
  - Enables mutually recursive agent execution between different agents
  - Subagent calls are tracked and displayed in the UI trace
\n
Args:\n
  - agent-client-fetcher - either an agent manager or agent node
  - agent-name - String name of the agent
\n
Returns:\n
  - Interface for agent interaction\n
\n
Example:\n
<pre>
;; From client code
(let [client (aor/agent-client manager \"my-agent\")]
  (aor/agent-invoke client \"Hello world\"))
;; From within an agent node (subagent execution)
(fn [agent-node input]
  (let [subagent-client (aor/agent-client agent-node \"helper-agent\")
        result (aor/agent-invoke subagent-client input)]
    (aor/result! agent-node result)))
</pre>"
  ^AgentClient [^IFetchAgentClient agent-client-fetcher agent-name]
  (.getAgentClient agent-client-fetcher agent-name))

(defn agent-names
  "Gets the names of all available agents in a module.\n
\n
Args:\n
  - agent-manager - agent manager instance
\n
Returns:\n
  - Set of agent names available in the module
\n
Example:\n
<pre>
(aor/agent-names manager) ; => #{\"ChatAgent\" \"ProcessAgent\" \"ToolsAgent\"}
</pre>"
  [^AgentManager agent-manager]
  (.getAgentNames agent-manager))

(defn agent-invoke
  "Synchronously invokes an agent with the provided arguments.\n
\n
This function blocks until the agent execution completes and returns the final result. For long-running agents, consider using [[agent-initiate]] with [[agent-result]] for better control.\n
\n
Args:\n
  - agent-client - agent client instance
  - args - Arguments to pass to the agent
\n
Returns:\n
  - The final result from the agent execution
\n
Example:\n
<pre>
(aor/agent-invoke client \"Hello world\")
(aor/agent-invoke client {:query \"What is AI?\" :context \"educational\"})
</pre>"
  [^AgentClient agent-client & args]
  (.invoke agent-client (into-array Object args)))

(defn agent-invoke-async
  "Asynchronously invokes an agent with the provided arguments.\n
\n
Returns a CompletableFuture that will complete with the agent's result. This allows for non-blocking agent execution and better resource utilization.\n
\n
Args:\n
  - agent-client - agent client instance
  - args - Arguments to pass to the agent
\n
Returns:\n
  - CompletableFuture - Future that completes with the agent result"
  ^CompletableFuture [^AgentClient agent-client & args]
  (.invokeAsync agent-client (into-array Object args)))

(defn agent-initiate
  "Initiates an agent execution and returns a handle for tracking.\n
\n
This function starts an agent execution but doesn't wait for completion. Use the returned result handle with [[agent-result]], [[agent-next-step]], or streaming functions to interact with the running agent.\n
\n
Args:\n
  - agent-client - agent client instance
  - args - Arguments to pass to the agent
\n
Returns:\n
  - Agent invoke handle for tracking and interacting with the execution
\n
Example:\n
<pre>
(let [invoke (aor/agent-initiate client \"Hello world\")]
  (aor/agent-result client invoke))
</pre>"
  ^AgentInvoke [agent-client & args]
  (apply c/agent-initiate agent-client args))

(defn agent-initiate-async
  "Asynchronously initiates an agent execution and returns a CompletableFuture with a handle for tracking.\n
\n
Args:\n
  - agent-client - agent client instance
  - args - Arguments to pass to the agent
\n
Returns:\n
  - CompletableFuture<AgentInvoke> - Future that completes with the handle"
  ^CompletableFuture [agent-client & args]
  (apply c/agent-initiate-async agent-client args))

(defn agent-invoke-with-context-async
  "Asynchronously invokes an agent with context metadata.\n
\n
Metadata allows attaching custom key-value data to agent executions. Metadata is an additional optional parameter to agent execution, and its also used for analytics. Metadata can be accessed anywhere inside agents by calling [[get-metadata]] within node functions.\n
\n
Args:\n
  - agent-client - agent client instance
  - context - Map with single key :metadata containing a map with string keys and values that are strings, numbers, or booleans
  - args - Arguments to pass to the agent
\n
Returns:\n
  - CompletableFuture - Future that completes with the agent result
\n
Example:\n
<pre>
(aor/agent-invoke-with-context-async client
  {:metadata {\"model\" \"openai\"}}
  \"Hello world\")
</pre>"
  ^CompletableFuture [agent-client context & args]
  (aor-types/invoke-with-context-async-internal agent-client context (into [] args)))

(defn agent-invoke-with-context
  "Synchronously invokes an agent with context metadata.\n
\n
Metadata allows attaching custom key-value data to agent executions. Metadata is an additional optional parameter to agent execution, and its also used for analytics. Metadata can be accessed anywhere inside agents by calling [[get-metadata]] within node functions.\n
\n
Args:\n
  - agent-client - agent client instance
  - context - Map with single key :metadata containing a map with string keys and values that are strings, numbers, or booleans
  - args - Arguments to pass to the agent
\n
Returns:\n
  - The final result from the agent execution
\n
Example:\n
<pre>
(aor/agent-invoke-with-context client
  {:metadata {\"model\" \"openai\"}}
  \"Hello world\")
</pre>"
  [agent-client context & args]
  (.get ^CompletableFuture (apply agent-invoke-with-context-async agent-client context args)))

(defn agent-initiate-with-context-async
  "Asynchronously initiates an agent execution with context metadata.\n
\n
Metadata allows attaching custom key-value data to agent executions. Metadata is an additional optional parameter to agent execution, and its also used for analytics. Metadata can be accessed anywhere inside agents by calling [[get-metadata]] within node functions.\n
\n
Args:\n
  - agent-client - agent client instance
  - context - Map with single key :metadata containing a map with string keys and values that are strings, numbers, or booleans
  - args - Arguments to pass to the agent
\n
Returns:\n
  - CompletableFuture<AgentInvoke> - Future that completes with the AgentInvoke handle
\n
Example:\n
<pre>
(aor/agent-initiate-with-context-async client
  {:metadata {\"model\" \"openai\"}}
  \"Hello world\")
</pre>"
  ^CompletableFuture [agent-client context & args]
  (apply c/agent-initiate-with-context-async agent-client context args))

(defn agent-initiate-with-context
  "Initiates an agent execution with context metadata.\n
\n
Metadata allows attaching custom key-value data to agent executions. Metadata is an additional optional parameter to agent execution, and its also used for analytics. Metadata can be accessed anywhere inside agents by calling [[get-metadata]] within node functions.\n
\n
Args:\n
  - agent-client - agent client instance
  - context - Map with single key :metadata containing a map with string keys and values that are strings, numbers, or booleans
  - args - Arguments to pass to the agent
\n
Returns:\n
  - Agent invoke handle for tracking and interacting with the execution
\n
Example:\n
<pre>
(aor/agent-initiate-with-context client
  {:metadata {\"model\" \"openai\"}}
  \"Hello world\")
</pre>"
  ^AgentInvoke [agent-client context & args]
  (apply c/agent-initiate-with-context agent-client context args))

(defn agent-fork
  "Creates a fork of an agent execution with modified parameters for specific nodes.\n
\n
Forking allows creating execution branches with different inputs for testing variations or exploring alternative execution paths.\n
\n
Args:\n
  - agent-client - agent clint instance
  - invoke - agent invoke handle to fork from
  - node-invoke-id->new-args - Map from node invoke ID (UUID) to new arguments. Node invoke IDs can be found in the trace UI.
\n
Returns:\n
 - Result of the forked execution"
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.fork agent-client invoke node-invoke-id->new-args))

(defn agent-fork-async
  "Asynchronously creates a fork of an agent execution.\n
\n
Forking allows creating execution branches with different inputs for testing variations or exploring alternative execution paths.\n
\n
Args:\n
  - agent-client - AgentClient instance
  - invoke - AgentInvoke instance to fork from
  - node-invoke-id->new-args - Map from node invoke ID (UUID) to new arguments. Node invoke IDs can be found in the trace UI.
\n
Returns:\n
  - CompletableFuture - Future that completes with the result of the forked execution"
  ^CompletableFuture
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.forkAsync agent-client invoke node-invoke-id->new-args))

(defn agent-initiate-fork
  "Initiates a fork of an agent execution without waiting for completion.\n
\n
Args:\n
  - agent-client - AgentClient instance
  - invoke - AgentInvoke instance to fork from
  - node-invoke-id->new-args - Map from node invoke ID (UUID) to new arguments. Node invoke IDs can be found in the trace UI.
\n
Returns:\n
  - New agent invoke handle for the forked execution"
  ^AgentInvoke
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.initiateFork agent-client invoke node-invoke-id->new-args))

(defn agent-initiate-fork-async
  "Asynchronously initiates a fork of an agent execution.\n
\n
Args:\n
  - agent-client - AgentClient instance
  - invoke - AgentInvoke instance to fork from
  - node-invoke-id->new-args - Map from node invoke ID (UUID) to new arguments. Node invoke IDs can be found in the trace UI.
\n
Returns:\n
  - Future that completes with the forked agent invoke handle"
  ^CompletableFuture
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.initiateForkAsync agent-client invoke node-invoke-id->new-args))

(defn agent-next-step
  "Gets the next step in an agent execution for step-by-step control.\n
\n
Returns the next execution step, which is either a human input request or agent result. Check which one by calling [[human-input-request?]]. If it's a result, it's a record with a key `:result` in it. If the agent fails, it will throw an exception.\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - Either a human input request or agent result record
\n
Example:\n
<pre>
(let [step (agent-next-step client invoke)]
  (if (human-input-request? step)
    (do-something-with-human-input step)
    (let [result (:result step)]
      (process-result result))))
</pre>"
  [^AgentClient client agent-invoke]
  (.nextStep client agent-invoke))

(defn agent-next-step-async
  "Asynchronously gets the next step in an agent execution.\n
\n
Returns the next execution step, which is either a human input request or agent result. Check which one by calling [[human-input-request?]]. If it's a result, it's a record with a key `:result` in it. If the agent fails, it will deliver an exception.\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - CompletableFuture - Future that completes with either a human input request or agent result record"
  ^CompletableFuture
  [^AgentClient client agent-invoke]
  (.nextStepAsync client agent-invoke))

(defn set-metadata!
  "Sets metadata on an agent invocation for tracking and debugging.\n
\n
Note: This only affects metadata visible to external clients and analytics. For agent execution within nodes, only the metadata provided at invocation time via [[agent-invoke-with-context]] or [[agent-initiate-with-context]] is accessible via [[get-metadata]].\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
  - key - String key for the metadata
  - value - Value to store (must be a restricted type: int, long, float, double, boolean, or string)
\n
Example:\n
<pre>
(set-metadata! client invoke \"user-id\" \"user-123\")
</pre>"
  [client agent-invoke key value]
  (aor-types/set-metadata-internal! client agent-invoke key value))

(defn remove-metadata!
  "Removes metadata from an agent invocation.\n
\n
Note: This only affects metadata visible to external clients and analytics. For agent execution within nodes, only the metadata provided at invocation time via [[agent-invoke-with-context]] or [[agent-initiate-with-context]] is accessible via [[get-metadata]].\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
  - key - String key of the metadata to remove"
  [^AgentClient client agent-invoke key]
  (.removeMetadata client agent-invoke key))

(defn human-input-request?
  "Checks if an object returned by [[agent-next-step]] is a human input request.\n
\n
Args:\n
  - obj - Object to check
\n
Returns:\n
  - Boolean - True if the object is a human input request"
  [obj]
  (instance? HumanInputRequest obj))

(defn agent-result
  "Gets the final result from an agent execution.\n
\n
Blocks until the agent execution completes and returns the final result. For non-blocking access, use [[agent-result-async]].\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - The final result from the agent execution"
  [agent-client agent-invoke]
  (c/agent-result agent-client agent-invoke))

(defn agent-result-async
  "Asynchronously gets the final result from an agent execution.\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - CompletableFuture - Future that completes with the agent result"
  ^CompletableFuture [agent-client agent-invoke]
  (c/agent-result-async agent-client agent-invoke))

(defn agent-invoke-complete?
  "Checks if an agent invocation has completed.\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - Boolean - True if the invocation has completed"
  [^AgentClient agent-client agent-invoke]
  (.isAgentInvokeComplete agent-client agent-invoke))

(defn agent-stream
  "Creates a streaming subscription to receive data from a specific node.\n
\n
Streams data from the first invocation of the specified node during agent execution. Useful for real-time monitoring and progress tracking.\n
\n
The returned object can be deref'd to get the current streamed chunks (list of chunks).\n
The returned object can have Closeable/close called on it to immediately stop streaming.\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
  - node - String name of the node to stream from
  - callback-fn - Optional callback function for handling chunks. Takes 4 arguments: 'all-chunks new-chunks reset? complete?' where all-chunks is the complete list of chunks so far, new-chunks are the latest chunks, reset? indicates if the stream was reset because the node failed and retried, and complete? indicates if streaming is finished
\n
Returns:\n
  - Streaming subscription for the node.
\n
Example:\n
<pre>
(aor/agent-stream client invoke \"process-node\"
  (fn [all-chunks new-chunks reset? complete?]
    (when reset? (println \"Stream was reset due to node retry\"))
    (doseq [chunk new-chunks]
      (println \"New chunk:\" chunk))
    (when complete? (println \"Streaming finished\"))))
</pre>"
  (^AgentStream [^AgentClient agent-client agent-invoke node]
   (.stream agent-client agent-invoke node))
  (^AgentStream [^AgentClient agent-client agent-invoke node callback-fn]
   (aor-types/stream-internal agent-client agent-invoke node callback-fn)))

(defn agent-stream-specific
  "Creates a streaming subscription to a specific node invocation.\n
\n
Streams data from a particular invocation of a node, useful when\n
a node is invoked multiple times and you want to track a specific one.\n
\n
The returned object can be deref'd to get the current streamed chunks (list of chunks).\n
The returned object can have Closeable/close called on it to immediately stop streaming.\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
  - node - String name of the node to stream from
  - node-invoke-id - UUID of the specific node invocation to stream from. Node invoke IDs can be found in the trace UI.
  - callback-fn - Optional callback function for handling chunks. Takes 4 arguments: 'all-chunks new-chunks reset? complete?' where all-chunks is the complete list of chunks so far, new-chunks are the latest chunks, reset? indicates if the stream was reset because the node failed and retried, and complete? indicates if streaming is finished
\n
Returns:\n
  - Streaming subscription for the specific node invocation"
  (^AgentStream
   [^AgentClient agent-client agent-invoke node node-invoke-id]
   (.streamSpecific agent-client agent-invoke node node-invoke-id))
  (^AgentStream
   [^AgentClient agent-client agent-invoke node node-invoke-id callback-fn]
   (aor-types/stream-specific-internal agent-client
                                       agent-invoke
                                       node
                                       node-invoke-id
                                       callback-fn)))

(defn agent-stream-all
  "Creates a streaming subscription to all invocations of a specific node.\n
\n
Streams data from all invocations of the specified node, with chunks\n
grouped by invocation ID. Useful for monitoring parallel processing.\n
\n
The returned object can be deref'd to get the current streamed chunks (map from node invoke ID to chunks).\n
The returned object can have Closeable/close called on it to immediately stop streaming.\n
\n
Args:\n
  - agent-client - agent client instance
  - agent-invoke - agent invoke handle
  - node - String name of the node to stream from
  - callback-fn - Optional callback function for handling chunks. Takes 4 arguments: 'all-chunks new-chunks reset-invoke-ids complete?' where all-chunks is a map from node invoke ID to complete list of chunks, new-chunks are the latest chunks grouped by invoke ID, reset-invoke-ids indicates if any nodes invokes in this iteration failed and retried, and complete? indicates if streaming is finished across all nodes invocations for the full agent execution.
\n
Returns:\n
  - Streaming subscription for all node invocations
\n
Example:\n
<pre>
(aor/agent-stream-all client invoke \"process-node\"
  (fn [all-chunks new-chunks reset-invoke-ids complete?]
    (when (not (empty? reset-invoke-ids)) (println \"Stream was reset for one or more node invokes\"))
    (doseq [[invoke-id chunks] new-chunks]
      (println \"New chunks for invocation\" invoke-id \":\" chunks))
    (when complete? (println \"Streaming finished\"))))
</pre>"
  (^AgentStreamByInvoke [^AgentClient agent-client agent-invoke node]
   (.streamAll agent-client agent-invoke node))
  (^AgentStreamByInvoke
   [^AgentClient agent-client agent-invoke node callback-fn]
   (aor-types/stream-all-internal agent-client agent-invoke node callback-fn)))

(defn agent-stream-reset-info
  "Gets reset information from a streaming subscription.\n
\n
Returns reset information based on the stream type:
- For streams created with [[agent-stream]] or [[agent-stream-specific]]: Number of resets
- For streams created with [[agent-stream-all]]: Map from node invoke ID to reset count
\n
Resets occur due to nodes failing and retrying.\n
\n
Args:\n
  - stream - return from [[agent-stream]], [[agent-stream-all]], or [[agent-stream-specific]]
\n
Returns:\n
  - Number or Map - Reset count for single streams, or map of invoke ID to reset count for stream-all"
  [stream]
  (cond (instance? AgentStream stream)
        (.numResets ^AgentStream stream)

        (instance? AgentStreamByInvoke stream)
        (.numResetsByInvoke ^AgentStreamByInvoke stream)

        :else (throw (h/ex-info "Unknown type" {:class (class stream)}))))

(defn pending-human-inputs
  "Gets all pending human input requests for an agent invocation handle.\n
\n
Returns a collection of request objects that are waiting for human responses to continue agent execution.\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:
  - Collection - Pending human input requests. Each request has fields `:node` and `:prompt` to get the node name making the request and the prompt.
\n
Example:\n
<pre>
(let [requests (aor/pending-human-inputs client invoke)]
  (doseq [request requests]
    (aor/provide-human-input client request \"yes\")))
</pre>"
  [^AgentClient client agent-invoke]
  (.pendingHumanInputs client agent-invoke))

(defn pending-human-inputs-async
  "Asynchronously gets all pending human input requests for an agent invocation.\n
\n
Args:\n
  - client - agent client instance
  - agent-invoke - agent invoke handle
\n
Returns:\n
  - CompletableFuture - Future with pending requests. Each request has fields `:node` and `:prompt` to get the node name making the request and the prompt."
  ^CompletableFuture
  [^AgentClient client agent-invoke]
  (.pendingHumanInputsAsync client agent-invoke))

(defn provide-human-input
  "Provides a human response to a pending human input request.\n
\n
This function sends a response to continue agent execution that was paused waiting for human input.\n
\n
Args:\n
  - client - agent client instance
  - request - request object from [[pending-human-inputs]] or [[agent-next-step]]
  - response - String response from the human
\n
Example:\n
<pre>
(aor/provide-human-input agent-client request \"yes\")
</pre>"
  [^AgentClient client request response]
  (.provideHumanInput client request response))

(defn provide-human-input-async
  "Asynchronously provides a human response to a pending human input request.\n
\n
Args:\n
  - client - agent client instance
  - request - request object from [[pending-human-inputs]] or [[agent-next-step]]
  - response - String response from the human
\n
Returns:\n
  - CompletableFuture - Future that completes when the response is processed"
  ^CompletableFuture
  [^AgentClient client request response]
  (.provideHumanInputAsync client request response))


(defn create-dataset!
  "Creates a new dataset for agent testing and evaluation.\n
\n
Datasets are collections of input/output examples used for testing agent performance, running experiments, and regression testing.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name for the dataset
  - options - Optional map with configuration:
    - :description - String description of the dataset
    - :input-json-schema - JSON schema for input validation
    - :output-json-schema - JSON schema for output validation
\n
Returns:\n
  - UUID of the created dataset
\n
Example:\n
<pre>
(aor/create-dataset! agent-manager \"test-cases\"
  {:description \"Basic test cases\"
   :input-json-schema {\"type\" \"object\" \"properties\" {\"query\" {\"type\" \"string\"}} \"required\" [\"query\"]}
   :output-json-schema {\"type\" \"string\"}})
</pre>"
  ([manager name] (create-dataset! manager name nil))
  ([^AgentManager manager name options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:description        h/any-spec
                         :input-json-schema  h/any-spec
                         :output-json-schema h/any-spec})
   (.createDataset manager
                   name
                   (:description options)
                   (:input-json-schema options)
                   (:output-json-schema options))))

(defn set-dataset-name!
  "Updates the name of an existing dataset.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - name - String new name for the dataset"
  [^AgentManager manager dataset-id name]
  (.setDatasetName manager dataset-id name))

(defn set-dataset-description!
  "Updates the description of an existing dataset.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - description - String new description for the dataset"
  [^AgentManager manager dataset-id description]
  (.setDatasetDescription manager dataset-id description))

(defn destroy-dataset!
  "Permanently deletes a dataset and all its examples.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset to delete"
  [^AgentManager manager dataset-id]
  (.destroyDataset manager dataset-id))

(defn add-dataset-example-async!
  "Asynchronously adds an example to a dataset. Fails and throws exception of input or output violates the dataset's JSON schemas.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - input - Input data for the example
  - options - Optional map with configuration:
    - :reference-output - Expected output for the example
    - :tags - Set of tags for categorization
\n
Returns:\n
  - CompletableFuture<UUID> - Future that completes with the example UUID"
  (^CompletableFuture [manager dataset-id input]
   (c/add-dataset-example-async! manager dataset-id input))
  (^CompletableFuture [^AgentManager manager dataset-id input options]
   (c/add-dataset-example-async! manager dataset-id input options)))

(defn add-dataset-example!
  "Adds an example to a dataset for testing and evaluation. Fails and throws exception of input or output violates the dataset's JSON schemas.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - input - Input data for the example
  - options - Optional map with configuration (same as add-dataset-example-async!)
\n
Returns:\n
  - UUID of the added example
\n
Example:\n
<pre>
(aor/add-dataset-example! agent-manager dataset-id
  {:query \"What is AI?\" :context \"educational\"}
  {:reference-output \"AI is artificial intelligence...\"
   :tags #{\"basic\" \"ai\"}})
</pre>"
  ([manager dataset-id input]
   (c/add-dataset-example! manager dataset-id input))
  ([^AgentManager manager dataset-id input options]
   (c/add-dataset-example! manager dataset-id input options)))

(defn set-dataset-example-input!
  "Updates the input data for a specific dataset example.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - example-id - UUID of the example
  - input - New input data for the example"
  ([manager dataset-id example-id input]
   (set-dataset-example-input! manager dataset-id example-id input nil))
  ([^AgentManager manager dataset-id example-id input options]
   ;; types are validated by Java API
   (h/validate-options! {:dataset-id dataset-id :example-id example-id}
                        options
                        {:snapshot h/any-spec})
   (.setDatasetExampleInput manager
                            dataset-id
                            (:snapshot options)
                            example-id
                            input)))

(defn set-dataset-example-reference-output!
  "Updates the reference output for a specific dataset example.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - example-id - UUID of the example
  - reference-output - New reference output for the example"
  ([manager dataset-id example-id reference-output]
   (set-dataset-example-reference-output! manager
                                          dataset-id
                                          example-id
                                          reference-output
                                          nil))
  ([^AgentManager manager dataset-id example-id reference-output options]
   ;; types are validated by Java API
   (h/validate-options! {:dataset-id dataset-id :example-id example-id}
                        options
                        {:snapshot h/any-spec})
   (.setDatasetExampleReferenceOutput manager
                                      dataset-id
                                      (:snapshot options)
                                      example-id
                                      reference-output)))

(defn remove-dataset-example!
  "Removes a specific example from a dataset.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - example-id - UUID of the example to remove"
  ([manager dataset-id example-id]
   (remove-dataset-example! manager dataset-id example-id nil))
  ([^AgentManager manager dataset-id example-id options]
   ;; types are validated by Java API
   (h/validate-options! {:dataset-id dataset-id :example-id example-id}
                        options
                        {:snapshot h/any-spec})
   (.removeDatasetExample manager
                          dataset-id
                          (:snapshot options)
                          example-id)))

(defn add-dataset-example-tag!
  "Adds a tag to a specific dataset example for categorization.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - example-id - UUID of the example
  - tag - String tag to add"
  ([manager dataset-id example-id tag]
   (add-dataset-example-tag! manager dataset-id example-id tag nil))
  ([^AgentManager manager dataset-id example-id tag options]
   ;; types are validated by Java API
   (h/validate-options! {:dataset-id dataset-id :example-id example-id :tag tag}
                        options
                        {:snapshot h/any-spec})
   (.addDatasetExampleTag manager
                          dataset-id
                          (:snapshot options)
                          example-id
                          tag)))

(defn remove-dataset-example-tag!
  "Removes a tag from a specific dataset example.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - example-id - UUID of the example
  - tag - String tag to remove"
  ([manager dataset-id example-id tag]
   (remove-dataset-example-tag! manager dataset-id example-id tag nil))
  ([^AgentManager manager dataset-id example-id tag options]
   ;; types are validated by Java API
   (h/validate-options! {:dataset-id dataset-id :example-id example-id :tag tag}
                        options
                        {:snapshot h/any-spec})
   (.removeDatasetExampleTag manager
                             dataset-id
                             (:snapshot options)
                             example-id
                             tag)))

(defn snapshot-dataset!
  "Creates a snapshot of a dataset at its current state.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - from-snapshot - String name of the source snapshot (or nil for current)
  - to-snapshot - String name for the new snapshot"
  [^AgentManager manager dataset-id from-snapshot to-snapshot]
  (.snapshotDataset manager dataset-id from-snapshot to-snapshot))

(defn remove-dataset-snapshot!
  "Removes a specific snapshot from a dataset.\n
\n
Args:\n
  - manager - agent manager instance
  - dataset-id - UUID of the dataset
  - snapshot-name - String name of the snapshot to remove"
  [^AgentManager manager dataset-id snapshot-name]
  (.removeDatasetSnapshot manager dataset-id snapshot-name))

(defn search-datasets
  "Searches for datasets by name or description.\n
\n
Args:\n
  - manager - agent manager instance
  - search-string - String to search for in names and descriptions
  - limit - Maximum number of results to return
\n
Returns:\n
  - Map - Map from dataset UUID to dataset name"
  [^AgentManager manager search-string limit]
  (.searchDatasets manager search-string limit))

(defn create-evaluator!
  "Creates an evaluator instance from a builder for measuring agent performance in experiments or actions.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name for the evaluator
  - builder-name - String name of the evaluator builder (declared in topology or built-in)
  - params - Map of parameters for the evaluator. Parameters are a map from parameter name to parameter value, both strings.
  - description - String description of what the evaluator measures
  - options - Optional map with configuration:
    - :input-json-path - JSON path to extract input from runs
    - :output-json-path - JSON path to extract output from runs
    - :reference-output-json-path - JSON path to extract reference output from runs
\n
Example:\n
<pre>
(aor/create-evaluator! agent-manager \"brief-check\" \"aor/conciseness\"
  {\"threshold\" \"150\"} \"Checks if response is under 150 characters\")
</pre>"
  ([^AgentManager manager name builder-name params description]
   (create-evaluator! manager name builder-name params description nil))
  ([^AgentManager manager name builder-name params description options]
   (h/validate-options! name
                        options
                        {:input-json-path  h/string-spec
                         :output-json-path h/string-spec
                         :reference-output-json-path h/string-spec})
   (let [joptions (CreateEvaluatorOptions.)]
     (set! (.inputJsonPath joptions) (:input-json-path options))
     (set! (.outputJsonPath joptions) (:output-json-path options))
     (set! (.referenceOutputJsonPath joptions)
           (:reference-output-json-path options))
     (.createEvaluator manager
                       name
                       builder-name
                       params
                       description
                       joptions))))

(defn remove-evaluator!
  "Removes an evaluator from the system.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name of the evaluator to remove"
  [^AgentManager manager name]
  (.removeEvaluator manager name))

(defn search-evaluators
  "Searches for evaluators by name or description.\n
\n
Args:\n
  - manager - agent manager instance
  - search-string - String to search for in evaluator names
\n
Returns:\n
  - Set - Set of matching evaluator names"
  [^AgentManager manager search-string]
  (.searchEvaluators manager search-string))

(defn try-evaluator
  "Tests an evaluator on a single sample input / reference output / output.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name of the evaluator
  - input - Input data for the evaluation
  - reference-output - Reference output for comparison
  - output - Actual output to evaluate
\n
Returns:\n
  - Map - Result scores from score name to score value"
  [^AgentManager manager name input reference-output output]
  (.tryEvaluator manager name input reference-output output))

(defn try-comparative-evaluator
  "Tests a comparative evaluator on multiple outputs.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name of the evaluator
  - input - Input data for the evaluation
  - reference-output - Reference output for comparison
  - outputs - Collection of actual outputs to compare
\n
Returns:\n
  - Map - Comparative evaluation result, a map of score name to score value"
  [^AgentManager manager name input reference-output outputs]
  (.tryComparativeEvaluator manager name input reference-output outputs))

(defn mk-example-run
  "Creates an example run for summary evaluation with [[try-summary-evaluator]].\n
\n
Args:\n
  - input - Input data for the example
  - reference-output - Expected output
  - output - Actual output
\n
Returns:\n
  - Example run instance for summary evaluation"
  [input reference-output output]
  (aor-types/->ExampleRunImpl input reference-output output))

(defn try-summary-evaluator
  "Tests a summary evaluator on a collection of example runs.\n
\n
Args:\n
  - manager - agent manager instance
  - name - String name of the evaluator
  - example-runs - Collection of example runs created with [[mk-example-run]]
\n
Returns:\n
  - Map - Summary evaluation result with aggregate metrics, a map from score name to score value"
  [^AgentManager manager name example-runs]
  (.trySummaryEvaluator manager name example-runs))

(defn start-ui
  "Starts the Agent-o-rama web UI for monitoring and debugging.\n
\n
The UI provides real-time visualization of agent execution, traces, datasets, experiments, and telemetry. Accessible via web browser.\n
\n
Args:\n
  - ipc - In-Process Cluster instance
  - options - Optional map with configuration:
    - :port - Port number for the UI (default 1974)
    - :host - Host address to bind to (default \"localhost\")
\n
Returns:\n
  - UI instance that should be closed when done
\n
Example:\n
<pre>
(with-open [ui (aor/start-ui ipc {:port 8080})]
  (run-agents))
</pre>"
  (^AutoCloseable [ipc] (start-ui ipc nil))
  (^AutoCloseable [ipc options]
   (let [start-fn (requiring-resolve
                   'com.rpl.agent-o-rama.impl.ui.core/start-ui)]
     (start-fn ipc options))))

(defn stop-ui
  "Stops the Agent-o-rama web UI started with [[start-ui]]"
  []
  (let [stop-fn (requiring-resolve 'com.rpl.agent-o-rama.impl.ui.core/stop-ui)]
    (stop-fn)))
