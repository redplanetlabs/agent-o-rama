(ns com.rpl.agent.basic.mirror-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.mirror-agent :refer [GreeterModule create-mirror-module]]))

(deftest mirror-agent-test
  ;; Test cross-module agent invocation using mirror agents
  (System/gc)
  (testing "MirrorAgent example"
    (with-open [ipc (rtest/create-ipc)]
      ;; Launch both modules
      (rtest/launch-module! ipc GreeterModule {:tasks 1 :threads 1})
      (let [greeter-module-name (rama/get-module-name GreeterModule)
            mirror-module (create-mirror-module greeter-module-name)]
        (rtest/launch-module! ipc mirror-module {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc (rama/get-module-name mirror-module))
              mirror-agent (aor/agent-client manager "MirrorAgent")]

          (testing "invokes Greeter via mirror and adds prefix"
            (is (= "Mirror says: Hello, Alice!"
                   (aor/agent-invoke mirror-agent "Alice"))))

          (testing "invokes Greeter via mirror for different input"
            (is (= "Mirror says: Hello, Bob!"
                   (aor/agent-invoke mirror-agent "Bob")))))))))