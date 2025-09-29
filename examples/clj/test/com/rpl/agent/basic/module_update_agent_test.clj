(ns com.rpl.agent.basic.module-update-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.rama :as rama]
   [com.rpl.agent.basic.module-update-agent :as mua]))

(deftest module-update-test
  ;; Tests that the main function runs without error
  (testing "main function"
    (testing "runs without error"
      (is (nil? (mua/demonstrate-module-update))))))
