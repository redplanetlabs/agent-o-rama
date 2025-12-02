(ns com.rpl.helpers-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:import
   [com.rpl.agentorama
    AgentFailedException]))

(deftest invoke-test
  (loop [args         []
         expected-res 0]
    (is (= expected-res (apply h/invoke + args)))
    (when (< (count args) 16)
      (let [next (-> args
                     count
                     inc)]
        (recur (conj args next) (+ expected-res next))
      ))))

(deftest validate-options!-test
  (let [spec {:ab h/positive-number-spec :bb h/boolean-spec}]
    (h/validate-options! "context" {:ab 1 :bb false} spec)
    (h/validate-options! "context" {} spec)
    (h/validate-options! "context" {:ab 10} spec)
    (is (thrown?
         clojure.lang.ExceptionInfo
         (h/validate-options! "context" {:ab 1 :bb true :c false} spec)))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (h/validate-options! "context" {:c nil :d nil :e nil} spec)))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (h/validate-options! "context" {:ab 0} spec)))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (h/validate-options! "context" {:bb nil} spec)))
    (is (thrown?
         clojure.lang.ExceptionInfo
         (h/validate-options! "context" {:ab -1 :bb 23} spec)))
  ))

(deftest node->output-test
  (is (= 123 (h/node->output (aor-types/->AgentResult 123 false) 12345)))
  (is (instance? AgentFailedException
                 (h/node->output (aor-types/->AgentResult "failed..." true) [])))
  (is (instance? AgentFailedException (h/result->output nil)))
  (is (= [{"node" "abc" "args" [1 2 3]}
          {"node" "defg" "args" ["a" 3]}
          {"node" "abc" "args" ["aa" "bb"]}]
         (h/node->output nil
                         [(aor-types/->AgentNodeEmit (h/random-uuid7) nil 2 "abc" [1 2 3])
                          (aor-types/->AgentNodeEmit (h/random-uuid7) nil 2 "defg" ["a" 3])
                          (aor-types/->AgentNodeEmit (h/random-uuid7) nil 2 "abc" ["aa" "bb"])])))
)

(deftest json-path-template-test
  (letlocals
   (bind template (h/parse-json-path-template "$.a"))
   (is (= 1 (h/resolve-json-path-template template {"a" 1 "b" 2})))
   (bind template (h/parse-json-path-template "\"$.a\""))
   (is (= 1 (h/resolve-json-path-template template {"a" 1 "b" 2})))
   (bind template (h/parse-json-path-template "{\"q\": \"$.a[1]\"}"))
   (is (= {"q" 2} (h/resolve-json-path-template template {"a" [1 2 3] "b" [4 5 6]})))
   (bind template (h/parse-json-path-template "{\"q\": \"$$.a\"}"))
   (is (= {"q" "$.a"} (h/resolve-json-path-template template {"a" [1 2 3] "b" [4 5 6]})))
  ))

(deftest split-into-n-test
  (testing "basic splitting"
    (is (= [[1 4 7]
            [2 5 8]
            [3 6]]
           (h/split-into-n 3 [1 2 3 4 5 6 7 8]))))

  (testing "exact multiples"
    (is (= [[1 4 7]
            [2 5 8]
            [3 6 9]]
           (h/split-into-n 3 [1 2 3 4 5 6 7 8 9]))))

  (testing "n larger than collection"
    (is (= [[1] [2] [3] [] []]
           (h/split-into-n 5 [1 2 3]))))

  (testing "empty coll returns n empty vectors"
    (is (= [[] [] []]
           (h/split-into-n 3 []))))

  (testing "n = 1 returns everything in one column"
    (is (= [[1 2 3 4]]
           (h/split-into-n 1 [1 2 3 4]))))

  (testing "order preservation within each column"
    (is (= [[0 3 6 9]
            [1 4 7 10]
            [2 5 8 11]]
           (h/split-into-n 3 (range 12))))))
