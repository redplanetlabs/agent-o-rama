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
   [com.rpl.rama.ops :as ops]))


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
                      (anode/get-cluster-retriever ~agent-node))

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
   (or (:module-name experiment) (anode/get-this-module-name agent-node))
   pstate-name))

(defn datasets-pstate
  [retriever]
  (get-pstate retriever (po/datasets-task-global-name)))

(defn evals-pstate
  [retriever]
  (get-pstate retriever (po/evaluators-task-global-name)))

(defn local-evals-pstate
  [{:Keys [agent-node]}]
  (foreign-pstate
   (anode/get-cluster-retriever agent-node)
   (anode/get-this-module-name agent-node)
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
  (let [builders (anode/get-evaluator-builders agent-node)
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

(defn define-experiments-agent
  [topology]
  (->
    topology
    (c/new-agent "_aor-experimenter")
    (c/node
     "start"
     "root"
     (fn [agent-node
          {:keys [cluster-conductor-host module-name dataset-id snapshot evaluators spec]
           :as   experiment}]
       (with-retriever [agent-node experiment]
         [retriever]
         (let [datasets      (datasets-pstate retriever)
               ds-evals      (evals-pstate reriever)
               local-evals   (local-evals-pstate retriever)
               eval-problems
               (filterv some?
                (mapv
                 (fn [{:keys [name remote?]}]
                   (let [evaluator (foreign-select-one (keypath name)
                                                       (if remove?
                                                         ds-evals
                                                         local-evals))
                         problem   (validate-evaluator agent-node spec evaluator)]
                     (when problem
                       (assoc problem
                        :name name
                        :remote? remote?))))
                 evaluators))]
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
             (let []
               ;; TODO: <<<<>>>>
               ;;  - look at dataset spec to divvy up the work, emitting concurrency times




             ))


         ))


       ;; TODO: <<<<>>>> different agent for comparative? probably not
       ;; - may have summary evaluators here though
       ;; - could just pass the whole spec down and figure out if there are summary evaluators
       ;; in last node
     ))
    (c/agg-start-node
     "root"
     "evaluate"
     (fn [agent-node
          {:keys [name cluster-conductor-host module-name dataset-id snapshot selector evaluators
                  spec concurrency]}]

     ))
    (c/node
     "evaluate"
     "finish"
     (fn [agent-node example-range evaluators]
     ))
  )


)

; (drp/defrecord+ StartExperiment
;   [name :- String
;    cluster-conductor-host :- (s/maybe String)
;    module-name :- (s/maybe String)
;
;    dataset-id :- UUID
;    snapshot :- (s/maybe String)
;    selector :- (s/maybe ExperimentInputSelector)
;    evaluators :- [EvaluatorSelector]
;
;    spec :- ExperimentSpec
;
;    max-concurrency :- Long
;   ])

;; TODO: <<<<>>>>
;;  - define agent here...
;;    - really want to use Clojure API for this
;;      - but impl/core needs to be able to reference this namespace
;;    - API one is currently referencing one in impl/core
