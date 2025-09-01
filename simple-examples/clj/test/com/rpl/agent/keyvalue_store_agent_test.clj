(ns com.rpl.agent.keyvalue-store-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.keyvalue-store-agent :refer [KeyValueStoreModule]]))

(deftest keyvalue-store-agent-test
  (testing "KeyValueStoreAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc KeyValueStoreModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name KeyValueStoreModule))
            agent (aor/agent-client manager "KeyValueStoreAgent")]
        
        (testing "first invocation initializes counter and user data"
          (let [result (aor/agent-invoke agent {:counter-name "test-counter" :user-id "test-user"})]
            (is (= "counter-increment" (:action result)))
            (is (= "test-counter" (:counter result)))
            (is (= 1 (:new-count result)))
            (is (= 1 (:total-user-interactions result)))
            (is (= "test-user" (get-in result [:user-data :name])))))
        
        (testing "second invocation increments counter and adds interaction"
          (let [result (aor/agent-invoke agent {:counter-name "test-counter" :user-id "test-user"})]
            (is (= 2 (:new-count result)))
            (is (= 2 (:total-user-interactions result)))
            (is (= 2 (count (get-in result [:user-data :interactions]))))))
        
        (testing "different counter name starts at 1"
          (let [result (aor/agent-invoke agent {:counter-name "other-counter" :user-id "test-user"})]
            (is (= "other-counter" (:counter result)))
            (is (= 1 (:new-count result)))
            (is (= 3 (:total-user-interactions result))))))))))