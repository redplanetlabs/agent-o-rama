(ns com.rpl.agent-o-rama.ui.query
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]))

;; ---- Core query state management ----

;; The query system state lives under :queries in the app-db
;; Each query is identified by a key vector, e.g., [:agent "module1" "agent1"]
;; {[:agent "module1" "agent1"] {:state :success, :data {...}, :error nil, :fetched-at timestamp}}

;; State transition flow:
;; :idle -> :loading -> :success | :error
;;           ^                     |
;;           +---------------------+

;; ----- Event Handlers -----

(re-frame/reg-event-fx
 :query/fetch
 (fn [{:keys [db]} [_ {:keys [key uri opts]}]]
   (let [method (or (:method opts) :get)
         retries (or (:retries opts) 3)
         response-format (or (:response-format opts) (ajax/transit-response-format))
         timeout (or (:timeout opts) 30000)]
     {:db (assoc-in db [:queries key] 
                    {:state :loading
                     :fetched-at (js/Date.)
                     :retry-count 0
                     :uri uri
                     :opts opts})
      :http-xhrio {:uri uri
                   :method method
                   :response-format response-format
                   :timeout timeout
                   :on-success [:query/success key]
                   :on-failure [:query/failure key opts]}})))

(re-frame/reg-event-db
 :query/success
 (fn [db [_ key data]]
   (assoc-in db [:queries key] 
             {:state :success
              :data data
              :error nil
              :fetched-at (js/Date.)})))

(re-frame/reg-event-fx
 :query/failure
 (fn [{:keys [db]} [_ key opts error]]
   (let [current-retry (or (get-in db [:queries key :retry-count]) 0)
         next-retry (inc current-retry)
         retries (or (:retries opts) 3)]
     (if (< current-retry retries)
       ;; Retry with exponential backoff
       (let [delay-ms (* 1000 (js/Math.pow 2 current-retry))]
         {:db (assoc-in db [:queries key :retry-count] next-retry)
          :dispatch-later [{:ms delay-ms
                            :dispatch [:query/retry key]}]})
       ;; Max retries reached, record the error
       {:db (assoc-in
             db
             [:queries key] 
             {:state :error
              :error error
              :uri (get-in db [:queries key :uri])
              :opts (get-in db [:queries key :opts])
              :fetched-at (js/Date.)})}))))

(re-frame/reg-event-fx
 :query/retry
 (fn [{:keys [db]} [_ key]]
   (let [query-data (get-in db [:queries key])
         uri (:uri query-data)
         opts (:opts query-data)]
     {:dispatch [:query/fetch {:key key :uri uri :opts opts}]})))

(re-frame/reg-event-fx
 :query/refetch
 (fn [{:keys [db]} [_ key]]
   (let [query-data (get-in db [:queries key])
         uri (:uri query-data)
         opts (:opts query-data)]
     {:dispatch [:query/fetch {:key key :uri uri :opts opts}]})))

(re-frame/reg-event-fx
 :query/invalidate
 (fn [{:keys [db]} [_ prefix]]
   (let [queries (:queries db)
         matches (filter (fn [[k _]]
                           (= (take (count prefix) k) prefix))
                         queries)
         dispatches (map (fn [[k _]] [:query/refetch k]) matches)]
     {:dispatch-n dispatches})))

;; ----- Subscriptions -----

(re-frame/reg-sub
 :query/state
 (fn [db [_ key]]
   (get-in db [:queries key :state])))

(re-frame/reg-sub
 :query/data
 (fn [db [_ key]]
   (get-in db [:queries key :data])))

(re-frame/reg-sub
 :query/error
 (fn [db [_ key]]
   (get-in db [:queries key :error])))

(re-frame/reg-sub
 :query/loading?
 (fn [db [_ key]]
   (= (get-in db [:queries key :state]) :loading)))

(re-frame/reg-sub
 :query/success?
 (fn [db [_ key]]
   (= (get-in db [:queries key :state]) :success)))

(re-frame/reg-sub
 :query/error?
 (fn [db [_ key]]
   (= (get-in db [:queries key :state]) :error)))

;; ----- View Helpers -----

(defn query-view
  "key - vector that uniquely identifies the query
   content-fn - a function that receives data and returns hiccup
  
   loading-view - (optional) hiccup for loading state
   error-view - (optional) function that receives error and refetch fn, returns hiccup"
  [key content-fn & {:keys [loading-view error-view]
                     :or {loading-view [:div "Loading..."]
                          error-view (fn [error refetch]
                                      [:div 
                                       [:h3 "An error occurred"]
                                       [:div "Could not load data"]
                                       [:div
                                        [:button {:on-click refetch}
                                         "Click to try again"]]])}}]
  (let [state @(re-frame/subscribe [:query/state key])
        data @(re-frame/subscribe [:query/data key])
        error @(re-frame/subscribe [:query/error key])
        refetch #(re-frame/dispatch [:query/refetch key])]
    (case state
      :loading loading-view
      :error (error-view error refetch)
      :success (content-fn data)
      loading-view))) ; Default to loading view if state is nil or unknown
