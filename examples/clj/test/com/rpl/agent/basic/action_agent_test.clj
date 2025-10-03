(ns com.rpl.agent.basic.action-agent-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent.basic.action-agent :as action-agent]))

(deftest action-agent-test
  ;; Test verifies the action example runs without errors
  ;; and produces expected output
  (System/gc)
  (testing "ActionAgent example"
    (testing "runs -main without errors"
      (let [output (with-out-str
                     (action-agent/-main))]

        (testing "shows invocations with results"
          (is (str/includes? output "Hi"))
          (is (str/includes? output "Hi!"))
          (is (str/includes? output "Hello World"))
          (is (str/includes? output "Hello World!")))

        (testing "reports action triggers"
          (is (str/includes? output "Actions triggered")))

        (testing "confirms rule deletion"
          (is (str/includes? output "Rule deleted")))))))
