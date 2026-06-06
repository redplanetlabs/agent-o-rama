(ns com.rpl.agent-o-rama.ui.human-feedback.queues.detail
  "Human feedback queue detail page (queue info + items list)."
  (:require
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [PencilIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.human-feedback.metric-input :as metric-input]
   [com.rpl.agent-o-rama.ui.human-feedback.queues.common :as q-common]
   [com.rpl.agent-o-rama.impl.ui.rpc.human-feedback :as rpc-hf]
   [re-frame.query :as rfq]
   [re-frame.core :as rf]
   [clojure.string :as str]))

(defui queue-info-header [{:keys [queue-info queue-id module-id]}]
  (let [rubrics (:rubrics queue-info)
        decoded-module-id (common/url-decode module-id)

        handle-edit (fn []
                      ;; Transform rubrics: queue-info has {:name ... :required ...}
                      ;; but form expects {:metric ... :required ... :id ...}
                      (let [rubrics-for-form (mapv (fn [r]
                                                     {:id (random-uuid)
                                                      :metric (:name r)
                                                      :required (:required r)})
                                                   rubrics)]
                        (rf/dispatch [:modal/show-form :create-human-feedback-queue
                                         {:module-id decoded-module-id
                                          :name queue-id
                                          :description (or (:description queue-info) "")
                                          :rubrics rubrics-for-form
                                          :editing? true}])))]
    ($ :div.bg-white.rounded-md.border.border-gray-200.p-6.mb-6
       ($ :div.flex.justify-between.items-start
          ($ :div
             ($ :h3.text-lg.font-semibold.text-gray-900.mb-2
                (str "Queue: " queue-id))
             ($ :p.text-gray-600 (or (:description queue-info) "")))
          ($ :button.inline-flex.items-center.px-3.py-2.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.transition-colors.cursor-pointer
             {:onClick handle-edit
              :data-testid "edit-queue-button"}
             ($ PencilIcon {:className "h-5 w-5 mr-2"})
             "Edit Queue"))

       ;; Rubrics
       ($ :div.mt-4
          ($ :h4.text-sm.font-medium.text-gray-700.mb-2 "Metrics:")
          (if (empty? rubrics)
            ($ :p.text-sm.text-gray-500.italic "No metrics configured")
            ($ :div.space-y-2
               (for [rubric rubrics]
                 (let [metric (:metric rubric)
                       is-category? (metric-input/category-metric? metric)
                       is-numeric? (metric-input/numeric-metric? metric)]
                   ($ :div.flex.items-start.gap-2 {:key (:name rubric)}
                      ($ :span.inline-flex.px-2.py-1.rounded.text-xs.font-medium
                         {:className (if (:required rubric)
                                       "bg-blue-100 text-blue-700"
                                       "bg-gray-100 text-gray-600")}
                         (if (:required rubric) "Required" "Optional"))
                      ($ :div
                         ($ :span.font-medium.text-gray-900 (:name rubric))
                         (when is-category?
                           ($ :div.text-xs.text-gray-500.mt-1
                              "Categories: " (str/join ", " (:categories metric))))
                         (when is-numeric?
                           ($ :div.text-xs.text-gray-500.mt-1
                              (str "Range: " (:min metric) " - " (:max metric))))))))))))))

(defui queue-item-row [{:keys [item module-id queue-id]}]
  (let [input (:input item)
        output (:output item)
        target (:target item)
        input-unavailable? (= input q-common/TARGET-DOES-NOT-EXIST)
        output-unavailable? (= output q-common/TARGET-DOES-NOT-EXIST)
        {:keys [failed? value]} (q-common/unwrap-agent-output output target)]
    ($ :tr.hover:bg-gray-50.cursor-pointer
       {:onClick #(rfe/push-state :module/human-feedback-queue-item
                                  {:module-id module-id
                                   :queue-id queue-id
                                   :item-id (str (:id item))})}
       ($ :td.px-4.py-3.text-sm.text-gray-900.font-mono (str (:id item)))
       ($ :td.px-4.py-3.text-sm.text-gray-600 (or (:comment item) ""))
       ($ :td.px-4.py-3.text-sm.text-gray-600
          ($ :div.max-w-xs.truncate
             (if input-unavailable?
               ($ :span.text-gray-400.italic "Data unavailable")
               (common/to-json input))))
       ($ :td.px-4.py-3.text-sm
          {:className (if failed? "text-red-600 font-semibold" "text-gray-600")}
          ($ :div.max-w-xs.truncate
             (if output-unavailable?
               ($ :span.text-gray-400.italic "Data unavailable")
               (common/to-json value)))))))

(defui detail []
  (let [{:keys [module-id queue-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        decoded-module-id (common/url-decode module-id)
        decoded-queue-id (common/url-decode queue-id)

        ;; Query for queue info (description, rubrics)
        {:keys [data error]
         queue-info-status :status}
        (use-subscribe [::rfq/query ::rpc-hf/get-queue-info!!
                        {:module-id decoded-module-id :queue-name decoded-queue-id}])
        loading? (#{:loading :idle} queue-info-status)

        queue-info data
        queue-info-error error

        ;; Query for paginated queue items
        {:keys [data isLoading isFetchingMore hasMore loadMore error refetch]}
        (q-common/use-queue-items
         {:module-id module-id
          :queue-id queue-id
          :enabled? (boolean (and decoded-module-id decoded-queue-id))})
        items-error error

        queue-items data

        ;; Always refetch from start when opening the queue detail page
        _ (uix/use-effect
           (fn []
             (when (boolean (and decoded-module-id decoded-queue-id))
               (refetch))
             js/undefined)
           [module-id queue-id refetch decoded-module-id decoded-queue-id])]

    (cond
      (or loading? isLoading)
      ($ :div.p-6.flex.justify-center.items-center.py-12
         ($ common/spinner {:size :large}))

      (or queue-info-error items-error)
      ($ :div.p-6.text-red-500
         "Error loading queue: " (str (or queue-info-error items-error)))

      :else
      ($ :div.p-6
         ;; Queue info header
         ($ queue-info-header {:queue-info queue-info
                               :queue-id decoded-queue-id
                               :module-id module-id})

         ;; Queue items table
         (if (empty? queue-items)
           ($ :div.bg-white.rounded-md.border.border-gray-200.p-8.text-center.text-gray-500
              "No items in this queue yet.")

           ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
              ($ :table.w-full.text-sm
                 ($ :thead.bg-gray-50.border-b.border-gray-200
                    ($ :tr
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "ID")
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Comment")
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Input")
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Output")))
                 ($ :tbody.divide-y.divide-gray-200
                    (for [item queue-items]
                      ($ queue-item-row {:key (:id item)
                                         :item item
                                         :module-id module-id
                                         :queue-id queue-id})))

                 ;; Load more button
                 (when hasMore
                   ($ :tfoot.bg-gray-50.border-t.border-gray-200
                      ($ :tr
                         ($ :td.px-4.py-3.text-center.text-sm.text-blue-600.font-medium.cursor-pointer
                            {:colSpan 4
                             :onClick (when-not isFetchingMore loadMore)}
                            (if isFetchingMore
                              ($ :div.flex.justify-center.items-center.gap-2
                                 ($ common/spinner {:size :small})
                                 "Loading...")
                              "Load More"))))))))))))
