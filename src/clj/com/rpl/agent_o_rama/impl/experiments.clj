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
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    AgentNode]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]))

(defn get-cluster-retriever
  [agent-node]
  (.getClusterRetriever ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-this-module-name
  [agent-node]
  (.getThisModuleName ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objects agent-node)))

(defn get-evaluator-builders
  [agent-node]
  (.getEvaluatorBuilders ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objectsagent-node)))

(defn get-evaluator
  [agent-node name builder-name params]
  (.getEvaluator ^AgentDeclaredObjectsTaskGlobal (anode/get-declared-objectsagent-node)
                 name
                 builder-name
                 params))

(defmacro with-retriever
  [[agent-node experiment] [retriever-sym] & body]
  `(let [{cluster-conductor-host# :cluster-conductor-host
          module-name# :module-name}
         ~experiment

         _ (when (and cluster-conductor-host# (nil? module-name#))
             (throw (h/ex-info "Must specify module when connecting to remote cluster"
                               {:cluster-conductor-host cluster-conductor-host#})))

         retriever# (if cluster-conductor-host#
                      (open-cluster-manager {"conductor.host" cluster-conductor-host#})
                      (get-cluster-retriever ~agent-node))

         retriever-sym# {:retriever retriever :agent-node agent-node :experiment experiment}]
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

(defn local-datasets-pstate
  [{:Keys [agent-node]}]
  (foreign-pstate
   (get-cluster-retriever agent-node)
   (get-this-module-name agent-node)
   (po/datasets-task-global-name)))

(defn local-evals-pstate
  [{:Keys [agent-node]}]
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
  [agent-node spec #{:keys [builder-name] :as evaluator}]
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
  (let [ds-evals    (evals-pstate reriever)
        local-evals (local-evals-pstate retriever)]
    (mapv
     (fn [{:keys [name remote?]}]
       (foreign-select-one (keypath name) (if remote? ds-evals local-evals)))
     evaluators)))

(defn relevant-evaluators
  [agent-node eval-info types]
  (let [builders  (get-evaluator-builders agent-node)
        relevant? (fn [{:keys [builder-name]}]
                    (contains? types
                               (-> builders
                                   (get builder-name)
                                   :type)))]
    (select [ALL
             (pred relevant?)
             (view #(get-evaluator agent-node
                                   (:name %)
                                   (:builder-name %)
                                   (:builder-params %)))]
            eval-info)))

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
            (fn [evaluator]
              (let [problem (validate-evaluator agent-node spec evaluator)]
                (when problem
                  (assoc problem
                   :name name
                   :remote? remote?))))
            eval-info))]
      (cond
        (not-empty eval-problems)
        (aor/result! agent-node
                     {:error    "Problem with one or more evaluators"
                      :problems eval-problems})

        (foreign-select-one
         [(keypath dataset-id) (view nil?)]
         datasets)
        (aor/result! agent-node {:error "Dataset does not exist"})

        (foreign-select-one
         [(keypath dataset-id) :snapshots (keypath snapshot) (view nil?)]
         datasets)
        (aor/result! agent-node {:error "Snapshot does not exist or has no examples"})

        :else
        (aor/emit! agent-node "root" experiment))
    )))

(defn handle-node-invoke
  [^AgentNode agent-node {:keys [agent-name node args]}]
  (let [^AgentDeclaredObjectsTaskGlobal declared-objects-tg (anode/get-declared-objects agent-node)
        node-fn (-> agent-node
                    .getAgentGraphs
                    (get agent-name)
                    :node-map
                    (get node)
                    :node
                    :node-fn)]
    (if (nil? node-fn)
      (aor/result! agent-node
                   (aor-types/->ExperimentFailure "Node does not exist" {:node node} nil))
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
            (aor/result! agent-node (:result @result-vol))
            (aor/result! agent-node @emits-vol))
          (catch Throwable t
            (aor/result!
             agent-node
             (aor-types/->ExperimentFailure "Failure executing node" {:node node :args args} t))))
      ))))


(defn define-experiments-agent!
  [topology]
  (->
    topology
    (c/new-agent "_aor-experimenter")
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
               (aor/emit! agent-node "initiate" experiment c)))
         ))
       experiment))
    (c/node
     "initiate"
     "evaluate"
     (fn [agent-node experiment example-ids]
       (with-retriever [agent-node
                        {:keys [name dataset-id] :as experiment}
                        example-ids]
         [retriever]
         (let [datasets (datasets-pstate retriever)]
           (reduce
            (fn [m example-id]
                ;; TODO: <<<<>>>
                ;;  - look at spec and initiate all agents
                ;;    - need to fetch dataset example input
                ;;    - need to create arguments from experiment spec input->args
                ;;  - TODO: <<<>>>
                ;;    - what if have an agent that can just execute a single node of another agent,
                ;;    and package it's emits
                ;;      - don't want the overhead of an entire other agent...
                ;;        - could make this agent capable of doing it with a dispatch at the root...
                ;;  - assoc into m example-id->[agent-invokes]
                ;;      - TODO: <<<<>>> what about for individual node?
            )
            {}
            example-ids
           )



         ))))
    (c/node
     "evaluate"
     "finish"
     (fn [agent-node experiment example-id->invoke-ids]
       (with-retriever [agent-node
                        {:keys [name dataset-id] :as experiment}
                        example-ids]
         [retriever]
         (let [eval-info  (all-evaluator-info retriever experiment)
               evaluators (relevant-evaluators agent-node eval-info #{:regular :comparative})
               local-ds   (local-datasets-pstate retriever)
               datasets   (datasets-pstate retriever)]
           (doseq [example-id example-ids]

           )
           ;; TODO: <<<<>>>>
           ;;  - skip if already recorded results for this example ID
           ;;     - how to store agent output vs. evaluators? probably different keys in PState so
           ;;     UI can distinguish
           ;;     - would be nice not to have to run agents multiple times if there's a node
           ;;     failure
           ;;     - seems like this node should initiate agent invokes, and should get results in
           ;;     next node
           ;;     - need to keep track of failures

           ; (doseq [{:keys [builder-name builder-params]}]
           ;
           ;
           ;   )

         ))))
    (c/agg-node
     "finish"
     nil
     aggs/+vec-agg ; doesn't matter
     (fn [agent-node _ experiment]
         ;; TODO: <<<<>>>> run summary evaluators if appropriate
     ))
  ))
