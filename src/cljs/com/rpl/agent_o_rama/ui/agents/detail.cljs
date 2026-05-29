(ns com.rpl.agent-o-rama.ui.agents.detail
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   [re-frame.core :as rf]
   [re-frame.query :as rfq]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   [com.rpl.agent-o-rama.ui.invocations.index :as inv-index]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]))

;; =============================================================================
;; FORM REGISTRATION - Manual Run Agent
;; =============================================================================

(forms/reg-form
 :manual-run-agent
 {:steps [:main]
  :main {:initial-fields (fn [props]
                           {:args ""
                            :metadata-args ""
                            :module-id (:module-id props)
                            :agent-name (:agent-name props)})
         :validators {:args [forms/required forms/valid-json]
                      :metadata-args [forms/valid-json]}
         :modal-props {:title "Manual Run Agent"}}
  :on-submit (fn [db form-state]
               (let [{:keys [args metadata-args module-id agent-name]} form-state
                     parsed-args (try (js->clj (js/JSON.parse args)) (catch js/Error _ nil))
                     parsed-metadata (try (if (str/blank? metadata-args)
                                            {}
                                            (js->clj (js/JSON.parse metadata-args)))
                                          (catch js/Error _ {}))]

                 ;; Mark as submitting
                 (forms/set-submitting! (:form-id form-state) true)

                 ;; Make the Sente request
                 (-> (rpc/call ::rpc-invocations/run-agent!!
                  {:module-id module-id
                   :agent-name agent-name
                   :args parsed-args
                   :metadata parsed-metadata})
                 (.then (fn [data]
                          (forms/set-submitting! (:form-id form-state) false)
                          (rf/dispatch [::forms/set-field (:form-id form-state) :args ""])
                          (rf/dispatch [::forms/set-field (:form-id form-state) :metadata-args ""])
                          (rfe/push-state :agent/invocation-detail
                                          {:module-id module-id
                                           :agent-name agent-name
                                           :invoke-id (str (:task-id data) "-" (:invoke-id data))})))
                 (.catch (fn [err]
                           (forms/set-submitting! (:form-id form-state) false)
                           (forms/set-error! (:form-id form-state) (str "Error: " (if (map? err) (or (:error err) "Unknown error") (str err)))))))))})

(defui node-stats-panel [{:keys [selected-node module-id agent-name granularity time-label
                                 granularity-items granularity-label stat-items stat-label]}]
  (let [node-id (when selected-node (aget selected-node "id"))
        decoded-agent-name (common/url-decode agent-name)
        {:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-invocations/get-node-stats!!
                        {:module-id module-id :agent-name agent-name :granularity granularity}])
        loading? (#{:loading :idle} query-status)]

    ($ :div
       ;; Header
       ($ :div.p-4.border-b.border-gray-200
          ($ :h3.text-lg.font-semibold.text-gray-800 "Node Stats"))

       ;; Dropdowns at top of panel
       ($ :div.p-4.border-b.border-gray-200.space-y-3
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Time window:")
             ($ :div
                ($ common/Dropdown
                   {:label "Granularity"
                    :display-text granularity-label
                    :items granularity-items
                    :data-testid "node-granularity-selector"})))
          ($ :div.flex.items-center.gap-2
             ($ :label.text-sm.font-medium.text-gray-700.whitespace-nowrap
                "Show on nodes:")
             ($ :div
                ($ common/Dropdown
                   {:label "Stat"
                    :display-text stat-label
                    :items stat-items
                    :data-testid "node-stat-selector"}))))

       ;; Stats content
       (cond
         (nil? selected-node)
         ($ :div.p-6.text-center.text-gray-500
            "Select a node to view stats")

         loading?
         ($ :div.p-6.text-center
            ($ common/spinner {:size :medium}))

         error
         ($ :div.p-6.text-center.text-red-500
            (str "Error loading stats: " error))

         :else
         (let [node-stats (:node-stats data)
               node-data (get node-stats node-id)]
           (if node-data
             ($ :div.grid.grid-cols-2.gap-4
                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Mean Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:mean node-data) (int (:mean node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Invocations")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (or (:count node-data) 0)))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Min Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:min node-data) (int (:min node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "Max Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (:max node-data) (int (:max node-data))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P50 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.5) (int (get node-data 0.5))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P90 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.9) (int (get node-data 0.9))) "ms")))

                ($ :div.bg-gray-50.p-4.rounded-md
                   ($ :div.text-xs.text-gray-500.uppercase.tracking-wide "P99 Latency")
                   ($ :div.text-base.font-semibold.text-gray-900
                      (str (when (get node-data 0.99) (int (get node-data 0.99))) "ms"))))
             ($ :div.p-6.text-center.text-gray-500
                (str "No data for \"" node-id "\" at " time-label))))))))

(defui graph-panel [{:keys [selected-node set-selected-node granularity selected-stat]}]
  (let [{:keys [module-id agent-name]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        decoded-agent-name (common/url-decode agent-name)

        ;; Fetch graph topology
        {graph-data :data graph-error :error graph-status :status}
        (use-subscribe [::rfq/query ::rpc-invocations/get-graph!!
                        {:module-id module-id :agent-name agent-name}])
        data graph-data
        loading? (= graph-status :loading)
        error graph-error

        ;; Fetch node stats for display on nodes
        {stats-data :data}
        (use-subscribe [::rfq/query ::rpc-invocations/get-node-stats!!
                        {:module-id module-id :agent-name agent-name :granularity granularity}])
        node-stats (:node-stats stats-data)]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading graph..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading graph: " error))
      (nil? (:graph data)) ($ :div.flex.justify-center.items-center.py-8
                               ($ :div.text-gray-500 "No graph available"))
      :else ($ agent-graph/graph {:initial-data data
                                  :height "500px"
                                  :selected-node selected-node
                                  :set-selected-node set-selected-node
                                  :node-stats node-stats
                                  :selected-stat selected-stat}))))

(defui manual-run [{:keys [form-id]}]
  (let [form (forms/use-form form-id)
        args-field (forms/use-form-field form-id :args)
        metadata-field (forms/use-form-field form-id :metadata-args)

        handle-submit (fn [e]
                        (.preventDefault e)
                        ((:submit! form)))
        is-blank (str/blank? (:value args-field))]

    ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6
       ($ :form {:onSubmit handle-submit}
          ($ :div.text-sm.font-medium.text-gray-600.mb-4 "Manually Run Agent")
          ($ :div.flex.gap-3.justify-between
             ;; Arguments textarea with live validation
             ($ :div.flex-1.flex.flex-col
                ($ :textarea
                   {:className (str "flex-1 p-3 border rounded-md text-sm placeholder-gray-400 focus:ring-2 transition-colors duration-150 resize-none "
                                    (if (and (not is-blank) (:error args-field))
                                      "border-red-300 focus:ring-red-500 focus:border-red-500"
                                      "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                    :placeholder "[arg1, arg2, arg3, ...] (json)"
                    :value (or (:value args-field) "")
                    :onChange #((:on-change args-field) (.. % -target -value))
                    :rows 3
                    :disabled (:submitting? form)})
                ;; Always render error container to prevent layout shift
                ($ :div.text-sm.text-red-600.mt-1 {:style {:min-height "1.25rem"}}
                   (if is-blank
                     ""
                     (or (:error args-field) ""))))

             ;; Metadata textarea with live validation
             ($ :div.flex-1.flex.flex-col
                ($ :textarea
                   {:className (str "flex-1 p-3 border rounded-md text-sm placeholder-gray-400 focus:ring-2 transition-colors duration-150 resize-none "
                                    (if (:error metadata-field)
                                      "border-red-300 focus:ring-red-500 focus:border-red-500"
                                      "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                    :placeholder "Metadata (JSON map, optional)"
                    :value (or (:value metadata-field) "")
                    :onChange #((:on-change metadata-field) (.. % -target -value))
                    :rows 3
                    :disabled (:submitting? form)})
                ;; Always render error container to prevent layout shift
                ($ :div.text-sm.text-red-600.mt-1 {:style {:min-height "1.25rem"}}
                   (or (:error metadata-field) "")))

             ;; Submit button
             ($ :button
                {:type "submit"
                 :disabled (or (not (:valid? form)) (:submitting? form))
                 :className (if (or (not (:valid? form)) (:submitting? form))
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-not-allowed transition-colors duration-150 bg-gray-400"
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-pointer transition-colors duration-150 bg-blue-600 hover:bg-blue-700")}
                (if (:submitting? form) "Running..." "Submit"))))

       ;; Show form-level errors only (success navigates to trace)
       (when (:error form)
         ($ :div.mt-4.p-3.rounded-md.bg-red-50.border.border-red-200
            ($ :div.text-red-700.text-sm (:error form)))))))

(defui agent []
  (let [{:keys [module-id agent-name]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        ;; Use a simple keyword for the form-id (schema expects Keyword, not vector)
        form-id :manual-run-agent

        ;; State for selected node and graph controls
        [selected-node set-selected-node] (uix/use-state nil)
        [granularity set-granularity] (uix/use-state 3600) ;; hour granularity = last hour
        [selected-stat set-selected-stat] (uix/use-state :mean) ;; default to mean

        ;; Granularity options
        ;; Granularity selector - each bucket is already aggregated by telemetry
        granularity-items [{:key 3600 :label "Last Hour" :selected? (= granularity 3600) :on-select #(set-granularity 3600)}
                           {:key 86400 :label "Last Day" :selected? (= granularity 86400) :on-select #(set-granularity 86400)}
                           {:key 2592000 :label "Last Month" :selected? (= granularity 2592000) :on-select #(set-granularity 2592000)}]

        ;; Time label for display
        time-label (condp = granularity
                     3600 "Last Hour"
                     86400 "Last Day"
                     2592000 "Last Month"
                     "Recent")

        ;; Stat selector options
        stat-items [{:key :mean :label "Mean" :selected? (= selected-stat :mean) :on-select #(set-selected-stat :mean)}
                    {:key :count :label "Count" :selected? (= selected-stat :count) :on-select #(set-selected-stat :count)}
                    {:key :min :label "Min" :selected? (= selected-stat :min) :on-select #(set-selected-stat :min)}
                    {:key :max :label "Max" :selected? (= selected-stat :max) :on-select #(set-selected-stat :max)}
                    {:key 0.25 :label "P25" :selected? (= selected-stat 0.25) :on-select #(set-selected-stat 0.25)}
                    {:key 0.5 :label "P50" :selected? (= selected-stat 0.5) :on-select #(set-selected-stat 0.5)}
                    {:key 0.75 :label "P75" :selected? (= selected-stat 0.75) :on-select #(set-selected-stat 0.75)}
                    {:key 0.9 :label "P90" :selected? (= selected-stat 0.9) :on-select #(set-selected-stat 0.9)}
                    {:key 0.99 :label "P99" :selected? (= selected-stat 0.99) :on-select #(set-selected-stat 0.99)}]

        granularity-label (or (:label (first (filter :selected? granularity-items))) "Last Hour")
        stat-label (or (:label (first (filter :selected? stat-items))) "Mean")]

    ;; Initialize the form when the component mounts or when module-id/agent-name changes
    (uix/use-effect
     (fn []
       (rf/dispatch [::forms/initialize form-id {:module-id module-id :agent-name agent-name}])
       ;; Cleanup: Clear the form when the component unmounts or agent changes
       (fn []
         (forms/clear-form! form-id)))
     [module-id agent-name form-id])

    ($ :div.p-4
       ($ :div.flex.gap-4
          ($ :div {:className "w-1/2"}
             ($ graph-panel {:selected-node selected-node
                             :set-selected-node set-selected-node
                             :granularity granularity
                             :selected-stat selected-stat}))
          ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm {:className "w-1/2"}
             ($ node-stats-panel {:selected-node selected-node
                                  :module-id module-id
                                  :agent-name agent-name
                                  :granularity granularity
                                  :time-label time-label
                                  :granularity-items granularity-items
                                  :granularity-label granularity-label
                                  :stat-items stat-items
                                  :stat-label stat-label})))
       ($ :div.p-4.flex.gap-1
          ($ :div
             {:style {:flex-grow "1"}}
             ($ manual-run {:form-id form-id})))

       ($ :div.p-4
          ($ inv-index/mini-invocations)))))
