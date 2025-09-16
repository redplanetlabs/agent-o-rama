(ns com.rpl.agent.basic.aggregation-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.aggregation-agent :refer [AggregationAgentModule]]))

(deftest aggregation-agent-test
  (System/gc)
  (testing "AggregationAgent example produces expected fan-out/fan-in behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc AggregationAgentModule {:tasks 2 :threads 2})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        AggregationAgentModule))
            agent   (aor/agent-client manager "AggregationAgent")]

        (testing "processes data in chunks and aggregates results"
          (let [result (aor/agent-invoke agent
                                         {:data       (range 1 13) ; [1 2 3 ...
                                          ; 12]
                                          :chunk-size 4})]
            ;; Verify final result structure
            (is (= 12 (:total-items result)))
            (is (= 3 (:chunks-processed result)))

            ;; Verify total sum calculation
            ;; Each item is squared, so sum = 1² + 2² + ... + 12² = 650
            (is (= 650 (:total-sum result)))

            ;; Verify chunk results structure
            (let [chunk-results (:chunk-results result)]
              (is (= 3 (count chunk-results)))
              (is (every? #(contains? % :original-chunk) chunk-results))
              (is (every? #(contains? % :processed-chunk) chunk-results))
              (is (every? #(contains? % :chunk-sum) chunk-results))

              ;; Verify chunk sums add up to total
              (is (= 650 (reduce + (map :chunk-sum chunk-results))))

              ;; Verify chunk processing (each original value squared)
              (is (= [1 4 9 16] (:processed-chunk (first chunk-results))))
              (is (= [25 36 49 64] (:processed-chunk (second chunk-results))))
              (is (= [81 100 121 144]
                     (:processed-chunk (nth chunk-results 2)))))))

        (testing "handles different chunk sizes correctly"
          (let [result (aor/agent-invoke agent
                                         {:data       (range 1 8) ; [1 2 3 4 5 6
                                          ; 7]
                                          :chunk-size 3})]
            (is (= 7 (:total-items result)))
            (is (= 3 (:chunks-processed result))) ; [1,2,3], [4,5,6], [7]
            ;; Sum = 1² + 2² + ... + 7² = 140
            (is (= 140 (:total-sum result)))))))))
