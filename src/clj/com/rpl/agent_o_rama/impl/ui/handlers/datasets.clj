(ns com.rpl.agent-o-rama.impl.ui.handlers.datasets
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-all
  [{:keys [manager pagination]} uid]
  ;; TODO search will use different query
  (let [datasets-page-query (:datasets-page-query (aor-types/underlying-objects manager))]
    (foreign-invoke-query datasets-page-query 1000 pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-props
  [{:keys [manager dataset-id]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-properties datasets-pstate dataset-id)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/create
  [{:keys [manager name description input-schema output-schema]} uid]
  (let [dataset-id (aor/create-dataset! manager name
                                        {:description (when-not (str/blank? description) description)
                                         :input-json-schema (when-not (str/blank? input-schema) input-schema)
                                         :output-json-schema (when-not (str/blank? output-schema) output-schema)})]
    {:status :ok :dataset-id dataset-id}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-name
  [{:keys [manager dataset-id name]} uid]
  (aor/set-dataset-name! manager dataset-id name)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-description
  [{:keys [manager dataset-id description]} uid]
  (aor/set-dataset-description! manager dataset-id description)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete
  [{:keys [manager dataset-id]} uid]
  (aor/destroy-dataset! manager dataset-id)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/search-examples
  [{:keys [manager dataset-id snapshot-name filters limit pagination]} uid]
  (let [{:keys [search-examples-query]} (aor-types/underlying-objects manager)]
    ;; [*dataset-id *snapshot *filters *limit *next-key :> *res]
    (foreign-invoke-query search-examples-query
                          dataset-id
                          (when-not (str/blank? snapshot-name) snapshot-name)
                          (or filters {}) ; filters map for search functionality
                          (or limit 20) ; reasonable default limit
                          pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-example
  [{:keys [manager dataset-id snapshot-name input output]} uid]
  (try
    ;; Input/Output from the UI will be JSON strings. We must parse them.
    (let [parsed-input (when-not (str/blank? input) (j/read-value input))
          parsed-output (when-not (str/blank? output) (j/read-value output))]
      (aor/add-dataset-example! manager
                                dataset-id
                                parsed-input
                                {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)
                                 :reference-output parsed-output})
      {:status :ok})
    (catch com.fasterxml.jackson.core.JsonParseException e
      (throw (ex-info (str "Invalid JSON provided: " (.getOriginalMessage e))
                      {:field (if (str/includes? (.getMessage e) "input") :input :output)})))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-snapshot-names
  [{:keys [manager dataset-id]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-snapshot-names datasets-pstate dataset-id)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/create-snapshot
  [{:keys [manager dataset-id from-snapshot-name to-snapshot-name]} uid]
  (let [from-name (when-not (str/blank? from-snapshot-name) from-snapshot-name)]
    (aor/snapshot-dataset! manager dataset-id from-name to-snapshot-name)
    ;; Return the name of the created snapshot on success
    {:status :ok :snapshot-name to-snapshot-name}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete-snapshot
  [{:keys [manager dataset-id snapshot-name]} uid]
  (aor/remove-dataset-snapshot! manager dataset-id snapshot-name)
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete-example
  [{:keys [manager dataset-id snapshot-name example-id]} uid]
  (aor/remove-dataset-example! manager
                               dataset-id
                               example-id
                               {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))

;; This is the new, unified handler. It accepts structured data directly.
(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/edit-example
  [{:keys [manager dataset-id snapshot-name example-id input reference-output]} uid]
  (try
    (let [snapshot-opts {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}]
      ;; The data is already parsed by the time it gets here.
      ;; We just need to dispatch the updates.
      ;; The `aor/set-dataset-example-*` functions correctly handle nil values if a field wasn't changed.

      (aor/set-dataset-example-input! manager
                                      dataset-id
                                      example-id
                                      input
                                      snapshot-opts)

      (aor/set-dataset-example-reference-output! manager
                                                 dataset-id
                                                 example-id
                                                 reference-output
                                                 snapshot-opts)

      {:status :ok})
    (catch Exception e
      ;; Catch schema validation errors from the backend and forward them
      (throw (ex-info (str "Failed to update example: " (.getMessage e))
                      {:dataset-id dataset-id :example-id example-id})))))

;; This adds the missing handler for the new inline editing flow.
;; It simply calls the unified :datasets/edit-example handler.
(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/update-example
  [ev-msg uid]
  ((get-method com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/edit-example) ev-msg uid))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-tag
  [{:keys [manager dataset-id snapshot-name example-id tag]} uid]
  (aor/add-dataset-example-tag! manager
                                dataset-id
                                example-id
                                tag
                                {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)})
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/remove-tag
  [{:keys [manager dataset-id snapshot-name example-id tag]} uid]
  (aor/remove-dataset-example-tag! manager
                                   dataset-id
                                   example-id
                                   tag
                                   {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)})
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-example
  [{:keys [manager dataset-id snapshot-name example-id]} uid]
  (let [{:keys [multi-examples-query]} (aor-types/underlying-objects manager)]
    ;; Fetch by exact ID to avoid search filtering and ordering issues
    (let [examples-map (foreign-invoke-query multi-examples-query
                                             dataset-id
                                             (when-not (str/blank? snapshot-name) snapshot-name)
                                             [example-id])
          example (get examples-map example-id)]
      (if example
        {:status :ok :example example}
        {:status :error :error "Example not found"}))))

;; =============================================================================
;; BULK OPERATION HANDLERS
;; =============================================================================

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-tag-to-examples
  [{:keys [manager dataset-id snapshot-name example-ids tag]} uid]
  (doseq [example-id example-ids]
    (aor/add-dataset-example-tag! manager
                                  dataset-id
                                  example-id
                                  tag
                                  {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/remove-tag-from-examples
  [{:keys [manager dataset-id snapshot-name example-ids tag]} uid]
  (doseq [example-id example-ids]
    (aor/remove-dataset-example-tag! manager
                                     dataset-id
                                     example-id
                                     tag
                                     {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete-examples
  [{:keys [manager dataset-id snapshot-name example-ids]} uid]
  (doseq [example-id example-ids]
    (aor/remove-dataset-example! manager
                                 dataset-id
                                 example-id
                                 {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
  {:status :ok})

;; =============================================================================
;; PREVIEW FROM TRACE HANDLER
;; =============================================================================

(defn- transform-and-validate [template source-data schema-str]
  (try
    (let [transformed (com.rpl.agent-o-rama.impl.helpers/read-json-path source-data template)
          validation-error (when schema-str
                             (datasets/validate-with-schema* schema-str transformed))]
      {:transformed-data transformed
       :is-valid? (nil? validation-error)
       :validation-error validation-error})
    (catch Exception e
      {:transformed-data nil
       :is-valid? false
       :validation-error (str "Invalid JSONPath template: " (.getMessage e))})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/preview-from-trace
  [{:keys [manager dataset-id input-template output-template source-args source-output]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
        schemas (queries/get-dataset-properties datasets-pstate dataset-id)]
    (if-not schemas
      (throw (ex-info "Dataset not found" {:dataset-id dataset-id}))
      {:input (transform-and-validate input-template source-args (:input-json-schema schemas))
       :output (transform-and-validate output-template source-output (:output-json-schema schemas))})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-from-trace
  [{:keys [manager dataset-id input-template output-template source-args source-output]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
        schemas (queries/get-dataset-properties datasets-pstate dataset-id)]
    (if-not schemas
      (throw (ex-info "Dataset not found" {:dataset-id dataset-id}))
      (let [input-result (transform-and-validate input-template source-args (:input-json-schema schemas))
            output-result (transform-and-validate output-template source-output (:output-json-schema schemas))]
        (if (and (:is-valid? input-result) (:is-valid? output-result))
          ;; Both are valid, add the example
          (do
            (aor/add-dataset-example! manager
                                      dataset-id
                                      (:transformed-data input-result)
                                      {:reference-output (:transformed-data output-result)})
            {:status :ok})
          ;; Invalid data, return error
          {:status :error
           :error (str "Invalid data: "
                       (when-not (:is-valid? input-result)
                         (str "Input: " (:validation-error input-result)))
                       (when-not (:is-valid? output-result)
                         (str "Output: " (:validation-error output-result))))})))))