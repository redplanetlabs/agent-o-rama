 (ns com.rpl.agent-o-rama.impl.ui.rpc.hello-world
   (:require
    [com.rpl.agent-o-rama.ui.common :as common]
    [re-frame.query :as rfq]
    [uix.core :refer [defui $]]
    [uix.re-frame :refer [use-subscribe]]))

 (rfq/reg-query
  ::index!!
  {:query-fn (fn [params]
               {:rpc/id ::index!!
                :payload params})
   :stale-time-ms 0})

 (defui page [{:keys [module-id]}]
   (let [{:keys [status data error]}
         (or (use-subscribe [::rfq/query ::index!! {:module-id module-id}])
             {:status :idle
              :data nil
              :error nil})]
     ($ :div.p-6.space-y-4
        ($ :h2.text-2xl.font-bold "RPC Hello World")
        (case status
          :loading ($ :div {:data-testid "rpc-hello-loading"}
                      ($ common/spinner {:size :large}))
          :error ($ :div.text-red-600
                    {:data-testid "rpc-hello-error"}
                    (str "RPC error: " error))
          :success ($ :div.space-y-3
                      ($ :div.text-sm.text-gray-600
                         {:data-testid "rpc-hello-rpc-id"}
                         (str ::index!!))
                      ($ :div.text-xl.font-semibold.text-green-700
                         {:data-testid "rpc-hello-message"}
                         (:message data))
                      ($ :div.text-sm.text-gray-700
                         {:data-testid "rpc-hello-module"}
                         (:module-id data)))
          ($ :div.text-gray-500
             {:data-testid "rpc-hello-idle"}
             "Waiting for query...")))))
