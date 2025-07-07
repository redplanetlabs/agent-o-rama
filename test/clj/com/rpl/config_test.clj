(ns com.rpl.config-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.test :as rtest]))


(deftest change-configs-test
  (with-open [ipc (rtest/create-ipc)]
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
             (aor/result! agent-node "abcd")
           )))))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))

     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind config-depot
       (foreign-depot ipc module-name (po/agent-config-depot-name "foo")))
     (bind config-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-config-task-global-name "foo")))

     (bind confirm!
       (fn [m]
         (dotimes [i 4]
           (let [curr (foreign-select-one STAY config-pstate {:pkey i})]
             (when-not (= curr m)
               (throw (ex-info "Mismatch" {:curr curr :target m})))))))

     (confirm! nil)
     (foreign-append! config-depot (aor-types/change-max-retries 0))
     (confirm! {"max.retries" 0})
     (foreign-append! config-depot (aor-types/change-max-retries 1000))
     (confirm! {"max.retries" 1000})
     (foreign-append! config-depot
                      (aor-types/change-stall-checker-threshold-millis 5))
     (confirm! {"max.retries" 1000 "stall.checker.threshold.millis" 5})

     (is (thrown? Exception
                  (aor-types/change-max-retries -1)))

     (aor-types/change-stall-checker-threshold-millis 1)
     (is (thrown? Exception
                  (aor-types/change-stall-checker-threshold-millis 0)))
     (is (thrown? Exception
                  (aor-types/change-stall-checker-threshold-millis -1)))
    )))
