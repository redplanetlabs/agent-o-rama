(ns com.rpl.agent-o-rama.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aort]
   [com.rpl.agent-o-rama.system :as sys]))

(defn replace-slash [s]
  "because urlencoding causes jetty to 400 with Ambiguous URI path separator"
  ;; TODO use proper urlencoding, fix jetty error
  (clojure.string/replace s #"/" "::"))

(defn unreplace-slash [s]
  "reverse of above function"
  (clojure.string/replace s #"::" "/"))

(comment
  (replace-slash "example.core/FlowModule")
  (unreplace-slash "example.core::FlowModule"))

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   (for [[module-name agent-name]
         (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (sys/get-object :aor-cache))]
     {:module-id (replace-slash module-name)
      :agent-id (replace-slash agent-name)})})

(defn get-client [module-id agent-id]
  (select-one [(unreplace-slash module-id)
               :clients
               (unreplace-slash agent-id)]
              (sys/get-object :aor-cache)))

(defn objects [module-id agent-id]
  (aort/underlying-objects (get-client module-id agent-id)))

(defn get-graph [{{:keys [module-id agent-id]} :path-params}]
  (println "graph")
  {:status
   200
   
   :body
   {:graph
    (second (first (foreign-select [LAST]
                                   (:graph-history-pstate
                                    (objects module-id agent-id))
                                   {:pkey 0})))}})

(defn manually-trigger-invoke [{{:keys [module-id agent-id]} :path-params
                                {:keys [args]} :body-params
                                :as req}]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  
  (let [inv (apply aor/agent-initiate (get-client module-id agent-id) args)]
    {:status 200
     :body
     {:task-id (.getTaskId inv)
      :invoke-id (.getAgentInvokeId inv)}}))

(defn get-invokes [{{:keys [module-id agent-id]} :path-params}]
  {:status
   200
   
   :body
   (foreign-invoke-query
    (:invokes-page-query (objects module-id agent-id))
    10 nil)})

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

(defn parse-url-agent-id [s]
  (let [[task-id agent-id] (clojure.string/split invoke-id #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

(defn invoke-paginated 
  [{{:keys [module-id agent-id invoke-id]} :path-params
    {:strs [start-node-id depth] :or {depth "3"}} :query-params
    :as req}]
  (def module-id module-id)
  (def agent-id agent-id)
  (def invoke-id invoke-id)
  (let [[task-id agent-id] (parse-url-agent-id invoke-id)]
    3)
  (objects module-id agent-id)
  {:status 200 :body []})


