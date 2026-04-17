(ns com.rpl.agent-o-rama.ui.invocations.filters
  (:require
   ["@heroicons/react/24/outline" :refer [MagnifyingGlassIcon]]
   ["use-debounce" :refer [useDebounce]]
   [re-frame.db :as rdb]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [reitit.frontend.easy :as rfe]
   [cognitect.transit :as transit]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]))

;; =============================================================================
;; RE-FRAME DB PATH  [:invocations-filters module-id agent-name]
;; :open-editor / :editor — ephemeral editor UI only.
;; Applied filters live in the URL only (reitit :parameters :query :filters);
;; parent decodes and passes :applied into filter-bar.
;; =============================================================================

(def default-applied-filters
  {:source "EXPERIMENT"
   :source-not? true})

(def filter-type-order [:node :latency :error :source :feedback])

(def filter-type-labels
  {:node "Node"
   :latency "Latency"
   :error "Error"
   :source "Source"
   :feedback "Feedback"})

(defn- panel-k [module-id agent-name]
  [:invocations-filters module-id agent-name])

(def panel-default-state
  {:open-editor nil
   :editor nil})

(rf/reg-sub ::panel
  (fn [db [_ module-id agent-name]]
    (merge panel-default-state
           (get-in db (panel-k module-id agent-name)))))

(defn metric-item->type [metric-item]
  (let [metric (:metric metric-item)]
    (if (or (contains? metric :categories) (some? (get metric "categories")))
      :categorical
      :numeric)))

(defn metric-item->categories [metric-item]
  (let [metric (:metric metric-item)]
    (->> (or (:categories metric) (get metric "categories") [])
         (map str)
         sort
         vec)))

;; --- URL: transit + base64 (only :applied) ---------------------------------

(defn encode-filters-param [filters] (js/btoa (transit/write rpc/writer filters)))

(defn decode-filters-param [encoded] (transit/read rpc/reader (js/atob encoded)))

(defn applied-filters-from-url [encoded] (if (nil? encoded) default-applied-filters (decode-filters-param encoded)))

(defn- current-applied-from-route []
  (applied-filters-from-url (get-in @rdb/app-db [:route :parameters :query :filters])))

(defn- replace-invocations-filters-in-url! [module-id agent-name applied]
  (rfe/replace-state :agent/invocations
                     {:module-id module-id :agent-name agent-name}
                     {:filters (encode-filters-param applied)}))

;; --- Small helpers -----------------------------------------------------------

(defn derive-active-filter-types [applied]
  (let [filters (or applied {})]
    (vec
     (remove nil?
             [(when (seq (:node-names filters)) :node)
              (when (or (some? (get-in filters [:latency-ms :min]))
                        (some? (get-in filters [:latency-ms :max])))
                :latency)
              (when (contains? filters :has-error?) :error)
              (when (or (some? (:source filters))
                        (:source-not? filters))
                :source)
              (when (seq (:feedback-metrics filters)) :feedback)]))))

(defn label-str [x]
  (cond
    (keyword? x) (name x)
    (nil? x) ""
    :else (str x)))

(defn parse-long-opt [s]
  (let [t (str/trim (or s ""))]
    (when-not (str/blank? t)
      (let [n (js/parseInt t 10)]
        (when-not (js/isNaN n) n)))))

(defn empty-feedback-metric []
  {:metric-name ""
   :metric-type :numeric
   :match-any-value? false
   :allowed-values []
   :comparator :<=
   :value ""
   :source :any})

;; --- Initial :editor :value (backend-shaped slices) --------------------------

(defn initial-editor-slice [filter-type applied open-editor chip]
  (case filter-type
    :node
    (if (= :new (:mode open-editor))
      {:node-names []}
      {:node-names (vec (or (:node-names applied) []))})

    :latency
    {:latency-ms (or (:latency-ms applied) {})}

    :error
    (cond
      (not (contains? applied :has-error?)) {}
      (:has-error? applied) {:has-error? true}
      :else {:has-error? false})

    :source
    (if (or (:source applied) (:source-not? applied))
      {:source (:source applied)
       :source-not? (boolean (:source-not? applied))}
      {})

    :feedback
    (if (= :new (:mode open-editor))
      {:feedback-metrics [(empty-feedback-metric)]}
      (let [idx (:feedback-idx chip)
            rows (vec (or (:feedback-metrics applied) []))
            row (if (and (integer? idx) (<= 0 idx) (< idx (count rows)))
                  (get rows idx)
                  (empty-feedback-metric))]
        {:feedback-metrics [row]}))

    {}))

;; --- Merge editor slice -> :applied ------------------------------------------

(defn merge-node-into-applied [applied editor open-editor]
  (let [names (filterv #(not (str/blank? (str %))) (:node-names editor))]
    (if (= :new (:mode open-editor))
      (if (seq names)
        (update applied :node-names (fn [o] (conj (vec o) (first names))))
        applied)
      (if (seq names)
        (assoc applied :node-names names)
        (dissoc applied :node-names)))))

(defn merge-latency-into-applied [applied editor]
  (let [lm (:latency-ms editor)]
    (if (or (some? (:min lm)) (some? (:max lm)))
      (assoc applied :latency-ms
             (cond-> {}
               (some? (:min lm)) (assoc :min (:min lm))
               (some? (:max lm)) (assoc :max (:max lm))))
      (dissoc applied :latency-ms))))

(defn merge-error-into-applied [applied editor]
  (if (contains? editor :has-error?)
    (assoc applied :has-error? (:has-error? editor))
    (dissoc applied :has-error?)))

(defn merge-source-into-applied [applied editor]
  (if (contains? editor :source)
    (cond-> (assoc applied :source (:source editor))
      (boolean (:source-not? editor))
      (assoc :source-not? true)
      (not (boolean (:source-not? editor)))
      (dissoc :source-not?))
    (-> applied (dissoc :source) (dissoc :source-not?))))

(defn merge-feedback-into-applied [applied editor open-editor]
  (let [rows (:feedback-metrics editor)
        m (first rows)]
    (if (= :new (:mode open-editor))
      (if (and m (not (str/blank? (str (:metric-name m)))))
        (update applied :feedback-metrics (fn [o] (conj (vec o) m)))
        applied)
      (if (and m (integer? (:feedback-idx open-editor)))
        (update applied :feedback-metrics
                (fn [old]
                  (assoc (vec old) (:feedback-idx open-editor) m)))
        applied))))

(defn merge-editor-into-applied [applied open-editor editor]
  (case (:filter-type open-editor)
    :node (merge-node-into-applied applied editor open-editor)
    :latency (merge-latency-into-applied applied editor)
    :error (merge-error-into-applied applied editor)
    :source (merge-source-into-applied applied editor)
    :feedback (merge-feedback-into-applied applied editor open-editor)
    applied))

(defn clear-type-from-applied [applied filter-type]
  (case filter-type
    :node (dissoc applied :node-names)
    :latency (dissoc applied :latency-ms)
    :error (dissoc applied :has-error?)
    :source (-> applied (dissoc :source) (dissoc :source-not?))
    :feedback (dissoc applied :feedback-metrics)
    applied))

(defn chip->open-editor [chip]
  (-> (select-keys chip [:chip-id :filter-type :node-idx :node-name :feedback-idx])
      (assoc :mode :edit)))

(defn active-filter-chips [applied-filters]
  (let [applied-node-names (or (:node-names applied-filters) [])
        applied-feedback-metrics (or (:feedback-metrics applied-filters) [])]
    (vec
     (concat
      (map-indexed
       (fn [idx node-name]
         {:chip-id (str "node-" idx "-" node-name)
          :filter-type :node
          :description node-name
          :node-idx idx
          :node-name node-name})
       applied-node-names)
      (when (contains? applied-filters :latency-ms)
        [{:chip-id "latency"
          :filter-type :latency
          :description (let [mn (get-in applied-filters [:latency-ms :min])
                             mx (get-in applied-filters [:latency-ms :max])]
                         (cond
                           (and (some? mn) (some? mx)) (str mn "ms-" mx "ms")
                           (some? mn) (str ">= " mn "ms")
                           (some? mx) (str "<= " mx "ms")
                           :else "Any latency"))}])
      (when (contains? applied-filters :has-error?)
        [{:chip-id "error"
          :filter-type :error
          :description (if (:has-error? applied-filters) "Errors only" "No errors")}])
      (when (contains? applied-filters :source)
        [{:chip-id "source"
          :filter-type :source
          :description (str (if (:source-not? applied-filters) "!=" "=")
                            " "
                            (case (:source applied-filters)
                              "MANUAL" "Manual"
                              "EXPERIMENT" "Experiment"
                              (:source applied-filters)))}])
      (map-indexed
       (fn [idx {:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
         (let [comparator-label (label-str (or comparator :<=))
               source-label (label-str source)
               display-source (when (and (some? source) (not= source :any))
                                (str " (" source-label ")"))]
           {:chip-id (str "feedback-" idx "-" metric-name "-" metric-type "-" comparator "-" value "-" source "-" (common/to-json allowed-values))
            :filter-type :feedback
            :feedback-idx idx
            :description (cond
                           match-any-value?
                           (str metric-name " (any value)" display-source)

                           (= metric-type :categorical)
                           (str metric-name " in ["
                                (str/join ", " (or allowed-values []))
                                "]"
                                display-source)
                           :else
                           (str metric-name " " comparator-label " " value display-source))}))
       applied-feedback-metrics)))))

(defn remove-at-index [coll idx]
  (->> (map-indexed vector (or coll []))
       (remove (fn [[i _]] (= i idx)))
       (mapv second)))

;; =============================================================================
;; EVENTS
;; =============================================================================

(rf/reg-event-db ::update-editor
  (fn [db [_ module-id agent-name update-fn]]
    (update-in db (conj (panel-k module-id agent-name) :editor :value)
               (fn [v] (update-fn (or v {}))))))

(rf/reg-event-db ::add-type
  (fn [db [_ module-id agent-name filter-type]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          applied (current-applied-from-route)
          open-editor (case filter-type
                        :node {:chip-id "new-node" :filter-type :node :mode :new}
                        :feedback {:chip-id "new-feedback" :filter-type :feedback :mode :new}
                        {:chip-id (str "singleton-" (name filter-type))
                         :filter-type filter-type
                         :mode :edit})
          chip (case filter-type
                 :node {:mode :new}
                 :feedback {:mode :new}
                 {})
          editor-value (initial-editor-slice filter-type applied open-editor chip)]
      (assoc-in db pk
                (merge panel
                       {:open-editor open-editor
                        :editor {:value editor-value}})))))

(rf/reg-event-fx ::apply
  (fn [{:keys [db]} [_ module-id agent-name]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          open-editor (:open-editor panel)
          applied (current-applied-from-route)
          editor-value (get-in panel [:editor :value])]
      (if open-editor
        (let [next-applied (merge-editor-into-applied applied open-editor editor-value)]
          (replace-invocations-filters-in-url! module-id agent-name next-applied)
          {:db (assoc-in db pk (merge panel {:open-editor nil :editor nil}))})
        {}))))

(rf/reg-event-fx ::clear-type
  (fn [{:keys [db]} [_ module-id agent-name filter-type]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          open-editor (:open-editor panel)
          close-editor? (= filter-type (:filter-type open-editor))
          applied (current-applied-from-route)
          next-applied (clear-type-from-applied applied filter-type)]
      (replace-invocations-filters-in-url! module-id agent-name next-applied)
      {:db (assoc-in db pk
                     (merge panel
                            {:open-editor (when-not close-editor? open-editor)
                             :editor (when-not close-editor? (:editor panel))}))})))

(rf/reg-event-db ::open-chip
  (fn [db [_ module-id agent-name chip]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          applied (current-applied-from-route)
          open-editor (:open-editor panel)
          next-open (chip->open-editor chip)]
      (if (= (:chip-id open-editor) (:chip-id next-open))
        (assoc-in db pk
                  (merge panel
                         {:open-editor nil
                          :editor nil}))
        (assoc-in db pk
                  (merge panel
                         {:open-editor next-open
                          :editor {:value (initial-editor-slice (:filter-type chip)
                                                                applied
                                                                next-open
                                                                chip)}}))))))

(rf/reg-event-fx ::remove-chip
  (fn [_ [_ module-id agent-name {:keys [filter-type node-idx feedback-idx]}]]
    (let [ft (cond
               (keyword? filter-type) filter-type
               (string? filter-type) (keyword filter-type)
               :else filter-type)
          applied (current-applied-from-route)]
      (cond
        (= ft :node)
        (let [next-applied (let [next-node-names (vec (remove-at-index (:node-names applied) node-idx))]
                             (if (seq next-node-names)
                               (assoc applied :node-names next-node-names)
                               (dissoc applied :node-names)))]
          (replace-invocations-filters-in-url! module-id agent-name next-applied)
          {})

        (= ft :feedback)
        (let [next-applied (let [next-feedback (vec (remove-at-index (:feedback-metrics applied) feedback-idx))]
                             (if (seq next-feedback)
                               (assoc applied :feedback-metrics next-feedback)
                               (dissoc applied :feedback-metrics)))]
          (replace-invocations-filters-in-url! module-id agent-name next-applied)
          {})

        (= ft :source)
        {:dispatch [::clear-type module-id agent-name :source]}

        (= ft :latency)
        {:dispatch [::clear-type module-id agent-name :latency]}

        (= ft :error)
        {:dispatch [::clear-type module-id agent-name :error]}

        :else nil))))

;; =============================================================================
;; COMPONENTS
;; =============================================================================

(defui filter-bar [{:keys [module-id agent-name applied node-options feedback-metric-options-by-name
                          feedback-metric-option-names]}]
  (let [panel (or (use-subscribe [::panel module-id agent-name]) {})
        applied (or applied default-applied-filters)
        args-query (or (:args-query applied) "")
        [args-query-input set-args-query-input] (uix/use-state args-query)
        [debounced-args-query] (useDebounce args-query-input 300)
        open-editor (:open-editor panel)
        editor (or (get-in panel [:editor :value]) {})
        active-types (derive-active-filter-types applied)
        chips (active-filter-chips applied)
        add-items (map (fn [ft]
                         {:key (name ft)
                          :label (get filter-type-labels ft)
                          :disabled? (and (not (#{:node :feedback} ft))
                                          (boolean (some #(= % ft) active-types)))
                          :on-select #(rf/dispatch [::add-type module-id agent-name ft])})
                       filter-type-order)
        update-editor! #(rf/dispatch [::update-editor module-id agent-name %])
        apply! #(rf/dispatch [::apply module-id agent-name])
        open-chip! #(rf/dispatch [::open-chip module-id agent-name %])
        remove-chip! #(rf/dispatch [::remove-chip module-id agent-name %])]
    (uix/use-effect
     (fn []
       (set-args-query-input args-query))
     [args-query])
    (uix/use-effect
     (fn []
       (let [trimmed-current (str/trim args-query)
             trimmed-next (str/trim (or debounced-args-query ""))]
         (when (not= trimmed-current trimmed-next)
           (let [next-applied (if (str/blank? trimmed-next)
                                (dissoc applied :args-query)
                                (assoc applied :args-query debounced-args-query))]
             (replace-invocations-filters-in-url! module-id agent-name next-applied)))))
     [args-query debounced-args-query applied module-id agent-name])
    ($ :div.bg-white.rounded-md.border.border-gray-200.p-4.shadow-sm
       ($ :div.flex.flex-wrap.items-center.gap-2
          ($ :div.relative.w-80.max-w-full
             ($ :div.pointer-events-none.absolute.inset-y-0.left-0.flex.items-center.pl-3
                ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"}))
             ($ :input
                {:type "text"
                 :value args-query-input
                 :onChange (fn [e] (set-args-query-input (.. e -target -value)))
                 :className "block w-full rounded-md border-0 py-1.5 pl-10 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                 :placeholder "Search invocation arguments..."
                 :data-testid "invocations-filter-args-query"}))
          ($ common/Dropdown
             {:label "Add filter"
              :display-text "Add filter"
              :items add-items
              :full-width? false
              :data-testid "add-invocations-filter"})
          (if (seq chips)
            (for [{:keys [chip-id filter-type description] :as chip} chips]
              ($ :button.inline-flex.items-center.gap-2.px-3.py-1.5.rounded-full.bg-blue-50.text-blue-700.text-xs.font-medium.border.border-blue-200.cursor-pointer.hover:bg-blue-100.transition-colors.duration-150
                 {:key chip-id :type "button" :onClick #(open-chip! chip)}
                 ($ :span (get filter-type-labels filter-type))
                 ($ :span.text-blue-500.truncate.max-w-48 description)
                 ($ :span.text-blue-400.hover:text-blue-700.cursor-pointer
                    {:onClick (fn [e]
                                (.stopPropagation e)
                                (remove-chip! chip))
                     :data-testid (str "invocations-filter-chip-remove-" chip-id)}
                    "x")))
            ($ :div.text-xs.text-gray-500 "No filters added")))
       (when open-editor
         ($ :div.mt-3.p-3.border.border-gray-200.rounded-md.bg-gray-50.max-w-2xl
            ($ :div.flex.items-center.justify-between.mb-3
               ($ :div.text-sm.font-medium.text-gray-800
                  (str (if (= :new (:mode open-editor)) "Add " "Edit ")
                       (get filter-type-labels (:filter-type open-editor))
                       " filter"))
               ($ :button.text-xs.px-2.py-1.bg-blue-600.text-white.rounded.hover:bg-blue-700.cursor-pointer
                  {:type "button" :data-testid "invocations-filter-apply" :onClick apply!}
                  "Apply"))
            (case (:filter-type open-editor)
              :node
              (let [mode (:mode open-editor)
                    names (vec (:node-names editor))
                    idx (:node-idx open-editor)
                    selected-val (if (= :new mode)
                                   (str (or (first names) ""))
                                   (str (if (and (integer? idx) (<= 0 idx) (< idx (count names)))
                                          (get names idx)
                                          "")))]
                ($ :div.space-y-2
                   (if (seq node-options)
                     ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                        {:value selected-val
                         :data-testid "invocations-filter-node-select"
                         :onChange (fn [e]
                                     (let [v (.. e -target -value)]
                                       (if (= :new mode)
                                         (update-editor!
                                          (fn [_]
                                            {:node-names (if (str/blank? v) [] [v])}))
                                         (update-editor!
                                          (fn [prev]
                                            (let [n (vec (:node-names prev))]
                                              (if (and (integer? idx) (<= 0 idx) (< idx (count n)))
                                                (assoc prev :node-names
                                                       (if (str/blank? v)
                                                         (vec (concat (subvec n 0 idx) (subvec n (inc idx))))
                                                         (assoc n idx v)))
                                                prev)))))))}
                        ($ :option {:value ""} "Select node")
                        (for [node-name node-options
                              :when (some? node-name)]
                          (let [nv (str node-name)]
                            ($ :option {:key nv :value nv} nv))))
                     ($ :div.text-xs.text-gray-500 "No nodes available"))
                   ($ :div.text-xs.text-gray-500 "This filter adds one required node condition.")))

              :latency
              (let [lm (:latency-ms editor)]
                ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                   ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                      {:type "number" :data-testid "invocations-filter-latency-min"
                       :placeholder "Latency min (ms)"
                       :value (if (some? (:min lm)) (str (:min lm)) "")
                       :onChange (fn [e]
                                   (let [parsed (parse-long-opt (.. e -target -value))]
                                     (update-editor!
                                      (fn [prev]
                                        (let [l (or (:latency-ms prev) {})]
                                          (assoc prev :latency-ms
                                                 (if (some? parsed)
                                                   (assoc l :min parsed)
                                                   (dissoc l :min))))))))})
                   ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                      {:type "number" :data-testid "invocations-filter-latency-max"
                       :placeholder "Latency max (ms)"
                       :value (if (some? (:max lm)) (str (:max lm)) "")
                       :onChange (fn [e]
                                   (let [parsed (parse-long-opt (.. e -target -value))]
                                     (update-editor!
                                      (fn [prev]
                                        (let [l (or (:latency-ms prev) {})]
                                          (assoc prev :latency-ms
                                                 (if (some? parsed)
                                                   (assoc l :max parsed)
                                                   (dissoc l :max))))))))})))

              :error
              (let [cur (cond
                          (not (contains? editor :has-error?)) :all
                          (:has-error? editor) :errors-only
                          :else :no-errors)]
                ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                   {:value (name cur)
                    :data-testid "invocations-filter-error-select"
                    :onChange (fn [e]
                                (let [v (keyword (.. e -target -value))]
                                  (update-editor!
                                   (fn [_]
                                     (case v
                                       :all {}
                                       :errors-only {:has-error? true}
                                       :no-errors {:has-error? false}
                                       {})))))}
                   ($ :option {:value "all"} "All results")
                   ($ :option {:value "errors-only"} "Errors only")
                   ($ :option {:value "no-errors"} "No errors")))

              :source
              (let [src (:source editor)
                    src-not? (boolean (:source-not? editor))
                    cur (if (some? src) src "all")]
                ($ :div.space-y-2
                   ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                      {:value (str cur)
                       :data-testid "invocations-filter-source-select"
                       :onChange
                       (fn [e]
                         (let [v (.. e -target -value)]
                           (if (= v "all")
                             (update-editor! (constantly {}))
                             (update-editor!
                              (fn [prev]
                                {:source v
                                 :source-not? (boolean (:source-not? prev))})))))}
                      ($ :option {:value "all"} "All sources")
                      ($ :option {:value "API"} "API")
                      ($ :option {:value "MANUAL"} "Manual")
                      ($ :option {:value "EXPERIMENT"} "Experiment"))
                   ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700
                      ($ :input.h-4.w-4.border.border-gray-300.rounded
                         {:type "checkbox" :data-testid "invocations-filter-source-not"
                          :checked src-not?
                          :disabled (= cur "all")
                          :onChange
                          (fn [e]
                            (let [v (.. e -target -checked)]
                              (update-editor!
                               (fn [prev]
                                 (if (some? (:source prev))
                                   (assoc prev :source-not? v)
                                   prev)))))})
                      ($ :span "Not selected source"))
                   ($ :div.text-xs.text-gray-500
                      "When enabled, returns invokes from all source types except the selected one.")))

              :feedback
              (let [m (first (:feedback-metrics editor))
                    selected-metric-name (:metric-name m)
                    selected-metric-option (get feedback-metric-options-by-name selected-metric-name)
                    metric-categories (when selected-metric-option
                                        (metric-item->categories selected-metric-option))
                    match-any? (boolean (:match-any-value? m))
                    form-class (when match-any? "opacity-60 pointer-events-none")]
                ($ :div.space-y-2
                   ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                      ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                         {:value (str (or selected-metric-name ""))
                          :data-testid "invocations-filter-feedback-metric"
                          :onChange (fn [e]
                                      (let [selected-name (.. e -target -value)
                                            metric-option (get feedback-metric-options-by-name selected-name)
                                            next-type (if metric-option (metric-item->type metric-option) :numeric)]
                                        (update-editor!
                                         (fn [_]
                                           {:feedback-metrics
                                            [(assoc (empty-feedback-metric)
                                                    :metric-name selected-name
                                                    :metric-type next-type
                                                    :match-any-value? false
                                                    :allowed-values []
                                                    :comparator :<=
                                                    :value ""
                                                    :source (or (:source m) :any))]}))))}
                         ($ :option {:value ""} "Select metric")
                         (for [[idx opt-name] (map-indexed vector (or feedback-metric-option-names []))]
                           (let [n (str opt-name)]
                             (when (not (str/blank? n))
                               ($ :option {:key (str "feedback-metric-option-" idx "-" n)
                                           :value n}
                                  n)))))
                      ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                         {:value (name (or (:source m) :any))
                          :data-testid "invocations-filter-feedback-source"
                          :onChange (fn [e]
                                      (let [v (keyword (.. e -target -value))]
                                        (update-editor!
                                         (fn [prev]
                                           (let [row (first (:feedback-metrics prev))]
                                             (assoc prev :feedback-metrics [(assoc row :source v)]))))))}
                         ($ :option {:value "any"} "Any source")
                         ($ :option {:value "human"} "Human")
                         ($ :option {:value "non-human"} "Non-human")))
                   ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                      ($ :input.h-4.w-4.border.border-gray-300.rounded
                         {:type "checkbox" :data-testid "invocations-filter-feedback-any-value"
                          :checked match-any?
                          :onChange (fn [_e]
                                      (update-editor!
                                       (fn [prev]
                                         (let [row (first (:feedback-metrics prev))]
                                           (assoc prev :feedback-metrics [(update row :match-any-value? not)])))))})
                      ($ :span "Match any value (metric exists)"))
                   ($ :div {:className (str "space-y-2 " (or form-class ""))}
                      (when match-any?
                        ($ :div.text-xs.text-gray-600
                           "Matches any invocation that has this metric, regardless of metric value."))
                      (if (= :categorical (:metric-type m))
                        ($ :div.space-y-2
                           ($ :div.text-xs.text-gray-600 "Select one or more categorical values:")
                           (if (seq metric-categories)
                             ($ :div.max-h-56.overflow-auto.border.border-gray-200.rounded-md.bg-white.p-2.space-y-1
                                (for [cat-value metric-categories]
                                  ($ :label.flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                                     {:key cat-value}
                                     ($ :input.h-4.w-4.border.border-gray-300.rounded
                                        {:type "checkbox"
                                         :data-testid "invocations-filter-feedback-category-select"
                                         :disabled match-any?
                                         :checked (boolean (some #{cat-value} (:allowed-values m)))
                                         :onChange (fn [e]
                                                     (let [checked? (.. e -target -checked)]
                                                       (update-editor!
                                                        (fn [prev]
                                                          (let [row (first (:feedback-metrics prev))
                                                                av (vec (or (:allowed-values row) []))]
                                                            (assoc prev :feedback-metrics
                                                                   [(assoc row :allowed-values
                                                                           (if checked?
                                                                             (vec (distinct (conj av cat-value)))
                                                                             (vec (remove #{cat-value} av))))]))))))})
                                     ($ :span cat-value))))
                             ($ :div.text-xs.text-gray-500 "No categories available for this metric.")))
                        ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                           ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                              {:value (name (or (:comparator m) :<=))
                               :data-testid "invocations-filter-feedback-comparator"
                               :disabled match-any?
                               :onChange (fn [e]
                                           (let [v (keyword (.. e -target -value))]
                                             (update-editor!
                                              (fn [prev]
                                                (let [row (first (:feedback-metrics prev))]
                                                  (assoc prev :feedback-metrics [(assoc row :comparator v)]))))))}
                              ($ :option {:value "<="} "<=")
                              ($ :option {:value "<"} "<")
                              ($ :option {:value "="} "=")
                              ($ :option {:value "not="} "!=")
                              ($ :option {:value ">"} ">")
                              ($ :option {:value ">="} ">="))
                           ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                              {:placeholder "Feedback value"
                               :data-testid "invocations-filter-feedback-value"
                               :disabled match-any?
                               :value (str (or (:value m) ""))
                               :onChange (fn [e]
                                           (let [v (.. e -target -value)]
                                             (update-editor!
                                              (fn [prev]
                                                (let [row (first (:feedback-metrics prev))]
                                                  (assoc prev :feedback-metrics [(assoc row :value v)]))))))}))))))

              ($ :div.text-sm.text-gray-500 "Unknown filter")))))))
