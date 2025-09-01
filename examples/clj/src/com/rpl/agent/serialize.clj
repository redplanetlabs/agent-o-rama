(ns com.rpl.agent.serialize)

(ser/extend-8-byte-freeze
 ToolSpecification
 [^ToolSpecification obj out]
 (nippy/freeze-to-out! out (.name obj))
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.parameters obj)))

(ser/extend-8-byte-thaw
 ToolSpecification
 [in]
 (-> (ToolSpecification/builder)
     (.name (nippy/thaw-from-in! in))
     (.description (nippy/thaw-from-in! in))
     (.parameters (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonObjectSchema
 [^JsonObjectSchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (into {} (.definitions obj)))
 (nippy/freeze-to-out! out (.required obj))
 (nippy/freeze-to-out! out (.additionalProperties obj))
 (nippy/freeze-to-out! out (into {} (.properties obj))))

(ser/extend-8-byte-thaw
 JsonObjectSchema
 [in]
 (let [builder (-> (JsonObjectSchema/builder)
                   (.description (nippy/thaw-from-in! in))
                   (.definitions (nippy/thaw-from-in! in))
                   (.required (nippy/thaw-from-in! in))
                   (.additionalProperties (nippy/thaw-from-in! in)))]
   (doseq [[name ^JsonSchemaElement property] (nippy/thaw-from-in! in)]
     (.addProperty builder name property))
   (.build builder)))

;; TODO use base class serializer for JsonSchemaElement

(ser/extend-8-byte-freeze
 JsonAnyOfSchema
 [^JsonAnyOfSchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.anyOf obj)))

(ser/extend-8-byte-thaw
 JsonAnyOfSchema
 [in]
 (-> (JsonAnyOfSchema/builder)
     (.description (nippy/thaw-from-in! in))
     (.anyOf (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonArraySchema
 [^JsonArraySchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.items obj)))

(ser/extend-8-byte-thaw
 JsonArraySchema
 [in]
 (-> (JsonArraySchema/builder)
     (.description (nippy/thaw-from-in! in))
     (.items (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonBooleanSchema
 [^JsonBooleanSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonBooleanSchema
 [in]
 (-> (JsonBooleanSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonEnumSchema
 [^JsonEnumSchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.enumValues obj)))

(ser/extend-8-byte-thaw
 JsonEnumSchema
 [in]
 (-> (JsonEnumSchema/builder)
     (.description (nippy/thaw-from-in! in))
     (.enumValues (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonIntegerSchema
 [^JsonIntegerSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonIntegerSchema
 [in]
 (-> (JsonIntegerSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))

;; (ser/extend-8-byte-freeze
;;     JsonNullSchema
;;     [^JsonNullSchema obj out]
;;   (nippy/freeze-to-out! out (.description obj)))

;; (ser/extend-8-byte-thaw
;;     JsonNullSchema
;;     [in]
;;   (-> (JsonNullSchema/builder)
;;       (.description (nippy/thaw-from-in! in))
;;       .build))

(ser/extend-8-byte-freeze
 JsonNumberSchema
 [^JsonNumberSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonNumberSchema
 [in]
 (-> (JsonNumberSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonReferenceSchema
 [^JsonReferenceSchema obj out]
 (nippy/freeze-to-out! out (.description obj))
 (nippy/freeze-to-out! out (.reference obj)))

(ser/extend-8-byte-thaw
 JsonReferenceSchema
 [in]
 (-> (JsonReferenceSchema/builder)
     (.description (nippy/thaw-from-in! in))
     (.reference (nippy/thaw-from-in! in))
     .build))

(ser/extend-8-byte-freeze
 JsonStringSchema
 [^JsonStringSchema obj out]
 (nippy/freeze-to-out! out (.description obj)))

(ser/extend-8-byte-thaw
 JsonStringSchema
 [in]
 (-> (JsonStringSchema/builder)
     (.description (nippy/thaw-from-in! in))
     .build))
