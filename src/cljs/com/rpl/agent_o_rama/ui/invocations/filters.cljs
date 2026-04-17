(ns com.rpl.agent-o-rama.ui.invocations.filters
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.specter :as s]))

;; =============================================================================
;; RE-FRAME DB PATH  [:invocations-filters module-id agent-name]
;; =============================================================================

;; =============================================================================
;; DEFAULTS & CONSTANTS
;; =============================================================================

(def default-draft-filters
  {:node-names []
   :node-current ""
   :latency-min ""
   :latency-max ""
   :error-filter "all"
   :source "EXPERIMENT"
   :source-not? true
   :feedback-current {:metric-name ""
                       :metric-type "numeric"
                       :match-any-value? false
                       :allowed-values []
                       :comparator "<="
                       :value ""
                       :source "any"}
   :feedback-metrics [{:metric-name ""
                       :metric-type "numeric"
                       :match-any-value? false
                       :allowed-values []
                       :comparator "<="
                       :value ""
                       :source "any"}]})

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

(rf/reg-sub ::panel
            (fn [db [_ module-id agent-name]]
              (merge {:draft default-draft-filters
                      :applied default-applied-filters
                      :open-editor nil
                      :active-filter-types []}
                     (get-in db (panel-k module-id agent-name)))))

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

(defn normalize-applied-filters [filters]
  (let [raw-filters (or filters {})
        feedback-metrics
        (->> (or (:feedback-metrics raw-filters) [])
             (keep (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                     (let [metric-name (str/trim (str (or metric-name "")))
                           normalized-type (keyword (name (or metric-type :numeric)))
                           match-any-value? (boolean match-any-value?)
                           value (str/trim (str (or value "")))
                           normalized-allowed-values (->> (or allowed-values [])
                                                          (map str)
                                                          (map str/trim)
                                                          (remove str/blank?)
                                                          vec)]
                       (when (not (str/blank? metric-name))
                         (cond
                           match-any-value?
                           (cond-> {:metric-name metric-name
                                    :metric-type normalized-type
                                    :match-any-value? true}
                             (some? source)
                             (assoc :source (keyword (name source))))

                           (= normalized-type :categorical)
                           (when (seq normalized-allowed-values)
                             (cond-> {:metric-name metric-name
                                      :metric-type :categorical
                                      :allowed-values normalized-allowed-values}
                               (some? source)
                               (assoc :source (keyword (name source)))))

                           (not (str/blank? value))
                           (cond-> {:metric-name metric-name
                                    :metric-type :numeric
                                    :comparator (keyword (name (or comparator :<=)))
                                    :value value}
                             (some? source)
                             (assoc :source (keyword (name source)))))))))
             vec)
        node-names
        (->> (or (:node-names raw-filters) [])
             (map str)
             (map str/trim)
             (remove str/blank?)
             vec)]
    (cond-> {}
      (seq node-names)
      (#(s/setval [:node-names] node-names %))

      (some? (s/select-one [:latency-ms :min] raw-filters))
      (#(s/setval [:latency-ms :min] (s/select-one [:latency-ms :min] raw-filters) %))

      (some? (s/select-one [:latency-ms :max] raw-filters))
      (#(s/setval [:latency-ms :max] (s/select-one [:latency-ms :max] raw-filters) %))

      (contains? raw-filters :has-error?)
      (#(s/setval [:has-error?] (:has-error? raw-filters) %))

      (some? (:source raw-filters))
      (#(s/setval [:source] (str (:source raw-filters)) %))

      (:source-not? raw-filters)
      (#(s/setval [:source-not?] true %))

      (seq feedback-metrics)
      (#(s/setval [:feedback-metrics] feedback-metrics %)))))

(defn applied->draft-filters [applied]
  (let [filters (or applied {})]
    (-> default-draft-filters
        (s/setval [:node-names] (vec (or (:node-names filters) [])))
        (s/setval [:node-current] "")
        (s/setval [:latency-min] (let [v (s/select-one [:latency-ms :min] filters)]
                                   (if (some? v) (str v) "")))
        (s/setval [:latency-max] (let [v (s/select-one [:latency-ms :max] filters)]
                                   (if (some? v) (str v) "")))
        (s/setval [:error-filter] (cond
                                    (true? (:has-error? filters)) "errors-only"
                                    (false? (:has-error? filters)) "no-errors"
                                    :else "all"))
        (s/setval [:source] (or (:source filters) "all"))
        (s/setval [:source-not?] (boolean (:source-not? filters)))
        (s/setval [:feedback-current] {:metric-name ""
                                       :metric-type "numeric"
                                       :match-any-value? false
                                       :allowed-values []
                                       :comparator "<="
                                       :value ""
                                       :source "any"})
        (s/setval [:feedback-metrics]
                  (if (seq (:feedback-metrics filters))
                    (mapv (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                            {:metric-name (or metric-name "")
                             :metric-type (name (or metric-type :numeric))
                             :match-any-value? (boolean match-any-value?)
                             :allowed-values (vec (or allowed-values []))
                             :comparator (name (or comparator :<=))
                             :value (if (nil? value) "" (str value))
                             :source (if (some? source) (name source) "any")})
                          (:feedback-metrics filters))
                    [{:metric-name ""
                      :metric-type "numeric"
                      :match-any-value? false
                      :allowed-values []
                      :comparator "<="
                      :value ""
                      :source "any"}])))))

(defn derive-active-filter-types [applied]
  (let [filters (or applied {})]
    (vec
     (remove nil?
             [(when (seq (:node-names filters)) :node)
              (when (or (some? (s/select-one [:latency-ms :min] filters))
                        (some? (s/select-one [:latency-ms :max] filters)))
                :latency)
              (when (contains? filters :has-error?) :error)
              (when (or (some? (:source filters))
                        (:source-not? filters))
                :source)
              (when (seq (:feedback-metrics filters)) :feedback)]))))

(defn parse-filter-value [s]
  (let [trimmed (str/trim (or s ""))
        parsed (js/Number trimmed)]
    (if (or (str/blank? trimmed) (js/isNaN parsed))
      trimmed
      parsed)))

(defn build-filter-map [f]
  (let [latency-min (parse-filter-value (:latency-min f))
        latency-max (parse-filter-value (:latency-max f))
        node-names (->> (or (:node-names f) [])
                        (map str/trim)
                        (remove str/blank?)
                        vec)
        feedback-metrics
        (->> (or (:feedback-metrics f) [])
             (keep (fn [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]}]
                     (let [metric-name (str/trim (or metric-name ""))
                           feedback-type (keyword (or metric-type "numeric"))
                           match-any-value? (boolean match-any-value?)
                           value (str/trim (or value ""))
                           allowed-values (->> (or allowed-values [])
                                               (map str)
                                               (map str/trim)
                                               (remove str/blank?)
                                               vec)]
                       (when (not (str/blank? metric-name))
                         (cond
                           match-any-value?
                           (cond-> {:metric-name metric-name
                                    :metric-type feedback-type
                                    :match-any-value? true}
                             (not= "any" source)
                             (assoc :source (keyword source)))

                           (= feedback-type :categorical)
                           (when (seq allowed-values)
                             (cond-> {:metric-name metric-name
                                      :metric-type :categorical
                                      :allowed-values allowed-values}
                               (not= "any" source)
                               (assoc :source (keyword source))))

                           (not (str/blank? value))
                           (cond-> {:metric-name metric-name
                                    :metric-type :numeric
                                    :comparator (keyword comparator)
                                    :value value}
                             (not= "any" source)
                             (assoc :source (keyword source))))))))
             vec)]
    (cond-> {}
      (seq node-names)
      (#(s/setval [:node-names] node-names %))

      (number? latency-min)
      (#(s/setval [:latency-ms :min] latency-min %))

      (number? latency-max)
      (#(s/setval [:latency-ms :max] latency-max %))

      (= "errors-only" (:error-filter f))
      (#(s/setval [:has-error?] true %))

      (= "no-errors" (:error-filter f))
      (#(s/setval [:has-error?] false %))

      (not= "all" (:source f))
      (#(s/setval [:source] (:source f) %))

      (and (not= "all" (:source f))
           (:source-not? f))
      (#(s/setval [:source-not?] true %))

      (seq feedback-metrics)
      (#(s/setval [:feedback-metrics] feedback-metrics %)))))

(defn applied-filters-from-url [encoded]
  (if (nil? encoded)
    default-applied-filters
    (if-let [decoded (decode-filters-param encoded)]
      (normalize-applied-filters decoded)
      default-applied-filters)))

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
          :description (let [mn (s/select-one [:latency-ms :min] applied-filters)
                             mx (s/select-one [:latency-ms :max] applied-filters)]
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
         (let [comparator-label (name (or comparator :<=))
               source-label (when (some? source) (name source))
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
                   (let [applied (applied-filters-from-url filters-encoded)
                         draft (applied->draft-filters applied)
                         active-types (derive-active-filter-types applied)]
                     (assoc-in db (panel-k module-id agent-name)
                               {:draft draft
                                :applied applied
                                :open-editor nil
                                :active-filter-types active-types}))))

(rf/reg-event-db ::update-draft
                 (fn [db [_ module-id agent-name update-fn]]
                   (update-in db (conj (panel-k module-id agent-name) :draft) update-fn)))

(defn upsert-at-index [coll idx next-value]
  (let [base (vec (or coll []))]
    (if (and (integer? idx)
             (<= 0 idx)
             (< idx (count base)))
      (assoc base idx next-value)
      (conj base next-value))))

(defn apply-node-editor [draft {:keys [mode node-idx]}]
  (let [selected (str/trim (or (:node-current draft) ""))]
    (if (str/blank? selected)
      draft
      (update draft
              :node-names
              (fn [names]
                (let [base (vec (or names []))]
                  (if (= mode :edit)
                    (upsert-at-index base node-idx selected)
                    (conj base selected))))))))

(defn feedback-current->row [feedback-current]
  (let [{:keys [metric-name metric-type comparator value source allowed-values match-any-value?]} feedback-current
        metric-name (str/trim (or metric-name ""))
        feedback-type (keyword (or metric-type "numeric"))
        match-any-value? (boolean match-any-value?)
        value (str/trim (or value ""))
        allowed-values (->> (or allowed-values [])
                            (map str)
                            (map str/trim)
                            (remove str/blank?)
                            vec)
        source (or source "any")]
    (when (not (str/blank? metric-name))
      (cond
        match-any-value?
        {:metric-name metric-name
         :metric-type feedback-type
         :match-any-value? true
         :source source}

        (= feedback-type :categorical)
        (when (seq allowed-values)
          {:metric-name metric-name
           :metric-type :categorical
           :allowed-values allowed-values
           :source source})

        :else
        (when (not (str/blank? value))
          {:metric-name metric-name
           :metric-type :numeric
           :comparator (or comparator "<=")
           :value value
           :source source})))))

(defn apply-feedback-editor [draft {:keys [mode feedback-idx]}]
  (if-let [next-row (feedback-current->row (:feedback-current draft))]
    (update draft
            :feedback-metrics
            (fn [rows]
              (let [base (vec (or rows []))]
                (if (= mode :edit)
                  (upsert-at-index base feedback-idx next-row)
                  (conj base next-row)))))
    draft))

(defn apply-open-editor [draft open-editor]
  (let [{:keys [filter-type]} open-editor]
    (case filter-type
      :node (apply-node-editor draft open-editor)
      :feedback (apply-feedback-editor draft open-editor)
      ;; :source/:latency/:error edit draft directly; Apply just commits current draft values.
      draft)))

(rf/reg-event-db ::apply
                 (fn [db [_ m a]]
                   (let [pk (panel-k m a)
                         open-editor (get-in db (conj pk :open-editor))]
                     (if open-editor
                       (let [draft (get-in db (conj pk :draft))
                             next-draft (apply-open-editor draft open-editor)
                             next-applied (build-filter-map next-draft)
                             active-types (derive-active-filter-types next-applied)]
                         (assoc-in db pk (merge (get-in db pk)
                                                {:draft next-draft
                                                 :applied next-applied
                                                 :open-editor nil
                                                 :active-filter-types active-types})))
                       db))))

(rf/reg-event-db ::clear-type
                 (fn [db [_ module-id agent-name filter-type]]
                   (let [pk (panel-k module-id agent-name)
                         draft (get-in db (conj pk :draft))
                         next-draft (case filter-type
                                      :node (-> draft (s/setval [:node-names] []) (s/setval [:node-current] ""))
                                      :latency (-> draft (s/setval [:latency-min] "") (s/setval [:latency-max] ""))
                                      :error (s/setval [:error-filter] "all" draft)
                                      :source (-> draft (s/setval [:source] "all") (s/setval [:source-not?] false))
                                      :feedback (-> draft
                                                    (s/setval [:feedback-current]
                                                              {:metric-name ""
                                                               :metric-type "numeric"
                                                               :match-any-value? false
                                                               :allowed-values []
                                                               :comparator "<="
                                                               :value ""
                                                               :source "any"})
                                                    (s/setval [:feedback-metrics]
                                                              [{:metric-name ""
                                                                :metric-type "numeric"
                                                                :match-any-value? false
                                                                :allowed-values []
                                                                :comparator "<="
                                                                :value ""
                                                                :source "any"}]))
                                      draft)
                         next-applied (build-filter-map next-draft)
                         active-types (derive-active-filter-types next-applied)
                         open-editor (get-in db (conj pk :open-editor))
                         close-editor? (= filter-type (:filter-type open-editor))]
                     (assoc-in db pk (merge (get-in db pk)
                                            {:draft next-draft
                                             :applied next-applied
                                             :active-filter-types (vec (remove #(= % filter-type) (get-in db (conj pk :active-filter-types))))
                                             :open-editor (if close-editor? nil open-editor)})))))

(rf/reg-event-db ::add-type
                 (fn [db [_ module-id agent-name filter-type]]
                   (let [pk (panel-k module-id agent-name)
                         active-types (get-in db (conj pk :active-filter-types))
                         draft (get-in db (conj pk :draft))
                         next-types (if (some #(= % filter-type) (or active-types []))
                                      (or active-types [])
                                      (conj (vec (or active-types [])) filter-type))
                         next-draft (case filter-type
                                      :node (s/setval [:node-current] "" draft)
                                      :feedback (-> draft
                                                    (s/setval [:feedback-current]
                                                              {:metric-name ""
                                                               :metric-type "numeric"
                                                               :match-any-value? false
                                                               :allowed-values []
                                                               :comparator "<="
                                                               :value ""
                                                               :source "any"}))
                                      draft)
                         open-editor (case filter-type
                                       :node {:chip-id "new-node" :filter-type :node :mode :new}
                                       :feedback {:chip-id "new-feedback" :filter-type :feedback :mode :new}
                                       {:chip-id (str "singleton-" (name filter-type))
                                        :filter-type filter-type
                                        :mode :edit})]
                     (assoc-in db pk (merge (get-in db pk)
                                            {:active-filter-types next-types
                                             :draft next-draft
                                             :open-editor open-editor})))))

(rf/reg-event-db ::open-chip
                 (fn [db [_ module-id agent-name {:keys [chip-id filter-type node-name node-idx feedback-idx]}]]
                   (let [pk (panel-k module-id agent-name)
                         open-editor (get-in db (conj pk :open-editor))
                         applied (get-in db (conj pk :applied))
                         draft (get-in db (conj pk :draft))]
                     (if (= chip-id (:chip-id open-editor))
                       (assoc-in db (conj pk :open-editor) nil)
                       (let [next-draft (case filter-type
                                          :node (s/setval [:node-current] (or node-name "") draft)
                                          :feedback
                                          (let [metrics (or (:feedback-metrics applied) [])
                                                metric (or (get metrics feedback-idx)
                                                           {:metric-name "" :metric-type :numeric
                                                            :match-any-value? false :allowed-values []
                                                            :comparator :<= :value "" :source :any})
                                                metric-name (:metric-name metric)]
                                            (s/setval [:feedback-current]
                                                      {:metric-name (or metric-name "")
                                                       :metric-type (name (or (:metric-type metric) :numeric))
                                                       :match-any-value? (boolean (:match-any-value? metric))
                                                       :allowed-values (vec (or (:allowed-values metric) []))
                                                       :comparator (name (or (:comparator metric) :<=))
                                                       :value (str (or (:value metric) ""))
                                                       :source (name (or (:source metric) :any))}
                                                      draft))
                                          draft)
                             next-open {:chip-id chip-id :filter-type filter-type :mode :edit
                                        :node-idx node-idx :node-name node-name :feedback-idx feedback-idx}]
                         (assoc-in db pk (merge (get-in db pk)
                                                {:draft next-draft
                                                 :open-editor next-open})))))))

(rf/reg-event-fx ::remove-chip
                 (fn [{:keys [db]} [_ module-id agent-name {:keys [filter-type node-idx feedback-idx]}]]
                   (cond
                     (or (= filter-type :node) (= filter-type "node"))
                     (let [pk (panel-k module-id agent-name)
                           draft (get-in db (conj pk :draft))
                           remove-node (fn [names]
                                         (->> (map-indexed vector (or names []))
                                              (remove (fn [[idx _]] (= idx node-idx)))
                                              (mapv second)))
                           next-draft (s/transform [:node-names] (s/terminal remove-node) draft)
                           next-applied (build-filter-map next-draft)
                           active-types (derive-active-filter-types next-applied)]
                       {:db (assoc-in db pk (merge (get-in db pk)
                                                   {:draft next-draft
                                                    :applied next-applied
                                                    :active-filter-types active-types}))})

                     (or (= filter-type :feedback) (= filter-type "feedback"))
                     (let [pk (panel-k module-id agent-name)
                           draft (get-in db (conj pk :draft))
                           remove-feedback (fn [rows]
                                             (->> (map-indexed vector (or rows []))
                                                  (remove (fn [[idx _]] (= idx feedback-idx)))
                                                  (mapv second)))
                           next-draft (s/transform [:feedback-metrics] (s/terminal remove-feedback) draft)
                           next-applied (build-filter-map next-draft)
                           active-types (derive-active-filter-types next-applied)]
                       {:db (assoc-in db pk (merge (get-in db pk)
                                                   {:draft next-draft
                                                    :applied next-applied
                                                    :active-filter-types active-types}))})

                     (or (= filter-type :source) (= filter-type "source"))
                     {:dispatch [::clear-type module-id agent-name :source]}

                     (or (= filter-type :latency) (= filter-type "latency"))
                     {:dispatch [::clear-type module-id agent-name :latency]}

                     (or (= filter-type :error) (= filter-type "error"))
                     {:dispatch [::clear-type module-id agent-name :error]}

                     :else nil)))

;; =============================================================================
;; COMPONENTS
;; =============================================================================

(defui filter-bar [{:keys [module-id agent-name node-options feedback-metric-options-by-name
                           feedback-metric-option-names]}]
  (let [panel (or (use-subscribe [::panel module-id agent-name]) {})
        draft (or (:draft panel) default-draft-filters)
        applied (or (:applied panel) default-applied-filters)
        open-editor (:open-editor panel)
        active-types (or (:active-filter-types panel) [])
        chips (active-filter-chips applied)
        add-items (map (fn [ft]
                         {:key (name ft)
                          :label (get filter-type-labels ft)
                          :disabled? (and (not (#{:node :feedback} ft))
                                          (boolean (some #(= % ft) active-types)))
                          :on-select #(rf/dispatch [::add-type module-id agent-name ft])})
                       filter-type-order)
        update-draft! #(rf/dispatch [::update-draft module-id agent-name %])
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
                      {:value (:node-current draft)
                       :data-testid "invocations-filter-node-select"
                       :onChange #(update-draft! (fn [prev] (assoc prev :node-current (.. % -target -value))))}
                      ($ :option {:value ""} "Select node")
                      (for [node-name node-options]
                        ($ :option {:key node-name :value node-name} node-name)))
                   ($ :div.text-xs.text-gray-500 "No nodes available"))
                 ($ :div.text-xs.text-gray-500 "This filter adds one required node condition."))

              :latency
              ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                 ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                    {:type "number" :data-testid "invocations-filter-latency-min"
                     :placeholder "Latency min (ms)" :value (:latency-min draft)
                     :onChange #(update-draft! (fn [prev] (assoc prev :latency-min (.. % -target -value))))})
                 ($ :input.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                    {:type "number" :data-testid "invocations-filter-latency-max"
                     :placeholder "Latency max (ms)" :value (:latency-max draft)
                     :onChange #(update-draft! (fn [prev] (assoc prev :latency-max (.. % -target -value))))}))

              :error
              ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                 {:value (:error-filter draft)
                  :data-testid "invocations-filter-error-select"
                  :onChange #(update-draft! (fn [prev] (assoc prev :error-filter (.. % -target -value))))}
                 ($ :option {:value "all"} "All results")
                 ($ :option {:value "errors-only"} "Errors only")
                 ($ :option {:value "no-errors"} "No errors"))

              :source
              ($ :div.space-y-2
                 ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                    {:value (:source draft)
                     :data-testid "invocations-filter-source-select"
                     :onChange #(update-draft! (fn [prev] (assoc prev :source (.. % -target -value))))}
                    ($ :option {:value "all"} "All sources")
                    ($ :option {:value "API"} "API")
                    ($ :option {:value "MANUAL"} "Manual")
                    ($ :option {:value "EXPERIMENT"} "Experiment"))
                 ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700
                    ($ :input.h-4.w-4.border.border-gray-300.rounded
                       {:type "checkbox" :data-testid "invocations-filter-source-not"
                        :checked (boolean (:source-not? draft))
                        :disabled (= "all" (:source draft))
                        :onChange #(update-draft! (fn [prev] (assoc prev :source-not? (.. % -target -checked))))})
                    ($ :span "Not selected source"))
                 ($ :div.text-xs.text-gray-500
                    "When enabled, returns invokes from all source types except the selected one."))

              :feedback
              ($ :div.space-y-2
                 ($ :div.grid.grid-cols-1.md:grid-cols-2.gap-2
                    ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                       {:value (s/select-one [:feedback-current :metric-name] draft)
                        :data-testid "invocations-filter-feedback-metric"
                        :onChange #(let [selected-name (.. % -target -value)
                                         metric-option (get feedback-metric-options-by-name selected-name)
                                         next-type (if metric-option (metric-item->type metric-option) "numeric")
                                         next-categories (if metric-option (metric-item->categories metric-option) [])]
                                     (update-draft! (fn [prev]
                                                      (assoc prev :feedback-current
                                                             {:metric-name selected-name
                                                              :metric-type next-type
                                                              :match-any-value? false
                                                              :allowed-values []
                                                              :categories next-categories
                                                              :comparator "<="
                                                              :value ""
                                                              :source (or (s/select-one [:feedback-current :source] prev) "any")}))))}
                       ($ :option {:value ""} "Select metric")
                       (for [[idx metric-name] (map-indexed vector (or feedback-metric-option-names []))]
                         ($ :option {:key (str "feedback-metric-option-" idx "-" metric-name)
                                     :value metric-name}
                            metric-name)))
                    ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.text-sm.bg-white
                       {:value (s/select-one [:feedback-current :source] draft)
                        :data-testid "invocations-filter-feedback-source"
                        :onChange #(update-draft! (fn [prev]
                                                    (s/setval [:feedback-current :source] (.. % -target -value) prev)))}
                       ($ :option {:value "any"} "Any source")
                       ($ :option {:value "human"} "Human")
                       ($ :option {:value "non-human"} "Non-human")))
                 ($ :label.inline-flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                    ($ :input.h-4.w-4.border.border-gray-300.rounded
                       {:type "checkbox" :data-testid "invocations-filter-feedback-any-value"
                        :checked (boolean (s/select-one [:feedback-current :match-any-value?] draft))
                        :onChange #(update-draft! (fn [prev]
                                                    (s/transform [:feedback-current :match-any-value?] (s/terminal not) prev)))})
                    ($ :span "Match any value (metric exists)"))
                 (let [match-any? (boolean (s/select-one [:feedback-current :match-any-value?] draft))
                       form-class (when match-any? "opacity-60 pointer-events-none")]
                   ($ :div {:className (str "space-y-2 " (or form-class ""))}
                      (when match-any?
                        ($ :div.text-xs.text-gray-600
                           "Matches any invocation that has this metric, regardless of metric value."))
                      (if (= "categorical" (s/select-one [:feedback-current :metric-type] draft))
                        ($ :div.space-y-2
                           ($ :div.text-xs.text-gray-600 "Select one or more categorical values:")
                           (if (seq (s/select-one [:feedback-current :categories] draft))
                             ($ :div.max-h-56.overflow-auto.border.border-gray-200.rounded-md.bg-white.p-2.space-y-1
                                (for [cat-value (s/select-one [:feedback-current :categories] draft)]
                                  ($ :label.flex.items-center.gap-2.text-sm.text-gray-700.cursor-pointer
                                     {:key cat-value}
                                     ($ :input.h-4.w-4.border.border-gray-300.rounded
                                        {:type "checkbox"
                                         :data-testid "invocations-filter-feedback-category-select"
                                         :disabled match-any?
                                         :checked (boolean (some (fn [v] (= v cat-value))
                                                                 (s/select-one [:feedback-current :allowed-values] draft)))
                                         :onChange (fn [e]
                                                     (let [checked? (.. e -target -checked)]
                                                       (update-draft! (fn [prev]
                                                                        (s/transform [:feedback-current :allowed-values]
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
                              {:value (s/select-one [:feedback-current :comparator] draft)
                               :data-testid "invocations-filter-feedback-comparator"
                               :disabled match-any?
                               :onChange #(update-draft! (fn [prev]
                                                           (s/setval [:feedback-current :comparator] (.. % -target -value) prev)))}
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
                               :value (s/select-one [:feedback-current :value] draft)
                               :onChange #(update-draft! (fn [prev]
                                                           (s/setval [:feedback-current :value] (.. % -target -value) prev)))})))
                      ($ :div.text-xs.text-gray-500 "This filter adds one feedback condition."))))

              ($ :div.text-sm.text-gray-500 "Unknown filter")))))))
