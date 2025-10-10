(ns com.rpl.agent-o-rama.ui.action-log
  (:require
   [uix.core :as uix :refer [defui $]]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]
   ["@heroicons/react/24/outline" :refer [ArrowTopRightOnSquareIcon]]))

(defn format-action-field
  "Format an action field for display."
  [value]
  (cond
    (nil? value) "nil"
    (string? value) value
    (boolean? value) (str value)
    (number? value) (str value)
    (map? value) (pr-str value)
    (vector? value) (pr-str value)
    :else (str value)))

(defui info-cell
  "Display info-map with truncation and modal dialog."
  [{:keys [info-map module-id agent-name]}]
  (let [formatted-for-modal (common/pp info-map)
        display-text (pr-str info-map)
        has-eval-invoke? (contains? info-map "eval-invoke")
        eval-invoke (get info-map "eval-invoke")

        show-modal (fn []
                     (state/dispatch
                      [:modal/show
                       :info-map
                       {:title "Action Info"
                        :component
                        ($ :div.p-4
                           ($ :pre.bg-gray-50.p-4.rounded.text-xs.overflow-auto.max-h-96
                              {:style {:font-family "monospace"}}
                              formatted-for-modal))}]))]

    ($ :div.relative.cursor-pointer.hover:bg-gray-50.p-2.rounded
       {:onClick show-modal}
       ($ :div
          {:style {:display "-webkit-box"
                   :WebkitLineClamp "2"
                   :WebkitBoxOrient "vertical"
                   :overflow "hidden"
                   :fontSize "0.875rem"
                   :lineHeight "1.25rem"
                   :color "#4b5563"}}
          display-text)
       (when has-eval-invoke?
         (let [eval-url (common/agent-invoke->url module-id "AgentEvaluator" eval-invoke)]
           ($ :a.absolute.top-1.right-1.text-blue-600.hover:text-blue-800.cursor-pointer
              {:href eval-url
               :target "_blank"
               :rel "noopener noreferrer"
               :onClick #(.stopPropagation %)}
              ($ ArrowTopRightOnSquareIcon {:className "h-4 w-4"})))))))

(defui action-row
  "Display a single action log entry as a table row."
  [{:keys [action-entry module-id agent-name]}]
  (let [action (:action action-entry)
        success? (:success? action)
        info-map (:info-map action)
        agent-invoke (:agent-invoke action)
        start-time (:start-time-millis action)
        finish-time (:finish-time-millis action)
        execution-time (when (and start-time finish-time)
                         (- finish-time start-time))
        invoke-url (when agent-invoke
                     (common/agent-invoke->url module-id agent-name agent-invoke))]
    ($ :tr.hover:bg-gray-50
       ($ :td.px-4.py-2.text-xs.text-gray-600
          (common/format-timestamp start-time))
       ($ :td.px-4.py-2.text-xs.text-gray-600
          (when execution-time
            (common/format-duration-ms execution-time)))
       ($ :td.px-4.py-2.text-sm.text-center
          ($ :span
             {:className (if success?
                           "inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800"
                           "inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800")}
             (if success? "Success" "Failed")))
       ($ :td.px-4.py-2.text-sm
          (when invoke-url
            ($ :a.inline-flex.items-center.gap-1.text-blue-600.hover:text-blue-800
               {:href invoke-url
                :target "_blank"
                :rel "noopener noreferrer"}
               ($ ArrowTopRightOnSquareIcon {:className "h-4 w-4"}))))
       ($ :td.px-4.py-2.text-sm.max-w-md
          ($ info-cell {:info-map info-map
                        :module-id module-id
                        :agent-name agent-name})))))

(defui action-log-page
  "Display action log for a specific rule with pagination."
  []
  (let [{:keys [module-id agent-name rule-name]} (state/use-sub [:route :path-params])
        [actions set-actions!] (uix/use-state [])
        [pagination-params set-pagination-params!] (uix/use-state nil)
        [has-more? set-has-more!] (uix/use-state false)
        [loading? set-loading!] (uix/use-state true)
        [error set-error!] (uix/use-state nil)
        connected? (state/use-sub [:sente :connected?])

        fetch-actions (uix/use-callback
                       (fn [page-params append?]
                         (set-loading! true)
                         (sente/request!
                          [:analytics/fetch-action-log
                           {:module-id module-id
                            :agent-name agent-name
                            :rule-name rule-name
                            :page-size 50
                            :pagination-params page-params}]
                          5000
                          (fn [reply]
                            (set-loading! false)
                            (if (:success reply)
                              (let [data (:data reply)
                                    new-actions (:actions data)
                                    new-pagination (:pagination-params data)
                                    has-more? (and new-pagination
                                                   (not (empty? new-pagination))
                                                   (some (fn [[_ item-id]] (not (nil? item-id)))
                                                         new-pagination))]
                                (if append?
                                  (set-actions! (fn [prev] (concat prev new-actions)))
                                  (set-actions! new-actions))
                                (set-pagination-params! (when has-more? new-pagination))
                                (set-has-more! has-more?))
                              (set-error! (or (:error reply) "Failed to fetch action log"))))))
                       [module-id agent-name rule-name])

        load-more (fn []
                    (when (and has-more? (not loading?) pagination-params)
                      (fetch-actions pagination-params true)))]

    (uix/use-effect
     (fn []
       (when (and connected? module-id agent-name rule-name)
         (fetch-actions nil false))
       js/undefined)
     [fetch-actions connected? module-id agent-name rule-name])

    ($ :div.p-6

       (cond
         (not (and module-id agent-name rule-name))
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "Loading page...")
            ($ common/spinner {:size :medium}))

         (and loading? (empty? actions))
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "Loading action log...")
            ($ common/spinner {:size :medium}))

         error
         ($ :div.bg-red-50.border.border-red-200.rounded-md.p-4
            ($ :div.text-red-800.font-medium "Error:")
            ($ :div.text-red-600 error))

         (empty? actions)
         ($ :div.bg-gray-50.border.border-gray-200.rounded-md.p-4.text-center
            ($ :div.text-gray-500.italic "No actions logged yet for this rule"))

         :else
         ($ :<>
            ($ :div.bg-white.shadow.overflow-hidden.sm:rounded-md.mb-4
               ($ :table.min-w-full.divide-y.divide-gray-200
                  {:data-id "action-log-table"}
                  ($ :thead.bg-gray-50
                     ($ :tr
                        ($ :th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Start Time")
                        ($ :th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Execution Time")
                        ($ :th.px-4.py-3.text-center.text-xs.font-medium.text-gray-500.uppercase "Status")
                        ($ :th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Agent Invoke")
                        ($ :th.px-4.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase "Info")))
                  ($ :tbody.bg-white.divide-y.divide-gray-200
                     (for [action-entry actions]
                       ($ action-row {:key (:action-id action-entry)
                                      :action-entry action-entry
                                      :module-id module-id
                                      :agent-name agent-name})))))

            (when has-more?
              ($ :div.flex.justify-center.mt-4
                 ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.text-sm.font-medium.cursor-pointer.disabled:opacity-50.disabled:cursor-not-allowed
                    {:onClick load-more
                     :disabled loading?}
                    (if loading? "Loading..." "Load More")))))))))
