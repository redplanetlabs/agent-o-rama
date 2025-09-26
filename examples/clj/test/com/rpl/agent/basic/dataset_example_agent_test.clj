(ns com.rpl.agent.basic.dataset-example-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.dataset-example-agent :refer [DatasetExampleModule math-input-schema math-output-schema]]
   [clojure.string :as str]))

(deftest dataset-example-agent-test
  ;; Test that the dataset example agent runs without errors
  (testing "dataset-example-agent"
    (testing "runs without errors"
      (is (nil? (com.rpl.agent.basic.dataset-example-agent/-main))
          "Main function should complete without throwing exceptions"))))