(ns com.rpl.agent-o-rama.impl.queries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [clojure.lang
    PersistentQueue]
   [java.util
    Comparator
    PriorityQueue]))

(defn tracing-query-name
  [agent-name]
  (str "_agent-get-trace-page-" agent-name))

(defn agent-get-names-query-name
  []
  "_agents-get-names")

(defn agent-get-fork-affected-aggs-query-name
  [agent-name]
  (str "_agent-get-fork-affected-aggs-" agent-name))

(defn agent-get-invokes-page-query-name
  [agent-name]
  (str "_agent-get-invokes-page-" agent-name))

(defn fork-affected-aggs-query-task-global
  [agent-name]
  (this-module-query-topology-task-global
   (agent-get-fork-affected-aggs-query-name agent-name)))

(defn- to-pqueue
  [coll]
  (reduce conj PersistentQueue/EMPTY coll))

(defn- to-trace-invoke-info
  [all-invoke-info]
  (let [all-invoke-info
        (if (contains? all-invoke-info :agg-inputs)
          (let [ai       (:agg-inputs all-invoke-info)
                ai-count (count ai)]
            (-> all-invoke-info
                (dissoc :agg-inputs)
                (assoc :agg-input-count ai-count)
                (assoc :agg-inputs-first-10
                       (select-any (srange 0 (min 10 ai-count)) ai))))
          all-invoke-info
        )]
    (if (and (-> all-invoke-info
                 (contains? :finish-time-millis)
                 not)
             (-> all-invoke-info
                 (contains? :invoked-agg-invoke-id)
                 not))
      (assoc all-invoke-info :node-task-id (ops/current-task-id))
      all-invoke-info
    )))

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

(defn- invokes-pqueue
  ^PriorityQueue []
  (PriorityQueue.
   20
   (reify
    Comparator
    (compare [_ [_ _ a-millis] [_ _ b-millis]]
      (compare b-millis a-millis)))))

(defn to-invokes-page-result
  [pages-map page-size]
  (let [pqueue       (invokes-pqueue)
        end-task-ids (volatile! #{})

        task-queues
        (transform
         [ALL (collect-one FIRST) LAST]
         (fn [task-id id->start-time-millis]
           (when (< (count id->start-time-millis) page-size)
             (vswap! end-task-ids conj task-id))
           (let [ret (invokes-pqueue)]
             (doseq [[id start-time-millis] id->start-time-millis]
               (.add ret [task-id id start-time-millis]))
             ret
           ))
         pages-map)]
    (doseq [[_ ^PriorityQueue q] task-queues]
      (if-let [tuple (.poll q)]
        (.add pqueue tuple)))
    (let [ret
          (loop [ret []]
            (let [[task-id _ _ :as item] (.poll pqueue)]
              (if-not item
                ret
                (let [ret       (conj ret item)
                      ^PriorityQueue nextq (get task-queues task-id)
                      next-item (.poll nextq)]
                  (when next-item
                    (.add pqueue next-item))
                  (if (and (= 1 (.size nextq))
                           (not (contains? @end-task-ids task-id)))
                    ret
                    (recur ret)
                  )))))
          ;; the next one is definitely OK, so include it to ensure this always
          ;; returns at least page-size elems even if the latest items all came
          ;; from the same task
          ret (if-let [item (.poll pqueue)]
                (conj ret item)
                ret)]
      (while (not (.isEmpty pqueue))
        (let [[task-id _ _ :as item] (.poll pqueue)
              ^PriorityQueue q       (get task-queues task-id)]
          (.add q item)))
      {:agent-invokes     ret
       :pagination-params (transform MAP-VALS
                                     (fn [^PriorityQueue q]
                                       (if-let [[_ id _] (.poll q)]
                                         id))
                                     task-queues)}
    )))

(defn adjust-page-size
  [i]
  (if (= i 1) 3 (inc i)))

;; returns map of form:
;; {:agent-invokes [[task-id agent-id start-time-millis] ...]
;;  :pagination-params {task-id end-id}}
(defn declare-get-invokes-page-topology
  [topologies agent-name]
  (let [root-sym (symbol (po/agent-root-task-global-name agent-name))]
    (<<query-topology topologies
      (agent-get-invokes-page-query-name agent-name)
      [*page-size *pagination-params :> *res]
      (|all)
      (ops/current-task-id :> *task-id)
      (get *pagination-params *task-id Long/MAX_VALUE :> *end-id)
      (<<if (nil? *end-id)
        (identity [] :> *task-page)
       (else>)
        (local-select>
         [(sorted-map-range-to *end-id
                               {:inclusive? true
                                :max-amt    (adjust-page-size *page-size)})
          (transformed MAP-VALS :start-time-millis)]
         root-sym
         :> *task-page))
      (|origin)
      (aggs/+map-agg *task-id *task-page :> *pages-map)
      (to-invokes-page-result *pages-map
                              (adjust-page-size *page-size)
                              :> *res)
    )))

(defn declare-agent-get-names-query-topology
  [topologies agent-names]
  (<<query-topology topologies
    (agent-get-names-query-name)
    [:> *res]
    (|origin)
    (identity agent-names :> *res)))
