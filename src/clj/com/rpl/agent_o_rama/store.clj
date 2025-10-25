(ns com.rpl.agent-o-rama.store
  "Store API for accessing persistent storage within agent nodes.\n
\n
This namespace provides a unified interface for working with different types of persistent storage in agent-o-rama. Stores are obtained via [[get-store]] within agent node functions and provide distributed, persistent, replicated storage that agents can use to maintain state across executions.\n
\n
Store types:\n
  - Key-value stores: Simple typed storage with automatic partitioning
  - Document stores: Schema-flexible storage for nested data
  - PState stores: Direct access to Rama's built-in storage capabilities
\n
All store operations are automatically traced and included in agent execution traces for debugging and monitoring purposes.\n
\n
Example:\n
<pre>
(fn [agent-node input]
  (let [store (aor/get-store agent-node \"$$user-cache\")]
    (store/put! store :user-id \"12345\")
    (store/put! store :last-seen (System/currentTimeMillis))
    (let [user-id (store/get store :user-id)
          last-seen (store/get store :last-seen)]
      (aor/result! agent-node {:user-id user-id :last-seen last-seen}))))
</pre>"
  (:use [com.rpl.rama path])
  (:refer-clojure :exclude [get contains?])
  (:require
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]))

(defn get
  "Gets a value from a key-value store.\n
\n
Retrieves the value associated with the given key from the store. If the key doesn't exist, returns the default value (or nil if not provided).\n
\n
Args:\n
  - store - Store instance obtained from [[get-store]]
  - k - Key to look up
  - default-value - Value to return if key doesn't exist (optional, defaults to nil)
\n
Returns:\n
  - The value associated with the key, or default-value if key doesn't exist
\n
Example:\n
<pre>
(let [store (aor/get-store agent-node \"$$cache\")]
  (store/get store :user-id \"unknown\")
  (store/get store :count 0))
</pre>"
  ([store k]
   (get store k nil))
  ([store k default-value]
   (simpl/get* store k default-value)))

(defn contains?
  "Checks if a key exists in a key-value store.\n
\n
Args:\n
  - store - Store instance obtained from [[get-store]]
  - k - Key to check for existence
\n
Returns:\n
  - Boolean - True if the key exists in the store
\n
Example:\n
<pre>
(let [store (aor/get-store agent-node \"$$cache\")]
  (when (store/contains? store :user-id)
    (store/get store :user-id)))
</pre>"
  [store k]
  (simpl/contains?* store k))

(defn put!
  "Stores a key-value pair in a key-value store.\n
\n
Args:\n
  - store - Store instance obtained from [[get-store]]
  - k - Key to store
  - v - Value to store
\n
Example:\n
<pre>
(let [store (aor/get-store agent-node \"$$cache\")]
  (store/put! store :user-id \"12345\")
  (store/put! store :last-seen (System/currentTimeMillis)))
</pre>"
  [store k v]
  (simpl/put* store k v))

(defn update!
  "Updates a value in a key-value store using a function.\n
\n
Applies the function to the current value (or nil if key doesn't exist) and stores the result back to the same key.\n
\n
Args:\n
  - store - Store instance obtained from [[get-store]]
  - k - Key to update
  - afn - Function that takes the current value and returns the new value
\n
Example:\n
<pre>
(let [store (aor/get-store agent-node \"$$counters\")]
  (store/update! store :page-views #(inc (or % 0)))
  (store/update! store :total #(+ (or % 0) amount)))
</pre>"
  [store k afn]
  (simpl/update* store k afn))

(defn get-document-field
  "Gets a specific field from a document in a document store.\n
\n
Document stores allow accessing individual fields of nested data structures without loading the entire document.\n
\n
Args:\n
  - store - Document store instance obtained from [[get-store]]
  - k - Document key (primary key)
  - doc-key - Field name within the document
  - default-value - Value to return if field doesn't exist (optional, defaults to nil)
\n
Returns:\n
  - The value of the specified field, or default-value if field doesn't exist\n
\n
Example:\n
<pre>
(let [doc-store (aor/get-store agent-node \"$$user-docs\")]
  (store/get-document-field doc-store :user-123 :email \"unknown@example.com\")
  (store/get-document-field doc-store :user-123 :preferences {}))
</pre>"
  ([store k doc-key]
   (get-document-field store k doc-key nil))
  ([store k doc-key default-value]
   (simpl/get-document-field* store k doc-key default-value)))

(defn contains-document-field?
  "Checks if a specific field exists in a document.\n
\n
Args:\n
  - store - Document store instance obtained from [[get-store]]
  - k - Document key (primary key)
  - doc-key - Field name to check for existence
\n
Returns:\n
  - Boolean - True if the field exists in the document
\n
Example:\n
<pre>
(let [doc-store (aor/get-store agent-node \"$$user-docs\")]
  (when (store/contains-document-field? doc-store :user-123 :email)
    (store/get-document-field doc-store :user-123 :email)))
</pre>"
  [store k doc-key]
  (simpl/contains-document-field?* store k doc-key))

(defn put-document-field!
  "Sets a specific field in a document.\n
\n
Args:\n
  - store - Document store instance obtained from [[get-store]]
  - k - Document key (primary key)
  - doc-key - Field name to set
  - value - Value to store in the field
\n
Example:\n
<pre>
(let [doc-store (aor/get-store agent-node \"$$user-docs\")]
  (store/put-document-field! doc-store :user-123 :email \"user@example.com\")
  (store/put-document-field! doc-store :user-123 :last-login (System/currentTimeMillis)))
</pre>"
  [store k doc-key value]
  (simpl/put-document-field* store k doc-key value))

(defn update-document-field!
  "Updates a specific field in a document using a function.\n
\n
Applies the function to the current field value (or nil if field doesn't exist) and stores the result back to the same field.\n
\n
Args:\n
  - store - Document store instance obtained from [[get-store]]
  - k - Document key (primary key)
  - doc-key - Field name to update
  - afn - Function that takes the current field value and returns the new value
\n
Example:\n
<pre>
(let [doc-store (aor/get-store agent-node \"$$user-docs\")]
  (store/update-document-field! doc-store :user-123 :login-count #(inc (or % 0)))
  (store/update-document-field! doc-store :user-123 :preferences #(assoc % :theme \"dark\")))
</pre>"
  [store k doc-key afn]
  (simpl/update-document-field* store k doc-key afn))

(defmacro pstate-select
  "Selects data from a PState store using Rama path expressions.\n
\n
PState stores provide direct access to Rama's built-in storage capabilities with powerful querying using path expressions. This function returns a collection of all matching values.\n
\n
Args:\n
  - apath - Rama path expression
  - store - PState store instance obtained from [[get-store]]
  - partitioning-key - Optional partitioning key for the query. Mandatory if the path does not begin with key navigation.
\n
Returns:\n
  - Collection of all values matching the path expression
\n
Example:\n
<pre>
(let [pstate (aor/get-store agent-node \"$$analytics\")]
  (store/pstate-select [:user-id ALL] pstate)
  (store/pstate-select [ALL (selected? :active)] pstate :some-partition-key))
</pre>"
  ([apath store]
   `(simpl/pstate-select* ~store (path ~apath)))
  ([apath store partitioning-key]
   `(simpl/pstate-select* ~store ~partitioning-key (path ~apath))))

(defmacro pstate-select-one
  "Selects a single value from a PState store using Rama path expressions.\n
\n
Similar to [[pstate-select]] but returns only the first matching value. Useful when you know the path will match exactly one item.\n
\n
Args:\n
  - apath - Rama path expression
  - store - PState store instance obtained from [[get-store]]
  - partitioning-key - Optional partitioning key for the query. Mandatory if the path does not begin with key navigation.
\n
Returns:\n
  - The first value matching the path expression, or nil if no match
\n
Example:\n
<pre>
(let [pstate (aor/get-store agent-node \"$$config\")]
  (store/pstate-select-one [:settings :max-retries] pstate))
</pre>"
  ([apath store]
   `(simpl/pstate-select-one* ~store (path ~apath)))
  ([apath store partitioning-key]
   `(simpl/pstate-select-one* ~store ~partitioning-key (path ~apath))))

(defmacro pstate-transform!
  "Transforms data in a PState store using Rama path expressions.\n
\n
Applies a transformation function to data matching the path expression.\n
\n
Args:\n
  - apath - Rama path expression with transformation
  - store - PState store instance obtained from [[get-store]]
  - partitioning-key - Partitioning key for the transformation
\n
Example:\n
<pre>
(let [pstate (aor/get-store agent-node \"$$analytics\")]
  (store/pstate-transform! [:alice :page-views (termval 42)] pstate :alice))
</pre>"
  [apath store partitioning-key]
  `(simpl/pstate-transform* ~store ~partitioning-key (path ~apath)))
