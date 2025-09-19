(ns com.rpl.agent.basic.multi-agg-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.multi-agg-agent :refer [MultiAggAgentModule]]))

(deftest multi-agg-agent-test
  (System/gc)
  (testing "MultiAggAgent example produces expected tagged aggregation behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MultiAggAgentModule {:tasks 2 :threads 2})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        MultiAggAgentModule))
            agent   (aor/agent-client manager "MultiAggAgent")]

        (testing "processes mixed data types with custom aggregation"
          (let [result (aor/agent-invoke agent
                                         {:numbers [5 10 15]
                                          :text    ["test" "hello world" "foo"]})]
            ;; Verify summary calculations
            (let [summary (:summary result)]
              (is (= 3 (:numbers-processed summary)))
              (is (= 3 (:text-processed summary)))

              ;; Sum of numbers: 5 + 10 + 15 = 30
              (is (= 30 (:number-sum summary)))

              ;; Sum of squares: 5² + 10² + 15² = 25 + 100 + 225 = 350
              (is (= 350 (:square-sum summary)))

              ;; Even count: only 10 is even
              (is (= 1 (:even-count summary)))

              ;; Total words: "test" (1) + "hello world" (2) + "foo" (1) = 4
              (is (= 4 (:total-words summary)))

              ;; Total characters: 4 + 11 + 3 = 18
              (is (= 18 (:total-characters summary))))

            ;; Verify detailed results structure (order not guaranteed)
            (let [details       (:details result)
                  numbers       (:numbers details)
                  number-values (set (map :value numbers))]
              ;; Numbers analysis
              (is (= 3 (count numbers)))
              (is (= #{5 10 15} number-values))

              ;; Check each number has correct fields
              (doseq [num-analysis numbers]
                (let [v (:value num-analysis)]
                  (is (= (* v v) (:square num-analysis)))
                  (is (= (even? v) (:even? num-analysis)))))

              ;; Text analysis
              (let [texts       (:text details)
                    text-values (set (map :value texts))]
                (is (= 3 (count texts)))
                (is (= #{"test" "hello world" "foo"} text-values))

                ;; Check each text has correct fields
                (doseq [text-analysis texts]
                  (let [v (:value text-analysis)]
                    (is (= (count v) (:length text-analysis)))
                    (is (= (clojure.string/upper-case v) (:uppercase text-analysis)))
                    (is (= (count (clojure.string/split v #"\s+"))
                           (:words text-analysis)))))))))

        (testing "handles empty collections correctly"
          (let [result (aor/agent-invoke agent
                                         {:numbers []
                                          :text    []})]
            (let [summary (:summary result)]
              (is (= 0 (:numbers-processed summary)))
              (is (= 0 (:text-processed summary)))
              (is (= 0 (:number-sum summary)))
              (is (= 0 (:square-sum summary)))
              (is (= 0 (:even-count summary)))
              (is (= 0 (:total-words summary)))
              (is (= 0 (:total-characters summary))))))

        (testing "processes larger datasets correctly"
          (let [result (aor/agent-invoke agent
                                         {:numbers [1 2 3 4 5 6 7 8 9 10]
                                          :text    ["Multi-agg is powerful"
                                                    "Parallel processing"
                                                    "State management"]})]
            (let [summary (:summary result)]
              (is (= 10 (:numbers-processed summary)))
              (is (= 3 (:text-processed summary)))

              ;; Sum: 1+2+3+4+5+6+7+8+9+10 = 55
              (is (= 55 (:number-sum summary)))

              ;; Sum of squares: 1+4+9+16+25+36+49+64+81+100 = 385
              (is (= 385 (:square-sum summary)))

              ;; Even numbers: 2,4,6,8,10 = 5
              (is (= 5 (:even-count summary)))

              ;; Words: 3 + 2 + 2 = 7
              (is (= 7 (:total-words summary)))

              ;; Characters: 21 + 19 + 16 = 56
              (is (= 56 (:total-characters summary))))))))))
