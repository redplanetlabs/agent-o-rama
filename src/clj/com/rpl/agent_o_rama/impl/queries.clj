(ns com.rpl.agent-o-rama.impl.queries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [clojure.lang
    PersistentQueue]))

(defn tracing-query-name
  [agent-name]
  (str "_agent-get-trace-page-" agent-name))

(defn agent-get-names-query-name
  []
  "_agents-get-names")

(defn agent-get-fork-affected-aggs-query-name
  [agent-name]
  (str "_agent-get-fork-affected-aggs-" agent-name))

(defn fork-affected-aggs-query-task-global
  [agent-name]
  (this-module-query-topology-task-global
   (agent-get-fork-affected-aggs-query-name agent-name)))

(defn- to-pqueue
  [coll]
  (reduce conj PersistentQueue/EMPTY coll))

(defn- to-trace-invoke-info
  [all-invoke-info]
  (if (contains? all-invoke-info :agg-inputs)
    (let [ai       (:agg-inputs all-invoke-info)
          ai-count (count ai)]
      (-> all-invoke-info
          (dissoc :agg-inputs)
          (assoc :agg-input-count ai-count)
          (assoc :agg-inputs-first-10
                 (select-any (srange 0 (min 10 ai-count)) ai))))
    all-invoke-info
  ))

(defn- emits->pairs
  [emits]
  (mapv (fn [emit] [(:target-task-id emit) (:invoke-id emit)]) emits))

(defn declare-tracing-query-topology
  [topologies agent-name]
  (let [topo-name    (tracing-query-name agent-name)
        scratch-sym  (symbol (str "$$" topo-name "$$"))
        nodes-pstate (symbol (po/agent-node-task-global-name agent-name))]
    (<<query-topology topologies
      topo-name
      [*agent-task-id *task-invoke-pairs *limit :> *res]
      (|direct *agent-task-id)
      (loop<- [*invokes-map {}
               *task-invoke-pairs (to-pqueue *task-invoke-pairs)
               :> *invokes-map *next-task-invoke-pairs]
        (<<if (or> (= *limit (count *invokes-map))
                   (empty? *task-invoke-pairs))
          (:> *invokes-map (vec *task-invoke-pairs))
         (else>)
          (peek *task-invoke-pairs :> [*task-id *invoke-id])
          (pop *task-invoke-pairs :> *next-task-invoke-pairs)
          ;; - do it this way so that agg-invokes-map and task-invoke-pairs
          ;; don't have to be potentially copied around the cluster for every
          ;; fetch
          ;; - only *invoke-id, *agent-task-id, and *invoke-info cross
          ;; partitioner boundaries
          (local-transform> (termval {:ti *next-task-invoke-pairs
                                      :m  *invokes-map})
                            scratch-sym)
          (|direct *task-id)
          (local-select> (keypath *invoke-id)
                         nodes-pstate
                         :> *all-invoke-info)
          (to-trace-invoke-info (into {} *all-invoke-info) :> *invoke-info)
          (|direct *agent-task-id)
          (local-select> STAY scratch-sym :> {*p :ti *m :m})
          (emits->pairs (get *invoke-info :emits) :> *pairs)
          (<<if (get *invoke-info :started-agg?)
            (conj *pairs
                  [*agent-task-id (get *invoke-info :agg-invoke-id)]
                  :> *new-pairs)
           (else>)
            (identity *pairs :> *new-pairs))
          (continue> (assoc *m *invoke-id *invoke-info)
                     (reduce conj *p *new-pairs))
        ))
      (|origin)
      (hash-map :invokes-map
                *invokes-map
                :next-task-invoke-pairs
                *next-task-invoke-pairs
                :> *res)
    )))

(defn declare-fork-affected-aggs-query-topology
  [topologies agent-name]
  (let [root-sym  (symbol (po/agent-root-task-global-name agent-name))
        nodes-sym (symbol (po/agent-node-task-global-name agent-name))]
    (<<query-topology topologies
      (agent-get-fork-affected-aggs-query-name agent-name)
      [*agent-task-id *agent-id *forked-invoke-ids-set :> *res]
      (|direct *agent-task-id)
      (local-select> [(keypath *agent-id) :root-invoke-id]
                     root-sym
                     :> *root-invoke-id)
      (loop<- [*invoke-id *root-invoke-id
               *agg-context #{}
               :> *agg-context]
        (local-select> (keypath *invoke-id)
                       nodes-sym
                       :> {:keys [*started-agg? *emits *agg-invoke-id *node]})
        (<<if *started-agg?
          (conj *agg-context *invoke-id :> *curr-agg-context)
         (else>)
          (identity *agg-context :> *curr-agg-context))
        (<<if (contains? *forked-invoke-ids-set *invoke-id)
          (:> *curr-agg-context))
        (anchor> <root>)
        (<<if *started-agg?
          (identity *agg-invoke-id :> *next-invoke-id)
          (identity *agg-context :> *next-agg-context)
          (anchor> <agg>))
        (hook> <root>)
        (ops/explode *emits
                     :> {*next-invoke-id :invoke-id
                         *task-id        :target-task-id})
        (identity *curr-agg-context :> *next-agg-context)
        (|direct *task-id)
        (anchor> <reg>)

        (unify> <agg> <reg>)
        (continue> *next-invoke-id *next-agg-context))
      (ops/explode *agg-context :> *invoke-id)
      (|origin)
      (aggs/+set-agg *invoke-id :> *res)
    )))

(defn declare-agent-get-names-query-topology
  [topologies agent-names]
  (<<query-topology topologies
    (agent-get-names-query-name)
    [:> *res]
    (|origin)
    (identity agent-names :> *res)))
