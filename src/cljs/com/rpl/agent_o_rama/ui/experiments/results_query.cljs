 (ns com.rpl.agent-o-rama.ui.experiments.results-query
   (:require
    [re-frame.query :as rfq]))

 (def experiment-results-query-key ::experiment-results)

 (defn- normalize-error
   [error _params]
   (cond
     (string? error) error
     (map? error) (or (:error error)
                      (:message error)
                      (pr-str error))
     :else (str error)))

 (defonce registered?
   (do
     (rfq/reg-query
      experiment-results-query-key
      {:query-fn (fn [params]
                   {:rpc/namespace :experiments
                    :rpc/method :get-results
                    :payload params})
       :stale-time-ms 0
       :polling-interval-ms 2000
       :tags (fn [{:keys [module-id dataset-id experiment-id]}]
               [[:experiment-results module-id dataset-id experiment-id]])
       :transform-error normalize-error})
     true))
