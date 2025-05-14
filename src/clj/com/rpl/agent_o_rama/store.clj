(ns com.rpl.agent-o-rama.store
  (:require [com.rpl.agent-o-rama.store.impl :as simpl])
  )

(defn get-async
  ([store k]
    (get-async store k nil))
  ([store k default-value]
    (simpl/get-async* store k default-value)))

(defn contains?-async [store k]
  (simpl/contains?-async* store k))

(defn put-async [store k v]
  (simpl/put-async* store k v))

(defn update-async [store k afn]
  (simpl/update-async* store k afn))

(defn get-document-field-async
  ([store k doc-key]
    (get-document-field-async store k doc-key nil))
  ([store k doc-key default-value]
    (simpl/get-document-field-async* store k doc-key default-value)))

(defn contains-document-field?-async [store k doc-key]
  (simpl/contains-document-field?-async* store k doc-key))

(defn put-document-field-async [store k doc-key value]
  (simpl/put-document-field-async* store k doc-key value))

(defn update-document-field-async [store k doc-key afn]
  (simpl/update-document-field-async* store k doc-key afn))

(defmacro pstate-select [store apath]
  `(simpl/pstate-select* ~store (path ~apath)))

(defmacro pstate-transform [store apath]
  `(simpl/pstate-transform* ~store (path ~apath)))
