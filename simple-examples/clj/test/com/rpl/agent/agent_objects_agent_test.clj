(ns com.rpl.agent.agent-objects-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.agent-objects-agent :refer [AgentObjectsModule]]))

(deftest agent-objects-agent-test
  (testing "AgentObjectsAgent example runs without error"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AgentObjectsModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name AgentObjectsModule))
            agent (aor/agent-client manager "AgentObjectsAgent")]
        
        (testing "agent uses shared objects successfully"
          (let [result (aor/agent-invoke agent "test")]
            (is (some? result))))))))