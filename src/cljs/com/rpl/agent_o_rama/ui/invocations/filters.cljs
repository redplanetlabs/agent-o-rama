(ns com.rpl.agent-o-rama.ui.invocations.filters
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.specter :as s]))

;; =============================================================================
;; RE-FRAME DB PATH  [:invocations-filters module-id agent-name]
;; =============================================================================

(def default-applied-filters
  {:source "EXPERIMENT"
   :source-not? true})

(def default-feedback-editor
  {:metric-name ""
   :metric-type "numeric"
   :match-any-value? false
   :allowed-values []
   :categories []
   :comparator "<="
   :value ""
   :source "any"})

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
  {:applied default-applied-filters
   :open-editor nil
   :editor nil})

(rf/reg-sub ::panel
  (fn [db [_ module-id agent-name]]
    (merge panel-default-state
           (get-in db (panel-k module-id agent-name))))
  )

(defn metric-item->type [metric-item]
  (let [metric (:metric metric-item)]
    (if (or (contains? metric :categories) (some? (get metric "categories")))
      "categorical"
      "numeric")))

(defn metric-item->categories [metric-item]
  (let [metric (:metric metric-item)]
    (->> (or (:categories metric) (get metric "categories") [])
         (map str)
         sort
         vec)))

;; =============================================================================
;; PURE FUNCTIONS
;; =============================================================================

(defn encode-filters-param [filters]
  (try
    (-> filters clj->js js/JSON.stringify js/btoa)
    (catch js/Error _ nil)))

(defn decode-filters-param [encoded]
  (try
    (-> encoded js/atob js/JSON.parse (js->clj :keywordize-keys true))
    (catch js/Error _ nil)))

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

(defn keyword-like->string
  [v]
  (cond
    (keyword? v) (name v)
    (string? v) v
    (nil? v) nil
    :else (str v)))

(defn normalize-node-names
  [node-names]
  (->> (or node-names [])
       (keep (fn [v]
               (let [s (str/trim (str v))]
                 (when (not (str/blank? s))
                   s))))
       vec))

(defn normalize-latency-ms
  [latency-ms]
  (let [mn (:min latency-ms)
        mx (:max latency-ms)]
    (cond-> {}
      (number? mn) (assoc :min mn)
      (number? mx) (assoc :max mx))))

(defn normalize-feedback-metric
  [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
  (let [metric-name (str/trim (or metric-name ""))
        metric-type (keyword (or (keyword-like->string metric-type) "numeric"))
        source-key (some-> source keyword-like->string keyword)
        comparator-key (keyword (or (keyword-like->string comparator) "<="))
        value-str (str/trim (or value ""))
        allowed-values (->> (or allowed-values [])
                            (keep (fn [v]
                                    (let [s (str/trim (str v))]
                                      (when (not (str/blank? s))
                                        s))))
                            vec)
        match-any-value? (boolean match-any-value?)]
    (when (not (str/blank? metric-name))
      (cond
        match-any-value?
        (cond-> {:metric-name metric-name
                 :metric-type metric-type
                 :match-any-value? true}
          (and (some? source-key) (not= source-key :any))
          (assoc :source source-key))

        (= metric-type :categorical)
        (when (seq allowed-values)
          (cond-> {:metric-name metric-name
                   :metric-type :categorical
                   :allowed-values allowed-values}
            (and (some? source-key) (not= source-key :any))
            (assoc :source source-key)))

        (not (str/blank? value-str))
        (cond-> {:metric-name metric-name
                 :metric-type :numeric
                 :comparator comparator-key
                 :value value-str}
          (and (some? source-key) (not= source-key :any))
          (assoc :source source-key))

        :else nil))))

(defn normalize-applied-filters
  [filters]
  (let [raw-filters (or filters {})
        node-names (normalize-node-names (:node-names raw-filters))
        latency-ms (normalize-latency-ms (:latency-ms raw-filters))
        source (some-> (:source raw-filters) str/upper-case str/trim)
        feedback-metrics (->> (or (:feedback-metrics raw-filters) [])
                              (keep normalize-feedback-metric)
                              vec)]
    (cond-> {}
      (seq node-names) (assoc :node-names node-names)
      (seq latency-ms) (assoc :latency-ms latency-ms)
      (contains? raw-filters :has-error?) (assoc :has-error? (:has-error? raw-filters))
      (and (string? source) (not (str/blank? source))) (assoc :source source)
      (:source-not? raw-filters) (assoc :source-not? true)
      (seq feedback-metrics) (assoc :feedback-metrics feedback-metrics))))

(defn parse-number-input
  [v]
  (let [s (str/trim (or v ""))
        parsed (js/Number s)]
    (when (and (not (str/blank? s))
               (not (js/isNaN parsed)))
      parsed)))

(defn upsert-at-index
  [coll idx next-value]
  (let [base (vec (or coll []))]
    (if (and (integer? idx) (<= 0 idx) (< idx (count base)))
      (assoc base idx next-value)
      (conj base next-value))))

(defn remove-at-index
  [coll idx]
  (->> (map-indexed vector (or coll []))
       (remove (fn [[i _]] (= i idx)))
       (mapv second)))

(defn feedback-metric->editor
  [metric]
  (let [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]} metric]
    (merge default-feedback-editor
           {:metric-name (or metric-name "")
            :metric-type (name (or metric-type :numeric))
            :match-any-value? (boolean match-any-value?)
            :allowed-values (vec (or allowed-values []))
            :categories (vec (or allowed-values []))
            :comparator (name (or comparator :<=))
            :value (if (nil? value) "" (str value))
            :source (name (or source :any))})))

(defn feedback-editor->metric
  [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
  (let [metric-name (str/trim (or metric-name ""))
        metric-type (keyword (or metric-type "numeric"))
        match-any-value? (boolean match-any-value?)
        value (str/trim (or value ""))
        allowed-values (->> (or allowed-values [])
                            (keep (fn [v]
                                    (let [s (str/trim (str v))]
                                      (when (not (str/blank? s))
                                        s))))
                            vec)
        source-key (when (and (some? source) (not= "any" source))
                     (keyword source))]
    (when (not (str/blank? metric-name))
      (cond
        match-any-value?
        (cond-> {:metric-name metric-name
                 :metric-type metric-type
                 :match-any-value? true}
          source-key
          (assoc :source source-key))

        (= metric-type :categorical)
        (when (seq allowed-values)
          (cond-> {:metric-name metric-name
                   :metric-type :categorical
                   :allowed-values allowed-values}
            source-key
            (assoc :source source-key)))

        (not (str/blank? value))
        (cond-> {:metric-name metric-name
                 :metric-type :numeric
                 :comparator (keyword (or comparator "<="))
                 :value value}
          source-key
          (assoc :source source-key))

        :else nil))))

(defn applied-filters-from-url [encoded]
  (if (nil? encoded)
    default-applied-filters
    (if-let [decoded (decode-filters-param encoded)]
      (normalize-applied-filters decoded)
      default-applied-filters)))

(defn editor-value-for-type
  [filter-type applied {:keys [node-name feedback-idx]}]
  (case filter-type
    :node
    {:node-current (or node-name "")}

    :latency
    {:latency-min (let [v (get-in applied [:latency-ms :min])]
                    (if (some? v) (str v) ""))
     :latency-max (let [v (get-in applied [:latency-ms :max])]
                    (if (some? v) (str v) ""))}

    :error
    {:error-filter (cond
                     (true? (:has-error? applied)) "errors-only"
                     (false? (:has-error? applied)) "no-errors"
                     :else "all")}

    :source
    {:source (or (:source applied) "all")
     :source-not? (boolean (:source-not? applied))}

    :feedback
    (feedback-metric->editor (or (get (or (:feedback-metrics applied) []) feedback-idx)
                                 {}))

    {}))

(defn apply-node-editor
  [applied editor {:keys [mode node-idx]}]
  (let [selected (str/trim (or (:node-current editor) ""))]
    (if (str/blank? selected)
      applied
      (let [next-node-names
            (if (= mode :edit)
              (upsert-at-index (:node-names applied) node-idx selected)
              (conj (vec (or (:node-names applied) [])) selected))]
        (assoc applied :node-names next-node-names)))))

(defn apply-latency-editor
  [applied editor]
  (let [latency-min (parse-number-input (:latency-min editor))
        latency-max (parse-number-input (:latency-max editor))
        next-latency (cond-> {}
                       (some? latency-min) (assoc :min latency-min)
                       (some? latency-max) (assoc :max latency-max))]
    (if (seq next-latency)
      (assoc applied :latency-ms next-latency)
      (dissoc applied :latency-ms))))

(defn apply-error-editor
  [applied editor]
  (case (:error-filter editor)
    "errors-only" (assoc applied :has-error? true)
    "no-errors" (assoc applied :has-error? false)
    (dissoc applied :has-error?)))

(defn apply-source-editor
  [applied editor]
  (let [source (:source editor)
        source-not? (boolean (:source-not? editor))]
    (if (= source "all")
      (-> applied
          (dissoc :source)
          (dissoc :source-not?))
      (cond-> (assoc applied :source source)
        source-not?
        (assoc :source-not? true)
        (not source-not?)
        (dissoc :source-not?)))))

(defn apply-feedback-editor
  [applied editor {:keys [mode feedback-idx]}]
  (if-let [next-metric (feedback-editor->metric editor)]
    (let [next-feedback-metrics
          (if (= mode :edit)
            (upsert-at-index (:feedback-metrics applied) feedback-idx next-metric)
            (conj (vec (or (:feedback-metrics applied) [])) next-metric))]
      (assoc applied :feedback-metrics next-feedback-metrics))
    applied))

(defn apply-editor-to-filters
  [applied open-editor editor]
  (case (:filter-type open-editor)
    :node (apply-node-editor applied editor open-editor)
    :latency (apply-latency-editor applied editor)
    :error (apply-error-editor applied editor)
    :source (apply-source-editor applied editor)
    :feedback (apply-feedback-editor applied editor open-editor)
    applied))

(defn clear-type-from-applied
  [applied filter-type]
  (case filter-type
    :node (dissoc applied :node-names)
    :latency (dissoc applied :latency-ms)
    :error (dissoc applied :has-error?)
    :source (-> applied
                (dissoc :source)
                (dissoc :source-not?))
    :feedback (dissoc applied :feedback-metrics)
    applied))

(defn chip->open-editor
  [chip]
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
         (let [comparator-label (keyword-like->string (or comparator :<=))
               source-label (keyword-like->string source)
               display-source (when (and source-label (not= source-label "any"))
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

;; =============================================================================
;; EVENTS (re-frame)
;; =============================================================================

(rf/reg-event-db ::init
  (fn [db [_ module-id agent-name filters-encoded]]
    (assoc-in db
              (panel-k module-id agent-name)
              {:applied (applied-filters-from-url filters-encoded)
               :open-editor nil
               :editor nil})))

(rf/reg-event-db ::update-editor
  (fn [db [_ module-id agent-name update-fn]]
    (update-in db (conj (panel-k module-id agent-name) :editor :value)
               (fn [v] (update-fn (or v {}))))))

(rf/reg-event-db ::add-type
  (fn [db [_ module-id agent-name filter-type]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          applied (:applied panel)
          open-editor (case filter-type
                        :node {:chip-id "new-node" :filter-type :node :mode :new}
                        :feedback {:chip-id "new-feedback" :filter-type :feedback :mode :new}
                        {:chip-id (str "singleton-" (name filter-type))
                         :filter-type filter-type
                         :mode :edit})
          editor-value (editor-value-for-type filter-type applied open-editor)]
      (assoc-in db pk
                (merge panel
                       {:open-editor open-editor
                        :editor {:value editor-value}})))))

(rf/reg-event-db ::apply
  (fn [db [_ module-id agent-name]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          open-editor (:open-editor panel)
          applied (:applied panel)
          editor-value (get-in panel [:editor :value])]
      (if open-editor
        (assoc-in db pk
                  (merge panel
                         {:applied (apply-editor-to-filters applied open-editor editor-value)
                          :open-editor nil
                          :editor nil}))
        db))))

(rf/reg-event-db ::clear-type
  (fn [db [_ module-id agent-name filter-type]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
          open-editor (:open-editor panel)
          close-editor? (= filter-type (:filter-type open-editor))]
      (assoc-in db pk
                (merge panel
                       {:applied (clear-type-from-applied (:applied panel) filter-type)
                        :open-editor (when-not close-editor? open-editor)
                        :editor (when-not close-editor? (:editor panel))})))))

(rf/reg-event-db ::open-chip
  (fn [db [_ module-id agent-name chip]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))
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
                          :editor {:value (editor-value-for-type (:filter-type chip)
                                                                 (:applied panel)
                                                                 chip)}}))))))

(rf/reg-event-fx ::remove-chip
  (fn [{:keys [db]} [_ module-id agent-name {:keys [filter-type node-idx feedback-idx]}]]
    (let [pk (panel-k module-id agent-name)
          panel (merge panel-default-state (get-in db pk))]
      (cond
        (or (= filter-type :node) (= filter-type "node"))
        {:db (assoc-in db pk
                       (update panel :applied
                               (fn [applied]
                                 (let [next-node-names (remove-at-index (:node-names applied) node-idx)]
                                   (if (seq next-node-names)
                                     (assoc applied :node-names next-node-names)
                                     (dissoc applied :node-names))))))}

        (or (= filter-type :feedback) (= filter-type "feedback"))
        {:db (assoc-in db pk
                       (update panel :applied
                               (fn [applied]
                                 (let [next-feedback (remove-at-index (:feedback-metrics applied) feedback-idx)]
                                   (if (seq next-feedback)
                                     (assoc applied :feedback-metrics next-feedback)
                                     (dissoc applied :feedback-metrics))))))}

        (or (= filter-type :source) (= filter-type "source"))
        {:dispatch [::clear-type module-id agent-name :source]}

        (or (= filter-type :latency) (= filter-type "latency"))
        {:dispatch [::clear-type module-id agent-name :latency]}

        (or (= filter-type :error) (= filter-type "error"))
        {:dispatch [::clear-type module-id agent-name :error]}

        :else nil))))

;; =============================================================================
;; COMPONENTS
;; =============================================================================

(defui filter-bar [{:keys [module-id agent-name node-options feedback-metric-options-by-name
                           feedback-metric-option-names]}]
  (let [panel (or (use-subscribe [::panel module-id agent-name]) {})
        applied (or (:applied panel) default-applied-filters)
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
    ($ :div.bg-white.rounded-md.border.border-gray-200.p-4.shadow-sm
       ($ :div.flex.flex-wrap.items-center.gap-2
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
                 ($ :span.text-blue-500 description)
                 ($ :span.text-blue-400.hover:text-blue-700.cursor-pointer
                    {:onClick (fn [e]
                                (.stopPropagation e)
                                (remove-chip! chip))}
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
              ($ :div.space-y-2
                 (if (seq node-options)
                   ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                      {:value (:node-current editor)
                       :data-testid "invocations-filter-node-select"
                       :onChange #(update-editor! (fn [prev] (assoc prev :node-current (.. % -target -value))))}
                      ($ :option {:value ""} "Select node")
                      (for [node-name node-options]
                        ($ :option {:key node-name :value node-name} node-name)))
                   ($ :div.text-xs.text-gray-500 "No nodes available"))
                 ($ :div.text-xs.text-gray-500 "This filter adds one required node condition."))

              :latency
              ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                 ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                    {:type "number" :data-testid "invocations-filter-latency-min"
                     :placeholder "Latency min (ms)" :value (:latency-min editor)
                     :onChange #(update-editor! (fn [prev] (assoc prev :latency-min (.. % -target -value))))})
                 ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                    {:type "number" :data-testid "invocations-filter-latency-max"
                     :placeholder "Latency max (ms)" :value (:latency-max editor)
                     :onChange #(update-editor! (fn [prev] (assoc prev :latency-max (.. % -target -value))))}))

              :error
              ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                 {:value (:error-filter editor)
                  :data-testid "invocations-filter-error-select"
                  :onChange #(update-editor! (fn [prev] (assoc prev :error-filter (.. % -target -value))))}
                 ($ :option {:value "all"} "All results")
                 ($ :option {:value "errors-only"} "Errors only")
                 ($ :option {:value "no-errors"} "No errors"))

              :source
              ($ :div.space-y-2
                 ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                    {:value (:source editor)
                     :data-testid "invocations-filter-source-select"
                     :onChange #(update-editor! (fn [prev] (assoc prev :source (.. % -target -value))))}
                    ($ :option {:value "all"} "All sources")
                    ($ :option {:value "API"} "API")
                    ($ :option {:value "MANUAL"} "Manual")
                    ($ :option {:value "EXPERIMENT"} "Experiment"))
                 ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700
                    ($ :input.h-4.w-4.border.border-gray-300.rounded
                       {:type "checkbox" :data-testid "invocations-filter-source-not"
                        :checked (boolean (:source-not? editor))
                        :disabled (= "all" (:source editor))
                        :onChange #(update-editor! (fn [prev] (assoc prev :source-not? (.. % -target -checked))))})
                    ($ :span "Not selected source"))
                 ($ :div.text-xs.text-gray-500
                    "When enabled, returns invokes from all source types except the selected one."))

              :feedback
              (let [selected-metric-name (:metric-name editor)
                    selected-metric-option (get feedback-metric-options-by-name selected-metric-name)
                    metric-categories (let [from-options (when selected-metric-option
                                                           (metric-item->categories selected-metric-option))]
                                        (if (seq from-options)
                                          from-options
                                          (vec (or (:categories editor) []))))
                    match-any? (boolean (:match-any-value? editor))
                    form-class (when match-any? "opacity-60 pointer-events-none")]
                ($ :div.space-y-2
                   ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                      ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                         {:value selected-metric-name
                          :data-testid "invocations-filter-feedback-metric"
                          :onChange #(let [selected-name (.. % -target -value)
                                           metric-option (get feedback-metric-options-by-name selected-name)
                                           next-type (if metric-option (metric-item->type metric-option) "numeric")
                                           next-categories (if metric-option (metric-item->categories metric-option) [])]
                                       (update-editor! (fn [prev]
                                                         (assoc prev
                                                                :metric-name selected-name
                                                                :metric-type next-type
                                                                :match-any-value? false
                                                                :allowed-values []
                                                                :categories next-categories
                                                                :comparator "<="
                                                                :value ""
                                                                :source (or (:source prev) "any")))))}
                         ($ :option {:value ""} "Select metric")
                         (for [[idx metric-name] (map-indexed vector (or feedback-metric-option-names []))]
                           ($ :option {:key (str "feedback-metric-option-" idx "-" metric-name)
                                       :value metric-name}
                              metric-name)))
                      ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                         {:value (:source editor)
                          :data-testid "invocations-filter-feedback-source"
                          :onChange #(update-editor! (fn [prev]
                                                       (assoc prev :source (.. % -target -value))))}
                         ($ :option {:value "any"} "Any source")
                         ($ :option {:value "human"} "Human")
                         ($ :option {:value "non-human"} "Non-human")))
                   ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                      ($ :input.h-4.w-4.border.border-gray-300.rounded
                         {:type "checkbox" :data-testid "invocations-filter-feedback-any-value"
                          :checked match-any?
                          :onChange #(update-editor! (fn [prev]
                                                       (update prev :match-any-value? not)))})
                      ($ :span "Match any value (metric exists)"))
                   ($ :div {:className (str "space-y-2 " (or form-class ""))}
                      (when match-any?
                        ($ :div.text-xs.text-gray-600
                           "Matches any invocation that has this metric, regardless of metric value."))
                      (if (= "categorical" (:metric-type editor))
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
                                         :checked (boolean (some (fn [v] (= v cat-value))
                                                                 (:allowed-values editor)))
                                         :onChange (fn [e]
                                                     (let [checked? (.. e -target -checked)]
                                                       (update-editor! (fn [prev]
                                                                         (s/transform
                                                                          [:allowed-values]
                                                                          (s/terminal (fn [vals]
                                                                                        (let [curr (vec (or vals []))]
                                                                                          (if checked?
                                                                                            (vec (distinct (conj curr cat-value)))
                                                                                            (vec (remove (fn [v] (= v cat-value)) curr))))))
                                                                          prev)))))})
                                     ($ :span cat-value))))
                             ($ :div.text-xs.text-gray-500 "No categories available for this metric.")))
                        ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                           ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                              {:value (:comparator editor)
                               :data-testid "invocations-filter-feedback-comparator"
                               :disabled match-any?
                               :onChange #(update-editor! (fn [prev]
                                                            (assoc prev :comparator (.. % -target -value))))}
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
                               :value (:value editor)
                               :onChange #(update-editor! (fn [prev]
                                                            (assoc prev :value (.. % -target -value))))})))
                      ($ :div.text-xs.text-gray-500 "This filter adds one feedback condition."))))

              ($ :div.text-sm.text-gray-500 "Unknown filter")))))))
