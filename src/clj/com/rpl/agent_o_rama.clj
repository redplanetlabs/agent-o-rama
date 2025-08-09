(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.multi-agg :as ma]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agentorama
    AgentClient
    AgentClient$StreamAllCallback
    AgentClient$StreamCallback
    AgentGraph
    AgentInvoke
    AgentManager
    AgentNode
    AgentObjectSetup
    AgentsTopology
    AgentStream
    AgentStreamByInvoke
    HumanInputRequest
    MultiAgg$Impl
    UpdateMode]
   [com.rpl.agentorama.impl
    IFetchAgentClient
    IFetchAgentObject]
   [com.rpl.rama
    PState$Declaration
    PState$Schema]
   [com.rpl.rama.module
    StreamTopology]
   [com.rpl.rama.ops
    RamaAccumulatorAgg
    RamaCombinerAgg]
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
        mb-topology          (microbatch-topology
                              topologies
                              aor-types/AGENTS-MB-TOPOLOGY-NAME)
        defined?-vol         (volatile! false)
        agents-vol           (volatile! {})
        mirror-agents-vol    (volatile! {})
        store-info-vol       (volatile! {})
        declared-objects-vol (volatile! {})]
    (reify
     AgentsTopology
     (newAgent [this name]
       (check-unique-agent-name! agents-vol mirror-agents-vol name)
       (let [ret (graph/mk-agent-graph)]
         (vswap! agents-vol assoc name ret)
         ret))
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
       (i/define-agents!
        setup
        topologies
        stream-topology
        mb-topology
        @agents-vol
        @mirror-agents-vol
        @store-info-vol
        @declared-objects-vol))
     aor-types/AgentsTopologyInternal
     (declare-agent-object-builder-internal [this name afn options]
       (when-not (ifn? afn)
         (throw (h/ex-info "Object builder must be a function"
                           {:actual-type (class afn)})))
       (when (contains? @declared-objects-vol name)
         (throw (h/ex-info "Object already declared" {:name name})))
       (let [invalid-opts (set/difference (-> options
                                              keys
                                              set)
                                          #{:thread-safe?
                                            :auto-tracing?
                                            :worker-object-limit})
             full-options (merge {:thread-safe?        false
                                  :auto-tracing?       true
                                  :worker-object-limit 1000}
                                 options)]
         (when-not (empty? invalid-opts)
           (throw (h/ex-info "Invalid agent object options"
                             {:name name :invalid-keys invalid-opts})))
         (h/validate-option! name full-options :thread-safe? boolean?)
         (h/validate-option! name full-options :auto-tracing? boolean?)
         (h/validate-option! name
                             full-options
                             :worker-object-limit
                             integer?
                             pos?)
         (vswap! declared-objects-vol
                 assoc
                 name
                 {"limit"       (:worker-object-limit full-options)
                  "threadSafe"  (:thread-safe? full-options)
                  "autoTracing" (:auto-tracing? full-options)
                  "builderFn"   afn
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

(defn declare-cluster-agent
  [^AgentsTopology agents-topology local-name module-name agent-name]
  (.declareClusterAgent agents-topology local-name module-name agent-name))

(defn setup-object-name
  [^AgentObjectSetup setup]
  (.getObjectName setup))

(defn new-agent
  [^AgentsTopology agents-topology name]
  (.newAgent agents-topology name))

(defn node
  [agent-graph name output-nodes-spec node-fn]
  (graph/internal-add-node!
   agent-graph
   name
   output-nodes-spec
   (aor-types/->Node node-fn)))

(defn agg-start-node
  [agent-graph name output-nodes-spec node-fn]
  (graph/internal-add-node!
   agent-graph
   name
   output-nodes-spec
   (aor-types/->NodeAggStart node-fn nil)))

(defn agg-node
  [agent-graph name output-nodes-spec agg node-fn]
  (graph/internal-add-agg-node!
   agent-graph
   name
   output-nodes-spec
   agg
   node-fn))

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
  [^AgentNode agent-node node & args]
  (.emit agent-node node (into-array Object args)))

(defn result!
  [^AgentNode agent-node val]
  (.result agent-node val))

(defn get-store
  [^AgentNode agent-node name]
  (.getStore agent-node name))

(defn get-agent-object
  [^IFetchAgentObject fetch name]
  (.getAgentObject fetch name))

(defn stream-chunk!
  [^AgentNode agent-node chunk]
  (.streamChunk agent-node chunk))

(defn record-nested-op!
  [agent-node nested-op-type start-time-millis finish-time-millis
   info-map]
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
          ))]
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
                                   (po/agent-config-task-global-name agentName))
             root-pstate          (foreign-pstate
                                   cluster
                                   module-name
                                   (po/agent-root-task-global-name agentName))
             streaming-pstate     (foreign-pstate
                                   cluster
                                   module-name
                                   (po/agent-streaming-results-task-global-name
                                    agentName))
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
               (h/current-time-millis)))
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
             streaming-pstate
             agent-invoke
             node
             callback-fn))
          (stream-specific-internal [this agent-invoke node node-invoke-id
                                     callback-fn]
            (iclient/agent-stream-specific-impl
             streaming-pstate
             agent-invoke
             node
             node-invoke-id
             callback-fn))
          (stream-all-internal [this agent-invoke node callback-fn]
            (iclient/agent-stream-all-impl
             streaming-pstate
             agent-invoke
             node
             callback-fn))
          (underlying-objects [this]
            {:agent-depot          agent-depot
             :agent-config-depot   agent-config-depot
             :config-pstate        config-pstate
             :root-pstate          root-pstate
             :streaming-pstate     streaming-pstate
             :graph-history-pstate graph-history-pstate
             :tracing-query        tracing-query
             :invokes-page-query   invokes-page-query
             :current-graph-query  current-graph-query
            })
         ))))))

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
  ^AgentInvoke [^AgentClient agent-client & args]
  (.initiate agent-client (into-array Object args)))

(defn agent-initiate-async
  ^CompletableFuture [^AgentClient agent-client & args]
  (.initiateAsync agent-client (into-array Object args)))

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
  [^AgentClient agent-client agent-invoke]
  (.result agent-client agent-invoke))

(defn agent-result-async
  ^CompletableFuture [^AgentClient agent-client agent-invoke]
  (.resultAsync agent-client agent-invoke))

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

(defn start-ui
  (^java.io.Closeable [ipc] (start-ui ipc nil))
  (^java.io.Closeable [ipc options]
   (let [start-fn (requiring-resolve
                   'com.rpl.agent-o-rama.impl.ui.core/start-ui)]
     (start-fn ipc options))))

(defn stop-ui []
  (let [stop-fn (requiring-resolve 'com.rpl.agent-o-rama.impl.ui.core/stop-ui)]
    (stop-fn)))
