(ns com.rpl.agent-o-rama.ui.human-feedback.add-to-queue
  (:require
   [uix.core :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.searchable-selector :refer [SearchableSelector]]
   ["@heroicons/react/24/outline" :refer [ArrowDownIcon]]))

(defui QueueCombobox [{:keys [module-id value on-change error required?]}]
  ($ SearchableSelector
     {:module-id module-id
      :value value
      :on-change on-change
      :sente-event-fn (fn [module-id search-string]
                        [:human-feedback/get-queues
                         {:module-id module-id
                          :filters {:search-string (or search-string "")}}])
      :items-key :items
      :item-id-fn :name
      :item-label-fn :name
      :placeholder "Type to search queues..."
      :label "Human Feedback Queue"
      :required? required?
      :error error
      :data-testid "queue-selector"}))

(defui AddToQueueForm [{:keys [form-id]}]
  (let [;; Form fields
        queue-name-field (forms/use-form-field form-id :queue-name)
        comment-field (forms/use-form-field form-id :comment)

        props (state/use-sub [:forms form-id])
        {:keys [module-id source-type agent-name node-name]} props]

    ($ forms/form
       ($ :div.space-y-4.p-4
          ;; Source info display (at top, clean styling)
          ($ :div.space-y-1.text-sm.text-gray-600
             ($ :div.flex.gap-2
                ($ :span.text-gray-500 "Type:")
                ($ :span.font-medium (name source-type)))
             (when node-name
               ($ :div.flex.gap-2
                  ($ :span.text-gray-500 "Node:")
                  ($ :span.font-medium.font-mono node-name)))
             ($ :div.flex.gap-2
                ($ :span.text-gray-500 "Agent:")
                ($ :span.font-medium.font-mono agent-name)))

          ;; Down arrow indicator
          ($ :div.flex.justify-center.text-gray-400
             ($ ArrowDownIcon {:className "h-5 w-5"}))

          ;; Queue Selection
          ($ QueueCombobox {:module-id module-id
                           :value (:value queue-name-field)
                           :on-change (:on-change queue-name-field)
                           :error (:error queue-name-field)
                           :required? true})

          ;; Optional comment
          ($ forms/form-field
             {:label "Comment (optional)"
              :type :textarea
              :rows 3
              :value (:value comment-field)
              :on-change (:on-change comment-field)
              :error (:error comment-field)
              :help-text "Add a note for the reviewer"
              :data-testid "comment-input"})))))

(forms/reg-form
 :add-to-feedback-queue
 {:steps [:main]
  :main
  {:initial-fields
   (fn [props]
     (merge
      {:queue-name ""
       :comment ""}
      props))
   :validators {:queue-name [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ AddToQueueForm {:form-id form-id}))
   :modal-props (fn [props] {:title (or (:title props) "Add to Human Feedback Queue") 
                             :submit-text "Add to Queue"})}
  :on-submit
  {:event
   (fn [_db form-state]
     ;; invoke-id is in "taskId-agentInvokeId" format
     (let [{:keys [module-id queue-name comment agent-name invoke-id 
                   node-task-id node-invoke-id]} form-state]
       [:human-feedback/add-to-queue
        {:module-id module-id
         :queue-name queue-name
         :agent-name agent-name
         :invoke-id invoke-id
         :node-task-id node-task-id
         :node-invoke-id node-invoke-id
         :comment comment}]))
   :on-success-invalidate (fn [_db {:keys [module-id queue-name]} _reply]
                            {:query-key-pattern [:human-feedback-queue-items module-id queue-name]})}})

(defn show-add-to-queue-modal
  "Show the add-to-queue modal with the given props.
   
   Props should include:
   - :module-id - the module ID
   - :agent-name - the agent name
   - :task-id - agent task ID (UUID)
   - :invoke-id - agent invoke ID (UUID)
   - :source-type - :agent or :node
   
   For node sources, also include:
   - :node-task-id - node task ID (UUID)
   - :node-invoke-id - node invoke ID (UUID)"
  [props]
  (state/dispatch [:modal/show-form :add-to-feedback-queue props]))

