(ns com.rpl.agent-o-rama.impl.analytics
  (:use [com.rpl.rama]
        [com.rpl.rama path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.ops :as ops]))


(def EMPTY-OP-STATS (aor-types/->valid-OpStats 0 0))
(def EMPTY-BASIC-STATS (aor-types/->valid-BasicAgentInvokeStats {} 0 0 0 {}))
(def EMPTY-SUBAGENT-STATS (aor-types/->valid-SubagentInvokeStats 0 EMPTY-BASIC-STATS))
(def EMPTY-AGENT-STATS (aor-types/->valid-AgentInvokeStats {} EMPTY-BASIC-STATS))

(defn adder
  [v]
  (fn [v2]
    (+ v v2)))

(defn merge-op-stats
  [m1 m2]
  (merge-with
   (fn [o1 o2]
     (aor-types/->valid-OpStats
      (+ (:count o1) (:count o2))
      (+ (:total-time-millis o1) (:total-time-millis o2))))
   m1
   m2))

(defn combine-basic-stats
  [b1 b2]
  (aor-types/->valid-BasicAgentInvokeStats
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
     (aor-types/->valid-SubagentInvokeStats
      (+ (:count sa1) (:count sa2))
      (combine-basic-stats (:basic-stats sa1) (:basic-stats sa2))))
   m1
   m2))

(defn agent-stats-merger
  [stats]
  (fn [existing]
    (if (nil? stats)
      existing
      (aor-types/->valid-AgentInvokeStats
       (merge-subagent-stats (:subagent-stats existing) (:subagent-stats stats))
       (combine-basic-stats (:basic-stats existing) (:basic-stats stats))))))

(defn mk-node-stats
  [node start-time-millis finish-time-millis nested-ops]
  (let [tc-vol   (volatile! {:input  0
                             :output 0
                             :total  0})
        nops-vol (volatile! {})
        sa-vol   (volatile! {})]
    (doseq [{:keys [start-time-millis finish-time-millis type info]} nested-ops]
      (let [delta-millis (- finish-time-millis start-time-millis)]
        (multi-transform [h/VOLATILE
                          (keypath type)
                          (nil->val EMPTY-OP-STATS)
                          (multi-path [:count (term inc)]
                                      [:total-time-millis (term (adder delta-millis))])]
                         nops-vol)
        (when (= :model-call type)
          (multi-transform [h/VOLATILE
                            (multi-path [:input (term (adder (get info "inputTokenCount" 0)))]
                                        [:output (term (adder (get info "outputTokenCount" 0)))]
                                        [:total (term (adder (get info "totalTokenCount" 0)))])]
                           tc-vol))
        (when (= :agent-call type)
          (let [agent-module-name (get info "agent-module-name")
                agent-name        (get info "agent-name")
                sub-stats         (get info "stats")
               ]
            ;; just in case user sets these themselves
            (when (and (string? agent-module-name)
                       (string? agent-name)
                       (aor-types/AgentInvokeStats? sub-stats))
              (transform h/VOLATILE #(merge-subagent-stats % (:subagent-stats sub-stats)) sa-vol)
              (multi-transform
               [h/VOLATILE
                (keypath (aor-types/->valid-AgentRef agent-module-name agent-name))
                (nil->val EMPTY-SUBAGENT-STATS)
                (multi-path
                 [:count (term inc)]
                 [:basic-stats (term #(combine-basic-stats % (:basic-stats sub-stats)))])]
               sa-vol)
            )))
      ))
    (aor-types/->valid-AgentInvokeStats
     @sa-vol
     (aor-types/->valid-BasicAgentInvokeStats
      @nops-vol
      (:input @tc-vol)
      (:output @tc-vol)
      (:total @tc-vol)
      {node (aor-types/->valid-OpStats 1 (- finish-time-millis start-time-millis))}
     ))))
