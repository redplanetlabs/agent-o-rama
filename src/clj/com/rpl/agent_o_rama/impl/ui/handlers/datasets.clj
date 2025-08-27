(ns com.rpl.agent-o-rama.impl.ui.handlers.datasets
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-all
  [{:keys [module-id pagination]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)
        datasets-page-query (:datasets-page-query (aor-types/underlying-objects manager))]
    (foreign-invoke-query datasets-page-query 100 pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-props
  [{:keys [module-id dataset-id]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-properties datasets-pstate (UUID/fromString dataset-id))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/create
  [{:keys [module-id name description input-schema output-schema]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (try
      (let [dataset-id (aor/create-dataset! manager name
                                            {:description (when-not (str/blank? description) description)
                                             :input-json-schema (when-not (str/blank? input-schema) input-schema)
                                             :output-json-schema (when-not (str/blank? output-schema) output-schema)})]
        {:status :ok, :dataset-id dataset-id})
      (catch Exception e
        (throw (ex-info (-> e .getCause .getMessage) {}))))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-name
  [{:keys [module-id dataset-id name]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (aor/set-dataset-name! manager dataset-id name)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-description
  [{:keys [module-id dataset-id description]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (aor/set-dataset-description! manager dataset-id description)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete
  [{:keys [module-id dataset-id]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (aor/destroy-dataset! manager dataset-id)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-examples-page
  [{:keys [module-id dataset-id snapshot-name pagination]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-examples-page
     datasets-pstate
     (UUID/fromString dataset-id)
     (when-not (str/blank? snapshot-name) snapshot-name)
     100 ;; Page size
     (:pagination-params pagination))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/add-example
  [{:keys [module-id dataset-id snapshot-name input output]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (try
      ;; Input/Output from the UI will be JSON strings. We must parse them.
      (let [parsed-input (when-not (str/blank? input) (j/read-value input))
            parsed-output (when-not (str/blank? output) (j/read-value output))]
        (aor/add-dataset-example! manager
                                  (UUID/fromString dataset-id)
                                  parsed-input
                                  {:snapshot (when-not (str/blank? snapshot-name) snapshot-name)
                                   :reference-output parsed-output})
        {:status :ok})
      (catch com.fasterxml.jackson.core.JsonParseException e
        (throw (ex-info (str "Invalid JSON provided: " (.getOriginalMessage e))
                        {:field (if (str/includes? (.getMessage e) "input") :input :output)})))
      (catch Exception e
        ;; The validation error from `add-dataset-example!` will be in the cause.
        (let [cause (.getCause e)]
          (throw (ex-info (or (and cause (.getMessage cause)) (.getMessage e)) {})))))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-snapshot-names
  [{:keys [module-id dataset-id]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-snapshot-names datasets-pstate (UUID/fromString dataset-id))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/create-snapshot
  [{:keys [module-id dataset-id from-snapshot-name to-snapshot-name]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)
        from-name (when-not (str/blank? from-snapshot-name) from-snapshot-name)]
    (try
      (aor/snapshot-dataset! manager (UUID/fromString dataset-id) from-name to-snapshot-name)
      {:status :ok}
      (catch Exception e
        (throw (ex-info (-> e .getCause .getMessage) {}))))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete-snapshot
  [{:keys [module-id dataset-id snapshot-name]} uid]
  (let [decoded-module-id (common/url-decode module-id)
        manager (common/get-manager decoded-module-id)]
    (try
      (aor/remove-dataset-snapshot! manager (UUID/fromString dataset-id) snapshot-name)
      {:status :ok}
      (catch Exception e
        (throw (ex-info (-> e .getCause .getMessage) {}))))))
