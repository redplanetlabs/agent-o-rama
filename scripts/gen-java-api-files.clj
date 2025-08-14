;; run with "lein with-profile gen exec -p scripts/gen-java-api-files.clj"
(require '[clojure.java.io :as io])
(require '[comb.template :as template])
(require '[clojure.string :as str])
(require '[agentoramajavadoc :as javadoc])

(def MAX-ARITY 9)

(defmacro dofor
  [& body]
  `(doall (for ~@body)))

(defn capitalize-first
  [s]
  (apply str (str/capitalize (nth s 0)) (rest s)))

(defn- spaces
  [amt]
  (apply str (repeat amt " ")))

(defn mk-type-strs
  [i]
  (vec (for [j (range i)] (str "T" j))))

(defn mk-void-function-types [i]
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


(defn template-api-files
  []
  (let [fs (file-seq (io/file "scripts/templates/java/api"))]
    (filter
     (fn [f]
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

(def TOOLS-AGENT-OPTIONS-METHODS
  [["errorHandlerDefault" "ToolsAgentOptions.Impl" []]
   ["errorHandlerStaticString" "ToolsAgentOptions.Impl" [["String" "message"]]]
   ["errorHandlerRethrow" "ToolsAgentOptions.Impl" []]
   ["errorHandlerStaticStringByType" "ToolsAgentOptions.Impl"
    [["StaticStringHandler..." "handlers"]]]
   ["errorHandlerByType" "ToolsAgentOptions.Impl"
    [["FunctionHandler..." "handlers"]]]
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
    (spit (str "src/java/com/rpl/agentorama/" (.getName f))
          (str
           "// this file is auto-generated\n"
           (template/eval f))))

  (catch Throwable t
    (do
      (.printStackTrace t)
      (throw t)
    )))
