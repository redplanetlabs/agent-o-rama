(ns com.rpl.agent-o-rama.ui.common
  (:require ["@tanstack/react-query" :as rq]
            ["axios" :as axios]))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn use-query
  "Wrap useQuery

  `query-key` query key array
  `query-fn` query function to fetch data"
  [{:keys [query-key query-url]}]
  (let [result (rq/useQuery #js {:queryFn (fn [] (axios/get query-url))
                                 :queryKey (into-array query-key)})]
    {:data (:data (js->clj result.data {:keywordize-keys true}))
     :error? result.isError
     :success? result.isSuccess
     :loading? result.isLoading}))
