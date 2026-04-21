(ns com.rpl.agent-o-rama.ui.rpc
  (:require
   [clojure.string :as str]
   [cognitect.transit :as transit]))

(def ^:private sse-client-id-storage-key "aor_sse_client_id")

(defn sse-client-id!
  "Stable UUID string per browser tab (sessionStorage). Used so concurrent tabs each keep their own server SSE subscription."
  []
  (or (js/sessionStorage.getItem sse-client-id-storage-key)
      (let [id (str (random-uuid))]
        (js/sessionStorage.setItem sse-client-id-storage-key id)
        id)))

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
  [rpc-id]
  (let [rpc-ns (namespace rpc-id)
        rpc-fn (name rpc-id)]
    (str "/api/rpc/"
         (js/encodeURIComponent rpc-ns)
         "/"
         (js/encodeURIComponent rpc-fn))))

(defn call
  "Call a transit-over-HTTP RPC endpoint.

   Example:
   (call :com.rpl.agent-o-rama.impl.ui.rpc.experiments/results!!
         {:module-id module-id
          :dataset-id dataset-id
          :experiment-id experiment-id})"
  ([rpc-id]
   (call rpc-id {}))
  ([rpc-id payload]
   (-> (js/fetch (route-for rpc-id)
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
                  (js/Promise.reject reply))))
       (.catch (fn [error]
                 (js/Promise.reject error))))))

(defn- sse-drain-complete-events!
  "Parses `buf` for SSE event boundaries (blank line); invokes `on-data-str` for each data line payload."
  [buf-atom on-data-str]
  (loop []
    (when-let [^js s @buf-atom]
      (when (pos? (.-length s))
        (when-let [idx (str/index-of s "\n\n")]
          (let [event-text (subs s 0 idx)
                remainder (subs s (+ idx 2))]
            (reset! buf-atom remainder)
            (doseq [line (str/split-lines event-text)
                    :let [line (str/triml line)]
                    :when (str/starts-with? line "data:")
                    :let [ps (-> line (subs (count "data:")) str/trim)]
                    :when (seq ps)]
              (on-data-str ps))
            (recur)))))))

(defn call-sse
  "POST + `Accept: text/event-stream`, then read the body with the fetch stream API.
  `on-event` receives each decoded Transit value (Clojure data from the `data:` line).
  Returns a no-arg function to abort the request (triggers server on-close and stream close).

  `opts` (optional map):
  - `:on-error` — (fn [v]) if the HTTP response is not ok; `v` is usually a parsed `transit/read` error map."
  ([rpc-id payload on-event]
   (call-sse rpc-id payload on-event nil))
  ([rpc-id payload on-event opts]
   (let [controller (js/AbortController.)
         signal (.-signal controller)
         on-err (or (:on-error opts)
                    (fn [_err] nil))]
     (-> (js/fetch (route-for rpc-id)
                   #js {:method "POST"
                        :credentials "same-origin"
                        :headers #js {"Content-Type" transit-content-type
                                      "Accept" "text/event-stream"}
                        :body (transit/write writer
                                            (assoc (or payload {})
                                                   :sse-client-id (sse-client-id!)))
                        :signal signal})
         (.then (fn [^js resp]
                  (if-not (.-ok resp)
                    (-> (.text resp)
                        (.then (fn [body]
                                 (try
                                   (on-err (transit/read reader body))
                                   (catch :default _
                                     (on-err {:http-status (.-status resp)
                                              :body body}))))))
                    (let [r (.. resp -body (getReader))
                          decoder (js/TextDecoder. "utf-8")
                          buf (atom "")]
                      (letfn [(step [result]
                                (let [done? (.-done result)
                                      v (.-value result)]
                                  (if done?
                                    nil
                                    (let [chunk (.decode decoder v #js {:stream true})]
                                      (swap! buf str chunk)
                                      (sse-drain-complete-events! buf
                                                                  (fn [ps]
                                                                    (try
                                                                      (on-event (transit/read reader ps))
                                                                      (catch :default e
                                                                        (println "SSE transit decode error" e)))))
                                      (-> (.read r)
                                          (.then step))))))]
                        (-> (.read r)
                            (.then step))))))))
     (fn abort []
       (.abort controller)))))

