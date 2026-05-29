(ns com.rpl.agent-o-rama.ui.datasets.index
  (:require
   [uix.core :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon MagnifyingGlassIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
   [re-frame.core :as rf]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]))

(defn get-dataset-path [module-id dataset-id]
  (rfe/href :module/dataset-detail.examples
            {:module-id module-id
             :dataset-id dataset-id}))

(defui index [{:keys [module-id]}]
  (let [;; Add state for search term and debounce it
        [search-term set-search-term] (useState "")
        [debounced-search-term] (useDebounce search-term 300)

        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-infinite-rpc-query
         {:rfq-key ::rpc-datasets/get-all-inf!!
          :params {:module-id module-id
                   :filters (when-not (str/blank? debounced-search-term)
                              {:search-string debounced-search-term})}
          :page-size 3
          :enabled? (boolean module-id)})]

    ($ :div.p-6
       ;; Header with search input
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex.items-center.gap-3
             ($ CircleStackIcon {:className "h-8 w-8 text-indigo-600"})
             ;; Search input field
             ($ :div.relative.ml-4
                ($ :div.pointer-events-none.absolute.inset-y-0.left-0.flex.items-center.pl-3
                   ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"}))
                ($ :input
                   {:type "text"
                    :value search-term
                    :onChange #(set-search-term (.. % -target -value))
                    :className "block w-full rounded-md border-0 py-1.5 pl-10 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    :placeholder "Search datasets..."})))

          ($ :div.flex.items-center.gap-2
             ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors.cursor-pointer
                {:onClick #(rf/dispatch [:modal/show-form :create-dataset {:module-id module-id}])}
                ($ PlusIcon {:className "h-5 w-5 mr-2"})
                "Create Dataset")))

       ;; Content
       (cond
         (and isLoading (empty? data))
         ($ :div.flex.items-center.justify-center.h-full ($ :div "Loading datasets..."))

         error
         ($ :div.flex.items-center.justify-center.h-full ($ :div.text-red-500 "Error loading datasets"))

         (empty? data)
         ($ :div.text-center.py-12
            ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400 mb-4"})
            ($ :h3.text-lg.font-medium.text-gray-900.mb-2 "No datasets yet")
            ($ :p.text-gray-500.mb-6 "Create your first dataset to get started.")
            ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors.cursor-pointer
               {:onClick #(rf/dispatch [:modal/show-form :create-dataset {:module-id module-id}])}
               ($ PlusIcon {:className "h-5 w-5 mr-2"})
               "Create Dataset"))

         :else
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Description")
                     ($ :th {:className (:th common/table-classes)} "Created")
                     ($ :th {:className (:th common/table-classes)} "Modified")
                     ($ :th {:className (:th common/table-classes)} "Actions")))
               ($ :tbody
                  (into []
                        (for [dataset data
                              :let [is-remote? (:remote? dataset)
                                    name (:name dataset)
                                    desc (:description dataset)
                                    dsid (:dataset-id dataset)
                                    href (get-dataset-path module-id dsid)
                                    ;; Format remote description
                                    remote-desc (when is-remote?
                                                  (let [host (:remote-host dataset)
                                                        port (:remote-port dataset)
                                                        module (:remote-module-name dataset)]
                                                    (cond
                                                      (and host port) (str host ":" port " " module)
                                                      host (str host " " module)
                                                      :else module)))]]
                          ($ :tr {:key dsid
                                  :className (common/cn "hover:bg-gray-50 cursor-pointer"
                                                        {"bg-purple-50" is-remote?})
                                  :onClick (fn [_]
                                             (rfe/push-state :module/dataset-detail.examples
                                                             {:module-id module-id
                                                              :dataset-id dsid}))}
                             ($ :td {:className (:td common/table-classes)}
                                (if is-remote?
                                  ($ :div.flex.flex-col.gap-1
                                     ($ :span.text-purple-700.font-semibold.uppercase.text-xs "REMOTE DATASET")
                                     ($ :span.text-xs.text-gray-600 name))
                                  ($ :a.text-indigo-600.hover:text-indigo-800 {:href href} name)))
                             ($ :td {:className (:td common/table-classes)}
                                (if is-remote?
                                  ($ :span.text-sm.text-purple-600.font-mono remote-desc)
                                  (if (seq (str desc))
                                    ($ :span.text-sm.text-gray-600.desc.truncate {:title desc} desc)
                                    ($ :span.text-sm.text-gray-400.italic "—"))))
                             ($ :td {:className (:td common/table-classes)}
                                (when-not is-remote?
                                  ($ :span.text-sm.text-gray-600 {:title (common/format-timestamp (:created-at dataset))}
                                     (common/format-relative-time (:created-at dataset)))))
                             ($ :td {:className (:td common/table-classes)}
                                (when-not is-remote?
                                  ($ :span.text-sm.text-gray-600 {:title (common/format-timestamp (:modified-at dataset))}
                                     (common/format-relative-time (:modified-at dataset)))))
                             ($ :td {:className (:td-right common/table-classes)}
                                ($ :div.flex.items-center.space-x-2
                                   (when-not is-remote?
                                     ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-gray-700.cursor-pointer
                                        {:onClick (fn [e]
                                                    (.preventDefault e)
                                                    (.stopPropagation e)
                                                    (rf/dispatch [:modal/show-form :edit-dataset
                                                                     {:module-id module-id
                                                                      :dataset-id dsid
                                                                      :name name
                                                                      :description desc
                                                                      :initial-name name
                                                                      :initial-description desc}]))}
                                        ($ PencilIcon {:className "h-4 w-4 mr-1"})
                                        "Edit"))
                                   ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                      {:onClick (fn [e]
                                                  (.preventDefault e)
                                                  (.stopPropagation e)
                                                  (when (js/confirm (str "Are you sure you want to delete dataset '" name "'? This action cannot be undone."))
                                                    (-> (rpc/call ::rpc-datasets/delete!! {:module-id module-id :dataset-id dsid})
                                                    (.then (fn [_]
                                                             (do (rf/dispatch [:query/invalidate {:query-key-pattern [:datasets module-id]}])
                                                             (rf/dispatch [:re-frame.query/invalidate-tags [[:datasets module-id]]]))))
                                                    (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))}
                                      ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                      "Delete")))))))

               ;; Load More button
               (when hasMore
                 ($ :tfoot.bg-gray-50.border-t.border-gray-200
                    ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                       {:onClick (when-not isFetchingMore loadMore)}
                       ($ :td.px-4.py-3.cursor-pointer {:colSpan 5}
                          ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                             ($ :span.mr-2.text-sm.font-medium (if isFetchingMore "Loading..." "Load More"))
                             (when-not isFetchingMore
                               ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                                  ($ :path {:fillRule "evenodd"
                                            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                            :clipRule "evenodd"}))))))))))))))
