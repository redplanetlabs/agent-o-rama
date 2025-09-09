(ns com.rpl.agent-o-rama.impl.ui.handlers.experiments
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types])
  (:use [com.rpl.rama]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :experiments/get-all-for-dataset
  [{:keys [manager dataset-id pagination]} uid]
  (let [search-query (:search-experiments-query (aor-types/underlying-objects manager))]
    ;; For the index table, we get the first page with a reasonable limit
    (foreign-invoke-query search-query
                          dataset-id
                          {} ; no filters
                          20 ; limit
                          pagination)))