(ns com.rpl.agent-o-rama.ui.human-feedback.queues.item-detail
  "Single-item review form for a human feedback queue."
  (:require
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [ChevronLeftIcon ChevronRightIcon XMarkIcon ArrowTopRightOnSquareIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.human-feedback.metric-input :as metric-input]
   [com.rpl.agent-o-rama.ui.human-feedback.common :as hf-common]
   [com.rpl.agent-o-rama.ui.human-feedback.queues.common :as q-common]
   [com.rpl.agent-o-rama.impl.ui.rpc.human-feedback :as rpc-hf]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [re-frame.query :as rfq]
   [re-frame.core :as rf]
   [clojure.string :as str]
   [cljs.pprint]))

;; Helper to truncate text to first N lines
(defn- truncate-to-lines [text max-lines]
  (let [lines (str/split-lines text)
        line-count (count lines)]
    (if (<= line-count max-lines)
      {:text text
       :truncated? false
       :line-count line-count}
      {:text (str/join "\n" (take max-lines lines))
       :truncated? true
       :line-count line-count})))

;; Component for showing truncated JSON with expandable modal
(defui ExpandableJsonContent [{:keys [content title max-lines]
                               :or {max-lines 30}}]
  (let [;; If content is a string, try to parse it first, otherwise use as-is
        parsed-content content
        ;; Use with-out-str and pprint to ensure proper formatting
        pretty-str (if (string? parsed-content)
                     parsed-content ; Already a string, use as-is
                     (with-out-str (cljs.pprint/pprint parsed-content)))
        {:keys [text truncated? line-count]} (truncate-to-lines pretty-str max-lines)
        handle-expand (fn [e]
                        (.stopPropagation e)
                        (rf/dispatch [:modal/show :content-detail
                                      {:title title
                                       :component ($ common/ContentDetailModal
                                                     {:title title
                                                      :content pretty-str})}]))]
    ($ :div
       ($ :pre.text-xs.bg-gray-50.p-3.rounded.overflow-auto.max-h-64.font-mono.whitespace-pre
          text)
       (when truncated?
         ($ :div.mt-2.text-center
            ($ :button.text-xs.text-blue-600.hover:text-blue-800.font-medium.cursor-pointer
               {:onClick handle-expand}
               (str "Show all " line-count " lines ↗")))))))

;; Use the shared metric input component for queue item review
(defui metric-field [{:keys [rubric value on-change error data-testid]}]
  ($ metric-input/MetricInput
     {:metric (:metric rubric)
      :label (:name rubric)
      :description (:description rubric)
      :required? (:required rubric)
      :value value
      :on-change on-change
      :error error
      :data-testid data-testid}))

(defui item-detail []
  (let [{:keys [module-id queue-id item-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        decoded-module-id (common/url-decode module-id)
        decoded-queue-id (common/url-decode queue-id)
        item-id-str (str item-id)

        ;; Fetch queue info for rubrics
        {:keys [data]
         queue-info-status :status}
        (use-subscribe [::rfq/query ::rpc-hf/get-queue-info!!
                        {:module-id decoded-module-id :queue-name decoded-queue-id}])
        queue-info data
        queue-info-loading? (#{:loading :idle} queue-info-status)

        ;; Fetch queue items with shared cache for review session
        ;; If item isn't in cache yet, load from its cursor and merge.
        {:keys [data isLoading isBidirLoading isFetchingMore isFetchingBefore
                hasMore hasMoreBefore loadMore loadMoreBefore]}
        (q-common/use-queue-items
         {:module-id module-id
          :queue-id queue-id
          :initial-cursor item-id
          :include-initial-cursor? true
          :enabled? (boolean (and decoded-module-id decoded-queue-id item-id))})
        items-loading? isLoading
        items (or data [])
        [pending-next? set-pending-next?] (uix/use-state false)
        [pending-prev? set-pending-prev?] (uix/use-state false)

        ;; Find current item and navigation indices
        current-idx (some (fn [[idx item]] (when (= (str (:id item)) item-id-str) idx))
                          (map-indexed vector items))
        current-item (when current-idx (nth items current-idx nil))

        ;; Navigation:
        ;; - Previous disabled if we're at index 0 (started from URL cursor)
        ;; - Next enabled if there are more items in array OR hasMore on backend
        nav-busy? (or pending-next? pending-prev? isFetchingMore isFetchingBefore)
        has-prev? (and current-idx (or (> current-idx 0) hasMoreBefore))
        has-next? (and current-idx
                       (or (< current-idx (dec (count items)))
                           hasMore))
        prev-item-id (when (and current-idx (> current-idx 0))
                       (str (:id (nth items (dec current-idx)))))
        next-item-id (when (and current-idx (< current-idx (dec (count items))))
                       (str (:id (nth items (inc current-idx)))))

        handle-prev (fn []
                      (cond
                        (and current-idx (> current-idx 0))
                        (rfe/push-state :module/human-feedback-queue-item
                                        {:module-id module-id
                                         :queue-id queue-id
                                         :item-id prev-item-id})

                        hasMoreBefore
                        (do
                          (set-pending-prev? true)
                          (loadMoreBefore))

                        :else
                        nil))

        handle-next (fn []
                      (cond
                        next-item-id
                        (rfe/push-state :module/human-feedback-queue-item
                                        {:module-id module-id
                                         :queue-id queue-id
                                         :item-id next-item-id})

                        hasMore
                        (do
                          (set-pending-next? true)
                          (loadMore))

                        :else
                        nil))

;; Form state
        [scores set-scores] (uix/use-state {})
        [comment set-comment] (uix/use-state "")
        [reviewer-name set-reviewer-name] (uix/use-state (hf-common/get-reviewer-name))
        [errors set-errors] (uix/use-state {})

        ;; Validate a single metric value and return error or nil
        validate-metric (fn [rubric value]
                          (let [metric (:metric rubric)]
                            (cond
                              (or (nil? value) (= value ""))
                              nil ;; Don't show error for empty (will catch on submit if required)

                              (metric-input/numeric-metric? metric)
                              (let [int-val (js/parseInt value 10)]
                                (cond
                                  (js/isNaN int-val) "Must be an integer"
                                  (< int-val (:min metric)) (str "Must be at least " (:min metric))
                                  (> int-val (:max metric)) (str "Must be at most " (:max metric))
                                  :else nil))

                              :else nil)))

        validate-form (fn []
                        (let [errs (reduce (fn [acc rubric]
                                             (let [metric-name (:name rubric)
                                                   value (get scores metric-name)
                                                   metric (:metric rubric)]
                                               (cond
                                                 (and (:required rubric) (or (nil? value) (= value "")))
                                                 (assoc acc metric-name "This field is required")

                                                 (and (metric-input/numeric-metric? metric)
                                                      value
                                                      (not= value ""))
                                                 (let [int-val (js/parseInt value 10)
                                                       float-val (js/parseFloat value)]
                                                   (cond
                                                     (js/isNaN int-val)
                                                     (assoc acc metric-name "Must be an integer")

                                                     (not= int-val float-val)
                                                     (assoc acc metric-name "Must be an integer (no decimals)")

                                                     (< int-val (:min metric))
                                                     (assoc acc metric-name (str "Must be at least " (:min metric)))

                                                     (> int-val (:max metric))
                                                     (assoc acc metric-name (str "Must be at most " (:max metric)))

                                                     :else acc))

                                                 :else acc)))
                                           {}
                                           (or (:rubrics queue-info) []))]
                          (if (str/blank? reviewer-name)
                            (assoc errs :reviewer-name "Reviewer name is required")
                            errs)))

        handle-submit (fn []
                        (let [validation-errors (validate-form)]
                          (if (empty? validation-errors)
                            (do
                              (hf-common/save-reviewer-name! reviewer-name)
                              ;; Submit to backend
                              (-> (rpc/call ::rpc-hf/resolve-queue-item!!
                                            {:module-id decoded-module-id
                                             :queue-name decoded-queue-id
                                             :item-id item-id-str
                                             :target (:target current-item)
                                             :reviewer-name reviewer-name
                                             :scores scores
                                             :comment comment})
                                  (.then (fn [_]
                                           (rf/dispatch [:re-frame.query/invalidate-tags
                                                         [[:human-feedback/queue-items module-id queue-id]]])
                                           (set-scores {})
                                           (set-comment "")
                                           (set-errors {})
                                           (if has-next?
                                             (handle-next)
                                             (rfe/push-state :module/human-feedback-queue-end
                                                             {:module-id module-id :queue-id queue-id}))))
                                  (.catch (fn [err] (js/alert (str "Error submitting: " (if (map? err) (or (:error err) (str err)) (str err))))))))
                            (set-errors validation-errors))))

        handle-dismiss (fn []
                         (when (js/confirm "Dismiss this item? This will remove it from the queue without adding feedback. This action cannot be undone.")
                           ;; Dismiss via backend
                           (-> (rpc/call ::rpc-hf/dismiss-queue-item!!
                                         {:module-id decoded-module-id :queue-name decoded-queue-id :item-id item-id-str})
                               (.then (fn [_]
                                        (rf/dispatch [:re-frame.query/invalidate-tags
                                                      [[:human-feedback/queue-items module-id queue-id]]])
                                        (set-scores {})
                                        (set-comment "")
                                        (set-errors {})
                                        (if has-next?
                                          (rfe/push-state :module/human-feedback-queue-item
                                                          {:module-id module-id :queue-id queue-id :item-id next-item-id})
                                          (rfe/push-state :module/human-feedback-queue-detail
                                                          {:module-id module-id :queue-id queue-id}))))
                               (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))]

    (uix/use-effect
     (fn []
       (cond
         (and pending-next? next-item-id)
         (do
           (set-pending-next? false)
           (rfe/push-state :module/human-feedback-queue-item
                           {:module-id module-id
                            :queue-id queue-id
                            :item-id next-item-id}))

         (and pending-next? (not isFetchingMore) (not hasMore))
         (set-pending-next? false))
       js/undefined)
     [pending-next? next-item-id isFetchingMore hasMore module-id queue-id])

    (uix/use-effect
     (fn []
       (cond
         (and pending-prev? prev-item-id)
         (do
           (set-pending-prev? false)
           (rfe/push-state :module/human-feedback-queue-item
                           {:module-id module-id
                            :queue-id queue-id
                            :item-id prev-item-id}))

         (and pending-prev? (not isFetchingBefore) (not hasMoreBefore))
         (set-pending-prev? false))
       js/undefined)
     [pending-prev? prev-item-id isFetchingBefore hasMoreBefore module-id queue-id])

    (cond
      ;; Block during bidirectional initial load; otherwise show once the item is available.
      (or isBidirLoading (and items-loading? (not current-item)))
      ($ :div.p-6
         ($ :div.text-center.text-gray-500 "Loading..."))

      ;; Item not found
      (and (not items-loading?) (not current-item))
      ($ :div.p-6
         ($ :div.text-center.text-gray-500 "Item not found"))

      :else

      ($ :div.p-6.max-w-5xl.mx-auto
         ;; Header with navigation
         ($ :div.flex.justify-between.items-center.mb-6
            ($ :h2.text-2xl.font-bold.text-gray-900
               (str "Review Item: " item-id))
            ($ :div.flex.gap-2
               ($ :button.px-3.py-2.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                  {:disabled (or nav-busy? (not has-prev?))
                   :data-testid "previous-item-button"
                   :onClick #(when (and has-prev? (not nav-busy?)) (handle-prev))}
                  ($ ChevronLeftIcon {:className "h-5 w-5"}))
               ($ :button.px-3.py-2.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                  {:disabled (or nav-busy? (not has-next?))
                   :data-testid "next-item-button"
                   :onClick #(when (and has-next? (not nav-busy?)) (handle-next))}
                  ($ ChevronRightIcon {:className "h-5 w-5"}))))

         ;; Target Info Panel (Agent/Node with trace link)
         (let [target (:target current-item)
               item-input (:input current-item)
               item-output (:output current-item)
               input-unavailable? (= item-input q-common/TARGET-DOES-NOT-EXIST)
               output-unavailable? (= item-output q-common/TARGET-DOES-NOT-EXIST)
               output-for-dataset (:value (q-common/unwrap-agent-output item-output target))
               agent-name (:agent-name target)
               agent-invoke (:agent-invoke target)
               node-invoke (:node-invoke target)
               agent-task-id (:task-id agent-invoke)
               agent-invoke-id (:agent-invoke-id agent-invoke)
               is-node-target? (some? node-invoke)
               ;; Build URL to agent invocation trace
               ;; Note: module-id from route params is decoded, so we need to encode it
               base-url (str "/agents/" (common/url-encode decoded-module-id)
                             "/agent/" (common/url-encode agent-name)
                             "/invocations/" agent-task-id "-" agent-invoke-id)
               ;; Add node query parameter if this is a node target
               trace-url (if node-invoke
                           (let [node-task-id (:task-id node-invoke)
                                 node-invoke-id (:node-invoke-id node-invoke)
                                 node-id (str node-task-id "-" node-invoke-id)]
                             (str base-url "?node=" (common/url-encode node-id)))
                           base-url)
               handle-add-to-dataset
               (fn []
                 (when (and (not input-unavailable?) (not output-unavailable?))
                   (rf/dispatch
                    [:modal/show-form :add-from-trace
                     (merge {:module-id decoded-module-id
                             :title "Add to Dataset"}
                            (if (q-common/agent-target? target)
                              {:source-args item-input
                               :source-result output-for-dataset}
                              {:source-args item-input
                               :source-emits output-for-dataset}))])))]
           ($ :div.bg-gray-50.border.border-gray-200.rounded-md.p-4.mb-6
              {:data-testid "target-info-panel"}
              ($ :div.space-y-3
                 ($ :div.flex.items-start.justify-between
                    ($ :div.text-sm.font-semibold.text-gray-900 "Target Information")
                    ($ :div.flex.flex-col.items-end.gap-2
                       ($ :a.inline-flex.items-center.gap-1.px-3.py-1.text-xs.font-medium.text-blue-600.hover:text-blue-800.hover:bg-blue-50.rounded.transition-colors
                          {:href trace-url
                           :target "_blank"
                           :data-testid "trace-link"}
                          "View Trace"
                          ($ ArrowTopRightOnSquareIcon {:className "h-3.5 w-3.5"}))
                       ($ :button.inline-flex.items-center.justify-center.px-3.py-1.text-xs.font-medium.text-gray-700.bg-white.border.border-gray-300.rounded.hover:bg-gray-50.transition-colors.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                          {:type "button"
                           :data-testid "add-to-dataset-button"
                           :disabled (or input-unavailable? output-unavailable?)
                           :title (when (or input-unavailable? output-unavailable?)
                                    "Input/output data is not available for this item")
                           :onClick handle-add-to-dataset}
                          "Add to Dataset")))
                 ($ :div.flex.flex-col.gap-2
                    ;; Target type
                    ($ :div.flex.items-start.gap-2
                       ($ :span.text-xs.text-gray-500.w-20 "Type:")
                       ($ :span.text-sm.font-medium.text-gray-900
                          (if is-node-target? "Node" "Agent")))
                    ;; Agent name
                    ($ :div.flex.items-start.gap-2
                       ($ :span.text-xs.text-gray-500.w-20 "Agent:")
                       ($ :span.text-sm.font-mono.text-gray-900 agent-name))
                    ;; Invocation ID
                    ($ :div.flex.items-start.gap-2
                       ($ :span.text-xs.text-gray-500.w-20 "Invocation:")
                       ($ :span.text-xs.font-mono.text-gray-700
                          (str agent-task-id "-" agent-invoke-id)))
                    ;; Node invocation ID (if exists)
                    (when node-invoke
                      ($ :div.flex.items-start.gap-2
                         ($ :span.text-xs.text-gray-500.w-20 "Node Invoke:")
                         ($ :span.text-xs.font-mono.text-gray-700
                            (str (:task-id node-invoke) "-" (:node-invoke-id node-invoke)))))))))

;; Comment
         (when (not (str/blank? (:comment current-item)))
           ($ :div.bg-blue-50.border.border-blue-200.rounded-md.p-4.mb-6
              ($ :div.text-sm.text-blue-800 (:comment current-item))))

         ;; Input/Output Display
         (let [{:keys [failed? value]} (q-common/unwrap-agent-output (:output current-item) (:target current-item))]
           ($ :div.grid.grid-cols-2.gap-4.mb-6
              ($ :div.bg-white.border.border-gray-200.rounded-md.p-4
                 {:data-id "item-input"}
                 ($ :h3.text-sm.font-semibold.text-gray-700.mb-2 "Input")
                 ($ ExpandableJsonContent {:content (:input current-item)
                                           :title "Input"
                                           :max-lines 30}))
              ($ :div.bg-white.border.border-gray-200.rounded-md.p-4
                 {:data-id "item-output"}
                 ($ :h3.text-sm.font-semibold.mb-2
                    {:className (if failed? "text-red-600" "text-gray-700")}
                    (if failed? "Output (Failed)" "Output"))
                 ($ ExpandableJsonContent {:content value
                                           :title "Output"
                                           :max-lines 30}))))

         ;; Evaluation Form
         ($ :div.bg-white.border.border-gray-200.rounded-md.p-6.mb-6
            ;; Metric field
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Metrics")
            ($ :div.space-y-2
               (for [[idx rubric] (map-indexed vector (:rubrics queue-info))]
                 (let [metric-name (:name rubric)]
                   ($ metric-field {:key metric-name
                                    :rubric rubric
                                    :value (get scores metric-name)
                                    :on-change (fn [v]
                                                 (set-scores (assoc scores metric-name v))
                                                 ;; Real-time validation
                                                 (let [err (validate-metric rubric v)]
                                                   (set-errors (if err
                                                                 (assoc errors metric-name err)
                                                                 (dissoc errors metric-name)))))
                                    :error (get errors metric-name)
                                    :data-testid (str "metric-value-" idx)}))))

            ;; Comment field
            ($ :div.mb-4.mt-4
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
                  "Comment (optional)")
               ($ :textarea.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
                  {:value comment
                   :onChange #(set-comment (.. % -target -value))
                   :rows 3
                   :placeholder "Add any additional notes..."}))

            ;; Reviewer name
            ($ :div.mb-4
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
                  "Reviewer Name"
                  ($ :span.text-red-500.ml-1 "*"))
               ($ :input {:type "text"
                          :value reviewer-name
                          :onChange #(set-reviewer-name (.. % -target -value))
                          :placeholder "Your name"
                          :className (common/cn
                                      "w-full p-2 border border-gray-300 rounded-md focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                                      {"border-red-500" (:reviewer-name errors)})})
               (when (:reviewer-name errors)
                 ($ :div.text-sm.text-red-600.mt-1 (:reviewer-name errors)))))

         ;; Action buttons
         ($ :div.flex.justify-between
            ($ :button.px-4.py-2.border.border-red-300.text-red-700.rounded-md.hover:bg-red-50.transition-colors.inline-flex.items-center.cursor-pointer
               {:onClick handle-dismiss}
               ($ XMarkIcon {:className "h-5 w-5 mr-2"})
               "Dismiss")
            (let [has-errors? (or (seq errors)
                                  (str/blank? reviewer-name))
                  has-required-empty? (some (fn [rubric]
                                              (and (:required rubric)
                                                   (let [v (get scores (:name rubric))]
                                                     (or (nil? v) (= v "")))))
                                            (or (:rubrics queue-info) []))
                  is-invalid? (or has-errors? has-required-empty?)]
              ($ :button
                 {:onClick handle-submit
                  :disabled is-invalid?
                  :className (common/cn "px-6 py-2 rounded-md transition-colors"
                                        {"bg-gray-300 text-gray-500 cursor-not-allowed" is-invalid?
                                         "bg-blue-600 text-white hover:bg-blue-700 cursor-pointer" (not is-invalid?)})}
                 "Submit & Continue")))))))
