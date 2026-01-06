(ns com.rpl.agent-o-rama.ui.human-feedback.add-to-queue
  (:require
   [uix.core :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.searchable-selector :refer [SearchableSelector]]))

(defui QueueCombobox [{:keys [module-id value on-change error required?]}]
  ($ SearchableSelector
     {:module-id module-id
      :value value
      :on-change on-change
      :sente-event-fn (fn [module-id search-string]
                        [:human-feedback/get-queues
                         {:module-id module-id
                          :filters {:search-string (or search-string "")}}])
      :items-key :results
      :item-id-fn :name
      :item-label-fn :name
      :item-sublabel-fn :description
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
        {:keys [module-id source-type agent-name]} props]

    ($ :div {:className "max-w-4xl mx-auto p-6"}
       ($ forms/form
          ($ :div {:className "space-y-6"}
             ;; Queue Selection
             ($ QueueCombobox {:module-id module-id
                              :value (:value queue-name-field)
                              :on-change (:on-change queue-name-field)
                              :error (:error queue-name-field)
                              :required? true})

             ;; Source info display
             ($ :div {:className "p-4 bg-gray-50 border rounded-md"}
                ($ :h4 {:className "font-semibold text-gray-700 mb-2"} "Adding to Queue")
                ($ :div {:className "space-y-1 text-sm"}
                   ($ :div {:className "flex gap-2"}
                      ($ :span {:className "text-gray-500"} "Type:")
                      ($ :span {:className "font-medium"} (name source-type)))
                   ($ :div {:className "flex gap-2"}
                      ($ :span {:className "text-gray-500"} "Agent:")
                      ($ :span {:className "font-medium font-mono"} agent-name))))

             ;; Optional comment
             ($ forms/form-field
                {:label "Comment (optional)"
                 :type :textarea
                 :rows 3
                 :value (:value comment-field)
                 :on-change (:on-change comment-field)
                 :error (:error comment-field)
                 :help-text "Add a note for the reviewer"}))))))

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
         :node-task-id (when node-task-id (str node-task-id))
         :node-invoke-id (when node-invoke-id (str node-invoke-id))
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

