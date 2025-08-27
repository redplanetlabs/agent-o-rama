(ns com.rpl.agent-o-rama.impl.ui.handlers.common
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.json-serialize :as jser]
   [clojure.walk :as walk])
  (:import
   [java.net URLEncoder URLDecoder]))

;; --- Shared Helper Functions (Moved from old agents.clj) ---

(defn url-encode [s]
  "Encode string for safe use in URLs using standard URL encoding"
  (java.net.URLEncoder/encode ^String s "UTF-8"))

(defn url-decode [s]
  "Decode URL-encoded string using standard URL decoding"
  (java.net.URLDecoder/decode ^String s "UTF-8"))

(defn get-client [module-id agent-name]
  ;; Expects already-decoded module-id and agent-name (API handlers decode them)
  (select-one [module-id
               :clients
               agent-name]
              (ui/get-object :aor-cache)))

(defn get-manager [module-id]
  (select-one [module-id :manager] (ui/get-object :aor-cache)))

(defn objects [module-id agent-name]
  (aor-types/underlying-objects (get-client module-id agent-name)))

(defn remove-implicit-nodes
  "Preprocesses the invokes-map to remove implicit nodes and rewire edges to real nodes.
   Returns a new map without implicit nodes where all references are updated."
  [invokes-map]
  (let [implicit->real
        (into {}
              (select [ALL
                       (selected? LAST (must :invoked-agg-invoke-id))
                       (view (fn [[id node]]
                               [id (:invoked-agg-invoke-id node)]))]
                      invokes-map))]
    (->> invokes-map
         (setval [ALL
                  (selected? LAST (must :invoked-agg-invoke-id))]
                 NONE)
         (transform [ALL
                     LAST
                     (must :emits)
                     ALL
                     :invoke-id]
                    #(get implicit->real % %)))))

(defn ->ui-serializable
  [data]
  (walk/postwalk
   (fn [item]
     (if (satisfies? jser/JSONFreeze item)
       (jser/json-freeze*-with-type item)
       item))
   data))

(defn from-ui-serializable
  [data]
  (walk/postwalk
   jser/json-thaw*
   data))

(defn parse-url-pair [s]
  (let [[task-id agent-id] (clojure.string/split s #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

;; --- New API Handler Wrapper ---

