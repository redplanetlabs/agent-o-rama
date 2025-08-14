(ns com.rpl.agent-o-rama.ui.common
  (:require [cognitect.transit :as t]
            [clojure.string :as str]
            [uix.core :as uix]))

(defn url-decode [s] (str/replace s #"::" "/"))

(def reader (t/reader :json))
(def writer (t/writer :json))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))

(defn fetch [url]
  (.then (js/fetch url #js {:headers #js {:Accept "application/transit+json"}})
         (fn [response] (.then (.text response) (fn [text] (t/read reader text))))))

(defn post [url data]
  (.then (js/fetch url #js {:method "POST"
                            :headers #js {:Accept "application/transit+json"
                                          :Content-Type "application/transit+json"}
                            :body (t/write writer data)})
         (fn [response] (.then (.text response) (fn [text] (t/read reader text))))))

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