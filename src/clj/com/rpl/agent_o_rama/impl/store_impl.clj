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
    PState]
   [java.util
    UUID]))

(def KV :kv)
(def DOC :doc)

(drp/defrecord+ StoreParams
  [pstate-name :- String
   agent-name :- String
   agent-task-id :- Long
   agent-id :- UUID
   retry-num :- Long
   mirror? :- Boolean
   pstate-client :- PState
   write-depot :- Depot
   nested-ops-vol :- (s/pred volatile?)
  ])

(defn declare-store*
  [stream-topology stores-vol name store-type schema]
  (when (contains? @stores-vol name)
    (throw (h/ex-info "Cannot declare same store twice" {:name name})))
  (vswap! stores-vol assoc name store-type)
  (declare-pstate* stream-topology (symbol name) schema))


(defprotocol KeyValueStoreInternal
  (get* [this k default-value])
  (put* [this k v])
  (contains?* [this k])
  (update* [this k afn]))

(defn hook:initiating-pstate-write [])

(defn- pstate-write!*
  [store-params path k op params]
  (when (:mirror? store-params)
    (throw (h/ex-info "Can only write to colocated PStates"
                      {:pstate-name (:pstate-name store-params)})))
  (let [start-time  (h/current-time-millis)
        _ (hook:initiating-pstate-write)
        {ret aor-types/AGENTS-TOPOLOGY-NAME}
        (foreign-append!
         (:write-depot store-params)
         (aor-types/->PStateWrite
          (:agent-name store-params)
          (:agent-task-id store-params)
          (:agent-id store-params)
          (:retry-num store-params)
          (:pstate-name store-params)
          path
          k))
        finish-time (h/current-time-millis)]
    (if (= (:type ret) :failure)
      (throw (:exception ret)))
    (vswap! (:nested-ops-vol store-params)
            conj
            (aor-types/->NestedOpInfo
             start-time
             finish-time
             :store-write
             {"name"   (:pstate-name store-params)
              "op"     op
              "params" params}
            ))))

(defmacro pstate-write!
  [store-params path k op & params]
  `(pstate-write!* ~store-params ~path ~k ~op ~(vec params)))

(defn hook:initiating-pstate-query [])

(defn recorded-pstate-query!*
  [query-fn apath store-params options op params]
  (let [start-time  (h/current-time-millis)
        _ (hook:initiating-pstate-query)
        res         (query-fn
                     apath
                     (:pstate-client store-params)
                     options)
        finish-time (h/current-time-millis)
       ]
    (vswap!
     (:nested-ops-vol store-params)
     conj
     (aor-types/->valid-NestedOpInfo
      start-time
      finish-time
      :store-read
      {"name"   (:pstate-name store-params)
       "op"     op
       "params" params
       "result" res}
     ))
    res))

(defmacro recorded-pstate-select-one!
  [apath store-params options op & params]
  `(recorded-pstate-query!*
    compiled-foreign-select-one
    (path ~apath)
    ~store-params
    ~options
    ~op
    ~(vec params)
   ))

(defmacro recorded-pstate-select!
  [apath store-params options op & params]
  `(recorded-pstate-query!*
    compiled-foreign-select
    (path ~apath)
    ~store-params
    ~options
    ~op
    ~(vec params)
   ))

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
     (recorded-pstate-select-one!
      (view (fn [v#] (get v# k# default-value#)))
      ~store-params
      {:pkey k#}
      "get"
      k#))
    (~'put*
     [this# k# v#]
     (pstate-write! ~store-params
                    (path (keypath k#) (termval v#))
                    k#
                    "put"
                    k#
                    v#))
    (~'contains?*
     [this# k#]
     (recorded-pstate-select-one! (view contains? k#)
                                  ~store-params
                                  {:pkey k#}
                                  "contains?"
                                  k#))
    (~'update*
     [this# k# afn#]
     (pstate-write! ~store-params
                    (path (keypath k#) (term afn#))
                    k#
                    "update"
                    k#))
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
     (update-document-field* this# k# doc-key# (h/convert-jfn jfn#)))
    DocumentStoreInternal
    (~'get-document-field*
     [this# k# doc-key# default-value#]
     (recorded-pstate-select-one! [(keypath k#)
                                   (view (fn [v#]
                                           (get v# doc-key# default-value#)))]
                                  ~store-params
                                  {:pkey k#}
                                  "get-document-field"
                                  k#
                                  doc-key#
                                  {:default default-value#}
     ))
    (~'contains-document-field?*
     [this# k# doc-key#]
     (recorded-pstate-select-one! [(keypath k#)
                                   (view contains? doc-key#)]
                                  ~store-params
                                  {:pkey k#}
                                  "contains-document-field?"
                                  k#
                                  doc-key#))
    (~'put-document-field*
     [this# k# doc-key# v#]
     (pstate-write! ~store-params
                    (path (keypath k# doc-key#) (termval v#))
                    k#
                    "put-document-field"
                    k#
                    doc-key#
                    v#))
    (~'update-document-field*
     [this# k# doc-key# afn#]
     (pstate-write! ~store-params
                    (path (keypath k# doc-key#) (term afn#))
                    k#
                    "update-document-field"
                    k#
                    doc-key#))
   ))

(defprotocol PStateStoreInternal
  (pstate-select* [this path] [this pkey path])
  (pstate-select-one* [this path] [this pkey path])
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
     (recorded-pstate-select!
      path#
      ~store-params
      {}
      "pstate-select"))
    (~'pstate-select*
     [this# pkey# path#]
     (recorded-pstate-select!
      path#
      ~store-params
      {:pkey pkey#}
      "pstate-select"
      {:pkey pkey#}))
    (~'pstate-select-one*
     [this# path#]
     (recorded-pstate-select-one! path#
                                  ~store-params
                                  {}
                                  "pstate-select-one"))
    (~'pstate-select-one*
     [this# pkey# path#]
     (recorded-pstate-select-one! path#
                                  ~store-params
                                  {:pkey pkey#}
                                  "pstate-select-one"
                                  {:pkey pkey#}))
    (~'pstate-transform*
     [this# pkey# path#]
     (pstate-write! ~store-params path# pkey# "pstate-transform" pkey#)
    )))

(defmacro reify-store
  [impls store-params]
  (let [code (mapcat (fn [f] ((resolve f) store-params))
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
