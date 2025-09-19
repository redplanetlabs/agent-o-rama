(ns com.rpl.agent.basic.router-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.router-agent :refer [RouterAgentModule]]))

(deftest router-agent-test
  (System/gc)
  (testing "RouterAgent routes messages to appropriate handlers"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc RouterAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name RouterAgentModule))
            agent   (aor/agent-client manager "RouterAgent")]

        (testing "routes urgent messages to urgent handler"
          (let [result (aor/agent-invoke agent "urgent:system failure")]
            (is (= "[HIGH] system failure" result))))

        (testing "routes non-urgent messages to default handler"
          (let [result (aor/agent-invoke agent "just a message")]
            (is (= "[NORMAL] just a message" result))))))))
