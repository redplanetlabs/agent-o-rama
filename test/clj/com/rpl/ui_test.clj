(ns com.rpl.ui-test
  (:require
   [clojure.test :refer [deftest is testing]])
  (:import
   [com.rpl.aortest
    UITest]))

(deftest ui-java-api-test
  (testing "UI Java API functionality"
    (is (UITest/runAllTests))))
