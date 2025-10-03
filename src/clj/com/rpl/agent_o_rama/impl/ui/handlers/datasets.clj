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

(defn- process-example-source
  "Add source-string to example by calling getSourceString() on the source object"
  [example]
  (if-let [source (:source example)]
    (assoc example :source-string (aor-types/source-string source))
    example))

(defn- process-examples
  "Process a collection of examples to add source-string"
  [examples]
  (mapv process-example-source examples))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-all
  [{:keys [manager pagination filters]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        search-string (get filters :search-string)]
    (if-not (str/blank? search-string)
      ;; Use the search query when a search string is provided
      (let [search-query (:search-datasets-query underlying-objects)]
        (->> (foreign-invoke-query search-query search-string 500)
             (mapv (fn [[id name]] {:dataset-id id, :name name}))
             (hash-map :datasets)))
      ;; Otherwise, use the existing page query
      (let [datasets-page-query (:datasets-page-query underlying-objects)]
        (foreign-invoke-query datasets-page-query 1000 pagination)))))

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
    (let [result (foreign-invoke-query search-examples-query
                                       dataset-id
                                       (when-not (str/blank? snapshot-name) snapshot-name)
                                       (or filters {}) ; filters map for search functionality
                                       (or limit 20) ; reasonable default limit
                                       pagination)]
      ;; Process examples to add source-string
      (update result :examples process-examples))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-example
  [{:keys [manager dataset-id snapshot-name input output tags]} uid]
  (binding [aor-types/OPERATION-SOURCE (aor-types/->HumanSourceImpl "user")]
    (aor/add-dataset-example! manager
                              dataset-id
                              input
                              {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)
                               :reference-output output
                               :tags (set tags)}))
  {:status :ok})

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
        {:status :ok :example (process-example-source example)}
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

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/validate-direct-data
  [{:keys [manager dataset-id input output]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
        schemas (queries/get-dataset-properties datasets-pstate dataset-id)
        input-schema (:input-json-schema schemas)
        output-schema (:output-json-schema schemas)]
    (if-not schemas
      (throw (ex-info "Dataset not found" {:dataset-id dataset-id}))
      (let [input-validation (when input-schema (datasets/validate-with-schema* input-schema input))
            output-validation (when output-schema (datasets/validate-with-schema* output-schema output))]
        {:input {:is-valid? (or (nil? input-schema) (nil? input-validation))
                 :validation-error input-validation}
         :output {:is-valid? (or (nil? output-schema) (nil? output-validation))
                  :validation-error output-validation}}))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-direct-data
  [{:keys [manager dataset-id input output]} uid]
  (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
        schemas (queries/get-dataset-properties datasets-pstate dataset-id)
        input-schema (:input-json-schema schemas)
        output-schema (:output-json-schema schemas)]
    (if-not schemas
      (throw (ex-info "Dataset not found" {:dataset-id dataset-id}))
      (let [input-validation (when input-schema
                               (datasets/validate-with-schema* input-schema input))
            output-validation (when output-schema
                                (datasets/validate-with-schema* output-schema output))]
        (cond
          input-validation (throw (ex-info (str "Input schema validation failed: " input-validation) {}))
          output-validation (throw (ex-info (str "Output schema validation failed: " output-validation) {}))
          :else (do
                  (binding [aor-types/OPERATION-SOURCE (aor-types/->HumanSourceImpl "user")]
                    (aor/add-dataset-example! manager dataset-id input
                                              {:reference-output output}))
                  {:status :ok}))))))
