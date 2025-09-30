(ns com.rpl.record-nested-op-test
  (:require
   [clojure.test :refer [deftest is testing]])
  (:import
   [com.rpl.aortest
    RecordNestedOpTest]))

(deftest record-nested-op-test
  (testing "AgentNode.recordNestedOp functionality"
    (is (RecordNestedOpTest/runAllTests))))