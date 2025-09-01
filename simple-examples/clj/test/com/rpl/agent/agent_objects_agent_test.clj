(ns com.rpl.agent.agent-objects-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.agent-objects-agent :refer [AgentObjectsModule]]))

(deftest agent-objects-agent-test
  (testing "AgentObjectsAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AgentObjectsModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name AgentObjectsModule))
            agent (aor/agent-client manager "AgentObjectsAgent")]
        
        (testing "first invocation produces expected result structure"
          (let [result (aor/agent-invoke agent "Hello")]
            (is (= "Hello" (:processed-input result)))
            (is (= 1 (:usage-count result)))
            (is (string? (:service-info result)))
            (is (map? (:system-info result)))
            (is (= "1.2.3" (get-in result [:system-info :version])))
            (is (number? (:processed-at result)))))
        
        (testing "second invocation increments usage count"
          (let [result (aor/agent-invoke agent "World")]
            (is (= "World" (:processed-input result)))
            (is (= 2 (:usage-count result)))))))))