(ns com.rpl.agent-o-rama.ui.agents-new
  (:require
   [uix.core :as uix :refer [defui $]]
   ["wouter" :as wouter]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.common :as common]))

(defui index []
  (let [agents (state/use-sub [:agents])
        loading? (state/use-sub [:loading])
        agents-loading? (contains? loading? :agents)]
    
    ;; Load agents when component mounts
    (uix/use-effect
     (fn []
       (when (empty? agents)
         (state/set-loading! :agents true)
         (sente/chsk-send! [:api/agents] 8000
                           (fn [reply]
                             (if (sente/cb-success? reply)
                               (state/dispatch [:agents/load-success reply])
                               (js/console.error "Failed to load agents:" reply))
                             (state/set-loading! :agents false)))))
     [])
    
    (cond
      ;; Still loading initial data
      agents-loading? ($ :div.flex.justify-center.items-center.py-8
                        ($ :div.text-gray-500 "Loading agents..."))
      ;; No agents returned from the API
      (empty? agents) ($ :div.flex.justify-center.items-center.py-8
                        ($ :div.text-gray-500 "No agents found"))
      :else ($ :div.p-4
              (for [agent agents
                    :let [url (str "/agents/" (:module-id agent) "/" (:agent-name agent))]]
                ($ :div.p-4.transition-colors.duration-150.hover:bg-gray-200.bg-gray-100.m-4  {:key url}
                  ($ wouter/Link {:href url}
                     ($ :div.flex.items-center.group 
                      ($ :div.flex-1
                          ($ :div.text-lg.font-medium.text-indigo-600.group-hover:text-indigo-800
                            ($ :div (common/url-decode (:module-id agent)) ":" (common/url-decode (:agent-name agent))))
                          ($ :div.mt-1.text-sm.text-gray-500.group-hover:text-gray-700
                            "View agent details")))))))))))