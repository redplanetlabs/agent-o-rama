(ns com.rpl.agent-o-rama.ui.common
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]))

(defn http-loader-view [fsm-id body]
  (reagent/with-let [state (re-frame/subscribe [::http/state fsm-id])]
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
           [:div
            "Could not load data"
            [:div
             [:button {:on-click #(re-frame/dispatch [::http/restart fsm-id])}
              "Click to try again"]]])]

        ::http/loaded
        body))))
