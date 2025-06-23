(ns com.rpl.agent-o-rama
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
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
    AgentGraph
    AgentInvoke
    AgentManager
    AgentNode
    AgentsTopology
    AgentStream
    MultiAgg$Impl]
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

(defn agents-topology
  [setup topologies]
  (let [^StreamTopology stream-topology (stream-topology
                                         topologies
                                         aor-types/AGENTS-TOPOLOGY-NAME)
        mb-topology    (microbatch-topology topologies
                                            aor-types/AGENTS-MB-TOPOLOGY-NAME)
        defined?-vol   (volatile! false)
        agents-vol     (volatile! {})
        store-info-vol (volatile! {})]
    (reify
     AgentsTopology
     (newAgent [this name]
       (when (contains? @agents-vol name)
         (throw (h/ex-info "Agent already exists" {:name name})))
       (let [ret (graph/mk-agent-graph)]
         (vswap! agents-vol assoc name ret)
         ret))
     (getStreamTopology [this] stream-topology)

     ;; TODO: need methods for getting mirror agents, and will also need methods
     ;; for invoking mirror agents
     ;;    - should invoking a mirror agent be a node, or should it just be an
     ;;    invoke?
     ;;      - feels like an invoke

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
       (declare-object* setup (symbol name) o))
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
        @store-info-vol)
     ))))

(defn underlying-stream-topology
  [^AgentsTopology at]
  (.getStreamTopology at))

(defn define-agents!
  [^AgentsTopology at]
  (.define at))


; (declareKeyValueStore [this name key-class val-class]
; (declareDocumentStore [this name key-class key-val-classes]
; (^PState$Declaration declarePStateStore [this ^String name ^Class schema]

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

;; TODO: all the declare methods

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

(defn stream-chunk!
  [^AgentNode agent-node chunk]
  (.streamChunk agent-node chunk))

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
             invokes-pstate       (foreign-pstate
                                   cluster
                                   module-name
                                   (po/agent-invoke-task-global-name agentName))
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
                                   (queries/tracing-query-topology-name
                                    agentName))]

         (reify
          AgentClient
          (invoke [this args]
            (.get (.invokeAsync this args)))
          (invokeAsync [this args]
            (.thenCompose
             (.initiateAsync this args)
             (h/cf-function [agent-invoke]
               (.agentResultAsync this agent-invoke))))
          (initiate [this args]
            (.get (.initiateAsync this args)))
          (initiateAsync [this args]
            (.thenApply
             (foreign-append-async!
              agent-depot
              (aor-types/->AgentInvoke
               (vec args)
               (h/current-time-millis)
               nil))
             (h/cf-function [{[graph-task-id graph-id]
                              aor-types/AGENTS-TOPOLOGY-NAME}]
               (AgentInvoke. graph-task-id graph-id)
             )))
          (agentResult [this agent-invoke]
            (.get (.agentResultAsync this agent-invoke)))
          (agentResultAsync [this agent-invoke]
            (let [graph-task-id (.getTaskId ^AgentInvoke agent-invoke)
                  graph-id      (.getAgentInvokeId ^AgentInvoke agent-invoke)
                  ret           (CompletableFuture.)
                  proxy-atom    (atom nil)]
              (.thenApply
               (foreign-proxy-async
                [(keypath graph-id) :result]
                invokes-pstate
                {:pkey        graph-task-id
                 :callback-fn (fn [new-val _ _]
                                (when (some? new-val)
                                  (if (:failure? new-val)
                                    (.completeExceptionally
                                     ret
                                     (h/ex-info (:val new-val) {}))
                                    (.complete ret (:val new-val)))
                                  (locking proxy-atom
                                    (if (nil? @proxy-atom)
                                      (reset! proxy-atom ::close)
                                      (do
                                        (close! @proxy-atom)
                                        (reset! proxy-atom ::done)
                                      )))
                                ))
                })
               (h/cf-function [proxy-state]
                 (i/hook:agent-result-proxy proxy-state)
                 (locking proxy-atom
                   (if (= ::close @proxy-atom)
                     (do
                       (close! proxy-state)
                       (reset! proxy-atom ::done))
                     (reset! proxy-atom proxy-state))
                 ))
              )
              ret
            ))
          (stream [this agent-invoke node]
            (.stream this agent-invoke node nil))
          (stream [this agent-invoke node callback-void-jfn]
            (aor-types/stream-internal this
                                       agent-invoke
                                       node
                                       (when callback-void-jfn
                                         (h/convert-void-jfn
                                          callback-void-jfn))))
          ;; TODO: <<<<>>> methods for getting graph history
          ;;    - just max version and method to get historicalgraphinfo at a
          ;;    particular version
          ;;    - need historicalgraphinfo to be a java type

          aor-types/AgentClientInternal
          (stream-internal [this agent-invoke node callback-fn]
            (iclient/agent-stream-impl
             streaming-pstate
             agent-invoke
             node
             callback-fn))
         ))))))

(defn agent-client
  ^AgentClient [^AgentManager agent-manager agent-name]
  (.getAgentClient agent-manager agent-name))

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

(defn agent-result
  [^AgentClient agent-client agent-invoke]
  (.agentResult agent-client agent-invoke))

(defn agent-result-async
  ^CompletableFuture [^AgentClient agent-client agent-invoke]
  (.agentResultAsync agent-client agent-invoke))

(defn agent-stream
  (^AgentStream [^AgentClient agent-client agent-invoke node]
   (.stream agent-client agent-invoke node))
  (^AgentStream [^AgentClient agent-client agent-invoke node callback-fn]
   (aor-types/stream-internal agent-client agent-invoke node callback-fn)))

(defn agent-stream-num-resets
  [^AgentStream stream]
  (.numResets stream))

;; TODO: <<<<>>>> need to define Clojure API for any other methods
