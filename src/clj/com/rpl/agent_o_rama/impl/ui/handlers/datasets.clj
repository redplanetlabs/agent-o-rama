(ns com.rpl.agent-o-rama.impl.ui.handlers.datasets
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.helpers :as h]
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

(defn- mark-remote-datasets
  "Add :remote? flag and connection info to datasets that are remote references"
  [datasets]
  (mapv (fn [dataset]
          (let [props dataset
                module-name (:module-name props)
                host (:cluster-conductor-host props)
                port (:cluster-conductor-port props)]
            ;; A dataset is remote if it has a module-name field
            (if module-name
              (assoc dataset
                     :remote? true
                     :remote-module-name module-name
                     :remote-host host
                     :remote-port port)
              dataset)))
        datasets))

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
      (let [datasets-page-query (:datasets-page-query underlying-objects)
            result (foreign-invoke-query datasets-page-query 25 pagination)]
        ;; Process datasets to add remote flags
        (update result :datasets mark-remote-datasets)))))

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

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-example
  [{:keys [manager dataset-id snapshot-name example-id]} uid]
  (let [{:keys [multi-examples-query]} (aor-types/underlying-objects manager)
        examples-map (foreign-invoke-query multi-examples-query
                                           dataset-id
                                           (when-not (str/blank? snapshot-name) snapshot-name)
                                           [example-id])
        example (get examples-map example-id)]
    (if example
      {:status :ok :example (process-example-source example)}
      {:status :error :error "Example not found"})))

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

