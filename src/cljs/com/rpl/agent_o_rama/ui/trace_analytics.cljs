(ns com.rpl.agent-o-rama.ui.trace-analytics
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [ArrowPathIcon]]))

(defui info
  "Displays analytics and statistics for a trace execution.
   Takes summary-data and renders overall statistics."
  [{:keys [summary-data]}]
  (let [retry-count          (:retry-num summary-data)
        ;; TODO replace dummy values with real analytics
        total-execution-time (unchecked-subtract
                              (:finish-time-millis summary-data)
                              (:start-time-millis summary-data))
        total-tokens         45892
        store-reads          127
        store-writes         23
        model-calls          156]
    ($ :div
       {:className "grid grid-cols-1 gap-3"}
       ;; Execution time
       ($ :div
          {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
          ($ :div
             {:className "flex justify-between items-center"}
             ($ :div
                ($ :div {:className "text-sm font-medium text-gray-700"} "Execution Time"))
             ($ :div
                {:className "text-right"}
                ($ :div
                   {:className "text-lg font-bold text-gray-800"}
                   (str (.toLocaleString total-execution-time) "ms")))))

       ;; Retry count
       (when (and retry-count (> retry-count 0))
         ($ :div
            {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
            ($ :div
               {:className "flex justify-between items-center"}
               ($ :div
                  {:className "flex items-center gap-2"}
                  ($ ArrowPathIcon {:className "h-4 w-4 text-gray-600"})
                  ($ :div {:className "text-sm font-medium text-gray-700"} "Retries"))
               ($ :div
                  {:className "text-right"}
                  ($ :div {:className "text-lg font-bold text-gray-800"} retry-count)))))

       ;; Store operations
       ($ :div
          {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
          ($ :div
             ($ :div {:className "text-sm font-medium text-gray-700 mb-2"} "Store Operations")
             ($ :div
                {:className "flex justify-between items-center"}
                ($ :div
                   ($ :div {:className "text-xs text-gray-600"} "Reads")
                   ($ :div {:className "text-lg font-bold text-gray-800"} store-reads))
                ($ :div
                   ($ :div {:className "text-xs text-gray-600"} "Writes")
                   ($ :div {:className "text-lg font-bold text-gray-800"} store-writes)))))

       ;; Model calls
       ($ :div
          {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
          ($ :div
             {:className "flex justify-between items-center"}
             ($ :div
                ($ :div {:className "text-sm font-medium text-gray-700"} "Model Calls"))
             ($ :div
                {:className "text-right"}
                ($ :div {:className "text-lg font-bold text-gray-800"} model-calls))))

       ;; Tokens
       ($ :div
          {:className "bg-gray-50 p-3 rounded-lg border border-gray-200"}
          ($ :div
             {:className "flex justify-between items-center"}
             ($ :div
                ($ :div {:className "text-sm font-medium text-gray-700"} "Tokens"))
             ($ :div
                {:className "text-right"}
                ($ :div
                   {:className "text-lg font-bold text-gray-800"}
                   (str (.toLocaleString total-tokens)))))))))
