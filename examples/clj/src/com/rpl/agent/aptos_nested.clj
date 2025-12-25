(ns com.rpl.agent.aptos-nested
  "Nested learning agent for Aptos blockchain with multi-level optimization.
   Google Research pattern: different update frequencies per level.
   Level 0: Fast (immediate tx decisions)
   Level 1: Medium (strategy updates)
   Level 2: Slow (model/policy evolution)"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.model.openai OpenAiChatModel]))

(def ^:private UPDATE-FREQUENCIES
  {:level-0-fast   1
   :level-1-medium 10
   :level-2-slow   100})

(def ^:private NESTED-STATE
  (atom {:tick            0
         :fast-decisions  []
         :strategy        {:risk-tolerance 0.5
                           :position-limit 1000
                           :slippage-max   0.02}
         :policy          {:model-version  1
                           :learning-rate  0.001
                           :exploration    0.1}}))

(defn- gf3-trit
  [seed]
  (let [v (mod (bit-xor seed (bit-shift-right seed 17)) 3)]
    (case v 0 :ergodic 1 :plus 2 :minus)))

(defn- splitmix64-step
  [seed]
  (let [z (+ seed 0x9E3779B97F4A7C15)]
    (-> z
        (bit-xor (bit-shift-right z 30))
        (* 0xBF58476D1CE4E5B9)
        (bit-and 0xFFFFFFFFFFFFFFFF))))

(defn- fast-decision
  [agent-node tx-context]
  (let [{:keys [strategy tick]} @NESTED-STATE
        {:keys [risk-tolerance position-limit slippage-max]} strategy
        seed       (splitmix64-step tick)
        trit       (gf3-trit seed)
        confidence (/ (mod seed 100) 100.0)]
    (swap! NESTED-STATE update :tick inc)
    (swap! NESTED-STATE update :fast-decisions conj
           {:tick   tick
            :trit   trit
            :action (cond
                      (< confidence risk-tolerance)   :execute
                      (> confidence (- 1 slippage-max)) :defer
                      :else                           :simulate)})
    {:level      0
     :type       :fast-decision
     :tick       tick
     :trit       trit
     :confidence confidence
     :decision   (last (:fast-decisions @NESTED-STATE))}))

(defn- strategy-update
  [agent-node performance-window]
  (let [{:keys [tick fast-decisions strategy]} @NESTED-STATE
        recent       (take-last (:level-1-medium UPDATE-FREQUENCIES) fast-decisions)
        success-rate (if (empty? recent)
                       0.5
                       (/ (count (filter #(= :execute (:action %)) recent))
                          (count recent)))
        trit-balance (->> recent
                          (map :trit)
                          (map {:ergodic 0 :plus 1 :minus -1})
                          (reduce + 0))]
    (swap! NESTED-STATE update :strategy merge
           {:risk-tolerance (+ (:risk-tolerance strategy)
                               (* 0.1 (- success-rate 0.5)))
            :last-update    tick
            :trit-balance   (mod trit-balance 3)})
    {:level        1
     :type         :strategy-update
     :tick         tick
     :success-rate success-rate
     :trit-balance trit-balance
     :new-strategy (:strategy @NESTED-STATE)}))

(defn- policy-evolve
  [agent-node evolution-context]
  (let [{:keys [tick strategy policy]} @NESTED-STATE
        {:keys [trit-balance]} strategy
        new-lr      (* (:learning-rate policy)
                       (case trit-balance
                         0 1.0
                         1 1.1
                         2 0.9
                         1.0))
        exploration (max 0.01 (- (:exploration policy) 0.001))]
    (swap! NESTED-STATE update :policy merge
           {:model-version (inc (:model-version policy))
            :learning-rate (min 0.1 (max 0.0001 new-lr))
            :exploration   exploration
            :last-evolve   tick})
    {:level      2
     :type       :policy-evolve
     :tick       tick
     :old-policy policy
     :new-policy (:policy @NESTED-STATE)}))

(defn- should-update?
  [level tick]
  (zero? (mod tick (get UPDATE-FREQUENCIES level 1))))

;;; ============================================================================
;;; GF(3) Constants for Tool Balance
;;; ============================================================================

(def ^:const GF3-PLUS  1)   ;; Write/execute operations
(def ^:const GF3-ZERO  0)   ;; Simulate/coordinate operations
(def ^:const GF3-MINUS -1)  ;; Read/observe operations

;;; ============================================================================
;;; GF(3) Balanced Tool Set (FORWARD-COMPATIBLE FIX)
;;; Σ trit = (+1) + (0) + (-1) = 0 ≡ 0 (mod 3) ✓ BALANCED
;;; ============================================================================

(def ^:private NESTED-TOOLS
  [;; Tool 1: aptos_tx (TRIT: +1, WRITE)
   (tools/tool-info
    (tools/tool-specification
     "aptos_tx"
     (lj/object
      {:description "Submit Aptos transaction (TRIT: +1)"
       :required    ["action" "amount"]}
      {"action" (lj/string "buy, sell, or hold")
       "amount" (lj/string "Amount in APT")})
     "Execute Aptos transaction via nested learning")
    (fn [agent-node _ arguments]
      (let [action (get arguments "action")
            amount (get arguments "amount")
            tick   (:tick @NESTED-STATE)]
        {:result (str "TX: " action " " amount " APT")
         :trit   GF3-PLUS
         :tick   tick
         :level  0}))
    {:include-context? true})

   ;; Tool 2: aptos_simulate (TRIT: 0, SIMULATE)
   (tools/tool-info
    (tools/tool-specification
     "aptos_simulate"
     (lj/object
      {:description "Simulate Aptos transaction without execution (TRIT: 0)"
       :required    ["action" "amount"]}
      {"action" (lj/string "buy, sell, or hold")
       "amount" (lj/string "Amount in APT")})
     "Simulate transaction to estimate gas and validate payload")
    (fn [agent-node _ arguments]
      (let [action (get arguments "action")
            amount (get arguments "amount")
            {:keys [strategy tick]} @NESTED-STATE
            {:keys [risk-tolerance slippage-max]} strategy
            seed (splitmix64-step tick)
            estimated-gas (+ 100 (mod seed 900))]
        {:result     (str "SIMULATE: " action " " amount " APT")
         :trit       GF3-ZERO
         :tick       tick
         :gas-estimate estimated-gas
         :risk-check (<= (/ (mod seed 100) 100.0) risk-tolerance)
         :level      1}))
    {:include-context? true})

   ;; Tool 3: aptos_state (TRIT: -1, READ)
   (tools/tool-info
    (tools/tool-specification
     "aptos_state"
     (lj/object
      {:description "Read current nested learning state (TRIT: -1)"
       :required    []}
      {"level" (lj/string "Optional: fast, strategy, or policy")})
     "Observe nested learning state for decision support")
    (fn [agent-node _ arguments]
      (let [level (get arguments "level" "all")
            {:keys [tick fast-decisions strategy policy]} @NESTED-STATE
            state-view (case level
                         "fast"     {:tick tick :recent (take-last 5 fast-decisions)}
                         "strategy" {:tick tick :strategy strategy}
                         "policy"   {:tick tick :policy policy}
                         {:tick tick
                          :fast (take-last 5 fast-decisions)
                          :strategy strategy
                          :policy policy})]
        {:result     state-view
         :trit       GF3-MINUS
         :tick       tick
         :level      (case level "fast" 0 "strategy" 1 "policy" 2 -1)}))
    {:include-context? true})])

;;; GF(3) Tally for NESTED-TOOLS:
;;; aptos_tx: +1, aptos_simulate: 0, aptos_state: -1
;;; Total: (+1) + (0) + (-1) = 0 ≡ 0 (mod 3) ✓ BALANCED

(aor/defagentmodule AptosNestedModule
  [topology]
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "demo-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (->
    topology
    (aor/new-agent "AptosNestedAgent")
    (aor/node
     "fast-decision"
     nil
     (fn [agent-node tx-context]
       (let [result (fast-decision agent-node tx-context)
             tick   (:tick result)]
         (when (should-update? :level-1-medium tick)
           (aor/emit! agent-node "strategy-update" {}))
         (when (should-update? :level-2-slow tick)
           (aor/emit! agent-node "policy-evolve" {}))
         (aor/result! agent-node result))))
    (aor/node
     "strategy-update"
     nil
     (fn [agent-node context]
       (let [result (strategy-update agent-node context)]
         (aor/result! agent-node result))))
    (aor/node
     "policy-evolve"
     nil
     (fn [agent-node context]
       (let [result (policy-evolve agent-node context)]
         (aor/result! agent-node result))))
    (aor/node
     "nested-loop"
     nil
     (fn [agent-node messages]
       (let [openai   (aor/get-agent-object agent-node "openai-model")
             response (lc4j/chat openai (lc4j/chat-request messages {:tools NESTED-TOOLS}))
             ai-msg   (.aiMessage response)]
         (aor/emit! agent-node "fast" {:input (.text ai-msg)})
         (aor/result! agent-node (.text ai-msg)))))))

(defn run-nested-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc AptosNestedModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name AptosNestedModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "AptosNestedAgent")]
      (println "Nested Learning Agent Ready")
      (println "Levels: Fast(1) | Medium(10) | Slow(100)")
      (loop []
        (print "> ")
        (flush)
        (when-let [input (read-line)]
          (when-not (= input "quit")
            (let [result (aor/agent-invoke agent [input])]
             (println result)
             (println "State:" @NESTED-STATE))
            (recur)))))))

;;; Forward-only variant: fixes node naming and enforces GF(3) triad toolset.

(def ^:private NESTED-STATE-FORWARD
  (atom {:tick            0
         :fast-decisions  []
         :strategy        {:risk-tolerance 0.5
                           :position-limit 1000
                           :slippage-max   0.02}
         :policy          {:model-version  1
                           :learning-rate  0.001
                           :exploration    0.1}}))

(defn- fast-decision-forward
  [agent-node tx-context]
  (let [{:keys [strategy tick]} @NESTED-STATE-FORWARD
        {:keys [risk-tolerance position-limit slippage-max]} strategy
        seed       (splitmix64-step tick)
        trit       (gf3-trit seed)
        confidence (/ (mod seed 100) 100.0)]
    (swap! NESTED-STATE-FORWARD update :tick inc)
    (swap! NESTED-STATE-FORWARD update :fast-decisions conj
           {:tick   tick
            :trit   trit
            :action (cond
                      (< confidence risk-tolerance)   :execute
                      (> confidence (- 1 slippage-max)) :defer
                      :else                           :simulate)})
    {:level      0
     :type       :fast-decision
     :tick       tick
     :trit       trit
     :confidence confidence
     :decision   (last (:fast-decisions @NESTED-STATE-FORWARD))}))

(defn- strategy-update-forward
  [agent-node performance-window]
  (let [{:keys [tick fast-decisions strategy]} @NESTED-STATE-FORWARD
        recent       (take-last (:level-1-medium UPDATE-FREQUENCIES) fast-decisions)
        success-rate (if (empty? recent)
                       0.5
                       (/ (count (filter #(= :execute (:action %)) recent))
                          (count recent)))
        trit-balance (->> recent
                          (map :trit)
                          (map {:ergodic 0 :plus 1 :minus -1})
                          (reduce + 0))]
    (swap! NESTED-STATE-FORWARD update :strategy merge
           {:risk-tolerance (+ (:risk-tolerance strategy)
                               (* 0.1 (- success-rate 0.5)))
            :last-update    tick
            :trit-balance   (mod trit-balance 3)})
    {:level        1
     :type         :strategy-update
     :tick         tick
     :success-rate success-rate
     :trit-balance trit-balance
     :new-strategy (:strategy @NESTED-STATE-FORWARD)}))

(defn- policy-evolve-forward
  [agent-node evolution-context]
  (let [{:keys [tick strategy policy]} @NESTED-STATE-FORWARD
        {:keys [trit-balance]} strategy
        new-lr      (* (:learning-rate policy)
                       (case trit-balance
                         0 1.0
                         1 1.1
                         2 0.9
                         1.0))
        exploration (max 0.01 (- (:exploration policy) 0.001))]
    (swap! NESTED-STATE-FORWARD update :policy merge
           {:model-version (inc (:model-version policy))
            :learning-rate (min 0.1 (max 0.0001 new-lr))
            :exploration   exploration
            :last-evolve   tick})
    {:level      2
     :type       :policy-evolve
     :tick       tick
     :old-policy policy
     :new-policy (:policy @NESTED-STATE-FORWARD)}))

(defn- gf3-add-forward
  [a b]
  (let [sum (+ a b)]
    (cond (> sum 1)  (- sum 3)
          (< sum -1) (+ sum 3)
          :else      sum)))

(defn- validate-nested-trit-conservation
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce gf3-add-forward 0 trits)]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :operations (mapv :operation tracked-results)}))

(def ^:private NESTED-TOOLS-FORWARD
  [(tools/tool-info
    (tools/tool-specification
     "aptos_tx_read"
     (lj/object
      {:description "Read-only Aptos context (TRIT: -1)"
       :required ["query"]}
      {"query" (lj/string "Read query description")})
     "Read context for nested decisions.")
    (fn [_agent-node _caller-data arguments]
      {:trit -1
       :operation :aptos-tx-read
       :query (get arguments "query")})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "aptos_tx_simulate"
     (lj/object
      {:description "Simulate Aptos action (TRIT: 0)"
       :required ["action" "amount"]}
      {"action" (lj/string "buy, sell, or hold")
       "amount" (lj/string "Amount in APT")})
     "Simulate nested decision action.")
    (fn [_agent-node _caller-data arguments]
      {:trit 0
       :operation :aptos-tx-simulate
       :action (get arguments "action")
       :amount (get arguments "amount")})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "aptos_tx_write"
     (lj/object
      {:description "Execute Aptos action (TRIT: +1)"
       :required ["action" "amount"]}
      {"action" (lj/string "buy, sell, or hold")
       "amount" (lj/string "Amount in APT")})
     "Execute nested decision action.")
    (fn [_agent-node _caller-data arguments]
      {:trit 1
       :operation :aptos-tx-write
       :action (get arguments "action")
       :amount (get arguments "amount")})
    {:include-context? true})])

(def ^:private FORWARD-PROMPT
  "Forward-only nested agent.

   Use the triad: read (-1) → simulate (0) → write (+1).
   Maintain GF(3) balance per cycle.")

(aor/defagentmodule AptosNestedForwardModule
  [topology]
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "demo-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (aor/declare-agent-object-builder
   topology
   "trit-accumulator"
   (fn [_setup]
     (atom [])))
  (tools/new-tools-agent topology "nested-tools-forward" NESTED-TOOLS-FORWARD)
  (-> topology
      (aor/new-agent "AptosNestedForwardAgent")
      (aor/node
       "fast"
       "fast"
       (fn [agent-node tx-context]
         (let [result (fast-decision-forward agent-node tx-context)
               tick   (:tick result)]
           (when (should-update? :level-1-medium tick)
             (aor/emit! agent-node "strategy-update" {}))
           (when (should-update? :level-2-slow tick)
             (aor/emit! agent-node "policy-evolve" {}))
           (aor/result! agent-node result))))
      (aor/node
       "strategy-update"
       nil
       (fn [agent-node context]
         (aor/result! agent-node (strategy-update-forward agent-node context))))
      (aor/node
       "policy-evolve"
       nil
       (fn [agent-node context]
         (aor/result! agent-node (policy-evolve-forward agent-node context))))
      (aor/node
       "nested-loop-forward"
       nil
       (fn [agent-node messages]
         (let [openai   (aor/get-agent-object agent-node "openai-model")
               tools    (aor/agent-client agent-node "nested-tools-forward")
               trit-acc (aor/get-agent-object agent-node "trit-accumulator")
               full-msgs (cons {:role "system" :content FORWARD-PROMPT} messages)
               response (lc4j/chat openai (lc4j/chat-request full-msgs {:tools NESTED-TOOLS-FORWARD}))
               ai-msg   (.aiMessage response)
               tool-calls (vec (.toolExecutionRequests ai-msg))]
           (when (seq tool-calls)
             (let [tool-results (aor/agent-invoke tools tool-calls)]
               (doseq [tr tool-results]
                 (when (contains? tr :trit)
                   (swap! trit-acc conj tr)))
               (aor/emit! agent-node "fast" {:input (.text ai-msg)})
               (aor/result! agent-node
                            {:response (.text ai-msg)
                             :tool-results tool-results
                             :trit-tracking (validate-nested-trit-conservation @trit-acc)}))))))))

(defn run-nested-agent-forward
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc AptosNestedForwardModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name AptosNestedForwardModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "AptosNestedForwardAgent")]
      (println "Nested Forward Agent Ready")
      (println "Levels: Fast(1) | Medium(10) | Slow(100)")
      (loop []
        (print "> ")
        (flush)
        (when-let [input (read-line)]
          (when-not (= input "quit")
            (let [result (aor/agent-invoke agent [input])]
              (println result)
              (println "State:" @NESTED-STATE-FORWARD))
            (recur)))))))
