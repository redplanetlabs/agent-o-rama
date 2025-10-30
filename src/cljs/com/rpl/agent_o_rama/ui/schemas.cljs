(ns com.rpl.agent-o-rama.ui.schemas
  (:require
   [schema.core :as s :include-macros true]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Spy Schema for Discovery
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spy
  "Creates a spy schema that logs values with a label to identify which field it came from.
   Usage: (spy \"field-name\") or (spy :invocation/graph)"
  [label]
  (s/pred
   (fn [value]
     (when (some? value)
       (println "SPY |" label "|" (type value) "|" value))
     true)
   'spy-schema))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Core State Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema CurrentInvocationSchema
  {:invoke-id (s/maybe s/Str)
   :module-id (s/maybe s/Str)
   :agent-name (s/maybe s/Str)})

(s/defschema InvocationDataSchema
  {:status (s/enum :loading :success :error)
              ;; all optional because it starts with just {:status :loading}
   (s/optional-key :graph) {:raw-nodes {s/Uuid (spy "raw-nodes")}
                            :nodes {s/Uuid (spy "nodes")}
                            :edges [(spy "edges")]}
   (s/optional-key :implicit-edges) [(spy "implicit-edges")]
   (s/optional-key :summary) (s/maybe {:forks #{s/Uuid}
                                       :fork-of (s/maybe s/Uuid)
                                       s/Keyword (spy "summary-extra")})
   (s/optional-key :root-invoke-id) (s/maybe (spy "root-invoke-id"))
   (s/optional-key :task-id) (s/maybe s/Int)
   (s/optional-key :is-complete) s/Bool
   (s/optional-key :historical-graph) (spy "historical-graph")
   (s/optional-key :forks) #{s/Uuid}
   (s/optional-key :fork-of) (spy "fork-of")
   (s/optional-key :error) (spy "invocation-error")})

(s/defschema InvocationsSchema
  {:all-invokes [(spy "all-invokes")]
   :pagination-params (s/maybe {s/Int (s/maybe s/Int)})
   :has-more? (s/maybe s/Bool)
   :loading? s/Bool})

(s/defschema QueryStateSchema
  {:status (s/enum :idle :loading :success :error)

   (s/optional-key :data) s/Any ;; any server data
   (s/optional-key :error) s/Any ;; any server data

   ;; Regular query keys
   (s/optional-key :fetching?) s/Bool

   ;; Paginated query keys
   (s/optional-key :pagination-params) s/Any
   (s/optional-key :has-more?) s/Bool
   (s/optional-key :fetching-more?) s/Bool

   (s/optional-key :should-refetch?) s/Bool})

;; Forward declaration for recursive reference
(declare QueriesCacheSchema)

(def QueriesCacheSchema
  "A schema for the nested query cache. It's a recursive map where
   leaf nodes must match QueryStateSchema."
   ;; Keys can be keywords, strings, numbers (for granularity), symbols (for module-ids), or UUIDs (dataset-ids, etc.)
  {(s/cond-pre s/Keyword s/Str s/Num s/Symbol s/Uuid)
   (s/conditional
    ;; Predicate: if the value is a map containing :status, treat it as a
    ;; leaf (QueryStateSchema)
    #(and (map? %) (contains? % :status))
    QueryStateSchema

    ;; Otherwise, expect another nested map conforming to the same structure
    (constantly true)
    (s/recursive #'QueriesCacheSchema))})

(s/defschema RouteMatchSchema s/Any);; don't want to schematize all of reitit

(s/defschema
  FormStateSchema
  "Schema for form state. Each form has common metadata fields plus form-specific fields."
  {;; Common form metadata fields
   (s/optional-key :field-errors) {s/Keyword s/Str} ;; Map of field -> error message
   (s/optional-key :valid?) s/Bool
   (s/optional-key :submitting?) s/Bool
   (s/optional-key :error) (s/maybe s/Str)
   (s/optional-key :current-step) s/Keyword
   (s/optional-key :steps) [s/Keyword]

  ;; Form-specific fields - can be anything
   s/Keyword s/Any})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; UI State Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema ModalStateSchema
  "Schema for modal state. Modal data can contain form metadata or a React component."
  {:active (s/maybe s/Keyword)
   :data {;; Common modal data fields
          (s/optional-key :title) s/Str
          (s/optional-key :submit-text) s/Str
          (s/optional-key :form-id) s/Keyword
          (s/optional-key :component) s/Any ;; React component
          }
   :form {:submitting? s/Bool
          :error (s/maybe s/Str)}})

(s/defschema HitlStateSchema
  {:responses {s/Uuid s/Str}
   :submitting {s/Uuid s/Bool}})

(s/defschema DatasetsUiSchema
  {:selected-examples {s/Uuid #{s/Uuid}}
   :selected-snapshot-per-dataset {s/Uuid (s/maybe s/Str)}})

(s/defschema ManualRunSchema
  {s/Str {s/Str {:args s/Any
                 (s/optional-key :error-msg) (s/maybe s/Str)
                 :loading s/Bool}}})

(s/defschema UiSchema
  {:selected-node-id (s/maybe s/Uuid)
   :forking-mode? s/Bool
   :changed-nodes {s/Uuid s/Str}
   :active-tab s/Keyword
   :current-route s/Str
   :modal ModalStateSchema
   :hitl HitlStateSchema
   :datasets DatasetsUiSchema
   (s/optional-key :manual-run) ManualRunSchema
   (s/optional-key :node-details)
   {:active-tab (s/enum :feedback :info)}
   (s/optional-key :rules)
   {:refetch-trigger {s/Any s/Any}}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Top-Level App DB Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema AppDbSchema
  {:current-invocation CurrentInvocationSchema
   :invocations-data {s/Str InvocationDataSchema}
   :invocations InvocationsSchema
   :queries QueriesCacheSchema
   :route RouteMatchSchema
   :forms {s/Keyword FormStateSchema}
   :ui UiSchema
   :sente s/Any ;; don't want to schematize all of sente
   })
