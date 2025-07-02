(ns com.rpl.agent-o-rama.impl.topology
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.client :as iclient]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops])
  (:import
   [com.rpl.agent_o_rama.impl.types
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.agentorama
    StreamingChunk]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]))

(deframafn read-config
  [*agent-name *k]
  (<<with-substitutions
   [$$config
    (this-module-pobject-task-global
     (po/agent-config-task-global-name *agent-name))]
   (local-select> (keypath *k) $$config :> *ret)
   (:> *ret)))

(defn get-node-obj
  [agent-graph node]
  (select-any [:node-map (keypath node) :node]
              agent-graph))

(defn hook:filtered-event [agent-task-id agent-id retry-num])

(deframafn valid-retry-num?
  [*agent-name *agent-task-id *agent-id *retry-num]
  (<<with-substitutions
   [$$valid
    (this-module-pobject-task-global (po/agent-valid-invokes-task-global-name
                                      *agent-name))]
   (local-select> (keypath [*agent-task-id *agent-id])
                  $$valid
                  :> *valid-retry-num)
   (:> (or> (nil? *valid-retry-num) (= *valid-retry-num *retry-num)))))

(deframaop filter-valid-retry-num>
  [*agent-name *agent-task-id *agent-id *retry-num]
  (<<if (valid-retry-num? *agent-name *agent-task-id *agent-id *retry-num)
    (:>)
   (else>)
    (hook:filtered-event *agent-task-id *agent-id *retry-num)))

(defbasicblocksegmacro |aor
  [:<* [[agent-name agent-task-id agent-id retry-num] & partitioner+args]]
  [(vec partitioner+args)
   [filter-valid-retry-num> agent-name agent-task-id agent-id retry-num]])

(defn fetch-graph
  [agent-name]
  (declared-object-task-global (po/agent-graph-task-global-name agent-name)))

(defn hook:finding-graph-version [starting-task-id])

(deframaop fetch-graph-version
  [*agent-name]
  (<<with-substitutions
   [*graph (fetch-graph *agent-name)
    $$graph-history
    (this-module-pobject-task-global (po/graph-history-task-global-name
                                      *agent-name))]
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
  (StreamingChunk.
   -1
   -1
   iclient/FINISHED))

(deframaop send-emits>
  [*agent-name *agent-task-id *agent-id *retry-num *invoke-id *agg-invoke-id
   *emits]
  (<<with-substitutions
   [$$root
    (this-module-pobject-task-global
     (po/agent-invoke-task-global-name *agent-name))

    $$streaming
    (this-module-pobject-task-global
     (po/agent-streaming-results-task-global-name *agent-name))]
   (anchor> <root>)
   (ops/explode *emits
                :> {:keys [*invoke-id *target-task-id *node-name *args]
                    :as   *emit})
   (hook:emit> *emit)
   (|aor [*agent-name *agent-task-id *agent-id *retry-num]
         |direct
         *target-task-id)
   (aor-types/->valid-NodeOp *invoke-id
                             *node-name
                             *args
                             *agg-invoke-id
                             :> *op)
   (anchor> <regular-emit>)

   (hook> <root>)
   (mapv :invoke-id *emits :> *next-invoke-ids)
   (reduce bit-xor *invoke-id *next-invoke-ids :> *ack-val)
   (|aor [*agent-name *agent-task-id *agent-id *retry-num]
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
   [$$root
    (this-module-pobject-task-global (po/agent-invoke-task-global-name
                                      *agent-name))
    $$active
    (this-module-pobject-task-global
     (po/agent-active-invokes-task-global-name *agent-name))]
   (|aor [*agent-name *agent-task-id *agent-id *retry-num]
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
   [$$root
    (this-module-pobject-task-global (po/agent-invoke-task-global-name
                                      *agent-name))]
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
   [$$id-gen
    (this-module-pobject-task-global (po/agent-id-gen-task-global-name
                                      *agent-name))
    $$active
    (this-module-pobject-task-global
     (po/agent-active-invokes-task-global-name *agent-name))
    *agent-graph (fetch-graph *agent-name)]
   (get *data :args :> *args)
   (ops/current-task-id :> *agent-task-id)
   (gen-id $$id-gen :> *agent-id)
   (init-retry-num* :> *retry-num)
   (init-root *agent-name *agent-id *retry-num *args :> *invoke-id)
   (local-transform> [(keypath *agent-id) (termval true)]
                     $$active)
   (aor-types/->valid-NodeOp *invoke-id
                             (get *agent-graph :start-node)
                             *args
                             nil
                             :> *op)
   (:> *agent-task-id *agent-id *retry-num *op)))

(defn hook:received-retry [agent-task-id agent-id retry-num])

(deframafn complete-with-failure!
  [$$root *agent-id *message]
  (local-transform>
   ;; TODO: <<<<>>>> probably need to update finish-time as well
   ;;    - factor this into helper "complete-with-failure!"
   [(keypath *agent-id)
    :result
    (termval (aor-types/->valid-AgentResult *message true))]
   $$root)
  (:>))

(deframaop intake-retry
  [*agent-name {:keys [*agent-task-id *agent-id *expected-retry-num]}]
  (<<with-substitutions
   [$$root
    (this-module-pobject-task-global
     (po/agent-invoke-task-global-name *agent-name))

    $$gc-invokes
    (this-module-pobject-task-global (po/agent-gc-invokes-task-global-name
                                      *agent-name))]
   (hook:received-retry *agent-task-id *agent-id *expected-retry-num)
   (local-select> (keypath *agent-id)
                  $$root
                  :> {*root-invoke-id :root-invoke-id
                      *curr-retry-num :retry-num
                      *graph-version  :graph-version
                      *args           :args})
   ;; if it got GC'd, ignore
   (filter> (some? *root-invoke-id))
   (filter> (= *expected-retry-num *curr-retry-num))
   (fetch-graph-version *agent-name :> *curr-graph-version)
   (<<cond
    (case> (= *curr-graph-version *graph-version))
     (identity :continue :> *handle-mode)

    (case> (= *curr-graph-version (inc *graph-version)))
     (fetch-graph *agent-name :> {*handle-mode :update-mode})

    (default>)
     ;; if somehow to module updates got through before the retry could be
     ;; processed, drop the retry since don't know if it's valid to
     ;; continue it
     (identity :drop :> *handle-mode))


   (<<if (= :drop *handle-mode)
     (complete-with-failure! $$root *agent-id "Retry dropped")
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
     (complete-with-failure! $$root *agent-id "Max retry limit exceeded")
    (else>)
     ;; TODO: <<<<>>>>
     ;;   - write new retry num
     ;;   - continue where it left off
     ;;     - share code with forking


     ;; TODO: <<<<>>>>
     ;;   - retry of fork needs to rehydrate invoke-id->new-args
     ;;   - on retry, check if it's already completed (got a result). if so
     ;;   just remove agent from active-agents – this is unnecessary since
     ;;   those happen together atomically – comment on this
     (identity nil :> *op)
     (filter> false)
     ;; TODO: <<<<>>>>
     ;;   - look at update mode in AgentGraph to determine how to handle
     ;;   version difference
     ;;      - if version difference is more than one, then log and drop
     ;;      - for retry mode, it's actually a full retry that should
     ;;      disregard current state
     ;;        - how does it GC the current state?
     ;;          - maybe write it's current root invoke ID to a special
     ;;          PState for later GC by tick
     ;;          - and then overwrite the node
     (:> *agent-task-id *agent-id *retry-num *op)
   )))

(deframaop intake-fork
  [*agent-name {:keys [*agent-task-id *agent-id *invoke-id->new-args]}]
  ;; TODO: <<<<<>>>>
  ;;  - need to track on root what this is a fork of, and also what
  ;;  invoke-id->new-args is for when this gets retried
  (identity 0 :> *retry-num)
  (identity nil :> *op)
  (filter> false))

(defn hook:appended-agent-failure [agent-task-id agent-id retry-num])

(deframaop intake-node-failure
  [*agent-name {:keys [*invoke-id *retry-num]}]
  (<<with-substitutions
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name
                                      *agent-name))

    *failure-depot
    (this-module-pobject-task-global (po/agent-failures-depot-name
                                      *agent-name))]
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id]})
   (filter> (some? *agent-id))
   (filter-valid-retry-num> *agent-name *agent-task-id *agent-id *retry-num)
   (depot-partition-append!
    *failure-depot
    (aor-types/->valid-AgentFailure *agent-task-id
                                    *agent-id
                                    *retry-num)
    :append-ack)
   (hook:appended-agent-failure *agent-task-id
                                *agent-id
                                *retry-num)
   (filter> false)))

(defn mark-virtual-task-complete!
  [invoke-id]
  (let [^AgentNodeExecutorTaskGlobal node-exec
        (declared-object-task-global (po/agent-node-executor-name))]
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
   [$$nodes
    (this-module-pobject-task-global (po/agent-node-task-global-name
                                      *agent-name))]
   (mark-virtual-task-complete! *invoke-id)
   (local-select> (keypath *invoke-id)
                  $$nodes
                  :> {:keys [*agent-task-id *agent-id *node
                             *agg-invoke-id]})
   (filter> (some? *agent-id))
   (filter-valid-retry-num> *agent-name *agent-task-id *agent-id *retry-num)

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
   (get-node-obj (fetch-graph *agent-name) *node :> *node-obj)

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
   [$$root
    (this-module-pobject-task-global
     (po/agent-invoke-task-global-name *agent-name))

    $$streaming
    (this-module-pobject-task-global
     (po/agent-streaming-results-task-global-name *agent-name))]
   (hook:processing-streaming *node *streaming-index *value)
   (local-select> [(keypath *agent-id) :retry-num (pred= *retry-num)] $$root)
   ;; this ensures idempotence
   (<<ramafn %correct-index?
     [*v]
     (:> (= (inc *v) *streaming-index)))
   (aor-types/mk-StreamingChunk
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
   [$$config
    (this-module-pobject-task-global
     (po/agent-config-task-global-name *agent-name))]
   (|all)
   (local-transform> [(keypath *key) (termval *val)] $$config)))
