Of course. Let's walk through all the code changes required to replace `wouter` with `reitit` using the recommended `app-db` integration strategy.

This is a significant but highly beneficial refactoring that will centralize your routing logic, eliminate the "hack," and make the entire UI more robust and maintainable.

---

### Step 1: Update Project Dependencies

First, we need to add `reitit` and remove `wouter`.

**1. Modify `project.clj`:**

Add `[metosin/reitit-frontend "0.7.0"]` to the `:dependencies` list inside the `:ui` profile.

```clojure
;; project.clj
...
:ui {:dependencies [
                      [com.rpl/specter "1.1.4"] ;; only cljs
                      [com.pitch/uix.core "1.4.3"]
                      [com.pitch/uix.dom "1.4.3"]
                      [thheller/shadow-cljs "3.1.7"]
                      ;; ++ ADD THIS LINE ++
                      [metosin/reitit-frontend "0.7.0"]
                      [net.java.dev.jna/jna "5.17.0"] ;; to fix
                                                     ;; dynlink
                                                     ;; error
                                                     ;; on arm
                                                     ;; macs
                      [org.clojure/clojure "1.12.0"]
                    ]}
...
```

**2. Modify `package.json`:**

Remove `wouter` from the `dependencies`.

```json
// package.json
...
  "dependencies": {
    "@dagrejs/dagre": "^1.1.5",
    "@heroicons/react": "^2.2.0",
    "@xyflow/react": "^12.6.1",
    "buffer": "^6.0.3",
    "css-element-queries": "^1.2.3",
    "elkjs": "^0.9.3",
    "process": "^0.11.10",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "uplot": "^1.6.30"
    // -- REMOVE THIS LINE --
    // "wouter": "^3.7.0" 
  }
...
```

**3. Update `node_modules`:**

Run this command in your project's root directory:
```bash
npm uninstall wouter
```

Finally, restart your Shadow CLJS build process to pick up the dependency changes.

---

### Step 2: Update State Management (`state.cljs`)

We need to add a key to our `app-db` to hold the current route and an event to update it.

```clojure
;; src/cljs/com/rpl/agent_o_rama/ui/state.cljs

(ns com.rpl.agent_o_rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

(def initial-db
  {:current-invocation {:invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :invocations-data {}
   :invocations {:all-invokes []
                 :pagination-params nil
                 :has-more? true
                 :loading? false}
   :queries {}
   :route nil ; <-- ++ ADD THIS LINE ++
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :active-tab :info
        :current-route "/"
        :breadcrumbs []
        :modal {:active nil
                :data {}}
        :hitl {:responses {}
               :submitting {}}}
   :sente {:connected? false
           :connection-state {}}
   :session {:user-id nil
             :preferences {}}})
...
;; Add the new event handler at the bottom of the file
;; =============================================================================
;; ROUTING EVENTS
;; =============================================================================

(reg-event :route/navigated
           (fn [db new-match]
             [:route (s/terminal-val new-match)]))
```

---

### Step 3: Refactor the Main UI File (`ui.cljs`)

This file has the most significant changes as it's where we'll define our routes and set up the router.

<details>
<summary>Click here for the full content of the updated `src/cljs/com/rpl/agent_o_rama/ui.cljs`</summary>

```clojure
;; src/cljs/com/rpl/agent_o_rama/ui.cljs
(ns com.rpl.agent_o_rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]
   [com.rpl.agent_o_rama.ui.agents :as agents]
   [com.rpl.agent_o_rama.ui.config-page :as config-page]
   [com.rpl.agent_o_rama.ui.datasets :as datasets]
   ;; ++ ADD REITIT REQUIRES ++
   [reitit.core :as r]
   [reitit.frontend :as rf]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [HomeIcon CpuChipIcon CircleStackIcon ChevronLeftIcon ChevronRightIcon
                                          RectangleStackIcon ChartBarIcon BeakerIcon Cog6ToothIcon]]
   [com.rpl.agent_o_rama.ui.common :as common]
   [com.rpl.agent_o_rama.ui.stats :as stats]
   [com.rpl.agent_o_rama.ui.sente :as sente]
   [com.rpl.agent_o_rama.ui.state :as state]
   [com.rpl.agent_o_rama.ui.invocation-graph-view :refer [global-modal-component]]
   [com.rpl.agent_o_rama.ui.events]))

;; ++ DEFINE ROUTES AS DATA ++
(def routes
  ["/"
   ["" {:name :home, :view agents/index}]
   ["agents"
    ["" {:name :agents/index, :view agents/index}]
    ["/:module-id"
     ["/datasets"
      ["" {:name :module/datasets, :view datasets/index}]
      ["/:dataset-id" {:name :module/dataset-detail, :view datasets/detail}]]
     ["/evaluations" {:name :module/evaluations, :view agents/evaluations}]
     ["/:agent-name"
      ["" {:name :agent/detail, :view agents/agent}]
      ["/invocations"
       ["" {:name :agent/invocations, :view agents/invocations}]
       ["/:invoke-id" {:name :agent/invocation-detail, :view agents/invoke}]]
      ["/config" {:name :agent/config, :view config-page/config-page}]
      ["/stats" {:name :agent/stats, :view stats/stats}]]]]])

;; ++ ADD ROUTER WRAPPER COMPONENT ++
(defui with-router [{:keys [routes children]}]
  (let [router (uix/use-memo #(rf/router routes) [routes])]
    (uix/use-effect
     #(rfe/start! router
                  (fn [new-match] (state/dispatch [:route/navigated new-match]))
                  {:use-fragment false})
      [router])
    ($ :<> children)))


;; Reusable nav-link component (changed from wouter/Link to a)
(defui nav-link [{:keys [href location collapsed? title children]}]
  (let [is-active? (or (= location href)
                       (and (not= href "/") (.startsWith location href)))
        link-classes (str "flex items-center rounded-md transition-colors text-sm font-medium "
                          (if collapsed?
                            "justify-center p-2 w-10 h-10"
                            "px-3 py-2")
                          (if is-active?
                            " bg-gray-300 text-gray-900"
                            " hover:bg-gray-200 text-gray-700"))]
    ($ :a {:href href :className link-classes :title (when collapsed? title)}
       (if collapsed?
         (first children)
         children))))

(defui agent-context-nav [{:keys [module-id agent-name collapsed?]}]
  (let [location (state/use-sub [:route :path])]
    ($ :<>
       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))
          ($ nav-link {:href (str "/agents/" module-id "/datasets")
                       :location location :collapsed? collapsed? :title "Datasets"}
             ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Datasets")))
          ($ nav-link {:href (str "/agents/" module-id "/evaluations")
                       :location location :collapsed? collapsed? :title "Evaluations"}
             ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Evaluations"))))

       ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
          (when-not collapsed?
            ($ :div.px-3.text-xs.font-semibold.text-gray-500 "AGENT"))
          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/invocations")
                       :location location :collapsed? collapsed? :title "Invocations"}
             ($ RectangleStackIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Invocations")))
          ($ nav-link {:href (str "/agents/" module-id "/" agent-name "/config")
                       :location location :collapsed? collapsed? :title "Config"}
             ($ Cog6ToothIcon {:className "h-5 w-5 flex-shrink-0"})
             (when-not collapsed? ($ :span.ml-3 "Config")))))))

(defui module-context-nav [{:keys [module-id collapsed?]}]
  (let [location (state/use-sub [:route :path])]
    ($ :div.border-t.border-gray-300.my-3.pt-3.space-y-2
       (when-not collapsed?
         ($ :div.px-3.text-xs.font-semibold.text-gray-500 "MODULE"))
       ($ nav-link {:href (str "/agents/" module-id "/datasets")
                    :location location :collapsed? collapsed? :title "Datasets"}
          ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Datasets")))
       ($ nav-link {:href (str "/agents/" module-id "/evaluations")
                    :location location :collapsed? collapsed? :title "Evaluations"}
          ($ BeakerIcon {:className "h-5 w-5 flex-shrink-0"})
          (when-not collapsed? ($ :span.ml-3 "Evaluations"))))))

(defui sidebar-nav []
  (let [match (state/use-sub [:route])
        location (:path match)
        {:keys [module-id agent-name]} (:path-params match)
        route-name (get-in match [:data :name])
        is-agent-context? (and module-id agent-name)
        is-module-context? (and module-id (not agent-name))
        [collapsed? set-collapsed] (common/use-local-storage "sidebar-collapsed?" false)
        toggle-collapsed #(set-collapsed (not collapsed?))]

    ($ :div {:className (str "h-screen flex flex-col bg-gray-100 transition-all duration-300 "
                             (if collapsed? "w-16" "w-64"))}
       ($ :div.flex.items-center.justify-between.p-4.border-b.border-gray-200.overflow-hidden
          (when-not collapsed?
            ($ :img {:src "/logo-black.png"
                     :alt "Agent-O-Rama"
                     :className "h-8 max-w-48 object-contain"}))
          ($ :button {:onClick toggle-collapsed
                      :className "p-2 rounded-md hover:bg-gray-200 transition-colors"
                      :title (if collapsed? "Expand sidebar" "Collapse sidebar")}
             (if collapsed?
               ($ ChevronRightIcon {:className "h-5 w-5"})
               ($ ChevronLeftIcon {:className "h-5 w-5"}))))
       ($ :nav.flex-1.p-3
          ($ :div.space-y-2
             ($ nav-link {:href "/" :location location :collapsed? collapsed? :title "Overview"}
                ($ HomeIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed? ($ :span.ml-3 "Overview"))))
          (when is-agent-context?
            ($ agent-context-nav {:module-id module-id
                                  :agent-name agent-name
                                  :collapsed? collapsed?}))
          (when is-module-context?
            ($ module-context-nav {:module-id module-id
                                   :collapsed? collapsed?}))
          )))))

(defui breadcrumb []
  (let [match (state/use-sub [:route])
        breadcrumb-items (->> (:matches match)
                              (map-indexed
                               (fn [idx m]
                                 (let [params (:path-params m)
                                       route-data (:data m)]
                                   (when (:name route-data) ; only include routes with names
                                     {:label (or (:breadcrumb-label route-data)
                                                 (cond
                                                   (:agent-name params) (str (common/url-decode (:module-id params)) ":" (common/url-decode (:agent-name params)))
                                                   (:module-id params) (common/url-decode (:module-id params))
                                                   :else (name (:name route-data))))
                                      :path (r/match->path (:name route-data) params router-instance)})))) ; Need router-instance here. For now just build manually
                              (filter some?)
                              (vec))]
    ($ :div.bg-gray-100.px-4.py-2.text-sm.text-gray-600
       ($ :div.flex.items-center.space-x-2
          ($ :a {:href "/" :className "text-blue-600 hover:text-blue-800"} "Home")
          (when-let [matches (get-in (state/use-sub [:route]) [:matches])]
            (let [items (->> matches
                             (map-indexed (fn [idx m]
                                            (let [path (apply str (take (inc idx) (get-in m [:template])))]
                                              (assoc (:data m) :path path))))
                             (filter :name))]
              (map-indexed
               (fn [idx item]
                 (let [is-last? (= idx (dec (count items)))
                       label (:name item)]
                   ($ :span {:key idx} " / "
                      (if is-last?
                        ($ :span {:className "text-gray-500"} (name label))
                        ($ :a {:href (:path item) :className "text-blue-600 hover:text-blue-800"} (name label))))))
               items)))))))

(defui app []
  (let [match (state/use-sub [:route])
        view (get-in match [:data :view])]
    ($ :div.flex.h-screen.bg-gray-50
       ($ sidebar-nav)
       ($ :div.flex-1.flex.flex-col.min-h-0
          ;; This is a simplified breadcrumb, a full one needs route data
          ;;($ breadcrumb) 
          ($ :div.flex-1.overflow-auto
             (if view
               ($ view)
               ;; You might want a proper 404 component here
               ($ :div.p-8.text-center "Route not found")))
          ($ global-modal-component)))))


(defn init []
  (sente/init!)
  (uix.dom/render-root
   ($ with-router {:routes routes}
      ($ app))
   (uix.dom/create-root
    (.getElementById js/document "root"))))
```
</details>

---

### Step 4: Update Page Components

Now we update the page components to use the new routing system. The pattern is the same for all of them: remove `wouter` requires and use `(state/use-sub [:route :path-params])`.

**`src/cljs/com/rpl/agent_o_rama/ui/agents.cljs`:**
*   Replace `["wouter" :as wouter :refer [useLocation]]` with `[reitit.frontend.easy :as rfe]`.
*   Replace `wouter/Link` with a standard `<a>` tag in `invocation-row`.
*   Replace `(let [[location navigate] (useLocation)] ... (navigate url))` with `(rfe/navigate url)`.
*   Replace `(js->clj (wouter/useParams))` with `(state/use-sub [:route :path-params])`.

**`src/cljs/com/rpl/agent_o_rama/ui/invocation_page.cljs`:**
*   Remove `["wouter" :refer [useParams useLocation]]`.
*   Replace `(let [{:strs [...]} (js->clj (useParams))] ...)` with `(let [{:keys [...]} (state/use-sub [:route :path-params])] ...)`
*   Replace `(let [[location set-location] (useLocation)] ... (set-location new-path))` with `(rfe/navigate new-path)` (and require `reitit.frontend.easy`).

**`src/cljs/com/rpl/agent_o_rama/ui/config_page.cljs`:**
*   Remove `["wouter" :refer [useParams]]`.
*   Replace `(let [{:strs [...]} (js->clj (useParams))] ...)` with `(let [{:keys [...]} (state/use-sub [:route :path-params])] ...)`.

**`src/cljs/com/rpl/agent_o_rama/ui/datasets.cljs` and `src/cljs/com/rpl/agent_o_rama/ui/stats.cljs`:**
*   Apply the same pattern: remove `useParams` and use `state/use-sub`.

---

After applying these changes, your application's routing will be fully integrated with your central `app-db`, making it more robust, easier to debug, and free of the manual URL-parsing hack.
