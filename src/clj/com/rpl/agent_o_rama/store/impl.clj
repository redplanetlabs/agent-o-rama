(ns com.rpl.agent-o-rama.store.impl
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.types :as aor-types]
            [com.rpl.ramaspecter.defrecord-plus :as drp]
            [rpl.schema.core :as s])
  (:import [com.rpl.agentorama.store
             DocumentStore
             KeyValueStore
             PStateStore]))

(def KV :kv)
(def DOC :doc)

(drp/defrecord+ StoreParams
  [this-module-name :- String
   module-name :- String
   pstate-name :- String
   emits-vol :- (s/pred volatile?)
   async-ops-vol :- (s/pred volatile?)])

(defn declare-store* [stream-topology stores-vol name store-type schema]
  (when (contains? @stores-vol name)
    (throw (ex-info "Cannot declare same store twice" {:name name})))
  (vswap! stores-vol assoc name store-type)
  (declare-pstate* stream-topology (symbol name) schema))

(defn- add-async-op! [emits-vol async-ops-vol emit]
  (vswap! emits-vol conj emit)
  (vswap! async-ops-vol conj (aor-types/->valid-AsyncOpInfo nil nil nil)))

(defn add-pstate-query! [store-params path]
  (let [i (count @async-ops-vol)]
    (add-async-op! (:emits-vol store-params)
                   (:async-ops-vol store-params)
                   (aor-types/->valid-AsyncPStateQuery
                     (:module-name store-params)
                     (:pstate-name store-params)
                     path
                     i))
    (aor-types/->valid-AsyncResultPStateQuery i)))

(defn add-pstate-transform! [store-params path]
  (when-not (= (:this-module-name store-params) (:module-name store-params))
    (throw (h/ex-info "Cannot transform PState from a different module"
                      {:this-module-name (:this-module-name store-params)
                       :pstate-module-name (:module-name store-params)
                       :pstate-name (:pstate-name store-params)})))
  (let [i (count @async-ops-vol)]
    (add-async-op! (:emits-vol store-params)
                   (:async-ops-vol store-params)
                   (aor-types/->valid-AsyncPStateTransform
                     (:pstate-name store-params)
                     path
                     i))
    nil))

(defprotocol KeyValueStoreInternal
  (get-async* [this k default-value])
  (put-async* [this k v])
  (contains?-async* [this k])
  (update-async* [this k afn]))

(defn KeyValueImpl [store-params]
  `(KeyValueStore
    (getAsync [this# k#]
      (get-async* this# k# nil))
    (getOrDefaultAsync [this# k# default-value#]
      (get-async* this# k# default-value#))
    (putAsync [this# k# v#]
      (put-async* this# k# v#)))
    (updateAsync [this# k# jfn#]
      (update-async* this k# (h/convert-jfn jfn#)))
    (containsAsync [this# k#]
      (contains?-async* this# k#))
    KeyValueStoreInternal
    (get-async* [this# k# default-value#]
      (add-pstate-query! ~store-params (path (view #(get % k# default-value#)))))
    (put-async* [this# k# v#]
      (add-pstate-transform! ~store-params (path (keypath k#) (termval v#))))
    (contains?-async* [this# k#]
      (add-pstate-query! ~store-params (path (view #(contains? % k#)))))
    (update-async* [this k# afn#]
      (add-pstate-transform! ~store-params (path (keypath k#) (term afn#))))
    )

(defprotocol DocumentStoreInternal
  (get-document-field-async* [this k doc-key default-value])
  (contains-document-field?-async* [this k doc-key])
  (put-document-field-async* [this k doc-key v])
  (update-document-field-async* [this k doc-key afn]))

(defn DocImpl [store-params]
  `(DocumentStore
     (getDocumentFieldAsync [this# k# doc-key#]
       (get-document-field-async* this# k# doc-key# nil))
     (getDocumentFieldOrDefaultAsync [this# k# doc-key# default-value#]
       (get-document-field-async* this# k# doc-key# default-value#))
     (containsDocumentFieldAsync [this# k# doc-key#]
       (contains-document-field?-async* this# k# doc-key#))
     (putDocumentFieldAsync [this# k# doc-key# v#]
       (put-document-field-async* this# k# doc-key# v#))
     (updateDocumentFieldAsync [this# k# doc-key# jfn#]
       (update-document-field-async* this k# doc-key# (h/convert-jfn jfn#)))
    DocumentStoreInternal
    (get-document-field-async* [this# k# doc-key# default-value#]
      (add-pstate-query! ~store-params (path (keypath k#) (view #(get % doc-key# default-value#)))))
    (contains-document-field?-async* [this# k# doc-key#]
      (add-pstate-query! ~store-params (path (keypath k#) (view #(contains? % doc-key#)))))
    (put-document-field-async* [this# k# doc-key# v#]
      (add-pstate-transform! ~store-params (path (keypath k# doc-key#) (termval v#))))
    (update-document-field-async* [this# k# doc-key# afn#]
      (add-pstate-transform! ~store-params (path (keypath k# doc-key#) (term afn#))))
    ))

(defprotocol PStateStoreInternal
  (pstate-select* [this path])
  (pstate-transform* [this path]))

(defn PStateStoreImpl [store-params]
  `(PStateStore
    (select [this# jpath#]
      (pstate-select* this# (java-path->clojure-path jpath#)))
    (transform [this# jpath#]
      (pstate-transform* this# (java-path->clojure-path jpath#)))
    PStateStoreInternal
    (pstate-select* [this# path#]
      (add-pstate-query! ~store-params path#))
    (pstate-transform* [this# path#]
      (add-pstate-transform! ~store-params path#))))

(defmacro reify-store [impls store-params]
  (let [code (mapcat (fn [f] (f store-params))
                     impls)]
    `(reify ~@code)))

(defn mk-kv-store [store-params]
  (reify-store [KeyValueImpl] store-params))

(defn mk-doc-store [store-params]
  (reify-store [KeyValueImpl DocImpl] store-params))

(defn mk-pstate-store [store-params]
  (reify-store [PStateStoreImpl] store-params))
