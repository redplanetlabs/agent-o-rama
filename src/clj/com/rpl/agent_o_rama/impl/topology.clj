(ns com.rpl.agent-o-rama.impl.topology
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    FinishedAgg]
   [com.rpl.agent_o_rama.impl.types
    HumanInput
    Node
    NodeAgg
    NodeAggStart
    NodeHumanInputRequest]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]))

(defn get-node-obj
  [agent-graph node]
  (select-any [:node-map (keypath node) :node]
              agent-graph))

(defn hook:finding-graph-version [starting-task-id])

(deframaop fetch-graph-version
  [*agent-name]
  (<<with-substitutions
   [*graph (po/agent-graph-task-global *agent-name)
    $$graph-history (po/graph-history-task-global *agent-name)]
   (get *graph :uuid :> *curr-uuid)
   (local-select> (view last) $$graph-history :> [*version {:keys [*uuid]}])
   (<<if (= *uuid *curr-uuid)
     (:> *version)
    (else>)
     (ops/current-task-id :> *task-id)
     (|global)
     (hook:finding-graph-version *task-id)
     (local-select> (view last) $$graph-history :> [*version {:keys [*uuid]}])
     (<<if (= *uuid *curr-uuid)
       (identity *version :> *found-version)
      (else>)
       (inc (or> *version -1) :> *found-version)
       (local-transform> [(keypath *found-version)
                          (termval (graph/graph->historical-graph-info *graph))]
                         $$graph-history))
     (|direct *task-id)
     (local-transform> [(keypath *found-version)
                        (termval (graph/graph->historical-graph-info *graph))]
                       $$graph-history)
     (:> *found-version)
   )))

(deframaop hook:emit>
  [*emit]
  (:>))

(deframaop hook:update-last-progress>
  []
  (:>))

(defn hook:writing-result [agent-task-id agent-id result])

(defn finished-streaming-chunk
  []
  (aor-types/->StreamingChunk -1 -1 iclient/FINISHED))

(deframaop send-emits>
  [*agent-name *agent-task-id *agent-id *retry-num *invoke-id *agg-invoke-id
   *emits *result *stats *fork-context]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)]
   (anchor> <root>)
   (ops/explode *emits
                :> {:keys [*invoke-id *fork-invoke-id *target-task-id
                           *node-name *args]
                    :as   *emit})
   (hook:emit> *emit)
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *target-task-id)
   (aor-types/->valid-NodeOp
    *invoke-id
    *fork-invoke-id
    *fork-context
    *node-name
    *args
    *agg-invoke-id
    :> *op)
   (anchor> <regular-emit>)

   (hook> <root>)
   (mapv (comp h/half-uuid :invoke-id) *emits :> *next-ack-vals)
   (reduce bit-xor (h/half-uuid *invoke-id) *next-ack-vals :> *ack-val)
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *agent-task-id)
   ;; <<atomic here only because tests override the hook to elide this
   (<<atomic
     (hook:update-last-progress>)
     (local-transform>
      [(keypath *agent-id)
       (multi-path [:last-progress-time-millis (termval (h/current-time-millis))]
                   [:stats (term (stats/agent-stats-merger *stats))])]
      $$root))

   (<<if (some? *result)
     (hook:writing-result *agent-task-id *agent-id *result)
     ;; if race with retry and it happened to have finished, don't change the
     ;; result here – this can happen if the agent has other branches that fail
     ;; besides the one that created the result
     (h/current-time-millis :> *finish-time-millis)
     (local-transform>
      [(keypath *agent-id)
       (selected? :result nil?)
       (multi-path [:result (termval *result)]
                   [:finish-time-millis (termval *finish-time-millis)])]
      $$root)
     (local-transform> [(keypath *agent-id) NONE>] $$active))
   (<<if (some? *agg-invoke-id)
     (aor-types/->valid-AggAckOp *agg-invoke-id *ack-val :> *op)
     (anchor> <agg-ack-emit>)
    (else>)
     (<<ramafn %update-ack-val
       [*v]
       (:> (bit-xor *v *ack-val)))
     (local-transform>
      [(keypath *agent-id)
       :ack-val
       (term %update-ack-val)]
      $$root)
     (local-select> (keypath *agent-id)
                    $$root
                    :> {*root-ack-val :ack-val *result :result})
     (<<if (= 0 *root-ack-val)
       (<<if (nil? *result)
         (h/current-time-millis :> *finish-time-millis)
         (local-transform>
          [(keypath *agent-id)
           (multi-path [:result
                        (termval (aor-types/->AgentResult
                                  "Agent completed without result"
                                  true))]
                       [:finish-time-millis (termval *finish-time-millis)])]
          $$root))
       (finished-streaming-chunk :> *finished-streaming-chunk)
       (local-transform>
        [(keypath *agent-id)
         :streaming
         MAP-VALS
         :all
         AFTER-ELEM
         (termval *finished-streaming-chunk)]
        $$root))
   )

   (unify> <regular-emit> <agg-ack-emit>)
   (:> *op)))


(deframaop init-root
  [*agent-name *agent-id *retry-num *args *metadata *source]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$root-count (po/agent-root-count-task-global *agent-name)]
   (fetch-graph-version *agent-name :> *version)
   (anode/gen-node-id :> *invoke-id)
   (h/current-time-millis :> *current-time-millis)
   (local-select> [(keypath *agent-id) (view some?)] $$root :> *exists?)
   (<<if (not *exists?)
     (local-transform> (term inc) $$root-count))
   (local-transform>
    [(keypath *agent-id)
     (termval {:root-invoke-id    *invoke-id
               :invoke-args       *args
               :graph-version     *version
               :ack-val           (h/half-uuid *invoke-id)
               :last-progress-time-millis *current-time-millis
               :retry-num         *retry-num
               :stats             stats/EMPTY-AGENT-STATS
               :metadata          *metadata
               :source            *source
               :start-time-millis *current-time-millis})]
    $$root)
   (:> *invoke-id)))

(defn init-retry-num [] 0)

;; factored out for redef in tests
(defn gen-new-agent-id
  [agent-name]
  (h/random-uuid7))

(deframaop intake-agent-initiate
  [*agent-name *data]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (get *data :args :> *args)
   (ops/current-task-id :> *agent-task-id)
   (get *data :forced-agent-invoke-id :> *forced-agent-id)
   (get *data :source :> *source)
   (get *data :metadata :> *metadata)
   (<<if (some? *forced-agent-id)
     ;; stop if already exists for idempotency
     (local-select> [(keypath *forced-agent-id) nil?] $$root)
     (identity *forced-agent-id :> *agent-id)
    (else>)
     (gen-new-agent-id *agent-name :> *agent-id))
   (init-retry-num :> *retry-num)
   (init-root *agent-name *agent-id *retry-num *args *metadata *source :> *invoke-id)
   (local-transform> [(keypath *agent-id) (termval true)]
                     $$active)
   (aor-types/->valid-NodeOp *invoke-id
                             nil
                             nil
                             (get *agent-graph :start-node)
                             *args
                             nil
                             :> *op)
   (:> *agent-task-id
       *agent-id
       (aor-types/->valid-AgentExecutionContext *metadata *source)
       *retry-num
       *op)))

(defn hook:received-retry [agent-task-id agent-id retry-num])
(deframaop hook:running-retry>
  [*agent-task-id *agent-id *retry-num]
  (:>))

(deframafn complete-with-failure!
  [*agent-name *agent-id *message]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)]
   (h/current-time-millis :> *finish-time-millis)
   (local-transform>
    [(keypath *agent-id)
     (multi-path
      [:result
       (termval (aor-types/->valid-AgentResult *message true))]
      [:finish-time-millis (termval *finish-time-millis)])]
    $$root)
   (local-transform> [(keypath *agent-id) NONE>] $$active)
   (:>)))

(deframaop intake-retry
  [*agent-name {:keys [*agent-task-id *agent-id *expected-retry-num]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$gc (po/agent-gc-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (hook:received-retry *agent-task-id *agent-id *expected-retry-num)
   (local-select> (keypath *agent-id)
                  $$root
                  :> {*root-invoke-id :root-invoke-id
                      *curr-retry-num :retry-num
                      *graph-version :graph-version
                      *args :invoke-args
                      *result :result
                      *source :source
                      *metadata :metadata

                      {:keys [*fork-context
                              *parent-root-invoke-id]}
                      :fork-of})
   ;; - this is mostly a sanity check, though it is technically possible for
   ;; multiple retries to come through from stall checker if it runs multiple
   ;; times before any retries are processed (e.g. stream topology is paused)
   ;; - don't need to remove from active-invokes in this case since writing
   ;; result and removing from active-invokes is done atomically
   (filter> (nil? *result))
   ;; if it got GC'd, ignore
   (filter> (some? *root-invoke-id))
   (filter> (= *expected-retry-num *curr-retry-num))
   (hook:running-retry> *agent-task-id *agent-id *expected-retry-num)
   (fetch-graph-version *agent-name :> *curr-graph-version)
   (<<cond
    (case> (= *curr-graph-version *graph-version))
     (identity :continue :> *handle-mode)

    (case> (= *curr-graph-version (inc *graph-version)))
     (po/agent-graph-task-global *agent-name :> {*handle-mode :update-mode})

    (default>)
     ;; if somehow two or more module updates got through before the retry could
     ;; be processed, drop the retry since don't know if it's valid to continue
     ;; it
     (identity :drop :> *handle-mode))

   (inc *expected-retry-num :> *retry-num)

   (<<if (= :drop *handle-mode)
     (complete-with-failure! *agent-name *agent-id "Retry dropped")
    (else>)
     (<<if (= :restart *handle-mode)
       (local-transform> [(keypath *root-invoke-id) (termval nil)]
                         $$gc)
       (init-root *agent-name *agent-id *retry-num *args *metadata *source :> *root-invoke-id)
      (else>)
       (identity *root-invoke-id :> *root-invoke-id))

     (anode/read-config *agent-name
                        aor-types/MAX-RETRIES-CONFIG
                        :> *max-retries)
     (<<if (> *retry-num *max-retries)
       (complete-with-failure! *agent-name *agent-id "Max retry limit exceeded")
      (else>)
       (local-transform> [(keypath *agent-id)
                          (multi-path
                           [:retry-num (termval *retry-num)]
                           [:graph-version
                            (termval *curr-graph-version)]
                           [:ack-val (termval (h/half-uuid *root-invoke-id))])]
                         $$root)

       (aor-types/->valid-NodeOp *root-invoke-id
                                 *parent-root-invoke-id
                                 *fork-context
                                 (get *agent-graph :start-node)
                                 *args
                                 nil
                                 :> *op)
       (:> *agent-task-id
           *agent-id
           (aor-types/->valid-AgentExecutionContext *metadata *source)
           *retry-num
           *op)
     ))))

(deframaop intake-fork
  [*agent-name {:keys [*agent-task-id *agent-id *invoke-id->new-args]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)
    %affected-aggs (queries/fork-affected-aggs-query-task-global *agent-name)]
   (local-select> (keypath *agent-id)
                  $$root
                  :> {:keys [*root-invoke-id *invoke-args *graph-version *metadata]})
   (<<if (nil? *invoke-args)
     (throw! (h/ex-info "Forked agent ID does not exist"
                        {:agent-id *agent-id})))
   (%affected-aggs *agent-task-id
                   *agent-id
                   (-> *invoke-id->new-args
                       keys
                       set)
                   :> *affected-aggs)
   (aor-types/->ForkContext *invoke-id->new-args
                            *affected-aggs
                            :> *fork-context)
   (h/random-uuid7 :> *fork-agent-id)
   (init-retry-num :> *retry-num)
   (init-root *agent-name
              *fork-agent-id
              *retry-num
              *invoke-args
              *metadata
              nil
              :> *invoke-id)
   (local-select> [(keypath *fork-agent-id) :graph-version]
                  $$root
                  :> *fork-graph-version)
   (<<if (not= *graph-version *fork-graph-version)
     (throw! (h/ex-info "Cannot fork a run from an old version"
                        {:current-version *fork-graph-version
                         :old-version     *graph-version})))
   (local-transform> [(keypath *fork-agent-id) (termval true)]
                     $$active)
   (local-transform> [(keypath *agent-id)
                      :forks
                      NONE-ELEM
                      (termval *fork-agent-id)]
                     $$root)
   (local-transform> [(keypath *fork-agent-id)
                      :fork-of
                      (termval {:parent-agent-id *agent-id
                                :fork-context    *fork-context})]
                     $$root)
   (aor-types/->valid-NodeOp *invoke-id
                             *root-invoke-id
                             *fork-context
                             (get *agent-graph :start-node)
                             *invoke-args
                             nil
                             :> *op)
   (:> *agent-task-id
       *fork-agent-id
       (aor-types/->valid-AgentExecutionContext *metadata nil)
       *retry-num
       *op)))

(deframaop intake-node-failure
  [*agent-name {:keys [*invoke-id *retry-num *throwable-str *nested-ops]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$nodes (po/agent-node-task-global *agent-name)
    *failure-depot (po/agent-failures-depot-task-global *agent-name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node]})
   (filter> (some? *agent-id))
   (apart/filter-valid-retry-num> *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *retry-num)
   (local-transform> [(keypath *invoke-id)
                      (multi-path
                       [:nested-ops (termval *nested-ops)]
                       [:exceptions AFTER-ELEM (termval *throwable-str)])]
                     $$nodes)
   (|direct *agent-task-id)
   (local-transform>
    [(must *agent-id)
     :exception-summaries
     AFTER-ELEM
     (termval (aor-types/->ExceptionSummary *throwable-str *node *invoke-id))]
    $$root)
   (depot-partition-append!
    *failure-depot
    (aor-types/->valid-AgentFailure *agent-task-id
                                    *agent-id
                                    *retry-num)
    :append-ack)
   (anode/hook:appended-agent-failure *agent-task-id
                                      *agent-id
                                      *retry-num)
   (filter> false)))

(defn mark-virtual-task-complete!
  [invoke-id]
  (let [^AgentNodeExecutorTaskGlobal node-exec
        (po/agent-node-executor-task-global)]
    (.removeTrackedInvokeId node-exec invoke-id)))

(deframaop begin-node-complete
  [*agent-name *invoke-id *retry-num]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)]
   (mark-virtual-task-complete! *invoke-id)
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node
                             *agg-invoke-id *start-time-millis
                             *metadata *source]})
   (filter> (some? *agent-id))
   (apart/filter-valid-retry-num> *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *retry-num)
   (:> *agent-task-id
       *agent-id
       (aor-types/->valid-AgentExecutionContext *metadata *source)
       *node
       *agg-invoke-id
       *start-time-millis)))

(deframaop handle-node-complete-emits
  [*agent-name *agent-task-id *agent-id *retry-num *node *invoke-id
   *agg-invoke-id *result *emits *stats *fork-context]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)]
   (<<subsource (get-node-obj (po/agent-graph-task-global *agent-name) *node)
    (case> Node)
     (identity *invoke-id :> *invoke-id)

    (case> NodeAggStart)
     (identity *invoke-id :> *invoke-id)

    (case> NodeAgg)
     (local-select> (keypath *invoke-id)
                    $$nodes
                    :> {*invoke-id :agg-start-invoke-id})
   )

   (send-emits> *agent-name
                *agent-task-id
                *agent-id
                *retry-num
                *invoke-id
                *agg-invoke-id
                *emits
                *result
                *stats
                *fork-context
                :> *op)
   (:> *op)))

(deframaop intake-node-complete
  [*agent-name
   {:keys [*invoke-id
           *retry-num
           *node-fn-res
           *emits
           *result
           *nested-ops
           *finish-time-millis]}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)]
   (begin-node-complete
    *agent-name
    *invoke-id
    *retry-num
    :> *agent-task-id *agent-id *execution-context *node *agg-invoke-id *start-time-millis)
   (<<ramafn %merger
     [*m]
     (:> (reduce-kv assoc
                    *m
                    {:emits      *emits
                     :result     *result
                     :nested-ops *nested-ops
                     :finish-time-millis *finish-time-millis})))
   (local-transform> [(keypath *invoke-id) (term %merger)]
                     $$nodes)

   (<<if (-> (po/agent-graph-task-global *agent-name)
             (get-node-obj *node)
             aor-types/NodeAggStart?)
     (local-transform> [(keypath *agg-invoke-id)
                        :agg-start-res
                        (termval *node-fn-res)]
                       $$nodes))


   (stats/mk-node-stats *node *start-time-millis *finish-time-millis *nested-ops :> *stats)
   (handle-node-complete-emits
    *agent-name
    *agent-task-id
    *agent-id
    *retry-num
    *node
    *invoke-id
    *agg-invoke-id
    *result
    *emits
    *stats
    nil
    :> *op)
   (:> *agent-task-id *agent-id *execution-context *retry-num *op)
  ))

(defn extract-agg-result
  [res]
  (cond
    (reduced? res)
    {:new-agg-state @res
     :finished?     true}

    (instance? FinishedAgg res)
    {:new-agg-state (.getValue ^FinishedAgg res)
     :finished?     true}

    :else
    {:new-agg-state res
     :finished?     false}))

(defn hook:running-complete-agg! [])

(deframaop complete-agg!
  [*agent-name *invoke-id *execution-context *retry-num]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (hook:running-complete-agg!)
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node *agg-ack-val
                             *agg-state *agg-start-res *agg-invoke-id]})
   (local-transform>
    [(keypath *invoke-id) :agg-finished? (termval true)]
    $$nodes)
   (get-node-obj *agent-graph *node :> {:keys [*node-fn]})
   (vector *agg-state *agg-start-res :> *args)
   (anode/handle-node-invoke *agent-name
                             *agent-task-id
                             *agent-id
                             *execution-context
                             *node-fn
                             *invoke-id
                             *retry-num
                             *node
                             *args
                             *agg-invoke-id
                             ;; if running agg completion on this codepath,
                             ;; this means we're past the last fork and are on
                             ;; a fresh run of this part of the graph
                             nil)
   (:>)))

(deframaop ack-agg!
  [*agent-name *invoke-id *execution-context *retry-num *ack-val]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agg-ack-val *agg-finished? *agent-task-id
                             *agent-id]})
   ;; - not strictly necessary, since early agg return doesn't ack
   ;; - but cleaner to just stop updating the agg completely at that point
   ;; - additional acks can come from rest of agg subgraph, or from retries
   (filter> (not *agg-finished?))
   (bit-xor *ack-val *agg-ack-val :> *new-ack-val)
   (local-transform>
    [(keypath *invoke-id) :agg-ack-val (termval *new-ack-val)]
    $$nodes)
   (filter> (= 0 *new-ack-val))
   ;; - replicate the new ack val before executing it to make potential retries
   ;; do less work
   ;; - note that agent-task-id is the same as current-task-id in this case
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               (ops/current-task-id))
   (complete-agg! *agent-name *invoke-id *execution-context *retry-num)
   (:>)))

(deframaop reset-aggregation-state!
  [*agent-name *agent-task-id *agent-id *execution-context *retry-num *agg-node-name *invoke-id
   *agg-invoke-id *parent-agg-invoke-id]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (get-node-obj *agent-graph *agg-node-name :> {:keys [*init-fn]})
   (anode/invoke-on-task-thread *agent-name
                                *agent-task-id
                                *agent-id
                                *agg-node-name
                                *agg-invoke-id
                                *retry-num
                                *init-fn
                                :agg-init
                                :> *init-agg-state)
   ;; - this is for forking/retries and not relevant in regular agent invoke
   ;; - when agg graph is changed but not start agg node, forking sets this
   ;; and just a couple other fields for agg-invoke-id and expects the retry
   ;; to pick it up and maintain it
   (local-select> [(keypath *agg-invoke-id) :agg-start-res]
                  $$nodes
                  :> *agg-start-res)
   (local-transform>
    [(keypath *agg-invoke-id)
     (termval {:agent-id            *agent-id
               :agent-task-id       *agent-task-id
               :node                *agg-node-name
               :agg-invoke-id       *parent-agg-invoke-id
               :agg-inputs          []
               :agg-start-res       *agg-start-res
               :agg-state           *init-agg-state
               :agg-ack-val         (h/half-uuid *invoke-id)
               :agg-start-invoke-id *invoke-id
               :metadata            (get *execution-context :metadata)
               :source              (get *execution-context :source)
              })]
    $$nodes)
   (:>)))

(deframaop hook:handling-retry-node-complete>
  [*agent-name *node *invoke-id *retry-num]
  (:>))

(deframaop intake-retry-node-complete
  [*agent-name {:keys [*invoke-id *retry-num *fork-context]}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)
    *agent-depot (po/agent-depot-task-global *agent-name)]
   (begin-node-complete *agent-name
                        *invoke-id
                        *retry-num
                        :> *agent-task-id *agent-id *execution-context *node *agg-invoke-id _)
   (hook:handling-retry-node-complete> *agent-name *node *invoke-id *retry-num)
   (get-node-obj *agent-graph *node :> *node-obj)
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*result *emits]})

   (<<if (aor-types/NodeAggStart? *node-obj)
     (local-select> (keypath *agg-invoke-id)
                    $$nodes
                    :> {*agg-finished?          :agg-finished?
                        *agg-node-name          :node
                        *parent-agg-invoke-id   :agg-invoke-id
                        *agg-finish-time-millis :finish-time-millis})
     (<<if *agg-finished?
       (<<if (some? *agg-finish-time-millis)
         (depot-partition-append!
          *agent-depot
          (aor-types/->valid-RetryNodeComplete *agg-invoke-id
                                               *retry-num
                                               *fork-context)
          :append-ack)
        (else>)
         (complete-agg! *agent-name *agg-invoke-id *execution-context *retry-num))
       (filter> (some? *fork-context))
      (else>)
       (reset-aggregation-state!
        *agent-name
        *agent-task-id
        *agent-id
        *execution-context
        *retry-num
        *agg-node-name
        *invoke-id
        *agg-invoke-id
        *parent-agg-invoke-id)
     ))

   (handle-node-complete-emits
    *agent-name
    *agent-task-id
    *agent-id
    *retry-num
    *node
    *invoke-id
    *agg-invoke-id
    *result
    *emits
    nil
    *fork-context
    :> *op)
   (:> *agent-task-id *agent-id *execution-context *retry-num *op)))

(deframaop intake-agent-depot
  [*agent-name *data]
  (<<cond
   (case> (aor-types/AgentInitiate? *data))
    (intake-agent-initiate *agent-name
                           *data
                           :> *agent-task-id *agent-id *execution-context *retry-num *op)
    (ack-return> [*agent-task-id *agent-id])

   (case> (aor-types/RetryAgentInvoke? *data))
    (intake-retry *agent-name
                  *data
                  :> *agent-task-id *agent-id *execution-context *retry-num *op)

   (case> (aor-types/ForkAgentInvoke? *data))
    (intake-fork *agent-name
                 *data
                 :> *agent-task-id *agent-id *execution-context *retry-num *op)
    (ack-return> [*agent-task-id *agent-id])

   (case> (aor-types/NodeFailure? *data))
    ;; doesn't actually emit here, but emit needed for unification
    (intake-node-failure *agent-name
                         *data
                         :> *agent-task-id *agent-id *execution-context *retry-num *op)

   (case> (aor-types/NodeComplete? *data))
    (intake-node-complete *agent-name
                          *data
                          :> *agent-task-id *agent-id *execution-context *retry-num *op)

   (case> (aor-types/RetryNodeComplete? *data))
    (intake-retry-node-complete *agent-name
                                *data
                                :> *agent-task-id *agent-id *execution-context *retry-num *op)

   (default> :unify false)
    (throw! (h/ex-info "Unrecognized data type" {:class (class *data)})))
  (:> *agent-task-id *agent-id *execution-context *retry-num *op))

(defn hook:processing-streaming [node streaming-index value])

(deframaop handle-streaming
  [*agent-name
   {:keys [*agent-id
           *node
           *invoke-id
           *retry-num
           *streaming-index
           *value]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)]
   (hook:processing-streaming *node *streaming-index *value)
   (local-select> [(keypath *agent-id) :retry-num (pred= *retry-num)] $$root)
   ;; this ensures idempotence
   (<<ramafn %correct-index?
     [*v]
     (:> (or> (= *streaming-index 0)
              (= (inc *v) *streaming-index))))
   (aor-types/->StreamingChunk
    *invoke-id
    *streaming-index
    *value
    :> *chunk)
   (local-transform>
    [(keypath *agent-id :streaming *node)
     (selected?
      :invokes
      (keypath *invoke-id)
      (nil->val -1)
      (pred %correct-index?))
     (multi-path
      [:all AFTER-ELEM (termval *chunk)]
      [:invokes (keypath *invoke-id) (termval *streaming-index)])]
    $$root)
  ))

(defn- complete-human-future!
  [^AgentNodeExecutorTaskGlobal node-exec invoke-id uuid response]
  (if-let [cf (.getHumanFuture node-exec invoke-id uuid)]
    (.complete cf response)))

(deframaop handle-human
  [*agent-name *data]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    *node-exec (po/agent-node-executor-task-global)]
   (<<subsource *data
    (case> NodeHumanInputRequest :> {:keys [*agent-id]})
     (local-transform> [(must *agent-id)
                        :human-requests
                        NONE-ELEM
                        (termval *data)]
                       $$root)

    (case> HumanInput
           :> {:keys [*request *response]})
     (identity *request :> {:keys [*agent-id *node-task-id *invoke-id *uuid]})
     (complete-human-future! *node-exec *invoke-id *uuid *response)
     (get *request :agent-task-id :> *agent-task-id)
     (|direct *agent-task-id)
     (local-transform> [(must *agent-id)
                        :human-requests
                        (set-elem *request)
                        NONE>]
                       $$root)
   )))

(deframaop handle-config
  [*agent-name {:keys [*key *val]}]
  (<<with-substitutions
   [$$config (po/agent-config-task-global *agent-name)]
   (|all)
   (local-transform> [(keypath *key) (termval *val)] $$config)))

(deframaop handle-global-config
  [{:keys [*key *val]}]
  (<<with-substitutions
   [$$global-config (po/agent-global-config-task-global)]
   (|all)
   (local-transform> [(keypath *key) (termval *val)] $$global-config)))

(deframafn node-complete?
  [*agent-name *next-node *invoke-id]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (<<if (aor-types/NodeAgg? (get-node-obj *agent-graph *next-node))
     ;; Four cases here:
     ;;  - This node doesn't exist, which means it definitely was not applied
     ;;  - The node exists and this is a retry. In that case, the agg state was
     ;;  cleared and this needs to be applied again even if the node exists
     ;;  (which was written atomically with the agg update on a previous
     ;;  attempt).
     ;;  - This is a retry of a fork on an unmodified part of the graph, in
     ;;  which case it's just copying nodes over. In this case, the attempt to
     ;;  apply the node will be a no-op since it will filter itself out when it
     ;;  sees aggregation is already finished (since the fork of the unmodified
     ;;  graph copies the agg state).
     ;;  - This is a retry of a fork of a modified part of the graph. This is
     ;;  the same as case #2.
     (:> false)
    (else>)
     (local-select> (keypath *invoke-id)
                    $$nodes
                    :> {:keys [*finish-time-millis]})
     (:> (some? *finish-time-millis))
   )))

(deframaop execute-node-op
  [*agent-name
   *agent-task-id
   *agent-id
   *execution-context
   *retry-num
   {:keys [*invoke-id *next-node *args *agg-invoke-id
           *fork-invoke-id *fork-context]}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (<<subsource (get-node-obj *agent-graph *next-node)
    (case> Node :> {:keys [*node-fn]})
     (anode/handle-node-invoke
      *agent-name
      *agent-task-id
      *agent-id
      *execution-context
      *node-fn
      *invoke-id
      *retry-num
      *next-node
      *args
      *agg-invoke-id
      *fork-context)

    (case> NodeAggStart :> {:keys [*node-fn *agg-node-name]})
     (anode/gen-node-id :> *new-agg-invoke-id)
     (local-transform>
      [(keypath *invoke-id) :started-agg? (termval true)]
      $$nodes)
     (reset-aggregation-state!
      *agent-name
      *agent-task-id
      *agent-id
      *execution-context
      *retry-num
      *agg-node-name
      *invoke-id
      *new-agg-invoke-id
      *agg-invoke-id)
     (anode/handle-node-invoke
      *agent-name
      *agent-task-id
      *agent-id
      *execution-context
      *node-fn
      *invoke-id
      *retry-num
      *next-node
      *args
      *new-agg-invoke-id
      *fork-context)

    (case> NodeAgg :> {:keys [*update-fn]})
     ;; - on retry, it's correct to run this again even if it ran on previous
     ;; attempt since the agg state is reset on retry
     (assert! (some? *agg-invoke-id))
     (local-select> (keypath *agg-invoke-id)
                    $$nodes
                    :> {*agg-state           :agg-state
                        *parent-agg-invoke-id :agg-invoke-id
                        *agg-start-invoke-id :agg-start-invoke-id
                        *agg-finished?       :agg-finished?
                       })
     (local-transform> [(keypath *invoke-id)
                        :invoked-agg-invoke-id
                        (termval *agg-invoke-id)]
                       $$nodes)
     (filter> (not *agg-finished?))
     (<<ramafn %update-fn
       []
       (:> (apply *update-fn *agg-state *args)))
     (anode/invoke-on-task-thread *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *next-node
                                  *agg-invoke-id
                                  *retry-num
                                  %update-fn
                                  :agg-update
                                  :> *res)
     (extract-agg-result *res :> {:keys [*new-agg-state *finished?]})

     (local-transform>
      [(keypath *agg-invoke-id)
       (multi-path [:agg-state (termval *new-agg-state)]
                   [:agg-inputs AFTER-ELEM
                    (termval (aor-types/->valid-AggInput *invoke-id
                                                         *args))])]
      $$nodes)

     ;; by not acking here and going straight go complete-agg!, it also prevents
     ;; rest of agg subgraph from ever completing and calling complete-agg!
     ;; again
     (<<if *finished?
       (complete-agg! *agent-name *agg-invoke-id *execution-context *retry-num)
      (else>)
       (ack-agg! *agent-name
                 *agg-invoke-id
                 *execution-context
                 *retry-num
                 (h/half-uuid *invoke-id)))
   )))

(deframaop handle-node-already-complete
  [*agent-name
   *retry-num
   {:keys [*invoke-id *fork-context]}]
  (<<with-substitutions
   [*agent-depot (po/agent-depot-task-global *agent-name)]
   (depot-partition-append!
    *agent-depot
    (aor-types/->valid-RetryNodeComplete *invoke-id *retry-num *fork-context)
    :append-ack)))

(deframafn update-forked-emits
  [*emits]
  (<<ramafn %update-emit
    [{*emit-invoke-id :invoke-id :as *emit}]
    (:>
     (assoc *emit
      :fork-invoke-id *emit-invoke-id
      :invoke-id (anode/gen-node-id))))
  (:> (mapv %update-emit *emits)))

(deframafn copy-unforked-agg-state
  [$$nodes *from-agg-invoke-id *agg-invoke-id]
  (local-select> [(keypath *from-agg-invoke-id)
                  (submap [:nested-ops :emits :result :start-time-millis
                           :finish-time-millis :input :agg-state :agg-ack-val
                           :agg-finished?])]
                 $$nodes
                 :> *sm)
  (update *sm :emits update-forked-emits :> *sm)
  (<<ramafn %merger
    [*m]
    (:> (reduce-kv assoc *m *sm)))
  (local-transform> [(keypath *agg-invoke-id) (term %merger)] $$nodes)

  (local-select> [(keypath *from-agg-invoke-id) :agg-inputs (view count)]
                 $$nodes
                 :> *amt)

  ;; tracing only grabs first 10, so don't bother copying over everything in
  ;; this case
  (min *amt 100 :> *endi)
  (local-select> [(keypath *from-agg-invoke-id) :agg-inputs (srange 0 *endi)]
                 $$nodes
                 :> *v)
  (local-transform> [(keypath *agg-invoke-id) :agg-inputs (termval *v)]
                    $$nodes)
  (:>))

(deframaop handle-node-op
  [*agent-name
   *agent-task-id
   *agent-id
   *execution-context
   *retry-num
   {:keys [*next-node *invoke-id *fork-invoke-id *fork-context *agg-invoke-id]
    :as   *node-op}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)
    *agent-depot (po/agent-depot-task-global *agent-name)]
   (identity *fork-context :> {:keys [*affected-aggs *invoke-id->new-args]})
   (get-node-obj *agent-graph *next-node :> *node-obj)
   (<<cond
    (case> (node-complete? *agent-name *next-node *invoke-id))
     (handle-node-already-complete *agent-name *retry-num *node-op)

    (case> (contains? *invoke-id->new-args *fork-invoke-id))
     (execute-node-op *agent-name
                      *agent-task-id
                      *agent-id
                      *execution-context
                      *retry-num
                      (assoc
                       *node-op
                       :args
                       (get *invoke-id->new-args *fork-invoke-id)))

    (case> (and> (some? *fork-context)
                 (some? *fork-invoke-id)
                 (not (aor-types/NodeAgg? *node-obj))))
     (local-select> [(keypath *fork-invoke-id) (view h/into-map)]
                    $$nodes
                    :> {*emits :emits
                        *fork-agg-invoke-id :agg-invoke-id
                        :as    *curr-data})
     (update-forked-emits *emits :> *new-emits)
     (local-transform> [(keypath *invoke-id)
                        (termval (assoc *curr-data
                                  :agent-task-id *agent-task-id
                                  :agent-id *agent-id
                                  :emits *new-emits
                                  ;; this will be overwritten below if it's
                                  ;; NodeAggStart
                                  :agg-invoke-id *agg-invoke-id))]
                       $$nodes)
     (<<if (aor-types/NodeAggStart? *node-obj)
       (anode/gen-node-id :> *new-agg-invoke-id)
       (local-select> (keypath *fork-agg-invoke-id)
                      $$nodes
                      :> {*agg-node      :node
                          *agg-start-res :agg-start-res})
       ;; forks don't change metadata, so execution context metadata is the same as in the forked
       ;; agg state
       (local-transform> [(keypath *new-agg-invoke-id)
                          (termval {:agent-task-id *agent-task-id
                                    :agent-id      *agent-id
                                    :node          *agg-node
                                    :metadata      (get *execution-context :metadata)
                                    :agg-invoke-id *agg-invoke-id
                                    :agg-start-res *agg-start-res
                                    :agg-start-invoke-id *invoke-id})]
                         $$nodes)
       (local-transform> [(keypath *invoke-id)
                          :agg-invoke-id
                          (termval *new-agg-invoke-id)]
                         $$nodes)
       (<<if (not (contains? *affected-aggs *fork-invoke-id))
         (<<if (contains? *invoke-id->new-args *fork-agg-invoke-id)
           ;; the RetryNodeComplete on the start agg node will see that this
           ;; node isn't finished and will call complete-agg! on it
           (get *invoke-id->new-args
                *fork-agg-invoke-id
                :> [*new-agg-state *new-agg-start-res])
           (local-transform>
            [(keypath *new-agg-invoke-id)
             (multi-path [:agg-state (termval *new-agg-state)]
                         [:agg-start-res (termval *new-agg-start-res)]
                         [:agg-finished? (termval true)])]
            $$nodes)
          (else>)
           (copy-unforked-agg-state $$nodes
                                    *fork-agg-invoke-id
                                    *new-agg-invoke-id)
         )))
     ;; replicate writes before initiating RetryNodeComplete
     (|direct (ops/current-task-id))
     (depot-partition-append!
      *agent-depot
      (aor-types/->valid-RetryNodeComplete *invoke-id
                                           *retry-num
                                           *fork-context)
      :append-ack)

    (default>)
     (execute-node-op *agent-name
                      *agent-task-id
                      *agent-id
                      *execution-context
                      *retry-num
                      *node-op))))

(deframaop handle-gc
  [*agent-name]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$root-count (po/agent-root-count-task-global *agent-name)
    $$nodes (po/agent-node-task-global *agent-name)
    $$gc (po/agent-gc-invokes-task-global *agent-name)
    *gc-valid-depot (po/agent-gc-valid-invokes-depot-task-global *agent-name)]
   (anode/read-config *agent-name
                      aor-types/MAX-TRACES-PER-TASK-CONFIG
                      :> *max-traces)
   (|all)
   (local-select> STAY $$root-count :> *curr-count)
   (- *curr-count *max-traces :> *delete-count)
   (<<if (pos? *delete-count)
     (<<atomic
       (local-select> (sorted-map-range-from-start *delete-count)
                      $$root
                      {:allow-yield? true}
                      :> *to-delete)
       (ops/current-task-id :> *agent-task-id)
       (select>
         ALL
         *to-delete
         {:allow-yield? true}
         :> [*agent-id {:keys [*root-invoke-id *retry-num *result]}])
       (filter> (some? *result))
       (local-transform> [(keypath *agent-id)
                          (multi-path [:forks NONE>]
                                      [:human-requests NONE>]
                                      [:streaming
                                       (multi-path [:all NONE>]
                                                   [:invokes NONE>])])]
                         $$root)
       (|direct *agent-task-id)
       (local-transform> [(keypath *agent-id) :streaming NONE>] $$root)
       (|direct *agent-task-id)
       ;; rare possibility it ticks again while partitioning and tries to delete
       ;; same elements concurrently
       (local-select> [(keypath *agent-id) (view some?)] $$root :> *exists?)
       (<<if *exists?
         (<<if (> *retry-num 0)
           (depot-partition-append! *gc-valid-depot
                                    [*agent-task-id *agent-id]
                                    :append-ack))
         (local-transform> [(keypath *root-invoke-id) (termval nil)] $$gc)
         (local-transform> [(keypath *agent-id) NONE>] $$root)
         (local-transform> (term dec) $$root-count))))
   (local-select> MAP-KEYS $$gc {:allow-yield? true} :> *invoke-id)
   (local-select> [(keypath *invoke-id)]
                  $$nodes
                  :> {:keys [*emits *started-agg? *agg-invoke-id]})
   (ops/current-task-id :> *start-task-id)
   (<<ramafn %to-tuple
     [{:keys [*target-task-id *invoke-id]}]
     (:> [*target-task-id *invoke-id]))
   (mapv %to-tuple *emits :> *tuples)
   (<<if *started-agg?
     (conj *tuples [*start-task-id *agg-invoke-id] :> *tuples)
    (else>)
     (identity *tuples :> *tuples))
   (loop<- [*tuples (seq *tuples)]
     (<<if (empty? *tuples)
       (:>)
      (else>)
       (first *tuples :> [*emit-task-id *emit-invoke-id])
       (|direct *emit-task-id)
       (local-transform> [(keypath *emit-invoke-id) (termval nil)] $$gc)
       (continue> (next *tuples))))
   (|direct *start-task-id)
   (local-transform> [(keypath *invoke-id) :agg-inputs NONE>] $$nodes)
   (|direct *start-task-id)
   (local-transform> [(keypath *invoke-id) NONE>] $$nodes)
   (local-transform> [(keypath *invoke-id) NONE>] $$gc)
  ))
