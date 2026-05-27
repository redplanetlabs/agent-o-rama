(ns com.rpl.agent-o-rama.ui.invocations.graph-status
  (:require
   [uix.core :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   ["@heroicons/react/24/outline" :refer [ExclamationTriangleIcon]]))

(defui node-status-bar
  [{:keys [in-progress? is-stuck? has-changes has-human-request has-exceptions has-result]}]
  (let [indicators (cond-> []
                     (and in-progress? (not is-stuck?))
                     (conj {:type :spinner :title "Processing..."})
                     is-stuck?
                     (conj {:type :stuck :title "Node terminated due to max retries"})
                     has-changes
                     (conj {:type :changed :title "Modified for fork"})
                     has-human-request
                     (conj {:type :human :title "Awaiting human input"})
                     (and has-exceptions (not is-stuck?))
                     (conj {:type :exception :title "Has exceptions"})
                     has-result
                     (conj {:type :success :title "Completed successfully"}))]
    (when (seq indicators)
      ($ :div {:className (common/cn "absolute -top-1 -right-1 flex items-center gap-0.5 rounded-full px-0.5 py-0.5 bg-white border border-gray-200")}
         (for [{:keys [type title]} indicators]
           ($ :div {:key type
                    :className (common/cn "w-3 h-3 flex items-center justify-center")
                    :title title}
              (case type
                :spinner ($ common/spinner {:size :small})
                :stuck ($ :div {:className (common/cn "w-3 h-3 bg-red-500 rounded-full flex items-center justify-center")}
                          ($ :svg {:className (common/cn "w-2 h-2 text-white") :fill "none" :viewBox "0 0 24 24" :stroke "currentColor"}
                             ($ :path {:strokeLinecap "round" :strokeLinejoin "round" :strokeWidth 3 :d "M6 18L18 6M6 6l12 12"})))
                :changed ($ :div {:className (common/cn "w-3 h-3 bg-orange-400 rounded-full")})
                :human ($ :div {:className (common/cn "w-3 h-3 flex items-center justify-center text-xs")} "🙋")
                :exception ($ :div {:className (common/cn "w-3 h-3 bg-yellow-500 rounded-full flex items-center justify-center")}
                              ($ ExclamationTriangleIcon {:className "w-2 h-2 text-white"}))
                :success ($ :div {:className (common/cn "w-3 h-3 bg-green-500 rounded-full")})
                nil)))))))
