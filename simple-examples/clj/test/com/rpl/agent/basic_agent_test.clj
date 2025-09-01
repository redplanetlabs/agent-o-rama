(ns com.rpl.agent.basic-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic-agent :refer [BasicAgentModule]]))

(deftest basic-agent-test
  (testing "BasicAgent example runs without error"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc BasicAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name BasicAgentModule))
            agent (aor/agent-client manager "BasicAgent")]
        
        (testing "agent processes input successfully"
          (let [result (aor/agent-invoke agent "test")]
            (is (some? result))))))))