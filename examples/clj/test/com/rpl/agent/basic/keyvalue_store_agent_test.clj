(ns com.rpl.agent.basic.keyvalue-store-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.keyvalue-store-agent :refer [KeyValueStoreModule]]))

(deftest keyvalue-store-agent-test
  (System/gc)
  (testing "KeyValueStoreAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc KeyValueStoreModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        KeyValueStoreModule))
            agent   (aor/agent-client manager "KeyValueStoreAgent")]

        (testing "set operation stores value"
          (let [result (aor/agent-invoke agent
                                         {:counter-name "test-counter"
                                          :operation    :set
                                          :value        42})]
            (is (= :set (:action result)))
            (is (= "test-counter" (:counter result)))
            (is (= 42 (:value result)))))

        (testing "get operation retrieves stored value"
          (let [result (aor/agent-invoke agent
                                         {:counter-name "test-counter"
                                          :operation    :get})]
            (is (= :get (:action result)))
            (is (= "test-counter" (:counter result)))
            (is (= 42 (:value result)))))

        (testing "increment operation increases value"
          (let [result (aor/agent-invoke agent
                                         {:counter-name "test-counter"
                                          :operation    :increment})]
            (is (= :increment (:action result)))
            (is (= "test-counter" (:counter result)))
            (is (= 42 (:previous-value result)))
            (is (= 43 (:new-value result)))))

        (testing "update operation adds to existing value"
          (let [result (aor/agent-invoke agent
                                         {:counter-name "test-counter"
                                          :operation    :update
                                          :value        7})]
            (is (= :update (:action result)))
            (is (= "test-counter" (:counter result)))
            (is (= 43 (:previous-value result)))
            (is (= 7 (:added-value result)))
            (is (= 50 (:new-value result)))))

        (testing "increment on non-existent counter starts at 0"
          (let [result (aor/agent-invoke agent
                                         {:counter-name "new-counter"
                                          :operation    :increment})]
            (is (= :increment (:action result)))
            (is (= "new-counter" (:counter result)))
            (is (= 0 (:previous-value result)))
            (is (= 1 (:new-value result)))))))))
