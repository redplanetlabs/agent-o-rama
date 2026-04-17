(ns com.rpl.agent-o-rama.ui.state
  "Compatibility shim: application state lives in re-frame `app-db`.
   Prefer `re-frame.core/dispatch` and `uix.re-frame/use-subscribe` with
   `::com.rpl.agent-o-rama.ui.re-frame/get-in` for new code."
  (:require
   [uix.core :as uix]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]))

(def initial-db aor-rf/default-app-db)

(def app-db rdb/app-db)

(defn app-db->window-db
  "Convert app-db into a JS-friendly structure for ad hoc browser debugging."
  [db]
  (clj->js db {:keyword-fn (fn [k] (str/replace (name k) "-" "_"))}))

(defn expose-db!
  "Expose the current re-frame app-db as window.db when debugging manually."
  []
  (aset js/window "db" (app-db->window-db @rdb/app-db)))

(defn clear-exposed-db!
  "Remove any manually exposed window.db value."
  []
  (js-delete js/window "db"))

(defn dispatch
  "Same as `re-frame.core/dispatch` (kept for incremental migration of call sites)."
  [event]
  (rf/dispatch event))

(defn use-sub
  "Subscribe to `path` in re-frame app-db (same path shape as the legacy atom)."
  [path]
  (uix/use-subscribe [::aor-rf/get-in path]))

(defn reg-event
  "Register a synchronous `re-frame` db handler for tests. Handler `(fn [db & args] -> db)`."
  [event-id handler-fn]
  (rf/reg-event-db event-id
    (fn [db event-vec]
      (let [result (apply handler-fn db (rest event-vec))]
        (or result db)))))

(defn get-db [] @rdb/app-db)

(defn reset-db!
  "Reset re-frame app-db to `initial-db`. Useful for development and tests."
  []
  (reset! rdb/app-db initial-db))

(defn debug-state
  "Print current app-db state to console. Optionally filter by path vector."
  ([]
   (js/console.log "Current app-db:" (clj->js @rdb/app-db)))
  ([path]
   (js/console.log "Value at path" path ":"
                   (clj->js (get-in @rdb/app-db path)))))
