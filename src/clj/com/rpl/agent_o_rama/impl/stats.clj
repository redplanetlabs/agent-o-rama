(ns com.rpl.agent-o-rama.impl.stats
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]))


(def EMPTY-OP-STATS (aor-types/->valid-OpStatsImpl 0 0))
(def EMPTY-BASIC-STATS (aor-types/->valid-BasicAgentInvokeStatsImpl {} 0 0 0 {}))
(def EMPTY-SUBAGENT-STATS (aor-types/->valid-SubagentInvokeStatsImpl 0 EMPTY-BASIC-STATS))
(def EMPTY-AGENT-STATS (aor-types/->valid-AgentInvokeStatsImpl {} EMPTY-BASIC-STATS))

(defn adder
  [v]
  (fn [v2]
    (+ v v2)))

(defn merge-op-stats
  [m1 m2]
  (merge-with
   (fn [o1 o2]
     (aor-types/->valid-OpStatsImpl
      (+ (:count o1) (:count o2))
      (+ (:total-time-millis o1) (:total-time-millis o2))))
   m1
   m2))

(defn combine-basic-stats
  [b1 b2]
  (aor-types/->valid-BasicAgentInvokeStatsImpl
   (merge-op-stats (:nested-op-stats b1) (:nested-op-stats b2))
   (+ (:input-token-count b1) (:input-token-count b2))
   (+ (:output-token-count b1) (:output-token-count b2))
   (+ (:total-token-count b1) (:total-token-count b2))
   (merge-op-stats (:node-stats b1) (:node-stats b2))))

(defn aggregated-basic-stats
  [stats]
  (reduce
   combine-basic-stats
   (:basic-stats stats)
   (traverse [:subagent-stats MAP-VALS :basic-stats] stats)))

(defn merge-subagent-stats
  [m1 m2]
  (merge-with
   (fn [sa1 sa2]
     (aor-types/->valid-SubagentInvokeStatsImpl
      (+ (:count sa1) (:count sa2))
      (combine-basic-stats (:basic-stats sa1) (:basic-stats sa2))))
   m1
   m2))

(defn agent-stats-merger
  [stats]
  (fn [existing]
    (if (nil? stats)
      existing
      (aor-types/->valid-AgentInvokeStatsImpl
       (merge-subagent-stats (:subagent-stats existing) (:subagent-stats stats))
       (combine-basic-stats (:basic-stats existing) (:basic-stats stats))))))


(defn nested-op-token-counts
  [{:keys [type info]}]
  (if (= :model-call type)
    {:input  (get info "inputTokenCount" 0)
     :output (get info "outputTokenCount" 0)
     :total  (get info "totalTokenCount" 0)}
    {:input  0
     :output 0
     :total  0}))

(defn nested-op-stats
  [nested-ops]
  (let [tc-vol   (volatile! {:input  0
                             :output 0
                             :total  0})
        nops-vol (volatile! {})
        sa-vol   (volatile! {})]
    (doseq [{:keys [start-time-millis finish-time-millis type info] :as nested-op} nested-ops]
      (let [delta-millis (- finish-time-millis start-time-millis)
            token-counts (nested-op-token-counts nested-op)]
        (multi-transform [h/VOLATILE
                          (keypath type)
                          (nil->val EMPTY-OP-STATS)
                          (multi-path [:count (term inc)]
                                      [:total-time-millis (term (adder delta-millis))])]
                         nops-vol)
        (multi-transform [h/VOLATILE
                          (multi-path [:input (term (adder (:input token-counts)))]
                                      [:output (term (adder (:output token-counts)))]
                                      [:total (term (adder (:total token-counts)))])]
                         tc-vol)
        (when (= :agent-call type)
          (let [agent-module-name (get info "agent-module-name")
                agent-name        (get info "agent-name")
                sub-stats         (get info "stats")]
            ;; just in case user sets these themselves
            (when (and (string? agent-module-name)
                       (string? agent-name)
                       (aor-types/AgentInvokeStatsImpl? sub-stats))
              (transform h/VOLATILE #(merge-subagent-stats % (:subagent-stats sub-stats)) sa-vol)
              (multi-transform
               [h/VOLATILE
                (keypath (aor-types/->valid-AgentRefImpl agent-module-name agent-name))
                (nil->val EMPTY-SUBAGENT-STATS)
                (multi-path
                 [:count (term inc)]
                 [:basic-stats (term #(combine-basic-stats % (:basic-stats sub-stats)))])]
               sa-vol)
            )))
      ))
    {:subagent-stats  @sa-vol
     :nested-op-stats @nops-vol
     :token-counts    @tc-vol}))

(defn mk-node-stats
  [node start-time-millis finish-time-millis nested-ops]
  (let [nstats       (nested-op-stats nested-ops)
        token-counts (:token-counts nstats)]
    (aor-types/->valid-AgentInvokeStatsImpl
     (:subagent-stats nstats)
     (aor-types/->valid-BasicAgentInvokeStatsImpl
      (:nested-op-stats nstats)
      (:input token-counts)
      (:output token-counts)
      (:total token-counts)
      {node (aor-types/->valid-OpStatsImpl 1 (- finish-time-millis start-time-millis))}
     ))))
