(ns com.rpl.agent-o-rama.impl.store-impl
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.ramaspecter.defrecord-plus :as drp]
   [rpl.schema.core :as s])
  (:import
   [com.rpl.agentorama.store
    DocumentStore
    KeyValueStore
    PStateStore]
   [com.rpl.rama
    Depot
    PState]))

(def KV :kv)
(def DOC :doc)

(drp/defrecord+ StoreParams
  [pstate-name :- String
   mirror? :- Boolean
   pstate-client :- PState
   write-depot :- Depot
  ])

(defn declare-store*
  [stream-topology stores-vol name store-type schema]
  (when (contains? @stores-vol name)
    (throw (ex-info "Cannot declare same store twice" {:name name})))
  (vswap! stores-vol assoc name store-type)
  (declare-pstate* stream-topology (symbol name) schema))


(defprotocol KeyValueStoreInternal
  (get* [this k default-value])
  (put* [this k v])
  (contains?* [this k])
  (update* [this k afn]))

(defn- pstate-write!
  [store-params path k]
  (when (:mirror? store-params)
    (throw (ex-info "Can only write to colocated PStates"
                    {:pstate-name (:pstate-name store-params)})))
  (foreign-append!
   (:write-depot store-params)
   (aor-types/->PStateWrite
    (:pstate-name store-params)
    path
    k
   )))

(defn KeyValueImpl
  [store-params]
  `(KeyValueStore
    (~'get
     [this# k#]
     (get* this# k# nil))
    (~'getOrDefault
     [this# k# default-value#]
     (get* this# k# default-value#))
    (~'put
     [this# k# v#]
     (put* this# k# v#))
    (~'update
     [this# k# jfn#]
     (update* this# k# (h/convert-jfn jfn#)))
    (~'containsKey
     [this# k#]
     (contains?* this# k#))
    KeyValueStoreInternal
    (~'get*
     [this# k# default-value#]
     (foreign-select-one (view #(get ~'% k# default-value#))
                         (:pstate-client ~store-params)
                         {:pkey k#}))
    (~'put*
     [this# k# v#]
     (pstate-write! ~store-params (path (keypath k#) (termval v#)) k#))
    (~'contains?*
     [this# k#]
     (foreign-select-one #(view contains? ~'% k#)
                         (:pstate-client ~store-params)
                         {:pkey k#}))
    (~'update*
     [this k# afn#]
     (pstate-write! ~store-params (path (keypath k#) (term afn#)) k#))
   ))


(defprotocol DocumentStoreInternal
  (get-document-field* [this k doc-key default-value])
  (contains-document-field?* [this k doc-key])
  (put-document-field* [this k doc-key v])
  (update-document-field* [this k doc-key afn]))

(defn DocImpl
  [store-params]
  `(DocumentStore
    (~'getDocumentField
     [this# k# doc-key#]
     (get-document-field* this# k# doc-key# nil))
    (~'getDocumentFieldOrDefault
     [this# k# doc-key# default-value#]
     (get-document-field* this# k# doc-key# default-value#))
    (~'containsDocumentField
     [this# k# doc-key#]
     (contains-document-field?* this# k# doc-key#))
    (~'putDocumentField
     [this# k# doc-key# v#]
     (put-document-field* this# k# doc-key# v#))
    (~'updateDocumentField
     [this# k# doc-key# jfn#]
     (update-document-field* this k# doc-key# (h/convert-jfn jfn#)))
    DocumentStoreInternal
    (~'get-document-field*
     [this# k# doc-key# default-value#]
     (foreign-select-one [(keypath k#)
                          (view #(get ~'% doc-key# default-value#))]
                         (:pstate-client ~store-params)
                         {:pkey k#}))
    (~'contains-document-field?*
     [this# k# doc-key#]
     (foreign-select-one [(keypath k#) #(view contains? ~'% doc-key#)]
                         (:pstate-client ~store-params)
                         {:pkey k#}))
    (~'put-document-field*
     [this# k# doc-key# v#]
     (pstate-write! ~store-params (path (keypath k# doc-key#) (termval v#)) k#))
    (~'update-document-field*
     [this# k# doc-key# afn#]
     (pstate-write! ~store-params (path (keypath k# doc-key#) (term afn#)) k#))
   ))

(defprotocol PStateStoreInternal
  (pstate-select* [this path])
  (pstate-select* [this pkey path])
  (pstate-select-one* [this path])
  (pstate-select-one* [this pkey path])
  (pstate-transform* [this pkey path]))

(defn PStateStoreImpl
  [store-params]
  `(PStateStore
    (~'select
     [this# jpath#]
     (pstate-select* this# (java-path->clojure-path jpath#)))
    (~'select
     [this# pkey# jpath#]
     (pstate-select* this# pkey# (java-path->clojure-path jpath#)))
    (~'selectOne
     [this# pkey# jpath#]
     (pstate-select-one* this# pkey# (java-path->clojure-path jpath#)))
    (~'selectOne
     [this# jpath#]
     (pstate-select-one* this# (java-path->clojure-path jpath#)))
    (~'transform
     [this# pkey# jpath#]
     (pstate-transform* this# pkey# (java-path->clojure-path jpath#)))
    PStateStoreInternal
    (~'pstate-select*
     [this# path#]
     (foreign-select path# (:pstate-client ~store-params)))
    (~'pstate-select*
     [this# pkey# path#]
     (foreign-select path# (:pstate-client ~store-params) {:pkey pkey#}))
    (~'pstate-select-one*
     [this# path#]
     (foreign-select-one path# (:pstate-client ~store-params)))
    (~'pstate-select-one*
     [this# pkey# path#]
     (foreign-select-one path# (:pstate-client ~store-params) {:pkey pkey#}))
    (~'pstate-transform*
     [this# pkey# path#]
     (pstate-write! ~store-params path# pkey#)
    )))

(defmacro reify-store
  [impls store-params]
  (let [code (mapcat (fn [f] (f store-params))
              impls)]
    `(reify ~@code)))

(defn mk-kv-store
  [store-params]
  (reify-store [KeyValueImpl PStateStoreImpl] store-params))

(defn mk-doc-store
  [store-params]
  (reify-store [KeyValueImpl DocImpl PStateStoreImpl] store-params))

(defn mk-pstate-store
  [store-params]
  (reify-store [PStateStoreImpl] store-params))
