(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AndFilter
    ErrorFilter
    FeedbackFilter
    InputMatchFilter
    LatencyFilter
    NotFilter
    OrFilter
    OutputMatchFilter
    TokenCountFilter]
   [java.util.concurrent
    CompletableFuture]))

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
  (simpl/do-pstate-write!
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
            eval-client  (get-agent-client aor-types/EVALUATOR-AGENT-NAME)]
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
            (let [{:keys [stats result]}
                  (.get
                   ^CompletableFuture
                   (aor-types/subagent-next-step-async eval-client
                                                       eval-agent-invoke))]
              (merge {"invoke" eval-agent-invoke}
                     (if (instance? Throwable result)
                       {"success?" false "failure" (h/throwable->str result)}
                       {"success?" true "stats" stats})))
          ))))
    :description
    "Run an evaluator to add feedback to a node or agent"
    :options
    {:params
     {"name"
      {:description
       "Evaluator to use"
      }}}
   }})




(defprotocol RuleFilter
  (dependency-rule-ids [this])
  (rule-matches? [this info]))


(defn- agent-run-type?
  [info]
  (= :agent (:run-type info)))

(extend-protocol aor-types/RuleFilter
  FeedbackFilter
  (dependency-rule-ids [this] #{rule-id})
  (rule-matches? [this info]
    (selected-any?
     [:feedback
      :results
      ALL
      (selected? :source #(instance? EvalSource %)
                 :source #(instance? ActionSource %)
                 :rule-id (pred= (:rule-id this)))
      :scores
      (must (:feedback-key this))
      #(aor-types/comparator-spec-matches? (:comparator-spec this) %)]
     info))

  LatencyFilter
  (dependency-rule-ids [this] #{})
  (rule-matches? [this {:keys [start-time-millis finish-time-millis]}]
    (and start-time-millis
         finish-time-millis
         (aor-types/comparator-spec-matches? (:comparator-spec this)
                                             (- finish-time-millis start-time-millis))))

  ErrorFilter
  (dependency-rule-ids [this] #{})
  (rule-matches? [this info]
    (if (agent-run-type? info)
      (not (empty? (:exception-summaries info)))
      (not (empty? (:exceptions info)))
    ))

  InputMatchFilter
  (dependency-rule-ids [this] #{})
  (rule-matches? [this info]
    (let [o (if (agent-run-type? info) (:invoke-args info) (:input info))]
      (some? (re-find (:regex this) (h/read-json-path o (:json-path this))))))

  OutputMatchFilter
  (dependency-rule-ids [this] #{})
  (rule-matches? [this info]
    (let [output (if (agent-run-type? info)
                   (-> info
                       :result
                       :val)
                   (h/node->output (:result info) (:emits info)))]
      (some? (re-find (:regex this) (h/read-json-path output (:json-path this))))
    ))

  TokenCountFilter
  (dependency-rule-ids [this] #{})
  (rule-matches? [this info]
    (let [token-counts
          (if (agent-node-type? info)
            (let [combined (stats/aggregated-basic-stats (:stats info))]
              {:input  (:input-token-count combined)
               :output (:output-token-count combined)
               :total  (:total-token-count combined)})
            (-> info
                :nested-ops
                stats/nested-op-stats
                :token-counts))]
      (aor-types/comparator-spec-matches?
       (:comparator-spec this)
       (get token-counts (:type this)))
    ))

  AndFilter
  (dependency-rule-ids [this] (apply set/union (mapv dependency-rule-ids (:filters this))))
  (rule-matches? [this info]
    (every? #(rule-matches? % info) (:filters this)))


  OrFilter
  (dependency-rule-ids [this] (apply set/union (mapv dependency-rule-ids (:filters this))))
  (rule-matches? [this info]
    (if (some #(rule-matches? % info) (:filters this))
      true
      false))

  NotFilter
  (dependency-rule-ids [this] (dependency-rule-ids (:filter this)))
  (rule-matches? [this info]
    (not (rule-matches? (:filter this) info)))
)


(defbasicblocksegmacro handle-analytics-tick
  [agent-names]
  ;; TODO: <<<<>>>>
  ;;  - use config for max concurrency
  ;;  - need DVV for each agent as to where it's computing up to
  ;;      - can be on each task
  ;;  - do <<batch to go to all tasks
  ;;    - TODO: how to handle chained rules?
  ;;      - maybe if have a chain, don't keep going
  ;;        - or keep track of which ones need to keep being checked
  ;;      - no, rules are handled independently...
  ;;    - if don't sample something, still should write action state to it
  ;;      - would be really good to do those in chunks, especially for low sample rate
  ;;      - can have "PStateWrites" depot append
)
