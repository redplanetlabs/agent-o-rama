(ns com.rpl.agent.topos-mcp
  "Unified Topos MCP Orchestrator - GF(3) conserving incidence algebra.

   From Aptos LLMs.txt:
   • 'Keyless Accounts: Onboard users and sign without wallets'
   • 'Sponsored Transactions: Pay for users' gas'
   • 'View functions do not modify blockchain state'

   Requires all 5 stream modules:
   - topos-alice       (PLUS +1)         : Generator, write operations
   - topos-bob         (MINUS -1)        : Validator, read operations
   - topos-alice-plus-bob (ERGODIC 0)    : Convolution ζ_A * ζ_B
   - topos-alice-minus-bob (Möbius -1)   : A * μ(B), write without read
   - topos-bob-minus-alice (Inverse +1)  : B * μ(A), read without write

   GF(3) Conservation at Module Level:
   Σ trit ≡ 0 (mod 3) verified at end of each request

   Incidence Algebra Operations:
   - (alice * bob) = simulate via convolution
   - (alice - bob) = alice * μ(bob) (Möbius inversion)
   - (bob - alice) = bob * μ(alice) (inverse Möbius)

   Genesis Seed: 0x42D
   Derivation: SplitMixTernary with GF(3) trit coloring"
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.topos-alice :as alice]
   [com.rpl.agent.topos-bob :as bob]
   [com.rpl.agent.topos-alice-plus-bob :as alice+bob]
   [com.rpl.agent.topos-alice-minus-bob :as alice-bob]
   [com.rpl.agent.topos-bob-minus-alice :as bob-alice])
  (:import
   [dev.langchain4j.data.message UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]))

;;; ============================================================================
;;; Genesis Seed & GF(3) Constants
;;; ============================================================================

(def ^:const GENESIS-SEED 0x42D)
(def ^:const GOLDEN-GAMMA 0x9E3779B97F4A7C15)

(def ^:const TRIT-PLUS  +1)  ;; Alice, Bob-Alice: write/action
(def ^:const TRIT-ZERO   0)  ;; Alice+Bob: simulation/ergodic
(def ^:const TRIT-MINUS -1)  ;; Bob, Alice-Bob: read/perception

;;; ============================================================================
;;; GF(3) Arithmetic
;;; ============================================================================

(defn gf3-add
  "Addition in GF(3): result in {-1, 0, +1}"
  [a b]
  (let [sum (+ a b)]
    (cond
      (> sum 1)  (- sum 3)
      (< sum -1) (+ sum 3)
      :else      sum)))

(defn gf3-multiply
  "Multiplication in GF(3)"
  [a b]
  (let [prod (* a b)]
    (cond
      (> prod 1)  (- prod 3)
      (< prod -1) (+ prod 3)
      :else       prod)))

(defn gf3-conserved?
  "Validate GF(3) conservation: Σ trits ≡ 0 (mod 3)"
  [trits]
  (zero? (reduce gf3-add 0 trits)))

(defn gf3-balance-needed
  "Calculate how many operations needed to balance"
  [current-sum target-trit]
  (let [diff (gf3-add (- target-trit) current-sum)]
    {:needed diff :current current-sum :target target-trit}))

;;; ============================================================================
;;; Stream Definitions with GF(3) Trit Assignment
;;; ============================================================================

(def streams
  {:alice      {:trit TRIT-PLUS  :role :generator   :module 'topos-alice
                :description "PLUS (+1): Write operations, fund/transfer/stake"}
   :bob        {:trit TRIT-MINUS :role :validator   :module 'topos-bob
                :description "MINUS (-1): Read operations, balance/view/modules/resources"}
   :alice+bob  {:trit TRIT-ZERO  :role :simulator   :module 'topos-alice-plus-bob
                :description "ERGODIC (0): Simulation, convolution ζ_A * ζ_B"}
   :alice-bob  {:trit TRIT-MINUS :role :perception  :module 'topos-alice-minus-bob
                :description "Möbius (-1): A * μ(B), write without read (dangerous)"}
   :bob-alice  {:trit TRIT-PLUS  :role :action      :module 'topos-bob-minus-alice
                :description "Inverse Möbius (+1): B * μ(A), read without write (cached)"}})

(defn stream-trit [stream-key]
  (get-in streams [stream-key :trit]))

;;; ============================================================================
;;; Möbius Function for Incidence Algebra
;;; ============================================================================

(defn prime-factors
  "Return prime factors with multiplicities"
  [n]
  (loop [n n d 2 factors {}]
    (cond
      (< n 2) factors
      (zero? (mod n d)) (recur (quot n d) d (update factors d (fnil inc 0)))
      (> (* d d) n) (if (> n 1) (assoc factors n 1) factors)
      :else (recur n (inc d) factors))))

(defn moebius
  "Classical Möbius function μ(n):
   μ(1) = 1
   μ(n) = (-1)^k if n = p₁×p₂×...×pₖ (k distinct primes)
   μ(n) = 0 if n has squared prime factor
   
   Critical: μ(3) = -1 (3 is prime)"
  [n]
  (cond
    (= n 1) 1
    :else
    (let [factors (prime-factors n)]
      (if (some #(> % 1) (vals factors))
        0
        (if (even? (count factors)) 1 -1)))))

(def MOEBIUS-3 (moebius 3))  ;; = -1

;;; ============================================================================
;;; Incidence Algebra Operations
;;; ============================================================================

(defn zeta
  "Zeta function: ζ(d,n) = 1 if d|n, else 0"
  [d n]
  (if (and (pos? d) (zero? (mod n d))) 1 0))

(defn divisors
  "All divisors of n"
  [n]
  (for [d (range 1 (inc n)) :when (zero? (mod n d))] d))

(defn dirichlet-convolve
  "Dirichlet convolution: (f * g)(n) = Σ_{d|n} f(d) × g(n/d)
   Used for alice * bob = simulate"
  [f g n]
  (reduce + (for [d (divisors n)]
              (* (f d) (g (quot n d))))))

(defn moebius-invert
  "Möbius inversion: g(n) = Σ_{d|n} μ(n/d) × f(d)
   Used for alice - bob = alice * μ(bob)"
  [f n]
  (reduce + (for [d (divisors n)]
              (* (moebius (quot n d)) (f d)))))

(defn incidence-algebra-ops
  "Define the three core incidence algebra operations"
  []
  {:convolve     dirichlet-convolve
   :moebius-inv  moebius-invert
   :zeta         zeta
   :moebius      moebius})

;;; ============================================================================
;;; SplitMixTernary RNG (Deterministic from Genesis)
;;; ============================================================================

(defn splitmix64-next
  "SplitMix64 state transition"
  [^long state]
  (let [z (unchecked-add state GOLDEN-GAMMA)]
    (-> z
        (bit-xor (unsigned-bit-shift-right z 30))
        (unchecked-multiply 0xBF58476D1CE4E5B9)
        (bit-xor (fn [x] (unsigned-bit-shift-right x 27)))
        (unchecked-multiply 0x94D049BB133111EB)
        (bit-and 0x7FFFFFFFFFFFFFFF))))

(defn seed->trit
  "Map seed to GF(3) trit deterministically"
  [seed]
  (case (mod seed 3)
    0  0
    1 +1
    2 -1))

(defn derive-stream-seed
  "Derive deterministic seed for each stream"
  [stream-key]
  (let [offset (case stream-key
                 :alice     0
                 :bob       1
                 :alice+bob 2
                 :alice-bob 3
                 :bob-alice 4
                 0)]
    (splitmix64-next (+ GENESIS-SEED (* offset 0x1000)))))

;;; ============================================================================
;;; Trit Accumulator for Conservation Tracking
;;; ============================================================================

(defrecord TritInvocation
  [stream-key trit operation result timestamp seed])

(defn track-invocation
  "Track an invocation with its trit value"
  [stream-key operation result]
  (let [trit (stream-trit stream-key)
        seed (derive-stream-seed stream-key)]
    (->TritInvocation stream-key trit operation result 
                      (System/currentTimeMillis) seed)))

;;; ============================================================================
;;; Stream Router - Routes to Correct Stream Based on Intent
;;; ============================================================================

(defn classify-intent
  "Classify user intent to appropriate stream.
   
   From Aptos LLMs.txt:
   - 'View functions do not modify blockchain state' → bob
   - 'Sponsored Transactions: Pay for users' gas' → alice
   - 'Keyless Accounts: Onboard users and sign without wallets' → alice+bob"
  [intent]
  (let [intent-lower (str/lower-case (str intent))]
    (cond
      ;; ERGODIC (0): Simulation operations
      (or (str/includes? intent-lower "simulate")
          (str/includes? intent-lower "estimate")
          (str/includes? intent-lower "validate")
          (str/includes? intent-lower "keyless"))
      :alice+bob
      
      ;; PLUS (+1): Write operations  
      (or (str/includes? intent-lower "fund")
          (str/includes? intent-lower "transfer")
          (str/includes? intent-lower "stake")
          (str/includes? intent-lower "execute")
          (str/includes? intent-lower "sponsor"))
      :alice
      
      ;; MINUS (-1): Read operations
      (or (str/includes? intent-lower "balance")
          (str/includes? intent-lower "view")
          (str/includes? intent-lower "modules")
          (str/includes? intent-lower "resources")
          (str/includes? intent-lower "query"))
      :bob
      
      ;; Möbius (-1): Dangerous write without read
      (or (str/includes? intent-lower "blind")
          (str/includes? intent-lower "force")
          (str/includes? intent-lower "emergency"))
      :alice-bob
      
      ;; Inverse Möbius (+1): Cached read without write
      (or (str/includes? intent-lower "cache")
          (str/includes? intent-lower "historical")
          (str/includes? intent-lower "snapshot"))
      :bob-alice
      
      ;; Default to simulation
      :else :alice+bob)))

;;; ============================================================================
;;; GF(3) Conservation Validator
;;; ============================================================================

(defn validate-gf3-conservation
  "Validate GF(3) conservation across all invocations.
   
   Returns detailed conservation analysis."
  [invocations]
  (let [trits (mapv :trit invocations)
        sum (reduce gf3-add 0 trits)
        conserved? (zero? sum)
        by-stream (group-by :stream-key invocations)
        stream-counts (into {} (map (fn [[k v]] [k (count v)]) by-stream))]
    {:conserved? conserved?
     :sum sum
     :trits trits
     :count (count invocations)
     :by-stream stream-counts
     :operations (mapv :operation invocations)
     :balance-suggestion (when-not conserved?
                           (cond
                             (= sum 1)  "Add 2 MINUS (-1) operations or 1 PLUS (+1)"
                             (= sum -1) "Add 2 PLUS (+1) operations or 1 MINUS (-1)"
                             :else nil))}))

;;; ============================================================================
;;; Stream Executor Functions
;;; ============================================================================

(defn execute-alice-stream
  "Execute PLUS (+1) stream - write operations.
   
   'Sponsored Transactions: Pay for users' gas'"
  [request]
  (let [{:keys [operation args]} request]
    (case operation
      :fund     (alice/aptos-fund-tool args)
      :transfer (alice/aptos-transfer-execute-tool args)
      :stake    (alice/aptos-stake-tool args)
      {:error "Unknown alice operation" :operation operation})))

(defn execute-bob-stream
  "Execute MINUS (-1) stream - read operations.
   
   'View functions do not modify blockchain state'"
  [request]
  (let [{:keys [operation args]} request]
    (case operation
      :balance   (bob/aptos-balance-tool args)
      :view      (bob/aptos-view-tool args)
      :modules   (bob/aptos-modules-tool args)
      :resources (bob/aptos-resources-tool args)
      {:error "Unknown bob operation" :operation operation})))

(defn execute-alice+bob-stream
  "Execute ERGODIC (0) stream - simulation operations.
   
   (ζ_alice * ζ_bob) = convolution for simulation"
  [request]
  (let [{:keys [operation args]} request]
    (case operation
      :simulate     (alice+bob/aptos-simulate-transfer-tool args)
      :estimate-gas (alice+bob/aptos-estimate-gas-tool args)
      :validate     (alice+bob/aptos-validate-payload-tool args)
      {:error "Unknown alice+bob operation" :operation operation})))

(defn execute-alice-bob-stream
  "Execute Möbius (-1) stream - write without read.
   
   alice * μ(bob) = dangerous blind operations"
  [request]
  (let [{:keys [operation args]} request]
    (case operation
      :blind-transfer   (alice-bob/aptos-blind-transfer-tool args)
      :force-stake      (alice-bob/aptos-force-stake-tool args)
      :emergency-unstake (alice-bob/aptos-emergency-unstake-tool args)
      {:error "Unknown alice-bob operation" :operation operation})))

(defn execute-bob-alice-stream
  "Execute Inverse Möbius (+1) stream - read without write.
   
   bob * μ(alice) = cached/historical reads"
  [request]
  (let [{:keys [operation args]} request]
    (case operation
      :cached-balance    (bob-alice/aptos-cached-balance-tool args)
      :historical-view   (bob-alice/aptos-historical-view-tool args)
      :snapshot-resources (bob-alice/aptos-snapshot-resources-tool args)
      {:error "Unknown bob-alice operation" :operation operation})))

(defn execute-stream
  "Route execution to appropriate stream"
  [stream-key request]
  (case stream-key
    :alice     (execute-alice-stream request)
    :bob       (execute-bob-stream request)
    :alice+bob (execute-alice+bob-stream request)
    :alice-bob (execute-alice-bob-stream request)
    :bob-alice (execute-bob-alice-stream request)
    {:error "Unknown stream" :stream stream-key}))

;;; ============================================================================
;;; Unified Topos MCP Tools
;;; ============================================================================

(defn topos-route-tool
  "Route intent to appropriate stream and execute.
   
   From Aptos LLMs.txt:
   'Keyless Accounts: Onboard users and sign without wallets'"
  [args]
  (let [intent (get args "intent")
        operation (get args "operation")
        op-args (get args "args" {})
        stream-key (classify-intent intent)]
    {:stream stream-key
     :trit (stream-trit stream-key)
     :result (execute-stream stream-key {:operation (keyword operation) 
                                         :args op-args})
     :description (get-in streams [stream-key :description])}))

(def TOPOS-ROUTE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "topos-route"
    (lj/object
     {:description "Route intent to GF(3) stream and execute"
      :required ["intent" "operation"]}
     {"intent" (lj/string "Natural language description of intent")
      "operation" (lj/string "Specific operation (fund, balance, simulate, etc.)")
      "args" (lj/object {:description "Operation arguments"} {})})
    "Route to appropriate stream based on intent. Keyless Accounts: Onboard users and sign without wallets")
   topos-route-tool))

(defn topos-convolve-tool
  "Compute incidence algebra convolution: (alice * bob).
   
   Simulates transaction by convolving write and read operations."
  [args]
  (let [n (get args "n" 6)
        f-type (get args "f" "zeta")
        g-type (get args "g" "zeta")
        f (case f-type "moebius" moebius "zeta" (fn [d] (zeta d n)) identity)
        g (case g-type "moebius" moebius "zeta" (fn [d] (zeta d n)) identity)]
    {:operation :convolve
     :n n
     :f f-type
     :g g-type
     :result (dirichlet-convolve f g n)
     :divisors (divisors n)
     :note "(alice * bob) = simulate transaction via convolution"}))

(def TOPOS-CONVOLVE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "topos-convolve"
    (lj/object
     {:description "Dirichlet convolution (f * g)(n)"
      :required ["n"]}
     {"n" (lj/number "Integer to compute convolution at")
      "f" (lj/enum "First function" ["zeta" "moebius" "identity"])
      "g" (lj/enum "Second function" ["zeta" "moebius" "identity"])})
    "Compute (alice * bob) = simulate via incidence algebra convolution")
   topos-convolve-tool))

(defn topos-moebius-tool
  "Compute Möbius inversion: alice * μ(bob) or bob * μ(alice).
   
   alice - bob = alice * μ(bob)
   bob - alice = bob * μ(alice)"
  [args]
  (let [n (get args "n" 6)
        direction (get args "direction" "alice-bob")
        f (case direction
            "alice-bob" (fn [d] d)      ;; alice contribution
            "bob-alice" (fn [d] (- d))  ;; bob contribution (negated)
            identity)]
    {:operation :moebius-invert
     :n n
     :direction direction
     :result (moebius-invert f n)
     :moebius-values (mapv (fn [d] {:d d :mu (moebius (quot n d))}) (divisors n))
     :note (case direction
             "alice-bob" "(alice - bob) = alice * μ(bob), write without read"
             "bob-alice" "(bob - alice) = bob * μ(alice), read without write"
             "Möbius inversion")}))

(def TOPOS-MOEBIUS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "topos-moebius"
    (lj/object
     {:description "Möbius inversion for stream differences"
      :required ["n"]}
     {"n" (lj/number "Integer to compute inversion at")
      "direction" (lj/enum "Inversion direction" ["alice-bob" "bob-alice"])})
    "Compute (alice - bob) = alice * μ(bob) via Möbius inversion")
   topos-moebius-tool))

(defn topos-gf3-check-tool
  "Check GF(3) conservation status.
   
   Verify Σ trit ≡ 0 (mod 3) at end of request."
  [args]
  (let [trits (get args "trits" [])
        parsed-trits (if (string? trits)
                       (mapv #(Integer/parseInt %) (str/split trits #","))
                       trits)]
    {:operation :gf3-check
     :trits parsed-trits
     :sum (reduce gf3-add 0 parsed-trits)
     :conserved? (gf3-conserved? parsed-trits)
     :count (count parsed-trits)
     :note "GF(3) conservation: Σ trit ≡ 0 (mod 3)"}))

(def TOPOS-GF3-CHECK-TOOL
  (tools/tool-info
   (tools/tool-specification
    "topos-gf3-check"
    (lj/object
     {:description "Verify GF(3) conservation"
      :required ["trits"]}
     {"trits" (lj/array "Array of trit values" (lj/number "Trit (-1, 0, or +1)"))})
    "Verify Σ trit ≡ 0 (mod 3). Sponsored Transactions: Pay for users' gas")
   topos-gf3-check-tool))

;;; ============================================================================
;;; All Topos MCP Tools
;;; ============================================================================

(def TOPOS-MCP-TOOLS
  [TOPOS-ROUTE-TOOL
   TOPOS-CONVOLVE-TOOL
   TOPOS-MOEBIUS-TOOL
   TOPOS-GF3-CHECK-TOOL])

;;; ============================================================================
;;; Agent Module Definition
;;; ============================================================================

(aor/defagentmodule ToposMcpModule
  [topology]

  ;; Declare OpenAI model for intent classification
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "demo-key"))
         (.modelName "gpt-4o-mini")
         .build)))

  ;; Declare trit accumulator for GF(3) tracking
  (aor/declare-agent-object-builder
   topology
   "trit-accumulator"
   (fn [_setup]
     (atom {:invocations []
            :genesis-seed GENESIS-SEED
            :session-start (System/currentTimeMillis)})))

  ;; Create tools agents for each stream
  (tools/new-tools-agent topology "AliceToolsAgent" alice/ALICE-PLUS-TOOLS)
  (tools/new-tools-agent topology "BobToolsAgent" bob/BOB-TOOLS)
  (tools/new-tools-agent topology "AliceBobToolsAgent" alice+bob/ALICE-BOB-TOOLS)
  (tools/new-tools-agent topology "MoebiusToolsAgent" alice-bob/TOPOS-ALICE-BOB-TOOLS)
  (tools/new-tools-agent topology "InverseMoebiusToolsAgent" bob-alice/INVERSE-MOEBIUS-TOOLS)

  ;; Create orchestrator tools agent
  (tools/new-tools-agent topology "ToposOrchestratorTools" TOPOS-MCP-TOOLS)

  ;; Main orchestrator agent
  (->
    topology
    (aor/new-agent "ToposMcpOrchestrator")

    ;; Entry node: classify and route
    (aor/node
     "orchestrate"
     nil
     (fn orchestrate-fn [agent-node requests]
       (let [model        (aor/get-agent-object agent-node "openai-model")
             trit-acc     (aor/get-agent-object agent-node "trit-accumulator")
             alice-agent  (aor/agent-client agent-node "AliceToolsAgent")
             bob-agent    (aor/agent-client agent-node "BobToolsAgent")
             ab-agent     (aor/agent-client agent-node "AliceBobToolsAgent")
             moeb-agent   (aor/agent-client agent-node "MoebiusToolsAgent")
             inv-agent    (aor/agent-client agent-node "InverseMoebiusToolsAgent")
             results      (atom [])]

         (doseq [request requests]
           (let [intent (if (string? request) request (:intent request))
                 stream-key (classify-intent intent)
                 stream-info (get streams stream-key)
                 trit (:trit stream-info)
                 
                 ;; Select appropriate tools agent
                 tools-agent (case stream-key
                               :alice     alice-agent
                               :bob       bob-agent
                               :alice+bob ab-agent
                               :alice-bob moeb-agent
                               :bob-alice inv-agent
                               ab-agent)
                 
                 ;; Get tools for this stream
                 stream-tools (case stream-key
                                :alice     alice/ALICE-PLUS-TOOLS
                                :bob       bob/BOB-TOOLS
                                :alice+bob alice+bob/ALICE-BOB-TOOLS
                                :alice-bob alice-bob/TOPOS-ALICE-BOB-TOOLS
                                :bob-alice bob-alice/INVERSE-MOEBIUS-TOOLS
                                alice+bob/ALICE-BOB-TOOLS)
                 
                 ;; Chat with model to get tool calls
                 response (lc4j/chat model
                                     (lc4j/chat-request
                                      [(UserMessage. (str intent))]
                                      {:tools stream-tools}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             ;; Track this invocation
             (swap! trit-acc update :invocations conj
                    (track-invocation stream-key (keyword (:role stream-info)) 
                                      {:intent intent :stream stream-key}))

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (swap! results conj
                   {:request intent
                    :stream stream-key
                    :trit trit
                    :tool-calls (mapv #(.name %) tool-calls)
                    :tool-results tool-results
                    :description (:description stream-info)}))
               (swap! results conj
                 {:request intent
                  :stream stream-key
                  :trit trit
                  :response (.text ai-message)
                  :description (:description stream-info)}))))

         ;; Validate GF(3) conservation
         (let [acc @trit-acc
               conservation (validate-gf3-conservation (:invocations acc))
               incidence-ops (incidence-algebra-ops)]
           (aor/result! agent-node
                        {:results @results
                         :gf3-conservation conservation
                         :incidence-algebra {:moebius-3 MOEBIUS-3
                                             :identity-verified (= 1 (moebius-invert (fn [d] (zeta d 6)) 1))}
                         :genesis-seed (format "0x%X" GENESIS-SEED)
                         :session {:start (:session-start acc)
                                   :invocations-count (count (:invocations acc))}
                         :streams-available (keys streams)})))))))

;;; ============================================================================
;;; Convenience Functions
;;; ============================================================================

(defn route-intent
  "Route natural language intent to appropriate stream"
  [intent]
  (let [stream-key (classify-intent intent)]
    {:stream stream-key
     :trit (stream-trit stream-key)
     :info (get streams stream-key)}))

(defn convolve
  "Compute alice * bob convolution at n"
  [n]
  (dirichlet-convolve (fn [d] (zeta d n)) (fn [d] (zeta d n)) n))

(defn alice-minus-bob
  "Compute alice - bob = alice * μ(bob)"
  [n]
  (moebius-invert identity n))

(defn bob-minus-alice
  "Compute bob - alice = bob * μ(alice)"
  [n]
  (moebius-invert (fn [d] (- d)) n))

(defn verify-conservation
  "Verify a sequence of operations conserves GF(3)"
  [& stream-keys]
  (let [trits (map stream-trit stream-keys)]
    {:streams stream-keys
     :trits trits
     :conserved? (gf3-conserved? trits)
     :sum (reduce gf3-add 0 trits)}))

;;; ============================================================================
;;; Entry Point
;;; ============================================================================

(defn -main
  "Run the unified Topos MCP orchestrator.
   
   From Aptos LLMs.txt:
   'Keyless Accounts: Onboard users and sign without wallets'
   'Sponsored Transactions: Pay for users' gas'
   'View functions do not modify blockchain state'"
  [& _args]
  (println "╔════════════════════════════════════════════════════════════════════╗")
  (println "║                  Unified Topos MCP Orchestrator                    ║")
  (println "║       GF(3) Conservation × Incidence Algebra × 5 Streams           ║")
  (println "╠════════════════════════════════════════════════════════════════════╣")
  (println "║  From Aptos LLMs.txt:                                              ║")
  (println "║  • 'Keyless Accounts: Onboard users and sign without wallets'      ║")
  (println "║  • 'Sponsored Transactions: Pay for users' gas'                    ║")
  (println "║  • 'View functions do not modify blockchain state'                 ║")
  (println "╚════════════════════════════════════════════════════════════════════╝")

  (println (format "\n[Genesis Seed: 0x%X]" GENESIS-SEED))
  (println (format "[μ(3) = %d (Möbius of 3)]" MOEBIUS-3))

  (println "\n=== 5 Streams with GF(3) Assignment ===")
  (doseq [[k v] streams]
    (println (format "  %-12s  trit=%+d  %s" (name k) (:trit v) (:description v))))

  (println "\n=== Incidence Algebra Operations ===")
  (println "  (alice * bob)   = convolve  → simulate transaction")
  (println "  (alice - bob)   = μ-invert  → write without read (Möbius)")
  (println "  (bob - alice)   = μ-invert  → read without write (Inverse)")

  (println "\n=== GF(3) Conservation Examples ===")
  (println "  alice + bob + alice+bob =" (verify-conservation :alice :bob :alice+bob))
  (println "  alice-bob + bob-alice + alice+bob =" 
           (verify-conservation :alice-bob :bob-alice :alice+bob))

  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToposMcpModule {:tasks 5 :threads 5})

    (let [manager (aor/agent-manager ipc (rama/get-module-name ToposMcpModule))
          orchestrator (aor/agent-client manager "ToposMcpOrchestrator")
          requests ["Check balance of 0x1 on mainnet"
                    "Simulate transfer of 1 APT from 0xalice to 0xbob"
                    "Fund my devnet account 0xtest with 5 APT"
                    "View historical state at version 12345"
                    "Emergency unstake 10 APT from pool 0xpool"
                    "Execute transfer with sponsored gas"]
          result (aor/agent-invoke orchestrator requests)]

      (println "\n=== Orchestration Results ===")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (println (format "    Stream: %s (trit=%+d)" 
                         (name (:stream r)) (:trit r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))))

      (println "\n=== GF(3) Conservation Check ===")
      (let [gc (:gf3-conservation result)]
        (println "  Conserved:" (:conserved? gc))
        (println "  Sum:" (:sum gc))
        (println "  Trits:" (:trits gc))
        (when (:balance-suggestion gc)
          (println "  Suggestion:" (:balance-suggestion gc))))

      (println "\n=== Session Summary ===")
      (println "  Genesis Seed:" (:genesis-seed result))
      (println "  Invocations:" (get-in result [:session :invocations-count]))
      (println "  Streams Available:" (str/join ", " (map name (:streams-available result)))))))

(comment
  ;; REPL examples
  
  ;; Route intent to stream
  (route-intent "check balance of 0x1")
  ;; => {:stream :bob, :trit -1, :info {...}}
  
  (route-intent "fund my devnet account")
  ;; => {:stream :alice, :trit +1, :info {...}}
  
  (route-intent "simulate transfer")
  ;; => {:stream :alice+bob, :trit 0, :info {...}}
  
  ;; Incidence algebra operations
  (convolve 6)                    ;; (ζ * ζ)(6) = number of divisor pairs
  (alice-minus-bob 6)             ;; Möbius inversion
  (bob-minus-alice 6)             ;; Inverse Möbius
  
  ;; Verify conservation
  (verify-conservation :alice :bob :alice+bob)
  ;; => {:streams [...], :trits [1 -1 0], :conserved? true, :sum 0}
  
  ;; All 5 streams
  (verify-conservation :alice :bob :alice+bob :alice-bob :bob-alice)
  ;; => sum = 1 + (-1) + 0 + (-1) + 1 = 0 ✓
  
  ;; Run orchestrator
  (-main))
