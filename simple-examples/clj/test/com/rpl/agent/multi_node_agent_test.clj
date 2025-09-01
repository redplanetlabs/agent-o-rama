(ns com.rpl.agent.multi-node-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.multi-node-agent :refer [MultiNodeAgentModule]]))

(deftest multi-node-agent-test
  (testing "MultiNodeAgent example runs without error"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MultiNodeAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name MultiNodeAgentModule))
            agent (aor/agent-client manager "MultiNodeAgent")]
        
        (testing "agent processes input through multiple nodes successfully"
          (let [result (aor/agent-invoke agent 42)]
            (is (some? result))))))))