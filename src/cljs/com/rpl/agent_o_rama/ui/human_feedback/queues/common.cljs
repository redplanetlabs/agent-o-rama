(ns com.rpl.agent-o-rama.ui.human-feedback.queues.common
  "Shared helpers and form registrations for human feedback queue views."
  (:require
   [re-frame.db :as rdb]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui defhook $]]
   [uix.re-frame :refer [use-subscribe]]
   ["@heroicons/react/24/outline" :refer [TrashIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [com.rpl.agent-o-rama.impl.ui.rpc.human-feedback :as rpc-hf]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [re-frame.core :as rf]
   [clojure.string :as str]))

;; =============================================================================
;; QUEUE TARGET / OUTPUT HELPERS
;; =============================================================================

(def TARGET-DOES-NOT-EXIST :com.rpl.agent-o-rama.impl.queries/target-does-not-exist)

(defn agent-target?
  "Returns true if the target is an agent (not a node)"
  [target]
  (nil? (:node-invoke target)))

(defn unwrap-agent-output
  "For agent targets, output is {:val <value> :failure? <bool>}.
   Returns {:failed? bool :value <unwrapped-value-or-original>}"
  [output target]
  (if (agent-target? target)
    {:failed? (:failure? output)
     :value (:val output)}
    {:failed? false
     :value output}))

(defn- queue-item-matches?
  [item item-id]
  (= (str (:id item)) (str item-id)))

(defn- merge-queue-items
  [existing-items new-items]
  (let [by-id (reduce (fn [acc item]
                        (assoc acc (str (:id item)) item))
                      {}
                      (concat existing-items new-items))]
    (->> (vals by-id)
         (sort-by (comp str :id))
         vec)))

;; =============================================================================
;; QUEUE ITEMS HOOK
;; =============================================================================

(defhook use-queue-items
  [{:keys [module-id queue-id initial-cursor include-initial-cursor? force-from-start? enabled?]
    :or {enabled? true}}]
  (let [decoded-module-id (common/url-decode module-id)
        decoded-queue-id (common/url-decode queue-id)
        state-path [:queries :human-feedback-queue-items module-id queue-id]
        query-state (use-subscribe [::aor-rf/get-in state-path])
        should-refetch? (:should-refetch? query-state)
        data (or (:data query-state) [])
        pagination-params (:pagination-params query-state)
        reverse-pagination-params (:reverse-pagination-params query-state)
        has-more? (get query-state :has-more? false)
        has-more-before? (get query-state :has-more-before? false)
        is-loading? (= (:status query-state) :loading)
        is-fetching-more? (:fetching-more? query-state)
        is-fetching-before? (:fetching-before? query-state)
        error (when (= (:status query-state) :error) (:error query-state))
        initial-needed? (and initial-cursor
                             (not (some #(queue-item-matches? % initial-cursor) data)))]

    (let [fetch-page (uix/use-callback
                      (fn [pagination-cursor append? include-cursor? merge? reverse?]
                        (when enabled?
                          (cond
                            reverse?
                            (rf/dispatch [:db/set-value (into state-path [:fetching-before?]) true])

                            append?
                            (rf/dispatch [:db/set-value (into state-path [:fetching-more?]) true])

                            :else
                            (rf/dispatch [:db/set-value (into state-path [:status]) :loading]))

                          (let [payload (cond-> {:module-id decoded-module-id
                                                  :queue-name decoded-queue-id
                                                  :pagination pagination-cursor
                                                  :limit 20
                                                  :reverse? reverse?}
                                          include-cursor?
                                          (assoc :include-cursor? true))]
                            (-> (rpc/call ::rpc-hf/get-queue-items!! payload)
                                (.then (fn [response-data]
                                         (if reverse?
                                           (rf/dispatch [:db/set-value (into state-path [:fetching-before?]) false])
                                           (rf/dispatch [:db/set-value (into state-path [:fetching-more?]) false]))
                                         (let [new-items (or (:items response-data) [])
                                               new-pagination (:pagination-params response-data)
                                               new-has-more? (queries/has-more-pages? new-pagination)
                                               current-data (or (get-in @rdb/app-db (into state-path [:data])) [])
                                               update-pagination? (not reverse?)]
                                           (cond
                                             append?
                                             (rf/dispatch [:db/set-value (into state-path [:data])
                                                              (vec (concat current-data new-items))])

                                             (and merge? (seq current-data))
                                             (rf/dispatch [:db/set-value (into state-path [:data])
                                                              (merge-queue-items current-data new-items)])

                                             :else
                                             (rf/dispatch [:db/set-value (into state-path [:data]) new-items]))
                                           (if update-pagination?
                                             (do
                                               (rf/dispatch [:db/set-value (into state-path [:pagination-params]) new-pagination])
                                               (rf/dispatch [:db/set-value (into state-path [:has-more?]) new-has-more?]))
                                             (do
                                               (rf/dispatch [:db/set-value (into state-path [:reverse-pagination-params]) new-pagination])
                                               (rf/dispatch [:db/set-value (into state-path [:has-more-before?]) new-has-more?])))
                                           (let [bidir-path (into state-path [:initial-bidir-outstanding])
                                                 bidir (get-in @rdb/app-db bidir-path)]
                                             (if (number? bidir)
                                               (let [n' (dec bidir)]
                                                 (if (pos? n')
                                                   (rf/dispatch [:db/set-value bidir-path n'])
                                                   (do
                                                     (rf/dispatch [:db/set-value bidir-path nil])
                                                     (rf/dispatch [:db/set-value (into state-path [:status]) :success]))))
                                               (rf/dispatch [:db/set-value (into state-path [:status]) :success]))))))
                                (.catch (fn [err]
                                          (if reverse?
                                            (rf/dispatch [:db/set-value (into state-path [:fetching-before?]) false])
                                            (rf/dispatch [:db/set-value (into state-path [:fetching-more?]) false]))
                                          (rf/dispatch [:db/set-value (into state-path [:initial-bidir-outstanding]) nil])
                                          (rf/dispatch [:db/set-value (into state-path [:status]) :error])
                                          (rf/dispatch [:db/set-value (into state-path [:error])
                                                           (if (map? err) (or (:error err) (str err)) (str err))])))))))
                      [enabled? decoded-module-id decoded-queue-id state-path])

          load-more (uix/use-callback
                     (fn []
                       (when (and has-more? (not is-loading?) (not is-fetching-more?))
                         (fetch-page pagination-params true false false false)))
                     [has-more? is-loading? is-fetching-more? pagination-params fetch-page])

          load-more-before (uix/use-callback
                            (fn []
                              (let [cursor (or reverse-pagination-params (:id (first data)))]
                                (when (and cursor has-more-before? (not is-loading?) (not is-fetching-before?))
                                  (fetch-page cursor false false true true))))
                            [reverse-pagination-params data has-more-before? is-loading? is-fetching-before? fetch-page])

          refetch (uix/use-callback
                   (fn []
                     (rf/dispatch [:db/set-value state-path
                                      {:status :idle
                                       :data []
                                       :pagination-params nil
                                       :reverse-pagination-params nil
                                       :has-more? true
                                       :has-more-before? false
                                       :fetching-more? false
                                       :fetching-before? false
                                       :initial-bidir-outstanding nil
                                       :error nil
                                       :should-refetch? false}])
                     (fetch-page nil false false false false))
                   [fetch-page state-path])]

      ;; Effect: Force refetch from start if flag is set and cache exists
      (uix/use-effect
       (fn []
         (when (and force-from-start? (seq data) enabled?)
           (refetch))
         js/undefined)
       [force-from-start?]) ; Only run on mount

      (uix/use-effect
       (fn []
         (when (and enabled?
                    (or (empty? data) initial-needed?))
           (if (and initial-needed? initial-cursor include-initial-cursor?)
             (do
               (rf/dispatch [:db/set-value (into state-path [:initial-bidir-outstanding]) 2])
               (fetch-page initial-cursor false true true false)
               (fetch-page initial-cursor false true true true))
             (fetch-page initial-cursor false include-initial-cursor? false false)))
         js/undefined)
       [enabled? data initial-needed? fetch-page initial-cursor include-initial-cursor?])

      (uix/use-effect
       (fn []
         (when (and should-refetch? enabled?)
           (rf/dispatch [:db/set-value (into state-path [:should-refetch?]) false])
           (refetch)))
       [should-refetch? enabled? refetch state-path])

      {:data data
       :isLoading is-loading?
       :isFetchingMore is-fetching-more?
       :isFetchingBefore is-fetching-before?
       :hasMore has-more?
       :hasMoreBefore has-more-before?
       :error error
       :loadMore load-more
       :loadMoreBefore load-more-before
       :refetch refetch})))

;; =============================================================================
;; CREATE / EDIT QUEUE FORM
;; =============================================================================

(defui metric-selector
  "Metric selector with required checkbox and remove button for rubric forms.

   Uses SearchableSelector under the hood."
  [{:keys [module-id value on-change on-remove required? index]}]
  ($ :div.flex.items-start.gap-2 {:data-testid (str "rubric-" index)}
     ;; Searchable metric selector
     ($ :div.flex-1
        {:key "selector"}
        ($ ss/SearchableSelector
           {:module-id module-id
            :value value
            :on-change on-change
            :rfq-key ::rpc-hf/get-metrics!!
            :items-key :items
            :item-id-fn :name
            :item-label-fn :name
            :placeholder "Select metric..."
            :label "Metric"
            :hide-label? true
            :data-testid "metric-selector"}))

     ;; Required checkbox
     ($ :label.flex.items-center.gap-1.pt-2
        {:key "checkbox"}
        ($ :input.rounded.border-gray-300
           {:data-testid "metric-required-checkbox"
            :type "checkbox"
            :checked (boolean required?)
            :onChange #(on-change value {:required (.. % -target -checked)})})
        ($ :span.text-sm.text-gray-600 "Required"))

     ;; Remove button
     ($ :button.text-red-600.hover:text-red-800.p-2.rounded.mt-1.cursor-pointer
        {:key "remove"
         :data-testid "remove-rubric-button"
         :type "button"
         :onClick on-remove}
        ($ TrashIcon {:className "h-5 w-5"}))))

(forms/reg-form
 :create-human-feedback-queue
 {:steps [:main]
  :main
  {:initial-fields (fn [props]
                     (merge {:name ""
                             :description ""
                             :rubrics []}
                            props))
   :validators {:name [forms/required]
                :rubrics [(fn [rubrics _form-state]
                            (cond
                              (empty? rubrics)
                              "At least one rubric is required"

                              :else
                              ;; Check for duplicate metrics
                              (let [metric-names (->> rubrics
                                                      (map :metric)
                                                      (filter some?))
                                    duplicates (->> metric-names
                                                    frequencies
                                                    (filter #(> (val %) 1))
                                                    (map key))]
                                (when (seq duplicates)
                                  (str "Duplicate metrics: " (str/join ", " duplicates))))))]}
   :ui (fn [{:keys [form-id props]}]
         (let [{:keys [module-id editing?]} props
               name-field (forms/use-form-field form-id :name)
               desc-field (forms/use-form-field form-id :description)
               rubrics-field (forms/use-form-field form-id :rubrics)
               rubrics (:value rubrics-field)

               add-rubric (fn []
                            ((:on-change rubrics-field)
                             (conj rubrics {:id (random-uuid)
                                            :metric nil
                                            :required false})))

               update-rubric (fn [idx metric-name opts]
                               (let [prev (get rubrics idx)
                                     opts' (dissoc opts :item)
                                     ;; Checkbox sends {:required ...} with `value` from a possibly
                                     ;; stale render — never merge {:metric nil} over a fresh selection.
                                     row (if (:item opts)
                                           (merge prev (merge {:metric metric-name} opts'))
                                           (merge prev (select-keys opts' [:required])))
                                     updated (assoc-in rubrics [idx] row)]
                                 ((:on-change rubrics-field) updated)))

               remove-rubric (fn [idx]
                               (let [updated (vec (concat (subvec rubrics 0 idx)
                                                          (subvec rubrics (inc idx))))]
                                 ((:on-change rubrics-field) updated)))]

           ($ :div.space-y-4.p-4
              ;; Name field (disabled when editing)
              ($ forms/form-field (merge {:label "Queue Name"
                                          :required? true
                                          :data-testid "queue-name-input"
                                          :placeholder "e.g., support-quality"
                                          :disabled editing?}
                                         name-field))

              ;; Guidelines field
              ($ forms/form-field (merge {:label "Guidelines"
                                          :data-testid "queue-description-input"
                                          :placeholder "What guidelines should reviewers follow when providing feedback?"}
                                         desc-field))

              ;; Rubrics section
              ($ :div.space-y-2
                 ($ :label.block.text-sm.font-medium.text-gray-700
                    "Rubric"
                    ($ :span.text-red-500.ml-1 "*"))

                 ($ :div.text-sm.text-gray-500.mb-2
                    "Add metrics that reviewers should evaluate")

                 ;; Rubric list
                 ($ :div.space-y-2
                    (vec
                     (for [[idx rubric] (map-indexed vector rubrics)]
                       ($ metric-selector
                          {:key (:id rubric)
                           :index idx
                           :module-id module-id
                           :value (:metric rubric)
                           :required? (:required rubric)
                           :on-change (fn [metric-name & [opts]]
                                        (update-rubric idx metric-name opts))
                           :on-remove #(remove-rubric idx)}))))

                 ;; Add rubric button
                 ($ :button.w-full.px-3.py-2.border-2.border-dashed.border-gray-300.rounded-md.text-gray-600.hover:border-gray-400.hover:text-gray-700.transition-colors.cursor-pointer
                    {:data-testid "add-rubric-button"
                     :type "button"
                     :onClick add-rubric}
                    "+ Add Metric")

                 ;; Error message
                 (when (:error rubrics-field)
                   ($ :p.text-sm.text-red-600.mt-1 (:error rubrics-field)))))))
   :modal-props (fn [props]
                  (if (:editing? props)
                    {:title "Edit Human Feedback Queue"
                     :submit-text "Update"}
                    {:title "Create Human Feedback Queue"
                     :submit-text "Create"}))}
  :on-submit
  {:mutation (fn [_db form-state]
               (let [{:keys [name description rubrics module-id editing?]} form-state
                     clean-rubrics (mapv #(dissoc % :id) rubrics)]
                 (if editing?
                   [::rpc-hf/update-queue!!
                    {:module-id module-id :name name :description description :rubrics clean-rubrics}]
                   [::rpc-hf/create-queue!!
                    {:module-id module-id :name name :description description :rubrics clean-rubrics}])))
   :on-success-invalidate (fn [_db {:keys [module-id]} _reply]
                            {:query-key-pattern [:human-feedback-queues module-id]})
   :rfq-invalidate-tags (fn [_db {:keys [module-id name]} _reply]
                          [[:human-feedback/queues module-id]
                           [:human-feedback/queue-info module-id name]])}})
