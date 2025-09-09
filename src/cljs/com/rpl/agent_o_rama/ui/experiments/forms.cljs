(ns com.rpl.agent-o-rama.ui.experiments.forms
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]))

(defui CreateExperimentForm [{:keys [form-id]}]
  (let [;; This will eventually manage the full wizard state
        [step set-step!] (uix/use-state :basic-info)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]

    ($ :div
       ;; Wizard step indicator could go here in the future
       ($ :div.p-2 "Step 1 of 5: Basic Information")

       (case step
         :basic-info
         ($ forms/form
            ($ forms/form-field
               {:label "Experiment Name"
                :value (:value name-field)
                :on-change (:on-change name-field)
                :error (:error name-field)
                :required? true
                :placeholder "e.g., Test new prompt for summary agent"})
            ($ forms/form-field
               {:label "Description (Optional)"
                :type :textarea
                :value (:value description-field)
                :on-change (:on-change description-field)
                :error (:error description-field)
                :rows 3}))

         ;; Placeholder for future steps
         :select-data ($ :div.p-4 "Step 2: Data Selection UI would be here.")
         :configure-targets ($ :div.p-4 "Step 3: Target Configuration UI would be here.")
         :select-evaluators ($ :div.p-4 "Step 4: Evaluator Selection UI would be here.")
         :settings ($ :div.p-4 "Step 5: Execution Settings UI would be here.")))))