(ns com.rpl.agent-o-rama.ui
  (:require
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [reagent.dom :as rd]
   [reitit.core :as r]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   
   [day8.re-frame.http-fx]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]))

(def app-db  (reagent/atom {}))

(re-frame/reg-fx :push-state
                 (fn [route]
                   (apply rfe/push-state route)))


(re-frame/reg-event-db
 ::initialize-db
 (fn [db _]
   (if db
     db
     {:current-route nil})))

(re-frame/reg-event-fx
 ::push-state
 (fn [_ [_ & route]]
   {:push-state route}))


(re-frame/reg-event-db
 ::navigated

 (fn [db [_ new-match]]
   (let [old-match   (:current-route db)
         controllers (rfc/apply-controllers (:controllers old-match) new-match)]
     (assoc db :current-route (assoc new-match :controllers controllers)))))


(re-frame/reg-sub ::current-route
                  (fn [db]
                    (:current-route db)))

(defn home-page []
  [:div
   [:h1 "This is home page"]])

(defn href
  "Return relative url for given route. Url can be used in HTML links."
  ([k]
   (href k nil nil))
  ([k params]
   (href k params nil))
  ([k params query]
   (rfe/href k params query)))

(def routes
  ["/"
   [""
    {:name      ::home
     :view      home-page
     :link-text "Home"
     :controllers
     [{;; Do whatever initialization needed for home page
       ;; I.e (re-frame/dispatch [::events/load-something-with-ajax])
       :start (fn [& params](js/console.log "Entering home page"))
       ;; Teardown can be done here.
       :stop  (fn [& params] (js/console.log "Leaving home page"))}]}]
   agents/routes])

(defn on-navigate [new-match]
  (when new-match
    (re-frame/dispatch [::navigated new-match])))

(def router
  (rf/router
    routes
    {:data {:coercion rss/coercion}}))

(defn init-routes! []
  (js/console.log "initializing routes")
  (rfe/start!
    router
    on-navigate
    {:use-fragment true}))

(defn nav [{:keys [router current-route]}]
  [:ul
   (for [route-name (r/route-names router)
         :let       [route (r/match-by-name router route-name)
                     text (-> route :data :link-text)]]
     [:li {:key route-name}
      (when (= route-name (-> current-route :data :name))
        "> ")

      ;; only a couple routes are clickable directly from nav (no path parameters)
      (cond
        (= ::home route-name)
        [:a {:href (href route-name)} text]
        
        (= ::agents/index route-name)
        [:a {:href (href route-name)} text]
        
        :else
        [:span text])])])

(defn router-component [router]
  (let [current-route @(re-frame/subscribe [::current-route])]
    [:div
     [nav {:router router :current-route current-route}]
     (when current-route
       [(-> current-route :data :view)])]))


(defn init []
  (re-frame/clear-subscription-cache!)
  (re-frame/dispatch-sync [::initialize-db])
  (init-routes!)

  (rd/render [router-component router] (.getElementById js/document "root")))
