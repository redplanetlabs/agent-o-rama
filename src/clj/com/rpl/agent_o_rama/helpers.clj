(ns com.rpl.agent-o-rama.helpers
  (:refer-clojure :exclude [ex-info])
  (:use [com.rpl.rama.path])
  (:require [com.rpl.rama.ops :as ops])
  (:import [com.rpl.agentorama.impl AORExceptionInfo]
           [com.rpl.rama.helpers TopologyUtils]))

(def MAX-ARITY 8)

(defn ex-info
  ([message data]
    (AORExceptionInfo. message data))
  ([message data cause]
    (AORExceptionInfo. message data cause)))

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

(defn clj-transform [compiled-path obj]
  (multi-transform ^:direct-nav compiled-path obj))

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

(defn random-long []
  (.nextLong ^java.util.Random (ops/current-random-source)))

(defn invoke
  ([afn] (afn))
  ([afn a] (afn a))
  ([afn a b] (afn a b))
  ([afn a b c] (afn a b c))
  ([afn a b c d] (afn a b c d))
  ([afn a b c d e] (afn a b c d e))
  ([afn a b c d e f] (afn a b c d e f))
  ([afn a b c d e f g] (afn a b c d e f g))
  ([afn a b c d e f g h] (afn a b c d e f g h))
  ([afn a b c d e f g h i] (afn a b c d e f g h i))
  ([afn a b c d e f g h i j] (afn a b c d e f g h i j))
  ([afn a b c d e f g h i j k] (afn a b c d e f g h i j k))
  ([afn a b c d e f g h i j k l] (afn a b c d e f g h i j k l))
  ([afn a b c d e f g h i j k l m] (afn a b c d e f g h i j k l m))
  ([afn a b c d e f g h i j k l m n] (afn a b c d e f g h i j k l m n))
  ([afn a b c d e f g h i j k l m n o] (afn a b c d e f g h i j k l m n o))
  ([afn a b c d e f g h i j k l m n o p] (afn a b c d e f g h i j k l m n o p)))
