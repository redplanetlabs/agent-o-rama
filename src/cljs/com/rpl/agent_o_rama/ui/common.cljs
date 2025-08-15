(ns com.rpl.agent-o-rama.ui.common
  (:require [cognitect.transit :as t]
            [clojure.string :as str]
            [uix.core :as uix]))

(defn url-decode [s]
  "Decode URL-encoded string using standard browser decoding"
  (try
    (js/decodeURIComponent s)
    (catch js/Error e
      (js/console.error "Failed to decode URI component:" s e)
      s))) ; Fallback to original string on error

(def reader (t/reader :json))
(def writer (t/writer :json))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

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