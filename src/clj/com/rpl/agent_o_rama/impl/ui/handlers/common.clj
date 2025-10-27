(ns com.rpl.agent-o-rama.impl.ui.handlers.common
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.json-serialize :as jser]
   [clojure.walk :as walk]
   [clojure.string :as str])
  (:import
   [java.net URLEncoder URLDecoder]
   [java.util UUID]))

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
     (cond (satisfies? jser/JSONFreeze item)
           (jser/json-freeze*-with-type item)

           (instance? Throwable item)
           (Throwable->map item)

           :else
           item))
   data))

(comment
  (def m (new dev.langchain4j.data.message.SystemMessage "test"))
  (->ui-serializable m)
  ; {"text" "test", "_aor-type" "dev.langchain4j.data.message.SystemMessage"}
  (->ui-serializable (into-array [m]))
  ; #object["[Ldev.langchain4j.data.message.SystemMessage;" 0x1f52c9ee "[Ldev.langchain4j.data.message.SystemMessage;@1f52c9ee"]
  )

(defn from-ui-serializable
  [data]
  (walk/postwalk
   jser/json-thaw*
   data))

(defn parse-url-pair [^String s]
  (let [first-dash-idx (.indexOf s "-")]
    (if (= first-dash-idx -1)
      (throw (ex-info "Invalid URL pair" {:url-pair s}))
      (let [task-id-str (.substring s 0 first-dash-idx)
            agent-id-str (.substring s (inc first-dash-idx))]
        [(parse-long task-id-str) (java.util.UUID/fromString agent-id-str)]))))

(defn preprocess-event-msg
  "Cleans, parses, and enriches an incoming Sente event message."
  [{:keys [?data] :as ev-msg}]
  (if-not (map? ?data)
    ev-msg ; Return original message if there's no data map to process
    (let [thawed-data (from-ui-serializable ?data)

          ;; --- The rest of the function operates on thawed-data ---
          decoded-module-id (when-let [mid (:module-id thawed-data)]
                              (url-decode mid))
          decoded-agent-name (when-let [aname (:agent-name thawed-data)]
                               (url-decode aname))

          ;; --- Parse String Identifiers into Rich Types ---
          parsed-dataset-id (when-let [did (:dataset-id thawed-data)]
                              (if (and (string? did) (not (str/blank? did)))
                                (UUID/fromString did)
                                did))
          parsed-experiment-id (when-let [eid (:experiment-id thawed-data)]
                                 (if (string? eid) (UUID/fromString eid) eid))
          parsed-invoke-pair (when-let [iid (:invoke-id thawed-data)]
                               (if (string? iid) (parse-url-pair iid) iid))

          ;; --- Fetch Common Contextual Objects ---
          manager (when decoded-module-id (get-manager decoded-module-id))
          client (when (and manager decoded-agent-name)
                   (get-client decoded-module-id decoded-agent-name))

          ;; --- Build the new, enriched data map ---
          enriched-data (cond-> thawed-data
                          decoded-module-id (assoc :decoded-module-id decoded-module-id)
                          decoded-agent-name (assoc :decoded-agent-name decoded-agent-name)
                          parsed-dataset-id (assoc :dataset-id parsed-dataset-id)
                          parsed-experiment-id (assoc :experiment-id parsed-experiment-id)
                          parsed-invoke-pair (assoc :invoke-pair parsed-invoke-pair) ; Store as a new key
                          manager (assoc :manager manager)
                          client (assoc :client client))]

      ;; Return the event message with the enriched data map
      (assoc ev-msg :?data enriched-data))))
