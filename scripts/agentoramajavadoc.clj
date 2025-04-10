(ns agentoramajavadoc
  (:refer-clojure :exclude [group-by]))

(defn arity->name
  [i]
  (if-let [n ({0 "zero"
               1 "one"
               2 "two"
               3 "three"
               4 "four"
               5 "five"
               6 "six"
               7 "seven"
               8 "eight"
               9 "nine"}
              i)]
    n
    (throw (ex-info "No mapping" {:i i}))
  ))

(defn args-str
  [i]
  (str (arity->name i)
       " "
       (if (= i 1) "argument" "arguments")))
