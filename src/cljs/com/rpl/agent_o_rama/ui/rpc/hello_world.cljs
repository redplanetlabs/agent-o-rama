 (ns com.rpl.agent-o-rama.ui.rpc.hello-world
   (:require
    [com.rpl.agent-o-rama.ui.common :as common]
    [re-frame.query :as rfq]
    [uix.core :refer [defui $]]
    [uix.re-frame :refer [use-subscribe]]))

 (def hello-world-rpc-id
   :com.rpl.agent-o-rama.impl.ui.rpc.hello-world/index!!)

 (defonce registered?
   (do
     (rfq/reg-query
      hello-world-rpc-id
      {:query-fn (fn [params]
                   {:rpc/id hello-world-rpc-id
                    :payload params})
       :stale-time-ms 0})
     true))

 (defui page [{:keys [module-id]}]
   (let [{:keys [status data error]}
         (or (use-subscribe [::rfq/query hello-world-rpc-id {:module-id module-id}])
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
                         (str hello-world-rpc-id))
                      ($ :div.text-xl.font-semibold.text-green-700
                         {:data-testid "rpc-hello-message"}
                         (:message data))
                      ($ :div.text-sm.text-gray-700
                         {:data-testid "rpc-hello-module"}
                         (:module-id data))
                      ($ :div.text-sm.text-gray-700
                         {:data-testid "rpc-hello-transport"}
                         (:transport data)))
          ($ :div.text-gray-500
             {:data-testid "rpc-hello-idle"}
             "Waiting for query...")))))
