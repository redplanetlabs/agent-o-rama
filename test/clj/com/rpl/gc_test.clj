(ns com.rpl.gc-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]))

(deftest gc-by-task-test
  ;; TODO: <<<<>>>>>
  ;;  - can target task ID be controlled here?
  ;;      - yes, by redef of something in agent-depot-partitioner
  ;;  - sub tick depot for regular depot
  ;;  - verify full trace is GC'd after enough iterations
  ;;  - verify GC of restarted traces (special case)
  ;;  - verify removal from $$gc
  ;;  - verify $$root-count maintained correctly
  ;;    - check with forks, retries, restarts
  )
