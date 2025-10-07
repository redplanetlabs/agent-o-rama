(ns com.rpl.agent-o-rama.ui.feedback
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.common :as common]
   ["@heroicons/react/24/outline" :refer [ArrowTopRightOnSquareIcon]]))

(defn format-ms [ms]
  (let [date (js/Date. ms)
        formatter (js/Intl.DateTimeFormat.
                   "en-US"
                   #js {:year "numeric"
                        :month "short"
                        :day "numeric"
                        :hour "2-digit"
                        :minute "2-digit"
                        :second "2-digit"
                        :hour12 false})
        base (.replace (.format formatter date) "," "")
        millis (.padStart (str (.getMilliseconds date)) 3 "0")]
    (str base "." millis)))

(defui feedback-panel
  "Displays a single feedback item with scores, source, and timestamps.
   Props:
   - :feedback - A feedback object containing :scores, :source, :created-at, :modified-at"
  [{:keys [feedback]}]
  (when (and feedback (seq (:scores feedback)))
    (let [scores (:scores feedback)
          source (:source feedback)
          created-at (:created-at feedback)
          modified-at (:modified-at feedback)
          raw-source-str (:source source "Unknown")
          ;; Remove agent name prefix before "/" if present
          ;; e.g., "action[FeedbackTestAgent/agent-dual-eval]" -> "action[agent-dual-eval]"
          source-str (if-let [slash-idx (clojure.string/index-of raw-source-str "/")]
                       (let [before-slash (subs raw-source-str 0 slash-idx)
                         after-slash (subs raw-source-str (inc slash-idx))
                         ;; Find the opening bracket before the slash
                         bracket-idx (clojure.string/last-index-of before-slash "[")]
                         (if bracket-idx
                           (str (subs before-slash 0 (inc bracket-idx)) after-slash)
                           raw-source-str))
                       raw-source-str)]
      ($ :div {:className "bg-purple-50 p-2 rounded-lg border border-purple-200"
               :data-id   "feedback-panel"}
         ($ :div {:className "text-sm font-medium text-purple-700 mb-1 flex items-center justify-between"}
            ($ :span {:className "text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full"}
               source-str))
         ($ :div {:className "space-y-1"}
            ;; Display scores
            (vec
             (for [[score-name score-value] (sort-by key scores)]
               (let [score-name (name score-name)]
                 ($ :div {:key       score-name
                          :className "flex justify-between items-center"}
                    ($ :span {:className "text-xs font-medium text-purple-600"}
                       score-name)
                    ($ :span {:className "text-sm font-semibold text-purple-800"}
                       (if (number? score-value)
                         (str score-value)
                         (str score-value)))))))
            ;; Display timestamp if available
            (when created-at
              ($ :div {:className "text-xs text-purple-500 mt-1 pt-1 border-t border-purple-200"}
                 (str "Created: " (format-ms created-at)))))))))

(defui feedback-actions
  [{:keys [module-id evaluator-agent-name actions]}]
  ($ :div {:className "bg-gray-50 p-2 rounded-lg border border-gray-200 mb-2"}
     ($ :div {:className "text-sm font-medium text-gray-700 mb-1"} "Actions")
     ($ :div {:className "space-y-1"}
        (vec
         (for [[action-name action-value] (sort-by key actions)]
           (let [action-name     (name action-name)
                 task-id         (:task-id action-value)
                 agent-invoke-id (:agent-invoke-id action-value)
                 url             (when (and task-id agent-invoke-id module-id)
                                   (str "/agents/" (common/url-encode module-id)
                                        "/agent/" (common/url-encode evaluator-agent-name)
                                        "/invocations/" task-id "-" agent-invoke-id))]
             (if url
               ($ :a {:key       action-name
                      :href      url
                      :target    "_blank"
                      :className "flex items-center justify-between group hover:bg-gray-100 transition-colors rounded px-1"}
                  ($ :span {:className "text-xs font-medium text-gray-700 group-hover:text-indigo-600"}
                     action-name)
                  ($ :div {:className "flex items-center gap-1"}
                     ($ ArrowTopRightOnSquareIcon {:className "h-3 w-3 text-gray-400 group-hover:text-indigo-600"})))
               ($ :div {:key       action-name
                        :className "flex items-center justify-between px-1"}
                  ($ :span {:className "text-xs font-medium text-gray-700"}
                     action-name)
                  ($ :span {:className "text-xs text-gray-500"}
                     "No invocation data")))))))))

(defui feedback-list
   "Displays a list of feedback items from the summary data.
   Props:
   - :feedback-data - The feedback object containing :results (vector of FeedbackImpl)
   - :module-id - The module ID for constructing URLs"
  [{:keys [feedback-data module-id]}]
  (let [results (:results feedback-data)
        actions (:actions feedback-data)
        evaluator-agent-name "_aor-evaluator"]
    (if (and results (seq results))
      ($ :div {:className "space-y-2"
               :data-id "feedback-list"}
         ;; Display actions if present
         (when (and actions (seq actions))
           ($ feedback-actions
              {:actions actions
               :module-id module-id
               :evaluator-agent-name evaluator-agent-name}))

         ;; Display each feedback result
         (vec
          (for [[idx feedback] (map-indexed vector results)]
            ($ :div {:key       idx
                     :className "feedback-item"
                     :data-id   (str "feedback-item-" idx)}
               ($ feedback-panel {:feedback feedback})))))

      ;; Empty state
      ($ :div {:className "text-gray-500 text-center py-8"
               :data-id "feedback-empty-state"}
         "No feedback available"))))
