(ns com.rpl.agent.basic.record-op-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.record-op-agent :refer [RecordOpAgentModule]]))

(deftest record-op-agent-test
  ;; Test that the agent executes correctly and returns expected results
  (System/gc)
  (testing "RecordOpAgent example"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc RecordOpAgentModule {:tasks 1 :threads 1})
      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name RecordOpAgentModule))
            agent   (aor/agent-client manager "RecordOpAgent")]

        (testing "generates greeting for user"
          (is (= "Hello, Alice!"
                 (aor/agent-invoke agent "Alice"))))

        (testing "generates greeting for different user"
          (is (= "Hello, Bob!"
                 (aor/agent-invoke agent "Bob"))))))))