(ns com.rpl.agent.basic.basic-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.basic-agent :refer [BasicAgentModule]]))

(deftest basic-agent-test
  (System/gc)
  (testing "BasicAgent example"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc BasicAgentModule {:tasks 1 :threads 1})
      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name BasicAgentModule))
            agent   (aor/agent-client manager "BasicAgent")]

        (testing "welcomes user by name"
          (is (= "Welcome to agent-o-rama, Alice!"
                 (aor/agent-invoke agent "Alice"))))

        (testing "welcomes different user by name"
          (is (= "Welcome to agent-o-rama, Bob!"
                 (aor/agent-invoke agent "Bob"))))))))
