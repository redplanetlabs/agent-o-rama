(ns com.rpl.agent.basic.async-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.async-agent :refer [AsyncAgentModule]]))

(deftest async-agent-test
  (testing "AsyncAgent example produces expected results"
    (System/gc)
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AsyncAgentModule {:tasks 1 :threads 1})
      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name AsyncAgentModule))
            agent   (aor/agent-client manager "AsyncAgent")]
        (testing "synchronous invocation produces expected result"
          (let [result (aor/agent-invoke agent "Test Task")]
            (is (= "Task 'Test Task' completed successfully" result))))

        (testing "asynchronous initiation and result produces expected result"
          (let [invoke (aor/agent-initiate agent "Async Task")
                result (aor/agent-result agent invoke)]
            (is (= "Task 'Async Task' completed successfully" result))))))))
