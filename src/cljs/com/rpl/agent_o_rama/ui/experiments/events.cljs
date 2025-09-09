(ns com.rpl.agent-o-rama.ui.experiments.events
  (:require
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.forms :as forms]))

(state/reg-event :experiment/show-create-modal
                 (fn [db {:keys [module-id dataset-id]}]
    ;; Initialize the form state for our new multi-step wizard.
    ;; For now, we only define fields for the first step.
                   (let [form-id :create-experiment]
                     (state/dispatch
                      [:form/init form-id
                       {:fields {:name "", :description ""}
                        :validators {:name [forms/required]}
         ;; The final submit event will gather data from all steps.
                        :submit-event [:experiment/create {:module-id module-id, :dataset-id dataset-id}]}])
      ;; Show the modal.
                     (state/dispatch [:modal/show :create-experiment
                                      {:title "Run New Experiment"
                                       :form-id form-id
                                       :submit-text "Next: Select Data" ; The button proceeds to the next step
                                       :component ($ 'com.rpl.agent-o-rama.ui.experiments.forms/CreateExperimentForm
                                                     {:form-id form-id})}]))
                   nil)) ;; This handler dispatches other events, so it doesn't need to return a path.

(state/reg-event :experiment/create
                 (fn [db {:keys [module-id dataset-id]}]
    ;; This will eventually handle the full experiment creation logic
    ;; For now, just log the form data and close the modal
                   (let [form-data (get-in db [:forms :create-experiment :fields])]
                     (println "Creating experiment with data:" form-data)
                     (state/dispatch [:modal/hide])
      ;; TODO: Implement actual experiment creation via Sente
                     )
                   nil))