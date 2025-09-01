(ns com.rpl.agent.async-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.async-agent :refer [AsyncAgentModule]]))

(deftest async-agent-test
  (testing "AsyncAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AsyncAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name AsyncAgentModule))
            agent (aor/agent-client manager "AsyncAgent")]
        
        (testing "synchronous invocation produces expected result structure"
          (let [result (aor/agent-invoke agent {:task-name "Test Task" :duration 50})]
            (is (= "Test Task" (:task result)))
            (is (= 50 (:expected-duration result)))
            (is (number? (:actual-duration result)))
            (is (number? (:completed-at result)))))
        
        (testing "asynchronous initiation and result produces expected structure"
          (let [invoke (aor/agent-initiate agent {:task-name "Async Task" :duration 30})
                result (aor/agent-result agent invoke)]
            (is (= "Async Task" (:task result)))
            (is (= 30 (:expected-duration result)))
            (is (number? (:actual-duration result)))
            (is (number? (:completed-at result)))))))))