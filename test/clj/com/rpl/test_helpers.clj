(ns com.rpl.test-helpers)

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
