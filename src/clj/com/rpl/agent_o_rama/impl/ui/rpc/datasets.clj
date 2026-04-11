(ns com.rpl.agent-o-rama.impl.ui.rpc.datasets
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:use [com.rpl.rama]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn- process-example-source [example]
  (if-let [source (:source example)]
    (assoc example :source-string (aor-types/source-string source))
    example))

(defn- process-examples [examples]
  (mapv process-example-source examples))

(defn- mark-remote-datasets [datasets]
  (mapv (fn [dataset]
          (let [module-name (:module-name dataset)
                host (:cluster-conductor-host dataset)
                port (:cluster-conductor-port dataset)]
            (if module-name
              (assoc dataset
                     :remote? true
                     :remote-module-name module-name
                     :remote-host host
                     :remote-port port)
              dataset)))
        datasets))

(defn get-all!!
  [system {:keys [module-id pagination filters limit cursor]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-string (get filters :search-string)
        page-size (or limit 25)
        pagination' (or pagination cursor)]
    (if-not (str/blank? search-string)
      (let [search-query (:search-datasets-query underlying-objects)]
        (->> (foreign-invoke-query search-query search-string 500)
             (mapv (fn [[id name]] {:dataset-id id, :name name}))
             (hash-map :datasets)))
      (let [datasets-page-query (:datasets-page-query underlying-objects)
            result (foreign-invoke-query datasets-page-query page-size pagination')]
        (update result :datasets mark-remote-datasets)))))

(defn get-props!!
  [system {:keys [module-id dataset-id]}]
  (let [manager (get-manager system module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-properties datasets-pstate dataset-id)))

(defn get-snapshot-names!!
  [system {:keys [module-id dataset-id]}]
  (let [manager (get-manager system module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-snapshot-names datasets-pstate dataset-id)))

(defn search-examples!!
  [system {:keys [module-id dataset-id snapshot-name filters limit pagination cursor]}]
  (let [manager (get-manager system module-id)
        {:keys [search-examples-query]} (aor-types/underlying-objects manager)
        pagination' (or pagination cursor)
        result (foreign-invoke-query search-examples-query
                                     dataset-id
                                     (when-not (str/blank? snapshot-name) snapshot-name)
                                     (or filters {})
                                     (or limit 20)
                                     pagination')]
    (update result :examples process-examples)))

(defn get-example!!
  [system {:keys [module-id dataset-id snapshot-name example-id]}]
  (let [manager (get-manager system module-id)
        {:keys [multi-examples-query]} (aor-types/underlying-objects manager)
        examples-map (foreign-invoke-query multi-examples-query
                                           dataset-id
                                           (when-not (str/blank? snapshot-name) snapshot-name)
                                           [example-id])
        example (get examples-map example-id)]
    (if example
      {:status :ok :example (process-example-source example)}
      {:status :error :error "Example not found"})))

(defn fetch-example!!
  [system params]
  (get-example!! system params))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn create!!
  [system {:keys [module-id name description input-schema output-schema]}]
  (let [manager (get-manager system module-id)
        dataset-id (aor/create-dataset! manager name
                                        {:description (when-not (str/blank? description) description)
                                         :input-json-schema (when-not (str/blank? input-schema) input-schema)
                                         :output-json-schema (when-not (str/blank? output-schema) output-schema)})]
    {:status :ok :dataset-id dataset-id}))

(defn add-remote!!
  [system {:keys [module-id remote-dataset-id cluster-conductor-host cluster-conductor-port module-name]}]
  (let [manager (get-manager system module-id)]
    (aor-types/add-remote-dataset-internal
     manager
     (java.util.UUID/fromString remote-dataset-id)
     (when-not (str/blank? cluster-conductor-host) cluster-conductor-host)
     (when cluster-conductor-port (long cluster-conductor-port))
     module-name)
    {:status :ok :dataset-id remote-dataset-id}))

(defn set-name!!
  [system {:keys [module-id dataset-id name]}]
  (let [manager (get-manager system module-id)]
    (aor/set-dataset-name! manager dataset-id name)
    {:status :ok}))

(defn set-description!!
  [system {:keys [module-id dataset-id description]}]
  (let [manager (get-manager system module-id)]
    (aor/set-dataset-description! manager dataset-id description)
    {:status :ok}))

(defn upsert!!
  "Update dataset name and/or description, applying only changed fields."
  [system {:keys [module-id dataset-id name description initial-name initial-description]}]
  (let [manager (get-manager system module-id)]
    (when (and name (not= name initial-name))
      (aor/set-dataset-name! manager dataset-id name))
    (when (not= description initial-description)
      (aor/set-dataset-description! manager dataset-id description))
    {:status :ok}))

(defn delete!!
  [system {:keys [module-id dataset-id]}]
  (let [manager (get-manager system module-id)]
    (aor/destroy-dataset! manager dataset-id)
    {:status :ok}))

(defn add-example!!
  [system {:keys [module-id dataset-id snapshot-name input output tags]}]
  (let [manager (get-manager system module-id)]
    (binding [aor-types/OPERATION-SOURCE (aor-types/->HumanSourceImpl "user" nil)]
      (aor/add-dataset-example! manager
                                dataset-id
                                input
                                {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)
                                 :reference-output output
                                 :tags (set tags)}))
    {:status :ok}))

(defn create-snapshot!!
  [system {:keys [module-id dataset-id from-snapshot-name to-snapshot-name]}]
  (let [manager (get-manager system module-id)
        from-name (when-not (str/blank? from-snapshot-name) from-snapshot-name)]
    (aor/snapshot-dataset! manager dataset-id from-name to-snapshot-name)
    {:status :ok :snapshot-name to-snapshot-name}))

(defn delete-snapshot!!
  [system {:keys [module-id dataset-id snapshot-name]}]
  (let [manager (get-manager system module-id)]
    (aor/remove-dataset-snapshot! manager dataset-id snapshot-name)
    {:status :ok}))

(defn delete-example!!
  [system {:keys [module-id dataset-id snapshot-name example-id]}]
  (let [manager (get-manager system module-id)]
    (aor/remove-dataset-example! manager
                                 dataset-id
                                 example-id
                                 {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)})))

(defn edit-example!!
  [system {:keys [module-id dataset-id snapshot-name example-id input reference-output]}]
  (let [manager (get-manager system module-id)
        snapshot-opts {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}]
    (aor/set-dataset-example-input! manager dataset-id example-id input snapshot-opts)
    (aor/set-dataset-example-reference-output! manager dataset-id example-id reference-output snapshot-opts)
    {:status :ok}))

(defn add-tag!!
  [system {:keys [module-id dataset-id snapshot-name example-id tag]}]
  (let [manager (get-manager system module-id)]
    (aor/add-dataset-example-tag! manager
                                  dataset-id
                                  example-id
                                  tag
                                  {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)})
    {:status :ok}))

(defn remove-tag!!
  [system {:keys [module-id dataset-id snapshot-name example-id tag]}]
  (let [manager (get-manager system module-id)]
    (aor/remove-dataset-example-tag! manager
                                     dataset-id
                                     example-id
                                     tag
                                     {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)})
    {:status :ok}))

(defn add-tag-to-examples!!
  [system {:keys [module-id dataset-id snapshot-name example-ids tag]}]
  (let [manager (get-manager system module-id)]
    (doseq [example-id example-ids]
      (aor/add-dataset-example-tag! manager
                                    dataset-id
                                    example-id
                                    tag
                                    {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
    {:status :ok}))

(defn remove-tag-from-examples!!
  [system {:keys [module-id dataset-id snapshot-name example-ids tag]}]
  (let [manager (get-manager system module-id)]
    (doseq [example-id example-ids]
      (aor/remove-dataset-example-tag! manager
                                       dataset-id
                                       example-id
                                       tag
                                       {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
    {:status :ok}))

(defn delete-examples!!
  [system {:keys [module-id dataset-id snapshot-name example-ids]}]
  (let [manager (get-manager system module-id)]
    (doseq [example-id example-ids]
      (aor/remove-dataset-example! manager
                                   dataset-id
                                   example-id
                                   {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)}))
    {:status :ok}))

(defn add-direct-data!!
  [system {:keys [module-id dataset-id input output]}]
  (let [manager (get-manager system module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
        schemas (queries/get-dataset-properties datasets-pstate dataset-id)
        input-schema (:input-json-schema schemas)
        output-schema (:output-json-schema schemas)]
    (if-not schemas
      (throw (ex-info "Dataset not found" {:dataset-id dataset-id}))
      (let [input-validation  (when input-schema
                                (datasets/validate-with-schema* input-schema input))
            output-validation (when output-schema
                                (datasets/validate-with-schema* output-schema output))]
        (cond
          input-validation (throw (ex-info (str "Input schema validation failed: " input-validation) {}))
          output-validation (throw (ex-info (str "Output schema validation failed: " output-validation) {}))
          :else (do
                  (binding [aor-types/OPERATION-SOURCE (aor-types/->HumanSourceImpl "user" nil)]
                    (aor/add-dataset-example! manager dataset-id input
                                              {:reference-output output}))
                  {:status :ok}))))))

(defn validate-direct-data!!
  [system {:keys [module-id dataset-id input output]}]
  (let [manager (get-manager system module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
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

(defn preview-expression!!
  [system {:keys [module-id dataset-id snapshot-name source-field expression type]}]
  (let [manager (get-manager system module-id)
        {:keys [search-examples-query multi-examples-query]}
        (aor-types/underlying-objects manager)
        search-result (foreign-invoke-query search-examples-query
                                            dataset-id
                                            (when-not (str/blank? snapshot-name) snapshot-name)
                                            {}
                                            1
                                            nil)
        example-summary (first (:examples search-result))]
    (if-not example-summary
      {:status :ok :result nil :error "No examples found in dataset"}
      (let [full-examples (foreign-invoke-query multi-examples-query
                                                dataset-id
                                                (when-not (str/blank? snapshot-name) snapshot-name)
                                                [(:id example-summary)])
            example (get full-examples (:id example-summary))
            source-data (case source-field
                          :input (:input example)
                          :reference-output (:reference-output example)
                          nil)
            preview-result
            (if (nil? source-data)
              ::no-source-data
              (try
                (case type
                  :path
                  (h/read-json-path source-data expression)
                  :template
                  (let [template-obj
                        (if (string? expression)
                          (if (or (str/starts-with? (str/trim expression) "{")
                                  (str/starts-with? (str/trim expression) "["))
                            (j/read-value expression)
                            expression)
                          expression)]
                    (h/resolve-json-path-template template-obj source-data)))
                (catch Exception e
                  (let [msg (or (.getMessage e) "")]
                    (cond
                      (or (str/includes? msg "can not be null")
                          (str/includes? msg "cannot be null")
                          (str/includes? msg "No results")
                          (str/includes? msg "Missing property"))
                      ::path-not-found
                      :else
                      (throw e))))))]
        (cond
          (= preview-result ::no-source-data)
          {:status :ok :result nil :error (str "No " (name source-field) " data in this example")}
          (= preview-result ::path-not-found)
          {:status :ok :result nil :error "Path not found in data"}
          :else
          {:status :ok
           :result (common/->ui-serializable preview-result)
           :example-preview (common/->ui-serializable source-data)})))))
