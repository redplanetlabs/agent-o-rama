;; run with "lein with-profile gen exec -p scripts/gen-java-api-files.clj"
(require '[clojure.java.io :as io])
(require '[comb.template :as template])
(require '[clojure.string :as str])
(require '[agentoramajavadoc :as javadoc])

(def MAX-ARITY 9)

(defn- path
  ^java.nio.file.Path [s]
  (.getPath
   (java.nio.file.FileSystems/getDefault)
   (str s)
   (into-array String [])))

(defmacro dofor
  [& body]
  `(doall (for ~@body)))

(defn capitalize-first
  [s]
  (apply str (str/capitalize (nth s 0)) (rest s)))

(defn camel->kebab
  [camel-name]
  (-> camel-name
      str/trim
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      (str/replace #"([A-Z])([A-Z][a-z])" "$1-$2")
      str/lower-case))

(defn- spaces
  [amt]
  (apply str (repeat amt " ")))

(defn mk-type-strs
  [i]
  (vec (for [j (range i)] (str "T" j))))

(defn mk-void-function-types
  [i]
  (if (= i 0)
    ""
    (str "<" (str/join "," (conj (mk-type-strs i))) ">")))

(defn mk-full-type-decl
  ([i] (mk-full-type-decl [] i []))
  ([pre-type-strs i post-type-strs]
   (if (and (empty? pre-type-strs) (= i 0) (empty? post-type-strs))
     ""
     (str "<"
          (str/join "," (concat pre-type-strs (mk-type-strs i) post-type-strs))
          ">"))))

(defn mk-agg-node-on-type-decl
  [i]
  (str "<"
       (str/join "," (concat ["S"] (mk-type-strs i)))
       ">"))

(defn mk-agg-node-on-type-arg-decl
  [i]
  (str "<"
       (str/join "," (concat ["S"] (mk-type-strs i) ["Object"]))
       ">"))

(defn mk-type-args-decl
  ([i] (mk-type-args-decl [] i))
  ([pre-args i]
   (let [type-strs (mk-type-strs i)]
     (str/join
      ", "
      (concat
       (for [[t n] pre-args] (str t " " n))
       (for [j (range i)] (str (nth type-strs j) " arg" j))
      )))))


(def template-base (io/file "scripts/templates/java/api"))

(defn template-api-files
  []
  (let [fs (file-seq template-base)]
    (filter
     (fn [^java.io.File f]
       (.endsWith (.getName f) ".java"))
     fs)))

(defn args-declaration-str
  [args]
  (str/join ", "
            (dofor [[t v] args]
              (str t " " v)
            )))

(defn args-vars-str
  [args]
  (str/join ", " (mapv second args)))

(defn args-vars-str-or-true
  [args]
  (if (seq args)
    (str/join ", " (mapv second args))
    "true"))

(def TOOLS-AGENT-OPTIONS-METHODS
  [["errorHandlerDefault" "ToolsAgentOptions.Impl" []]
   ["errorHandlerStaticString" "ToolsAgentOptions.Impl" [["String" "message"]]]
   ["errorHandlerRethrow" "ToolsAgentOptions.Impl" []]
   ["errorHandlerStaticStringByType" "ToolsAgentOptions.Impl"
    [["StaticStringHandler..." "handlers"]]]
   ["errorHandlerByType" "ToolsAgentOptions.Impl"
    [["FunctionHandler..." "handlers"]]]
  ])

(def EVALUATOR-BUILDER-OPTIONS-METHODS
  [["param" "EvaluatorBuilderOptions.Impl"
    [["String" "name"] ["String" "description"]]]
   ["param" "EvaluatorBuilderOptions.Impl"
    [["String" "name"] ["String" "description"] ["String" "defaultValue"]]]
   ["withoutInputPath" "EvaluatorBuilderOptions.Impl" []]
   ["withoutOutputPath" "EvaluatorBuilderOptions.Impl" []]
   ["withoutReferenceOutputPath" "EvaluatorBuilderOptions.Impl" []]
  ])

(def ACTION-BUILDER-OPTIONS-METHODS
  [["param" "ActionBuilderOptions.Impl"
    [["String" "name"] ["String" "description"]]]
   ["param" "ActionBuilderOptions.Impl"
    [["String" "name"] ["String" "description"] ["String" "defaultValue"]]]
   ["limitConcurrency" "ActionBuilderOptions.Impl" []]
  ])

(def UI-OPTIONS-METHODS
  [["port" "UIOptions" [["int" "portNumber"]]]
   ["noInputBeforeClose" "UIOptions" []]
  ])


(def ^:dynamic *operation-index*)

(try
  (doseq [i (range MAX-ARITY)]
    (binding [*operation-index* i]
      (spit (str "src/java/com/rpl/agentorama/ops/RamaVoidFunction" i ".java")
            (str
             "// this file is auto-generated\n"
             (template/eval
              (io/file
               "scripts/templates/java/reusable/RamaVoidFunctionN.java"))))))
  (doseq [f (template-api-files)]
    (spit (str "src/java/com/rpl/agentorama/"
               (.relativize (path template-base) (path f)))
          (str
           "// this file is auto-generated\n"
           (template/eval f))))

  (catch Throwable t
    (do
      (.printStackTrace t)
      (throw t)
    )))
