(ns com.rpl.agent-o-rama.impl.retries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]))

(def DEFAULT-CHECKER-TICK-MILLIS 10000)

(deframafn checker-threshold-millis
  [*agent-name]
  (:> (anode/read-config *agent-name
                         aor-types/STALL-CHECKER-THRESHOLD-MILLIS-CONFIG)))

(defn invalid-time-delta?
  [agent-name time-millis]
  (>= (- (h/current-time-millis) time-millis)
      (checker-threshold-millis agent-name)))

(defn invoke-id-executing?
  [^AgentNodeExecutorTaskGlobal node-exec invoke-id]
  (contains? (.getRunningInvokeIds node-exec) invoke-id))

(defgenerator stalled-agent-ids
  [agent-name]
  (let [agent-node-pstate-sym
        (symbol (po/agent-node-task-global-name agent-name))

        agent-root-pstate-sym
        (symbol (po/agent-root-task-global-name agent-name))

        agent-active-invokes-pstate-sym
        (symbol (po/agent-active-invokes-task-global-name agent-name))

        pending-retries-pstate-sym
        (symbol (po/pending-retries-task-global-name agent-name))

        node-exec (symbol (po/agent-node-executor-name))]
    (batch<- [*agent-task-id *agent-id *retry-num]
      (|all)
      (ops/current-task-id :> *agent-task-id)
      (local-select> MAP-KEYS
                     agent-active-invokes-pstate-sym
                     {:allow-yield? true}
                     :> *agent-id)
      (local-select> (keypath *agent-id)
                     agent-root-pstate-sym
                     :> {:keys [*root-invoke-id
                                *start-time-millis
                                *last-progress-time-millis
                                *retry-num]})
      (filter> (invalid-time-delta? agent-name *last-progress-time-millis))
      (loop<- [*invoke-id *root-invoke-id
               *emitted-millis *start-time-millis]
        (local-select> (keypath *invoke-id)
                       agent-node-pstate-sym
                       :> {:keys [*start-time-millis
                                  *finish-time-millis
                                  *started-agg?
                                  *agg-invoke-id
                                  *emits
                                  *invoked-agg-invoke-id]})
        ;; successful agg or successful regular node
        (<<if (or> (some? *invoked-agg-invoke-id)
                   (some? *finish-time-millis))
          (<<if *started-agg?
            (local-select> (keypath *agg-invoke-id)
                           agent-node-pstate-sym
                           :> {*agg-finished? :agg-finished?
                               *agg-finish    :finish-time-millis
                               *agg-emits     :emits})
            (<<if *agg-finished?
              ;; don't need emitted-time here since the node definitely
              ;; exists, and the node invoke happens synchronously with
              ;; :agg-finished? being set
              (continue> *agg-invoke-id nil)
             (else>)
              (identity *emits :> *check-emits)
              (anchor> <check-agg-graph>))
           (else>)
            (identity *emits :> *check-emits)
            (anchor> <check-regular-node-emits>))

          (unify> <check-agg-graph> <check-regular-node-emits>)
          (ops/explode *check-emits
                       :> {*next-invoke-id :invoke-id
                           *task-id        :target-task-id})
          (|direct *task-id)
          (continue> *next-invoke-id *finish-time-millis)
         (else>)
          (<<if (or>
                 (and> (nil? *start-time-millis)
                       (invalid-time-delta? agent-name *emitted-millis))
                 (not (invoke-id-executing? node-exec *invoke-id)))
            (:>)
          ))
      ))))

(defn hook:checker-finished [])
(defn hook:checker-finished* [] (hook:checker-finished))
(defn hook:stall-detected [agent-task-id agent-id retry-num])
(defn hook:stall-detected*
  [agent-task-id agent-id retry-num]
  (hook:stall-detected agent-task-id agent-id retry-num))

(defn log-warn
  [msg data]
  (cljlogging/warn msg data))

(defn declare-check-impl
  [mb-topology agent-name]
  (let [check-tick-sym        (symbol (po/agent-check-tick-depot-name
                                       agent-name))
        agent-depot-sym       (symbol (po/agent-depot-name agent-name))
        failure-depot-sym     (symbol (po/agent-failures-depot-name agent-name))
        gc-depot-sym          (symbol (po/agent-gc-valid-invokes-depot-name
                                       agent-name))
        agent-root-pstate-sym (symbol (po/agent-root-task-global-name
                                       agent-name))

        agent-valid-invokes-pstate-sym
        (symbol (po/agent-valid-invokes-task-global-name agent-name))

        pending-retries-pstate-sym
        (symbol (po/pending-retries-task-global-name agent-name))

        uniqued-sym           (symbol (str "$$uniqued-" agent-name))]
    (<<sources mb-topology
     (source> check-tick-sym :> %microbatch)
      (%microbatch)
      (<<batch
        (stalled-agent-ids agent-name
                           :> *agent-task-id *agent-id *retry-num)
        (log-warn "Detected stall"
                  {:agent-name    agent-name
                   :agent-task-id *agent-task-id
                   :agent-id      *agent-id
                   :retry-num     *retry-num})
        (+group-by [*agent-task-id *agent-id]
          (aggs/+max *retry-num :> *retry-num))
        (hook:stall-detected* *agent-task-id *agent-id *retry-num)
        (depot-partition-append!
         failure-depot-sym
         (aor-types/->valid-AgentFailure *agent-task-id *agent-id *retry-num)
         :append-ack))
      (hook:checker-finished*)

     (source> failure-depot-sym :> %microbatch)
      ;; this needs to happen here so that the updates to valid-invokes-pstate
      ;; in the previous microbatch have been committed
      (<<batch
        (|all)
        (local-select> MAP-KEYS
                       pending-retries-pstate-sym
                       {:allow-yield? true}
                       :> [*agent-task-id *agent-id *retry-num :as *tuple])
        (local-transform> [(keypath *tuple) NONE>] pending-retries-pstate-sym)
        (depot-partition-append!
         agent-depot-sym
         (aor-types/->valid-RetryAgentInvoke
          *agent-task-id
          *agent-id
          *retry-num)
         :append-ack))
      (<<batch
        (%microbatch :> *data)
        (filter> (not (keyword? *data)))
        (identity *data :> {:keys [*agent-task-id *agent-id *retry-num]})
        (+group-by [*agent-task-id *agent-id]
          (aggs/+max *retry-num :> *retry-num))
        (materialize> *agent-task-id *agent-id *retry-num :> uniqued-sym))
      (<<batch
        (uniqued-sym :> *agent-task-id *agent-id *retry-num)
        (|direct *agent-task-id)
        (local-select> [(keypath *agent-id) :retry-num (pred= *retry-num)]
                       agent-root-pstate-sym)
        (local-transform> [(keypath [*agent-task-id *agent-id *retry-num])
                           (termval nil)]
                          pending-retries-pstate-sym)
        (inc *retry-num :> *next-retry-num)
        (|all)
        (local-transform> [(keypath [*agent-task-id *agent-id])
                           (termval *next-retry-num)]
                          agent-valid-invokes-pstate-sym))
      (<<batch
        (uniqued-sym :> *agent-task-id *agent-id *retry-num)
        (|global)
        (aggs/+count :> *num-pending)
        (<<if (> *num-pending 0)
          (depot-partition-append!
           failure-depot-sym
           ::trigger
           :append-ack)))

     (source> gc-depot-sym :> %microbatch)
      (<<batch
        (%microbatch :> *tuple)
        (|all)
        (local-transform> [(keypath *tuple) NONE>]
                          agent-valid-invokes-pstate-sym))
    )))
