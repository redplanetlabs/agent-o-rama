(ns com.rpl.agent-o-rama.ui.agents.index
  (:require
   [uix.core :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   [re-frame.query :as rfq]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.impl.ui.rpc.agents :as rpc-agents]))

(defui index []
  (let [{:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-agents/get-all!! {}])
        loading? (#{:loading :idle} query-status)]

    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading agents..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading agents: " error))
      (empty? data) ($ :div.flex.justify-center.items-center.py-8
                       ($ :div.text-gray-500 "No agents found"))
      :else ($ :div.p-4
               ($ :div {:className "inline-block bg-white shadow sm:rounded-md"}
                  ($ :table {:className "divide-y divide-gray-200"}
                     ($ :thead {:className (:thead common/table-classes)}
                        ($ :tr
                           ($ :th {:className (:th common/table-classes)} "Module")
                           ($ :th {:className (:th common/table-classes)} "Agent")))
                     ($ :tbody
                        (let [sorted-agents (sort-by
                                             (fn [agent]
                                               (let [module-name (:module-id agent)
                                                     decoded-module (common/url-decode module-name)
                                                     agent-name (:agent-name agent)
                                                     decoded-agent (common/url-decode agent-name)]
                                                  ;; Sort by: 1) module name, 2) underscore-prefixed agents last, 3) agent name
                                                 [decoded-module (str/starts-with? decoded-agent "_") decoded-agent]))
                                             data)]
                          (into []
                                (for [agent sorted-agents
                                      :let [module (common/url-decode (:module-id agent))
                                            agent-name (common/url-decode (:agent-name agent))
                                            href (str "/agents/" (common/url-encode (:module-id agent)) "/agent/" (common/url-encode (:agent-name agent)))]]
                                  ($ :tr {:key href :className "hover:bg-gray-50 cursor-pointer"
                                          :onClick (fn [_]
                                                     (rfe/push-state :agent/detail
                                                                     {:module-id (:module-id agent)
                                                                      :agent-name (:agent-name agent)}))}
                                     ($ :td {:className (:td common/table-classes)} module)
                                     ($ :td {:className (:td common/table-classes)} agent-name))))))))))))
