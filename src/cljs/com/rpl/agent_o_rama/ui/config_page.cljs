(ns com.rpl.agent-o-rama.ui.config-page
  (:require
   [re-frame.core :as rf]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["@heroicons/react/24/outline" :refer [CheckIcon ArrowPathIcon InformationCircleIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.impl.ui.rpc.config :as rpc-config]
   [re-frame.query :as rfq]))

(defui config-item [{:keys [module-id agent-name item refetch]}]
  (let [{:keys [key doc current-value default-value input-type]} item
        [edit-value set-edit-value] (uix/use-state current-value)
        is-dirty? (not= (str current-value) (str edit-value))

        ;; Subscribe to the submission status for this specific item
        item-state (use-subscribe [::aor-rf/get-in [:ui :config-page (keyword key)]])
        submitting? (:submitting? item-state)
        submit-error (:error item-state)]

    ;; When the `current-value` prop changes from a refetch, update our local edit state
    (uix/use-effect (fn [] (set-edit-value current-value)) [current-value])

    (let [handle-save #(rf/dispatch [:config/submit-change
                                        {:module-id module-id
                                         :agent-name agent-name
                                         :key key
                                         :value edit-value
                                         :on-success refetch}])]
      ($ :div.bg-white.p-4.border.border-gray-200.rounded-lg.shadow-sm
         ($ :div.flex.justify-between.items-center.mb-2
            ($ :h3.text-md.font-semibold.text-gray-800.font-mono key))

         ($ :p.text-sm.text-gray-600.mb-4 doc)

         ($ :div.flex.items-center.gap-4
            ($ :input.flex-1.p-2.border.border-gray-300.rounded-md.font-mono.text-sm
               {:type (name input-type)
                :value edit-value
                :onChange #(set-edit-value (.. % -target -value))
                :disabled submitting?})
            ($ :button
               {:onClick handle-save
                :disabled (or (not is-dirty?) submitting?)
                :className (str "px-4 py-2 text-sm font-semibold rounded-md flex items-center gap-2 transition-colors "
                                (if (or (not is-dirty?) submitting?)
                                  "bg-gray-300 text-gray-500 cursor-not-allowed"
                                  "bg-blue-600 text-white hover:bg-blue-700 cursor-pointer"))}
               (if submitting?
                 ($ :<> ($ common/spinner {:size :medium}) "Saving...")
                 ($ :<> ($ CheckIcon {:className "h-4 w-4"}) "Save"))))

         ($ :div.flex.justify-between.items-center.mt-2.text-xs.text-gray-500
            ($ :span "Default: " ($ :code.font-mono default-value))
            (when (not= (str current-value) (str default-value))
              ($ :button.text-blue-600.hover:underline {:onClick #(set-edit-value default-value)}
                 "Reset to default")))

         (when submit-error
           ($ :div.mt-3.text-xs.text-red-600.bg-red-50.p-2.rounded.border.border-red-200
              submit-error))))))

(defui config-page []
  (let [{:keys [module-id agent-name]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        {:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-config/get-all!!
                        {:module-id module-id :agent-name agent-name}])
        loading? (#{:loading :idle} query-status)]

    ($ :div.p-6
       ($ :h2.text-2xl.font-semibold.text-gray-800.mb-2 "Agent Configuration")

       (cond
         loading? ($ :div.text-center.py-8 ($ common/spinner {:size :large}))
         error ($ :div.text-center.py-8.text-red-500 "Error loading configuration: " error)
         :else ($ :div.space-y-4.max-w-2xl.mx-auto

                  (for [item (sort-by :key data)]
                    ($ config-item {:key (:key item)
                                    :module-id module-id
                                    :agent-name agent-name
                                    :item item
                                    :refetch nil})))))))
