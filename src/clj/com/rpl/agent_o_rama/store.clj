(ns com.rpl.agent-o-rama.store
  (:refer-clojure :exclude [get contains?])
  (:require
   [com.rpl.agent-o-rama.impl.store-impl :as simpl])
)

(defn get
  ([store k]
   (get store k nil))
  ([store k default-value]
   (simpl/get* store k default-value)))

(defn contains?
  [store k]
  (simpl/contains?* store k))

(defn put
  [store k v]
  (simpl/put* store k v))

(defn update
  [store k afn]
  (simpl/update* store k afn))

(defn get-document-field
  ([store k doc-key]
   (get-document-field store k doc-key nil))
  ([store k doc-key default-value]
   (simpl/get-document-field* store k doc-key default-value)))

(defn contains-document-field?
  [store k doc-key]
  (simpl/contains-document-field?* store k doc-key))

(defn put-document-field
  [store k doc-key value]
  (simpl/put-document-field* store k doc-key value))

(defn update-document-field
  [store k doc-key afn]
  (simpl/update-document-field* store k doc-key afn))

(defmacro pstate-select
  ([store apath]
   `(simpl/pstate-select* ~store (path ~apath)))
  ([store partitioning-key apath]
   `(simpl/pstate-select* ~store ~partitioning-key (path ~apath))))

(defmacro pstate-select-one
  ([store apath]
   `(simpl/pstate-select-one* ~store (path ~apath)))
  ([store partitioning-key apath]
   `(simpl/pstate-select-one* ~store ~partitioning-key (path ~apath))))

(defmacro pstate-transform
  [store partitioning-key apath]
  `(simpl/pstate-transform* ~store ~partitioning-key (path ~apath)))
