(ns com.rpl.agent-o-rama.impl.partitioner
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

(defn hook:filtered-event [agent-task-id agent-id retry-num])

(deframafn valid-retry-num?
  [*agent-name *agent-task-id *agent-id *retry-num]
  (<<with-substitutions
   [$$valid (po/agent-valid-invokes-task-global *agent-name)]
   (local-select> (keypath [*agent-task-id *agent-id])
                  $$valid
                  :> *valid-retry-num)
   (:> (or> (nil? *valid-retry-num) (= *valid-retry-num *retry-num)))))

(deframaop filter-valid-retry-num>
  [*agent-name *agent-task-id *agent-id *retry-num]
  (<<if (valid-retry-num? *agent-name *agent-task-id *agent-id *retry-num)
    (:>)
   (else>)
    (hook:filtered-event *agent-task-id *agent-id *retry-num)))

(defbasicblocksegmacro |aor
  [:<* [[agent-name agent-task-id agent-id retry-num] & partitioner+args]]
  [(vec partitioner+args)
   [filter-valid-retry-num> agent-name agent-task-id agent-id retry-num]])

(defdepotpartitioner agent-task-id-depot-partitioner
  [{:keys [agent-task-id]} num-partitions]
  agent-task-id)

(defdepotpartitioner human-depot-partitioner
  [data num-partitions]
  (cond
    (aor-types/NodeHumanInputRequest? data)
    (:agent-task-id data)

    (aor-types/HumanInput? data)
    (-> data
        :request
        :node-task-id)

    :else
    (throw (h/ex-info "Unexpected type" {:type (class data)}))
  ))

(defn next-agent-task
  [num-partitions]
  (rand-int num-partitions))

(defdepotpartitioner agent-depot-partitioner
  [data num-partitions]
  (cond (or (aor-types/NodeComplete? data)
            (aor-types/NodeFailure? data))
        (:task-id data)

        (aor-types/ForkAgentInvoke? data)
        (:agent-task-id data)

        :else
        (next-agent-task num-partitions)))

(defn task-id-key-partitioner
  [num-partitions task-id]
  task-id)
