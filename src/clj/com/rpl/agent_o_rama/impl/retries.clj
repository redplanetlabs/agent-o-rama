(ns com.rpl.agent-o-rama.impl.retries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po])
  (:import
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]))

(deframafn invalid-time-delta?
  [*time-millis]
  ;; TODO: <<<<<>>>>> make configurable
  (:> (>= (- (h/current-time-millis) *time-millis) 10000)))

(defn invoke-id-executing?
  [^AgentNodeExecutorTaskGlobal node-exec invoke-id]
  (contains? (.getRunningInvokeIds node-exec) invoke-id))

(defgenerator stalled-agent-ids
  [microbatch]
  (batch<- [*agent-task-id *agent-id *retry-num]
    (%microbatch)
    (|all)
    (current-task-id :> *agent-task-id)
    (local-select> MAP-KEYS
                   agent-active-invokes-pstate-sym
                   {:allow-yield? true}
                   :> *agent-id)
    (local-select> (keypath *agent-id)
                   agent-invoke-pstate-sym
                   :> {:keys [*root-invoke-id
                              *start-time-millis
                              *last-progress-time-millis
                              *retry-num]})
    (filter> (invalid-time-delta? *last-progress-time-millis))
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
                     (invalid-time-delta? *emitted-millis))
               (not (invoke-id-executing? node-exec *invoke-id)))
          (:>)
        ))
    )))

(defn declare-check-impl
  [mb-topology name]
  (let [check-tick-sym (symbol (po/agent-check-tick-depot-name name))
        agent-depot-sym (symbol (po/agent-depot-name name))
        failure-depot-sym (symbol (po/agent-failures-depot-name name))

        agent-node-pstate-sym (symbol (po/agent-node-task-global-name name))
        agent-invoke-pstate-sym (symbol (po/agent-invoke-task-global-name name))

        agent-valid-invokes-pstate-sym
        (symbol (po/agent-valid-invokes-task-global-name name))

        agent-active-invokes-pstate-sym
        (symbol (po/agent-active-invokes-task-global-name name))

        node-exec (symbol (po/agent-node-executor-name))]
    (<<sources mb-topology
     (source> check-tick-sym :> %microbatch)
      (<<batch
        (stalled-agent-ids microbatch :> *agent-task-id *agent-id *retry-num)
        (+group-by [*agent-task-id *agent-id]
          (aggs/+max *retry-num :> *retry-num))
        (depot-partition-append!
         failure-depot-sym
         (aor-types/->valid-AgentFailure *agent-task-id *agent-id *retry-num)
         :append-ack))

     (source> failure-depot-sym :> %microbatch)
      (<<batch
        (%microbatch :> {:keys [*agent-task-id *agent-id *retry-num]})
        (+group-by [*agent-task-id *agent-id]
          (aggs/+max *retry-num :> *retry-num))
        (materialize> *agent-task-id *agent-id *retry-num :> $$uniqued))
      (<<batch
        ($$uniqued :> *agent-task-id *agent-id *retry-num)
        (|direct *agent-task-id)
        (local-select> [(keypath *agent-id) :retry-num (pred= *retry-num)]
                       agent-invoke-pstate-sym)
        (materialize> *agent-task-id *agent-id *retry-num :> $$retries)
        (inc *retry-num :> *next-retry-num)
        (|all)
        (local-transform> [(keypath [*agent-task-id *agent-id])
                           (termval *next-retry-num)]
                          agent-valid-invokes-pstate-sym))
      (<<batch
        ($$retries :> *agent-task-id *agent-id *retry-num)
        (depot-partition-append!
         agent-depot-sym
         (aor-types/->valid-RetryAgentInvoke
          *agent-task-id
          *agent-id
          *retry-num)
         :append-ack))
    )))
