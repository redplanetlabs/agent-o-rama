(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.clojure :as c]
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
    RamaClientsTaskGlobal]
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
