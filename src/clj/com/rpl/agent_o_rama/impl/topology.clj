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
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agentorama
    FinishedAgg]
   [com.rpl.agent_o_rama.impl.types
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]))

(deframafn read-config
  [*agent-name *config]
  (<<with-substitutions
   [$$config (po/agent-config-task-global *agent-name)]
   (local-select> STAY $$config :> *config-map)
   (:> (aor-types/get-config *config-map *config))))

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

(defn finished-streaming-chunk
  []
  (aor-types/->StreamingChunk -1 -1 iclient/FINISHED))

(deframaop send-emits>
  [*agent-name *agent-task-id *agent-id *retry-num *invoke-id *agg-invoke-id
   *emits]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$streaming (po/agent-streaming-results-task-global *agent-name)]
   (anchor> <root>)
   (ops/explode *emits
                :> {:keys [*invoke-id *target-task-id *node-name *args]
                    :as   *emit})
   (hook:emit> *emit)
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *target-task-id)
   (aor-types/->valid-NodeOp
    *invoke-id
    ;; TODO: <<<<>>>> probably need original emits
    ;; to fill in fork-invoke-id
    ;;   - actually, generation of emits should
    ;;   do that when handling fork
    ;;     - will take previous emits and generate
    ;;     replacement invoke-ids while moving current
    ;;     invoke-id to the fork-invoke-id spot
    ;;   - this fragment needs invoke-id->new-args as an argument
    nil
    nil
    *node-name
    *args
    *agg-invoke-id
    :> *op)
   (anchor> <regular-emit>)

   (hook> <root>)
   (mapv :invoke-id *emits :> *next-invoke-ids)
   (reduce bit-xor *invoke-id *next-invoke-ids :> *ack-val)
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *agent-task-id)
   (<<atomic
     (hook:update-last-progress>)
     (local-transform>
      [(keypath *agent-id)
       :last-progress-time-millis
       (termval (h/current-time-millis))]
      $$root))
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
         (local-transform>
          [(keypath *agent-id)
           :result
           (termval (aor-types/->AgentResult "Agent completed without result"
                                             true))]
          $$root))
       (finished-streaming-chunk :> *finished-streaming-chunk)
       (local-transform>
        [(keypath *agent-id)
         MAP-VALS
         :all
         AFTER-ELEM
         (termval *finished-streaming-chunk)]
        $$streaming))
   )

   (unify> <regular-emit> <agg-ack-emit>)
   (:> *op)))

(defn hook:writing-result [agent-task-id agent-id result])

(deframaop handle-result!
  [*agent-name *agent-task-id *agent-id *retry-num *result]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)]
   (apart/|aor [*agent-name *agent-task-id *agent-id *retry-num]
               |direct
               *agent-task-id)
   (hook:writing-result *agent-task-id *agent-id *result)
   (local-transform>
    [(keypath *agent-id)
     :result
     ;; if race with retry and it happened to have finished, don't change the
     ;; result here – this can happen if the agent has other branches that fail
     ;; besides the one that created the result
     nil?
     (termval *result)]
    $$root)
   (local-transform> [(keypath *agent-id) NONE>] $$active)
   (:>)))

(deframaop init-root
  [*agent-name *agent-id *retry-num *args]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)]
   (fetch-graph-version *agent-name :> *version)
   (h/random-long :> *invoke-id)
   (h/current-time-millis :> *current-time-millis)
   (local-transform>
    [(keypath *agent-id)
     (termval {:root-invoke-id    *invoke-id
               :invoke-args       *args
               :graph-version     *version
               :ack-val           *invoke-id
               :last-progress-time-millis *current-time-millis
               :retry-num         *retry-num
               :start-time-millis *current-time-millis})]
    $$root)
   (:> *invoke-id)))

(defn init-retry-num [] 0)
(defn init-retry-num* [] (init-retry-num))

(deframaop gen-id
  [$$id]
  (local-select> STAY $$id :> *ret)
  (local-transform> (term inc) $$id)
  (:> *ret))

(deframaop intake-agent-invoke
  [*agent-name *data]
  (<<with-substitutions
   [$$id-gen (po/agent-id-gen-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (get *data :args :> *args)
   (ops/current-task-id :> *agent-task-id)
   (gen-id $$id-gen :> *agent-id)
   (init-retry-num* :> *retry-num)
   (init-root *agent-name *agent-id *retry-num *args :> *invoke-id)
   (local-transform> [(keypath *agent-id) (termval true)]
                     $$active)
   (aor-types/->valid-NodeOp *invoke-id
                             nil
                             nil
                             (get *agent-graph :start-node)
                             *args
                             nil
                             :> *op)
   (:> *agent-task-id *agent-id *retry-num *op)))

(defn hook:received-retry [agent-task-id agent-id retry-num])

(deframafn complete-with-failure!
  [*agent-name *agent-id *message]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)]
   (local-transform>
    ;; TODO: <<<<>>>> probably need to update finish-time as well
    [(keypath *agent-id)
     :result
     (termval (aor-types/->valid-AgentResult *message true))]
    $$root)
   (local-transform> [(keypath *agent-id) NONE>] $$active)
   (:>)))

(deframaop intake-retry
  [*agent-name {:keys [*agent-task-id *agent-id *expected-retry-num]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$gc-invokes (po/agent-gc-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (hook:received-retry *agent-task-id *agent-id *expected-retry-num)
   (local-select> (keypath *agent-id)
                  $$root
                  :> {*root-invoke-id :root-invoke-id
                      *curr-retry-num :retry-num
                      *graph-version :graph-version
                      *args :invoke-args
                      *result :result

                      {:keys [*invoke-id->new-args
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


   (<<if (= :drop *handle-mode)
     (complete-with-failure! *agent-name *agent-id "Retry dropped")
     (filter> false))
   (<<if (= :retry *handle-mode)
     (local-transform> [(keypath *root-invoke-id) (termval nil)]
                       $$gc-invokes)
     (init-retry-num* :> *retry-num)
     (init-root *agent-name *agent-id *retry-num *args :> *root-invoke-id)
    (else>)
     (inc *expected-retry-num :> *retry-num)
     (identity *root-invoke-id :> *root-invoke-id))

   (read-config *agent-name aor-types/MAX-RETRIES-CONFIG :> *max-retries)
   (<<if (> *retry-num *max-retries)
     (complete-with-failure! *agent-name *agent-id "Max retry limit exceeded")
    (else>)
     (local-transform> [(keypath *agent-id)
                        (multi-path [:retry-num (termval *retry-num)]
                                    [:ack-val (termval *root-invoke-id)])]
                       $$root)

     (aor-types/->valid-NodeOp *root-invoke-id
                               *parent-root-invoke-id
                               *invoke-id->new-args
                               (get *agent-graph :start-node)
                               *args
                               nil
                               :> *op)
     (:> *agent-task-id *agent-id *retry-num *op)
   )))

(deframaop intake-fork
  [*agent-name {:keys [*agent-task-id *agent-id *invoke-id->new-args]}]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$id-gen (po/agent-id-gen-task-global *agent-name)
    $$active (po/agent-active-invokes-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> (keypath *agent-id)
                  $$root
                  :> {:keys [*root-invoke-id *invoke-args *graph-version]})
   (<<if (nil? *invoke-args)
     (h/throw! (h/ex-info "Forked agent ID does not exist"
                          {:agent-id *agent-id})))
   (gen-id $$id-gen :> *fork-agent-id)
   (init-retry-num* :> *retry-num)
   (init-root *agent-name
              *fork-agent-id
              *retry-num
              *invoke-args
              :> *invoke-id)
   (local-select> [(keypath *fork-agent-id) :graph-version]
                  $$root
                  :> *fork-graph-version)
   (<<if (not= *graph-version *fork-graph-version)
     (h/throw! (h/ex-info "Cannot fork a run from an old version"
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
                      (termval {:parent-agent-id     *agent-id
                                :invoke-id->new-args *invoke-id->new-args})]
                     $$root)
   (aor-types/->valid-NodeOp *invoke-id
                             *root-invoke-id
                             *invoke-id->new-args
                             (get *agent-graph :start-node)
                             *invoke-args
                             nil
                             :> *op)
   (:> *agent-task-id *fork-agent-id *retry-num *op)))

(deframaop intake-node-failure
  [*agent-name {:keys [*invoke-id *retry-num]}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *failure-depot (po/agent-failures-depot-task-global *agent-name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id]})
   (filter> (some? *agent-id))
   (apart/filter-valid-retry-num> *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *retry-num)
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
   (mark-virtual-task-complete! *invoke-id)
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node
                             *agg-invoke-id]})
   (filter> (some? *agent-id))
   (apart/filter-valid-retry-num> *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *retry-num)

   (<<ramafn %merger
     [*m]
     (:> (reduce-kv assoc
                    *m
                    {:emits      *emits
                     :result     *result
                     :nested-ops *nested-ops
                     :finish-time-millis *finish-time-millis
                     :retry-time-millis *finish-time-millis})))
   (local-transform> [(keypath *invoke-id) (term %merger)]
                     $$nodes)
   (get-node-obj (po/agent-graph-task-global *agent-name) *node :> *node-obj)

   (<<subsource *node-obj
    (case> Node)
     (identity *invoke-id :> *invoke-id)

    (case> NodeAggStart)
     (local-transform> [(keypath *agg-invoke-id)
                        :agg-start-res
                        (termval *node-fn-res)]
                       $$nodes)
     (identity *invoke-id :> *invoke-id)


    (case> NodeAgg)
     (local-select> (keypath *invoke-id)
                    $$nodes
                    :> {*invoke-id :agg-start-invoke-id})
   )

   ;; AgentNode implementation makes it impossible for there to be both
   ;; emits and result
   (<<if (some? *result)
     (handle-result! *agent-name *agent-task-id *agent-id *retry-num *result))
   (send-emits> *agent-name
                *agent-task-id
                *agent-id
                *retry-num
                *invoke-id
                *agg-invoke-id
                *emits
                :> *op)
   (:> *agent-task-id *agent-id *retry-num *op)
  ))

(deframaop intake-agent-depot
  [*agent-name *data]
  (<<cond
   (case> (aor-types/AgentInvoke? *data))
    (intake-agent-invoke *agent-name
                         *data
                         :> *agent-task-id *agent-id *retry-num *op)
    (ack-return> [*agent-task-id *agent-id])

   (case> (aor-types/RetryAgentInvoke? *data))
    (intake-retry *agent-name
                  *data
                  :> *agent-task-id *agent-id *retry-num *op)

   (case> (aor-types/ForkAgentInvoke? *data))
    (intake-fork *agent-name
                 *data
                 :> *agent-task-id *agent-id *retry-num *op)
    (ack-return> [*agent-task-id *agent-id])

   (case> (aor-types/NodeFailure? *data))
    ;; doesn't actually emit here, but emit needed for unification
    (intake-node-failure *agent-name
                         *data
                         :> *agent-task-id *agent-id *retry-num *op)

   (case> (aor-types/NodeComplete? *data))
    (intake-node-complete *agent-name
                          *data
                          :> *agent-task-id *agent-id *retry-num *op)

   (default> :unify false)
    (throw! (h/ex-info "Unrecognized data type" {:class (class *data)})))
  (:> *agent-task-id *agent-id *retry-num *op))

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
   [$$root (po/agent-root-task-global *agent-name)
    $$streaming (po/agent-streaming-results-task-global *agent-name)]
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
    [(keypath *agent-id *node)
     (selected?
      :invokes
      (keypath *invoke-id)
      (nil->val -1)
      (pred %correct-index?))
     (multi-path
      [:all AFTER-ELEM (termval *chunk)]
      [:invokes (keypath *invoke-id) (termval *streaming-index)])]
    $$streaming)
  ))

(deframaop handle-config
  [*agent-name {:keys [*key *val]}]
  (<<with-substitutions
   [$$config (po/agent-config-task-global *agent-name)]
   (|all)
   (local-transform> [(keypath *key) (termval *val)] $$config)))


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

(deframaop complete-agg!
  [*agent-name *invoke-id *retry-num]
  (<<with-substitutions
   [$$root (po/agent-root-task-global *agent-name)
    $$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node *agg-ack-val
                             *agg-state *agg-start-res *agg-invoke-id]})
   ;; since agg state is colocated with root of graph invoke
   (local-select> [(keypath *agent-id) :fork-of :invoke-id->new-args]
                  $$root
                  :> *invoke-id->new-args)
   (local-transform>
    [(keypath *invoke-id) :agg-finished? (termval true)]
    $$nodes)
   (get-node-obj *agent-graph *node :> {:keys [*node-fn]})
   (vector *agg-state *agg-start-res :> *args)
   (anode/handle-node-invoke *agent-name
                             *agent-task-id
                             *agent-id
                             *node-fn
                             *invoke-id
                             *retry-num
                             *node
                             *args
                             *agg-invoke-id
                             *invoke-id->new-args)
   (:>)))

(deframaop ack-agg!
  [*agent-name *invoke-id *retry-num *ack-val]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   (local-select> [(keypath *invoke-id) :agg-ack-val] $$nodes :> *agg-ack-val)
   (bit-xor *ack-val *agg-ack-val :> *new-ack-val)
   (local-transform>
    [(keypath *invoke-id) :agg-ack-val (termval *new-ack-val)]
    $$nodes)
   (filter> (= 0 *new-ack-val))
   ;; replicate the new ack val before executing it to make potential retries
   ;; do less work
   (|direct (ops/current-task-id))
   (complete-agg! *agent-name *invoke-id *retry-num)
   (:>)))

(deframaop handle-node-op
  [*agent-name
   *agent-task-id
   *agent-id
   *retry-num
   {:keys [*invoke-id *next-node *args *agg-invoke-id
           *fork-invoke-id *invoke-id->new-args]}]
  (<<with-substitutions
   [$$nodes (po/agent-node-task-global *agent-name)
    *agent-graph (po/agent-graph-task-global *agent-name)]
   ;; TODO: <<<<>>>>
   ;;   - if invoke-id already exists
   ;;     - if finished-time-millis is written, then do RetryNodeComplete
   ;;         - just contains: invoke-id, retry-num, invoke-id->new-args
   ;;     - if not complete, then need to reset it's state?
   ;;       - just delete it and then continue like normal?
   ;;           - what about retry of an agg node?
   ;;     - maybe like this for aggs:
   ;;       - for retry, when get to agg-start-node, if the agg node is already
   ;;       done, just move on from there
   ;;         - if agg node is not done, but its ack val is 0, then just execute
   ;;         the agg node
   ;;       - otherwise, reset the agg node and agg-start-node completely
   ;;       and re-execute
   ;;  - if invoke-id does not exist:
   ;;     - if fork-invoke-id is set and that node exists:
   ;;        - check if fork-invoke-id is in invoke-id->new-args
   ;;        - if so, override args

   ;; TODO: <<<<<>>>>>>
   ;;  - fork of a node within agg graph needs to change that agg input...
   ;;     - ordering of agg inputs is random though with parallelization, so
   ;;     don't want to reorder if there's no fork there
   ;;       - maybe fork could say which agg-start-node invoke IDs are affected
   ;;     - seems like forks should just repeat everything and potentially
   ;;     change the order
   ;;     - another possibility is for forks to first walk the graph to
   ;;     determine aggs associated with each fork, and then execute it
   ;;         - so it would be invoke-id->[new-args, start-agg-invoke-id]
   ;;         - it can be a query topology to do the walk at the beginning
   ;;     - the fork can affect any number of aggregation subgraphs that it's
   ;;     nested within...
   ;;       - so any one with a fork inside needs to recompute
   ;;       - output of query topology is actually just a set of affected
   ;;       start-agg-node invoke IDs
   ;;         - on fork, they create brand new agg invoke ID state from scratch


   ;; TODO: <<<<>>>>>
   ;;    - how to handle retries/forks here?
   ;;      - can an agg node be forked?
   ;;        - yes, it's forking the input (agg val + agg-start-res), not the
   ;;        agg-inputs
   ;;    - just check here if node is already finished, and continue with its
   ;;    emits
   ;;      - if it's a fork, write the new invoke ID with everything copied
   ;;      over
   ;;        - copy over agg inputs?
   ;;        - if there was a result, need to send the result back
   ;;          - this would be in "NodeComplete"
   ;;          - for retries, need to send it over still and only write it if
   ;;          it's not there
   ;;     - so if it's finished here, it's either NodeComplete for fork or
   ;;     RetryNodeComplete
   (<<subsource (get-node-obj *agent-graph *next-node)
    (case> Node :> {:keys [*node-fn]})
     (anode/handle-node-invoke
      *agent-name
      *agent-task-id
      *agent-id
      *node-fn
      *invoke-id
      *retry-num
      *next-node
      *args
      *agg-invoke-id
      *invoke-id->new-args)

    (case> NodeAggStart :> {:keys [*node-fn *agg-node-name]})
     (h/random-long :> *new-agg-invoke-id)
     (local-transform>
      [(keypath *invoke-id) :started-agg? (termval true)]
      $$nodes)
     (get-node-obj *agent-graph *agg-node-name :> {:keys [*init-fn]})
     (anode/invoke-on-task-thread *agent-name
                                  *agent-task-id
                                  *agent-id
                                  *retry-num
                                  *init-fn
                                  :agg-init
                                  :> *init-agg-state)
     (local-transform>
      [(keypath *new-agg-invoke-id)
       (termval {:agent-id            *agent-id
                 :agent-task-id       *agent-task-id
                 :node                *agg-node-name
                 :start-time-millis   (h/current-time-millis)
                 :agg-invoke-id       *agg-invoke-id
                 :agg-inputs          []
                 :agg-state           *init-agg-state
                 :agg-ack-val         *invoke-id
                 :agg-start-invoke-id *invoke-id
                })]
      $$nodes)
     (anode/handle-node-invoke
      *agent-name
      *agent-task-id
      *agent-id
      *node-fn
      *invoke-id
      *retry-num
      *next-node
      *args
      *new-agg-invoke-id
      *invoke-id->new-args)


    (case> NodeAgg :> {:keys [*update-fn]})
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

     (<<if *finished?
       (complete-agg! *agent-name *agg-invoke-id *retry-num)
      (else>)
       (ack-agg! *agent-name *agg-invoke-id *retry-num *invoke-id))
   )))
