(ns com.rpl.agent.cluster-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :refer :all]
   [com.rpl.rama :refer :all]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.cluster-agent :refer [ClusterAgentModule WorkerAgentModule]]))

(deftest cluster-agent-test
  (testing "ClusterAgent example demonstrates cross-module communication correctly"
    (with-open [ipc (rtest/create-ipc)]
      ;; Deploy both modules to simulate cluster deployment
      (rtest/launch-module! ipc ClusterAgentModule {:tasks 2 :threads 2})
      (rtest/launch-module! ipc WorkerAgentModule {:tasks 2 :threads 2})

      (let [coord-manager (agent-manager ipc (get-module-name ClusterAgentModule))
            worker-manager (agent-manager ipc (get-module-name WorkerAgentModule))
            coordinator-agent (agent-client coord-manager "CoordinatorAgent")
            worker-agent (agent-client worker-manager "WorkerAgent")]

        (testing "coordinator agent manages cross-module coordination"
          (let [result (agent-invoke coordinator-agent
                                     {:task-id "test-task-001"
                                      :workers 3
                                      :data ["item1" "item2" "item3" "item4" "item5"]})]

            ;; Verify coordinator result structure
            (is (= "coordination-complete" (:action result)))
            (is (= "test-task-001" (:task-id result)))
            (is (= 3 (:workers result)))
            (is (number? (:processed-at result)))

            ;; Verify coordination result details
            (let [coord-result (:coordination-result result)]
              (is (= 3 (:processed-workers coord-result)))
              (is (= 5 (:total-data-size coord-result)))
              (is (= true (:coordination-complete coord-result))))))

        (testing "worker agent processes assigned work correctly"
          (let [result (agent-invoke worker-agent
                                     {:worker-id "test-worker-1"
                                      :task "test-task-001"
                                      :items ["item1" "item2"]})]

            ;; Verify worker result structure
            (is (= "work-complete" (:action result)))
            (is (= "test-worker-1" (:worker-id result)))
            (is (= "test-task-001" (:task result)))
            (is (number? (:processed-at result)))

            ;; Verify work result details
            (let [work-result (:work-result result)]
              (is (= 2 (:items-processed work-result)))
              (is (= 100 (:processing-time work-result))) ; 2 items * 50ms each
              (is (= "test-worker-1" (:worker-id work-result))))))

        (testing "multiple worker agents can work in parallel"
          (let [worker1-result (agent-invoke worker-agent
                                             {:worker-id "parallel-worker-1"
                                              :task "parallel-task"
                                              :items ["a" "b"]})
                worker2-result (agent-invoke worker-agent
                                             {:worker-id "parallel-worker-2"
                                              :task "parallel-task"
                                              :items ["c" "d" "e"]})]

            ;; Verify both workers completed successfully
            (is (= "work-complete" (:action worker1-result)))
            (is (= "work-complete" (:action worker2-result)))

            ;; Verify different worker IDs and correct item processing
            (is (= "parallel-worker-1" (:worker-id worker1-result)))
            (is (= "parallel-worker-2" (:worker-id worker2-result)))
            (is (= 2 (get-in worker1-result [:work-result :items-processed])))
            (is (= 3 (get-in worker2-result [:work-result :items-processed])))))))))