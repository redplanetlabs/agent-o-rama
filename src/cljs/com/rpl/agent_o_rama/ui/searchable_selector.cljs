(ns com.rpl.agent-o-rama.ui.searchable-selector
  "Unified searchable dropdown selector component.
   
   Can be used for single-select or multi-select scenarios.
   Handles search, keyboard navigation, loading states, and error states."
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]
   ["use-debounce" :refer [useDebounce]]
   ["@heroicons/react/24/outline" :refer [XMarkIcon MagnifyingGlassIcon]]
   ["react-dom" :refer [createPortal]]))

(defui SearchableSelector
  "A unified searchable dropdown selector.
   
   Props:
   - :module-id - The module ID for queries
   - :value - Current selected value (can be single value or vector for multi-select)
   - :on-change - Callback fn [new-value]
   - :sente-event-fn - Function [module-id search-string] => sente event vector
   - :items-key - Keyword to extract items from query result (e.g. :datasets, :items)
   - :item-id-fn - Function to extract ID from item (e.g. :dataset-id, :name)
   - :item-label-fn - Function to extract display label from item (e.g. :name)
   - :item-sublabel-fn - Optional function to extract sublabel (e.g. :description)
   - :placeholder - Placeholder text for search input
   - :label - Label text to display above selector
   - :required? - Whether field is required
   - :hide-label? - If true, hides the label
   - :error - Error message to display
   - :disabled? - Whether selector is disabled
   - :multi-select? - If true, allows multiple selections
   - :with-icon? - If true, shows search icon in input
   - :data-testid - Test ID for the component"
  [{:keys [module-id value on-change sente-event-fn items-key item-id-fn item-label-fn
           item-sublabel-fn placeholder label required? hide-label? error disabled?
           multi-select? with-icon? data-testid]
    :or {placeholder "Type to search..."
         label "Select"
         items-key :items
         item-id-fn :name
         item-label-fn :name
         multi-select? false
         with-icon? false}}]

  (let [[search-term set-search-term!] (uix/use-state "")
        [debounced-search] (useDebounce search-term 300)
        [is-open? set-open!] (uix/use-state false)
        [highlighted-idx set-highlighted-idx!] (uix/use-state 0)
        input-ref (uix/use-ref nil)
        container-ref (uix/use-ref nil)
        [dropdown-pos set-dropdown-pos!] (uix/use-state nil)

        ;; Query items
        {:keys [data loading? error query-error refetch]}
        (queries/use-sente-query
         {:query-key [:searchable-selector module-id (str items-key) debounced-search]
          :sente-event (sente-event-fn module-id debounced-search)
          :enabled? true})

        items (or (get data items-key) [])

        ;; Handle both single and multi-select values
        selected-values (if multi-select?
                          (set value)
                          #{value})

        ;; Find selected items
        selected-items (filter #(selected-values (item-id-fn %)) items)

        ;; Display value
        display-value (cond
                        (and (not multi-select?) (seq selected-items))
                        (item-label-fn (first selected-items))

                        (not (str/blank? search-term))
                        search-term

                        :else "")

        input-classes (str "w-full p-2 border rounded-md text-sm transition-colors "
                           (if with-icon? "pl-10 " "")
                           (if error
                             "border-red-300 focus:ring-red-500 focus:border-red-500"
                             "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))

        ;; Event handlers
        handle-select (fn [item]
                        (let [item-id (item-id-fn item)]
                          (if multi-select?
                           ;; Multi-select: toggle item in set
                            (if (selected-values item-id)
                              (on-change (vec (disj selected-values item-id)) {:item item})
                              (on-change (vec (conj selected-values item-id)) {:item item}))
                           ;; Single-select: set value and close
                            (do
                              (on-change item-id {:item item})
                              (set-search-term! (item-label-fn item))
                              (set-open! false)))))

        handle-clear (fn []
                       (on-change (if multi-select? [] nil))
                       (set-search-term! "")
                       (set-open! false))

        handle-input-change (fn [e]
                              (let [v (.. e -target -value)]
                                (set-search-term! v)
                                (set-open! true)
                                (set-highlighted-idx! 0)))

        handle-input-focus (fn []
                             (set-open! true)
                             ;; Clear search term so user can type fresh
                             (set-search-term! ""))

        handle-input-blur (fn []
                           ;; Delay to allow click on dropdown item
                            (js/setTimeout #(set-open! false) 200))

        handle-keydown (fn [e]
                         (when is-open?
                           (case (.-key e)
                             "ArrowDown" (do (.preventDefault e)
                                             (set-highlighted-idx!
                                              #(min (dec (count items)) (inc %))))
                             "ArrowUp" (do (.preventDefault e)
                                           (set-highlighted-idx!
                                            #(max 0 (dec %))))
                             "Enter" (do (.preventDefault e)
                                         (when (< highlighted-idx (count items))
                                           (handle-select (nth items highlighted-idx))))
                             "Escape" (do (.preventDefault e)
                                          (set-open! false))
                             nil)))]

    ;; Update search term when value changes externally (single-select only)
    (uix/use-effect
     (fn []
       (when (and (not multi-select?) value (seq selected-items))
         (let [label (item-label-fn (first selected-items))]
           (when (not= search-term label)
             (set-search-term! label))))
       js/undefined)
     [value selected-items multi-select?])

    ;; Refetch when dropdown opens
    (uix/use-effect
     (fn []
       (when is-open?
         (refetch))
       js/undefined)
     [is-open? refetch])

    ;; Calculate dropdown position when it opens
    (uix/use-effect
     (fn []
       (when (and is-open? @container-ref)
         (let [rect (.getBoundingClientRect @container-ref)]
           (set-dropdown-pos! {:top (+ (.-bottom rect) 4)
                               :left (.-left rect)
                               :width (.-width rect)})))
       js/undefined)
     [is-open?])

    ;; Close dropdown on any scroll (prevents stranded dropdown when modal scrolls)
    (uix/use-effect
     (fn []
       (when is-open?
         (let [handle-scroll (fn [_e] (set-open! false))]
           (.addEventListener js/document "scroll" handle-scroll true)
           (fn [] (.removeEventListener js/document "scroll" handle-scroll true))))
       js/undefined)
     [is-open?])

    ($ :div.relative
       {:ref container-ref
        :data-testid (str data-testid "-container")}
       ($ :div.space-y-1
          ;; Label
          (when-not hide-label?
            ($ :label.block.text-sm.font-medium.text-gray-700
               {:data-testid (str data-testid "-label")}
               label
               (when required? ($ :span.text-red-500.ml-1 "*"))))

          ;; Input with optional icon and clear button
          ($ :div.relative
             (when with-icon?
               ($ :div {:className "pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3"}
                  ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"})))

             ($ :input {:ref input-ref
                        :data-testid (str data-testid "-input")
                        :type "text"
                        :className input-classes
                        :value (if is-open? search-term display-value)
                        :placeholder placeholder
                        :onChange handle-input-change
                        :onFocus handle-input-focus
                        :onBlur handle-input-blur
                        :onKeyDown handle-keydown
                        :disabled disabled?})

             ;; Clear button (X) when value is selected
             (when (and (not disabled?)
                        (or (and (not multi-select?) value)
                            (and multi-select? (seq value))))
               ($ :button
                  {:type "button"
                   :data-testid (str data-testid "-clear-button")
                   :className "absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                   :onClick handle-clear
                   :onMouseDown #(.preventDefault %)}
                  ($ XMarkIcon {:className "h-4 w-4"}))))

          ;; Error message
          (when error
            ($ :p.text-sm.text-red-600.mt-1 {:data-testid (str data-testid "-error")} error)))

       ;; Dropdown list using portal
       (when (and is-open? dropdown-pos)
         (createPortal
          ($ :div {:role "listbox"
                   :aria-label (str label " search results")
                   :aria-busy loading?
                   :data-testid (str data-testid "-dropdown")
                   :className "bg-white border border-gray-300 rounded-md shadow-lg max-h-60 overflow-y-auto"
                   :style {:position "fixed"
                           :top (str (:top dropdown-pos) "px")
                           :left (str (:left dropdown-pos) "px")
                           :width (str (:width dropdown-pos) "px")
                           :z-index 999999}}

             (cond
               loading?
               ($ :div.p-4.text-center.text-gray-500.flex.items-center.justify-center
                  {:data-testid (str data-testid "-loading")}
                  ($ common/spinner {:size :medium})
                  ($ :span.ml-2 "Loading..."))

               query-error
               ($ :div.p-3.text-sm.text-red-500
                  {:data-testid (str data-testid "-error-state")}
                  "Error loading items.")

               (empty? items)
               ($ :div.p-4.text-center.text-gray-500
                  {:data-testid (str data-testid "-empty-state")}
                  "No items found")

               :else
               (for [[idx item] (map-indexed vector items)]
                 (let [item-id (item-id-fn item)
                       is-selected? (selected-values item-id)]
                   ($ :div {:key (str item-id)
                            :data-testid (str data-testid "-option-" item-id)
                            :role "option"
                            :aria-selected is-selected?
                            :className (str "p-3 cursor-pointer hover:bg-blue-50 "
                                            (when (= idx highlighted-idx) "bg-blue-100 ")
                                            (when is-selected? "bg-blue-50"))
                            :onMouseEnter #(set-highlighted-idx! idx)
                            :onClick #(handle-select item)}
                      ($ :div.flex.justify-between.items-start
                         ($ :div.flex-1
                            ($ :div.font-medium.text-sm (item-label-fn item))
                            (when item-sublabel-fn
                              (when-let [sublabel (item-sublabel-fn item)]
                                (when-not (str/blank? sublabel)
                                  ($ :div.text-xs.text-gray-500.mt-1 sublabel)))))
                         (when is-selected?
                           ($ :span.text-blue-600.ml-2 "✓"))))))))
          (.-body js/document))))))

