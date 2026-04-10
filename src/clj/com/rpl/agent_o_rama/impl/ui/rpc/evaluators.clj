(ns com.rpl.agent-o-rama.impl.ui.rpc.evaluators
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

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-all-builders!!
  [system {:keys [module-id]}]
  (let [manager (get-manager system module-id)]
    (foreign-invoke-query (:all-eval-builders-query (aor-types/underlying-objects manager)))))

(defn get-all-instances!!
  [system {:keys [module-id pagination filters]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-evals-query underlying-objects)
        search-string (get filters :search-string)
        types (get filters :types)
        query-limit 20]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string)
                            (seq types)
                            (assoc :types types))
                          query-limit
                          pagination)))
