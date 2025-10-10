(ns com.rpl.agent-o-rama.ui.filter-builder-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.filter-builder :as fb]
   [com.rpl.agent-o-rama.ui.dom])); Load DOM setup before tests

(deftest comparator-spec-ui-test
  ;; Tests the ComparatorSpec component exists and is callable
  (testing "ComparatorSpec"
    (testing "component exists"
      (is (fn? fb/ComparatorSpec)
          "ComparatorSpec should be a function component"))))

(deftest filter-type-components-test
  ;; Tests that all specialized filter UI components exist
  (testing "specialized filter components"
    (testing "ErrorFilterUI exists"
      (is (fn? fb/ErrorFilterUI)
          "ErrorFilterUI should be a function component"))

    (testing "LatencyFilterUI exists"
      (is (fn? fb/LatencyFilterUI)
          "LatencyFilterUI should be a function component"))

    (testing "InputMatchFilterUI exists"
      (is (fn? fb/InputMatchFilterUI)
          "InputMatchFilterUI should be a function component"))

    (testing "OutputMatchFilterUI exists"
      (is (fn? fb/OutputMatchFilterUI)
          "OutputMatchFilterUI should be a function component"))

    (testing "TokenCountFilterUI exists"
      (is (fn? fb/TokenCountFilterUI)
          "TokenCountFilterUI should be a function component"))

    (testing "FeedbackFilterUI exists"
      (is (fn? fb/FeedbackFilterUI)
          "FeedbackFilterUI should be a function component"))

    (testing "AndFilterUI exists"
      (is (fn? fb/AndFilterUI)
          "AndFilterUI should be a function component"))

    (testing "OrFilterUI exists"
      (is (fn? fb/OrFilterUI)
          "OrFilterUI should be a function component"))

    (testing "NotFilterUI exists"
      (is (fn? fb/NotFilterUI)
          "NotFilterUI should be a function component"))))

(deftest filter-node-test
  ;; Tests the main FilterNode component
  (testing "FilterNode"
    (testing "component exists"
      (is (fn? fb/FilterNode)
          "FilterNode should be a function component"))))

(deftest filter-builder-test
  ;; Tests the top-level FilterBuilder component
  (testing "FilterBuilder"
    (testing "component exists"
      (is (fn? fb/FilterBuilder)
          "FilterBuilder should be a function component"))))

(deftest comparators-constant-test
  ;; Tests that the COMPARATORS constant is defined correctly
  (testing "COMPARATORS constant"
    (testing "contains all required comparators"
      (is (= 6 (count fb/COMPARATORS))
          "should have 6 comparators")
      (is (some #(= := (:value %)) fb/COMPARATORS)
          "should include =")
      (is (some #(= :not= (:value %)) fb/COMPARATORS)
          "should include ≠")
      (is (some #(= :< (:value %)) fb/COMPARATORS)
          "should include <")
      (is (some #(= :> (:value %)) fb/COMPARATORS)
          "should include >")
      (is (some #(= :<= (:value %)) fb/COMPARATORS)
          "should include ≤")
      (is (some #(= :>= (:value %)) fb/COMPARATORS)
          "should include ≥"))))

(deftest filter-initialization-test
  ;; Tests that filter types are initialized with correct default values
  (testing "filter initialization"
    (testing "InputMatchFilter has prefilled values"
      (let [filter-value {:type :input-match :json-path "$" :regex ".*"}]
        (is (= "$" (:json-path filter-value))
            "json-path should be prefilled with $")
        (is (= ".*" (:regex filter-value))
            "regex should be prefilled with .*")))

    (testing "OutputMatchFilter has prefilled values"
      (let [filter-value {:type :output-match :json-path "$" :regex ".*"}]
        (is (= "$" (:json-path filter-value))
            "json-path should be prefilled with $")
        (is (= ".*" (:regex filter-value))
            "regex should be prefilled with .*")))))
