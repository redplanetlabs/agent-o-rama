(ns com.rpl.agent-o-rama.ui.rpc
  (:require
   [cognitect.transit :as transit]))

(def transit-content-type "application/transit+json")

(def uuid-write-handler
  (transit/write-handler
   (constantly "u")
   (fn [value] (str value))))

(def uuid-read-handler
  (transit/read-handler uuid))

(def writer
  (transit/writer :json {:handlers {cljs.core/UUID uuid-write-handler}}))

(def reader
  (transit/reader :json {:handlers {"u" uuid-read-handler}}))

(defn route-for
  [namespace method]
  (str "/api/rpc/"
       (js/encodeURIComponent (name namespace))
       "/"
       (js/encodeURIComponent (name method))))

(defn call
  "Call a transit-over-HTTP RPC endpoint.

   Example:
   (call :experiments :get-results {:module-id module-id
                                    :dataset-id dataset-id
                                    :experiment-id experiment-id})"
  ([namespace method]
   (call namespace method {}))
  ([namespace method payload]
   (-> (js/fetch (route-for namespace method)
                 #js {:method "POST"
                      :credentials "same-origin"
                      :headers #js {"Content-Type" transit-content-type
                                    "Accept" transit-content-type}
                      :body (transit/write writer (or payload {}))})
       (.then (fn [response]
                (-> (.text response)
                    (.then (fn [body]
                             (let [reply (transit/read reader body)]
                               (if (.-ok response)
                                 reply
                                 (js/Promise.reject reply))))))))
       (.then (fn [reply]
                (if (:success reply)
                  (:data reply)
                  (js/Promise.reject reply)))))))
