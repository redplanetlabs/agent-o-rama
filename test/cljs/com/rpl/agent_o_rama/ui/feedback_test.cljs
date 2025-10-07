(ns com.rpl.agent-o-rama.ui.feedback-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.feedback :as feedback]))

;;; Test data

(def sample-feedback-data
  "Sample feedback data matching FEEDBACK-SCHEMA structure"
  {:actions {"agent-single-eval"  {:task-id         0
                                   :agent-invoke-id #uuid "0199bef6-04f5-753f-a6d2-91f1c5e779ff"}
             "agent-dual-eval"    {:agent-invoke-id #uuid "0199bef6-04f5-773b-a6d3-4b83640befa7"
                                   :task-id         0}
             "agent-numeric-eval" {:task-id         0
                                   :agent-invoke-id #uuid "0199bef6-04f5-773b-a6d3-4b83640befa8"}}
   :results [{:source      {:eval-name    "dual-eval"
                            :agent-invoke {:agent-invoke-id #uuid "0199bef6-04f5-773b-a6d3-4b83640befa7"
                                           :task-id         0}
                            :source       "action[FeedbackTestAgent/agent-dual-eval]"}
              :scores      {"length-ok"  true
                            "has-prefix" true}
              :modified-at 1759845418944
              :created-at  1759845418944}
             {:source      {:source       "action[FeedbackTestAgent/agent-single-eval]"
                            :eval-name    "single-eval"
                            :agent-invoke {:task-id         0
                                           :agent-invoke-id #uuid "0199bef6-04f5-753f-a6d2-91f1c5e779ff"}}
              :modified-at 1759845419722
              :created-at  1759845419722
              :scores      {"quality" true}}
             {:source      {:source       "action[FeedbackTestAgent/agent-numeric-eval]"
                            :eval-name    "numeric-eval"
                            :agent-invoke {:task-id         0
                                           :agent-invoke-id #uuid "0199bef6-04f5-773b-a6d3-4b83640befa8"}}
              :modified-at 1759845420500
              :created-at  1759845420500
              :scores      {:accuracy  0.95
                            :precision 0.87
                            :recall    0.92}}]})

;;; Tests for format-ms

(deftest format-ms-test
  (testing "format-ms"
    (testing "formats milliseconds as date string with milliseconds"
      (let [ms 1704067200000
            result (feedback/format-ms ms)]
        (is (string? result))
        (is (re-matches #"\w+ \d+ \d{4}, \d{2}:\d{2}:\d{2}\.\d{3}" result))))

    (testing "includes milliseconds component"
      (let [ms 1704067200123
            result (feedback/format-ms ms)]
        (is (re-find #"\.\d{3}$" result))))

    (testing "pads milliseconds to three digits"
      (let [ms-with-small-millis 1704067200005
            result (feedback/format-ms ms-with-small-millis)]
        (is (re-find #"\.005$" result))))))

;;; Tests for feedback data structure

(deftest feedback-data-structure-test
  (testing "sample feedback data structure"
    (testing "has required top-level keys"
      (is (contains? sample-feedback-data :actions))
      (is (contains? sample-feedback-data :results)))

    (testing "actions is a map"
      (is (map? (:actions sample-feedback-data)))
      (is (= 3 (count (:actions sample-feedback-data)))))

    (testing "results is a vector"
      (is (vector? (:results sample-feedback-data)))
      (is (= 3 (count (:results sample-feedback-data)))))

    (testing "each result has required keys"
      (doseq [result (:results sample-feedback-data)]
        (is (contains? result :source))
        (is (contains? result :scores))
        (is (contains? result :created-at))
        (is (contains? result :modified-at))))

    (testing "each action has required keys"
      (doseq [[_action-name action-value] (:actions sample-feedback-data)]
        (is (contains? action-value :task-id))
        (is (contains? action-value :agent-invoke-id))
        (is (uuid? (:agent-invoke-id action-value)))
        (is (number? (:task-id action-value)))))))

;;; Tests for feedback result content

(deftest feedback-results-test
  (testing "feedback results content"
    (testing "first result has expected scores"
      (let [first-result (first (:results sample-feedback-data))
            scores (:scores first-result)]
        (is (= true (get scores "length-ok")))
        (is (= true (get scores "has-prefix")))))

    (testing "second result has expected scores"
      (let [second-result (second (:results sample-feedback-data))
            scores (:scores second-result)]
        (is (= true (get scores "quality")))))

    (testing "third result has keyword keys and numeric values"
      (let [third-result (nth (:results sample-feedback-data) 2)
            scores (:scores third-result)]
        (is (= 0.95 (:accuracy scores)))
        (is (= 0.87 (:precision scores)))
        (is (= 0.92 (:recall scores)))
        (is (every? keyword? (keys scores)))
        (is (every? number? (vals scores)))))

    (testing "source strings contain agent name"
      (let [first-source (get-in sample-feedback-data [:results 0 :source :source])
            second-source (get-in sample-feedback-data [:results 1 :source :source])]
        (is (re-find #"FeedbackTestAgent" first-source))
        (is (re-find #"FeedbackTestAgent" second-source))))

    (testing "timestamps are present and valid"
      (doseq [result (:results sample-feedback-data)]
        (is (number? (:created-at result)))
        (is (number? (:modified-at result)))
        (is (pos? (:created-at result)))
        (is (pos? (:modified-at result)))))))

;;; Tests for source string processing

(deftest source-string-processing-test
  (testing "source string agent name removal"
    (testing "removes agent name prefix before slash"
      (let [raw-source "action[FeedbackTestAgent/agent-dual-eval]"
            expected "action[agent-dual-eval]"]
        (is (= expected
               (if-let [slash-idx (clojure.string/index-of raw-source "/")]
                 (let [before-slash (subs raw-source 0 slash-idx)
                       after-slash (subs raw-source (inc slash-idx))
                       bracket-idx (clojure.string/last-index-of before-slash "[")]
                   (if bracket-idx
                     (str (subs before-slash 0 (inc bracket-idx)) after-slash)
                     raw-source))
                 raw-source)))))

    (testing "handles source without slash"
      (let [raw-source "action[simple-eval]"
            result (if-let [slash-idx (clojure.string/index-of raw-source "/")]
                     (let [before-slash (subs raw-source 0 slash-idx)
                           after-slash (subs raw-source (inc slash-idx))
                           bracket-idx (clojure.string/last-index-of before-slash "[")]
                       (if bracket-idx
                         (str (subs before-slash 0 (inc bracket-idx)) after-slash)
                         raw-source))
                     raw-source)]
        (is (= raw-source result))))

    (testing "handles source without bracket"
      (let [raw-source "action-no-bracket/simple-eval"
            result (if-let [slash-idx (clojure.string/index-of raw-source "/")]
                     (let [before-slash (subs raw-source 0 slash-idx)
                           after-slash (subs raw-source (inc slash-idx))
                           bracket-idx (clojure.string/last-index-of before-slash "[")]
                       (if bracket-idx
                         (str (subs before-slash 0 (inc bracket-idx)) after-slash)
                         raw-source))
                     raw-source)]
        (is (= raw-source result))))))

;;; Tests for empty and edge cases

(deftest feedback-edge-cases-test
  (testing "feedback edge cases"
    (testing "empty results"
      (let [empty-feedback {:actions {} :results []}]
        (is (empty? (:results empty-feedback)))
        (is (zero? (count (:results empty-feedback))))))

    (testing "nil feedback"
      (is (nil? (:results nil))))

    (testing "feedback with no actions"
      (let [no-actions-feedback {:actions {} :results [{:source {:source "test"} :scores {} :created-at 0 :modified-at 0}]}]
        (is (empty? (:actions no-actions-feedback)))
        (is (seq (:results no-actions-feedback)))))

    (testing "feedback with empty scores"
      (let [empty-scores-result {:source {:source "test"} :scores {} :created-at 0 :modified-at 0}]
        (is (empty? (:scores empty-scores-result)))))))

;;; Integration test with sample data

(deftest sample-feedback-integration-test
  (testing "complete feedback data processing"
    (testing "can access all feedback results"
      (let [results (:results sample-feedback-data)]
        (is (= 3 (count results)))
        (is (every? #(contains? % :scores) results))
        (is (every? #(contains? % :source) results))))

    (testing "can access all actions"
      (let [actions (:actions sample-feedback-data)]
        (is (= 3 (count actions)))
        (is (contains? actions "agent-single-eval"))
        (is (contains? actions "agent-dual-eval"))
        (is (contains? actions "agent-numeric-eval"))))

    (testing "can format all timestamps"
      (let [results (:results sample-feedback-data)]
        (doseq [result results]
          (when-let [created-at (:created-at result)]
            (let [formatted (feedback/format-ms created-at)]
              (is (string? formatted))
              (is (pos? (count formatted))))))))

    (testing "all scores are accessible"
      (let [all-scores (mapcat #(seq (:scores %)) (:results sample-feedback-data))]
        (is (= 6 (count all-scores)))
        (is (every? (fn [[k v]] (some? v)) all-scores))))

    (testing "mixed score key types"
      (let [results (:results sample-feedback-data)
            string-key-scores (filter #(every? string? (keys (:scores %))) results)
            keyword-key-scores (filter #(every? keyword? (keys (:scores %))) results)]
        (is (= 2 (count string-key-scores)))
        (is (= 1 (count keyword-key-scores)))))))
