(ns com.rpl.agent-o-rama.ui.common
  (:require ["@tanstack/react-query" :as rq]
            ["axios" :as axios]))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn use-query
  "Wrap useQuery

  `query-key` query key array
  `query-fn` query function to fetch data"
  [{:keys [query-key query-fn]}]
  (let [result (rq/useQuery #js {:queryFn query-fn
                                 :queryKey (into-array query-key)})]
    {:data (js->clj result.data {:keywordize-keys true})
     :error? result.isError
     :success? result.isSuccess
     :loading? result.isLoading}))
