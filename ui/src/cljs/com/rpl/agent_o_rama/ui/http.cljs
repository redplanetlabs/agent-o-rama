(ns com.rpl.agent-o-rama.ui.http
  (:require
   [re-frame.core :as f]
   [re-statecharts.core :as rs]
   [statecharts.core :as sc]
   [clojure.string :as str]))

(defn path->keyword-id [path]
  (->> path
       (map #(if (keyword? %) (name %) (str %)))
       (str/join "--")
       (str "fsm-")
       keyword))

(defn update-retries [state & _]
  (update state :retries inc))

(defn reset-retries [state & _]
  (assoc state :retries 0))

(defn more-retries? [max-retries {:keys [retries]} _]
  (< retries max-retries))

(defn store-error [state event]
  (assoc state :error (:data event)))

(defn fsm-body [{:keys [state-path max-retries retry-delay on-loading on-error on-failure failure-state success-state] :as config}]
  (let [retry-delay (if (fn? retry-delay)
                      (comp retry-delay :retries)
                      retry-delay)]
    {:initial ::loading
     :states  {::loading {:entry (fn [state event]
                                   (f/dispatch [::load config])
                                   (when on-loading
                                     (f/dispatch (vec (concat on-loading [state event])))))
                          :on    {::error   ::error
                                  ::success (or success-state ::loaded)}}
               ::error   {:initial ::retrying
                          :entry   (fn [state event]
                                     (let [assign (sc/assign store-error)]
                                       (when on-error
                                         (f/dispatch (vec (concat on-error [state event]))))
                                       (assign state event)))
                          :states  {::retrying {:always  [{:guard  (fn [] (< max-retries 1))
                                                           :target (or failure-state ::halted)}]
                                                :initial ::waiting
                                                :entry   (sc/assign reset-retries)
                                                :states  {::loading {:entry [(sc/assign update-retries)
                                                                             #(f/dispatch [::load config])]
                                                                     :on    {::error   [{:guard  (partial more-retries? max-retries)
                                                                                         :target ::waiting}
                                                                                        (or failure-state (vec (concat state-path [::error ::halted])))]
                                                                             ::success (or success-state (vec (concat state-path [::loaded])))}}
                                                          ::waiting {:after [{:delay  retry-delay
                                                                              :target ::loading}]}}}
                                    ::halted   {:entry (fn [state event]
                                                         (when on-failure
                                                           (f/dispatch (vec (concat on-failure [state event])))))}}}
               ::loaded  {}}}))

(defn fsm [{:keys [id] :as config}]
  (merge (fsm-body config)
         {:id id}))

(f/reg-event-fx ::on-failure
                (fn [_ [_ {:keys [id]} error]]
                  {:dispatch [::rs/transition id ::error error]}))

(f/reg-event-fx ::on-success
  (fn [{db :db} [_ {:keys [id on-success path]} data]]
    (merge
     (when path
       {:db (assoc-in db path data)})
     {:dispatch-n [[::rs/transition id ::success]
                   (when on-success (conj on-success data))]})))

(f/reg-event-fx ::load
  (fn [_ [_ {:keys [http-xhrio] :as config}]]
    {:http-xhrio (merge http-xhrio
                        {:on-failure [::on-failure config]
                         :on-success [::on-success config]})}))

(f/reg-event-fx ::start
                (fn [_ [_ user-config]]
                  (let [path (:path user-config)
                        keyword-id (path->keyword-id path)
                        fsm-config (assoc user-config :id keyword-id)]
                    {::rs/start (fsm (merge {:state-path [:>]
                                             :max-retries 0
                                             :retry-delay 2000}
                                            fsm-config))})))

(defn ->seq [x]
  (if (coll? x)
    x
    [x]))

(f/reg-sub ::state
  (fn [[_ path]]
    (f/subscribe [::rs/state (path->keyword-id path)]))
  ->seq)
