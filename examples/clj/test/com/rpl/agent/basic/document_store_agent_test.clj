(ns com.rpl.agent.basic.document-store-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.document-store-agent :refer [DocumentStoreModule]]))

(deftest document-store-agent-test
  (System/gc)
  (testing "DocumentStoreAgent with simplified user profiles"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc DocumentStoreModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        DocumentStoreModule))
            agent   (aor/agent-client manager "DocumentStoreAgent")]

        (testing "creates and stores user profile"
          (let [result (aor/agent-invoke
                        agent
                        {:user-id         "test-user"
                         :profile-updates {:name        "Test User"
                                           :age         25
                                           :preferences {:theme "dark"}}})]
            (is (= "test-user" (:user-id result)))
            (is (= "Test User" (:name result)))
            (is (= 25 (:age result)))
            (is (= {:theme "dark"} (:preferences result)))))

        (testing "updates individual fields independently"
          (let [result (aor/agent-invoke
                        agent
                        {:user-id         "test-user"
                         :profile-updates {:age         30
                                           :preferences {:notifications true}}})]
            (is (= 30 (:age result)))
            (is (= "Test User" (:name result))) ; Name unchanged
            ;; Preferences should be merged
            (is (= {:theme "dark" :notifications true} (:preferences result)))))

        (testing "handles multiple users"
          (let [result (aor/agent-invoke
                        agent
                        {:user-id         "user2"
                         :profile-updates {:name        "Another User"
                                           :age         35
                                           :preferences {:theme "light"}}})]
            (is (= "user2" (:user-id result)))
            (is (= "Another User" (:name result)))
            (is (= 35 (:age result)))
            (is (= {:theme "light"} (:preferences result)))))))))
