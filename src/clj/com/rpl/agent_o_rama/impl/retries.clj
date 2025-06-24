(ns com.rpl.agent-o-rama.impl.retries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]))

(deframafn invalid-time-delta?
  [*time-millis]
  ;; TODO: <<<<<>>>>> make configurable
  (:> (>= (- (h/current-time-millis) *time-millis) 10000)))

(defn declare-check-impl
  [mb-topology name]
  (let [check-tick-sym          (symbol (po/agent-check-tick-depot-name name))
        agent-depot-sym         (symbol (po/agent-depot-name name))
        agent-node-pstate-sym   (symbol (po/agent-node-task-global-name name))
        agent-invoke-pstate-sym (symbol (po/agent-invoke-task-global-name name))

        agent-active-invokes-pstate-sym
        (symbol (po/agent-active-invokes-task-global-name name))
       ]
    (<<sources mb-topology
     (source> check-tick-sym :> %microbatch)
      (%microbatch)
      (|all)
      (local-select> MAP-KEYS
                     agent-active-invokes-pstate-sym
                     {:allow-yield? true}
                     :> *agent-id)
      (local-select> (keypath *agent-id)
                     agent-invoke-pstate-sym
                     :> {:keys [*root-invoke-id *last-progress-time-millis]})
      (filter> (invalid-time-delta? *last-progress-time-millis))
      (loop<- [*invoke-id *root-invoke-id]
              ;; TODO: <<<<>>>>
              ;;   - if regular node, just walk emits
              ;;   - agg start node should:
              ;;      - check if agg node already is finished
              ;;        - if so, continue from its emits
              ;;      - otherwise, just walk regular emits to see if there's a
              ;;      violation
              ;;        - eventually as to whether it's recorded in agg-inputs
              ;;          - no, that's not indexed by invoke-id
              ;;      - can instead look at if >10s has elapsed since last
              ;;       emit to agg invoke and agg hasn't finished yet
              ;;         - but this requires aggregation of the "last emitted to
              ;;         agg" times
              ;;         - alternatively, could materialize a subindexed set of
              ;;         invoke IDs that's written atomically
      )

      ;; TODO: <<<<>>>>
      ;;   - walk the execution graph to see if there's a node that hasn't
      ;;   finished but doesn't have active node, or a node that should exist
      ;;   but it's been more than Ns since the emit
      ;;     - is it possible to race with actual execution being sent
      ;;     over?
      ;;       - should do it based on a certain amount of time passing since
      ;;       the finished-time of the sending node

    )))
