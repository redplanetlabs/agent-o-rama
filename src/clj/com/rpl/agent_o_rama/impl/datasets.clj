(ns com.rpl.agent-o-rama.impl.datasets
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
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
   [com.rpl.agent_o_rama.impl.types
    AddDatasetExample
    AddDatasetExampleTag
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
   [java.util.function
    Consumer]))


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

;; TODO: <<<<>>>> need first class API with agent-manager, which UI will use

;; TODO: <<<<>>>>
;;   - how to define schemas?
;;      {"a" String
;;       "b" Long}
;;    - keep it just as map like this for now? key -> class?
;;    - could make my own thing that's equivalent to JSON schema, with a nice
;;    API for Clojure, but also support arbitrary classes


(deframaop handle-datasets-op
  [{:keys [*dataset-id] :as *data}]
  (<<with-substitutions
   [$$datasets (po/datasets-task-global)]
   (<<subsource *data
    (case> CreateDataset :> {:keys [*name *description]})
     (local-transform> [(keypath *dataset-id) :props
                        (termval {:name *name :description *description})]
                       $$datasets)

    (case> UpdateDatasetProperty :> {:keys [*key *value]})
     (local-transform> [(keypath *dataset-id :props *key) (termval *value)]
                       $$datasets)

    (case> DestroyDataset)
     (local-transform> [(keypath *dataset-id :snapshots MAP-VALS) NONE>]
                       $$datasets)
     (|direct (ops/current-task-id))
     (local-transform> [(keypath *dataset-id) NONE>]
                       $$datasets)

    (case> AddDatasetExample
           :> {:keys [*snapshot-name *example-id *input *reference-output
                       *tags]})
     ;; TODO: <<<<>>>> check schema
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id)
       (termval
        {:input *input :reference-output *reference-output :tags *tags})]
      $$datasets)

    (case> UpdateDatasetExample
           :> {:keys [*snapshot-name *example-id *key *value]})
     ;; TODO: <<<<>>>> check schema if updating input/output
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id *key)
       (termval *value)]
      $$datasets)

    (case> RemoveDatasetExample :> {:keys [*snapshot-name *example-id]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id) NONE>]
      $$datasets)

    (case> AddDatasetExampleTag :> {:keys [*snapshot-name *example-id *tag]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id :tags)
       NONE-ELEM
       (termval *tag)]
      $$datasets)

    (case> RemoveDatasetExampleTag
           :> {:keys [*snapshot-name *example-id *tag]})
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name *example-id :tags)
       (set-elem *tag)
       NONE>]
      $$datasets)

    (case> DatasetSnapshot
           :> {:keys [*from-snapshot-name *to-snapshot-name]})
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
     (local-transform>
      [(keypath *dataset-id :snapshots *snapshot-name) NONE>]
      $$datasets)
   )))
