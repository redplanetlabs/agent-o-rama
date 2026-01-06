(ns com.rpl.agent-o-rama.impl.ui.handlers.human-feedback
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
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

;; =============================================================================
;; HUMAN FEEDBACK QUEUES HANDLERS
;; =============================================================================

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

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/create-queue
  [{:keys [manager name description rubrics]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        ;; Convert rubrics from maps to HumanFeedbackQueueRubric records
        rubric-records (mapv (fn [r]
                               (aor-types/->valid-HumanFeedbackQueueRubric
                                (:metric r)
                                (boolean (:required r))))
                             rubrics)]
    (evals/create-human-feedback-queue! global-actions-depot name description rubric-records)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/delete-queue
  [{:keys [manager name]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)]
    (evals/remove-human-feedback-queue! global-actions-depot name)
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/get-queue-info
  [{:keys [manager queue-name]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        queue-info-query (:human-feedback-queue-info-query underlying-objects)]
    (foreign-invoke-query queue-info-query queue-name)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/get-queue-items
  [{:keys [manager queue-name pagination]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        queue-page-query (:human-feedback-queue-page-query underlying-objects)
        query-limit 20]
    (foreign-invoke-query queue-page-query queue-name query-limit pagination)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/add-to-queue
  [{:keys [manager queue-name agent-name invoke-id node-task-id node-invoke-id comment]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        ;; Parse invoke-id which is "taskId-agentInvokeId" format
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        ;; Build agent invoke
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        ;; Build node invoke if provided
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl (parse-long node-task-id)
                                                   (UUID/fromString node-invoke-id)))
        ;; Build feedback target
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)]
    (evals/add-human-feedback-request! global-actions-depot queue-name feedback-target (or comment ""))
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/resolve-queue-item
  [{:keys [manager queue-name item-id target reviewer-name scores comment]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        ;; Parse item-id
        item-uuid (if (uuid? item-id) item-id (UUID/fromString (str item-id)))
        ;; Build feedback target from the target data
        {:keys [agent-name agent-invoke node-invoke]} target
        agent-invoke-impl (aor-types/->AgentInvokeImpl 
                           (:task-id agent-invoke)
                           (:agent-invoke-id agent-invoke))
        node-invoke-impl (when node-invoke
                           (aor-types/->NodeInvokeImpl
                            (:task-id node-invoke)
                            (:node-invoke-id node-invoke)))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke-impl node-invoke-impl)]
    (evals/resolve-human-feedback-queue-item! 
     global-actions-depot 
     queue-name 
     item-uuid 
     feedback-target
     reviewer-name
     scores
     (or comment ""))
    {:status :ok}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :human-feedback/dismiss-queue-item
  [{:keys [manager queue-name item-id]} uid]
  (let [underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        ;; Parse item-id
        item-uuid (if (uuid? item-id) item-id (UUID/fromString (str item-id)))]
    (evals/remove-human-feedback-queue-item! global-actions-depot queue-name item-uuid)
    {:status :ok}))

