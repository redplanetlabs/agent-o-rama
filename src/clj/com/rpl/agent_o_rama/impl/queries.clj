(ns com.rpl.agent-o-rama.impl.queries
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.evaluators :as evals]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    AgentNodeExecutorTaskGlobal]
   [clojure.lang
    PersistentQueue]
   [java.util
    Comparator
    PriorityQueue]
   [java.util.concurrent
    CompletableFuture]))

(defn tracing-query-name
  []
  "_agent-get-trace-page")

(defn agent-get-names-query-name
  []
  "_agents-get-names")

(defn agent-get-fork-affected-aggs-query-name
  []
  "_agent-get-fork-affected-aggs")

(defn agent-get-invokes-page-query-name
  []
  "_agent-get-invokes-page")

(defn fork-affected-aggs-query-task-global
  []
  (this-module-query-topology-task-global
   (agent-get-fork-affected-aggs-query-name)))

(defn agent-get-current-graph-name
  []
  "_agent-get-current-graph")

(defn action-log-page-name
  []
  "_agent-get-action-log-page")

(defn search-metadata-name
  []
  "_agent-search-metadata")

(defn all-agent-metrics-name
  []
  "_agent-all-metrics")

(defn get-datasets-page-query-name
  []
  "_aor-get-datasets")

(defn search-datasets-name
  []
  "_aor-search-datasets")

(defn multi-examples-name
  []
  "_aor-multi-examples")

(defn all-evaluator-builders-name
  []
  "_aor-all-evaluator-builders")

(defn try-evaluator-name
  []
  "_aor-try-evaluator")

(defn search-examples-name
  []
  "_aor-search-dataset-examples")

(defn search-evaluators-name
  []
  "_aor-search-evaluators")

(defn search-experiments-name
  []
  "_aor-search-experiments")

(defn experiment-results-name
  []
  "_aor-experiment-results")


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
  [topologies]
  (let [topo-name   (tracing-query-name)
        scratch-sym (symbol (str "$$" topo-name "$$"))
        node-exec   (symbol (po/agent-node-executor-name))]
    (<<query-topology topologies
      topo-name
      [*agent-name *agent-task-id *task-invoke-pairs *limit :> *res]
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
          (po/agent-node-task-global *agent-name :> $$nodes)
          (local-select> (keypath *invoke-id)
                         $$nodes
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
  [topologies]
  (<<query-topology topologies
    (agent-get-fork-affected-aggs-query-name)
    [*agent-name *agent-task-id *agent-id *forked-invoke-ids-set :> *res]
    (|direct *agent-task-id)
    (po/agent-root-task-global *agent-name :> $$root)
    (local-select> [(keypath *agent-id) :root-invoke-id]
                   $$root
                   :> *root-invoke-id)
    (loop<- [*invoke-id *root-invoke-id
             *agg-context #{}
             :> *agg-context]
      (po/agent-node-task-global *agent-name :> $$nodes)
      (local-select> (keypath *invoke-id)
                     $$nodes
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
  ))

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

(defbasicblocksegmacro get-distributed-page*
  [page-size pagination-params pstate-name res info-transformer page-result-fn max-key-fn initial-path]
  (let [task-id-sym (gen-anyvar "task-id")
        end-id-sym (gen-anyvar "end-id")
        task-page-sym (gen-anyvar "task-page")
        pages-map-sym (gen-anyvar "pages-map")]
    [[|all]
     [ops/current-task-id :> task-id-sym]
     [get pagination-params task-id-sym (seg# max-key-fn) :> end-id-sym]
     [<<if (seg# nil? end-id-sym)
       [identity [] :> task-page-sym]
      [else>]
       [local-select>
        [initial-path
         (seg# sorted-map-range-to end-id-sym
                                   {:inclusive? true
                                    :max-amt    (seg# adjust-page-size page-size)})
         (seg# transformed MAP-VALS info-transformer)]
        (seg# this-module-pobject-task-global pstate-name)
        :> task-page-sym]]
     [|origin]
     [aggs/+map-agg task-id-sym task-page-sym :> pages-map-sym]
     [page-result-fn pages-map-sym
                     (seg# adjust-page-size page-size)
                     :> res]
     ]))

;; returns map of form:
;; {:agent-invokes
;;   [{:task-id ... :agent-id ... :start-time-millis ...
;;     :finish-time-millis ... :invoke-args ... :result ...
;;     :graph-version ...}
;;    ...]
;;  :pagination-params {task-id end-id}}
(defn declare-get-distributed-page-topology
  [topologies query-name pstate-name info-transformer page-result-fn max-key-fn]
  (<<query-topology topologies
    query-name
    [*page-size *pagination-params :> *res]
    (get-distributed-page* *page-size
                           *pagination-params
                           pstate-name
                           *res
                           info-transformer
                           page-result-fn
                           max-key-fn
                           STAY)
  ))


;; like declare-get-distributed-page-topology, but for agent-specific PStates
(defn declare-get-agent-distributed-page-topology
  [topologies query-name pstate-name-fn info-transformer page-result-fn max-key-fn]
  (<<query-topology topologies
    query-name
    [*agent-name *page-size *pagination-params :> *res]
    (pstate-name-fn *agent-name :> *pstate-name)
    (get-distributed-page* *page-size
                           *pagination-params
                           *pstate-name
                           *res
                           info-transformer
                           page-result-fn
                           max-key-fn
                           STAY)
  ))

(defn declare-get-invokes-page-topology
  [topologies]
  (declare-get-agent-distributed-page-topology
   topologies
   (agent-get-invokes-page-query-name)
   po/agent-root-task-global-name
   relevant-invoke-submap
   to-invokes-page-result
   h/max-uuid))

(defn declare-agent-get-names-query-topology
  [topologies agent-names]
  (<<query-topology topologies
    (agent-get-names-query-name)
    [:> *res]
    (|origin)
    (identity agent-names :> *res)))

(defn declare-get-current-graph
  [topologies]
  (<<query-topology topologies
    (agent-get-current-graph-name)
    [*agent-name :> *res]
    (|origin)
    (graph/graph->historical-graph-info (po/agent-graph-task-global *agent-name)
                                        :> *res)
  ))

;; Datasets

(defn dataset-info
  [m]
  (into {} (:props m)))

(defn to-dataset-page-result
  [pages-map page-size]
  (to-page-result pages-map page-size :dataset-id :datasets :dataset-id))


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
   h/max-uuid))

(defn search-pagination-size
  []
  1000)

(defn fetch-name
  [m]
  (-> m
      :props
      :name))

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
      (h/+concatv *l :> *items)
      (into {} (take *limit *items) :> *res)
    )))

(defn multi-keypath
  [l]
  (apply multi-path (mapv keypath l)))

;; returns map from example-id -> all example info
(defn declare-multi-examples-query-topology
  [topologies]
  (let [datasets-sym (symbol (po/datasets-task-global-name))]
    (<<query-topology topologies
      (multi-examples-name)
      [*dataset-id *snapshot *example-ids :> *res]
      (|hash *dataset-id)
      (multi-keypath *example-ids :> *examples-nav)
      (local-select>
       [(keypath *dataset-id :snapshots *snapshot)
        (subselect *examples-nav)]
       datasets-sym
       :> *values)
      (zipmap *example-ids *values :> *res)
      (|origin)
    )))

(defn all-evaluator-builders-without-builder-fns
  []
  (setval [MAP-VALS :builder-fn]
          NONE
          (evals/all-evaluator-builders)))

(defn declare-all-evaluator-builders-query-topology
  [topologies]
  (<<query-topology topologies
    (all-evaluator-builders-name)
    [:> *res]
    (|origin)
    (all-evaluator-builders-without-builder-fns :> *res)))

(defn evaluator-event
  [^CompletableFuture cf name eval-type builder-name builder-params params]
  (let [declared-objects-tg (po/agent-declared-objects-task-global)
        fetcher (anode/mk-fetcher)]
    (fn []
      (try
        (let [eval-fn (.getEvaluator declared-objects-tg
                                     name
                                     builder-name
                                     builder-params)]
          (h/thread-local-set!
           AgentDeclaredObjectsTaskGlobal/ACQUIRE_TIMEOUT_MILLIS
           30000)
          (.complete
           cf
           (cond
             (= eval-type :regular)
             (eval-fn fetcher
                      (get params "input")
                      (get params "referenceOutput")
                      (get params "output"))

             (= eval-type :comparative)
             (eval-fn fetcher
                      (get params "input")
                      (get params "referenceOutput")
                      (get params "outputs"))

             (= eval-type :summary)
             (eval-fn fetcher
                      (get params "exampleRuns"))

             :else
             (throw (h/ex-info "Invalid evaluator type"
                               {:type eval-type}))
           ))
        )
        (catch Throwable t
          (.completeExceptionally cf t))
        (finally
          (anode/release-acquired-objects! fetcher)))
    )))

(defn declare-try-evaluator-query-topology
  [topologies]
  (let [evals-pstate-sym (symbol (po/evaluators-task-global-name))]
    (<<query-topology topologies
      (try-evaluator-name)
      [*name *type *builder-name *builder-params *params :> *res]
      (h/mk-completable-future :> *cf)
      (anode/submit-virtual-task! nil
                                  (evaluator-event *cf
                                                   *name
                                                   *type
                                                   *builder-name
                                                   *builder-params
                                                   *params))
      (completable-future> *cf :> *res)
      (|origin))))

(defn conj-vol!
  [v item]
  (vswap! v conj item))

(defn dissoc-all
  [m ks]
  (apply dissoc m ks))

(defn next-search-key
  [m reverse?]
  (if reverse? (h/first-key m) (h/last-key m)))

(deframaop search-loop
  [$$p *map-path %filter *limit *next-key *reverse?]
  (ramafn> %filter)
  (volatile! [] :> *results)
  (loop<- [*next-key *next-key
           :> *page-key]
    (yield-if-overtime)
    (<<if *reverse?
      (<<if (nil? *next-key)
        (sorted-map-range-to-end *limit :> *range-nav)
       (else>)
        (sorted-map-range-to *next-key
                             {:inclusive? false :max-amt *limit}
                             :> *range-nav))
     (else>)
      (sorted-map-range-from *next-key
                             {:inclusive? false :max-amt *limit}
                             :> *range-nav))
    (local-select> [*map-path *range-nav] $$p :> *m)
    (<<atomic
      (<<if *reverse?
        (rseq (vec *m) :> *seq)
       (else>)
        (seq *m :> *seq))
      (ops/explode *seq :> [*id *info])
      (%filter *id *info :> *assoc-map *dissoc-keys)
      (<<if (some? *assoc-map)
        (conj-vol! *results
                   (merge (into {} (dissoc-all *info *dissoc-keys))
                          *assoc-map))))
    (<<cond
     (case> (< (count *m) *limit))
      (:> nil)

     (case> (>= (count @*results) *limit))
      (:> (next-search-key *m *reverse?))

     (default>)
      (continue> (next-search-key *m *reverse?))))
  (:> @*results *page-key))

;; accepts filters :source, :tag, and search-string (looks for match within
;; stringified input or reference-output)
(defn declare-search-examples-query-topology
  [topologies]
  (let [datasets-sym (symbol (po/datasets-task-global-name))]
    (<<query-topology topologies
      (search-examples-name)
      [*dataset-id *snapshot *filters *limit *next-key :> *res]
      (|hash *dataset-id)
      (identity
       *filters
       :> {*search-tag    :tag
           *search-string :search-string
           *search-source :source})
      (ifexpr (some? *search-string)
        (str/lower-case *search-string)
        :> *search-string-lower)
      (<<ramafn %filter
        [*id {:keys [*input *reference-output *source *tags]}]
        (<<cond
         (case> (and> (some? *search-tag) (not (contains? *tags *search-tag))))
          (:> nil nil)

         (case>
          (and> (some? *search-source)
                (not (h/contains-string? (aor-types/source-string *source)
                                          *search-source))))
          (:> nil nil)

         (case>
          (and>
           (some? *search-string-lower)
           (and>
            (not (h/contains-string? (str/lower-case (str *id))
                                      *search-string-lower))
            (not (h/contains-string? (str/lower-case (str (or> *input "")))
                                      *search-string-lower))
            (not (h/contains-string? (str/lower-case
                                      (str (or> *reference-output "")))
                                      *search-string-lower)))))
          (:> nil nil)

         (default>)
          (:> {:id *id} nil)))
      (search-loop datasets-sym
                   (keypath *dataset-id :snapshots *snapshot)
                   %filter
                   *limit
                   *next-key
                   false
                   :> *items *page-key)
      (|origin)
      (hash-map :examples *items :pagination-params *page-key :> *res)
    )))

;; - filters can contain :search-string, which matches against the evaluator
;; name, or :types which is set of :regular, :comparative, or :summary
;; - limit is approximate, it will return at least that amount and up to twice
;; that amount
;; - returns {:items [{:name ... :type ... :description ... :builder-name ...
;;                    :builder-params ... <all the other info in the PState>}
;;                    ...]
;;            :pagination-params <next-key>}
(defn declare-search-evaluators-query-topology
  [topologies]
  (let [evals-pstate-sym (symbol (po/evaluators-task-global-name))]
    (<<query-topology topologies
      (search-evaluators-name)
      [*filters *limit *next-key :> *res]
      (|direct 0)
      (identity *filters :> {:keys [*types *search-string]})
      (ifexpr (some? *search-string)
        (str/lower-case *search-string)
        :> *search-string-lower)
      (evals/all-evaluator-builders :> *builders)
      (<<ramafn %filter
        [*name {:keys [*builder-name] :as *info}]
        (select> [(keypath *builder-name) :type] *builders :> *type)
        (<<cond
         (case> (nil? *type))
          (:> nil nil)

         (case> (and> (some? *types) (not (contains? *types *type))))
          (:> nil nil)

         (case> (and> (some? *search-string-lower)
                      (not (h/contains-string? (str/lower-case *name)
                                                *search-string-lower))))
          (:> nil nil)

         (default>)
          (:> (assoc *info
               :name *name
               :type *type)
              nil)))
      (search-loop evals-pstate-sym
                   STAY
                   %filter
                   *limit
                   *next-key
                   false
                   :> *items *page-key)
      (|origin)
      (hash-map :items *items :pagination-params *page-key :> *res)
    )))
;; - filters can contain:
;;    - :search-string, which matches against the experiment name or ID
;;    - :type which is either com.rpl.agent_o_rama.impl.types.RegularExperiment
;;    or
;;      com.rpl.agent_o_rama.impl.types.ComparativeExperiment class
;;    - :times, which is vector of maps containing {:pred <fn>, :value <value>}
;;       - :pred is 2-arity function (use either <= or >=)
;;       - :value is the millis timestamp to compare against
;;       - {:pred < :value 100} will result only in experiments started before
;;       timestamp 100
;; - limit is approximate, it will return at least that amount and up to twice
;; that amount
;; - returns {:items [{:experiment-info <StartExperiment type>
;; :experiment-invoke ...
;; :start-time-millis ... :finish-time-millis ...}
;;                    ...]
;;            :pagination-params <next-key>}
(defn declare-search-experiments-query-topology
  [topologies]
  (let [datasets-pstate-sym (symbol (po/datasets-task-global-name))]
    (<<query-topology topologies
      (search-experiments-name)
      [*dataset-id *filters *limit *next-key :> *res]
      (|hash *dataset-id)
      (identity *filters :> {:keys [*type *search-string *times]})
      (ifexpr (some? *search-string)
        (str/lower-case *search-string)
        :> *search-string-lower)
      (<<ramafn %filter
        [*id {:keys [*start-time-millis *experiment-info] :as *m}]
        (<<ramafn %matches-time-spec?
          [{:keys [*pred *value]}]
          (:> (h/invoke *pred *start-time-millis *value)))
        (get *experiment-info :name :> *name)
        (get *experiment-info :spec :> *spec)
        (<<cond
         (case> (and> (some? *type) (not (instance? *type *spec))))
          (:> nil nil)

         (case> (and> (some? *search-string-lower)
                      (not (h/contains-string? (str/lower-case (str *id))
                                                *search-string-lower))
                      (not (h/contains-string? (str/lower-case *name)
                                                *search-string-lower))))
          (:> nil nil)

         (case> (not (every? %matches-time-spec? *times)))
          (:> nil nil)

         (default>)
          (:> {} [:results])))
      (search-loop datasets-pstate-sym
                   (keypath *dataset-id :experiments)
                   %filter
                   *limit
                   *next-key
                   true
                   :> *items *page-key)
      (|origin)
      (hash-map :items *items :pagination-params *page-key :> *res)
    )))

(defn is-remote-dataset?
  [{:keys [module-name]}]
  (some? module-name))

(defn example-fetch-batch-size
  []
  500)

(defn merge-examples-to-results
  [results-map example-id->example]
  (transform
   MAP-VALS
   (fn [{:keys [example-id] :as m}]
     (if-let [example (get example-id->example example-id)]
       (assoc m
        :input (:input example)
        :reference-output (:reference-output example))
       (assoc m :missing-example? true)
     ))
   results-map))

(defn fetch-remote-examples
  [declared-objects-tg remote-params query-path]
  (let [cf (h/mk-completable-future)]
    (anode/submit-virtual-task!
     nil
     (fn []
       (try
         (datasets/with-datasets-pstate
          declared-objects-tg
          remote-params
          [datasets]
          (.complete cf (foreign-select-one query-path datasets))
         )
         (catch Throwable t
           (.completeExceptionally cf t)
         ))
     ))
    cf))


;; - returns {:items [{<all the info in values of :experiments in datasets
;; PState, with
;; examples
;; hydrated with example input/reference-output into the keys :input,
;; :reference-output}
;;                    ...]
;;            :pagination-params <next-key>}
;;    - if example is missing from the dataset (e.g. it was deleted), it won't
;;    have :input or
;;    :reference-output and will instead have the key :missing-example? set to
;;    true
(defn declare-experiment-results-query-topology
  [topologies]
  (let [datasets-pstate-sym (symbol (po/datasets-task-global-name))]
    (<<query-topology topologies
      (experiment-results-name)
      [*dataset-id *experiment-id :> *res]
      (|hash *dataset-id)
      (local-select> [(keypath *dataset-id)
                      :props
                      (submap [:cluster-conductor-host :cluster-conductor-port
                               :module-name])]
                     datasets-pstate-sym
                     :> *remote-params)
      (local-select> [(keypath *dataset-id :experiments *experiment-id)
                      (submap [:experiment-info
                               :experiment-invoke
                               :start-time-millis
                               :finish-time-millis
                               :summary-evals
                               :summary-eval-failures
                               :eval-number-stats
                               :latency-number-stats
                               :input-token-number-stats
                               :output-token-number-stats
                               :total-token-number-stats
                              ])]
                     datasets-pstate-sym
                     :> *experiment-props)
      (local-select> [(keypath *dataset-id :experiments *experiment-id :results)
                      (subselect ALL)]
                     datasets-pstate-sym
                     {:allow-yield? true}
                     :> *results-tuples)
      (into {} *results-tuples :> *results-map)
      (select> [(subselect MAP-VALS :example-id) (view set)]
        *results-map
        :> *example-ids)
      (example-fetch-batch-size :> *batch-size)
      (partition *batch-size *batch-size [] *example-ids :> *chunks)
      (select> [:experiment-info :snapshot] *experiment-props :> *snapshot)
      (loop<- [*m {}
               *chunks (seq *chunks)
               :> *example-id->example]
        (yield-if-overtime)
        (<<if (nil? *chunks)
          (:> *m)
         (else>)
          (first *chunks :> *chunk)
          (multi-keypath *chunk :> *example-paths)
          (path> (keypath *dataset-id :snapshots *snapshot)
                 (subselect *example-paths)
                 :> *query-path)
          (<<if (is-remote-dataset? *remote-params)
            (fetch-remote-examples (po/agent-declared-objects-task-global)
                                   *remote-params
                                   *query-path
                                   :> *examples-cf)
            (completable-future> *examples-cf :> *examples)
           (else>)
            (local-select> *query-path datasets-pstate-sym :> *examples))
          (mapv vector *chunk *examples :> *pairs)
          (continue> (into *m *pairs) (next *chunks))
        ))
      (assoc *experiment-props
       :results (merge-examples-to-results *results-map *example-id->example)
       :> *res)
      (|origin))))

(defn action-log-info
  [action-log]
  {:action action-log})

(defn to-action-log-page-result
  [pages-map page-size]
  (to-page-result pages-map
                  page-size
                  :action-id
                  :actions
                  :action-id))


;; returns map of form:
;; {:actions
;;   [{:action-id ...
;      :action ...}
;;    ...]
;;  :pagination-params {task-id end-id}}
(defn declare-get-action-log-page-topology
  [topologies]
  (let [pstate-name (po/action-log-task-global-name)]
    (<<query-topology topologies
      (action-log-page-name)
      [*agent-name *rule-name *page-size *pagination-params :> *res]
      (get-distributed-page* *page-size
                             *pagination-params
                             pstate-name
                             *res
                             action-log-info
                             to-action-log-page-result
                             h/max-uuid
                             (keypath *agent-name *rule-name))
    )))

(defn add-implicit-metadata
  [items search-string-lower]
  (if (h/contains-string? "aor/status" search-string-lower)
    (->> (conj items {:name "aor/status" :examples #{"run-success" "run-failure"}})
         (sort-by :name)
         vec)
    items
  ))

;; returns {:metadata [{:name ... :examples #{...}} ...] :pagination-params ...}
(defn declare-search-metadata-topology
  [topologies]
  (<<query-topology topologies
    (search-metadata-name)
    [*agent-name *search-string *limit *next-key :> *res]
    (|direct 0)
    (str/lower-case *search-string :> *search-string-lower)
    (<<ramafn %filter
      [*name _]
      (<<cond
       (case> (not (h/contains-string? (str/lower-case (str *name)) *search-string-lower)))
        (:> nil nil)

       (default>)
        (:> {:name *name} nil)))
    (po/agent-stream-shared-task-global *agent-name :> $$shared-streaming)
    (search-loop $$shared-streaming
                 (path> :metadata)
                 %filter
                 *limit
                 *next-key
                 false
                 :> *items *page-key)
    (|origin)
    (hash-map :metadata
              (add-implicit-metadata *items *search-string-lower)
              :pagination-params
              *page-key
              :> *res)
  ))

(defn declare-all-agent-metrics-topology
  [topologies]
  (<<query-topology topologies
    (all-agent-metrics-name)
    [*agent-name :> *res]
    (|all)
    (po/agent-telemetry-task-global *agent-name :> $$telemetry)
    (local-select>
     [(keypath 60) MAP-KEYS]
     $$telemetry
     {:allow-yield? true}
     :> *metric-id)
    (|origin)
    (aggs/+set-agg *metric-id :> *res)
  ))

;; direct queries on PStates

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
