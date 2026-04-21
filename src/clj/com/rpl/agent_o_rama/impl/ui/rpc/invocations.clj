(ns com.rpl.agent-o-rama.impl.ui.rpc.invocations
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [jsonista.core :as j])
  (:import [com.rpl.agentorama AgentInvoke]
           [java.util UUID]))

(defn- get-client [system module-id agent-name]
  (get-in system [:aor-cache module-id :clients (common/url-decode agent-name)]))

(defn- get-manager [system module-id]
  (get-in system [:aor-cache module-id :manager]))

(defn get-page!!
  [system {:keys [module-id agent-name pagination filters limit cursor]}]
  (let [client (get-client system module-id agent-name)
        page-size (or limit 10)
        scan-page-size 100
        pages-raw (or pagination cursor)
        pages (if (empty? pages-raw) nil pages-raw)]
    (when client
      (foreign-invoke-query
       (:invokes-page-query (aor-types/underlying-objects client))
       page-size scan-page-size pages filters))))

(defn get-filter-options!!
  [system {:keys [module-id agent-name]}]
  (let [client (get-client system module-id agent-name)
        manager (get-manager system module-id)
        graph-nodes (let [graph-res (foreign-invoke-query
                                     (:current-graph-query (aor-types/underlying-objects client)))]
                      (-> graph-res
                          :node-map
                          keys
                          sort
                          vec))
        human-metrics (let [search-human-metrics-query (:search-human-metrics-query
                                                        (aor-types/underlying-objects manager))
                            metric-res (foreign-invoke-query search-human-metrics-query {} 1000 nil)]
                        (->> (:items metric-res)
                             (sort-by (comp str :name))
                             vec))]
    {:nodes graph-nodes
     :feedback-metrics human-metrics}))

(defn get-graph!!
  [system {:keys [module-id agent-name]}]
  (let [client (get-client system module-id agent-name)]
    (if client
      {:graph (foreign-invoke-query
               (:current-graph-query
                (aor-types/underlying-objects client)))}
      {:graph nil})))

(defn get-graph-page!!
  [system {:keys [module-id agent-name invoke-id]}]
  (let [client (get-client system module-id agent-name)]
    (if-not client
      (throw (ex-info "No client available - module or agent may not be loaded" {:invoke-id invoke-id}))
      (let [invoke-pair (common/parse-url-pair invoke-id)
            client-objects (aor-types/underlying-objects client)
            tracing-query (:tracing-query client-objects)
            root-pstate (:root-pstate client-objects)
            stream-shared-pstate (:stream-shared-pstate client-objects)

            [agent-task-id agent-id] invoke-pair
            summary-info-raw (foreign-select-one
                              [(keypath agent-id)
                               (submap
                                [:result :start-time-millis :finish-time-millis :graph-version
                                 :retry-num :fork-of :exception-summaries :invoke-args :stats
                                 :feedback :metadata])]
                              root-pstate
                              {:pkey agent-task-id})
            summary-info (merge
                          {:forks (foreign-select-one
                                   [(keypath agent-id) :forks
                                    (sorted-set-range-to-end 100)]
                                   root-pstate
                                   {:pkey agent-task-id})}
                          (->> summary-info-raw
                               (transform [:feedback :results ALL]
                                          (fn [feedback-result]
                                            (let [feedback-map (into {} feedback-result)
                                                  source (:source feedback-map)]
                                              (if source
                                                (assoc feedback-map :source-string (aor-types/source-string source))
                                                feedback-map))))
                               (transform [:feedback :results ALL :scores MAP-KEYS] name)
                               (transform [:feedback :actions MAP-KEYS] name))
                          (when-let [stats (:stats summary-info-raw)]
                            {:stats (merge {:aggregated-stats
                                            (stats/aggregated-basic-stats stats)}
                                           stats)}))
            root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                               root-pstate
                                               {:pkey agent-task-id})
            historical-graph (when-let [graph-version (:graph-version summary-info)]
                               (foreign-select-one [:history (keypath graph-version)]
                                                   stream-shared-pstate
                                                   {:pkey 0}))
            dynamic-trace (foreign-invoke-query tracing-query
                                                agent-task-id
                                                [[agent-task-id root-invoke-id]]
                                                10000)
            cleaned-nodes (when-let [m (:invokes-map dynamic-trace)]
                            (->> m
                                 common/remove-implicit-nodes
                                 (transform
                                  [MAP-VALS :feedback :results ALL]
                                  (fn [feedback-result]
                                    (let [feedback-map (into {} feedback-result)
                                          source (:source feedback-map)]
                                      (if source
                                        (assoc feedback-map :source-string (aor-types/source-string source))
                                        feedback-map))))
                                 (transform
                                  [MAP-VALS :feedback :results ALL :scores MAP-KEYS]
                                  name)
                                 (transform
                                  [MAP-VALS :feedback :actions MAP-KEYS]
                                  name)))

            agent-is-complete? (boolean (or (:finish-time-millis summary-info)
                                            (:result summary-info)))]

        {:is-complete agent-is-complete?
         :nodes cleaned-nodes
         :summary summary-info
         :task-id agent-task-id
         :agent-id agent-id
         :root-invoke-id root-invoke-id
         :historical-graph historical-graph}))))

;; =============================================================================
;; MUTATIONS
;; =============================================================================

(defn run-agent!!
  [system {:keys [module-id agent-name args metadata]}]
  (let [client (get-client system module-id agent-name)]
    (when-not (vector? args)
      (throw (ex-info "must be a json list of args" {:bad-args args})))
    (let [m (or metadata {})
          ^AgentInvoke inv (apply aor/agent-initiate-with-context client {:metadata m} args)]
      {:task-id (.getTaskId inv)
       :invoke-id (.getAgentInvokeId inv)})))

(defn execute-fork!!
  [system {:keys [module-id agent-name invoke-id changed-nodes]}]
  (let [client (get-client system module-id agent-name)
        [task-id agent-invoke-id] (common/parse-url-pair invoke-id)
        json-parsed-nodes (transform
                           [MAP-VALS]
                           #(j/read-value %)
                           changed-nodes)
        rehydrated-nodes (common/from-ui-serializable json-parsed-nodes)
        ^AgentInvoke result (aor/agent-initiate-fork
                             client
                             (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                             rehydrated-nodes)]
    {:agent-invoke-id (:agentInvokeId (bean result))
     :task-id (:taskId (bean result))}))

(defn provide-human-input!!
  [system {:keys [module-id agent-name request response]}]
  (let [client (get-client system module-id agent-name)
        {:keys [agent-task-id agent-id node node-task-id invoke-id uuid prompt]} request
        req (aor-types/->NodeHumanInputRequest agent-task-id agent-id node node-task-id invoke-id prompt uuid)]
    (aor/provide-human-input client req response)
    {:ok true}))

(defn set-metadata!!
  [system {:keys [module-id agent-name invoke-id key value-str]}]
  (let [client (get-client system module-id agent-name)
        [task-id agent-id] (common/parse-url-pair invoke-id)
        invoke (aor-types/->AgentInvokeImpl task-id agent-id)
        parsed-value (j/read-value value-str)]
    (aor/set-metadata! client
                       invoke
                       key
                       (if (= java.lang.Integer (class parsed-value))
                         (long parsed-value)
                         parsed-value))
    {:success true}))

(defn remove-metadata!!
  [system {:keys [module-id agent-name invoke-id key]}]
  (let [client (get-client system module-id agent-name)
        [task-id agent-id] (common/parse-url-pair invoke-id)
        invoke (aor-types/->AgentInvokeImpl task-id agent-id)]
    (aor/remove-metadata! client invoke key)
    {:success true}))


(defn get-node-stream-snapshot!!
  "One-shot fetch of buffered stream chunks for a node invocation (Closeable released in `finally`).
   Used for completed traces so the UI need not keep an SSE connection open."
  [system {:keys [module-id agent-name invoke-id node-name node-invoke-id]}]
  (when-not (uuid? node-invoke-id)
    (throw (ex-info "node-invoke-id must be a UUID" {:node-invoke-id node-invoke-id})))
  (let [client (get-client system module-id agent-name)]
    (if-not client
      {:all-chunks [] :new-chunks [] :reset? false :complete? true}
      (let [[task-id agent-id] (common/parse-url-pair invoke-id)
            invoke (aor-types/->AgentInvokeImpl task-id agent-id)
            ^java.io.Closeable stream
            (aor/agent-stream-specific client invoke node-name node-invoke-id nil)]
        (try
          (let [chunks (vec @stream)]
            {:all-chunks chunks
             :new-chunks chunks
             :reset? false
             :complete? true})
          (finally
            (.close stream)))))))

(defn stream-node!!sse
  "SSE RPC: subscribes to [[com.rpl.agent-o-rama/agent-stream-specific]] for one node invocation.
   Invoked as (stream-node!!sse system payload on-event); returns a Closeable stream handle.
   If there is no client, emits one terminal event with empty chunks and `complete?` true.
   For completed node traces use [[get-node-stream-snapshot!!]] instead so connections do not linger."
  [system {:keys [module-id agent-name invoke-id node-name node-invoke-id]} on-event]
  (when-not (uuid? node-invoke-id)
    (throw (ex-info "node-invoke-id must be a UUID" {:node-invoke-id node-invoke-id})))
  (let [client (get-client system module-id agent-name)]
    (cond
      (not client)
      (do
        (on-event {:all-chunks [] :new-chunks [] :reset? false :complete? true})
        (reify java.io.Closeable (close [_])))

      :else
      (let [[task-id agent-id] (common/parse-url-pair invoke-id)
            invoke (aor-types/->AgentInvokeImpl task-id agent-id)]
        (aor/agent-stream-specific client invoke node-name node-invoke-id
                                   (fn [all-chunks new-chunks reset? complete?]
                                     (on-event {:all-chunks all-chunks
                                                :new-chunks new-chunks
                                                :reset? reset?
                                                :complete? complete?})))))))

(defn get-node-stats!!
  [system {:keys [module-id agent-name granularity]}]
  (let [decoded-agent-name (common/url-decode agent-name)
        client (get-client system module-id agent-name)]
    (when client
      (let [client-objects (aor-types/underlying-objects client)
            telemetry-pstate (:telemetry-pstate client-objects)
            gran-seconds (or granularity po/HOUR-GRANULARITY)
            now-millis (System/currentTimeMillis)
            bucket-size-millis (* gran-seconds 1000)
            lookback-millis (* 3 bucket-size-millis)
            start-time-millis (- now-millis lookback-millis)
            end-time-millis (+ now-millis bucket-size-millis)
            telemetry-data (ana/select-telemetry
                            telemetry-pstate
                            decoded-agent-name
                            gran-seconds
                            [:agent :node-latencies]
                            start-time-millis
                            end-time-millis
                            [:mean :count :min :max 0.25 0.5 0.75 0.9 0.99]
                            nil)]

        (let [current-bucket (quot now-millis bucket-size-millis)
              prev-bucket (dec current-bucket)
              current-bucket-data (get telemetry-data current-bucket)
              prev-bucket-data (get telemetry-data prev-bucket)

              merge-node-stats
              (fn [curr-stats prev-stats]
                (cond
                  (and curr-stats prev-stats)
                  (let [total-count (+ (:count curr-stats) (:count prev-stats))
                        total-latency (+ (* (:mean curr-stats) (:count curr-stats))
                                         (* (:mean prev-stats) (:count prev-stats)))]
                    {:count total-count
                     :mean (/ total-latency total-count)
                     :min (min (:min curr-stats) (:min prev-stats))
                     :max (max (:max curr-stats) (:max prev-stats))
                     0.5 (max (get curr-stats 0.5 0) (get prev-stats 0.5 0))
                     0.9 (max (get curr-stats 0.9 0) (get prev-stats 0.9 0))
                     0.99 (max (get curr-stats 0.99 0) (get prev-stats 0.99 0))})
                  curr-stats curr-stats
                  prev-stats prev-stats
                  :else nil))

              all-nodes (set (concat (keys current-bucket-data) (keys prev-bucket-data)))

              aggregated-stats
              (into {}
                    (keep (fn [node-name]
                            (when-let [merged (merge-node-stats
                                               (get current-bucket-data node-name)
                                               (get prev-bucket-data node-name))]
                              [node-name merged]))
                          all-nodes))]

          {:node-stats aggregated-stats
           :granularity gran-seconds})))))
