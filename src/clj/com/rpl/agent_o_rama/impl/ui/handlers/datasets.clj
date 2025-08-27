(ns com.rpl.agent-o-rama.impl.ui.handlers.datasets
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.string :as str])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-all
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id pagination]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           manager (common/get-manager decoded-module-id)
           datasets-page-query (:datasets-page-query (aor-types/underlying-objects manager))]
       (foreign-invoke-query datasets-page-query 100 pagination)))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/get-props
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id dataset-id]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           manager (common/get-manager decoded-module-id)
           datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
       (queries/get-dataset-properties datasets-pstate (UUID/fromString dataset-id))))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/create
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id name description input-schema output-schema]} uid]
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
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-name
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id dataset-id name]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           manager (common/get-manager decoded-module-id)]
       (aor/set-dataset-name! manager dataset-id name)
       {:status :ok}))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/set-description
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id dataset-id description]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           manager (common/get-manager decoded-module-id)]
       (aor/set-dataset-description! manager dataset-id description)
       {:status :ok}))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :datasets/delete
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id dataset-id]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           manager (common/get-manager decoded-module-id)]
       (aor/destroy-dataset! manager dataset-id)
       {:status :ok}))
   ev-msg))
