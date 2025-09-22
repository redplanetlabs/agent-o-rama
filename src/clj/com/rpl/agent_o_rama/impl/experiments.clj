(ns com.rpl.agent-o-rama.impl.experiments
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.feedback :as fb]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama
    AgentClient
    AgentNode]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    ComparativeExperiment
    DeleteExperiment
    RegularExperiment
    StartExperiment
    UpdateExperimentName]
   [java.util.concurrent
    CompletableFuture]
  ))

(def EVALUATOR-AGENT-NAME "_aor-evaluator")

(defn get-cluster-retriever
  [agent-node]
  (.getClusterRetriever ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-this-module-name
  [agent-node]
  (.getThisModuleName ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-evaluator-builders
  [agent-node]
  (merge
   evals/BUILT-IN
   (.getEvaluatorBuilders ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node))))

(defn get-evaluator
  [agent-node name builder-name params]
  (.getEvaluator ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)
                 name
                 builder-name
                 params))

(defmacro with-retriever
  [[agent-node experiment remote-info] [retriever-sym] & body]
  `(let [{cluster-conductor-host# :cluster-conductor-host
          cluster-conductor-port# :cluster-conductor-port
          module-name# :module-name}
         ~remote-info

         ~'_ (when (and cluster-conductor-host# (nil? module-name#))
               (throw (h/ex-info "Must specify module when connecting to remote cluster"
                                 {:cluster-conductor-host cluster-conductor-host#
                                  :cluster-conductor-port cluster-conductor-port#})))

         retriever# (if cluster-conductor-host#
                      (open-cluster-manager (h/to-rama-connection-info cluster-conductor-host#
                                                                       cluster-conductor-port#))
                      (get-cluster-retriever ~agent-node))

         ~retriever-sym {:retriever   retriever#
                         :agent-node  ~agent-node
                         :experiment  ~experiment
                         :remote-info ~remote-info}]
     (try
       ~@body
       (finally
         (when cluster-conductor-host#
           (close! retriever#))))))

(defn get-pstate
  [{:keys [agent-node retriever experiment remote-info]} pstate-name]
  (foreign-pstate
   retriever
   (or (:module-name remote-info) (get-this-module-name agent-node))
   pstate-name))

(defn datasets-pstate
  [retriever]
  (get-pstate retriever (po/datasets-task-global-name)))

(defn evals-pstate
  [retriever]
  (get-pstate retriever (po/evaluators-task-global-name)))

(defn local-datasets-store*
  [^AgentNode agent-node]
  (.getStore agent-node (po/datasets-task-global-name)))

(defn local-datasets-store
  [{:keys [agent-node] :as _retriever}]
  (local-datasets-store* agent-node))

(defn local-evals-pstate
  [{:keys [agent-node]}]
  (foreign-pstate
   (get-cluster-retriever agent-node)
   (get-this-module-name agent-node)
   (po/evaluators-task-global-name)))

(defn valid-evaluator-types
  [spec]
  (cond (aor-types/RegularExperiment? spec)
        #{:regular :summary}

        (aor-types/ComparativeExperiment? spec)
        #{:comparative}

        :else
        (throw (h/ex-info "Unexpected experiment spec" {:type (class spec)}))))

(defn experiment-type->kw
  [klass]
  (cond
    (= RegularExperiment klass) :regular
    (= ComparativeExperiment klass) :comparative
    :else (throw (h/ex-info "Unexpected experiment type" {:type klass}))))

(defn validate-evaluator
  [agent-node spec {:keys [retrieval-failed? builder-name] :as evaluator}]
  (let [builders (get-evaluator-builders agent-node)
        info     (get builders builder-name)]
    (cond
      retrieval-failed?
      {:problem "Evaluator does not exist"}

      (nil? info)
      {:problem "Could not find associated builder" :builder-name builder-name}

      (not (contains? (valid-evaluator-types spec) (:type info)))
      {:problem         "Evaluator type does not match experiment"
       :experiment-type (experiment-type->kw (class spec))
       :evaluator-type  (:type info)})))

(defn retrieve-all-examples-ids
  [datasets dataset-id snapshot selector]
  (cond
    (nil? selector)
    (foreign-select [(keypath dataset-id :snapshots snapshot) MAP-KEYS] datasets)

    (aor-types/TagSelector? selector)
    (let [tag (:tag selector)]
      (foreign-select [(keypath dataset-id :snapshots snapshot)
                       ALL
                       (selected? LAST :tags (view contains? tag) identity)
                       FIRST]
                      datasets))

    (aor-types/ExampleIdsSelector? selector)
    (vec (:example-ids selector))

    :else
    (throw (h/ex-info "Unexpected dataset selector type" {:type (class selector)}))
  ))

(defn all-evaluator-info
  [retriever {:keys [evaluators]}]
  (let [ds-evals    (evals-pstate retriever)
        local-evals (local-evals-pstate retriever)]
    (mapv
     (fn [{:keys [name remote?]}]
       (if-let [m (foreign-select-one (keypath name) (if remote? ds-evals local-evals))]
         (assoc m
          :name name
          :remote? remote?)
         {:retrieval-failed? true
          :name    name
          :remote? remote?}))
     evaluators)))

(defn relevant-eval-info
  [agent-node eval-info types]
  (let [builders  (get-evaluator-builders agent-node)
        relevant? (fn [{:keys [builder-name]}]
                    (contains? types
                               (-> builders
                                   (get builder-name)
                                   :type)))]
    (into {}
          (select [ALL (pred relevant?) (view #(vector (:name %) %))]
                  eval-info))))

(defn eval-info->evaluator
  [agent-node {:keys [name builder-name builder-params]}]
  (get-evaluator agent-node name builder-name builder-params))

(defn handle-experiment-start
  [agent-node
   {:keys [dataset-id snapshot spec]
    :as   experiment}]
  (let [local-ds    (local-datasets-store* agent-node)
        remote-info (store/pstate-select-one
                     [(keypath dataset-id :props)
                      (submap [:cluster-conductor-host :cluster-conductor-port :module-name])]
                     local-ds)]
    (with-retriever [agent-node experiment remote-info]
      [retriever]
      (let [datasets      (datasets-pstate retriever)

            eval-info     (all-evaluator-info retriever experiment)
            eval-problems
            (filterv some?
             (mapv
              (fn [{:keys [name remote?] :as evaluator}]
                (let [problem (validate-evaluator agent-node spec evaluator)]
                  (when problem
                    (assoc problem
                     :name name
                     :remote? remote?))))
              eval-info))]
        (cond
          (not-empty eval-problems)
          (c/result! agent-node
                     {:error    "Problem with one or more evaluators"
                      :problems eval-problems})

          (foreign-select-one
           [(keypath dataset-id) :props (view nil?)]
           datasets)
          (c/result! agent-node {:error "Dataset does not exist"})

          (foreign-select-one
           [(keypath dataset-id) :snapshots (keypath snapshot) (view nil?)]
           datasets)
          (c/result! agent-node {:error "Snapshot does not exist or has no examples"})

          :else
          (c/emit! agent-node "root" experiment remote-info))
      ))))

(defn handle-node-invoke
  [^AgentNode agent-node {:keys [agent-name node args]}]
  (let [^AgentDeclaredObjectsTaskGlobal declared-objects-tg (anode/get-declared-objects agent-node)
        node-fn (-> declared-objects-tg
                    .getAgentGraphs
                    (get agent-name)
                    :node-map
                    (get node)
                    :node
                    :node-fn)]
    (if (nil? node-fn)
      (c/result! agent-node
                 (aor-types/->AgentResult {:message "Node does not exist" :node node}
                                          true))
      (let [result-vol         (volatile! nil)
            emits-vol          (volatile! [])

            wrapper-agent-node
            (reify
             AgentNode
             (emit [this node args]
               (vswap! emits-vol conj {"node" node "args" (vec args)}))
             (result [this arg]
               (vreset! result-vol {:result arg}))
             (getAgentObject [this name]
               (.getAgentObject agent-node name))
             (getAgentClient [this name]
               (.getAgentClient agent-node name))
             (getStore [this name]
               (.getStore agent-node name))
             (streamChunk [this chunk])
             (recordNestedOp [this nestedOpType startTimeMillis finishTimeMillis info])
             (getHumanInput [this prompt]
               (.getHumanInput agent-node prompt)))]
        (try
          (apply node-fn wrapper-agent-node args)
          (if (some? @result-vol)
            (c/result! agent-node (:result @result-vol))
            (c/result! agent-node @emits-vol))
          (catch Throwable t
            (c/result!
             agent-node
             (aor-types/->AgentResult
              {:message "Failure executing node" :node node :args args :throwable t}
              true))))
      ))))

(defn parse-input-spec
  [s]
  (j/read-value
   (if (and (str/starts-with? s "$") (not (str/starts-with? s "$$")))
     (str "\"" s "\"")
     s)))

(defn resolve-input-spec
  [spec input]
  (cond
    (string? spec)
    (if (str/starts-with? spec "$")
      (if (str/starts-with? spec "$$")
        (subs spec 1)               ; "$$..." → "$..."
        (h/read-json-path input spec))
      spec)

    (vector? spec)
    (mapv #(resolve-input-spec % input) spec)

    (map? spec)
    (transform MAP-VALS #(resolve-input-spec % input) spec)

    :else spec))

(defn convert-input->args
  [input parsed-input->args]
  (mapv #(resolve-input-spec % input) parsed-input->args))

(defn agent-result-obj
  [client agent-invoke]
  (try
    (let [result (c/agent-result client agent-invoke)]
      (if (aor-types/AgentResult? result)
        result
        (aor-types/->AgentResult result false)))
    (catch Throwable t
      (aor-types/->AgentResult {:message "Failure on example" :throwable t} true)
    )))

(defn validate-results!
  [o]
  (when-not (and (map? o) (every? string? (keys o)))
    (throw
     (h/ex-info
      "Invalid map of results (must be map with string keys)"
      {:return o}))))

(defn non-summary-evaluate!
  [agent-node eval-type eval-fn input reference-output outputs]
  (cond
    (= eval-type :regular)
    (do
      (assert (= 1 (count outputs)))
      (eval-fn agent-node input reference-output (nth outputs 0)))

    (= eval-type :comparative)
    (eval-fn agent-node input reference-output outputs)

    :else
    (throw (h/ex-info "Unexpected experiment type" {:type eval-type}))))

(defn evaluate!
  [local-ds dataset-id eval-name prefix-path results-key failures-key runner-fn]
  (try
    (let [results (runner-fn)]
      (validate-results! results)
      (store/pstate-transform!
       [prefix-path (keypath results-key eval-name) (termval results)]
       local-ds
       dataset-id))
    (catch Throwable t
      (store/pstate-transform!
       [prefix-path
        (multi-path [(keypath results-key eval-name) NONE>]
                    [(keypath failures-key eval-name) (termval (h/throwable->str t))])]
       local-ds
       dataset-id)
    )))

(defn fetch-example-info
  [local-ds datasets id dataset-id snapshot result+example-ids]
  (vec
   (for [[result-id example-id] result+example-ids
         :let
         [{:keys [input reference-output]}
          (foreign-select-one
           [(keypath dataset-id :snapshots snapshot example-id)]
           datasets)

          info
          (store/pstate-select-one
           [(keypath dataset-id
                     :experiments
                     id
                     :results
                     result-id)
            (submap [:agent-results :evals])]
           local-ds)]
         :when (not (selected-any? [:agent-results MAP-VALS :result :failure? identity] info))]
     (do
       (when-not (= 1 (count (:agent-results info)))
         (throw
          (h/ex-info
           "Results when fetching example runs unexpectedly does not contain exactly one value"
           {:results (:agent-results info)})))
       (let [m (select-any [:agent-results MAP-VALS] info)
             start-time-millis (:start-time-millis m)
             finish-time-millis (:finish-time-millis m)]
         {:input input
          :reference-output reference-output
          :output (-> m
                      :result
                      :val)
          :latency-millis (when (and start-time-millis finish-time-millis)
                            (- finish-time-millis start-time-millis))
          :input-token-count (:input-token-count m)
          :output-token-count (:output-token-count m)
          :total-token-count (:total-token-count m)
          :evals (:evals info)}))
   )))

(defn merge-number-evals
  [eval-maps]
  (let [wrap (fn [m]
               (transform [MAP-VALS MAP-VALS]
                          (fn [v]
                            (cond
                              (number? v) [v]
                              (boolean? v) [(if v 1 0)]
                              :else NONE))
                          m))]
    (apply merge-with (partial merge-with into) (mapv wrap eval-maps))))

(def PERCENTILES [0.1 0.2 0.3 0.4 0.5 0.6 0.7 0.8 0.9 0.99 0.999])

(defn compute-number-stats
  [nums]
  (if (empty? nums)
    (aor-types/->valid-EvalNumberStats 0 0 0 0 {})
    (let [nums (vec (sort nums))
          c    (count nums)]
      (aor-types/->valid-EvalNumberStats
       (reduce + 0 nums)
       (long c)
       (nth nums 0)
       (nth nums (dec c))
       (reduce
        (fn [m p]
          (assoc m p (nth nums (long (* p c)))))
        {}
        PERCENTILES)))))

(defn compute-eval-number-stats
  [example-info]
  (->> (merge-number-evals (mapv :evals example-info))
       (setval [MAP-VALS empty?] NONE)
       (transform [MAP-VALS MAP-VALS] compute-number-stats)))

(defn maybe-get-json-path
  [jp v]
  (when v
    (if jp
      (h/read-json-path v jp)
      v)))

(defn to-eval-infos
  [agent-initiates]
  (reduce-kv
   (fn [res i {:keys [agent-name agent-invoke]}]
     (conj res
           (aor-types/->valid-EvalInfo agent-name agent-invoke)))
   []
   agent-initiates))

(defn hook:do-eval [eval-name])

(defn handle-eval-invoke
  [^AgentNode agent-node
   {:keys [input reference-output outputs eval-name builder-name builder-params eval-type
           eval-infos source]}]
  (hook:do-eval eval-name)
  (let [eval-fn (get-evaluator agent-node eval-name builder-name builder-params)
        res     (non-summary-evaluate!
                 agent-node
                 eval-type
                 eval-fn
                 input
                 reference-output
                 outputs)]
    (validate-results! res)
    (when (and (= :regular eval-type) (not (empty? eval-infos)))
      (assert (= 1 (count eval-infos)))
      (let [{:keys [agent-name target]} (nth eval-infos 0)]
        (cond
          (aor-types/AgentInvokeImpl? target)
          (store/pstate-transform!
           [(keypath (:agent-invoke-id target)) (fb/add-feedback-path res source)]
           (.getStore agent-node (po/agent-root-task-global-name agent-name))
           (aor-types/->DirectTaskId (:task-id target)))

          (aor-types/EvalNodeTarget? target)
          (store/pstate-transform!
           [(keypath (:agent-invoke-id target)) (fb/add-feedback-path res source)]
           (.getStore agent-node (po/agent-node-task-global-name agent-name))
           (aor-types/->DirectTaskId (:task-id target)))

          :else
          (throw (h/ex-info "Unexpected eval target" {:type (class target)}))
        )))
    (c/result! agent-node res)
  ))

(defn hook:running-invoke-node [result+example-ids])
(defn hook:initiate-target [i])
(defn hook:initiate-eval [i])
(defn hook:result-target [i])
(defn hook:do-summary-eval [eval-name])

(defn define-evaluator-agent!
  [topology]
  (->
    topology
    (c/new-agent EVALUATOR-AGENT-NAME)
    (c/node
     "start"
     "root"
     (fn [agent-node input]
       (cond
         (aor-types/StartExperiment? input)
         (handle-experiment-start agent-node input)

         (aor-types/ExperimentNodeInvoke? input)
         (handle-node-invoke agent-node input)

         (aor-types/EvalInvoke? input)
         (handle-eval-invoke agent-node input)

         :else
         (throw (h/ex-info "Unexpected evaluator agent input" {:type (class input)})))
     ))
    (c/agg-start-node
     "root"
     "invoke"
     (fn [agent-node
          {:keys [dataset-id snapshot selector concurrency num-repetitions]
           :as   experiment}
          remote-info]
       (with-retriever [agent-node experiment remote-info]
         [retriever]
         (let [datasets    (datasets-pstate retriever)
               example-ids (retrieve-all-examples-ids datasets dataset-id snapshot selector)
               result+example-ids (vec (for [i (range 0 (* num-repetitions (count example-ids)))]
                                         [i (nth example-ids (mod i (count example-ids)))]))
               chunks
               (h/split-into-n concurrency result+example-ids)]
           (doseq [c chunks]
             (when-not (empty? c)
               (c/emit! agent-node "invoke" experiment remote-info c)))
         ))
       [experiment remote-info]))
    (c/node
     "invoke"
     "evaluate"
     (fn [^AgentNode agent-node
          {:keys [id dataset-id snapshot spec] :as experiment}
          remote-info
          result+example-ids]
       (hook:running-invoke-node result+example-ids)
       (with-retriever [agent-node experiment remote-info]
         [retriever]
         (let [datasets     (datasets-pstate retriever)
               local-ds     (local-datasets-store retriever)
               targets      (aor-types/experiment-targets spec)
               num-targets  (count targets)
               clients      (mapv
                             (fn [{:keys [target-spec]}]
                               (if (aor-types/AgentTarget? target-spec)
                                 (.getAgentClient agent-node (:agent-name target-spec))
                                 (.getAgentClient agent-node EVALUATOR-AGENT-NAME)))
                             targets)
               source       (aor-types/->valid-ExperimentSourceImpl dataset-id id)
               initiate-fns
               (mapv
                (fn [{:keys [target-spec input->args]} client]
                  (let [agent-name         (:agent-name target-spec)
                        parsed-input->args (mapv parse-input-spec input->args)]
                    (fn [input]
                      (binding [aor-types/OPERATION-SOURCE source]
                        (let [args (convert-input->args input parsed-input->args)]
                          (if (aor-types/AgentTarget? target-spec)
                            {:agent-name   agent-name
                             :agent-invoke (apply c/agent-initiate client args)}
                            {:agent-name   EVALUATOR-AGENT-NAME
                             :agent-invoke (c/agent-initiate client
                                                             (aor-types/->valid-ExperimentNodeInvoke
                                                              agent-name
                                                              (:node target-spec)
                                                              args))})
                        )))))
                targets
                clients)]
           (doseq [[result-id example-id] result+example-ids]
             (let [{:keys [agent-initiates agent-results] :as currm}
                   (store/pstate-select-one
                    [(keypath dataset-id :experiments id :results result-id)]
                    local-ds)
                   input         (when (< (count agent-initiates) (count targets))
                                   (foreign-select-one
                                    [(keypath dataset-id :snapshots snapshot example-id :input)]
                                    datasets))
                   initiates-vol (volatile! [])]
               (when-not (some? (:example-id currm))
                 (store/pstate-transform!
                  [(keypath dataset-id :experiments id :results result-id :example-id)
                   (termval example-id)]
                  local-ds
                  dataset-id))
               (dotimes [i num-targets]
                 (hook:initiate-target i)
                 (if-let [info (get agent-initiates i)]
                   (vswap! initiates-vol conj info)
                   (let [info ((nth initiate-fns i) input)]
                     (store/pstate-transform!
                      [(keypath dataset-id :experiments id :results result-id :agent-initiates)
                       (nil->val (sorted-map))
                       (keypath i)
                       (termval info)]
                      local-ds
                      dataset-id)
                     (vswap! initiates-vol conj info)
                   )))
               (dotimes [i num-targets]
                 (hook:result-target i)
                 (if (nil? (get agent-results i))
                   (let [client      (nth clients i)

                         {:keys [task-id agent-invoke-id] :as agent-invoke}
                         (:agent-invoke (nth @initiates-vol i))

                         result      (agent-result-obj client agent-invoke)
                         root        (:root-pstate (aor-types/underlying-objects client))
                         ;; transferring timings allows it to persist even if underlying trace gets
                         ;; GC'd
                         {:keys [start-time-millis finish-time-millis stats]}
                         (foreign-select-one
                          [(keypath agent-invoke-id)
                           (submap [:start-time-millis :finish-time-millis :stats])]
                          root
                          {:pkey task-id})
                         basic-stats (ana/aggregated-basic-stats stats)]
                     (store/pstate-transform!
                      [(keypath dataset-id :experiments id :results result-id :agent-results)
                       (nil->val (sorted-map))
                       (keypath i)
                       (termval {:result             result
                                 :start-time-millis  start-time-millis
                                 :finish-time-millis finish-time-millis
                                 :input-token-count  (:input-token-count basic-stats)
                                 :output-token-count (:output-token-count basic-stats)
                                 :total-token-count  (:total-token-count basic-stats)
                                })]
                      local-ds
                      dataset-id)
                   )))
             ))
           (c/emit! agent-node "evaluate" experiment remote-info result+example-ids)
         ))))
    (c/node
     "evaluate"
     "finish"
     (fn [^AgentNode agent-node
          {:keys [id dataset-id snapshot spec] :as experiment}
          remote-info
          result+example-ids]
       (with-retriever [agent-node experiment remote-info]
         [retriever]
         (let [eval-info     (all-evaluator-info retriever experiment)
               eval-info-map (relevant-eval-info agent-node eval-info #{:regular :comparative})
               eval-client   (.getAgentClient agent-node EVALUATOR-AGENT-NAME)
               local-ds      (local-datasets-store retriever)
               datasets      (datasets-pstate retriever)
               local-ds      (local-datasets-store retriever)]
           (doseq [[result-id example-id] result+example-ids]
             (let [{:keys [input reference-output]}
                   (foreign-select-one
                    [(keypath dataset-id :snapshots snapshot example-id)]
                    datasets)

                   {curr-evals          :evals
                    eval-failures       :eval-failures
                    agent-initiates     :agent-initiates
                    agent-results       :agent-results
                    curr-eval-initiates :eval-initiates}
                   (store/pstate-select-one
                    [(keypath dataset-id :experiments id :results result-id)]
                    local-ds)

                   eval-initiates (volatile! curr-eval-initiates)
                   eval-counter (volatile! -1)]
               (when-not (selected-any? [MAP-VALS :result :failure? identity] agent-results)
                 (doseq [[eval-name
                          {:keys [input-json-path reference-output-json-path output-json-path
                                  builder-name builder-params]}]
                         eval-info-map

                         :when (and (not (contains? curr-evals eval-name))
                                    (not (contains? eval-failures eval-name)))]

                   (hook:initiate-eval (vswap! eval-counter inc))
                   (when-not (contains? @eval-initiates eval-name)
                     (let [inv
                           (c/agent-initiate
                            eval-client
                            (aor-types/->valid-EvalInvoke
                             (maybe-get-json-path input-json-path input)
                             (maybe-get-json-path reference-output-json-path reference-output)
                             (select [MAP-VALS
                                      :result
                                      :val
                                      (view (fn [v] (maybe-get-json-path output-json-path v)))]
                                     agent-results)
                             eval-name
                             builder-name
                             builder-params
                             (experiment-type->kw (class spec))
                             (to-eval-infos agent-initiates)
                             (aor-types/->valid-ExperimentSourceImpl dataset-id id)))]
                       (store/pstate-transform!
                        [(keypath dataset-id :experiments id :results result-id)
                         (keypath :eval-initiates eval-name)
                         (termval inv)]
                        local-ds
                        dataset-id)
                       (vswap! eval-initiates assoc eval-name inv)
                     ))

                   (let [res (.get ^CompletableFuture
                                   (aor-types/subagent-next-step-async eval-client
                                                                       (get @eval-initiates
                                                                            eval-name)))]
                     (evaluate!
                      local-ds
                      dataset-id
                      eval-name
                      (keypath dataset-id :experiments id :results result-id)
                      :evals
                      :eval-failures
                      #(if (aor-types/AgentCompleteImpl? (:result res))
                         (-> res
                             :result
                             :result)
                         (throw
                          (h/ex-info "Evaluator failed" {:eval-name eval-name} (:result res))))))
                 ))))
           (c/emit! agent-node "finish" result+example-ids)
         ))))
    (c/agg-node
     "finish"
     nil
     h/+concatv
     (fn [agent-node
          result+example-ids
          [{:keys [id dataset-id snapshot spec] :as experiment} remote-info]]
       (with-retriever [agent-node experiment remote-info]
         [retriever]
         (let [eval-info     (all-evaluator-info retriever experiment)
               eval-info-map (relevant-eval-info agent-node eval-info #{:summary})
               datasets      (datasets-pstate retriever)
               local-ds      (local-datasets-store retriever)]
           (when (aor-types/RegularExperiment? spec)
             (let [example-info
                   (fetch-example-info local-ds datasets id dataset-id snapshot result+example-ids)

                   {curr-evals :summary-evals
                    curr-failures :summary-eval-failures
                    curr-eval-number-stats :eval-number-stats
                    curr-latency-number-stats :latency-number-stats
                    curr-input-token-number-stats :input-token-number-stats
                    curr-output-token-number-stats :output-token-number-stats
                    curr-total-token-number-stats :total-token-number-stats}
                   (store/pstate-select-one
                    [(keypath dataset-id :experiments id)
                     (submap [:summary-evals :summary-eval-failures
                              :eval-number-stats :latency-number-stats
                              :input-token-number-stats :output-token-number-stats
                              :total-token-number-stats
                             ])]
                    local-ds)]
               (when (nil? curr-eval-number-stats)
                 (let [eval-stats (compute-eval-number-stats example-info)]
                   (store/pstate-transform!
                    [(keypath dataset-id :experiments id :eval-number-stats)
                     (termval eval-stats)]
                    local-ds
                    dataset-id)))
               (when (nil? curr-latency-number-stats)
                 (let [stats (compute-number-stats (mapv :latency-millis example-info))]
                   (store/pstate-transform!
                    [(keypath dataset-id :experiments id :latency-number-stats)
                     (termval stats)]
                    local-ds
                    dataset-id)))
               (when (nil? curr-input-token-number-stats)
                 (let [stats (compute-number-stats (mapv :input-token-count example-info))]
                   (store/pstate-transform!
                    [(keypath dataset-id :experiments id :input-token-number-stats)
                     (termval stats)]
                    local-ds
                    dataset-id)))
               (when (nil? curr-output-token-number-stats)
                 (let [stats (compute-number-stats (mapv :output-token-count example-info))]
                   (store/pstate-transform!
                    [(keypath dataset-id :experiments id :output-token-number-stats)
                     (termval stats)]
                    local-ds
                    dataset-id)))
               (when (nil? curr-total-token-number-stats)
                 (let [stats (compute-number-stats (mapv :total-token-count example-info))]
                   (store/pstate-transform!
                    [(keypath dataset-id :experiments id :total-token-number-stats)
                     (termval stats)]
                    local-ds
                    dataset-id)))
               (doseq [[eval-name
                        {:keys [input-json-path reference-output-json-path output-json-path]
                         :as   eval-map}]
                       eval-info-map

                       :when (and (not (contains? curr-evals eval-name))
                                  (not (contains? curr-failures eval-name)))
                       :let [eval-fn      (eval-info->evaluator agent-node eval-map)
                             example-runs
                             (mapv
                              (fn [{:keys [input reference-output output]}]
                                (aor-types/->ExampleRunImpl (maybe-get-json-path input-json-path
                                                                                 input)
                                                            (maybe-get-json-path
                                                             reference-output-json-path
                                                             reference-output)
                                                            (maybe-get-json-path output-json-path
                                                                                 output)))
                              example-info)]]
                 (hook:do-summary-eval eval-name)
                 (evaluate! local-ds
                            dataset-id
                            eval-name
                            (keypath dataset-id :experiments id)
                            :summary-evals
                            :summary-eval-failures
                            #(eval-fn agent-node example-runs))
               )))
           (store/pstate-transform!
            [(keypath dataset-id :experiments id :finish-time-millis)
             (termval (h/current-time-millis))]
            local-ds
            dataset-id)
           (c/result! agent-node :done)
         ))))
  ))

(deframaop handle-experiments-op
  [*data]
  (<<with-substitutions
   [$$datasets (po/datasets-task-global)
    *agent-depot (po/agent-depot-task-global EVALUATOR-AGENT-NAME)]
   (<<subsource *data
    (case> StartExperiment :> {:keys [*id *dataset-id]})
     (|hash *dataset-id)
     (h/current-time-millis :> *start-time-millis)
     (local-transform>
      [(keypath *dataset-id :experiments *id)
       (multi-path [:start-time-millis (termval *start-time-millis)]
                   [:experiment-info (termval *data)])]
      $$datasets)
     (ops/current-task-id :> *task-id)
     (|direct *task-id)
     ;; have to do it this way since cannot do :ack on the depot append since it's running as part
     ;; of the same stream topology
     (h/random-uuid7 :> *agent-invoke-id)
     (aor-types/->valid-AgentInitiate [*data] *agent-invoke-id nil :> *initiate)
     (depot-partition-append!
      *agent-depot
      *initiate
      :append-ack)
     (aor-types/->AgentInvokeImpl *task-id *agent-invoke-id :> *agent-invoke)
     (local-transform>
      [(keypath *dataset-id :experiments *id :experiment-invoke) (termval *agent-invoke)]
      $$datasets)
     (ack-return> *agent-invoke)

    (case> UpdateExperimentName :> {:keys [*id *dataset-id *name]})
     (|hash *dataset-id)
     (local-transform>
      [(must *dataset-id :experiments *id :experiment-info)
       :name
       (termval *name)]
      $$datasets)

    (case> DeleteExperiment :> {:keys [*id *dataset-id]})
     (|hash *dataset-id)
     (local-transform>
      [(must *dataset-id :experiments *id :results) NONE>]
      $$datasets)
     (|direct (ops/current-task-id))
     (local-transform>
      [(must *dataset-id :experiments *id) NONE>]
      $$datasets)
   )))
