(ns com.rpl.agent-o-rama.impl.retries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po]))


(defn declare-check-impl
  [mb-topology agent-name]
  (let [check-tick-sym          (symbol (po/agent-check-tick-depot-name
                                         agent-name))
        agent-depot-sym         (symbol (po/agent-depot-name agent-name))
        agent-node-pstate-sym   (symbol (po/agent-node-task-global-name name))
        agent-invoke-pstate-sym (symbol (po/agent-invoke-task-global-name name))

       ]
    (<<sources mb-topology
     (source> check-tick-sym :> %microbatch)
      (%microbatch)
      ;; TODO: <<<<>>>>
      ;;   - need to know:
      ;;      - last updated time for each agent
      ;;      - which agents are active
      ;;      - which nodes are currently executing
      ;;        - query task global for this
      ;;      - algorithm:
      ;;        - for all active agents
      ;;        - for those that have last updated time greater than threshhold
      ;;        (e.g. 10s)
      ;;        - walk the execution graph to see if there's a node that hasn't
      ;;        finished but doesn't have active node
      ;;          - is it possible to race with actual execution being sent
      ;;          over?
      ;;          - should do it based on a certain amount of time passing since
      ;;          the finished-time of the sending node

    )))
