(ns com.rpl.agent.basic.module-update-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.module-update-agent :as mua]))

(deftest module-update-test
  ;; Tests that module updates work correctly with :continue mode
  (testing "CounterAgent"
    (testing "continues execution after module update"
      (with-open [ipc (rtest/create-ipc)]
        ;; Deploy Version 1
        (rtest/launch-module! ipc mua/CounterModule {:tasks 1 :threads 1})

        (let [module-name "CounterModule"
              manager (aor/agent-manager ipc module-name)]

          ;; Start counter
          (let [agent (aor/agent-client manager "CounterAgent")
                invoke-id (aor/agent-initiate agent 0)]

            ;; Let it count a bit with Version 1
            (Thread/sleep 2500)

            ;; Update to Version 2
            (rama/update-module! ipc mua/CounterModuleV2)

            ;; Get final result
            (let [final-count (aor/agent-result agent invoke-id)]
              ;; Should have counted with both increment styles
              (is (pos? final-count))
              (is (<= final-count 30)))))))))