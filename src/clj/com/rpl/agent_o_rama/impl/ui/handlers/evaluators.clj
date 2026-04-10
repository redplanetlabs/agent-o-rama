(ns com.rpl.agent-o-rama.impl.ui.handlers.evaluators
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


(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :evaluators/get-all-instances
  [{:keys [manager pagination module-id filters]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-evals-query underlying-objects)
        search-string (get filters :search-string)
        types (get filters :types)
        query-limit 20]
    ;; Invoke the search query with optional search string and types filters
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string)

                            (seq types)
                            (assoc :types types))
                          query-limit
                          pagination)))
