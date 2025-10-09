(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.experiments :as exp]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.throttled-logging :as tl]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [jsonista.core :as j]
   [org.httpkit.client :as http])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AddRule
    AndFilter
    DeleteRule
    ErrorFilter
    FeedbackFilter
    InputMatchFilter
    LatencyFilter
    NotFilter
    OrFilter
    OutputMatchFilter
    TokenCountFilter]
   [java.util.concurrent
    CompletableFuture
    TimeUnit]))

(def ^:dynamic ACTION-HELPERS)
(defn declared-objects ^AgentDeclaredObjectsTaskGlobal [] (:declared-objects ACTION-HELPERS))
(defn rama-clients ^RamaClientsTaskGlobal [] (:rama-clients ACTION-HELPERS))
(defn random-task-id [] (rand-int (:num-tasks ACTION-HELPERS)))


(defn get-num-tasks
  []
  (.getNumTasks ^com.rpl.rama.ModuleInstanceInfo (ops/module-instance-info)))

(defn retrieve-pstate
  [pstate-name]
  (let [declared-objects-tg (declared-objects)
        retriever (.getClusterRetriever declared-objects-tg)]
    (foreign-pstate
     retriever
     (.getThisModuleName declared-objects-tg)
     pstate-name)))

(defn pstate-write!
  [pstate-name path k]
  (simpl/do-pstate-write!
   (.getPStateWriteDepot (rama-clients))
   nil
   pstate-name
   path
   k
  ))

(defn get-agent-client
  [name]
  (.getAgentClient (declared-objects) name))

(defn get-agent-manager
  []
  (.getThisModuleAgentManager (declared-objects)))

(defn eval-action-builder-fn
  [{:strs [name]}]
  (let [evals-pstate (retrieve-pstate (po/evaluators-task-global-name))
        eval-info    (foreign-select-one (keypath name) evals-pstate)
        eval-client  (get-agent-client aor-types/EVALUATOR-AGENT-NAME)]
    (when (nil? eval-info)
      (throw (h/ex-info "Evaluator doesn't exist" {:name name})))
    (fn [fetcher input output {:keys [rule-name agent-name] :as run-info}]
      (let [target-pstate-name (if (= :agent (:type run-info))
                                 (po/agent-root-task-global-name agent-name)
                                 (po/agent-node-task-global-name agent-name))
            target-pstate (retrieve-pstate target-pstate-name)
            target (if (= :agent (:type run-info))
                     (:agent-invoke run-info)
                     (:node-invoke run-info))
            target-task-id (:task-id target)
            k (if (= :agent (:type run-info))
                (:agent-invoke-id target)
                (:node-invoke-id target))
            curr-eval-agent-invoke (foreign-select-one [(keypath k)
                                                        (fb/action-state-path rule-name)]
                                                       target-pstate
                                                       {:pkey target-task-id})
            eval-agent-invoke (or curr-eval-agent-invoke
                                  (aor-types/->AgentInvokeImpl (random-task-id)
                                                               (h/random-uuid7)))]
        (when (nil? curr-eval-agent-invoke)
          (pstate-write! target-pstate-name
                         (path (keypath k)
                               (fb/set-action-state-path rule-name eval-agent-invoke))
                         (aor-types/->DirectTaskId target-task-id)))
        (binding [aor-types/FORCED-AGENT-INVOKE-ID (:agent-invoke-id eval-agent-invoke)
                  aor-types/FORCED-AGENT-TASK-ID   (:task-id eval-agent-invoke)]
          ;; this is a no-op if it was already initiated
          (exp/initiate-eval eval-client
                             eval-info
                             :regular
                             input
                             nil
                             [output]
                             name
                             (:builder-name eval-info)
                             (:builder-params eval-info)
                             [(aor-types/->valid-EvalInfo agent-name target)]
                             (aor-types/->valid-ActionSourceImpl (:agent-name run-info)
                                                                 (:rule-name run-info))))
        ;; should be guaranteed to be delivered, so the timeout is just in case
        (let [{:keys [stats result]}
              (.get
               ^CompletableFuture
               (aor-types/subagent-next-step-async eval-client
                                                   eval-agent-invoke)
               180
               TimeUnit/SECONDS)]
          (merge {"invoke" eval-agent-invoke}
                 (if (instance? Throwable result)
                   {"success?" false "failure" (h/throwable->str result)}
                   {"success?" true "stats" stats})))
      ))))

(defn add-to-dataset-action-builder-fn
  [{:strs [datasetId inputJsonPathTemplate outputJsonPathTemplate]}]
  (let [input-template  (h/parse-json-path-template (or inputJsonPathTemplate "$"))
        output-template (h/parse-json-path-template (or outputJsonPathTemplate "$"))
        dataset-id      (java.util.UUID/fromString datasetId)
        manager         (get-agent-manager)]
    (fn [fetcher input output run-info]
      (let [input      (h/resolve-json-path-template input-template input)
            output     (h/resolve-json-path-template output-template output)
            example-id (binding [aor-types/OPERATION-SOURCE
                                 (aor-types/->valid-ActionSourceImpl (:agent-name run-info)
                                                                     (:rule-name run-info))]
                         (c/add-dataset-example! manager
                                                 dataset-id
                                                 input
                                                 {:reference-output output}))]
        {"exampleId" (str example-id)}
      ))))

(def DEFAULT-WEBHOOK-PAYLOAD
  "{\"input\": %input,
  \"output\": %output,
  \"runInfo\": %runInfo}")

(def STR-MAPPER (j/object-mapper {:decode-key-fn str}))

(defn best-effort-json
  [v]
  (try
    (j/write-value-as-string v)
    (catch Throwable t
      (str "\"" v "\""))))

(defn run-info->map
  [{:keys [rule-name action-name agent-name node-name type start-time-millis latency-millis
           feedback]}]
  (setval [MAP-VALS nil?]
          NONE
          {"ruleName"        rule-name
           "actionName"      action-name
           "agentName"       agent-name
           "nodeName"        node-name
           "type"            (if (= type :agent) "agent" "node")
           "startTimeMillis" start-time-millis
           "latencyMillis"   latency-millis
           "feedback"        (mapv (fn [{:keys [scores source]}]
                                     ;; scores are mandated by AOR to be numbers, strings, or
                                     ;; booleans, so don't need any special handling here
                                     {"scores" scores
                                      "source" (aor-types/source-string source)})
                                   feedback)}))

(defn webhook-action-builder-fn
  [{:strs [url payloadTemplate headers timeoutMillis]}]
  (let [timeout-millis (Long/parseLong timeoutMillis)
        headers        (j/read-value headers STR-MAPPER)]
    (fn [fetcher input output run-info]
      (let [payload (-> payloadTemplate
                        (str/replace "%input" (best-effort-json input))
                        (str/replace "%output" (best-effort-json output))
                        (str/replace "%runInfo" (j/write-value-as-string (run-info->map run-info))))
            res     (deref
                     (http/post url
                                {:headers      headers
                                 :body         payload
                                 :content-type :json})
                     timeout-millis
                     ::timeout)]
        (when (= res ::timeout)
          (throw (h/ex-info "Timeout on HTTP request" {:url url :payload payload})))
        (let [{:keys [status body error]} res]
          (when (or (not= status 200) error)
            (throw (h/ex-info "Error on HTTP request" {:status status :error error :body body})))
          {"response" body}
        )))))

(def BUILT-IN-ACTIONS
  {"aor/eval"
   {:builder-fn  eval-action-builder-fn
    :description "Run an evaluator to add feedback to a node or agent"
    :options     {:params
                  {"name" {:description "Evaluator to use"}}
                  :limit-concurrency? true}}
   "aor/add-to-dataset"
   {:builder-fn add-to-dataset-action-builder-fn
    :description "Add a run to a dataset"
    :options {:params
              {"datasetId" {:description "Dataset to add to"}
               "inputJsonPathTemplate"
               {:description
                "JSON path template to translate input of a run to dataset example input"
                :default     "$"}
               "outputJsonPathTemplate"
               {:description
                "JSON path template to translate output of a run to dataset example output"
                :default     "$"}}
             }}
   "aor/webhook"
   {:builder-fn  webhook-action-builder-fn
    :description "Post a JSON payload to a URL"
    :options
    {:params
     {"url"           {:description "URL"}
      "payloadTemplate"
      {:description
       "JSON payload for the POST request. %input, %output, and %runInfo can be used as variables in the payload."
       :default     DEFAULT-WEBHOOK-PAYLOAD}
      "headers"
      {:description
       "Map from header name to header string specified as JSON"
       :default     "{}"}
      "timeoutMillis"
      {:description
       "Timeout for the POST request"
       :default     "60000"}}
    }}
  })


(defn all-action-builders
  []
  (let [declared-objects (po/agent-declared-objects-task-global)]
    (merge BUILT-IN-ACTIONS
           (.getActionBuilders declared-objects))))

(defn all-action-builders-name
  []
  "_aor-all-action-builders")

(defn all-action-builders-without-builder-fns
  []
  (setval [MAP-VALS :builder-fn]
          NONE
          (all-action-builders)))

;; can't go in queries.clj because that creates a circular dependency
(defn declare-all-action-builders-query-topology
  [topologies]
  (<<query-topology topologies
    (all-action-builders-name)
    [:> *res]
    (|origin)
    (all-action-builders-without-builder-fns :> *res)))

(defn- agent-run-type?
  [info]
  (= :agent (:run-type info)))

(defn log-regex-error
  [t]
  (tl/error ::regex-failure t "Regex match exception"))

(defn- regex-match?
  [regex json-path o]
  (try
    (some? (re-find regex (h/read-json-path o json-path)))
    (catch Throwable t
      (log-regex-error t)
      false
    )))

(extend-protocol aor-types/RuleFilter
  FeedbackFilter
  (dependency-rule-names [this] #{(:rule-name this)})
  (rule-filter-matches? [this info]
    (selected-any?
     [:feedback
      :results
      ALL
      (selected? :source
                 aor-types/EvalSourceImpl?
                 :source
                 aor-types/ActionSourceImpl?
                 :rule-name
                 (pred= (:rule-name this)))
      :scores
      (must (:feedback-key this))
      #(aor-types/comparator-spec-matches? (:comparator-spec this) %)]
     info))

  LatencyFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this {:keys [start-time-millis finish-time-millis]}]
    (and start-time-millis
         finish-time-millis
         (aor-types/comparator-spec-matches? (:comparator-spec this)
                                             (- finish-time-millis start-time-millis))))

  ErrorFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (if (agent-run-type? info)
      (not (empty? (:exception-summaries info)))
      (not (empty? (:exceptions info)))
    ))

  InputMatchFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [o (if (agent-run-type? info) (:invoke-args info) (:input info))]
      (regex-match? (:regex this) (:json-path this) o)))

  OutputMatchFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [output (if (agent-run-type? info)
                   (-> info
                       :result
                       :val)
                   (h/node->output (:result info) (:emits info)))]
      (regex-match? (:regex this) (:json-path this) output)))

  TokenCountFilter
  (dependency-rule-names [this] #{})
  (rule-filter-matches? [this info]
    (let [token-counts
          (if (agent-run-type? info)
            (let [combined (stats/aggregated-basic-stats (:stats info))]
              {:input  (:input-token-count combined)
               :output (:output-token-count combined)
               :total  (:total-token-count combined)})
            (-> info
                :nested-ops
                stats/nested-op-stats
                :token-counts))]
      (aor-types/comparator-spec-matches?
       (:comparator-spec this)
       (get token-counts (:type this)))
    ))

  AndFilter
  (dependency-rule-names [this]
    (apply set/union (mapv aor-types/dependency-rule-names (:filters this))))
  (rule-filter-matches? [this info]
    (every? #(aor-types/rule-filter-matches? % info) (:filters this)))


  OrFilter
  (dependency-rule-names [this]
    (apply set/union (mapv aor-types/dependency-rule-names (:filters this))))
  (rule-filter-matches? [this info]
    (if (some #(aor-types/rule-filter-matches? % info) (:filters this))
      true
      false))

  NotFilter
  (dependency-rule-names [this] (aor-types/dependency-rule-names (:filter this)))
  (rule-filter-matches? [this info]
    (not (aor-types/rule-filter-matches? (:filter this) info)))
)

(defn check-rule-dependency-conflict
  [rules name]
  (let [conflict (select-first [ALL
                                (selected? :filter
                                           (view aor-types/dependency-rule-names)
                                           #(contains? % name))
                                :name]
                               rules)]
    (when (some? conflict)
      (format "Deletion failed because rule '%s' depends on it" conflict))))

(deframaop handle-rule-event
  [{:keys [*agent-name] :as *data}]
  (po/agent-names-set :> *agent-names)
  (filter> (contains? *agent-names *agent-name))
  (set (keys (all-action-builders)) :> *action-names)
  (<<with-substitutions
   [$$rules (po/agent-rules-task-global *agent-name)]
   (<<subsource *data
    (case> AddRule :> {:keys [*name *id *action-name *start-time-millis]})
     (local-select> [(keypath *name) :definition :id] $$rules :> *curr-id)
     (<<cond
      (case> (and> (some? *curr-id) (not= *curr-id *id)))
       (ack-return> (format "Rule '%s' already exists" *name))

      (case> (not (contains? *action-names *action-name)))
       (ack-return> (format "Action '%s' doesn't exist" *action-name))

      (default>)
       (local-transform> [(keypath *name) :definition (termval *data)]
                         $$rules))

    (case> DeleteRule :> {:keys [*name]})
     (local-select> (subselect MAP-VALS :definition) $$rules :> *rules)
     (check-rule-dependency-conflict *rules *name :> *error-str)
     (<<if (some? *error-str)
       (ack-return> *error-str)
      (else>)
       (local-transform> [(keypath *name) NONE>] $$rules))
   )))

(defn mk-cursor-map
  [start-time-millis]
  (let [^com.rpl.rama.ModuleInstanceInfo module-instance-info (ops/module-instance-info)
        num-tasks (.getNumTasks module-instance-info)
        uuid      (h/min-uuid7-at-timestamp start-time-millis)]
    (into {}
          (for [i (range 0 num-tasks)]
            [i uuid]
          ))))

(deframafn read-rules
  []
  (<<batch
    (ops/explode (po/agent-names-set) :> *agent-name)
    (po/agent-rules-task-global *agent-name :> $$rules)
    (po/agent-rule-cursors-task-global *agent-name :> $$rule-cursors)
    (local-select> STAY $$rules :> *rules)
    (local-select> STAY $$rule-cursors :> *all-rule-cursors)
    (ops/explode-map *rules :> *rule-name *rule-info)
    (get *all-rule-cursors *rule-name :> *curr-rule-cursors)
    (get *rule-info :definition :> {:keys [*start-time-millis]})
    (ifexpr (empty? *curr-rule-cursors)
      (mk-cursor-map *start-time-millis :> *cursor-map)
      *curr-rule-cursors
      :> *rule-cursors)
    (assoc *rule-info
     :cursors *rule-cursors
     :> *all-rule-info)
    (+compound {*agent-name {*rule-name (aggs/+last *all-rule-info)}} :> *ret))
  (:> *ret))

(defn compute-end-scan-offset
  [m start-offset]
  (if (empty? m)
    start-offset
    (h/uuid-inc (h/last-key m))
  ))

(defn action-target-pstate
  [agent-name node-name]
  (if (nil? node-name)
    (po/agent-root-task-global agent-name)
    (po/agent-node-task-global agent-name)))

(defn scan-amt [] 100)

(defn experiment-source?
  [data]
  (and (contains? data :source)
       (aor-types/ExperimentSourceImpl? (:source data))))

(def NODE-ACTION-STALL-TIME-MILLIS (* 1000 60 2))

(defn max-node-scan-time
  []
  (- (h/current-time-millis) NODE-ACTION-STALL-TIME-MILLIS))

(defn compute-end-offset
  [dep-offset max-scan-offset]
  (cond
    (and (nil? dep-offset) (nil? max-scan-offset))
    (h/max-uuid)

    (nil? dep-offset)
    max-scan-offset

    (nil? max-scan-offset)
    dep-offset

    :else
    (if (< (compare dep-offset max-scan-offset) 0)
      dep-offset
      max-scan-offset
    )))

(defn complete-node-map
  [m node-name node-exec]
  (if (nil? node-name)
    m
    (let [max-time        (max-node-scan-time)
          invalid-offset?
          (fn [[k data]]
            (and (not (contains? data :finish-time-millis))
                 (not (contains? data :invoked-agg-invoke-id))
                 (or (retries/invoke-id-executing? node-exec k)
                     (and (contains? data :start-time-millis); not strictly necessary
                          (> (:start-time-millis data) max-time)))))
          first-invalid-offset (select-first [ALL invalid-offset? FIRST] m)]
      (if (nil? first-invalid-offset)
        m
        (select-any (sorted-map-range-to first-invalid-offset) m)))))

(def BUILD-ERROR ::builder-error)

(defn run-virtual-with-action-helpers!
  [afn]
  (let [cf           (CompletableFuture.)
        declared-objects-tg (po/agent-declared-objects-task-global)
        rama-clients (po/agents-clients-task-global)
        num-tasks    (.getNumTasks ^com.rpl.rama.ModuleInstanceInfo (ops/module-instance-info))]
    (anode/submit-virtual-task!
     nil
     (fn []
       (try
         (binding [ACTION-HELPERS
                   {:num-tasks        num-tasks
                    :declared-objects declared-objects-tg
                    :rama-clients     rama-clients}]
           (afn cf)
         ))))
    cf))


(defn build-action-fn
  [builder-fn params]
  (run-virtual-with-action-helpers!
   (fn [^CompletableFuture cf]
     (.complete
      cf
      (if (nil? builder-fn)
        {BUILD-ERROR {"error" "Action builder does not exist"}}
        (try
          (builder-fn params)
          (catch Throwable t
            (tl/error ::build-action t "Action builder exception")
            {BUILD-ERROR {"error"     "Action failed to build"
                          "exception" (h/throwable->str t)}}

          )))))))

(defn hook:run-action [run-info])
(defn enable-action-error-logs? [] true)

(defn run-action!
  [action-fn input output run-info]
  (let [fetcher (anode/mk-fetcher)]
    (hook:run-action run-info)
    (run-virtual-with-action-helpers!
     (fn [^CompletableFuture cf]
       (try
         (let [m (action-fn fetcher input output run-info)]
           (when-not (and (instance? java.util.Map m) (every? string? (keys m)))
             (throw (h/ex-info "Action return must be map with string keys" {:return m})))
           (.complete cf {:success? true :info-map m}))
         (catch Throwable t
           (when (enable-action-error-logs?)
             (tl/error ::run-action t "Action exception"))
           (.complete cf {:success? false :info-map {"exception" (h/throwable->str t)}}))
       )))))

(defn sample?
  [sampling-rate]
  (< (rand) sampling-rate))

(def +min-uuid
  (combiner
   (fn [v1 v2]
     (cond
       (nil? v1) v2
       (nil? v2) v1
       (< (compare v1 v2) 0) v1
       :else v2))))

(defn data->latency-millis
  [{:keys [start-time-millis finish-time-millis]}]
  (when (and start-time-millis finish-time-millis)
    (- finish-time-millis start-time-millis)))

(defn maybe-to-clojure-map
  [o]
  (cond (map? o) o
        (instance? java.util.Map o) (into {} o)
        :else o))

(deframaop run-one-action!
  [*cache-pstate-name *agent-name *rule-name *offset *action-name *node-name]
  (<<with-substitutions
   [$$cache (this-module-pobject-task-global *cache-pstate-name)
    $$action-log (po/action-log-task-global)]
   (ops/current-task-id :> *task-id)
   (local-select> [(keypath *agent-name *rule-name) :action-fn] $$cache :> *action-fn)
   (h/current-time-millis :> *action-start-time-millis)
   (local-select> (keypath *agent-name *rule-name :data *offset) $$cache :> *data)
   (<<if (some? *node-name)
     (identity :node :> *type)
     (identity nil :> *agent-stats)
     (get *data :nested-ops :> *nested-ops)
     (aor-types/->AgentInvokeImpl (get *data :agent-task-id)
                                  (get *data :agent-id)
                                  :> *agent-invoke)
     (aor-types/->NodeInvokeImpl *task-id *offset :> *node-invoke)
     (get *data :input :> *input)
     (h/node->output (get *data :result) (get *data :emits) :> *output)
    (else>)
     (identity :agent :> *type)
     (get *data :stats :> *agent-stats)
     (identity nil :> *nested-ops)
     (aor-types/->AgentInvokeImpl *task-id *offset :> *agent-invoke)
     (identity nil :> *node-invoke)
     (get *data :invoke-args :> *input)
     (h/result->output (get *data :result) :> *output))
   (<<if (and> (map? *action-fn) (contains? *action-fn BUILD-ERROR))
     (get *action-fn BUILD-ERROR :> *info-map)
     (identity false :> *success?)
    (else>)
     (select> [:feedback :results NIL->VECTOR] *data :> *feedback)
     (aor-types/->valid-RunInfoImpl *rule-name
                                    *action-name
                                    *agent-name
                                    *node-name
                                    *agent-invoke
                                    *node-invoke
                                    *type
                                    (get *data :start-time-millis)
                                    (data->latency-millis *data)
                                    *feedback
                                    *agent-stats
                                    *nested-ops
                                    :> *run-info)
     (run-action! *action-fn *input *output *run-info :> *cf)
     (completable-future> *cf :> {:keys [*success? *info-map]}))
   (h/current-time-millis :> *action-finish-time-millis)
   (aor-types/->valid-ActionLog *action-start-time-millis
                                *action-finish-time-millis
                                *agent-invoke
                                *node-invoke
                                *success?
                                (maybe-to-clojure-map *info-map)
                                :> *action-log)
   (h/random-uuid7 :> *action-log-id)
   (local-transform> [(keypath *agent-name *rule-name *action-log-id) (termval *action-log)]
                     $$action-log)
   (:>)
  ))


(defn include-result-from-status?
  [status-filter {:keys [run-type result finish-time-millis]}]
  (let [success? (if (= :agent run-type)
                   (not (:failure? result))
                   (some? finish-time-millis))]
    (condp = status-filter
      :all true
      :success success?
      :fail (not success?)
      (throw (h/ex-info "Unexpected status filter" {:status-filter status-filter})))))

(deframaop find-qualified-offsets-and-run-unlimited
  [*agent->rule->info *cache-pstate-name *processed-pstate-name]
  (<<with-substitutions
   [$$cache (this-module-pobject-task-global *cache-pstate-name)
    $$processed (this-module-pobject-task-global *processed-pstate-name)]
   (ops/explode-map *agent->rule->info :> *agent-name *rule->info)
   (ops/explode-map *rule->info :> *rule-name *rule-info)
   (get *rule-info
        :definition
        :> {:keys [*filter *node-name *sampling-rate *action-name *action-params
                   *status-filter]})
   (aor-types/dependency-rule-names *filter :> *dependency-names)
   (select> [:cursors ALL (collect-one FIRST) LAST]
     *rule-info
     :> [*task-id *offset])
   (<<batch
     (ops/explode *dependency-names :> *dname)
     (select> [(keypath *dname) :cursors (keypath *task-id)] *rule->info :> *other-offset)
     (+min-uuid *other-offset :> *dep-end-offset))
   (|direct *task-id)
   (all-action-builders :> *action-builders)
   (get *action-builders *action-name :> {:keys [*builder-fn *options]})
   (get *options :limit-concurrency? false :> *limit-concurrency?)
   (completable-future> (build-action-fn *builder-fn *action-params) :> *action-fn)
   (<<if (some? *node-name)
     (identity nil :> *max-scan-offset)
    (else>)
     (po/agent-active-invokes-task-global *agent-name :> $$active)
     (local-select> [(subselect FIRST) (view first) (view first)] $$active :> *max-scan-offset))
   (compute-end-offset *dep-end-offset *max-scan-offset :> *end-offset)
   (action-target-pstate *agent-name *node-name :> $$p)
   (scan-amt :> *scan-amt)
   (<<ramafn %add-run-type
     [*m]
     (:> (assoc (into {} *m) :run-type (ifexpr (some? *node-name) :node :agent))))
   (po/agent-node-executor-task-global :> *node-exec)
   (local-select> [(sorted-map-range-from *offset *scan-amt)
                   (sorted-map-range *offset *end-offset)
                   (view complete-node-map *node-name *node-exec)
                   (transformed MAP-VALS %add-run-type)]
                  $$p
                  :> *m)
   (compute-end-scan-offset *m *offset :> *end-scan-offset)
   (<<ramafn %match?
     [*data]
     (:> (and> (not (experiment-source? *data))
               (not (contains? *data :invoked-agg-invoke-id))
               (contains? *data :start-time-millis) ; not stricly necessary
               (include-result-from-status? *status-filter *data)
               (or> (nil? *node-name) (= *node-name (get *data :node)))
               (aor-types/rule-filter-matches? *filter *data)
               (sample? *sampling-rate))))
   (select> (subselect ALL
                       (selected? LAST (pred %match?))
                       FIRST)
     *m
     :> *matching-offsets)
   (local-transform> [(keypath *agent-name *rule-name)
                      (multi-path [:data (termval *m)]
                                  [:action-fn (termval *action-fn)]
                                  [:end-scan-offset (termval *end-scan-offset)]
                                  [:matching-offsets (termval *matching-offsets)])]
                     $$cache)
   (<<if *limit-concurrency?
     (or> (first *matching-offsets) *end-scan-offset :> *next-offset)
     (local-transform> [(keypath *agent-name *rule-name) (termval *next-offset)] $$processed)
     (:> *agent-name *rule-name *task-id (count *matching-offsets))
    (else>)
     (local-transform> [(keypath *agent-name *rule-name) (termval *end-scan-offset)] $$processed)
     (ops/explode *matching-offsets :> *offset)
     (run-one-action! *cache-pstate-name *agent-name *rule-name *offset *action-name *node-name)
   )))


(defn to-action-queue
  [task->agent->rule->info]
  (letfn [(rr [colls]
              (lazy-seq
               (let [active (seq (filter seq colls))]
                 (when active
                   (concat (map first active)
                           (rr (map rest active)))))))]
    (let [tasks     (shuffle (or (keys task->agent->rule->info) []))
          task-seqs (for [t tasks]
                      (rr
                       (for [a (shuffle (keys (get task->agent->rule->info t)))]
                         (rr
                          (for [r (shuffle (keys (select-any (keypath t a)
                                                             task->agent->rule->info)))]
                            (let [match-count (select-any (keypath t a r) task->agent->rule->info)]
                              (repeat match-count
                                      {:task-id    t
                                       :agent-name a
                                       :rule-name  r})))))))]
      (rr task-seqs))))

(deframafn agg-items
  [*items]
  (<<batch
    (ops/explode *items :> {:keys [*task-id *agent-name *rule-name]})
    (+compound {*task-id {*agent-name {*rule-name (aggs/+count)}}} :> *res))
  (:> *res))

(deframaop run-limited-concurrency-actions!
  [*plan *agent->rule->info *cache-pstate-name *processed-pstate-name]
  (<<with-substitutions
   [$$cache (this-module-pobject-task-global *cache-pstate-name)
    $$processed (this-module-pobject-task-global *processed-pstate-name)]
   (ops/explode-map *plan :> *task-id *agent->rule->count)
   (ops/explode-map *agent->rule->count :> *agent-name *rule->count)
   (ops/explode-map *rule->count :> *rule-name *count)
   (select> (keypath *agent-name *rule-name :definition)
     *agent->rule->info
     :> {:keys [*action-name *node-name]})
   (|direct *task-id)
   (local-select> (keypath *agent-name *rule-name :matching-offsets) $$cache :> *matching-offsets)
   (local-select> (keypath *agent-name *rule-name :end-scan-offset) $$cache :> *end-scan-offset)
   (split-at *count *matching-offsets :> [*offsets *next-matching-offsets])
   (local-transform> [(keypath *agent-name *rule-name :matching-offsets)
                      (termval *next-matching-offsets)]
                     $$cache)

   (or> (first *next-matching-offsets) *end-scan-offset :> *next-offset)
   (local-transform> [(keypath *agent-name *rule-name) (termval *next-offset)] $$processed)
   (ops/explode *offsets :> *offset)
   (run-one-action! *cache-pstate-name *agent-name *rule-name *offset *action-name *node-name)
  ))

(defn action-iter-complete?
  [first-iter? queue start-time-millis target-millis]
  (let [time-delta (- (h/current-time-millis) start-time-millis)]
    (or (empty? queue)
        (and (not first-iter?)
             (> time-delta target-millis)))))



(deframafn update-rule-offsets!
  [*agent->rule->cursors]
  (<<atomic
    (ops/explode (po/agent-names-set) :> *agent-name)
    (get *agent->rule->cursors *agent-name :> *rule->cursors)
    (po/agent-rule-cursors-task-global *agent-name :> $$rule-cursors)
    (local-transform> (termval *rule->cursors) $$rule-cursors))
  (:>))

(defn hook:analytics-loop-iter* [])
(defn hook:analytics-loop-iter
  []
  (hook:analytics-loop-iter*))

(defbasicblocksegmacro handle-analytics-tick
  []
  (let [match-info-pstate (gen-pstatevar "match-info")
        cache-pstate (gen-pstatevar "cache")
        cache-pstate-name (str cache-pstate)
        processed-pstate (gen-pstatevar "processed-offsets")
        processed-pstate-name (str processed-pstate)
        processed-agg-pstate (gen-pstatevar "processed-agg")]
    [[anode/read-global-config aor-types/MAX-LIMITED-ACTIONS-CONCURRENCY-CONFIG :> '*max-concurrency]
     [anode/read-global-config aor-types/ACTIONS-PROCESSING-ITERATION-TIME-MILLIS-CONFIG :> '*target-millis]
     [h/current-time-millis :> '*actions-start-time-millis]
     [read-rules :> '*agent->rule->info]
     [<<batch
      [filter> false]
      [materialize> :> cache-pstate]
      [materialize> :> processed-pstate]]
     [<<batch
      [find-qualified-offsets-and-run-unlimited '*agent->rule->info cache-pstate-name processed-pstate-name
        :> '*agent-name '*rule-name '*task-id '*match-count]
      [|global]
      [+compound
        {'*task-id
          {'*agent-name
            {'*rule-name
              (seg# aggs/+last '*match-count)}}}
        :> match-info-pstate]]
     [ops/vget match-info-pstate :> '*match-info]
     [to-action-queue '*match-info :> '*queue]
     [loop<-
       ['*queue '*queue
        '*first-iter? true]
       [hook:analytics-loop-iter]
       [<<if (seg# action-iter-complete? '*first-iter? '*queue '*actions-start-time-millis '*target-millis)
         [:>]
        [else>]
         [split-at '*max-concurrency '*queue :> ['*items '*rest-queue]]
         [agg-items '*items :> '*plan]
         [<<batch
           [run-limited-concurrency-actions! '*plan '*agent->rule->info cache-pstate-name processed-pstate-name]]
         [continue> '*rest-queue false]
         ]]
     [<<batch
       [|all]
       [ops/current-task-id :> '*task-id]
       [local-select> STAY processed-pstate :> '*agent->rule->offset]
       [ops/explode-map '*agent->rule->offset :> '*agent-name '*rule->offset]
       [ops/explode-map '*rule->offset :> '*rule-name '*offset]
       [|global]
       [+compound {'*agent-name {'*rule-name {'*task-id (seg# aggs/+last '*offset)}}} :> processed-agg-pstate]]
     [local-select> STAY processed-agg-pstate :> '*new-cursors]
     [update-rule-offsets! '*new-cursors]
    ]))

(defn add-rule!
  [global-actions-depot name agent-name
   {:keys [node-name action-name action-params filter sampling-rate start-time-millis
           status-filter]}]
  (let [{error aor-types/AGENT-TOPOLOGY-NAME}
        (foreign-append!
         global-actions-depot
         (aor-types/->valid-AddRule
          name
          (h/random-uuid7)
          agent-name
          node-name
          action-name
          action-params
          filter
          sampling-rate
          start-time-millis
          status-filter))]
    (when error
      (throw (h/ex-info "Error adding rule" {:info error})))))

(defn delete-rule!
  [global-actions-depot agent-name name]
  (let [{error aor-types/AGENT-TOPOLOGY-NAME}
        (foreign-append!
         global-actions-depot
         (aor-types/->valid-DeleteRule
          agent-name
          name))]
    (when error
      (throw (h/ex-info "Error adding rule" {:info error})))))

(defn fetch-agent-rules
  [agent-rules-pstate]
  (foreign-select-one STAY agent-rules-pstate))
