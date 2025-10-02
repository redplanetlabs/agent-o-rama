(ns com.rpl.agent-o-rama.ui.common
  (:require [cognitect.transit :as t]
            [clojure.string :as str]
            [uix.core :as uix :refer [defhook defui $]]))

(defn url-decode [s]
  "Decode URL-encoded string using standard browser decoding"
  (try
    (js/decodeURIComponent s)
    (catch js/Error e
      (js/console.error "Failed to decode URI component:" s e)
      s)))

(defn url-encode [s]
  (js/encodeURIComponent s))

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

(defui DropdownRow [{:keys [label selected? on-select delete-button action? icon extra-content data-testid]}]
  (let [row-classes (cn
                     "flex items-center justify-between w-full px-4 py-2 text-sm cursor-pointer hover:bg-gray-100 focus:bg-gray-100"
                     {"bg-blue-50 text-blue-700" selected?
                      "text-blue-600 hover:bg-blue-50" action?
                      "text-gray-700" (not (or selected? action?))})]
    ($ :div
       ;; Main clickable area
       ($ :div
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (when on-select (on-select)))
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