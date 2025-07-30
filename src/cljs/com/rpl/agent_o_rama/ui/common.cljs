(ns com.rpl.agent-o-rama.ui.common
  (:require ["@tanstack/react-query" :as rq]
            ["axios" :as axios]
            [cognitect.transit :as t]
            [clojure.string :as str]))

(defn url-decode [s] (str/replace s #"::" "/"))

(def reader (t/reader :json))
(def writer (t/writer :json))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn fetch [url]
  (.then (js/fetch url #js {:headers #js {:Accept "application/transit+json"}})
         (fn [response] (.then (.text response) (fn [text] (t/read reader text))))))

(defn post [url data]
  (.then (js/fetch url #js {:method "POST"
                            :headers #js {:Accept "application/transit+json"
                                          :Content-Type "application/transit+json"}
                            :body (t/write writer data)})
         (fn [response] (.then (.text response) (fn [text] (t/read reader text))))))

(defn use-query
  "Wrap useQuery

  `query-key` query key array
  `query-fn` query function to fetch data"
  [{:keys [query-key query-url]}]
  (let [result (rq/useQuery #js {:queryFn (fn [] (fetch query-url))
                                 :queryKey (into-array query-key)})]
    {:data (js->clj result.data {:keywordize-keys true})
     :error? result.isError
     :success? result.isSuccess
     :loading? result.isLoading}))

(defn use-mutation
  "Wrap useMutation

  `mutation-fn` function that takes variables and returns a promise
  `on-success` optional callback for successful mutations
  `on-error` optional callback for failed mutations"
  [{:keys [mutation-fn on-success on-error]}]
  (let [result (rq/useMutation #js {:mutationFn mutation-fn
                                    :onSuccess on-success
                                    :onError on-error})]
    {:mutate result.mutate
     :data (js->clj result.data {:keywordize-keys true})
     :error (js->clj result.error {:keywordize-keys true})
     :loading? result.isPending
     :success? result.isSuccess
     :error? result.isError}))
