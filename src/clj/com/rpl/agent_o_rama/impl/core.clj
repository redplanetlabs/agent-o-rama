(ns com.rpl.agent-o-rama.impl.core
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentFailedException
    AgentInvoke
    AgentNode
    AgentObjectOptions$Impl
    AgentsTopology]
   [com.rpl.agentorama.impl
    RamaClientsTaskGlobal
    AgentDeclaredObjectsTaskGlobal
    AgentNodeExecutorTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AggAckOp
    NodeOp]
   [java.util.concurrent
    CompletableFuture]))

(def SUBSTITUTE-TICK-DEPOTS false)
(def DEFAULT-GC-TICK-MILLIS 10000)

;; for agent-o-rama namespace
(defn hook:building-plain-agent-object [name o])

(defn- define-agent!
  [agent-name setup topologies stream-topology mb-topology agent-graph]
  (let [agent-depot-sym           (symbol (po/agent-depot-name agent-name))
        agent-streaming-depot-sym (symbol (po/agent-streaming-depot-name
                                           agent-name))
        agent-human-depot-sym     (symbol (po/agent-human-depot-name
                                           agent-name))
        agent-config-depot-sym    (symbol (po/agent-config-depot-name
                                           agent-name))
        agent-gc-tick-depot-sym   (symbol (po/agent-gc-tick-depot-name
                                           agent-name))]
    (declare-depot* setup agent-depot-sym apart/agent-depot-partitioner)
    (declare-depot* setup
                    agent-streaming-depot-sym
                    apart/agent-task-id-depot-partitioner)
    (declare-depot* setup
                    agent-human-depot-sym
                    apart/human-depot-partitioner)
    (declare-depot* setup
                    agent-config-depot-sym
                    :random
                    {:global? true})

    (declare-object* setup
                     (symbol (po/agent-graph-task-global-name agent-name))
                     (graph/resolve-agent-graph agent-graph))

    (declare-pstate*
     stream-topology
     (symbol (po/agent-root-task-global-name agent-name))
     po/AGENT-ROOT-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-root-count-task-global-name agent-name))
     Long
     {:initial-value   0
      :key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-active-invokes-task-global-name agent-name))
     po/AGENT-ACTIVE-INVOKES-PSTATE-SCHEMA)
    (declare-pstate*
     stream-topology
     (symbol (po/agent-gc-invokes-task-global-name agent-name))
     po/AGENT-GC-ROOT-INVOKES-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-streaming-results-task-global-name agent-name))
     po/AGENT-STREAMING-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-node-task-global-name agent-name))
     po/AGENT-NODE-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/graph-history-task-global-name agent-name))
     po/GRAPH-HISTORY-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-id-gen-task-global-name agent-name))
     Long
     {:initial-value 0})
    (declare-pstate*
     stream-topology
     (symbol (po/agent-config-task-global-name agent-name))
     po/AGENT-CONFIG-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})

    (if SUBSTITUTE-TICK-DEPOTS
      (do
        (declare-depot* setup
                        (symbol (po/agent-check-tick-depot-name agent-name))
                        :random
                        {:global? true})
        (declare-depot* setup
                        agent-gc-tick-depot-sym
                        :random
                        {:global? true}))
      (do
        (declare-tick-depot* setup
                             (symbol (po/agent-check-tick-depot-name
                                      agent-name))
                             retries/DEFAULT-CHECKER-TICK-MILLIS)
        (declare-tick-depot* setup
                             agent-gc-tick-depot-sym
                             DEFAULT-GC-TICK-MILLIS)))
    (declare-depot* setup
                    (symbol (po/agent-failures-depot-name agent-name))
                    :random)
    (declare-depot* setup
                    (symbol (po/agent-gc-valid-invokes-depot-name agent-name))
                    :random)

    (doseq [d [(symbol (po/agent-failures-depot-name agent-name))
               (symbol (po/agent-gc-valid-invokes-depot-name agent-name))
               agent-config-depot-sym
               agent-streaming-depot-sym
               agent-human-depot-sym
               agent-depot-sym]]
      (set-launch-depot-dynamic-option!* setup
                                         d
                                         "depot.max.entries.per.partition"
                                         500))

    (declare-pstate*
     mb-topology
     (symbol (po/agent-valid-invokes-task-global-name agent-name))
     po/AGENT-VALID-INVOKES-PSTATE-SCHEMA
     {:key-partitioner apart/task-id-key-partitioner})
    (declare-pstate*
     mb-topology
     (symbol (po/pending-retries-task-global-name agent-name))
     po/PENDING-RETRIES-PSTATE-SCHEMA)

    (retries/declare-check-impl mb-topology agent-name)
    (queries/declare-tracing-query-topology topologies agent-name)
    (queries/declare-fork-affected-aggs-query-topology topologies agent-name)
    (queries/declare-get-invokes-page-topology topologies agent-name)
    (queries/declare-get-current-graph topologies agent-name)

    (<<sources stream-topology
     (source> agent-config-depot-sym {:retry-mode :all-after} :> *data)
      (at/handle-config agent-name *data)

     (source> agent-streaming-depot-sym {:retry-mode :all-after} :> *data)
      (at/handle-streaming agent-name *data)

     (source> agent-human-depot-sym :> *data)
      (at/handle-human agent-name *data)

     (source> agent-gc-tick-depot-sym)
      (at/handle-gc agent-name)

     (source> agent-depot-sym {:retry-mode :none} :> *data)
      (at/intake-agent-depot agent-name
                             *data
                             :> *agent-task-id *agent-id *retry-num *op)
      (<<subsource *op
       (case> NodeOp)
        (at/handle-node-op agent-name *agent-task-id *agent-id *retry-num *op)

       (case> AggAckOp :> {:keys [*agg-invoke-id *ack-val]})
        (at/ack-agg! agent-name *agg-invoke-id *retry-num *ack-val)
      )
    )))

(deframafn do-transform!*
  [*path $$p]
  (local-transform> *path $$p)
  (:> {:type :success}))

(defn do-transform!
  [path pstate]
  (try
    (do-transform!* path pstate)
    (catch Exception e
      {:type      :failure
       :exception e}
    )))

(defn mk-agents-info
  [agent-graphs mirror-agents]
  (reduce-kv
   (fn [m agent-name _]
     (assoc m agent-name [nil agent-name]))
   mirror-agents
   agent-graphs))

(defn define-agents!
  [setup topologies stream-topology mb-topology agent-graphs mirror-agents
   store-info declared-objects]
  (declare-object* setup
                   (symbol (po/agents-store-info-name))
                   (aor-types/->valid-StoreInfo store-info {}))
  (declare-object* setup
                   (symbol (po/agents-clients-name))
                   (RamaClientsTaskGlobal.
                    (-> agent-graphs
                        keys
                        vec)
                    []))
  (declare-object* setup
                   (symbol (po/agent-node-executor-name))
                   (AgentNodeExecutorTaskGlobal.))

  (declare-object* setup
                   (symbol (po/agent-declared-objects-name))
                   (AgentDeclaredObjectsTaskGlobal.
                    declared-objects
                    (mk-agents-info agent-graphs mirror-agents)))

  (let [pstate-write-depot-sym (symbol (po/agent-pstate-write-depot-name))]
    (declare-depot* setup pstate-write-depot-sym (hash-by :key))
    (set-launch-depot-dynamic-option!* setup
                                       pstate-write-depot-sym
                                       "depot.max.entries.per.partition"
                                       500)
    (<<sources stream-topology
     (source> pstate-write-depot-sym
               {:retry-mode :none}
              :> {:keys [*pstate-name *path *agent-name *agent-task-id
                          *agent-id *retry-num]})
      (<<if (apart/valid-retry-num? *agent-name
                                    *agent-task-id
                                    *agent-id
                                    *retry-num)
        (this-module-pobject-task-global *pstate-name :> $$p)
        (do-transform! *path $$p :> *ret)
        (ack-return> *ret)
       (else>)
        (ack-return> {:type      :failure
                      :exception (h/ex-info "Agent invoke has been retried"
                                            {})})
      )))
  (queries/declare-agent-get-names-query-topology topologies
                                                  (-> agent-graphs
                                                      keys
                                                      set))
  (doseq [[agent-name agent-graph] agent-graphs]
    (define-agent! agent-name
                   setup
                   topologies
                   stream-topology
                   mb-topology
                   agent-graph)))

(defn convert-agent-object-options
  [^AgentObjectOptions$Impl options]
  (when options
    (setval [MAP-VALS nil?]
            NONE
            {:thread-safe?        (.threadSafe options)
             :auto-tracing?       (.autoTracing options)
             :worker-object-limit (.workerObjectLimit options)})))

(defn mk-failure-exception
  [result exceptions]
  (let [s (-> result
              :val
              str)
        s (if (empty? exceptions)
            s
            (str s
                 " (last failure: "
                 (-> exceptions
                     last
                     h/first-line)
                 ")"))]
    (AgentFailedException. s)))

(defn hook:agent-result-proxy [proxy])

(defn client-wait-for-result
  [root-pstate ^AgentInvoke agent-invoke handle-fn]
  (let [agent-task-id (.getTaskId agent-invoke)
        agent-id      (.getAgentInvokeId agent-invoke)
        ret           (CompletableFuture.)
        proxy-atom    (atom nil)]
    (.thenApply
     (foreign-proxy-async
      [(keypath agent-id)
       (submap [:result :exceptions :human-requests])
       (transformed :human-requests first)
       (multi-transformed [(map-key :human-requests) (termval :human-request)])]
      root-pstate
      {:pkey        agent-task-id
       :callback-fn
       (fn [m _ _]
         (let [done-fn (handle-fn m)]
           (when (some? done-fn)
             (when-not (.isDone ret)
               (done-fn ret))
             (locking proxy-atom
               (cond
                 (nil? @proxy-atom)
                 (reset! proxy-atom ::close)

                 (keyword? @proxy-atom) nil

                 :else
                 (do
                   (close! @proxy-atom)
                   (reset! proxy-atom ::done)
                 )))
           )))
      })
     (h/cf-function [proxy-state]
       (hook:agent-result-proxy proxy-state)
       (locking proxy-atom
         (if (= ::close @proxy-atom)
           (do
             (close! proxy-state)
             (reset! proxy-atom ::done))
           (reset! proxy-atom proxy-state))
       )))
    ret
  ))

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

(defn emit!
  [^AgentNode agent-node node & args]
  (.emit agent-node node (into-array Object args)))

(defn result!
  [^AgentNode agent-node val]
  (.result agent-node val))
