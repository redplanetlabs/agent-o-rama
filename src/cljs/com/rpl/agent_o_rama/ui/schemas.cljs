(ns com.rpl.agent-o-rama.ui.schemas
  (:require [schema.core :as s :include-macros true]))

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
   :graph {:raw-nodes {s/Uuid (spy "raw-nodes")}
           :nodes {s/Uuid (spy "nodes")}
           :edges [(spy "edges")]}
   :implicit-edges [(spy "implicit-edges")]
   :summary (s/maybe {:forks [s/Uuid]
                      :fork-of (s/maybe s/Uuid)
                      s/Keyword (spy "summary-extra")})
   :root-invoke-id (s/maybe s/Uuid)
   :task-id (s/maybe s/Int)
   :is-complete s/Bool
   (s/optional-key :historical-graph) (spy "historical-graph")
   (s/optional-key :forks) [s/Uuid]
   (s/optional-key :fork-of) (spy "fork-of")
   (s/optional-key :error) (spy "invocation-error")})

(s/defschema InvocationsSchema
  {:all-invokes [(spy "all-invokes")]
   :pagination-params (s/maybe {s/Int (s/maybe s/Int)})
   :has-more? s/Bool
   :loading? s/Bool})

(s/defschema QueryStateSchema
  {:status (s/enum :loading :success :error)

   (s/optional-key :data) s/Any ;; any server data
   (s/optional-key :error) s/Any ;; any server data
   :fetching? s/Bool
   (s/optional-key :should-refetch?) s/Bool})

;; Forward declaration for recursive reference
(declare QueriesCacheSchema)

(def QueriesCacheSchema
  "A schema for the nested query cache. It's a recursive map where
   leaf nodes must match QueryStateSchema."
  {(s/cond-pre s/Keyword s/Str) ;; Keys can be keywords or strings (module-ids, dataset-ids, etc.)
   (s/conditional
    ;; Predicate: if the value is a map containing :status, treat it as a leaf (QueryStateSchema)
    #(and (map? %) (contains? % :status))
    QueryStateSchema

    ;; Otherwise, expect another nested map conforming to the same structure
    (constantly true)
    (s/recursive #'QueriesCacheSchema))})

(s/defschema RouteMatchSchema s/Any) ;; don't want to schematize all of reitit

(s/defschema FormStateSchema
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

          ;; Other modal-specific data
          s/Keyword (spy "modal-data-value")}
   :form {:submitting? s/Bool
          :error (s/maybe (spy "modal-form-error"))}})

(s/defschema HitlStateSchema
  {:responses {s/Uuid s/Str}
   :submitting {s/Uuid s/Bool}})

(s/defschema DatasetsUiSchema
  {:selected-examples {s/Str #{s/Str}}
   :selected-snapshot-per-dataset {s/Str s/Str}})

(s/defschema UiSchema
  {:selected-node-id (s/maybe s/Uuid)
   :forking-mode? s/Bool
   :changed-nodes {s/Uuid s/Str}
   :active-tab s/Keyword
   :current-route s/Str
   :breadcrumbs [(spy "breadcrumb")]
   :modal ModalStateSchema
   :hitl HitlStateSchema
   :datasets DatasetsUiSchema})

(s/defschema SessionSchema
  {:user-id (s/maybe s/Str)
   :preferences {s/Keyword (spy "session-preference-value")}})

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
   :session SessionSchema})
