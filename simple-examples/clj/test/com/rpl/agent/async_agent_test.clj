(ns com.rpl.agent.async-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.async-agent :refer [AsyncAgentModule]]))

(deftest async-agent-test
  (testing "AsyncAgent example runs without error"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AsyncAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name AsyncAgentModule))
            agent (aor/agent-client manager "AsyncAgent")]
        
        (testing "agent handles async execution successfully"
          (let [result (aor/agent-invoke agent {:task-name "Test" :duration 50})]
            (is (some? result))))))))