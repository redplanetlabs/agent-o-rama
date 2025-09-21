(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AddRule
    AndFilter
    DeleteRule
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


(defn all-action-builders
  []
  (let [declared-objects (po/agent-declared-objects-task-global)]
    (merge BUILT-IN-ACTIONS
           (.getActionBuilders declared-objects))))

(defn- agent-run-type?
  [info]
  (= :agent (:run-type info)))

(extend-protocol aor-types/RuleFilter
  FeedbackFilter
  (dependency-rule-names [this] #{(:rule-name this)})
  (rule-filter-matches? [this info]
    (selected-any?
     [:feedback
      :results
      ALL
      (selected? :source
                 aor-types/EvalSourceImpl?
                 :source
                 aor-types/ActionSourceImpl?
                 :rule-name
                 (pred= (:rule-name this)))
      :scores
      (must (:feedback-key this))
      #(aor-types/comparator-spec-matches? (:comparator-spec this) %)]
     info))

  LatencyFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this {:keys [start-time-millis finish-time-millis]}]
    (and start-time-millis
         finish-time-millis
         (aor-types/comparator-spec-matches? (:comparator-spec this)
                                             (- finish-time-millis start-time-millis))))

  ErrorFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (if (agent-run-type? info)
      (not (empty? (:exception-summaries info)))
      (not (empty? (:exceptions info)))
    ))

  InputMatchFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [o (if (agent-run-type? info) (:invoke-args info) (:input info))]
      (some? (re-find (:regex this) (h/read-json-path o (:json-path this))))))

  OutputMatchFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [output (if (agent-run-type? info)
                   (-> info
                       :result
                       :val)
                   (h/node->output (:result info) (:emits info)))]
      (some? (re-find (:regex this) (h/read-json-path output (:json-path this))))
    ))

  TokenCountFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [token-counts
          (if (agent-run-type? info)
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
  (dependency-rule-names [this]
    (apply set/union (mapv aor-types/dependency-rule-names (:filters this))))
  (rule-filter-matches? [this info]
    (every? #(aor-types/rule-filter-matches? % info) (:filters this)))


  OrFilter
  (dependency-rule-names [this]
    (apply set/union (mapv aor-types/dependency-rule-names (:filters this))))
  (rule-filter-matches? [this info]
    (if (some #(aor-types/rule-filter-matches? % info) (:filters this))
      true
      false))

  NotFilter
  (dependency-rule-names [this] (aor-types/dependency-rule-names (:filter this)))
  (rule-filter-matches? [this info]
    (not (aor-types/rule-filter-matches? (:filter this) info)))
)

(defn check-rule-dependency-conflict
  [rules name]
  (let [conflict (select-first [ALL
                                (selected? :filter
                                           (view aor-types/dependency-rule-names)
                                           #(contains? % name))
                                :name]
                               rules)]
    (when (some? conflict)
      (format "Deletion failed because rule '%s' depends on it" conflict))))

(defn mk-cursor-map
  [start-time-millis]
  (let [^com.rpl.rama.ModuleInstanceInfo module-instance-info (ops/module-instance-info)
        num-tasks (.getNumTasks module-instance-info)
        uuid      (h/min-uuid7-at-timestamp start-time-millis)]
    (into {}
          (for [i (range 0 num-tasks)]
            [i uuid]
          ))))

(defn agent-names-set
  []
  (-> (po/agent-declared-objects-task-global)
      .getAgentGraphs
      keys
      set))

(deframaop handle-rule-event
  [{:keys [*agent-name] :as *data}]
  (agent-names-set :> *agent-names)
  (filter> (contains? *agent-names *agent-name))
  (keys (all-action-builders) :> *action-names)
  (<<with-substitutions
   [$$rules (po/agent-rules-task-global *agent-name)]
   (<<subsource *data
    (case> AddRule :> {:keys [*name *id *action-name *start-time-millis]})
     (local-select> [(keypath *name) :id] $$rules :> *curr-id)
     (<<cond
      (case> (and> (some? *curr-id) (not= *curr-id *id)))
       (ack-return> (format "Rule '%s' already exists" *name))
      (case> (not (contains? *action-names *action-name)))
       (ack-return> (format "Action '%s' doesn't exist" *action-name))

      (default>)
       (mk-cursor-map *start-time-millis :> *cursor-map)
       (local-transform> [(keypath *name)
                          (multi-path [:definition (termval *data)]
                                      [:cursors (termval *cursor-map)])]
                         $$rules))

    (case> DeleteRule :> {:keys [*name]})
     (local-select> (subselect MAP-VALS) $$rules :> *rules)
     (check-rule-dependency-conflict *rules *name :> *error-str)
     (<<if (some? *error-str)
       (ack-return> *error-str)
      (else>)
       (local-transform> [(keypath *name) NONE>] $$rules))
   )))

(deframafn read-rules
  []
  (<<batch
    (ops/explode (agent-names-set) :> *agent-name)
    (po/agent-rules-task-global *agent-name :> $$rules)
    (local-select> STAY $$rules :> *rules)
    (aggs/+map-agg *agent-name *rules :> *ret))
  (:> *ret))

(deframaop find-qualified-offsets
  []
  (read-rules :> *agent->rule->info)
  (ops/explode *agent->rule->info :> [*agent-name *rule->info])
  (ops/explode *rule->info :> [*rule-name *rule-info])
  (aor-types/dependency-rule-names (-> *rule-info
                                       (get :definition)
                                       (get :filter))
                                   :> *dependency-names)
  (select> [:cursors ALL (collect-one FIRST LAST)]
    *rule-info
    :> [*task-id *offset])
  (<<batch
    (ops/explode *dependency-names :> *dname)
    (select> [(keypath *dname) :cursors (keypath *task-id)] *rule->info :> *other-offset)
    (aggs/+min *other-offset :> *dep-end-offset))
  (or> *dep-end-offset (h/max-uuid) :> *end-offset)
  (|direct *task-id)
  ;; TODO: <<<>>> query root/node as necessary to find matching offsets
  ;;    - go no higher than *end-offset
  ;;    - get a range with sorted-map-range-from of size 100, then check



)

(defbasicblocksegmacro handle-analytics-tick
  []
  [[anode/read-config aor-types/MAX-ACTIONS-CONCURRENCY-CONFIG :> '*max-concurrency]

   [<<batch
    [find-qualified-offsets :> '*task-id]

    ;; TODO: <<<<>>>>
    ;;  - for each agent/rule
    ;;   - for each task cursor
    ;;   - check if dependency rules have processed that far already

   ]
   ;; TODO: <<<<>>>>
   ;;  - do <<batch to go to all tasks
   ;;    - TODO: how to handle chained rules?
   ;;      - maybe if have a chain, don't keep going
   ;;        - or keep track of which ones need to keep being checked
   ;;      - no, rules are handled independently...
   ;;    - if don't sample something, still should write action state to it
   ;;      - would be really good to do those in chunks, especially for low sample rate
   ;;      - can have "PStateWrites" depot append
  ])



(defn add-rule!
  [global-actions-depot name agent-name
   {:keys [node-name action-name action-params filter sampling-rate start-time-millis]}]
  (let [{error aor-types/AGENT-TOPOLOGY-NAME}
        (foreign-append!
         global-actions-depot
         (aor-types/->valid-AddRule
          name
          (h/random-uuid7)
          agent-name
          node-name
          action-name
          action-params
          filter
          sampling-rate
          start-time-millis))]
    (when error
      (throw (h/ex-info "Error adding rule" {:info error})))))

(defn delete-rule!
  [global-actions-depot agent-name name]
  (let [{error aor-types/AGENT-TOPOLOGY-NAME}
        (foreign-append!
         global-actions-depot
         (aor-types/->valid-DeleteRule
          agent-name
          name))]
    (when error
      (throw (h/ex-info "Error adding rule" {:info error})))))
