(ns com.rpl.agent-o-rama.ui.common
  (:require ["@tanstack/react-query" :as rq]
            ["axios" :as axios]
            [cognitect.transit :as t]))

(def reader (t/reader :json))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn fetch [url]
  (.then (js/fetch url #js {:headers #js {:Accept "application/transit+json"}})
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
