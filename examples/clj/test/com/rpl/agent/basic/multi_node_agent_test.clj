(ns com.rpl.agent.basic.multi-node-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.multi-node-agent :refer [MultiNodeAgentModule]]))

(deftest multi-node-agent-test
  (System/gc)
  (testing "MultiNodeAgent greeting workflow"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MultiNodeAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name
                      MultiNodeAgentModule))
            agent   (aor/agent-client manager "MultiNodeAgent")]

        (testing "creates complete greeting message for Alice"
          (let [result (aor/agent-invoke agent "Alice")]
            (is
             (=
              "Hello, Alice! Welcome to agent-o-rama! Thanks for joining us, Alice."
              result))))

        (testing "creates complete greeting message for Bob"
          (let [result (aor/agent-invoke agent "Bob")]
            (is
             (=
              "Hello, Bob! Welcome to agent-o-rama! Thanks for joining us, Bob."
              result))))))))
