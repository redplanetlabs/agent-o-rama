 (ns com.rpl.agent-o-rama.ui.re-frame
   (:require
    [com.rpl.agent-o-rama.ui.rpc :as rpc]
    [re-frame.core :as rf]
    [re-frame.query :as rfq]))

 (def ^:private rpc-fx-key ::rpc)

 (rf/reg-fx
  rpc-fx-key
  (fn [{:keys [request on-success on-failure]}]
    (-> (rpc/call (:rpc/id request)
                  (:payload request))
        (.then (fn [data]
                 (rf/dispatch (conj on-success data))))
        (.catch (fn [error]
                  (rf/dispatch (conj on-failure error)))))))

(rfq/set-default-effect-fn!
 (fn [request on-success on-failure]
   {rpc-fx-key {:request request
                :on-success on-success
                :on-failure on-failure}}))
