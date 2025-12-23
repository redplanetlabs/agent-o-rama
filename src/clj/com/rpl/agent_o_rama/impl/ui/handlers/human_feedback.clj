(ns com.rpl.agent-o-rama.impl.ui.handlers.human-feedback
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import [java.util UUID])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/get-metrics
  [{:keys [manager pagination filters]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-metrics-query underlying-objects)
        search-string (get filters :search-string)
        query-limit 20]
    ;; Invoke the search query with optional search string filter
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/create-metric
  [{:keys [manager name type min max categories]} uid]
  (cond
    ;; Numeric metric
    (= type :numeric)
    (let [min-val (if min (long min) 1)
          max-val (if max (long max) 10)]
      (aor/create-numeric-human-metric! manager name "" min-val max-val))
    
    ;; Categorical metric
    (= type :categorical)
    (let [cat-list (if (string? categories)
                     (map str/trim (str/split categories #","))
                     categories)
          cat-set (set cat-list)]
      (aor/create-categorical-human-metric! manager name "" cat-set))
    
    :else
    (throw (ex-info "Invalid metric type" {:type type})))
  {:status :ok})

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/delete-metric
  [{:keys [manager name]} uid]
  (aor/remove-human-metric! manager name)
  {:status :ok})

