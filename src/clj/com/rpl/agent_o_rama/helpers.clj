(ns com.rpl.agent-o-rama.helpers)


(def MAX-ARITY 8)

(defn rama-void-function-class-symbol
  [i]
  (symbol (str "com.rpl.agentorama.ops.RamaVoidFunction" i)))

(defn rama-void-function-class
  [i]
  (resolve (rama-void-function-class-symbol i)))

(defn type-hinted
  [^Class class o]
  (with-meta o
    {:tag (-> class
              .getTypeName
              symbol)}))

(defmacro mk-void-jfn-converter
  []
  (let [arities (for [i (range MAX-ARITY)]
                  (let [klass (rama-void-function-class i)
                        args  (dofor [j (range i)]
                                (symbol (str "arg" j)))
                        t     (type-hinted klass 'f)]
                    `([~@args] (.invoke ~t ~@args))
                  ))]
    `(defn ~'convert-void-jfn
       [~'f]
       (fn ~@arities))))

(mk-void-jfn-converter)
