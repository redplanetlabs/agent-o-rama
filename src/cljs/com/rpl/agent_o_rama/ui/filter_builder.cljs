(ns com.rpl.agent-o-rama.ui.filter-builder
  (:require
   [clojure.string :as str]
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]))

;;; Comparator Component

(def COMPARATORS
  [{:value := :label "="}
   {:value :not= :label "≠"}
   {:value :< :label "<"}
   {:value :> :label ">"}
   {:value :<= :label "≤"}
   {:value :>= :label "≥"}])

(defui ComparatorSpec
  [{:keys [value on-change value-type]}]
  (let [comparator (:comparator value :=)
        comp-value (:value value "")]
    ($ :div.flex.gap-2.items-center
       ($ :select.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
          {:value (name comparator)
           :onChange #(on-change (assoc value :comparator (keyword (.. % -target -value))))}
          (for [comp COMPARATORS]
            ($ :option {:key (name (:value comp)) :value (name (:value comp))} (:label comp))))

       (case value-type
         :number
         ($ :input.p-2.border.rounded-md.text-sm.flex-1.border-gray-300.focus:ring-blue-500.focus:border-blue-500
            {:type "number"
             :value comp-value
             :placeholder "Value"
             :onChange #(on-change (assoc value :value (js/parseFloat (.. % -target -value))))})

         :json
         ($ :input.p-2.border.rounded-md.text-sm.flex-1.border-gray-300.focus:ring-blue-500.focus:border-blue-500
            {:type "text"
             :value (if (string? comp-value) comp-value (js/JSON.stringify (clj->js comp-value)))
             :placeholder "JSON value"
             :onChange #(let [raw-value (.. % -target -value)]
                          (on-change (assoc value :value raw-value)))})))))

;;; Specialized Filter Components

(defui ErrorFilterUI
  [{:keys [_value _on-change]}]
  ($ :div.text-sm.text-gray-600.italic "Matches runs with errors"))

(defui LatencyFilterUI
  [{:keys [value on-change]}]
  ($ :div.space-y-2
     ($ :label.text-sm.font-medium.text-gray-700 "Latency (ms)")
     ($ ComparatorSpec {:value value :on-change on-change :value-type :number})))

(defui InputMatchFilterUI
  [{:keys [value on-change]}]
  ($ :div.space-y-2
     ($ :div
        ($ :label.text-sm.font-medium.text-gray-700 "JSON Path")
        ($ :input.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
           {:type "text"
            :value (or (:json-path value) "")
            :placeholder "$.field.path"
            :onChange #(on-change (assoc value :json-path (.. % -target -value)))}))
     ($ :div
        ($ :label.text-sm.font-medium.text-gray-700 "Regex Pattern")
        ($ :input.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
           {:type "text"
            :value (or (:regex value) "")
            :placeholder ".*pattern.*"
            :onChange #(on-change (assoc value :regex (.. % -target -value)))}))))

(defui OutputMatchFilterUI
  [{:keys [value on-change]}]
  ($ :div.space-y-2
     ($ :div
        ($ :label.text-sm.font-medium.text-gray-700 "JSON Path")
        ($ :input.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
           {:type "text"
            :value (or (:json-path value) "")
            :placeholder "$.field.path"
            :onChange #(on-change (assoc value :json-path (.. % -target -value)))}))
     ($ :div
        ($ :label.text-sm.font-medium.text-gray-700 "Regex Pattern")
        ($ :input.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
           {:type "text"
            :value (or (:regex value) "")
            :placeholder ".*pattern.*"
            :onChange #(on-change (assoc value :regex (.. % -target -value)))}))))

(defui TokenCountFilterUI
  [{:keys [value on-change]}]
  (let [token-type (:token-type value :total)
        comparator-spec (:comparator-spec value {:comparator := :value 0})]
    ($ :div.space-y-2
       ($ :div
          ($ :label.text-sm.font-medium.text-gray-700 "Token Type")
          ($ :select.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
             {:value (name token-type)
              :onChange #(on-change (assoc value :token-type (keyword (.. % -target -value))))}
             ($ :option {:value "input"} "Input")
             ($ :option {:value "output"} "Output")
             ($ :option {:value "total"} "Total")))
       ($ :label.text-sm.font-medium.text-gray-700 "Count")
       ($ ComparatorSpec {:value comparator-spec
                          :on-change #(on-change (assoc value :comparator-spec %))
                          :value-type :number}))))

(defui FeedbackFilterUI
  [{:keys [value on-change module-id agent-name]}]
  (let [rule-name (:rule-name value "")
        feedback-key (:feedback-key value "")
        comparator-spec (:comparator-spec value {:comparator := :value ""})
        [rules set-rules!] (uix/use-state nil)
        [loading? set-loading!] (uix/use-state false)]

    (uix/use-effect
     (fn []
       (when (and module-id agent-name)
         (set-loading! true)
         (sente/request!
          [:analytics/fetch-rules {:module-id module-id
                                    :agent-name agent-name
                                    :names-only? true
                                    :filter-by-action "aor/eval"}]
          5000
          (fn [reply]
            (set-loading! false)
            (if (:success reply)
              (set-rules! (:data reply))
              (.error js/console "Failed to fetch rules:" (:error reply)))))
         js/undefined))
     [module-id agent-name])

    ($ :div.space-y-2
       ($ :div
          ($ :label.text-sm.font-medium.text-gray-700 "Rule Name"
             ($ :span.text-red-500.ml-1 "*"))
          ($ :select.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
             {:value rule-name
              :onChange #(on-change (assoc value :rule-name (.. % -target -value)))
              :disabled loading?}
             ($ :option {:value ""} (if loading? "Loading rules..." "Select a rule"))
             (when rules
               (for [rname rules]
                 ($ :option {:key (name rname) :value (name rname)} (name rname)))))
          (when (and (not loading?) (str/blank? rule-name))
            ($ :p.text-sm.text-red-600.mt-1 "Rule name is required")))
       ($ :div
          ($ :label.text-sm.font-medium.text-gray-700 "Metric Key"
             ($ :span.text-red-500.ml-1 "*"))
          ($ :input.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
             {:type "text"
              :value feedback-key
              :placeholder "score"
              :onChange #(on-change (assoc value :feedback-key (.. % -target -value)))}))
       ($ :div
          ($ :label.text-sm.font-medium.text-gray-700 "Value")
          ($ ComparatorSpec {:value comparator-spec
                             :on-change #(on-change (assoc value :comparator-spec %))
                             :value-type :json})))))

;;; Compound Filter Components

(declare FilterNode)

(defui AndFilterUI
  [{:keys [value on-change path form-id module-id agent-name]}]
  (let [filters (:filters value [])]
    ($ :div.space-y-2
       ($ :div.text-sm.font-medium.text-gray-700 "All of the following:")
       ($ :div.ml-4.space-y-2.border-l-2.border-gray-300.pl-4
          (for [[idx filter] (map-indexed vector filters)]
            ($ FilterNode {:key idx
                           :value filter
                           :on-change #(on-change (assoc value :filters
                                                         (assoc (vec filters) idx %)))
                           :on-remove #(on-change (assoc value :filters
                                                         (vec (concat (take idx filters)
                                                                      (drop (inc idx) filters)))))
                           :path (conj path :filters idx)
                           :form-id form-id
                           :module-id module-id
                           :agent-name agent-name}))
          ($ :button.px-3.py-1.text-sm.bg-gray-100.text-gray-700.rounded.hover:bg-gray-200
             {:type "button"
              :onClick #(on-change (assoc value :filters
                                          (conj (vec filters) {:type :error})))}
             "+ Add Filter")))))

(defui OrFilterUI
  [{:keys [value on-change path form-id module-id agent-name]}]
  (let [filters (:filters value [])]
    ($ :div.space-y-2
       ($ :div.text-sm.font-medium.text-gray-700 "Any of the following:")
       ($ :div.ml-4.space-y-2.border-l-2.border-gray-300.pl-4
          (for [[idx filter] (map-indexed vector filters)]
            ($ FilterNode {:key idx
                           :value filter
                           :on-change #(on-change (assoc value :filters
                                                         (assoc (vec filters) idx %)))
                           :on-remove #(on-change (assoc value :filters
                                                         (vec (concat (take idx filters)
                                                                      (drop (inc idx) filters)))))
                           :path (conj path :filters idx)
                           :form-id form-id
                           :module-id module-id
                           :agent-name agent-name}))
          ($ :button.px-3.py-1.text-sm.bg-gray-100.text-gray-700.rounded.hover:bg-gray-200
             {:type "button"
              :onClick #(on-change (assoc value :filters
                                          (conj (vec filters) {:type :error})))}
             "+ Add Filter")))))

(defui NotFilterUI
  [{:keys [value on-change path form-id module-id agent-name]}]
  (let [inner-filter (:filter value {:type :error})]
    ($ :div.space-y-2
       ($ :div.text-sm.font-medium.text-gray-700 "Not:")
       ($ :div.ml-4.border-l-2.border-gray-300.pl-4
          ($ FilterNode {:value inner-filter
                         :on-change #(on-change (assoc value :filter %))
                         :on-remove nil
                         :path (conj path :filter)
                         :form-id form-id
                         :module-id module-id
                         :agent-name agent-name})))))

;;; Main Filter Node Component

(defui FilterNode
  [{:keys [value on-change on-remove path form-id module-id agent-name]}]
  (let [filter-type (:type value :error)]
    ($ :div.border.rounded-md.p-3.bg-white.shadow-sm
       ($ :div.flex.gap-2.items-start
          ($ :div.flex-1
             ($ :div.mb-2
                ($ :select.w-full.p-2.border.rounded-md.text-sm.border-gray-300.focus:ring-blue-500.focus:border-blue-500
                   {:value (name filter-type)
                    :onChange #(let [new-type (keyword (.. % -target -value))
                                     new-filter (case new-type
                                                  :error {:type :error}
                                                  :latency {:type :latency :comparator := :value 0}
                                                  :input-match {:type :input-match :json-path "$" :regex ".*"}
                                                  :output-match {:type :output-match :json-path "$" :regex ".*"}
                                                  :token-count {:type :token-count :token-type :total :comparator-spec {:comparator := :value 0}}
                                                  :feedback {:type :feedback :rule-name "" :feedback-key "" :comparator-spec {:comparator := :value ""}}
                                                  :and {:type :and :filters []}
                                                  :or {:type :or :filters []}
                                                  :not {:type :not :filter {:type :error}}
                                                  {:type :error})]
                                 (on-change new-filter))}
                   ($ :option {:value "error"} "Error")
                   ($ :option {:value "latency"} "Latency")
                   ($ :option {:value "input-match"} "Input Match")
                   ($ :option {:value "output-match"} "Output Match")
                   ($ :option {:value "token-count"} "Token Count")
                   ($ :option {:value "feedback"} "Feedback")
                   ($ :option {:value "and"} "And")
                   ($ :option {:value "or"} "Or")
                   ($ :option {:value "not"} "Not")))

             ($ :div.mt-2
                (case filter-type
                  :error ($ ErrorFilterUI {:value value :on-change on-change})
                  :latency ($ LatencyFilterUI {:value value :on-change on-change})
                  :input-match ($ InputMatchFilterUI {:value value :on-change on-change})
                  :output-match ($ OutputMatchFilterUI {:value value :on-change on-change})
                  :token-count ($ TokenCountFilterUI {:value value :on-change on-change})
                  :feedback ($ FeedbackFilterUI {:value value :on-change on-change :module-id module-id :agent-name agent-name})
                  :and ($ AndFilterUI {:value value :on-change on-change :path path :form-id form-id :module-id module-id :agent-name agent-name})
                  :or ($ OrFilterUI {:value value :on-change on-change :path path :form-id form-id :module-id module-id :agent-name agent-name})
                  :not ($ NotFilterUI {:value value :on-change on-change :path path :form-id form-id :module-id module-id :agent-name agent-name})
                  ($ :div.text-sm.text-gray-500.italic "Unknown filter type"))))

          (when on-remove
            ($ :button.text-red-500.hover:text-red-700.font-bold.text-lg.leading-none
               {:type "button"
                :onClick on-remove
                :title "Remove filter"}
               "×"))))))

;;; Top-Level Filter Builder

(defui FilterBuilder
  [{:keys [form-id module-id agent-name]}]
  (let [filter-field (forms/use-form-field form-id :filter)
        filter-value (:value filter-field)
        filters (:filters filter-value [])]

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700 "Filter")
       ($ :div.space-y-2.p-4.bg-gray-50.rounded-md
          ($ :div.text-sm.font-medium.text-gray-700.mb-2 "Match all of:")
          ($ :div.space-y-2
             (for [[idx filter] (map-indexed vector filters)]
               ($ FilterNode {:key idx
                              :value filter
                              :on-change #((:on-change filter-field)
                                           (assoc filter-value :filters
                                                  (assoc (vec filters) idx %)))
                              :on-remove #((:on-change filter-field)
                                           (assoc filter-value :filters
                                                  (vec (concat (take idx filters)
                                                               (drop (inc idx) filters)))))
                              :path [:filter :filters idx]
                              :form-id form-id
                              :module-id module-id
                              :agent-name agent-name}))
             ($ :button.px-4.py-2.bg-blue-100.text-blue-700.rounded.hover:bg-blue-200.text-sm.font-medium
                {:type "button"
                 :onClick #((:on-change filter-field)
                            (assoc filter-value :filters
                                   (conj (vec filters) {:type :error})))}
                "+ Add Filter")))
       (when (:error filter-field)
         ($ :p.text-sm.text-red-600.mt-1 (:error filter-field))))))
