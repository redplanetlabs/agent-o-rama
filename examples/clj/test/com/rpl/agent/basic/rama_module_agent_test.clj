(ns com.rpl.agent.basic.rama-module-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.rama-module-agent :refer [RamaModuleAgent]]))

(deftest rama-module-agent-test
  ;; Test demonstrating Rama module with agents and depot integration
  (System/gc)
  (testing "RamaModuleAgent example"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc RamaModuleAgent {:tasks 1 :threads 1})

      (let [module-name (rama/get-module-name RamaModuleAgent)]
        (testing "depot is accessible and can receive data"
          (let [depot (rama/foreign-depot ipc module-name "*example-depot")]
            (is (some? depot))
            ;; Verify depot accepts data (stream processing happens asynchronously)
            (rama/foreign-append! depot "test-data")))

        (let [manager (aor/agent-manager ipc module-name)
              agent (aor/agent-client manager "FeedbackAgent")]

          (testing "agent is available in module"
            (is (contains? (set (aor/agent-names manager)) "FeedbackAgent")))

          (testing "processes feedback and returns success with message"
            (let [result (aor/agent-invoke agent "Great product!")]
              (is (= :success (:status result)))
              (is (= "Processed: Great product!" (:message result)))
              (is (= 14 (:length result)))))

          (testing "processes different feedback correctly"
            (let [result (aor/agent-invoke agent "Needs work")]
              (is (= :success (:status result)))
              (is (= "Processed: Needs work" (:message result)))
              (is (= 10 (:length result))))))))))