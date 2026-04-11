(ns com.rpl.agent-o-rama.impl.ui.rpc.human-feedback
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [clojure.string :as str])
  (:import [java.util UUID])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-metrics!!
  [system {:keys [module-id pagination filters limit cursor]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-metrics-query underlying-objects)
        search-string (get filters :search-string)
        query-limit (or limit 20)
        pagination' (or pagination cursor)]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination')))

(defn get-queues!!
  [system {:keys [module-id pagination filters limit cursor]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        search-query (:search-human-feedback-queues-query underlying-objects)
        search-string (get filters :search-string)
        query-limit (or limit 20)
        pagination' (or pagination cursor)]
    (foreign-invoke-query search-query
                          (cond-> {}
                            (not (str/blank? search-string))
                            (assoc :search-string search-string))
                          query-limit
                          pagination')))

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

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn create-metric!!
  [system {:keys [module-id name type min max categories]}]
  (let [manager (get-manager system module-id)]
    (cond
      (= type :numeric)
      (let [min-val (if min (long min) 1)
            max-val (if max (long max) 10)]
        (aor/create-numeric-human-metric! manager name "" min-val max-val))
      (= type :categorical)
      (let [cat-list (if (string? categories)
                       (map str/trim (str/split categories #","))
                       categories)
            cat-set (set cat-list)]
        (aor/create-categorical-human-metric! manager name "" cat-set))
      :else
      (throw (ex-info "Invalid metric type" {:type type})))
    {:status :ok}))

(defn delete-metric!!
  [system {:keys [module-id name]}]
  (let [manager (get-manager system module-id)]
    (aor/remove-human-metric! manager name)
    {:status :ok}))

(defn create-queue!!
  [system {:keys [module-id name description rubrics]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        rubric-records (mapv (fn [r]
                               (aor-types/->valid-HumanFeedbackQueueRubric
                                (:metric r)
                                (boolean (:required r))))
                             rubrics)]
    (evals/create-human-feedback-queue! global-actions-depot name description rubric-records)
    {:status :ok}))

(defn update-queue!!
  [system {:keys [module-id name description rubrics]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        rubric-records (mapv (fn [r]
                               (aor-types/->valid-HumanFeedbackQueueRubric
                                (:metric r)
                                (boolean (:required r))))
                             rubrics)]
    (evals/update-human-feedback-queue! global-actions-depot name description rubric-records)
    {:status :ok}))

(defn delete-queue!!
  [system {:keys [module-id name]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)]
    (evals/remove-human-feedback-queue! global-actions-depot name)
    {:status :ok}))

(defn add-to-queue!!
  [system {:keys [module-id queue-name agent-name invoke-id node-task-id node-invoke-id comment]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl node-task-id node-invoke-id))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)]
    (evals/add-human-feedback-request! global-actions-depot queue-name feedback-target (or comment ""))
    {:status :ok}))

(defn resolve-queue-item!!
  [system {:keys [module-id queue-name item-id target reviewer-name scores comment]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        item-uuid (if (uuid? item-id) item-id (UUID/fromString (str item-id)))
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
     global-actions-depot queue-name item-uuid feedback-target reviewer-name scores (or comment ""))
    {:status :ok}))

(defn dismiss-queue-item!!
  [system {:keys [module-id queue-name item-id]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        item-uuid (if (uuid? item-id) item-id (UUID/fromString (str item-id)))]
    (evals/remove-human-feedback-queue-item! global-actions-depot queue-name item-uuid)
    {:status :ok}))

(defn add-feedback!!
  [system {:keys [module-id agent-name invoke-id node-task-id node-invoke-id reviewer-name scores comment]}]
  (when (and (empty? scores) (str/blank? comment))
    (throw (ex-info "Feedback must include either metrics or a comment" {:scores scores :comment comment})))
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl node-task-id node-invoke-id))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)]
    (evals/add-human-feedback! global-actions-depot feedback-target reviewer-name scores (or comment ""))
    {:status :ok}))

(defn edit-feedback!!
  [system {:keys [module-id agent-name invoke-id node-task-id node-invoke-id feedback-id reviewer-name scores comment]}]
  (when (and (empty? scores) (str/blank? comment))
    (throw (ex-info "Feedback must include either metrics or a comment" {:scores scores :comment comment})))
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl node-task-id node-invoke-id))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)
        feedback-uuid (if (uuid? feedback-id) feedback-id (UUID/fromString (str feedback-id)))]
    (evals/edit-human-feedback! global-actions-depot feedback-target feedback-uuid reviewer-name scores (or comment ""))
    {:status :ok}))

(defn delete-feedback!!
  [system {:keys [module-id agent-name invoke-id node-task-id node-invoke-id feedback-id]}]
  (let [manager (get-manager system module-id)
        underlying-objects (aor-types/underlying-objects manager)
        global-actions-depot (:global-actions-depot underlying-objects)
        [agent-task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        agent-invoke (aor-types/->AgentInvokeImpl agent-task-id agent-invoke-id)
        node-invoke (when (and node-task-id node-invoke-id)
                      (aor-types/->NodeInvokeImpl node-task-id node-invoke-id))
        feedback-target (aor-types/->FeedbackTarget agent-name agent-invoke node-invoke)
        feedback-uuid (if (uuid? feedback-id) feedback-id (UUID/fromString (str feedback-id)))]
    (evals/delete-human-feedback! global-actions-depot feedback-target feedback-uuid)
    {:status :ok}))
