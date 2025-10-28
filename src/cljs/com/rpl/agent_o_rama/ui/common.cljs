(ns com.rpl.agent-o-rama.ui.common
  (:require
   [cognitect.transit :as t]
   [clojure.string :as str]
   [uix.core :as uix :refer [defhook defui $]]
   ["@heroicons/react/24/outline" :refer [ChevronDownIcon]]))

(defn url-decode [s]
  "Decode URL-encoded string using standard browser decoding"
  (try
    (js/decodeURIComponent s)
    (catch js/Error e
      (js/console.error "Failed to decode URI component:" s e)
      s)))

(defn url-encode [s]
  (js/encodeURIComponent s))

(defn agent-invoke->url
  "Convert an agent-invoke map to an invocation URL.
  Takes module-id, agent-name, and an agent-invoke with :task-id and :agent-invoke-id."
  [module-id agent-name agent-invoke]
  (when (and module-id agent-name agent-invoke)
    (let [task-id (:task-id agent-invoke)
          agent-invoke-id (:agent-invoke-id agent-invoke)
          invoke-id (str task-id "-" agent-invoke-id)]
      (str "/agents/" (url-encode module-id) "/agent/" (url-encode agent-name) "/invocations/" invoke-id))))

(defn coerce-uuid
  "Converts a string to a UUID if it matches UUID format, otherwise returns the original value."
  [s]
  (if (and (string? s)
           (re-matches #"^[0-9a-fA-F]{8}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{4}-?[0-9a-fA-F]{12}$" s))
    (uuid s)
    s)) ; Fallback to original string on error

(def reader (t/reader :json))
(def writer (t/writer :json))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn to-json [x]
  "Converts a ClojureScript data structure to a JSON string."
  (js/JSON.stringify (clj->js x)))

(defn pp-json [x]
  "Converts a ClojureScript data structure to a pretty-printed JSON string."
  (js/JSON.stringify (clj->js x) nil 2))

(defn format-timestamp [ms]
  (if (number? ms)
    (let [date (js/Date. ms)
          formatter (js/Intl.DateTimeFormat.
                     "en-US" ; Or use browser's locale
                     #js {:year "numeric"
                          :month "short"
                          :day "numeric"
                          :hour "2-digit"
                          :minute "2-digit"
                          :second "2-digit"
                          :hour12 false})]
      (.format formatter date))
    ""))

(defn format-relative-time [ms]
  (if (number? ms)
    (let [now (js/Date.now)
          diff (- now ms)
          seconds (js/Math.floor (/ diff 1000))
          minutes (js/Math.floor (/ seconds 60))
          hours (js/Math.floor (/ minutes 60))
          days (js/Math.floor (/ hours 24))]
      (cond
        (< seconds 60) (str seconds " seconds ago")
        (< minutes 60) (if (= minutes 1) "1 minute ago" (str minutes " minutes ago"))
        (< hours 24) (if (= hours 1) "1 hour ago" (str hours " hours ago"))
        (< days 7) (if (= days 1) "1 day ago" (str days " days ago"))
        :else (let [date (js/Date. ms)
                    formatter (js/Intl.DateTimeFormat.
                               "en-US"
                               #js {:year "numeric"
                                    :month "short"
                                    :day "numeric"})]
                (.format formatter date))))
    ""))

(defn format-duration-ms
  "Format a duration in milliseconds to a human-readable string."
  [duration-ms]
  (cond
    (< duration-ms 1000) (str duration-ms "ms")
    (< duration-ms 60000) (str (.toFixed (/ duration-ms 1000) 2) "s")
    :else (str (.toFixed (/ duration-ms 60000) 2) "m")))

(defn use-local-storage
  "Hook for localStorage functionality
  
  `key` string key for localStorage
  `initial-value` default value if key doesn't exist in localStorage"
  [key initial-value]
  (let [get-stored-value (fn []
                           (try
                             (let [item (js/localStorage.getItem key)]
                               (if (some? item)
                                 (js/JSON.parse item)
                                 initial-value))
                             (catch js/Error _
                               initial-value)))
        [stored-value set-stored-value] (uix/use-state get-stored-value)]

    ;; Update localStorage when value changes
    (uix/use-effect
     (fn []
       (try
         (js/localStorage.setItem key (js/JSON.stringify stored-value))
         (catch js/Error e
           (.error js/console "Error saving to localStorage:" e))))
     [stored-value key])

    [stored-value set-stored-value]))

(defhook use-page-visibility
  "Returns true if the document is currently visible, false otherwise.
   Updates reactively when the tab visibility changes."
  []
  (let [[is-visible set-is-visible] (uix/use-state (not (.-hidden js/document)))]
    (uix/use-effect
     (fn []
       (let [handler (fn [] (set-is-visible (not (.-hidden js/document))))]
         (.addEventListener js/document "visibilitychange" handler)
          ;; Cleanup function
         (fn []
           (.removeEventListener js/document "visibilitychange" handler))))
     [])
    is-visible))

(defn cn
  "Concatenate class names. Accepts strings, sequences, and maps of class->boolean."
  [& parts]
  (->> parts
       (mapcat (fn [p]
                 (cond
                   (string? p) [p]
                   (sequential? p) (remove str/blank? p)
                   (map? p) (for [[k v] p :when v] (name k))
                   :else [])))
       (remove str/blank?)
       (str/join " ")))

(def table-classes
  {:container "bg-white shadow sm:rounded-md overflow-x-auto"
   :table "min-w-full divide-y divide-gray-200"
   :thead "bg-gray-50"
   :th "px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider"
   :td "px-6 py-4 text-sm text-gray-900 whitespace-nowrap"
   :td-right "px-6 py-4 whitespace-nowrap text-right text-sm font-medium"})

(defui spinner [{:keys [size]}]
  (let [size-class (case size
                     :small "h-3 w-3"
                     :medium "h-4 w-4"
                     "h-4 w-4")
        classes (str size-class " text-blue-600 animate-spin")]
    ($ :svg
       {:className classes
        :viewBox "0 0 24 24"
        :fill "none"
        :xmlns "http://www.w3.org/2000/svg"}
       ($ :circle
          {:className "opacity-25"
           :cx "12" :cy "12" :r "10"
           :stroke "currentColor" :strokeWidth "4"})
       ($ :path
          {:className "opacity-75"
           :fill "currentColor"
           :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"}))))

(defui DropdownRow [{:keys [label selected? on-select delete-button action? icon extra-content data-testid disabled?]}]
  (let [row-classes (cn
                     "flex items-center justify-between w-full px-4 py-2 text-sm"
                     (if disabled?
                       "text-gray-400 cursor-not-allowed"
                       "cursor-pointer hover:bg-gray-100 focus:bg-gray-100")
                     {"bg-blue-50 text-blue-700" selected?
                      "text-blue-600 hover:bg-blue-50" (and action? (not disabled?))
                      "text-gray-700" (and (not (or selected? action?)) (not disabled?))})]
    ($ :div
       ;; Main clickable area
       ($ :div
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (when (and (not disabled?) on-select)
                        (on-select)))
           :className row-classes
           :data-testid data-testid}
          ($ :div.flex.items-center.flex-1
             (when icon ($ :div.mr-3 icon))
             ($ :span.truncate label)
             (when selected? ($ :span.ml-2.text-xs.text-blue-600 "✓")))
          ;; Delete button area (separate from main click area to avoid nesting)
          (when (and delete-button (not action?))
            ($ :div.ml-2
               {:onClick #(.stopPropagation %)} ;; Prevent triggering the row click
               delete-button)))
       ;; Extra content below the main row
       (when extra-content extra-content))))

(defui Dropdown
  "Reusable dropdown container for button-triggered menus."
  [{:keys [label disabled? display-text items loading? error? empty-content data-testid on-toggle]}]
  (let [[open? set-open] (uix/use-state false)
        close-dropdown (fn [] (set-open false))
        handle-toggle (fn [e]
                        (.stopPropagation e)
                        (when-not disabled?
                          (let [next (not open?)]
                            (set-open next)
                            (when on-toggle (on-toggle next)))))
        handle-select (fn [on-select]
                        (when (fn? on-select)
                          (on-select))
                        (close-dropdown))]

    (uix/use-effect
     (fn []
       (let [handle-click (fn [_] (when open? (close-dropdown)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [open?])

    ($ :div.relative.inline-block.text-left.w-full
       ($ :button.inline-flex.items-center.justify-between.w-full.px-3.py-2.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50.disabled:bg-gray-100.cursor-pointer
          {:type "button"
           :disabled disabled?
           :data-testid data-testid
           :onClick handle-toggle}
          ($ :span.truncate (or display-text label))
          ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))

       (when (and open? (not disabled?))
         ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
            {:onClick #(.stopPropagation %)}
            ($ :div.py-1.max-h-60.overflow-y-auto
               (cond
                 loading? ($ :div.px-4.py-2.text-sm.text-gray-500 "Loading...")
                 error? ($ :div.px-4.py-2.text-sm.text-red-500 "Error")
                 (seq items) (for [{:keys [key label selected? disabled? on-select]} items]
                               ($ DropdownRow {:key key
                                               :label label
                                               :selected? selected?
                                               :disabled? disabled?
                                               :on-select #(handle-select on-select)}))
                 empty-content empty-content
                 :else ($ :div.px-4.py-2.text-sm.text-gray-500 "No options available."))))))))

;; A simple modal component to display pre-formatted text content.
(defui ContentDetailModal [{:keys [title content]}]
  ($ :div.p-6
     ($ :h3.text-lg.font-bold.mb-4 title)
     ($ :pre.text-xs.bg-gray-50.p-3.rounded.border.overflow-auto.max-h-screen.font-mono.whitespace-pre-wrap.break-words
        content)))

;; A reusable component for displaying truncated content that expands into a modal.
(defui ExpandableContent [{:keys [content truncate-length modal-title color on-expand]
                           :or {truncate-length 150}}]
  (let [content-str (pp-json content) ; Use pretty-printed JSON string
        is-long? (> (count content-str) truncate-length)
        truncated-str (if is-long?
                        (str (subs content-str 0 truncate-length) "...")
                        content-str)
        handle-expand (fn [e]
                        (.stopPropagation e)
                        (when on-expand
                          (on-expand {:title modal-title
                                      :content content-str})))]
    ($ :div {:className (cn "relative group p-2 rounded min-w-0"
                            (when is-long? "cursor-pointer hover:bg-gray-100"))
             :onClick (when is-long? handle-expand)}
       ($ :pre {:className (cn "text-sm font-mono whitespace-pre-wrap break-words overflow-x-auto"
                               (str "text-" color "-800"))}
          truncated-str))))