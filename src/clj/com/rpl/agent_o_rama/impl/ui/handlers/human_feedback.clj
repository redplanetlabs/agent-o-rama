(ns com.rpl.agent-o-rama.impl.ui.handlers.human-feedback
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.helpers :as h]
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

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/get-queues
  [{:keys [manager pagination filters]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-feedback-queues-query underlying-objects)
        search-string (get filters :search-string)
        query-limit 20]
    ;; Invoke the search query with optional search string filter
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/get-queue-items
  [{:keys [manager queue-name pagination limit include-cursor? reverse?]} uid]
  (let [underlying-objects  (aor-types/underlying-objects manager)
        queue-page-query    (:human-feedback-queue-page-query underlying-objects)
        query-limit         (or limit 20)
        ;; If pagination is a UUID from item-id and we want inclusive behavior,
        ;; adjust it so search-loop with inclusive?=false includes the target item.
        adjusted-pagination (cond
                              (and include-cursor? (uuid? pagination) reverse?)
                              (h/uuid-inc pagination)

                              (and include-cursor? (uuid? pagination))
                              (h/uuid-dec pagination)

                              :else
                              pagination)]
    (foreign-invoke-query queue-page-query queue-name query-limit (boolean reverse?) adjusted-pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/add-to-queue
  [{:keys [manager queue-name agent-name invoke-id node-task-id node-invoke-id comment]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl node-task-id node-invoke-id))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)]
    (evals/add-human-feedback-request! global-actions-depot queue-name feedback-target (or comment ""))
    {:status :ok}))

