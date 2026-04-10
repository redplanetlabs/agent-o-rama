(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [clojure.string :as str])
  (:import [java.util UUID])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-metrics!!
  [system {:keys [module-id pagination filters]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-metrics-query underlying-objects)
        search-string (get filters :search-string)
        query-limit 20]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination)))

(defn get-queues!!
  [system {:keys [module-id pagination filters]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-feedback-queues-query underlying-objects)
        search-string (get filters :search-string)
        query-limit 20]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination)))

(defn get-queue-info!!
  [system {:keys [module-id queue-name]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        queue-info-query (:human-feedback-queue-info-query underlying-objects)]
    (foreign-invoke-query queue-info-query queue-name)))

(defn get-queue-items!!
  [system {:keys [module-id queue-name pagination limit include-cursor? reverse?]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        queue-page-query (:human-feedback-queue-page-query underlying-objects)
        query-limit (or limit 20)
        adjusted-pagination (cond
                              (and include-cursor? (uuid? pagination) reverse?)
                              (h/uuid-inc pagination)
                              (and include-cursor? (uuid? pagination))
                              (h/uuid-dec pagination)
                              :else
                              pagination)]
    (foreign-invoke-query queue-page-query queue-name query-limit (boolean reverse?) adjusted-pagination)))
