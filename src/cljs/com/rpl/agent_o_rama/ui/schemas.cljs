(ns com.rpl.agent-o-rama.ui.schemas
  (:require [schema.core :as s :include-macros true]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Spy Schema for Discovery
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema Spy
  "A custom schema that accepts any value but logs its structure to the console.
   Useful for discovering the shape of data during development."
  (s/pred
   (fn [value]
     ;; Only log if the value is not nil or empty to reduce console noise
     (when (and (some? value) (if (coll? value) (seq value) true))
       (println
        "SPY @ path:" (or (.-path s/*explain-out*) "unknown")
        "| value:" value))
     true) ; Always return true to pass validation
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
   :graph Spy
   :implicit-edges [Spy]
   :summary Spy
   :root-invoke-id (s/maybe s/Uuid)
   :task-id (s/maybe s/Int)
   :is-complete s/Bool
   (s/optional-key :historical-graph) Spy
   (s/optional-key :forks) [s/Uuid]
   (s/optional-key :fork-of) Spy
   (s/optional-key :error) Spy})

(s/defschema InvocationsSchema
  {:all-invokes [s/Any] ; Keep s/Any for now, or use Spy if you want to inspect invokes
   :pagination-params (s/maybe {s/Int (s/maybe s/Int)})
   :has-more? s/Bool
   :loading? s/Bool})

(s/defschema QueryStateSchema
  {:status (s/maybe (s/enum :loading :success :error))
   :data Spy
   :error Spy
   :fetching? s/Bool
   (s/optional-key :should-refetch?) s/Bool})

(s/defschema RouteMatchSchema s/Any)

(s/defschema FormStateSchema
  {s/Keyword Spy})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; UI State Schemas
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema ModalStateSchema
  {:active (s/maybe s/Keyword)
   :data {s/Keyword Spy}
   :form {s/Keyword Spy}})

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
   :preferences {s/Keyword Spy}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Top-Level App DB Schema
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(s/defschema AppDbSchema
  {:current-invocation CurrentInvocationSchema
   :invocations-data {s/Str InvocationDataSchema}
   :invocations InvocationsSchema
   :queries {s/Any {s/Any Spy}} ; Nested query structure: {query-type {param-key query-state}}
   :route RouteMatchSchema
   :forms {s/Keyword FormStateSchema}
   :ui UiSchema
   :sente SenteSchema
   :session SessionSchema})
