(ns com.rpl.agent-o-rama.ui.human-feedback.queues.index
  "Human feedback queues list page."
  (:require
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [TrashIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   ;; Side effect: registers :create-human-feedback-queue form
   [com.rpl.agent-o-rama.ui.human-feedback.queues.common]
   [com.rpl.agent-o-rama.impl.ui.rpc.human-feedback :as rpc-hf]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [re-frame.core :as rf]))

(defui index []
  (let [{:keys [module-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        decoded-module-id (common/url-decode module-id)

        ;; Search state
        [search-term set-search-term] (useState "")
        [debounced-search] (useDebounce search-term 300)

        {:keys [data isLoading isFetchingMore hasMore loadMore]}
        (queries/use-infinite-rpc-query
         {:rfq-key ::rpc-hf/get-queues-inf!!
          :params {:module-id decoded-module-id
                   :filters {:search-string debounced-search}}
          :page-size 20
          :enabled? true})]

    ($ :div.p-6
       ;; Header with Create Button
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h2.text-2xl.font-bold.text-gray-900 "Human Feedback Queues")
          ($ :button.bg-blue-600.text-white.px-4.py-2.rounded-md.hover:bg-blue-700.transition-colors.cursor-pointer
             {:data-testid "create-queue-button"
              :onClick #(rf/dispatch [:modal/show-form :create-human-feedback-queue {:module-id decoded-module-id}])}
             "+ Create Queue"))

       ;; Search bar
       ($ :div.mb-4
          ($ :input.w-full.px-4.py-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
             {:data-testid "search-queues-input"
              :type "text"
              :placeholder "Search queues..."
              :value search-term
              :onChange #(set-search-term (-> % .-target .-value))}))

       ;; Table or empty state
       (if (and (not isLoading) (empty? data))
         ($ :div.text-center.py-12.bg-gray-50.rounded-md
            {:data-testid "empty-state"}
            ($ :p.text-gray-500 "No queues found"))

         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Description")
                     ($ :th {:className (:th common/table-classes)} "Rubrics")
                     ($ :th {:className (:th common/table-classes)} "Actions")))

               ($ :tbody {:className (:tbody common/table-classes)}
                  (for [queue data]
                    (let [queue-name (:name queue)]
                      ($ :tr {:key queue-name
                              :className (:tr common/table-classes)
                              :data-testid (str "queue-row-" queue-name)}
                         ;; Name (clickable)
                         ($ :td {:className (:td common/table-classes)}
                            ($ :a.text-blue-600.hover:text-blue-800.font-medium
                               {:data-testid "queue-name-link"
                                :href (rfe/href :module/human-feedback-queue-detail
                                                {:module-id module-id
                                                 :queue-id (common/url-encode queue-name)})
                                :onClick (fn [e]
                                           (.preventDefault e)
                                           (rfe/push-state :module/human-feedback-queue-detail
                                                           {:module-id module-id
                                                            :queue-id (common/url-encode queue-name)}))}
                               queue-name))

                         ;; Description
                         ($ :td {:className (:td common/table-classes)}
                            ($ :span.text-gray-600 (or (:description queue) "")))

                         ;; Rubrics count
                         ($ :td {:className (:td common/table-classes)}
                            ($ :span.text-gray-600
                               {:data-testid "queue-rubric-count"}
                               (str (count (:rubrics queue)) " rubric"
                                    (if (= 1 (count (:rubrics queue))) "" "s"))))

                         ;; Actions
                         ($ :td {:className (:td common/table-classes)}
                            ($ :button.text-red-600.hover:text-red-800.p-2.rounded.cursor-pointer
                               {:data-testid "delete-queue-button"
                                :onClick (fn [e]
                                           (.stopPropagation e)
                                           (when (js/confirm (str "Are you sure you want to delete queue \"" queue-name "\"?"))
                                             (-> (rpc/call ::rpc-hf/delete-queue!! {:module-id decoded-module-id :name queue-name})
                                             (.then (fn [_]
                                                      (rf/dispatch [:query/invalidate {:query-key-pattern [:human-feedback-queues module-id]}])
                                                      (rf/dispatch [:re-frame.query/invalidate-tags [[:human-feedback/queues module-id]]])))
                                             (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))}
                               ($ TrashIcon {:className "h-5 w-5"})))))))

               ;; Load more row
               (when hasMore
                 ($ :tfoot
                    ($ :tr
                       ($ :td {:colSpan 4 :className "px-6 py-4 text-center"}
                          ($ :button.text-blue-600.hover:text-blue-800.font-medium
                             {:onClick loadMore
                              :disabled isFetchingMore}
                             (if isFetchingMore "Loading..." "Load More"))))))))))))
