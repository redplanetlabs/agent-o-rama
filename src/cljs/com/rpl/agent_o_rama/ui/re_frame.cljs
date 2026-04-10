 (ns com.rpl.agent-o-rama.ui.re-frame
   (:require
    [com.rpl.agent-o-rama.ui.rpc :as rpc]
    [re-frame.core :as rf]
    [re-frame.query :as rfq]))

 (def ^:private rpc-fx-key ::rpc)

 (defn- normalize-error
   [error]
   (cond
     (map? error) error
     (instance? js/Error error) {:error (or (.-message error) (str error))}
     :else {:error (str error)}))

 (rf/reg-fx
  rpc-fx-key
  (fn [{:keys [request on-success on-failure]}]
    (-> (rpc/call (:rpc/namespace request)
                  (:rpc/method request)
                  (:payload request))
        (.then (fn [data]
                 (rf/dispatch (conj on-success data))))
        (.catch (fn [error]
                  (rf/dispatch (conj on-failure (normalize-error error))))))))

 (defonce initialized? (atom false))

 (defn init!
   []
   (when-not @initialized?
     (reset! initialized? true)
     (rfq/set-default-effect-fn!
      (fn [request on-success on-failure]
        {rpc-fx-key {:request request
                     :on-success on-success
                     :on-failure on-failure}}))))
