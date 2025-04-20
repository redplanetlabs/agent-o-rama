(ns com.rpl.agent-o-rama.helpers
  (:import [com.rpl.rama.helpers TopologyUtils]))

(def MAX-ARITY 8)

(defmacro dofor
  "Shortcut for `doall` and `for`."
  [& body]
  `(doall (for ~@body)))

(defn current-time-millis []
  (TopologyUtils/currentTimeMillis))

(defn type-hinted
  [^Class class o]
  (with-meta o
    {:tag (-> class
              .getTypeName
              symbol)}))

(defn rama-void-function-class-symbol
  [i]
  (symbol (str "com.rpl.agentorama.ops.RamaVoidFunction" i)))

(defn rama-void-function-class
  [i]
  (resolve (rama-void-function-class-symbol i)))

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

(defn rama-function-class-symbol
  [i]
  (symbol (str "com.rpl.rama.ops.RamaFunction" i)))

(defn rama-function-class
  [i]
  (resolve (rama-function-class-symbol i)))

(defmacro mk-jfn-converter
  []
  (let [arities (for [i (range MAX-ARITY)]
                  (let [klass (rama-function-class i)
                        args  (dofor [j (range i)]
                                (symbol (str "arg" j)))
                        t     (type-hinted klass 'f)]
                    `([~@args] (.invoke ~t ~@args))
                  ))]
    `(defn ~'convert-jfn
       [~'f]
       (fn ~@arities))))

(mk-jfn-converter)
