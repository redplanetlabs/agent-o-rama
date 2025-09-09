(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.core :as i]
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
    AgentsTopology
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

(defn agents-topology
  [setup topologies]
  (let [^StreamTopology stream-topology (stream-topology
                                         topologies
                                         aor-types/AGENTS-TOPOLOGY-NAME)
        mb-topology            (microbatch-topology
                                topologies
                                aor-types/AGENTS-MB-TOPOLOGY-NAME)
        defined?-vol           (volatile! false)
        agents-vol             (volatile! {})
        mirror-agents-vol      (volatile! {})
        store-info-vol         (volatile! {})
        declared-objects-vol   (volatile! {})
        evaluator-builders-vol (volatile! {})]
    (reify
     AgentsTopology
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
       (aor-types/declare-java-evaluator-builer
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
       (aor-types/declare-java-evaluator-builer
        this
        :comparative
        name
        description
        builder-jfn
        options))
     (declareSummaryEvaluatorBuilder [this name description builder-jfn]
       (.declareSummaryEvaluatorBuilder this name description builder-jfn nil))
     (declareSummaryEvaluatorBuilder [this name description builder-jfn options]
       (aor-types/declare-java-evaluator-builer
        this
        :summary
        name
        description
        builder-jfn
        options))
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
         (throw (h/ex-info "Agents topology already defined" {})))
       (vreset! defined?-vol true)
       (exp/define-experiments-agent! this)
       (i/define-agents!
        setup
        topologies
        stream-topology
        mb-topology
        @agents-vol
        @mirror-agents-vol
        @store-info-vol
        @declared-objects-vol
        @evaluator-builders-vol))
     aor-types/AgentsTopologyInternal
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
    )))

(defn underlying-stream-topology
  [^AgentsTopology at]
  (.getStreamTopology at))

(defn define-agents!
  [^AgentsTopology at]
  (.define at))

(defn declare-key-value-store
  [^AgentsTopology agents-topology name key-class val-class]
  (.declareKeyValueStore agents-topology name key-class val-class))

(defn declare-document-store
  [^AgentsTopology agents-topology name key-class & key-val-classes]
  (.declareDocumentStore agents-topology
                         name
                         key-class
                         (into-array Object key-val-classes)))

(defn declare-pstate-store
  [^AgentsTopology agents-topology name schema]
  (declare-pstate* (.getStreamTopology agents-topology) (symbol name) schema))

(defn declare-agent-object
  [^AgentsTopology agents-topology name val]
  (.declareAgentObject agents-topology name val))

(defn declare-agent-object-builder
  ([agents-topology name afn]
   (declare-agent-object-builder agents-topology name afn nil))
  ([agents-topology name afn options]
   (aor-types/declare-agent-object-builder-internal agents-topology
                                                    name
                                                    afn
                                                    options)))


(defn declare-evaluator-builder
  ([agents-topology name description builder-fn]
   (declare-evaluator-builder agents-topology name description builder-fn nil))
  ([agents-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agents-topology
                                                 :regular
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-comparative-evaluator-builder
  ([agents-topology name description builder-fn]
   (declare-comparative-evaluator-builder agents-topology
                                          name
                                          description
                                          builder-fn
                                          nil))
  ([agents-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agents-topology
                                                 :comparative
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-summary-evaluator-builder
  ([agents-topology name description builder-fn]
   (declare-summary-evaluator-builder agents-topology
                                      name
                                      description
                                      builder-fn
                                      nil))
  ([agents-topology name description builder-fn options]
   (aor-types/declare-evaluator-builder-internal agents-topology
                                                 :summary
                                                 name
                                                 description
                                                 builder-fn
                                                 options)))

(defn declare-cluster-agent
  [^AgentsTopology agents-topology local-name module-name agent-name]
  (.declareClusterAgent agents-topology local-name module-name agent-name))

(defn setup-object-name
  [^AgentObjectSetup setup]
  (.getObjectName setup))

(defn new-agent
  [agents-topology name]
  (c/new-agent agents-topology name))

(defn node
  [agent-graph name output-nodes-spec node-fn]
  (c/node agent-graph name output-nodes-spec node-fn))

(defn agg-start-node
  [agent-graph name output-nodes-spec node-fn]
  (c/agg-start-node agent-graph name output-nodes-spec node-fn))

(defn agg-node
  [agent-graph name output-nodes-spec agg node-fn]
  (c/agg-node agent-graph name output-nodes-spec agg node-fn))

(defn set-update-mode
  [^AgentGraph agent-graph mode]
  (.setUpdateMode
   agent-graph
   (graph/convert-update-mode->java mode)))

(defmacro multi-agg
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
  [agent-node node & args]
  (apply c/emit! agent-node node args))

(defn result!
  [agent-node val]
  (c/result! agent-node val))

(defn get-store
  [^AgentNode agent-node name]
  (.getStore agent-node name))

(defn get-agent-object
  [^AgentObjectFetcher fetch name]
  (.getAgentObject fetch name))

(defn stream-chunk!
  [^AgentNode agent-node chunk]
  (.streamChunk agent-node chunk))

(defn record-nested-op!
  [agent-node nested-op-type start-time-millis finish-time-millis info-map]
  (anode/record-nested-op!-impl agent-node
                                nested-op-type
                                start-time-millis
                                finish-time-millis
                                info-map))

(defn get-human-input
  [^AgentNode agent-node prompt]
  (.getHumanInput agent-node prompt))

(defn- parse-map-options
  [[arg1 & rest-args :as args]]
  (if (map? arg1) [arg1 rest-args] [{} args]))

(defmacro agentmodule
  [& args]
  (let [[options [[agent-topology-sym] & body]] (parse-map-options args)]
    `(module ~options
       [setup# topologies#]
       (let [~agent-topology-sym (agents-topology setup# topologies#)]
         ~@body
         (define-agents! ~agent-topology-sym)
       ))))

(defmacro defagentmodule
  [sym & args]
  (let [[options args] (parse-map-options args)
        name-default   (str sym)]
    `(def ~sym
       (agentmodule ~(merge {:module-name name-default} options) ~@args))))

(defn agent-manager
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
       (let [agents-set           (foreign-invoke-query agent-names-query)
             _ (when-not (contains? agents-set agentName)
                 (throw (h/ex-info "Agent does not exist"
                                   {:available  agents-set
                                    :agent-name agentName})))
             agent-depot          (foreign-depot cluster
                                                 module-name
                                                 (po/agent-depot-name
                                                  agentName))
             human-depot          (foreign-depot cluster
                                                 module-name
                                                 (po/agent-human-depot-name
                                                  agentName))
             agent-config-depot   (foreign-depot cluster
                                                 module-name
                                                 (po/agent-config-depot-name
                                                  agentName))
             config-pstate        (foreign-pstate
                                   cluster
                                   module-name
                                   (po/agent-config-task-global-name
                                    agentName))
             root-pstate          (foreign-pstate
                                   cluster
                                   module-name
                                   (po/agent-root-task-global-name agentName))
             graph-history-pstate (foreign-pstate
                                   cluster
                                   module-name
                                   (po/graph-history-task-global-name
                                    agentName))
             tracing-query        (foreign-query
                                   cluster
                                   module-name
                                   (queries/tracing-query-name
                                    agentName))
             invokes-page-query   (foreign-query
                                   cluster
                                   module-name
                                   (queries/agent-get-invokes-page-query-name
                                    agentName))

             current-graph-query  (foreign-query
                                   cluster
                                   module-name
                                   (queries/agent-get-current-graph-name
                                    agentName))]
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
            (.thenApply
             (foreign-append-async!
              agent-depot
              (aor-types/->AgentInitiate
               (vec args)
               (h/current-time-millis)
               nil))
             (h/cf-function [{[agent-task-id agent-id]
                              aor-types/AGENTS-TOPOLOGY-NAME}]
               (aor-types/->AgentInvokeImpl agent-task-id agent-id)
             )))

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
                              aor-types/AGENTS-TOPOLOGY-NAME}]
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
          aor-types/UnderlyingObjects
          (underlying-objects [this]
            {:agent-depot          agent-depot
             :agent-config-depot   agent-config-depot
             :config-pstate        config-pstate
             :root-pstate          root-pstate
             :graph-history-pstate graph-history-pstate
             :tracing-query        tracing-query
             :invokes-page-query   invokes-page-query
             :current-graph-query  current-graph-query
            })
         )))
     (createDataset [this name description inputJsonSchema outputJsonSchema]
       (let [uuid (h/random-uuid7)

             {error aor-types/AGENTS-TOPOLOGY-NAME}
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
               (.source options)
               (.linkedTrace options)
              ))
             (.thenApply
              (h/cf-function [{error aor-types/AGENTS-TOPOLOGY-NAME}]
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
       (let [{error aor-types/AGENTS-TOPOLOGY-NAME}
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
       (let [{error aor-types/AGENTS-TOPOLOGY-NAME}
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
     (setDatasetExampleSource [this datasetId snapshotName exampleId source]
       (foreign-append!
        datasets-depot
        (aor-types/->valid-UpdateDatasetExample datasetId
                                                snapshotName
                                                exampleId
                                                :source
                                                source)))
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
       (let [{error aor-types/AGENTS-TOPOLOGY-NAME}
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
       (let [{error aor-types/AGENTS-TOPOLOGY-NAME}
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
        :search-evals-query        search-evals-query
        :search-experiments-query  search-experiments-query
        :experiments-results-query experiments-results-query
       }))))

(defn agent-client
  ^AgentClient [^IFetchAgentClient agent-client-fetcher agent-name]
  (.getAgentClient agent-client-fetcher agent-name))

(defn agent-names
  [^AgentManager agent-manager]
  (.getAgentNames agent-manager))

(defn agent-invoke
  [^AgentClient agent-client & args]
  (.invoke agent-client (into-array Object args)))

(defn agent-invoke-async
  ^CompletableFuture [^AgentClient agent-client & args]
  (.invokeAsync agent-client (into-array Object args)))

(defn agent-initiate
  ^AgentInvoke [agent-client & args]
  (apply c/agent-initiate agent-client args))

(defn agent-initiate-async
  ^CompletableFuture [agent-client & args]
  (apply c/agent-initiate-async agent-client args))

(defn agent-fork
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.fork agent-client invoke node-invoke-id->new-args))

(defn agent-fork-async
  ^CompletableFuture
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.forkAsync agent-client invoke node-invoke-id->new-args))

(defn agent-initiate-fork
  ^AgentInvoke
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.initiateFork agent-client invoke node-invoke-id->new-args))

(defn agent-initiate-fork-async
  ^CompletableFuture
  [^AgentClient agent-client ^AgentInvoke invoke node-invoke-id->new-args]
  (.initiateForkAsync agent-client invoke node-invoke-id->new-args))

(defn agent-next-step
  [^AgentClient client agent-invoke]
  (.nextStep client agent-invoke))

(defn agent-next-step-async
  ^CompletableFuture
  [^AgentClient client agent-invoke]
  (.nextStepAsync client agent-invoke))

(defn human-input-request?
  [obj]
  (instance? HumanInputRequest obj))

(defn agent-result
  [agent-client agent-invoke]
  (c/agent-result agent-client agent-invoke))

(defn agent-result-async
  ^CompletableFuture [agent-client agent-invoke]
  (c/agent-result-async agent-client agent-invoke))

(defn agent-invoke-complete?
  [^AgentClient agent-client agent-invoke]
  (.isAgentInvokeComplete agent-client agent-invoke))

(defn agent-stream
  (^AgentStream [^AgentClient agent-client agent-invoke node]
   (.stream agent-client agent-invoke node))
  (^AgentStream [^AgentClient agent-client agent-invoke node callback-fn]
   (aor-types/stream-internal agent-client agent-invoke node callback-fn)))

(defn agent-stream-specific
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
  (^AgentStreamByInvoke [^AgentClient agent-client agent-invoke node]
   (.streamAll agent-client agent-invoke node))
  (^AgentStreamByInvoke
   [^AgentClient agent-client agent-invoke node callback-fn]
   (aor-types/stream-all-internal agent-client agent-invoke node callback-fn)))

(defn agent-stream-reset-info
  [stream]
  (cond (instance? AgentStream stream)
        (.numResets ^AgentStream stream)

        (instance? AgentStreamByInvoke stream)
        (.numResetsByInvoke ^AgentStreamByInvoke stream)

        :else (throw (h/ex-info "Unknown type" {:class (class stream)}))))

(defn pending-human-inputs
  [^AgentClient client agent-invoke]
  (.pendingHumanInputs client agent-invoke))

(defn pending-human-inputs-async
  ^CompletableFuture
  [^AgentClient client agent-invoke]
  (.pendingHumanInputsAsync client agent-invoke))

(defn provide-human-input
  [^AgentClient client request response]
  (.provideHumanInput client request response))

(defn provide-human-input-async
  ^CompletableFuture
  [^AgentClient client request response]
  (.provideHumanInputAsync client request response))


(defn create-dataset!
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
  [^AgentManager manager dataset-id name]
  (.setDatasetName manager dataset-id name))

(defn set-dataset-description!
  [^AgentManager manager dataset-id description]
  (.setDatasetDescription manager dataset-id description))

(defn destroy-dataset!
  [^AgentManager manager dataset-id]
  (.destroyDataset manager dataset-id))

(defn add-dataset-example-async!
  (^CompletableFuture [manager dataset-id input]
   (add-dataset-example-async! manager dataset-id input nil))
  (^CompletableFuture [^AgentManager manager dataset-id input options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot         h/any-spec
                         :reference-output h/any-spec
                         :tags             h/any-spec
                         :source           h/any-spec
                         :linked-trace     h/any-spec})
   (let [joptions (AddDatasetExampleOptions.)]
     (set! (.snapshotName joptions) (:snapshot options))
     (set! (.referenceOutput joptions) (:reference-output options))
     (set! (.tags joptions) (:tags options))
     (set! (.source joptions) (:source options))
     (set! (.linkedTrace joptions) (:linked-trace options))
     (.addDatasetExampleAsync manager
                              dataset-id
                              input
                              joptions))))

(defn add-dataset-example!
  ([manager dataset-id input]
   (.get (add-dataset-example-async! manager dataset-id input)))
  ([^AgentManager manager dataset-id input options]
   (.get (add-dataset-example-async! manager dataset-id input options))))

(defn set-dataset-example-input!
  ([manager dataset-id example-id input]
   (set-dataset-example-input! manager dataset-id example-id input nil))
  ([^AgentManager manager dataset-id example-id input options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.setDatasetExampleInput manager
                            dataset-id
                            (:snapshot options)
                            example-id
                            input)))

(defn set-dataset-example-reference-output!
  ([manager dataset-id example-id reference-output]
   (set-dataset-example-reference-output! manager
                                          dataset-id
                                          example-id
                                          reference-output
                                          nil))
  ([^AgentManager manager dataset-id example-id reference-output options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.setDatasetExampleReferenceOutput manager
                                      dataset-id
                                      (:snapshot options)
                                      example-id
                                      reference-output)))

(defn set-dataset-example-source!
  ([manager dataset-id example-id source]
   (set-dataset-example-source! manager
                                dataset-id
                                example-id
                                source
                                nil))
  ([^AgentManager manager dataset-id example-id source options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.setDatasetExampleSource manager
                             dataset-id
                             (:snapshot options)
                             example-id
                             source)))

(defn remove-dataset-example!
  ([manager dataset-id example-id]
   (remove-dataset-example! manager dataset-id example-id nil))
  ([^AgentManager manager dataset-id example-id options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.removeDatasetExample manager
                          dataset-id
                          (:snapshot options)
                          example-id)))

(defn add-dataset-example-tag!
  ([manager dataset-id example-id tag]
   (add-dataset-example-tag! manager dataset-id example-id tag nil))
  ([^AgentManager manager dataset-id example-id tag options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.addDatasetExampleTag manager
                          dataset-id
                          (:snapshot options)
                          example-id
                          tag)))

(defn remove-dataset-example-tag!
  ([manager dataset-id example-id tag]
   (remove-dataset-example-tag! manager dataset-id example-id tag nil))
  ([^AgentManager manager dataset-id example-id tag options]
   ;; types are validated by Java API
   (h/validate-options! name
                        options
                        {:snapshot h/any-spec})
   (.removeDatasetExampleTag manager
                             dataset-id
                             (:snapshot options)
                             example-id
                             tag)))

(defn snapshot-dataset!
  [^AgentManager manager dataset-id from-snapshot to-snapshot]
  (.snapshotDataset manager dataset-id from-snapshot to-snapshot))

(defn remove-dataset-snapshot!
  [^AgentManager manager dataset-id snapshot-name]
  (.removeDatasetSnapshot manager dataset-id snapshot-name))

(defn search-datasets
  [^AgentManager manager search-string limit]
  (.searchDatasets manager search-string limit))

(defn create-evaluator!
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
  [^AgentManager manager name]
  (.removeEvaluator manager name))

(defn search-evaluators
  [^AgentManager manager search-string]
  (.searchEvaluators manager search-string))

(defn try-evaluator
  [^AgentManager manager name input reference-output output]
  (.tryEvaluator manager name input reference-output output))

(defn try-comparative-evaluator
  [^AgentManager manager name input reference-output outputs]
  (.tryComparativeEvaluator manager name input reference-output outputs))

(defn mk-example-run
  [input reference-output output]
  (aor-types/->ExampleRunImpl input reference-output output))

(defn try-summary-evaluator
  [^AgentManager manager name example-runs]
  (.trySummaryEvaluator manager name example-runs))

(defn start-ui
  (^AutoCloseable [ipc] (start-ui ipc nil))
  (^AutoCloseable [ipc options]
   (let [start-fn (requiring-resolve
                   'com.rpl.agent-o-rama.impl.ui.core/start-ui)]
     (start-fn ipc options))))

(defn stop-ui
  []
  (let [stop-fn (requiring-resolve 'com.rpl.agent-o-rama.impl.ui.core/stop-ui)]
    (stop-fn)))
