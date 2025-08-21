(ns com.rpl.agent-o-rama.impl.queries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]
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

(defn agent-get-current-graph-name
  [agent-name]
  (str "_agent-get-current-graph-" agent-name))

(defn get-datasets-page-query-name
  []
  "_aor-get-datasets")

(defn search-datasets-name
  []
  "_aor-search-datasets")

(defn- to-pqueue
  [coll]
  (reduce conj PersistentQueue/EMPTY coll))

(defn- to-trace-invoke-info
  [all-invoke-info human-request]
  (let [all-invoke-info (if human-request
                          (assoc all-invoke-info :human-request human-request)
                          all-invoke-info)
        all-invoke-info
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

(defn pending-human-request
  [^AgentNodeExecutorTaskGlobal node-exec invoke-id]
  (.getHumanRequest node-exec invoke-id))

(defn declare-tracing-query-topology
  [topologies agent-name]
  (let [topo-name    (tracing-query-name agent-name)
        scratch-sym  (symbol (str "$$" topo-name "$$"))
        nodes-pstate (symbol (po/agent-node-task-global-name agent-name))
        node-exec    (symbol (po/agent-node-executor-name))]
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
          ;; - do it this way so that agg-invokes-map and
          ;; task-invoke-pairs
          ;; don't have to be potentially copied around the cluster for
          ;; every
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
          (pending-human-request node-exec *invoke-id :> *human-request)
          (to-trace-invoke-info (into {} *all-invoke-info)
                                *human-request
                                :> *invoke-info)
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

(defn- items-pqueue
  ^PriorityQueue [item-compare-extractor]
  (PriorityQueue.
   20
   (reify
    Comparator
    (compare [_ m1 m2]
      (compare (item-compare-extractor m2) (item-compare-extractor m1))))))

(defn to-page-result
  [pages-map page-size entity-id-key result-key item-compare-extractor]
  (let [pqueue       (items-pqueue item-compare-extractor)
        end-task-ids (volatile! #{})

        task-queues
        (transform
         [ALL (collect-one FIRST) LAST]
         (fn [task-id id->info]
           (when (< (count id->info) page-size)
             (vswap! end-task-ids conj task-id))
           (let [ret (items-pqueue item-compare-extractor)]
             (doseq [[id info] id->info]
               (.add ret
                     (assoc info
                      :task-id task-id
                      entity-id-key id)))
             ret
           ))
         pages-map)]
    (doseq [[_ ^PriorityQueue q] task-queues]
      (if-let [m (.poll q)]
        (.add pqueue m)))
    (let [ret
          (loop [ret []]
            (let [{:keys [task-id] :as item} (.poll pqueue)]
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
        (let [{:keys [task-id] :as item} (.poll pqueue)
              ^PriorityQueue q (get task-queues task-id)]
          (.add q item)))
      {result-key         ret
       :pagination-params (transform MAP-VALS
                                     (fn [^PriorityQueue q]
                                       (if-let [m (.poll q)]
                                         (get m entity-id-key)))
                                     task-queues)}
    )))

(defn to-invokes-page-result
  [pages-map page-size]
  (to-page-result pages-map
                  page-size
                  :agent-id
                  :agent-invokes
                  :start-time-millis))

(defn adjust-page-size
  [i]
  (if (= i 1) 3 (inc i)))

(defn relevant-invoke-submap
  [m]
  (let [ret (select-keys m
                         [:start-time-millis :finish-time-millis
                          :invoke-args :result :graph-version])]
    (assoc ret
     :human-request?
     (-> m
         :human-requests
         empty?
         not))))

;; returns map of form:
;; {:agent-invokes
;;   [{:task-id ... :agent-id ... :start-time-millis ...
;;     :finish-time-millis ... :invoke-args ... :result ...
;;     :graph-version ...}
;;    ...]
;;  :pagination-params {task-id end-id}}
(defn declare-get-distributed-page-topology
  [topologies query-name pstate-name info-transformer page-result-fn max-key-fn]
  (let [pstate-sym (symbol pstate-name)]
    (<<query-topology topologies
      query-name
      [*page-size *pagination-params :> *res]
      (|all)
      (ops/current-task-id :> *task-id)
      (get *pagination-params *task-id (max-key-fn) :> *end-id)
      (<<if (nil? *end-id)
        (identity [] :> *task-page)
       (else>)
        (local-select>
         [(sorted-map-range-to *end-id
                               {:inclusive? true
                                :max-amt    (adjust-page-size *page-size)})
          (transformed MAP-VALS info-transformer)]
         pstate-sym
         :> *task-page))
      (|origin)
      (aggs/+map-agg *task-id *task-page :> *pages-map)
      (page-result-fn *pages-map
                      (adjust-page-size *page-size)
                      :> *res)
    )))

(defn max-invoke-id
  []
  Long/MAX_VALUE)

(defn declare-get-invokes-page-topology
  [topologies agent-name]
  (declare-get-distributed-page-topology
   topologies
   (agent-get-invokes-page-query-name agent-name)
   (po/agent-root-task-global-name agent-name)
   relevant-invoke-submap
   to-invokes-page-result
   max-invoke-id))

(defn declare-agent-get-names-query-topology
  [topologies agent-names]
  (<<query-topology topologies
    (agent-get-names-query-name)
    [:> *res]
    (|origin)
    (identity agent-names :> *res)))

(defn declare-get-current-graph
  [topologies agent-name]
  (let [agent-graph-sym (symbol (po/agent-graph-task-global-name agent-name))]
    (<<query-topology topologies
      (agent-get-current-graph-name agent-name)
      [:> *res]
      (|origin)
      (graph/graph->historical-graph-info agent-graph-sym :> *res)
    )))

;; Datasets

(defn dataset-info
  [m]
  (into {} (:props m)))

(defn to-dataset-page-result
  [pages-map page-size]
  (to-page-result pages-map page-size :dataset-id :datasets :dataset-id))

(defn max-dataset-id
  []
  (java.util.UUID. -1 -1))


;; returns map of form:
;; {:datasets
;;   [{:task-id ... :dataset-id ... :name ... :description ...
;;     :input-json-schema ... :output-json-schema ... :created-at ...
;;     :modified-at ...}
;;    ...]
;;  :pagination-params {task-id end-id}}
(defn declare-get-datasets-page-topology
  [topologies]
  (declare-get-distributed-page-topology
   topologies
   (get-datasets-page-query-name)
   (po/datasets-task-global-name)
   dataset-info
   to-dataset-page-result
   max-dataset-id))

(defn search-pagination-size
  []
  1000)

(defn fetch-name
  [m]
  (-> m
      :props
      :name))

(def +concat
  (accumulator
   (fn [v]
     (path END (termval v)))
   :init-fn
   (constantly [])))

(defn contains-string?-pred
  [substring]
  (fn [s]
    (h/contains-string? (str/lower-case s) substring)))

(defn declare-search-datasets-topology
  [topologies]
  (let [datasets-sym (symbol (po/datasets-task-global-name))]
    (<<query-topology topologies
      (search-datasets-name)
      [*search-input *limit :> *res]
      (str/lower-case *search-input :> *search)
      (|all)
      (loop<- [*k nil
               *results []
               :> *l]
        (yield-if-overtime)
        (search-pagination-size :> *page-size)
        (local-select>
         (sorted-map-range-from *k {:max-amt *page-size :inclusive? false})
         datasets-sym
         :> *m)
        (select> (subselect ALL
                            (transformed LAST fetch-name)
                            (selected? LAST
                                       (pred (contains-string?-pred *search))))
          *m
          :> *matches)
        (concat *results *matches :> *new-results)
        (<<if (or> (< (count *m) *page-size) (> (count *new-results) *limit))
          (:> *new-results)
         (else>)
          (continue> (h/last-key *m) *new-results)
        ))
      (|origin)
      (+concat *l :> *items)
      (into {} (take *limit *items) :> *res)
    )))


(defn get-dataset-properties
  [datasets-pstate dataset-id]
  (foreign-select-one
   [(keypath dataset-id) :props]
   datasets-pstate
  ))

(defn get-dataset-snapshot-names
  [datasets-pstate dataset-id]
  (set
   (foreign-select
    [(keypath dataset-id) :snapshots MAP-KEYS some?]
    datasets-pstate
   )))

(defn get-dataset-examples-page
  ([datasets-pstate dataset-id snapshot-name amt]
   (get-dataset-examples-page datasets-pstate dataset-id snapshot-name amt nil))
  ([datasets-pstate dataset-id snapshot-name amt pagination-params]
   (let [examples (foreign-select-one
                   [(keypath dataset-id :snapshots snapshot-name)
                    (sorted-map-range-from pagination-params
                                           {:max-amt amt :inclusive? false})]
                   datasets-pstate
                  )]
     {:examples examples
      :pagination-params (when (= (count examples) amt)
                           (h/last-key examples))}
   )))
