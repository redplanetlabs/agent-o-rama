(ns com.rpl.agent.basic.dataset-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.dataset-agent :refer [DatasetExampleModule math-input-schema math-output-schema]]
   [clojure.string :as str]))

(deftest dataset-agent-test
  ;; Test that the dataset agent runs without errors
  (testing "dataset-agent"
    (testing "runs without errors"
      (is (nil? (com.rpl.agent.basic.dataset-agent/-main))
          "Main function should complete without throwing exceptions"))))
