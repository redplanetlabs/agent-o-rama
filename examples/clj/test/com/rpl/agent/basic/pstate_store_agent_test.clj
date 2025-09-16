(ns com.rpl.agent.basic.pstate-store-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.pstate-store-agent :refer [PStateStoreModule]]))

(deftest pstate-store-agent-test
  (System/gc)
  (testing "PStateStoreAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc PStateStoreModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name PStateStoreModule))
            agent   (aor/agent-client manager "PStateStoreAgent")]

        (testing "creates company structure and first employee"
          (let [result (aor/agent-invoke
                        agent
                        {:company-id   "test-corp"
                         :company-name "Test Corp"
                         :dept-id      "tech"
                         :dept-name    "Technology"
                         :employee     {:id       "emp1"
                                        :name     "Test Employee"
                                        :salary   80000
                                        :metadata {:level "mid"}}})]
            (is (= "pstate-query" (:action result)))
            (is (= "test-corp" (:company-id result)))
            (is (= "Test Corp" (:company-name result)))
            (is (= "tech" (:dept-id result)))
            (is (= "Technology" (:dept-name result)))
            (is (= 1 (:employee-count result)))
            (is (= 80000 (:average-salary result)))
            (is (= 1 (:department-count result)))
            (is (= ["Test Employee"] (:all-company-employee-names result)))))

        (testing "adds second employee and calculates metrics"
          (let [result (aor/agent-invoke
                        agent
                        {:company-id "test-corp"
                         :dept-id    "tech"
                         :employee   {:id       "emp2"
                                      :name     "Second Employee"
                                      :salary   90000
                                      :metadata {:level "senior"}}})]
            (is (= 2 (:employee-count result)))
            (is (= 85000 (:average-salary result))) ; (80000 + 90000) / 2
            (is (= #{"Test Employee" "Second Employee"}
                   (set (:all-company-employee-names result))))))

        (testing "updates existing employee"
          (let [result (aor/agent-invoke
                        agent
                        {:company-id "test-corp"
                         :dept-id    "tech"
                         :employee   {:id       "emp1" ; Same ID - should
                                                       ; update
                                      :name     "Test Employee"
                                      :salary   100000 ; Updated salary
                                      :metadata {:level "principal"}}})]
            (is (= 2 (:employee-count result))) ; Still 2 employees
            (is (= 95000 (:average-salary result))) ; (100000 + 90000) / 2
          ))))))
