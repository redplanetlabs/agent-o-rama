(ns com.rpl.agent-o-rama.ui.experiments.events
  (:require
   [re-frame.core :as rf]))

(rf/reg-event-db
 :form/set-experiment-target-type
 (fn [db [_ form-id target-index new-type]]
   (cond
     (and (= target-index 0) (#{:regular :comparative} new-type))
     (let [targets-path [:forms form-id :spec :targets]
           current-targets (get-in db targets-path [])
           new-targets (if (= new-type :regular)
                         (if (empty? current-targets)
                           [{:target-spec {:type :agent :agent-name nil}
                             :input->args [{:id (random-uuid) :value "$"}]}]
                           [(first current-targets)])
                         (if (< (count current-targets) 2)
                           (vec (take 2 (concat current-targets
                                               (repeat {:target-spec {:type :agent :agent-name nil}
                                                        :input->args [{:id (random-uuid) :value "$"}]}))))
                           current-targets))]
       (-> db
           (assoc-in [:forms form-id :spec :type] new-type)
           (assoc-in targets-path new-targets)))

     (#{:agent :node} new-type)
     (let [base-path [:forms form-id :spec :targets target-index :target-spec]
           cur (get-in db base-path {})]
       (assoc-in db base-path
                 (if (= new-type :agent)
                   (-> cur (assoc :type :agent) (dissoc :node))
                   (-> cur (assoc :type :node) (assoc :node (or (:node cur) ""))))))

     :else db)))

