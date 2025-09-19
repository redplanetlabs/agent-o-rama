(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    RamaClientsTaskGlobal]))


(def EMPTY-OP-STATS (aor-types/->valid-OpStatsImpl 0 0))
(def EMPTY-BASIC-STATS (aor-types/->valid-BasicAgentInvokeStatsImpl {} 0 0 0 {}))
(def EMPTY-SUBAGENT-STATS (aor-types/->valid-SubagentInvokeStatsImpl 0 EMPTY-BASIC-STATS))
(def EMPTY-AGENT-STATS (aor-types/->valid-AgentInvokeStatsImpl {} EMPTY-BASIC-STATS))

(defn adder
  [v]
  (fn [v2]
    (+ v v2)))

(defn merge-op-stats
  [m1 m2]
  (merge-with
   (fn [o1 o2]
     (aor-types/->valid-OpStatsImpl
      (+ (:count o1) (:count o2))
      (+ (:total-time-millis o1) (:total-time-millis o2))))
   m1
   m2))

(defn combine-basic-stats
  [b1 b2]
  (aor-types/->valid-BasicAgentInvokeStatsImpl
   (merge-op-stats (:nested-op-stats b1) (:nested-op-stats b2))
   (+ (:input-token-count b1) (:input-token-count b2))
   (+ (:output-token-count b1) (:output-token-count b2))
   (+ (:total-token-count b1) (:total-token-count b2))
   (merge-op-stats (:node-stats b1) (:node-stats b2))))

(defn aggregated-basic-stats
  [stats]
  (reduce
   combine-basic-stats
   (:basic-stats stats)
   (traverse [:subagent-stats MAP-VALS :basic-stats] stats)))

(defn merge-subagent-stats
  [m1 m2]
  (merge-with
   (fn [sa1 sa2]
     (aor-types/->valid-SubagentInvokeStatsImpl
      (+ (:count sa1) (:count sa2))
      (combine-basic-stats (:basic-stats sa1) (:basic-stats sa2))))
   m1
   m2))

(defn agent-stats-merger
  [stats]
  (fn [existing]
    (if (nil? stats)
      existing
      (aor-types/->valid-AgentInvokeStatsImpl
       (merge-subagent-stats (:subagent-stats existing) (:subagent-stats stats))
       (combine-basic-stats (:basic-stats existing) (:basic-stats stats))))))

(defn mk-node-stats
  [node start-time-millis finish-time-millis nested-ops]
  (let [tc-vol   (volatile! {:input  0
                             :output 0
                             :total  0})
        nops-vol (volatile! {})
        sa-vol   (volatile! {})]
    (doseq [{:keys [start-time-millis finish-time-millis type info]} nested-ops]
      (let [delta-millis (- finish-time-millis start-time-millis)]
        (multi-transform [h/VOLATILE
                          (keypath type)
                          (nil->val EMPTY-OP-STATS)
                          (multi-path [:count (term inc)]
                                      [:total-time-millis (term (adder delta-millis))])]
                         nops-vol)
        (when (= :model-call type)
          (multi-transform [h/VOLATILE
                            (multi-path [:input (term (adder (get info "inputTokenCount" 0)))]
                                        [:output (term (adder (get info "outputTokenCount" 0)))]
                                        [:total (term (adder (get info "totalTokenCount" 0)))])]
                           tc-vol))
        (when (= :agent-call type)
          (let [agent-module-name (get info "agent-module-name")
                agent-name        (get info "agent-name")
                sub-stats         (get info "stats")
               ]
            ;; just in case user sets these themselves
            (when (and (string? agent-module-name)
                       (string? agent-name)
                       (aor-types/AgentInvokeStatsImpl? sub-stats))
              (transform h/VOLATILE #(merge-subagent-stats % (:subagent-stats sub-stats)) sa-vol)
              (multi-transform
               [h/VOLATILE
                (keypath (aor-types/->valid-AgentRefImpl agent-module-name agent-name))
                (nil->val EMPTY-SUBAGENT-STATS)
                (multi-path
                 [:count (term inc)]
                 [:basic-stats (term #(combine-basic-stats % (:basic-stats sub-stats)))])]
               sa-vol)
            )))
      ))
    (aor-types/->valid-AgentInvokeStatsImpl
     @sa-vol
     (aor-types/->valid-BasicAgentInvokeStatsImpl
      @nops-vol
      (:input @tc-vol)
      (:output @tc-vol)
      (:total @tc-vol)
      {node (aor-types/->valid-OpStatsImpl 1 (- finish-time-millis start-time-millis))}
     ))))

;; TODO: <<<>>>> need to be bound with :num-tasks, :declared-objects, and :rama-clients
(def ^:dynamic ACTION-HELPERS)
(defn declared-objects ^AgentDeclaredObjectsTaskGlobal [] (:declared-objects ACTION-HELPERS))
(defn rama-clients ^RamaClientsTaskGlobal [] (:rama-clients ACTION-HELPERS))
(defn random-task-id [] (rand-int (:num-tasks ACTION-HELPERS)))

(defn retrieve-pstate
  [pstate-name]
  (let [declared-objects-tg (declared-objects)
        retriever (.getClusterRetriever declared-objects-tg)]
    (foreign-pstate
     retriever
     (.getThisModuleName declared-objects-tg)
     pstate-name)))

(defn pstate-write!
  [pstate-name path k]
  (simpl/do-pstate-write
   (.getPStateWriteDepot (rama-clients))
   nil
   pstate-name
   path
   k
  ))

(defn get-agent-client
  [name]
  (.getAgentClient (declared-objects) name))

(def BUILT-IN-ACTIONS
  {"aor/eval"
   {:builder-fn
    (fn [{:keys [name]}]
      (let [evals-pstate (retrieve-pstate (po/evaluators-task-global-name))
            eval-info    (foreign-select-one (keypath name) evals-pstate)
            client       (get-agent-client exp/EVALUATOR-AGENT-NAME)]
        (when (nil? eval-info)
          (throw (h/ex-info "Evaluator doesn't exist" {:name name})))
        (fn [fetcher input output {:keys [action-name agent-name] :as run-info}]
          (let [target-pstate-name (if (= :agent (:type run-info))
                                     (po/agent-root-task-global-name agent-name)
                                     (po/agent-node-task-global-name agent-name))
                target-pstate (retrieve-pstate target-pstate-name)
                target (if (= :agent (:type run-info))
                         (:agent-invoke run-info)
                         (:node-invoke run-info))
                target-task-id (:task-id target)
                k (if (= :agent (:type run-info))
                    (:agent-invoke-id target)
                    (:node-invoke-id target))
                curr-eval-agent-invoke (foreign-select-one [(keypath k)
                                                            (fb/action-state-path action-name)]
                                                           target-pstate
                                                           {:pkey target-task-id})
                eval-agent-invoke (or curr-eval-agent-invoke
                                      (aor-types/->AgentInvokeImpl (random-task-id)
                                                                   (h/random-uuid7)))]
            (when (nil? curr-eval-agent-invoke)
              (pstate-write! target-pstate-name
                             (path (keypath k)
                                   (fb/set-action-state-path action-name eval-agent-invoke))
                             (:task-id target)))
            (binding [aor-types/FORCED-AGENT-INVOKE-ID (:agent-invoke-id eval-agent-invoke)
                      aor-types/FORCED-AGENT-TASK-ID   (:task-id eval-agent-invoke)]
              ;; this is a no-op if it was already initiated
              (exp/initiate-eval eval-client
                                 eval-info
                                 :regular
                                 input
                                 nil
                                 [output]
                                 name
                                 (:builder-name eval-info)
                                 (:builder-params eval-info)
                                 [(aor-types/->valid-EvalInfo agent-name target)]
                                 nil))
            ;; TODO: <<<<>>>>
            ;;  - now wait for result
            ;;    - don't need to do anything else
            ;;    - TODO: how does microbatching know what's happening in order to mark this as
            ;;    complete?
          ))))
    :description
    ""
    :options
    {:params
     {"name"
      {:description
       "Evaluator name to use"
      }}}
   }})
