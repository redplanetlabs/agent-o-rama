(ns com.rpl.agent-o-rama.ui.experiments.events
  (:require
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.specter :as s]))

(state/reg-event
 :form/set-experiment-target-type
 (fn [db form-id target-index new-type]
   (cond
     ;; Handle experiment type changes (when target-index is 0 and new-type is :regular or :comparative)
     (and (= target-index 0) (#{:regular :comparative} new-type))
     (s/multi-path
      ;; Update the experiment type
      [[:forms form-id :spec :type] (s/terminal-val new-type)]
      ;; Ensure we have the right number of targets
      [[:forms form-id :spec :targets] (s/terminal (fn [targets]
                                                     (if (= new-type :regular)
                                                       ;; For regular, ensure we have exactly 1 target
                                                       (if (empty? targets)
                                                         [{:target-spec {:type :agent :agent-name nil} :input->args [{:id (random-uuid) :value "$"}]}]
                                                         [(first targets)])
                                                       ;; For comparative, ensure we have at least 2 targets
                                                       (if (< (count targets) 2)
                                                         (vec (take 2 (concat targets (repeat {:target-spec {:type :agent :agent-name nil} :input->args [{:id (random-uuid) :value "$"}]}))))
                                                         targets))))])

     ;; Handle target type changes (when new-type is :agent or :node)
     (#{:agent :node} new-type)
     (let [base-path [:forms form-id :spec :targets target-index :target-spec]]
       (s/multi-path
        ;; 1. Set the new type
        [(into base-path [:type]) (s/terminal-val new-type)]
        ;; 2. Atomically clean up the spec based on the new type
        [base-path (s/terminal (fn [target-spec]
                                 (if (= new-type :agent)
                                   ;; If switching to agent, remove the :node key
                                   (dissoc target-spec :node)
                                   ;; If switching to node, ensure :node key exists
                                   (assoc target-spec :node ""))))])))))