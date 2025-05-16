(ns com.rpl.agent-o-rama.impl.queries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po])
  (:import
   [clojure.lang
    PersistentQueue]))

(defn tracing-query-topology-name
  [name]
  (str "get-trace-page-" name))

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
  [topologies name]
  (let [topo-name    (tracing-query-topology-name name)
        scratch-sym  (symbol (str "$$" topo-name "$$"))
        nodes-pstate (symbol (po/agent-node-task-global-name name))]
    (<<query-topology topologies
      topo-name
      [*graph-task-id *task-invoke-pairs *limit :> *res]
      (|direct *graph-task-id)
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
          ;; - only *invoke-id, *graph-task-id, and *invoke-info cross
          ;; partitioner boundaries
          (local-transform> (termval {:ti *next-task-invoke-pairs
                                      :m  *invokes-map})
                            scratch-sym)
          (|direct *task-id)
          (local-select> (keypath *invoke-id)
                         nodes-pstate
                         :> *all-invoke-info)
          (to-trace-invoke-info (into {} *all-invoke-info) :> *invoke-info)
          (|direct *graph-task-id)
          (local-select> STAY scratch-sym :> {*p :ti *m :m})
          (emits->pairs (get *invoke-info :emits) :> *pairs)
          (<<if (get *invoke-info :started-agg?)
            (conj *pairs
                  [*graph-task-id (get *invoke-info :agg-invoke-id)]
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
