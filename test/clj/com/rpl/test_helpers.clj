(ns com.rpl.test-helpers
  (:use [clojure.test]))

(defmacro letlocals
  [& body]
  (let [[tobind [last-binding-or-expr]]
        (split-at (dec (count body)) body)

        last-expr
        (if (and (list? last-binding-or-expr)
                 (= 'bind (first last-binding-or-expr)))
          (last last-binding-or-expr)
          last-binding-or-expr)

        binded
        (vec (mapcat (fn [e]
                       (if (and (list? e) (= 'bind (first e)))
                         [(second e) (last e)]
                         ['_ e]))

              tobind))]
    `(let ~binded
          ~last-expr)))

(defmacro ex-info-thrown? [re data & body]
  `(try
    ~@body
    (is false "Did not throw exception")
    (catch clojure.lang.ExceptionInfo e#
      (is (re-matches ~re (ex-message e#)))
      (is (= ~data (ex-data e#)))
      )))
