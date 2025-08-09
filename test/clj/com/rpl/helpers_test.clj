(ns com.rpl.helpers-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]))

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
