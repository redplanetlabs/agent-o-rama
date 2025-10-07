(ns com.rpl.agent-o-rama.ui.handlers.analytics-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama.impl.ui.handlers.analytics :as analytics]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agent_o_rama.impl.types
    AndFilter
    ComparatorSpec
    ErrorFilter
    FeedbackFilter
    InputMatchFilter
    LatencyFilter
    NotFilter
    OrFilter
    OutputMatchFilter
    TokenCountFilter]
   [java.util.regex Pattern]))

(deftest convert-ui-filter-test
  ;; Tests the convert-ui-filter function basic structure conversion
  (testing "convert-ui-filter"
    (testing "returns a record type (not plain map)"
      (let [result (analytics/convert-ui-filter {:type :and :filters []})]
        (is (instance? AndFilter result))))

    (testing "converts structure with correct field names"
      (let [result (analytics/convert-ui-filter
                    {:type :and
                     :filters [{:type :latency :comparator :> :value 1000}]})]
        (is (contains? result :filters))
        (is (vector? (:filters result)))
        (is (= 1 (count (:filters result))))))

    (testing "preserves nested filter types"
      (let [result (analytics/convert-ui-filter
                    {:type :and
                     :filters [{:type :latency :comparator :> :value 1000}]})]
        (is (instance? LatencyFilter (first (:filters result))))))

    (testing "converts comparator specs"
      (let [result (analytics/convert-ui-filter
                    {:type :and
                     :filters [{:type :latency :comparator :>= :value 500}]})]
        (is (= :>= (-> result :filters first :comparator-spec :comparator)))
        (is (= 500 (-> result :filters first :comparator-spec :value)))))

    (testing "compiles regex patterns"
      (let [result (analytics/convert-ui-filter
                    {:type :and
                     :filters [{:type :input-match
                                :json-path "$.test"
                                :regex ".*pattern.*"}]})]
        (is (instance? Pattern (-> result :filters first :regex)))))

    (testing "handles empty AND filter"
      (let [result (analytics/convert-ui-filter {:type :and :filters []})]
        (is (instance? AndFilter result))
        (is (empty? (:filters result)))))))

(deftest filter->ui-test
  ;; Tests the filter->ui function that converts backend records to UI maps
  (testing "filter->ui"
    (testing "converts ErrorFilter"
      (let [filter (aor-types/->valid-ErrorFilter)
            result (analytics/filter->ui filter)]
        (is (= {:type :error} result))))

    (testing "converts LatencyFilter"
      (let [comp-spec (aor-types/->valid-ComparatorSpec :> 1000)
            filter (aor-types/->valid-LatencyFilter comp-spec)
            result (analytics/filter->ui filter)]
        (is (= :latency (:type result)))
        (is (= :> (get-in result [:comparator-spec :comparator])))
        (is (= 1000 (get-in result [:comparator-spec :value])))))

    (testing "converts TokenCountFilter"
      (let [comp-spec (aor-types/->valid-ComparatorSpec :>= 100)
            filter (aor-types/->valid-TokenCountFilter :total comp-spec)
            result (analytics/filter->ui filter)]
        (is (= :token-count (:type result)))
        (is (= :total (:token-type result)))
        (is (= :>= (get-in result [:comparator-spec :comparator])))
        (is (= 100 (get-in result [:comparator-spec :value])))))

    (testing "converts FeedbackFilter"
      (let [comp-spec (aor-types/->valid-ComparatorSpec := 5)
            filter (aor-types/->valid-FeedbackFilter "test-rule" "score" comp-spec)
            result (analytics/filter->ui filter)]
        (is (= :feedback (:type result)))
        (is (= "test-rule" (:rule-name result)))
        (is (= "score" (:feedback-key result)))
        (is (= := (get-in result [:comparator-spec :comparator])))
        (is (= 5 (get-in result [:comparator-spec :value])))))

    (testing "converts InputMatchFilter"
      (let [filter (aor-types/->valid-InputMatchFilter "$.path" (Pattern/compile "test.*"))
            result (analytics/filter->ui filter)]
        (is (= :input-match (:type result)))
        (is (= "$.path" (:json-path result)))
        (is (= "test.*" (:regex result)))))

    (testing "converts OutputMatchFilter"
      (let [filter (aor-types/->valid-OutputMatchFilter "$.result" (Pattern/compile "success"))
            result (analytics/filter->ui filter)]
        (is (= :output-match (:type result)))
        (is (= "$.result" (:json-path result)))
        (is (= "success" (:regex result)))))

    (testing "converts AndFilter"
      (let [filter1 (aor-types/->valid-ErrorFilter)
            filter2 (aor-types/->valid-LatencyFilter
                     (aor-types/->valid-ComparatorSpec :> 500))
            and-filter (aor-types/->valid-AndFilter [filter1 filter2])
            result (analytics/filter->ui and-filter)]
        (is (= :and (:type result)))
        (is (= 2 (count (:filters result))))
        (is (= :error (get-in result [:filters 0 :type])))
        (is (= :latency (get-in result [:filters 1 :type])))))

    (testing "converts OrFilter"
      (let [filter1 (aor-types/->valid-ErrorFilter)
            filter2 (aor-types/->valid-LatencyFilter
                     (aor-types/->valid-ComparatorSpec :> 500))
            or-filter (aor-types/->valid-OrFilter [filter1 filter2])
            result (analytics/filter->ui or-filter)]
        (is (= :or (:type result)))
        (is (= 2 (count (:filters result))))))

    (testing "converts NotFilter"
      (let [inner (aor-types/->valid-ErrorFilter)
            not-filter (aor-types/->valid-NotFilter inner)
            result (analytics/filter->ui not-filter)]
        (is (= :not (:type result)))
        (is (= :error (get-in result [:filter :type])))))

    (testing "handles nil filter"
      (is (nil? (analytics/filter->ui nil))))))

(deftest filter-round-trip-test
  ;; Tests that UI → backend → UI conversions preserve the filter structure
  (testing "filter round-trip conversions"
    (testing "ErrorFilter round-trip"
      (let [ui-filter {:type :error}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= ui-filter back-to-ui))))

    (testing "LatencyFilter round-trip"
      (let [ui-filter {:type :latency
                       :comparator :>
                       :value 1000}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :latency (:type back-to-ui)))
        (is (= :> (get-in back-to-ui [:comparator-spec :comparator])))
        (is (= 1000 (get-in back-to-ui [:comparator-spec :value])))))

    (testing "TokenCountFilter round-trip"
      (let [ui-filter {:type :token-count
                       :token-type :total
                       :comparator-spec {:comparator :>=
                                         :value 100}}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= ui-filter back-to-ui))))

    (testing "FeedbackFilter round-trip"
      (let [ui-filter {:type :feedback
                       :rule-name "test-rule"
                       :feedback-key "score"
                       :comparator-spec {:comparator :=
                                         :value 5}}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= ui-filter back-to-ui))))

    (testing "InputMatchFilter round-trip"
      (let [ui-filter {:type :input-match
                       :json-path "$.test"
                       :regex "pattern.*"}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :input-match (:type back-to-ui)))
        (is (= "$.test" (:json-path back-to-ui)))
        (is (= "pattern.*" (:regex back-to-ui)))))

    (testing "OutputMatchFilter round-trip"
      (let [ui-filter {:type :output-match
                       :json-path "$.result"
                       :regex "success"}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :output-match (:type back-to-ui)))
        (is (= "$.result" (:json-path back-to-ui)))
        (is (= "success" (:regex back-to-ui)))))

    (testing "AndFilter round-trip"
      (let [ui-filter {:type :and
                       :filters [{:type :error}
                                 {:type :latency
                                  :comparator :>
                                  :value 500}]}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :and (:type back-to-ui)))
        (is (= 2 (count (:filters back-to-ui))))
        (is (= :error (get-in back-to-ui [:filters 0 :type])))
        (is (= :latency (get-in back-to-ui [:filters 1 :type])))))

    (testing "OrFilter round-trip"
      (let [ui-filter {:type :or
                       :filters [{:type :error}
                                 {:type :latency
                                  :comparator :>
                                  :value 500}]}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :or (:type back-to-ui)))
        (is (= 2 (count (:filters back-to-ui))))))

    (testing "NotFilter round-trip"
      (let [ui-filter {:type :not
                       :filter {:type :error}}
            backend (analytics/ui-filter->filter ui-filter)
            back-to-ui (analytics/filter->ui backend)]
        (is (= :not (:type back-to-ui)))
        (is (= :error (get-in back-to-ui [:filter :type])))))))
