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
     (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))

     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind config-depot
       (foreign-depot ipc module-name (po/agent-config-depot-name "foo")))
     (bind global-actions-depot
       (foreign-depot ipc module-name (po/global-actions-depot-name)))
     (bind config-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-config-task-global-name "foo")))
     (bind global-config-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-global-config-task-global-name)))

     (bind confirm!
       (fn [m]
         (dotimes [i 4]
           (let [curr (foreign-select-one STAY config-pstate {:pkey i})]
             (when-not (= curr m)
               (throw (ex-info "Mismatch" {:curr curr :target m})))))))

     (bind global-confirm!
       (fn [m]
         (dotimes [i 4]
           (let [curr (foreign-select-one STAY global-config-pstate {:pkey i})]
             (when-not (= curr m)
               (throw (ex-info "Mismatch global" {:curr curr :target m})))))))

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


     (global-confirm! nil)
     (foreign-append! global-actions-depot (aor-types/change-max-limited-actions-concurrency 100))
     (global-confirm! {"max.limited.actions.concurrency" 100})
     (foreign-append! global-actions-depot (aor-types/change-max-limited-actions-concurrency 50))
     (global-confirm! {"max.limited.actions.concurrency" 50})
     (is (thrown? Exception
                  (aor-types/change-max-limited-actions-concurrency -1)))
     (is (thrown? Exception
                  (aor-types/change-max-limited-actions-concurrency 2.5)))
    )))
