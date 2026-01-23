(ns com.rpl.agent-o-rama.ui.debug.queries
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]))

(defn- parse-number
  [value]
  (let [num (js/Number value)]
    (if (js/isNaN num) 0 num)))

(defn- positive-or-nil
  [value]
  (when (and (number? value) (pos? value))
    value))

(defn- number-input
  [{:keys [label data-id value on-change min step]}]
  ($ :label.flex.flex-col.gap-1.text-sm
     ($ :span.font-medium label)
     ($ :input.border.rounded.px-2.py-1.text-sm
        {:type "number"
         :data-id data-id
         :min (or min 0)
         :step (or step 1)
         :value value
         :onChange (fn [event]
                     (on-change (parse-number (.. event -target -value))))})))

(defui page
  []
  (let [[config set-config!] (uix/use-state {:delay-ms 0
                                             :failures-left 0})
        [options set-options!] (uix/use-state {:timeout-ms 1000
                                               :refetch-interval-ms 0
                                               :retry-base-ms 200
                                               :retry-max-ms 2000
                                               :retry-factor 2})
        query-key [:debug :query]
        query-state (state/use-sub (into [:queries] query-key))
        query-opts {:query-key query-key
                    :sente-event [:debug/query {}]
                    :timeout-ms (:timeout-ms options)
                    :refetch-interval-ms (positive-or-nil (:refetch-interval-ms options))
                    :retry-base-ms (positive-or-nil (:retry-base-ms options))
                    :retry-max-ms (positive-or-nil (:retry-max-ms options))
                    :retry-factor (:retry-factor options)
                    :refetch-on-mount false}
        {:keys [data error loading? fetching? refetch]}
        (queries/use-sente-query query-opts)
        current-state (merge queries/default-query-state query-state)
        start-run! (fn []
                     (state/dispatch
                      [:db/set-value (into [:queries] query-key)
                       (merge queries/default-query-state {:data nil :error nil})])
                     (sente/request!
                      [:debug/query-config config]
                      5000
                      (fn [_]
                        (refetch))))]
    ($ :div.p-6.space-y-6 {:data-id "debug-query-page"}
       ($ :h1.text-xl.font-semibold "Query Debug")

       ($ :div.grid.grid-cols-1.gap-6.md:grid-cols-2
          ($ :div.space-y-4
             ($ :h2.text-lg.font-semibold "Backend config")
             ($ number-input {:label "Delay ms"
                              :data-id "debug-delay-ms"
                              :value (:delay-ms config)
                              :on-change #(set-config! (assoc config :delay-ms %))})
             ($ number-input {:label "Failures left"
                              :data-id "debug-failures-left"
                              :value (:failures-left config)
                              :on-change #(set-config! (assoc config :failures-left %))}))

          ($ :div.space-y-4
             ($ :h2.text-lg.font-semibold "Query options")
             ($ number-input {:label "Timeout ms"
                              :data-id "debug-timeout-ms"
                              :value (:timeout-ms options)
                              :on-change #(set-options! (assoc options :timeout-ms %))})
             ($ number-input {:label "Refetch interval ms (0 disables)"
                              :data-id "debug-refetch-interval-ms"
                              :value (:refetch-interval-ms options)
                              :on-change #(set-options! (assoc options :refetch-interval-ms %))})
             ($ number-input {:label "Retry base ms (0 disables)"
                              :data-id "debug-retry-base-ms"
                              :value (:retry-base-ms options)
                              :on-change #(set-options! (assoc options :retry-base-ms %))})
             ($ number-input {:label "Retry max ms (0 disables)"
                              :data-id "debug-retry-max-ms"
                              :value (:retry-max-ms options)
                              :on-change #(set-options! (assoc options :retry-max-ms %))})
             ($ number-input {:label "Retry factor"
                              :data-id "debug-retry-factor"
                              :value (:retry-factor options)
                              :min 1
                              :step 0.5
                              :on-change #(set-options! (assoc options :retry-factor %))})))

       ($ :div.flex.items-center.gap-3
          ($ :button.rounded.bg-blue-600.text-white.px-4.py-2.text-sm.font-medium
             {:data-id "debug-start"
              :onClick start-run!}
             "Start request")
          ($ :div.text-sm.text-gray-500
             (cond
               loading? "Loading..."
               fetching? "Fetching..."
               error (str "Error: " error)
               :else "Idle")))

       ($ :div.grid.grid-cols-1.gap-4.md:grid-cols-2
          ($ :div.space-y-2
             ($ :h2.text-lg.font-semibold "Query state")
             ($ :div {:data-id "query-status"} (name (:status current-state)))
             ($ :div {:data-id "query-fetching"} (str (:fetching? current-state)))
             ($ :div {:data-id "query-pending"} (str (:pending? current-state)))
             ($ :div {:data-id "query-retry-count"} (str (:retry-count current-state)))
             ($ :div {:data-id "query-error"} (or (:error current-state) "")))

          ($ :div.space-y-2
             ($ :h2.text-lg.font-semibold "Response data")
             ($ :div {:data-id "query-request-count"} (str (get data :request-count "")))
             ($ :div {:data-id "query-max-in-flight"} (str (get data :max-in-flight "")))
             ($ :div {:data-id "query-failures-left"} (str (get data :failures-left "")))
             ($ :div {:data-id "query-delay-ms"} (str (get data :delay-ms "")))))

       ($ :div
          ($ :h2.text-lg.font-semibold "Raw state")
          ($ :pre.text-xs.bg-gray-100.rounded.p-3.whitespace-pre-wrap
             {:data-id "query-state-raw"}
             (common/pretty-format current-state))))))
