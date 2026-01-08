(ns com.rpl.agent-o-rama.ui.feedback
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.human-feedback.manual-feedback :as manual-feedback]
   ["@heroicons/react/24/outline" :refer [ArrowTopRightOnSquareIcon PlusIcon PencilIcon TrashIcon]]))

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
   - :module-id - The module ID for constructing invocation URLs
   - :agent-name - Agent name for edit/delete operations
   - :invoke-id - Invoke ID for edit/delete operations
   - :node-task-id - Node task ID (optional, for node feedback)
   - :node-invoke-id - Node invoke ID (optional, for node feedback)"
  [{:keys [feedback module-id agent-name invoke-id node-task-id node-invoke-id]}]
  (when (and feedback (seq (:scores feedback)))
    (let [scores (:scores feedback)
          source (:source feedback)
          created-at (:created-at feedback)
          modified-at (:modified-at feedback)
          feedback-id (:id feedback)
          comment (:comment feedback)
          raw-source-str (:source source "Unknown")
          ;; Check if this is human feedback (HumanSourceImpl)
          is-human-feedback? (or (= "HumanSourceImpl" (get source "_aor-type"))
                                 (contains? source :name))  ;; HumanSource has :name field
          human-name (or (:name source) (get source "name"))
          source-feedback-id (or (:id source) (get source "id"))
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
            ($ :div.flex.items-center.gap-2
               (if (and url (not is-human-feedback?))
                 ($ :a {:href      url
                        :target    "_blank"
                        :className "flex items-center gap-1 group hover:bg-purple-100 transition-colors rounded px-1"}
                    ($ :span {:className "text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full group-hover:bg-purple-200"}
                       source-str)
                    ($ ArrowTopRightOnSquareIcon {:className "h-3 w-3 text-purple-400 group-hover:text-purple-600"}))
                 ($ :span {:className "text-xs bg-purple-100 text-purple-600 px-2 py-0.5 rounded-full"}
                    (if is-human-feedback?
                      (str "Human: " human-name)
                      source-str))))
            
            ;; Edit/Delete buttons for human feedback
            (when (and is-human-feedback? source-feedback-id agent-name invoke-id)
              ($ :div.flex.items-center.gap-1
                 ($ :button.p-1.text-purple-600.hover:text-purple-800.hover:bg-purple-200.rounded.transition-colors
                    {:type "button"
                     :title "Edit feedback"
                     :data-testid "edit-feedback-button"
                     :onClick #(state/dispatch [:modal/show-form :add-manual-feedback
                                                {:module-id module-id
                                                 :agent-name agent-name
                                                 :invoke-id invoke-id
                                                 :node-task-id node-task-id
                                                 :node-invoke-id node-invoke-id
                                                 :feedback-id (str source-feedback-id)
                                                 :editing? true
                                                 :reviewer-name human-name
                                                 :comment (or comment "")
                                                 ;; Pre-populate metrics from scores
                                                 :metrics []}])}
                    ($ PencilIcon {:className "h-4 w-4"}))
                 
                 ($ :button.p-1.text-red-600.hover:text-red-800.hover:bg-red-100.rounded.transition-colors
                    {:type "button"
                     :title "Delete feedback"
                     :data-testid "delete-feedback-button"
                     :onClick #(when (js/confirm "Are you sure you want to delete this feedback?")
                                 (state/dispatch [:sente/request
                                                  [:human-feedback/delete-feedback
                                                   {:module-id module-id
                                                    :agent-name agent-name
                                                    :invoke-id invoke-id
                                                    :node-task-id node-task-id
                                                    :node-invoke-id node-invoke-id
                                                    :feedback-id (str source-feedback-id)}]
                                                  10000
                                                  (fn [reply]
                                                    (when (:success reply)
                                                      (state/dispatch [:invocation/start-graph-loading
                                                                       {:invoke-id invoke-id
                                                                        :module-id module-id
                                                                        :agent-name agent-name}])))]))}
                    ($ TrashIcon {:className "h-4 w-4"})))))
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
            ;; Display comment if present
            (when (and is-human-feedback? (not (clojure.string/blank? comment)))
              ($ :div {:className "text-xs text-purple-600 mt-1 pt-1 border-t border-purple-200"}
                 ($ :span.font-medium "Comment: ")
                 comment))
            ;; Display timestamp if available
            (when created-at
              ($ :div {:className "text-xs text-purple-500 mt-1 pt-1 border-t border-purple-200"}
                 (str "Created: " (format-ms created-at)))))))))

(defui feedback-list
  "Displays a list of feedback items from the summary data.
   Props:
   - :feedback-data - The feedback object containing :results (vector of FeedbackImpl)
   - :module-id - The module ID for constructing URLs
   - :agent-name - Agent name for add/edit/delete operations
   - :invoke-id - Invoke ID for add/edit/delete operations
   - :node-task-id - Node task ID (optional, for node feedback)
   - :node-invoke-id - Node invoke ID (optional, for node feedback)"
  [{:keys [feedback-data module-id agent-name invoke-id node-task-id node-invoke-id]}]
  (let [results (:results feedback-data)]
    ($ :div
       ;; Add feedback button
       (when (and module-id agent-name invoke-id)
         ($ :div.mb-4
            ($ :button.inline-flex.items-center.px-3.py-2.bg-purple-600.text-white.text-sm.font-medium.rounded-md.hover:bg-purple-700.transition-colors
               {:data-testid "add-feedback-button"
                :onClick #(state/dispatch [:modal/show-form :add-manual-feedback
                                           {:module-id module-id
                                            :agent-name agent-name
                                            :invoke-id invoke-id
                                            :node-task-id node-task-id
                                            :node-invoke-id node-invoke-id
                                            :editing? false}])}
               ($ PlusIcon {:className "h-4 w-4 mr-1"})
               "Add Feedback")))
       
       ;; Feedback list
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
                                     :module-id module-id
                                     :agent-name agent-name
                                     :invoke-id invoke-id
                                     :node-task-id node-task-id
                                     :node-invoke-id node-invoke-id})))))

         ;; Empty state
         ($ :div {:className "text-gray-500 text-center py-8"
                  :data-id "feedback-empty-state"}
            "No feedback available")))))
