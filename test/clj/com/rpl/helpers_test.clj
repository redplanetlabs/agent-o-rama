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
