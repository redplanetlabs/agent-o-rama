(ns com.rpl.agent-o-rama.ui.rules
  (:require
   ["@heroicons/react/24/outline" :refer [TrashIcon]]
   [clojure.string :as str]
   [reitit.frontend.easy :as rfe]
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.rules-forms :as rules-forms :refer [action-friendly-name]]))

(defn process-eval-action-info
  "Process action-params for aor/eval actions.
  If info-map contains 'invoke' (an AgentInvokeImpl), extract it and remove from info-map."
  [action-params]
  (if-let [invoke (get-in action-params ["info-map" "invoke"])]
    {:eval-invoke invoke
     :action-params (update action-params "info-map" dissoc "invoke")}
    {:eval-invoke nil
     :action-params action-params}))

(defn truncate-json
  "Truncate JSON string to max-length characters."
  [json-str max-length]
  (if (> (count json-str) max-length)
    (str (subs json-str 0 max-length) "...")
    json-str))

(defn pretty-print-json
  "Convert a map to pretty-printed JSON string."
  [obj]
  (js/JSON.stringify (clj->js obj) nil 2))

(defn format-filter-compact
  "Format filter structure as compact string representation."
  [filter-map]
  (when filter-map
    (let [type (get filter-map "type" (:type filter-map))]
      (if-not type
        nil
        (case (keyword type)
          :and
          (let [filters (get filter-map "filters" (:filters filter-map))]
            (if (empty? filters)
              nil
              (str "and(" (str/join ", " (map format-filter-compact filters)) ")")))

          :or
          (let [filters (get filter-map "filters" (:filters filter-map))]
            (if (empty? filters)
              "or()"
              (str "or(" (str/join ", " (map format-filter-compact filters)) ")")))

          :not
          (let [inner-filter (get filter-map "filter" (:filter filter-map))]
            (str "not(" (format-filter-compact inner-filter) ")"))

          :token-count
          (let [token-type (get filter-map "token-type" (:token-type filter-map))
                comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "tokenCount(" token-type ", " (name comparator) ", " value ")"))

          :error "error()"

          :latency
          (let [comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "latency(" (name comparator) ", " value ")"))

          :feedback
          (let [rule-name (get filter-map "rule-name" (:rule-name filter-map))
                feedback-key (get filter-map "feedback-key" (:feedback-key filter-map))
                comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
                comparator (get comp-spec "comparator" (:comparator comp-spec))
                value (get comp-spec "value" (:value comp-spec))]
            (str "feedback(" rule-name ", " feedback-key ", " (name comparator) ", " value ")"))

          :input-match
          (let [json-path (get filter-map "json-path" (:json-path filter-map))
                regex (get filter-map "regex" (:regex filter-map))]
            (str "inputMatch(" json-path ", " regex ")"))

          :output-match
          (let [json-path (get filter-map "json-path" (:json-path filter-map))
                regex (get filter-map "regex" (:regex filter-map))]
            (str "outputMatch(" json-path ", " regex ")"))

          (str type))))))

(defn format-filter-pretty
  "Format filter structure as multi-line indented string."
  [filter-map indent-level]
  (when filter-map
    (let [indent (apply str (repeat indent-level "  "))
          type (get filter-map "type" (:type filter-map))]
      (case (keyword type)
        :and
        (let [filters (get filter-map "filters" (:filters filter-map))]
          (str indent "and(\n"
               (str/join
                "\n"
                (map #(format-filter-pretty % (inc indent-level)) filters))
               "\n" indent ")"))

        :or
        (let [filters (get filter-map "filters" (:filters filter-map))]
          (str indent "or(\n"
               (str/join
                "\n"
                (map #(format-filter-pretty % (inc indent-level)) filters))
               "\n" indent ")"))

        :not
        (let [inner-filter (get filter-map "filter" (:filter filter-map))]
          (str indent "not(\n"
               (format-filter-pretty inner-filter (inc indent-level))
               "\n" indent ")"))

        :token-count
        (let [token-type (get filter-map "token-type" (:token-type filter-map))
              comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
              comparator (get comp-spec "comparator" (:comparator comp-spec))
              value (get comp-spec "value" (:value comp-spec))]
          (str indent "tokenCount(" token-type ", " (name comparator) ", " value ")"))

        :error
        (str indent "error()")

        :latency
        (let [comp-spec (get filter-map "comparator-spec" (:comparator-spec filter-map))
              comparator (get comp-spec "comparator" (:comparator comp-spec))
              value (get comp-spec "value" (:value comp-spec))]
          (str indent "latency(" (name comparator) ", " value ")"))

        :feedback
        (let [rule-name (get filter-map "rule-name" (:rule-name filter-map))
              feedback-key (get
                            filter-map
                            "feedback-key"
                            (:feedback-key filter-map))
              comp-spec (get
                         filter-map
                         "comparator-spec"
                         (:comparator-spec filter-map))
              comparator (get comp-spec "comparator" (:comparator comp-spec))
              value (get comp-spec "value" (:value comp-spec))]
          (str indent "feedback("
               rule-name
               ", "
               feedback-key
               ", "
               (name comparator)
               ", "
               value
               ")"))

        :input-match
        (let [json-path (get filter-map "json-path" (:json-path filter-map))
              regex (get filter-map "regex" (:regex filter-map))]
          (str indent "inputMatch(" json-path ", " regex ")"))

        :output-match
        (let [json-path (get filter-map "json-path" (:json-path filter-map))
              regex (get filter-map "regex" (:regex filter-map))]
          (str indent "outputMatch(" json-path ", " regex ")"))

        (str indent type)))))

(defn format-timestamp
  "Format timestamp millis as human-readable date/time."
  [millis]
  (when millis
    (let [date (js/Date. millis)
          now (js/Date.)
          diff-ms (- (.getTime now) millis)
          diff-hours (/ diff-ms 1000 60 60)]
      (if (< diff-hours 24)
        (str (.toLocaleTimeString date) " (today)")
        (.toLocaleString date)))))

(defui hover-tooltip
  "Tooltip component that shows content on hover."
  [{:keys [truncated-content full-content]}]
  (let [[show-tooltip? set-show-tooltip!] (uix/use-state false)]
    ($ :div.relative.inline-block
       {:on-mouse-enter #(set-show-tooltip! true)
        :on-mouse-leave #(set-show-tooltip! false)}
       ($ :div.truncate.cursor-help truncated-content)
       (when show-tooltip?
         ($ :div.fixed.z-50.bg-gray-900.text-white.text-xs.rounded.shadow-lg.p-3.max-w-lg.whitespace-pre-wrap.break-words.pointer-events-none
            {:style {:transform "translateY(4px)"}}
            full-content)))))

(defui rule-row
  "Display a single rule as a table row."
  [{:keys [rule-name rule module-id agent-name on-delete]}]
  (let [action-name (:action-name rule)
        node-name (:node-name rule)
        action-params (:action-params rule)
        filter-spec (:filter rule)
        sampling-rate (:sampling-rate rule)
        start-time-millis (:start-time-millis rule)
        status-filter (:status-filter rule)
        [deleting? set-deleting!] (uix/use-state false)

        handle-delete (fn []
                        (when (js/confirm
                               (str "Are you sure you want to delete rule '"
                                    rule-name
                                    "'?"))
                          (set-deleting! true)
                          (sente/request!
                           [:analytics/delete-rule {:module-id module-id
                                                    :agent-name agent-name
                                                    :rule-name rule-name}]
                           5000
                           (fn [reply]
                             (set-deleting! false)
                             (if (:success reply)
                               (when on-delete (on-delete))
                               (js/alert
                                (str "Failed to delete rule: "
                                     (or (:error reply) "Unknown error"))))))))]
    ($ :tr.hover:bg-gray-50
       ($ :td.px-4.py-1.text-sm.font-medium.text-gray-900 rule-name)
       ($ :td.px-4.py-1.text-sm.text-gray-600
          (or node-name ($ :span.italic.text-gray-400 "agent-level")))
       ($ :td.px-4.py-1.text-sm.text-gray-700 (action-friendly-name action-name))

       ;; action-params column with truncated JSON and hover tooltip
       ($ :td.px-4.py-1.text-sm.max-w-xs
          (if action-params
            (let [json-str (js/JSON.stringify (clj->js action-params))
                  truncated (truncate-json json-str 50)
                  pretty (pretty-print-json action-params)]
              ($ hover-tooltip
                 {:truncated-content truncated
                  :full-content pretty}))
            ($ :span.italic.text-gray-400 "none")))

       ;; status-filter column
       ($ :td.px-4.py-1.text-sm
          (let [status-kw (keyword status-filter)
                color-class (case status-kw
                              :success "text-green-700 font-medium"
                              :failure "text-red-700 font-medium"
                              :all "text-blue-700 font-medium"
                              "text-gray-700")]
            ($ :span {:className color-class}
               (or (name status-kw) "N/A"))))

;; filter column with compact string and hover tooltip
       ($ :td.px-4.py-1.text-sm.max-w-xs
          (let [compact (when filter-spec (format-filter-compact filter-spec))
                pretty (when filter-spec (format-filter-pretty filter-spec 0))
                [show-tooltip? set-show-tooltip!] (uix/use-state false)]
            (if (and compact (not (str/blank? compact)))
              ($ :div.relative
                 {:on-mouse-enter #(set-show-tooltip! true)
                  :on-mouse-leave #(set-show-tooltip! false)}
                 ($ :div.line-clamp-2.cursor-help compact)
                 (when show-tooltip?
                   ($ :div.fixed.z-50.bg-gray-900.text-white.text-xs.rounded.shadow-lg.p-3.max-w-lg.whitespace-pre-wrap.break-words.pointer-events-none
                      {:style {:transform "translateY(4px)"}}
                      pretty)))
              ($ :span.italic.text-gray-400 "None"))))

       ;; sampling-rate column
       ($ :td.px-4.py-1.text-sm.text-gray-700
          (if sampling-rate
            (str (int (* sampling-rate 100)) "%")
            ($ :span.italic.text-gray-400 "N/A")))

       ;; start-time-millis column
       ($ :td.px-4.py-1.text-sm.text-gray-700
          (if start-time-millis
            (format-timestamp start-time-millis)
            ($ :span.italic.text-gray-400 "N/A")))

       ;; action-log column
       ($ :td.px-4.py-1.text-sm
          ($ :a.text-blue-600.hover:text-blue-800.hover:underline.font-medium.cursor-pointer
             {:onClick (fn [e]
                         (.preventDefault e)
                         (rfe/push-state :agent/action-log
                                         {:module-id module-id
                                          :agent-name agent-name
                                          :rule-name rule-name}))}
             "View"))

       ;; actions column (delete button)
       ($ :td.px-4.py-1.text-sm.text-center
          ($ :button.inline-flex.items-center.justify-center.p-1.text-gray-400.hover:text-red-600.hover:bg-red-50.rounded.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick handle-delete
              :disabled deleting?
              :title (if deleting? "Deleting..." "Delete rule")}
             ($ TrashIcon {:className "h-5 w-5"}))))))

(defn th
  "Helper for table header cells"
  ([text] (th text nil))
  ([text {:keys [align] :or {align :left}}]
   ($ :th {:class (str "px-4 py-2 text-xs font-medium text-gray-500 uppercase "
                       (case align
                         :left "text-left"
                         :center "text-center"
                         :right "text-right"))}
      text)))

(defui rules-page
  "Display rules and actions for an agent as a table."
  []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        [rules set-rules!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state true)
        [error set-error!] (uix/use-state nil)
        refetch-trigger (state/use-sub [:ui :rules :refetch-trigger module-id agent-name])]

    (uix/use-effect
     (fn []
       (set-loading! true)
       (sente/request!
        [:analytics/fetch-rules {:module-id module-id :agent-name agent-name}]
        5000
        (fn [reply]
          (set-loading! false)
          (if (:success reply)
            (set-rules! (:data reply))
            (set-error! (or (:error reply) "Failed to fetch rules")))))
       js/undefined)
     [module-id agent-name refetch-trigger])

    ($ :div.p-6
       ($ :div.flex.justify-end.items-center.mb-4
          ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.text-sm.font-medium.cursor-pointer
             {:onClick #(rules-forms/show-add-rule-modal! {:module-id module-id
                                                           :agent-name agent-name})}
             "+ Add Rule"))

       (cond
         loading?
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "Loading rules...")
            ($ common/spinner {:size :medium}))

         error
         ($ :div.bg-red-50.border.border-red-200.rounded-md.p-4
            ($ :div.text-red-800.font-medium "Error:")
            ($ :div.text-red-600 error))

         (empty? rules)
         ($ :div.bg-gray-50.border.border-gray-200.rounded-md.p-4.text-center
            ($ :div.text-gray-500.italic "No rules configured for this agent"))

         :else
         ($ :div.bg-white.shadow.overflow-hidden.sm:rounded-md
            ($ :table.min-w-full.divide-y.divide-gray-200
               ($ :thead.bg-gray-50
                  ($ :tr
                     (th "Rule Name")
                     (th "Node")
                     (th "Action")
                     (th "Action Params")
                     (th "Status Filter")
                     (th "Filter")
                     (th "Sampling Rate")
                     (th "Start Time")
                     (th "Action Log")
                     (th "Actions" {:align :center})))
               ($ :tbody.bg-white.divide-y.divide-gray-200
                  (for [[rule-name rule] rules]
                    ($ rule-row
                       {:key rule-name
                        :rule-name (name rule-name)
                        :rule rule
                        :module-id module-id
                        :agent-name agent-name
                        :on-delete
                        (fn []
                          (let [current-val (or refetch-trigger 0)]
                            (state/dispatch
                             [:db/set-value
                              [:ui :rules :refetch-trigger module-id agent-name]
                              (inc current-val)])))})))))))))
