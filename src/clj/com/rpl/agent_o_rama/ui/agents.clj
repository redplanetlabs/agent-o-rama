(ns com.rpl.agent-o-rama.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aort]
   [com.rpl.agent-o-rama.ui :as ui])
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
         (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (ui/get-object :aor-cache))]
     {:module-id (replace-slash module-name)
      :agent-name (replace-slash agent-name)})})

(defn get-client [module-id agent-name]
  (select-one [(unreplace-slash module-id)
               :clients
               (unreplace-slash agent-name)]
              (ui/get-object :aor-cache)))

(defn objects [module-id agent-name]
  (aort/underlying-objects (get-client module-id agent-name)))

(defn get-graph [{{:keys [module-id agent-name]} :path-params}]
  {:status
   200
   
   :body
   {:graph
    (foreign-invoke-query (:current-graph-query (objects module-id agent-name)))}})

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

(defn parse-url-pair [s]
  (let [[task-id agent-id] (clojure.string/split s #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

(defn invoke-paginated 
  [{{:keys [module-id agent-name invoke-id]} :path-params
    {:strs [paginate-task-id missing-node-id]} :query-params
    :as req}]

  {:status 200
   :body
   (let [[agent-task-id agent-id] (parse-url-pair invoke-id)

         pair
         (cond
           (and (string? paginate-task-id)
                (string? missing-node-id))
           [(parse-long paginate-task-id) (parse-long missing-node-id)]
           (and (nil? paginate-task-id)
                (nil? missing-node-id))
           [agent-task-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                              (:root-pstate (objects module-id agent-name))
                                              {:pkey agent-task-id})])]
     (transform [:invokes-map]
                remove-implicit-nodes
                (foreign-invoke-query (:tracing-query (objects module-id agent-name))
                                      agent-task-id
                                      [pair]
                                      10)))})

(defn fork [{{:keys [module-id agent-name]} :path-params
             {:keys [changed-nodes invoke-id]} :body-params}]
  (let [^AgentInvoke result (let [[task-id agent-invoke-id]
                                  (parse-url-pair invoke-id)]
                              (aor/agent-initiate-fork
                               (get-client module-id agent-name)
                               (AgentInvoke. task-id agent-invoke-id )
                               (transform [MAP-VALS] read-string changed-nodes)))]
    {:status 200
     :body
     {:agent-invoke-id (:agentInvokeId (bean result))
      :task-id (:taskId (bean result))}}))

