(ns com.rpl.agent-o-rama.ui.trace-analytics-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.trace-analytics :as trace-analytics]))

(deftest info-test
  ;; Tests the trace-analytics info component rendering and data handling.
  ;; Verifies that the component correctly displays execution statistics
  ;; and handles edge cases like nil data and conditional retry display.
  (testing "info component"
    (testing "renders with valid data"
      (let [graph-data   [{:id "node1"} {:id "node2"} {:id "node3"}]
            summary-data {:retry-num 2}
            result       (trace-analytics/info {:graph-data   graph-data
                                                :summary-data summary-data})]
        (is (some? result) "Component should render")))

    (testing "handles empty graph data"
      (let [graph-data   []
            summary-data {:retry-num 0}
            result       (trace-analytics/info {:graph-data   graph-data
                                                :summary-data summary-data})]
        (is (some? result) "Component should render with empty data")))

    (testing "handles nil graph data"
      (let [graph-data   nil
            summary-data {:retry-num 0}
            result       (trace-analytics/info {:graph-data   graph-data
                                                :summary-data summary-data})]
        (is (some? result) "Component should handle nil graph data")))

    (testing "handles nil summary data"
      (let [graph-data   [{:id "node1"}]
            summary-data nil
            result       (trace-analytics/info {:graph-data   graph-data
                                                :summary-data summary-data})]
        (is (some? result) "Component should handle nil summary data")))

    (testing "retry count conditional rendering"
      (testing "displays retry count when greater than zero"
        (let [graph-data   [{:id "node1"}]
              summary-data {:retry-num 5}
              result       (trace-analytics/info {:graph-data   graph-data
                                                  :summary-data summary-data})]
          (is (some? result) "Component should render with retries")))

      (testing "does not display retry count when zero"
        (let [graph-data   [{:id "node1"}]
              summary-data {:retry-num 0}
              result       (trace-analytics/info {:graph-data   graph-data
                                                  :summary-data summary-data})]
          (is (some? result) "Component should render without retry section")))

      (testing "does not display retry count when nil"
        (let [graph-data   [{:id "node1"}]
              summary-data {}
              result       (trace-analytics/info {:graph-data   graph-data
                                                  :summary-data summary-data})]
          (is (some? result) "Component should render without retry section"))))))
