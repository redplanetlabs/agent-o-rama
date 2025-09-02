(ns com.rpl.agent-o-rama.impl.experiments
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.clojure :as c]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentClient
    AgentNode]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]))

(def EXPERIMENTER-NAME "_aor-experimenter")

(defn get-cluster-retriever
  [agent-node]
  (.getClusterRetriever ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-this-module-name
  [agent-node]
  (.getThisModuleName ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-evaluator-builders
  [agent-node]
  (.getEvaluatorBuilders ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-evaluator
  [agent-node name builder-name params]
  (.getEvaluator ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)
                 name
                 builder-name
                 params))

(defmacro with-retriever
  [[agent-node experiment] [retriever-sym] & body]
  `(let [{cluster-conductor-host# :cluster-conductor-host
          module-name# :module-name}
         ~experiment

         ~'_ (when (and cluster-conductor-host# (nil? module-name#))
               (throw (h/ex-info "Must specify module when connecting to remote cluster"
                                 {:cluster-conductor-host cluster-conductor-host#})))

         retriever# (if cluster-conductor-host#
                      (open-cluster-manager {"conductor.host" cluster-conductor-host#})
                      (get-cluster-retriever ~agent-node))

         ~retriever-sym {:retriever retriever# :agent-node ~agent-node :experiment ~experiment}]
     (try
       ~@body
       (finally
         (when cluster-conductor-host#
           (close! retriever#))))))

(defn get-pstate
  [{:keys [agent-node retriever experiment]} pstate-name]
  (foreign-pstate
   retriever
   (or (:module-name experiment) (get-this-module-name agent-node))
   pstate-name))

(defn datasets-pstate
  [retriever]
  (get-pstate retriever (po/datasets-task-global-name)))

(defn evals-pstate
  [retriever]
  (get-pstate retriever (po/evaluators-task-global-name)))

(defn local-datasets-store
  [{:keys [agent-node]}]
  (.getStore ^AgentNode agent-node (po/datasets-task-global-name)))

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

(defn validate-evaluator
  [agent-node spec {:keys [builder-name] :as evaluator}]
  (let [builders (get-evaluator-builders agent-node)
        info     (get builders builder-name)]
    (cond
      (nil? evaluator)
      {:problem "Evaluator does not exist"}

      (nil? info)
      {:problem "Could not find associated builder" :builder-name builder-name}

      (not (contains? (valid-evaluator-types spec)) (:type info))
      {:problem         "Evaluator type does not match experiment"
       :experiment-type (class spec)
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
                      datasets)

      (aor-types/ExampleIdsSelector? selector)
      (:example-ids selector)

      :else
      (throw (h/ex-info "Unexpected dataset selector type" {:type (class selector)}))
    )))

(defn all-evaluator-info
  [retriever {:keys [evaluators]}]
  (let [ds-evals    (evals-pstate retriever)
        local-evals (local-evals-pstate retriever)]
    (mapv
     (fn [{:keys [name remote?]}]
       (if-let [m (foreign-select-one (keypath name) (if remote? ds-evals local-evals))]
         (assoc m
          :name name
          :remote? remote?)))
     evaluators)))

(defn relevant-evaluators
  [agent-node eval-info types]
  (let [builders  (get-evaluator-builders agent-node)
        relevant? (fn [{:keys [builder-name]}]
                    (contains? types
                               (-> builders
                                   (get builder-name)
                                   :type)))]
    (into {}
          (select [ALL
                   (pred relevant?)
                   (view #(vector (:name %)
                                  (get-evaluator agent-node
                                                 (:name %)
                                                 (:builder-name %)
                                                 (:builder-params %))))]
                  eval-info))))

(defn handle-experiment-start
  [agent-node
   {:keys [dataset-id snapshot spec]
    :as   experiment}]
  (with-retriever [agent-node experiment]
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
         [(keypath dataset-id) (view nil?)]
         datasets)
        (c/result! agent-node {:error "Dataset does not exist"})

        (foreign-select-one
         [(keypath dataset-id) :snapshots (keypath snapshot) (view nil?)]
         datasets)
        (c/result! agent-node {:error "Snapshot does not exist or has no examples"})

        :else
        (c/emit! agent-node "root" experiment))
    )))

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
               (vswap! emits-vol conj [{"node" node "args" (vec args)}]))
             (result [this arg]
               (vreset! result-vol {:result arg}))
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

(defn convert-input->args
  [input compiled-input->args]
  (mapv
   (fn [p]
     (h/read-compiled-json-path input p))
   compiled-input->args))

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

(defn non-summary-evaluate!
  [agent-node spec eval-fn input reference-output outputs]
  (cond
    (aor-types/RegularExperiment? spec)
    (do
      (assert (= 1 (count outputs)))
      (eval-fn agent-node input reference-output (nth outputs 0)))

    (aor-types/ComparativeExperiment? spec)
    (eval-fn agent-node input reference-output outputs)

    :else
    (throw (h/ex-info "Unexpected experiment spec" {:type (class spec)}))))

(defn evaluate!
  [local-ds dataset-id eval-name prefix-path results-key failures-key runner-fn]
  (try
    (let [results (runner-fn)]
      (when-not (map? results)
        (throw (h/ex-info "Evaluator did not return a map of results" {:return results})))
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

(defn fetch-example-runs
  [local-ds datasets name dataset-id snapshot example-ids]
  (vec
   (for [example-id example-ids
         :let
         [{:keys [input reference-output]}
          (foreign-select-one
           [(keypath dataset-id :snapshots snapshot example-id)]
           datasets)

          results
          (store/pstate-select-one
           [(keypath dataset-id
                     :experiments
                     name
                     :results
                     example-id
                     :agent-results)]
           local-ds)

          outputs (select [MAP-VALS :val] results)]
         :when      (not (selected-any? [MAP-VALS :failure? identity] results))]
     (aor-types/->ExampleRunImpl input reference-output outputs)
   )))

;; TODO: <<<<>>>> depot append when inintializing experiment state needs to write start-time-millis
(defn define-experiments-agent!
  [topology]
  (->
    topology
    (c/new-agent EXPERIMENTER-NAME)
    (c/node
     "start"
     "root"
     (fn [agent-node input]
       (if (aor-types/StartExperiment? input)
         (handle-experiment-start agent-node input)
         (handle-node-invoke agent-node input))
     ))
    (c/agg-start-node
     "root"
     "initiate"
     (fn [agent-node
          {:keys [dataset-id snapshot selector concurrency]
           :as   experiment}]
       (with-retriever [agent-node experiment]
         [retriever]
         (let [datasets    (datasets-pstate retriever)
               example-ids (retrieve-all-examples-ids datasets dataset-id snapshot selector)
               chunks      (h/split-into-n concurrency example-ids)]
           (doseq [c chunks]
             (when-not (empty? c)
               (c/emit! agent-node "invoke" experiment c)))
         ))
       experiment))
    (c/node
     "invoke"
     "evaluate"
     (fn [^AgentNode agent-node {:keys [name dataset-id snapshot spec] :as experiment} example-ids]
       (with-retriever [agent-node experiment]
         [retriever]
         (let [datasets     (datasets-pstate retriever)
               local-ds     (local-datasets-store retriever)
               targets      (aor-types/experiment-targets spec)
               num-targets  (count targets)
               clients      (mapv
                             (fn [{:keys [target-spec]}]
                               (.getAgentClient agent-node (:agent-name target-spec)))
                             targets)
               initiate-fns
               (mapv
                (fn [{:keys [target-spec input->args]} client]
                  (let [agent-name (:agent-name target-spec)
                        compiled-input->args (mapv h/compile-json-path input->args)
                       ]
                    (fn [input]
                      (let [args (convert-input->args input compiled-input->args)]
                        {:agent-name   agent-name
                         :agent-invoke
                         (if (aor-types/AgentTarget? target-spec)
                           (apply c/agent-initiate client args)
                           (c/agent-initiate client
                                             {:agent-name agent-name
                                              :node       (:node target-spec)
                                              :args       args}))})
                    )))
                targets
                clients)]
           (doseq [example-id example-ids]
             (let [{:keys [agent-initiates agent-results]}
                   (store/pstate-select-one
                    [(keypath dataset-id :experiments name :results example-id)]
                    local-ds)
                   input         (when (< (count agent-initiates) (count targets))
                                   (foreign-select-one
                                    [(keypath dataset-id :snapshots snapshot example-id :input)]
                                    datasets))
                   initiates-vol (volatile! [])
                   results-vol   (volatile! [])]
               (dotimes [i num-targets]
                 (if-let [info (get agent-initiates i)]
                   (vswap! initiates-vol conj info)
                   (let [info ((nth initiate-fns i) input)]
                     (store/pstate-transform!
                      [(keypath dataset-id :experiments name :results example-id :agent-initiates)
                       (nil->val (sorted-map))
                       (keypath i)
                       (termval info)]
                      local-ds
                      dataset-id)
                     (vswap! initiates-vol conj info)
                   )))
               (dotimes [i num-targets]
                 (if-let [result (get agent-results i)]
                   (vswap! results-vol conj result)
                   (let [result (agent-result-obj (nth clients i)
                                                  (:agent-invoke (nth @initiates-vol i)))]
                     (store/pstate-transform!
                      [(keypath dataset-id :experiments name :results example-id :agent-results)
                       (nil->val (sorted-map))
                       (keypath i)
                       (termval result)]
                      local-ds
                      dataset-id)
                     (vswap! results-vol conj result)
                   )))
             ))
           (c/emit! agent-node "evaluate" experiment example-ids)
         ))))
    (c/node
     "evaluate"
     "finish"
     (fn [agent-node {:keys [name dataset-id snapshot spec] :as experiment} example-ids]
       (with-retriever [agent-node experiment]
         [retriever]
         (let [eval-info  (all-evaluator-info retriever experiment)
               evaluators (relevant-evaluators agent-node eval-info #{:regular :comparative})
               local-ds   (local-datasets-store retriever)
               datasets   (datasets-pstate retriever)
               local-ds   (local-datasets-store retriever)]
           (doseq [example-id example-ids]
             (let [{:keys [input reference-output]}
                   (foreign-select-one
                    [(keypath dataset-id :snapshots snapshot example-id)]
                    datasets)

                   {curr-evals    :evals
                    eval-failures :eval-failures
                    agent-results :agent-results}
                   (store/pstate-select-one
                    [(keypath dataset-id :experiments name :results example-id)]
                    local-ds)]
               (when-not (selected-any? [MAP-VALS :failure? identity] agent-results)
                 (doseq [[eval-name eval-fn] evaluators
                         :when (and (not (contains? curr-evals eval-name))
                                    (not (contains? eval-failures eval-name)))]
                   (evaluate! local-ds
                              dataset-id
                              eval-name
                              (keypath dataset-id :experiments name :results example-id)
                              :evals
                              :eval-failures
                              #(non-summary-evaluate!
                                agent-node
                                spec
                                eval-fn
                                input
                                reference-output
                                (select [MAP-VALS :val] agent-results)))
                 ))))
           (c/emit! agent-node "finish" example-ids)
         ))))
    (c/agg-node
     "finish"
     nil
     h/+concatv
     (fn [agent-node example-ids {:keys [name dataset-id snapshot] :as experiment}]
       (with-retriever [agent-node experiment]
         [retriever]
         (let [eval-info  (all-evaluator-info retriever experiment)
               evaluators (relevant-evaluators agent-node eval-info #{:summary})
               datasets   (datasets-pstate retriever)
               local-ds   (local-datasets-store retriever)]
           (when-not (empty? evaluators)
             (let [example-runs
                   (fetch-example-runs local-ds datasets name dataset-id snapshot example-ids)

                   {curr-evals :summary-evals curr-failures :summary-eval-failures}
                   (store/pstate-select-one [(keypath dataset-id :experiments name)
                                             (submap [:summary-evals :summary-eval-failures])]
                                            local-ds)]
               (doseq [[eval-name eval-fn] evaluators
                       :when (and (not (contains? curr-evals eval-name))
                                  (not (contains? curr-failures eval-name)))]
                 (evaluate! local-ds
                            dataset-id
                            eval-name
                            (keypath dataset-id :experiments name)
                            :summary-evals
                            :summary-eval-failures
                            #(eval-fn agent-node example-runs))
               )))
           (store/pstate-transform!
            [(keypath dataset-id :experiments name :finish-time-millis)
             (termval (h/current-time-millis))]
            local-ds
            dataset-id)
           (c/result! agent-node :done)
         ))))
  ))
