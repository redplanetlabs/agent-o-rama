(ns com.rpl.agent-o-rama.impl.datasets
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]
   [jsonista.core :as j])
  (:import
   [com.fasterxml.jackson.databind
    ObjectMapper
    JsonNode]
   [com.fasterxml.jackson.databind.node
    POJONode]
   [com.networknt.schema
    Keyword
    JsonValidator
    JsonSchema
    JsonSchemaFactory
    JsonSchemaFactory$Builder
    ValidationMessage
    JsonMetaSchema
    JsonMetaSchema$Builder
    SchemaLocation
    JsonNodePath
    ValidationContext
    SpecVersion$VersionFlag]
   [com.rpl.agentorama
    AddDatasetExampleOptions
    AgentManager]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AddDatasetExample
    AddDatasetExampleTag
    AddRemoteDataset
    CreateDataset
    DatasetSnapshot
    DestroyDataset
    RemoveDatasetExample
    RemoveDatasetExampleTag
    RemoveDatasetSnapshot
    UpdateDatasetExample
    UpdateDatasetProperty]
   [java.util
    Collections
    LinkedHashSet
    List
    Map]
   [java.util.concurrent
    Semaphore]
   [java.util.function
    BiConsumer
    Consumer]))

(def ^:dynamic EXAMPLE-SOURCE nil)

(def ^ObjectMapper MAPPER (ObjectMapper.))
(def META "urn:agent-o-rama:meta:java-types-2020-12")

(def java-type-keyword
  (reify
   Keyword
   (getValue [_] "x-javaType")
   (^JsonValidator newValidator
     [_ ^SchemaLocation schemaLocation
      ^JsonNodePath evaluationPath
      ^JsonNode schemaNode
      ^JsonSchema _parent
      ^ValidationContext _ctx]
     (let [^String fqcn (.asText schemaNode)
           ^Class clazz (Class/forName fqcn false (clojure.lang.RT/baseLoader))]
       (reify
        JsonValidator
        (getKeyword [_] "x-javaType")
        (getSchemaLocation [_] schemaLocation)
        (getEvaluationPath [_] evaluationPath)
        (validate
          [_ _ec node _root at]
          (let [ok?
                (cond
                  (instance? POJONode node)
                  (let [pojo (.getPojo ^POJONode node)]
                    (and pojo (.isInstance clazz pojo)))

                  (and (= clazz String)
                       (instance? com.fasterxml.jackson.databind.node.TextNode
                                  node))
                  true

                  (and (= clazz Long)
                       (instance? com.fasterxml.jackson.databind.node.LongNode
                                  node))
                  true

                  (and (= clazz Integer)
                       (instance? com.fasterxml.jackson.databind.node.IntNode
                                  node))
                  true

                  (and (= clazz Double)
                       (instance? com.fasterxml.jackson.databind.node.DoubleNode
                                  node))
                  true

                  (and (= clazz java.math.BigDecimal)
                       (instance?
                        com.fasterxml.jackson.databind.node.DecimalNode
                        node))
                  true

                  (and (= clazz Boolean)
                       (instance?
                        com.fasterxml.jackson.databind.node.BooleanNode
                        node))
                  true

                  (and (= clazz nil) (.isNull node))
                  true

                  (and (= clazz Map)
                       (instance? com.fasterxml.jackson.databind.node.ObjectNode
                                  node))
                  true

                  (and (= clazz List)
                       (instance? com.fasterxml.jackson.databind.node.ArrayNode
                                  node))
                  true

                  :else
                  false
                )]
            (if ok?
              (Collections/emptySet)
              (let [errs (LinkedHashSet.)
                    path (str at)
                    b    (ValidationMessage/builder)]
                (.code b "x-javaType")
                (.arguments b (into-array Object [fqcn]))
                (.message b
                          (str "x-javaType: " path
                               " — expected " fqcn))
                (.add errs (.build b))
                errs))))
       )))))

(def META-SCHEMA
  (-> (JsonMetaSchema/builder
       META
       (JsonMetaSchema/getV202012)) ; base metaschema
      (.addKeyword java-type-keyword)
      (.build)))

(def FACTORY
  (JsonSchemaFactory/getInstance
   SpecVersion$VersionFlag/V202012
   (reify
    Consumer
    (accept [_ fb]
      (let [^JsonSchemaFactory$Builder fb fb]
        (.metaSchemas fb
                      (reify
                       Consumer
                       (accept [_ m]
                         (.put ^Map m META META-SCHEMA))))
        (.defaultMetaSchemaIri fb META))))))

(defn build-schema
  ^JsonSchema [m]
  (.getSchema ^JsonSchemaFactory FACTORY (.valueToTree ^ObjectMapper MAPPER m)))

(defn wrap-pojos
  [x]
  (cond
    (and (map? x) (not (record? x)))
    (let [obj (.createObjectNode MAPPER)]
      (doseq [[k v] x]
        (.set obj (name k) (wrap-pojos v)))
      obj)

    (sequential? x)
    (let [arr (.createArrayNode MAPPER)]
      (doseq [v x] (.add arr ^JsonNode (wrap-pojos v)))
      arr)

    (or (string? x) (number? x) (boolean? x) (nil? x))
    (.valueToTree MAPPER x)

    :else
    (POJONode. x)))

(defn validate
  [^JsonSchema s data]
  (.validate s (wrap-pojos data)))

(defn schema-validation-errors
  [^JsonNode root]
  (let [^JsonSchemaFactory f ^JsonSchemaFactory FACTORY
        meta-uri   (java.net.URI/create (.getIri (JsonMetaSchema/getV202012)))
        meta       (.getSchema f meta-uri)
        violations (.validate meta root)]
    (into #{} (map #(.getMessage ^ValidationMessage %)) violations)))


(defn normalize-json-schema*
  [json-schema]
  (try
    (let [^JsonNode root (.readTree ^ObjectMapper MAPPER ^String json-schema)
          errors         (when (some? root) (schema-validation-errors root))]
      (cond
        (nil? root)
        {:error "Invalid JSON schema: empty input."}

        (-> errors
            empty?
            not)
        {:error (str "Invalid JSON schema:\n\n" (str/join "\n" errors))}

        (not (.isObject root))
        {:error "Invalid JSON schema: root must be a JSON object."}

        :else
        (let [^com.fasterxml.jackson.databind.node.ObjectNode o
              (.deepCopy ^com.fasterxml.jackson.databind.node.ObjectNode root)]
          (cond
            (.has o "$schema")
            {:error "User-specified $schema is not allowed."}

            (.has o "$vocabulary")
            {:error "User-specified $vocabulary is not allowed."}

            :else
            (do
              (.put o "$schema" ^String META)
              (try
                ;; compile to verify it’s a valid JSON schema
                (.getSchema ^JsonSchemaFactory FACTORY o)
                (.writeValueAsString ^ObjectMapper MAPPER o)
                (catch Exception e
                  {:error (str "Invalid JSON schema:\n\n"
                               (h/throwable->str e))})))))))
    (catch com.fasterxml.jackson.core.JsonProcessingException e
      {:error (str "Invalid JSON:\n\n" (h/throwable->str e))})
    (catch Exception e
      {:error (str "Failed to normalize schema:\n\n" (h/throwable->str e))})))

(deframaop normalize-json-schema>
  [*json-schema]
  (<<if (some? *json-schema)
    (normalize-json-schema* *json-schema :> *res)
    (<<if (map? *res)
      (ack-return> (get *res :error))
     (else>)
      (:> *res))
   (else>)
    (:> nil)))

(defn validate-with-schema*
  [^String json-schema value]
  (try
    (let [^JsonNode schema-node (.readTree ^ObjectMapper MAPPER json-schema)
          ^JsonSchema schema (.getSchema ^JsonSchemaFactory FACTORY schema-node)
          errs (.validate schema (wrap-pojos value))]
      (when (seq errs)
        (str/join
         "\n"
         (mapv #(.getMessage ^ValidationMessage %) errs))))
    (catch com.fasterxml.jackson.core.JsonProcessingException e
      (str "Invalid JSON schema: " (h/throwable->str e)))
    (catch Exception e
      (str "Failed to compile or apply schema: " (h/throwable->str e)))))

(deframaop validate-with-schema>
  [*json-schema *value]
  (<<if (some? *json-schema)
    (validate-with-schema* *json-schema *value :> *res)
    (<<if (some? *res)
      (ack-return> *res)
     (else>)
      (:>))
   (else>)
    (:>)))

(defbasicblocksegmacro update-dataset!
  [pstate dataset-id apath]
  (let [time-millis (gen-anyvar "time-millis")]
    [[h/current-time-millis :> time-millis]
     [local-transform>
       [(seg# keypath dataset-id)
        (seg# multi-path
             [:props :modified-at (seg# termval time-millis)]
             apath)]
       pstate]]))


(defbasicblocksegmacro update-dataset-example!
  [pstate dataset-id snapshot-name example-id apath]
  (let [time-millis (gen-anyvar "time-millis")]
    [[h/current-time-millis :> time-millis]
     [update-dataset!
       pstate
       dataset-id
       [:snapshots
        (seg# keypath snapshot-name example-id)
        some?
        (seg# multi-path
          [:modified-at (seg# termval time-millis)]
          apath
          )]]]))

(defn get-cluster-retriever
  [^AgentDeclaredObjectsTaskGlobal declared-objects-tg]
  (.getClusterRetriever declared-objects-tg))

(defmacro with-datasets-pstate
  [declared-objects-tg remote-params [datasets-sym] & body]
  `(let [{host# :cluster-conductor-host port# :cluster-conductor-port module-name# :module-name}
         ~remote-params

         retriever# (if host#
                      (open-cluster-manager (h/to-rama-connection-info host# port#))
                      (get-cluster-retriever ~declared-objects-tg))]
     (try
       (let [~datasets-sym (foreign-pstate retriever# module-name# (po/datasets-task-global-name))]
         ~@body)
       (finally
         (when host#
           (close! retriever#))
       ))))

(defn verify-remote-dataset
  [{:keys [dataset-id cluster-conductor-host cluster-conductor-port module-name] :as params}]
  (if (and (some? cluster-conductor-port) (nil? cluster-conductor-host))
    "Cannot set conductor port without setting conductor host"
    (try
      (with-datasets-pstate
       (po/agent-declared-objects-task-global)
       params
       [datasets]
       (let [exists? (foreign-select-one [(keypath dataset-id) :props :name (view some?)]
                                         datasets)]
         (when-not exists?
           (throw (h/ex-info "Remote dataset does not exist in specified module" {})))
         nil))
      (catch Throwable t
        (format "Failed to connect to remote dataset %s" (h/throwable->str t))))))

(deframaop handle-datasets-op
  [{:keys [*dataset-id] :as *data}]
  (<<with-substitutions
   [$$datasets (po/datasets-task-global)]
   (local-select> [(keypath *dataset-id) :props] $$datasets :> *props)
   (filter> (or> (instance? CreateDataset *data)
                 (instance? AddRemoteDataset *data)
                 (some? *props)))
   (<<subsource *data
    (case> CreateDataset
           :> {:keys [*name *description *input-json-schema
                       *output-json-schema]})
     (normalize-json-schema> *input-json-schema :> *isnorm)
     (normalize-json-schema> *output-json-schema :> *osnorm)
     (h/current-time-millis :> *current-time-millis)
     (local-transform> [(keypath *dataset-id) :props
                        (termval {:name              *name
                                  :description       *description
                                  :input-json-schema *isnorm
                                  :output-json-schema *osnorm
                                  :created-at        *current-time-millis
                                  :modified-at       *current-time-millis})]
                       $$datasets)

    (case> AddRemoteDataset
           :> {:keys [*cluster-conductor-host *cluster-conductor-port *module-name]})
     (verify-remote-dataset *data :> *error)
     (<<if *error
       (ack-return> *error)
      (else>)
       (h/current-time-millis :> *current-time-millis)
       (local-transform> [(keypath *dataset-id)
                          :props
                          (termval {:cluster-conductor-host *cluster-conductor-host
                                    :cluster-conductor-port *cluster-conductor-port
                                    :module-name *module-name
                                    :created-at  *current-time-millis
                                    :modified-at *current-time-millis})]
                         $$datasets))

    (case> UpdateDatasetProperty :> {:keys [*key *value]})
     (update-dataset! $$datasets
                      *dataset-id
                      [:props (keypath *key) (termval *value)])

    (case> DestroyDataset)
     (local-transform> [(keypath *dataset-id :snapshots MAP-VALS) NONE>]
                       $$datasets)
     (|direct (ops/current-task-id))
     (local-transform> [(keypath *dataset-id) NONE>]
                       $$datasets)

    (case> AddDatasetExample
           :> {:keys [*snapshot-name *example-id *input *reference-output
                       *tags *source]})
     (get *props :input-json-schema :> *input-json-schema)
     (get *props :output-json-schema :> *output-json-schema)
     (validate-with-schema> *input-json-schema *input)
     (<<if (some? *reference-output)
       (validate-with-schema> *output-json-schema *reference-output))
     (h/current-time-millis :> *current-time-millis)
     (update-dataset! $$datasets
                      *dataset-id
                      [(keypath :snapshots *snapshot-name *example-id)
                       (termval
                        {:input            *input
                         :reference-output *reference-output
                         :tags             *tags
                         :source           *source
                         :created-at       *current-time-millis
                         :modified-at      *current-time-millis})])


    (case> UpdateDatasetExample
           :> {:keys [*snapshot-name *example-id *key *value]})
     (<<cond
      (case> (= *key :input))
       (get *props :input-json-schema :> *input-json-schema)
       (validate-with-schema> *input-json-schema *value)

      (case> (= *key :reference-output))
       (get *props :output-json-schema :> *output-json-schema)
       (validate-with-schema> *output-json-schema *value)

      (default>))
     (update-dataset-example!
      $$datasets
      *dataset-id
      *snapshot-name
      *example-id
      [(keypath *key) (termval *value)])

    (case> RemoveDatasetExample :> {:keys [*snapshot-name *example-id]})
     (update-dataset!
      $$datasets
      *dataset-id
      [(keypath :snapshots *snapshot-name *example-id) NONE>])

    (case> AddDatasetExampleTag :> {:keys [*snapshot-name *example-id *tag]})
     (update-dataset-example!
      $$datasets
      *dataset-id
      *snapshot-name
      *example-id
      [:tags NONE-ELEM (termval *tag)])

    (case> RemoveDatasetExampleTag
           :> {:keys [*snapshot-name *example-id *tag]})
     (update-dataset-example!
      $$datasets
      *dataset-id
      *snapshot-name
      *example-id
      [:tags (set-elem *tag) NONE>])

    (case> DatasetSnapshot
           :> {:keys [*from-snapshot-name *to-snapshot-name]})
     ;; to update modified-at
     (update-dataset! $$datasets *dataset-id STOP)
     (local-select> [(keypath *dataset-id :snapshots *from-snapshot-name)
                     ALL]
                    $$datasets
                    {:allow-yield? true}
                    :> [*example-id *example])
     (local-transform>
      [(keypath *dataset-id :snapshots *to-snapshot-name *example-id)
       (termval *example)]
      $$datasets)

    (case> RemoveDatasetSnapshot :> {:keys [*snapshot-name]})
     (update-dataset!
      $$datasets
      *dataset-id
      [(keypath :snapshots *snapshot-name) NONE>])
   )))

(defn upload-jsonl-examples!
  "Best-effort JSONL uploader.

     path is String path to UTF-8 JSONL file
     failure-callback is (fn [line ex]) for any per-line failure

   Lines look like:
     {\"input\": <json>, \"output\": <json optional>, \"tags\": [\"...\"] optional }"
  [^AgentManager manager dataset-id snapshot-name path failure-callback]
  (let [sem    (Semaphore. 100)
        mapper (j/object-mapper)]
    (with-open [r (io/reader path)]
      (binding [EXAMPLE-SOURCE (aor-types/->BulkUploadSource)]
        (doseq [line (line-seq r)]
          (when-not (str/blank? line)
            (let [m (try
                      (j/read-value line mapper)
                      (catch Exception ex
                        (failure-callback line ex)
                        ::parse-failed))]
              (when-not (identical? ::parse-failed m)
                (let [input  (get m "input")
                      output (get m "output")
                      tags-v (get m "tags")]
                  (if-not (or (nil? tags-v)
                              (and (sequential? tags-v) (every? string? tags-v)))
                    (failure-callback
                     line
                     (ex-info
                      "Tags must be an array of strings or omitted"
                      {:tags tags-v}))
                    (let [tags    (if (nil? tags-v) #{} (set tags-v))
                          options (AddDatasetExampleOptions.)]
                      (set! (.snapshotName options) snapshot-name)
                      (set! (.tags options) tags)
                      (set! (.referenceOutput options) output)
                      (.acquire sem)
                      (try
                        (let [cf
                              (.addDatasetExampleAsync manager
                                                       dataset-id
                                                       input
                                                       options)]
                          (.whenComplete cf
                                         (reify
                                          BiConsumer
                                          (accept [_ _ ex]
                                            (.release sem)
                                            (when ex
                                              (failure-callback line ex))))))
                        (catch Throwable t
                          (.release sem)
                          (failure-callback line t))))
                  ))))))
        (.acquire sem 100)
        nil))))

(defn create-remote-dataset!
  [datasets-depot dataset-id cluster-conductor-host cluster-conductor-port module-name]
  (let [{error aor-types/AGENTS-TOPOLOGY-NAME}
        (foreign-append!
         datasets-depot
         (aor-types/->valid-AddRemoteDataset dataset-id
                                             cluster-conductor-host
                                             cluster-conductor-port
                                             module-name))]
    (when error
      (throw (h/ex-info "Error creating remote dataset" {:info error})))))
