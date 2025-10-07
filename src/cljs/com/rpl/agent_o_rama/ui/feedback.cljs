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
   - :feedback - A feedback object containing :scores, :source, :created-at, :modified-at
   - :module-id - The module ID for constructing invocation URLs"
  [{:keys [feedback module-id]}]
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
                       raw-source-str)
          ;; Extract agent-invoke data from source to build invocation link
          agent-invoke (:agent-invoke source)
          task-id (:task-id agent-invoke)
          agent-invoke-id (:agent-invoke-id agent-invoke)
          evaluator-agent-name "_aor-evaluator"
          url (when (and task-id agent-invoke-id module-id)
                (str "/agents/" (common/url-encode module-id)
                     "/agent/" (common/url-encode evaluator-agent-name)
                     "/invocations/" task-id "-" agent-invoke-id))]
      ($ :div {:className "bg-purple-50 p-2 rounded-lg border border-purple-200"
               :data-id   "feedback-panel"}
         ($ :div {:className "text-sm font-medium text-purple-700 mb-1 flex items-center justify-between"}
            (if url
              ($ :a {:href      url
                     :target    "_blank"
                     :className "flex items-center gap-1 group hover:bg-purple-100 transition-colors rounded px-1"}
                 ($ :span {:className "text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full group-hover:bg-purple-200"}
                    source-str)
                 ($ ArrowTopRightOnSquareIcon {:className "h-3 w-3 text-purple-400 group-hover:text-purple-600"}))
              ($ :span {:className "text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full"}
                 source-str)))
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

(defui feedback-list
   "Displays a list of feedback items from the summary data.
   Props:
   - :feedback-data - The feedback object containing :results (vector of FeedbackImpl)
   - :module-id - The module ID for constructing URLs"
  [{:keys [feedback-data module-id]}]
  (let [results (:results feedback-data)]
    (if (and results (seq results))
      ($ :div {:className "space-y-2"
               :data-id "feedback-list"}
         ;; Display each feedback result
         (vec
          (for [[idx feedback] (map-indexed vector results)]
            ($ :div {:key       idx
                     :className "feedback-item"
                     :data-id   (str "feedback-item-" idx)}
               ($ feedback-panel {:feedback feedback
                                  :module-id module-id})))))

      ;; Empty state
      ($ :div {:className "text-gray-500 text-center py-8"
               :data-id "feedback-empty-state"}
         "No feedback available"))))
