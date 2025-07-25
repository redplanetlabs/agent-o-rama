(ns com.rpl.agent-o-rama.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aort]
   [com.rpl.agent-o-rama.system :as sys])
  (:import
   [com.rpl.agentorama AgentInvoke]))

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
      :agent-name (replace-slash agent-name)})})

(defn get-client [module-id agent-name]
  (select-one [(unreplace-slash module-id)
               :clients
               (unreplace-slash agent-name)]
              (sys/get-object :aor-cache)))

(defn objects [module-id agent-name]
  (aort/underlying-objects (get-client module-id agent-name)))

(defn get-graph [{{:keys [module-id agent-name]} :path-params}]
  {:status
   200
   
   :body
   {:graph
    (second (first (foreign-select [LAST]
                                   (:graph-history-pstate
                                    (objects module-id agent-name))
                                   {:pkey 0})))}})

(defn manually-trigger-invoke [{{:keys [module-id agent-name]} :path-params
                                {:keys [args]} :body-params
                                :as req}]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  
  (let [^AgentInvoke inv (apply aor/agent-initiate (get-client module-id agent-name) args)]
    {:status 200
     :body
     {:task-id (.getTaskId inv)
      :invoke-id (.getAgentInvokeId inv)}}))

(defn get-invokes [{{:keys [module-id agent-name]} :path-params :as req}]
  (let [parsed-pagination-information
        (transform [(multi-path MAP-KEYS
                                MAP-VALS)]
                   parse-long
                   (-> req :query-params))
        pagination
        (if (= {} parsed-pagination-information)
          nil
          parsed-pagination-information)]
    {:status
     200
     
     :body
     (foreign-invoke-query
      (:invokes-page-query (objects module-id agent-name))
      10 pagination)}))

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

(defn parse-url-trace-id [s]
  (let [[task-id agent-id] (clojure.string/split s #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

(defn invoke-paginated 
  [{{:keys [module-id agent-name invoke-id]} :path-params
    {:strs [start-node-id depth] :or {depth "3"}} :query-params
    :as req}]
  (def module-id module-id)
  (def agent-name agent-name)
  (def invoke-id invoke-id)
  (def start-node-id start-node-id)
  (def depth depth)
  ;; TODO figure out why data is smaller
  ;; https://github.com/redplanetlabs/agent-o-rama/commit/d0d0cf0e8fcab3d8d445ae947518c205ce3a1a50#diff-cae22e578469a40db2bab77d83e834f1d5a3e857168c9d263480de365d392460
  (comment
    (foreign-select [5 ALL]
                    (:root-pstate (objects module-id agent-name))
                    {:pkey 0}))
  {:status 200
   :body
   (let [[task-id invoke-id-parsed] (parse-url-trace-id invoke-id)]
     (def task-id task-id)
     (def invoke-id-parsed invoke-id-parsed)
     (foreign-invoke-query (:tracing-query (objects module-id agent-name))
                           task-id
                           [ [task-id invoke-id-parsed]]
                           10))})


