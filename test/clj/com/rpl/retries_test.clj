(ns com.rpl.retries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.rama.helpers
    TopologyUtils]))

(def SEM)

(deframafn short-checker-threshold-millis
  []
  (:> 100))

;; TODO: <<<<<>>>> this test will need to set max retries to 0
(deftest retries-checker-test
  (with-redefs [SEM (h/mk-semaphore 0)
                retries/DEFAULT-CHECKER-TICK-MILLIS 10
                retries/checker-threshold-millis short-checker-threshold-millis]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (->
            topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node]

             )))))
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))

       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind inv (aor/agent-initiate foo))


       ; (rtest/pause-microbatch-topology! ipc
       ;                                   module-name
       ;                                   aor-types/AGENTS-MB-TOPOLOGY-NAME)
       ; (rtest/resume-microbatch-topology! ipc
       ;                                    module-name
       ;                                    aor-types/AGENTS-MB-TOPOLOGY-NAME)
       ;; TODO: <<<<<>>>>>
       ;;   - verify failures going to retry checker
       ;;   - verify it uniques failure requests
       ;;   - check stalling on:
       ;;      - regular node execution
       ;;      - regular node never being received (after the 10s timeout)
       ;;      - agg node execution
       ;;      - something after agg node (verify it keeps going on emits)
       ;;      - agg graph stall
       ;;      - multiple stalls in one agent run get deduplicated
       ;;  - check that it does the broadcast
       ;;  - check that events from prior executions get filtered
       ;;      - can use semaphore to stall the virtual thread invoke, then
       ;;      manually cause a stall and release
       ;;      - store writes
       ;;      - subsequent node invokes
       ;;      - check prime between NodeComplete and processing of emit
       ;;      (there's a filter there)
      ))))
