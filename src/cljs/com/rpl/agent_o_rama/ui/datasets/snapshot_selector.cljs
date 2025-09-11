(ns com.rpl.agent-o-rama.ui.datasets.snapshot-selector
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [PlusIcon TrashIcon ChevronDownIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [clojure.string :as str]))

(defui SnapshotManager [{:keys [module-id dataset-id selected-snapshot on-select-snapshot disabled? read-only?]}]
  (let [[dropdown-open? set-dropdown-open] (uix/use-state false)

        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:snapshot-names module-id dataset-id]
          :sente-event [:datasets/get-snapshot-names {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        snapshot-names (or (sort data) [])

        handle-create (fn []
                        (set-dropdown-open false)
                        (state/dispatch
                         [:modal/show-form :new-snapshot
                          {:module-id module-id
                           :dataset-id dataset-id
                           :from-snapshot-name selected-snapshot}]))

        handle-delete (fn [snapshot-name]
                        (set-dropdown-open false)
                        (when (js/confirm (str "Are you sure you want to delete snapshot '" snapshot-name "'?"))
                          (sente/request!
                           [:datasets/delete-snapshot {:module-id module-id :dataset-id dataset-id :snapshot-name snapshot-name}]
                           10000
                           (fn [reply]
                             (if (:success reply)
                               (do
                                 (when (= selected-snapshot snapshot-name)
                                   (on-select-snapshot "")) ;; Reset view to latest if deleting current
                                 (refetch))
                               (js/alert (str "Error deleting snapshot: " (:error reply))))))))

        handle-select (fn [snapshot-name]
                        (set-dropdown-open false)
                        (on-select-snapshot snapshot-name))

        current-display-name (if (str/blank? selected-snapshot)
                               "Latest (Working Copy)"
                               selected-snapshot)]

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [e]
                            (when dropdown-open?
                              (set-dropdown-open false)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [dropdown-open?])

    ($ :div.relative.inline-block.text-left
       ;; Main dropdown button
       ($ :button.inline-flex.items-center.justify-between.w-64.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-blue-500.cursor-pointer
          {:type "button"
           :onClick (fn [e]
                      (.stopPropagation e)
                      (let [is-opening (not dropdown-open?)]
                        (set-dropdown-open is-opening)
                        (when is-opening (refetch))))
           :disabled (or loading? disabled?)}
          ($ :span.truncate current-display-name)
          ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))

       ;; Dropdown menu
       (when dropdown-open?
         ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
            {:onClick #(.stopPropagation %)}
            ($ :div.py-1
               ;; Latest option
               ($ common/DropdownRow {:label "Latest (Working Copy)"
                                      :selected? (str/blank? selected-snapshot)
                                      :on-select #(handle-select "")
                                      :delete-button nil})

               ;; Named snapshots
               (for [name snapshot-names]
                 ($ common/DropdownRow {:key name
                                        :label name
                                        :selected? (= selected-snapshot name)
                                        :on-select #(handle-select name)
                                        :delete-button (when-not (or disabled? read-only?)
                                                         ($ :button.text-red-600.hover:text-red-800.p-1.rounded.hover:bg-red-100
                                                            {:type "button"
                                                             :onClick (fn [e]
                                                                        (.stopPropagation e)
                                                                        (handle-delete name))
                                                             :title (str "Delete " name)}
                                                            ($ TrashIcon {:className "h-3 w-3"})))}))

               ;; Divider
               ($ :div.border-t.border-gray-100.my-1)

               ;; New snapshot action
               (when-not (or disabled? read-only?)
                 ($ common/DropdownRow {:label "New snapshot"
                                        :action? true
                                        :on-select handle-create
                                        :icon ($ PlusIcon {:className "h-4 w-4"})
                                        :delete-button nil}))))))))