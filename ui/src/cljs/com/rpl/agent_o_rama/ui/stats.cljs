(ns com.rpl.agent-o-rama.ui.stats
  (:require
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]


   
   [com.rpl.agent-o-rama.ui.common :as common]))


(defui agent-graph []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-id "graph"]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/graph")})
        [selected-node set-selected-node] (uix/use-state nil)]
    (if loading?
      "...loading"
      ($ agent-graph/graph {:initial-data data
                            :height "500px"
                            :selected-node selected-node
                            :set-selected-node set-selected-node}))))

(defui stats []
  ($ :div.p-4
     ($ agent-graph)))
