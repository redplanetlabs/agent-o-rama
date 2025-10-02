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
   :graph (spy :invocation/graph)
   :implicit-edges [(spy :invocation/implicit-edge)]
   :summary (spy :invocation/summary)
   :root-invoke-id (s/maybe s/Uuid)
   :task-id (s/maybe s/Int)
   :is-complete s/Bool
   (s/optional-key :historical-graph) (spy :invocation/historical-graph)
   (s/optional-key :forks) [s/Uuid]
   (s/optional-key :fork-of) (spy :invocation/fork-of)
   (s/optional-key :error) (spy :invocation/error)})

(s/defschema InvocationsSchema
  {:all-invokes [s/Any] ; Keep s/Any for now, or use Spy if you want to inspect invokes
   :pagination-params (s/maybe {s/Int (s/maybe s/Int)})
   :has-more? s/Bool
   :loading? s/Bool})

(s/defschema QueryStateSchema
  {:status (s/maybe (s/enum :loading :success :error))
   :data (spy :query/data)
   :error (spy :query/error)
   :fetching? s/Bool
   (s/optional-key :should-refetch?) s/Bool})

(s/defschema RouteMatchSchema s/Any)

(s/defschema FormStateSchema
  {s/Keyword (spy :forms/field-value)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; UI State Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema ModalStateSchema
  {:active (s/maybe s/Keyword)
   :data {s/Keyword (spy :modal/data)}
   :form {s/Keyword (spy :modal/form)}})

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
   :breadcrumbs [s/Any]
   :modal ModalStateSchema
   :hitl HitlStateSchema
   :datasets DatasetsUiSchema})

(s/defschema SenteSchema
  {:connected? s/Bool
   :connection-state s/Any})

(s/defschema SessionSchema
  {:user-id (s/maybe s/Str)
   :preferences {s/Keyword (spy :session/preference)}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Top-Level App DB Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema AppDbSchema
  {:current-invocation CurrentInvocationSchema
   :invocations-data {s/Str InvocationDataSchema}
   :invocations InvocationsSchema
   :queries {s/Any {s/Any (spy :queries/nested-value)}} ; Nested query structure: {query-type {param-key query-state}}
   :route RouteMatchSchema
   :forms {s/Keyword FormStateSchema}
   :ui UiSchema
   :sente SenteSchema
   :session SessionSchema})
