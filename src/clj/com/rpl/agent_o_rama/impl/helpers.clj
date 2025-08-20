(ns com.rpl.agent-o-rama.impl.helpers
  (:refer-clojure :exclude [ex-info])
  (:use [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.github.f4b6a3.uuid
    UuidCreator]
   [com.rpl.agentorama.impl
    AORExceptionInfo]
   [com.rpl.rama.helpers
    TopologyUtils]
   [java.io
    PrintWriter
    StringWriter]
   [java.util
    UUID]
   [java.util.concurrent
    Semaphore]
   [java.util.function
    Function]))

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

(defn current-time-millis
  []
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

(defmacro cf-function
  [& body]
  `(let [afn# (fn ~@body)]
     (reify
      Function
      (apply [_ arg#]
        (afn# arg#)))))

(defn start-index [s] 0)
(defn srange-dynamic-end-index
  [s start-index]
  (count s))

(defn mk-semaphore
  (^Semaphore [permits] (mk-semaphore permits false))
  (^Semaphore [permits fair?] (Semaphore. permits fair?)))

(defn acquire-semaphore
  ([^Semaphore s] (.acquire s))
  ([^Semaphore s amt] (.acquire s amt))
  ([^Semaphore s amt timeout-millis]
   (or (.tryAcquire s
                    amt
                    timeout-millis
                    java.util.concurrent.TimeUnit/MILLISECONDS)
       (throw (ex-info "Semaphore timed out"
                       {:amt amt :timeout-millis timeout-millis})))))

(defn release-semaphore
  ([^Semaphore s] (.release s))
  ([^Semaphore s amt] (.release s amt)))

(defn lastv
  [v]
  (let [c (count v)]
    (if (not= c 0)
      (nth v (dec c)))))

(defnav VOLATILE
  []
  (select* [_this structure next-fn]
           (next-fn @structure))
  (transform* [_this structure next-fn]
              (vswap! structure next-fn)
              structure))

(defn throw!
  [e]
  (throw e))

(defn into-map
  [arg]
  (into {} arg))

(defn thread-local-set!
  [^ThreadLocal t v]
  (.set t v))

(defn thread-local-get
  [^ThreadLocal t]
  (.get t))

(defmacro safe->
  [x & forms]
  (let [g (gensym "val")]
    (reduce
     (fn [acc form]
       `(let [~g ~acc]
          (if (nil? ~g)
            nil
            ~(if (seq? form)
               (with-meta
                 `(~(first form) ~g ~@(rest form))
                 (meta form))
               `(~form ~g)))))
     x
     forms)))

(defn remove-empty-vals
  [m]
  (setval [MAP-VALS #(or (nil? %) (and (coll? %) (empty? %)))]
          NONE
          m))

(defn random-uuid7
  []
  (UuidCreator/getTimeOrderedEpoch))

(defn random-uuid-str
  []
  (str (random-uuid7)))

(defn half-uuid
  [^UUID uuid]
  (.getLeastSignificantBits uuid))

(defn throwable->str
  [^Throwable t]
  (let [sw (StringWriter.)]
    (.printStackTrace t (PrintWriter. sw))
    (.toString sw)))

(defn first-line
  [s]
  (first (str/split s #"\n" 2)))

(defn validate-options!
  [context options spec]
  (let [errors (->> options
                    (mapv
                     (fn [[k v]]
                       (if-let [sfn (get spec k)]
                         (if-let [s (sfn v)]
                           (format "Value for option %s is invalid: %s" k s))
                         (format "%s is an invalid option" k))))
                    (filterv some?))]
    (when-not (empty? errors)
      (throw (ex-info "Invalid options" {:errors errors :context context}))
    )))

(defn positive-number-spec
  [v]
  (when-not (and (number? v) (pos? v))
    "value must be positive number"))

(defn boolean-spec
  [v]
  (when-not (boolean? v)
    "value must be boolean"))

(defn fn-spec
  [v]
  (when-not (ifn? v)
    "value must be a function"))

(defn list-spec
  [v]
  (when-not (instance? java.util.List v)
    "value must be a list"))

(defn any-spec
  [v])

(defn map-spec
  [v]
  (when-not (map? v)
    "value must be a map"))

(defn string-spec
  [v]
  (when-not (string? v)
    "value must be a string"))

(defn last-key [^java.util.SortedMap m] (.lastKey m))

(defn contains-string?
  [^String s substring]
  (.contains s substring))
