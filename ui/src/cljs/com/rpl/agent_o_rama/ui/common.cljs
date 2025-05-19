(ns com.rpl.agent-o-rama.ui.common
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]))

(defn http-loader-view [path body]
  (reagent/with-let [state (re-frame/subscribe [::http/state path])]
    (let [[primary-state secondary-state] @state]
      (case primary-state
        nil
        [:div "Nothing much happened yet"]

        ::http/loading
        [:div "Loading..."]

        ::http/error
        [:div [:h3 "An error occurred"]
         (case secondary-state
           ::http/retrying
           [:div "Please wait, trying again"]

           ::http/halted
           [:div "Could not load data"])]

        ::http/loaded
        body))))

(defn pp [x] (with-out-str (cljs.pprint/pprint x)))
