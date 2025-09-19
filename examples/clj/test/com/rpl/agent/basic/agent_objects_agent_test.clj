(ns com.rpl.agent.basic.agent-objects-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.agent-objects-agent :refer [AgentObjectsModule]]))

(deftest agent-objects-agent-test
  (testing "AgentObjectsAgent example produces expected results"
    (System/gc)
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AgentObjectsModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name AgentObjectsModule))
            agent   (aor/agent-client manager "AgentObjectsAgent")]

        (testing "returns formatted messages with version and counter"
          (let [result1 (aor/agent-invoke agent "Hello")
                result2 (aor/agent-invoke agent "World")]
            (is (= "v1.2.3: Hello (#1 -> alerts)" result1))
            (is (= "v1.2.3: World (#1 -> alerts)" result2))))

        (testing "concurrent invocations return formatted messages"
          (let [invoke1 (aor/agent-initiate agent "Test1")
                invoke2 (aor/agent-initiate agent "Test2")
                result1 (aor/agent-result agent invoke1)
                result2 (aor/agent-result agent invoke2)]
            (is (= "v1.2.3: Test1 (#1 -> alerts)" result1))
            (is (= "v1.2.3: Test2 (#1 -> alerts)" result2))))))))
