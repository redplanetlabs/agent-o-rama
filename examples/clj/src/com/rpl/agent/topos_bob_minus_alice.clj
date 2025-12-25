(ns com.rpl.agent.topos-bob-minus-alice
  "Inverse Möbius: ζ⁻¹ = μ (zeta inverse equals Möbius function)
   Bob - Alice = Bob * μ(Alice) in incidence algebra
   
   BOB-ALICE is inverse Möbius: Bob * μ(Alice)
   In GF(3): (-1) - (+1) = -1 - 1 = -2 ≡ +1 (mod 3)
   
   Genesis seed: 0x42D, derivation via inverse Möbius:
   seed_bob ⊕ (μ(3) × seed_alice)
   
   Tools (read without write, TRIT = +1 in GF(3)):
   - aptos-cached-balance: Read from cache only
   - aptos-historical-view: View historical state
   - aptos-snapshot-resources: Snapshot resources without mutation
   
   Inverse Möbius flips sign: μ(3) = -1 (3 is prime)
   Bob - Alice ≡ Bob + (-Alice) ≡ Bob * μ(Alice) in incidence algebra"
  (:require
   [clojure.data.json :as json]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]
   [java.net URI]
   [java.net.http HttpClient HttpRequest HttpResponse$BodyHandlers]))

;;; ============================================================
;;; Möbius Function and Inverse Convolution
;;; ============================================================

(def ^:const GENESIS-SEED 0x42D)
(def ^:const GAMMA 0x9E3779B97F4A7C15)
(def ^:const MIX 0xBF58476D1CE4E5B9)

(defn prime-factors
  "Return prime factors of n with multiplicity"
  [n]
  (loop [n n
         d 2
         factors []]
    (cond
      (< n 2) factors
      (zero? (mod n d)) (recur (quot n d) d (conj factors d))
      (> (* d d) n) (if (> n 1) (conj factors n) factors)
      :else (recur n (inc d) factors))))

(defn squarefree?
  "Check if n is squarefree (no repeated prime factors)"
  [n]
  (let [pfs (prime-factors n)]
    (= (count pfs) (count (set pfs)))))

(defn moebius
  "Classical Möbius function μ(n):
   μ(1) = 1
   μ(n) = (-1)^k if n is product of k distinct primes
   μ(n) = 0 if n has squared prime factor
   
   Critical value: μ(3) = -1 (3 is prime)"
  [n]
  (cond
    (= n 1) 1
    (not (squarefree? n)) 0
    :else (let [k (count (prime-factors n))]
            (if (even? k) 1 -1))))

(defn moebius-sign
  "Track sign through Möbius transformation.
   Returns {:value μ(n) :sign (+/-) :n n :factors [...]}"
  [n]
  (let [mu (moebius n)
        factors (prime-factors n)]
    {:value mu
     :sign (case mu 1 :plus -1 :minus 0 :zero)
     :n n
     :factors factors
     :squarefree? (squarefree? n)}))

;;; ============================================================
;;; GF(3) Trit Tracking with Inverse Möbius
;;; ============================================================

(def ^:const TRIT-MINUS -1)
(def ^:const TRIT-ZERO   0)
(def ^:const TRIT-PLUS  +1)

(defn trit-add
  "GF(3) addition: (a + b) mod 3 mapped to {-1, 0, +1}"
  [a b]
  (let [sum (+ a b)]
    (cond
      (> sum 1)  (- sum 3)
      (< sum -1) (+ sum 3)
      :else sum)))

(defn trit-multiply
  "GF(3) multiplication"
  [a b]
  (let [prod (* a b)]
    (cond
      (> prod 1)  (- prod 3)
      (< prod -1) (+ prod 3)
      :else prod)))

(defn bob-minus-alice
  "Compute BOB - ALICE in GF(3) via inverse Möbius.
   
   Bob - Alice = Bob * μ(Alice)
   In GF(3): (-1) - (+1) = -2 ≡ +1 (mod 3)
   
   Since μ(3) = -1, Bob * μ(Alice) inverts the sign."
  [bob-trit alice-trit]
  (let [mu-3 (moebius 3)
        alice-inverted (trit-multiply alice-trit mu-3)]
    (trit-add bob-trit alice-inverted)))

(defn inverse-moebius-derivation
  "Derive seed via inverse Möbius: seed_bob ⊕ (μ(3) × seed_alice)
   
   μ(3) = -1, so this XORs bob with negated alice contribution"
  [seed-bob seed-alice]
  (let [mu-3 (moebius 3)
        alice-contribution (unchecked-multiply seed-alice (long mu-3))
        derived (bit-xor seed-bob alice-contribution)]
    (bit-and derived 0x7FFFFFFFFFFFFFFF)))

(defn derive-seed-chain
  "Chain derivation following unworld pattern:
   seed_{n+1} = (seed_n ⊕ (trit_n × γ)) × MIX mod 2⁶⁴"
  [seed trit]
  (let [trit-contribution (unchecked-multiply (long trit) GAMMA)
        xored (bit-xor seed trit-contribution)
        mixed (unchecked-multiply xored MIX)]
    (bit-and mixed 0x7FFFFFFFFFFFFFFF)))

(defn compute-bob-alice-trit
  "Compute the GF(3) trit from Bob - Alice.
   
   Bob (perception) = -1
   Alice (action) = +1
   Bob - Alice = (-1) - (+1) = -2 ≡ +1 (mod 3)
   
   Returns the computed trit value."
  []
  (let [bob-trit TRIT-MINUS
        alice-trit TRIT-PLUS
        result (bob-minus-alice bob-trit alice-trit)]
    {:bob bob-trit
     :alice alice-trit
     :bob-minus-alice result
     :interpretation (case result
                       +1 "PLUS: Read without write"
                       0 "ZERO: Ergodic balance"
                       -1 "MINUS: Write without read")}))

;;; ============================================================
;;; Inverse Convolution in Incidence Algebra
;;; ============================================================

(defn zeta
  "Zeta function on poset: ζ(x,y) = 1 if x ≤ y, else 0
   For divisor poset, ζ(d,n) = 1 if d|n"
  [x y]
  (if (zero? (mod y x)) 1 0))

(defn inverse-convolve
  "Inverse convolution: (f * μ)(n) = Σ_{d|n} f(d) × μ(n/d)
   This inverts summation over divisors."
  [f n]
  (let [divisors (filter #(zero? (mod n %)) (range 1 (inc n)))]
    (reduce + (for [d divisors]
                (* (f d) (moebius (quot n d)))))))

(defn bob-alice-convolution
  "Convolve Bob with inverse of Alice via Möbius.
   Bob * μ(Alice) = Bob - Alice in incidence algebra sense."
  [bob-value alice-value]
  (let [mu-alice (moebius 3)]
    (* bob-value mu-alice alice-value)))

;;; ============================================================
;;; Trit Tracked Result with Möbius Metadata
;;; ============================================================

(defrecord InverseMoebiusResult
  [result trit operation timestamp moebius-meta])

(defn track-inverse-moebius
  "Wrap result with inverse Möbius tracking"
  [result trit operation]
  (->InverseMoebiusResult
   result
   trit
   operation
   (System/currentTimeMillis)
   {:mu-3 (moebius 3)
    :bob-minus-alice (compute-bob-alice-trit)
    :genesis-seed GENESIS-SEED}))

;;; ============================================================
;;; Cache Infrastructure (Read-Only Operations)
;;; ============================================================

(def ^:private balance-cache (atom {}))
(def ^:private historical-state (atom {}))
(def ^:private resource-snapshots (atom {}))

(def NETWORKS
  {:mainnet "https://fullnode.mainnet.aptoslabs.com/v1"
   :testnet "https://fullnode.testnet.aptoslabs.com/v1"
   :devnet  "https://fullnode.devnet.aptoslabs.com/v1"})

(defn get-node-url [network]
  (get NETWORKS (keyword network) (:mainnet NETWORKS)))

;;; ============================================================
;;; Tool: aptos-cached-balance (TRIT: +1, read from cache only)
;;; ============================================================

(defn aptos-cached-balance-tool
  "Read balance from cache only - no network mutation.
   
   Inverse Möbius interpretation:
   Bob (observe) - Alice (modify) = pure observation (+1)
   
   TRIT: +1 (read without write - Bob minus Alice)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        cache-key [address network]
        cached (get @balance-cache cache-key)]
    (track-inverse-moebius
     (if cached
       {:success true
        :source :cache
        :address address
        :network network
        :balance-octas (:balance-octas cached)
        :balance-apt (:balance-apt cached)
        :cached-at (:timestamp cached)
        :moebius-note "Read from cache: Bob - Alice = +1"}
       {:success false
        :source :cache
        :address address
        :network network
        :error "No cached balance found"
        :moebius-note "Cache miss - pure read operation"})
     TRIT-PLUS
     :aptos-cached-balance)))

(def APTOS-CACHED-BALANCE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-cached-balance"
    (lj/object
     {:description "Read Aptos balance from cache only (no network call)"
      :required ["address"]}
     {"address" (lj/string "Aptos account address (0x...)")
      "network" (lj/enum "Network context" ["mainnet" "testnet" "devnet"])})
    "Read-only balance lookup from cache. TRIT: +1 (Bob - Alice)")
   aptos-cached-balance-tool))

;;; ============================================================
;;; Tool: aptos-historical-view (TRIT: +1, view historical state)
;;; ============================================================

(defn aptos-historical-view-tool
  "View historical state at specific version.
   Pure observation of past - no mutation possible.
   
   Inverse Möbius: observing past = Bob * μ(Alice)
   μ(3) = -1 inverts action to pure perception
   
   TRIT: +1 (read without write)"
  [args]
  (let [function (get args "function")
        version (get args "version")
        network (get args "network" "mainnet")
        type-args (get args "type_arguments" [])
        fn-args (get args "arguments" [])
        history-key [function version network]]
    (track-inverse-moebius
     (if-let [historical (get @historical-state history-key)]
       {:success true
        :source :historical
        :function function
        :version version
        :network network
        :result (:result historical)
        :observed-at (:timestamp historical)
        :moebius-note "Historical view: past is immutable, Bob - Alice = +1"}
       {:success true
        :source :historical
        :function function
        :version version
        :network network
        :result nil
        :moebius-note "No historical record - pure observation attempt"})
     TRIT-PLUS
     :aptos-historical-view)))

(def APTOS-HISTORICAL-VIEW-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-historical-view"
    (lj/object
     {:description "View historical state at specific ledger version"
      :required ["function" "version"]}
     {"function" (lj/string "Fully qualified Move function (e.g., 0x1::coin::balance)")
      "version" (lj/number "Ledger version to query")
      "type_arguments" (lj/array "Type arguments" (lj/string "Type"))
      "arguments" (lj/array "Function arguments" (lj/string "Argument"))
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "View state at historical ledger version. TRIT: +1 (Bob - Alice)")
   aptos-historical-view-tool))

;;; ============================================================
;;; Tool: aptos-snapshot-resources (TRIT: +1, snapshot without mutation)
;;; ============================================================

(defn aptos-snapshot-resources-tool
  "Snapshot account resources without mutation.
   Creates read-only view of current state.
   
   Inverse Möbius semantics:
   Snapshot = observe(state) = Bob
   No mutation = -Alice (negated action)
   Result: Bob + (-Alice) = Bob * μ(Alice) = +1
   
   TRIT: +1 (read without write)"
  [args]
  (let [address (get args "address")
        resource-type (get args "resource_type")
        network (get args "network" "mainnet")
        snapshot-key [address resource-type network (System/currentTimeMillis)]]
    (track-inverse-moebius
     (if-let [existing (get @resource-snapshots [address resource-type network])]
       {:success true
        :source :snapshot
        :address address
        :resource-type resource-type
        :network network
        :snapshot existing
        :snapshot-version (:version existing)
        :moebius-note "Resource snapshot: immutable view, Bob * μ(Alice)"}
       {:success true
        :source :snapshot
        :address address
        :resource-type resource-type
        :network network
        :snapshot nil
        :moebius-note "No snapshot exists - would create read-only view"})
     TRIT-PLUS
     :aptos-snapshot-resources)))

(def APTOS-SNAPSHOT-RESOURCES-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-snapshot-resources"
    (lj/object
     {:description "Snapshot account resources (read-only)"
      :required ["address"]}
     {"address" (lj/string "Account address (0x...)")
      "resource_type" (lj/string "Resource type to snapshot (optional)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Create immutable snapshot of resources. TRIT: +1 (Bob - Alice)")
   aptos-snapshot-resources-tool))

;;; ============================================================
;;; All Inverse Möbius Tools
;;; ============================================================

(def INVERSE-MOEBIUS-TOOLS
  [APTOS-CACHED-BALANCE-TOOL
   APTOS-HISTORICAL-VIEW-TOOL
   APTOS-SNAPSHOT-RESOURCES-TOOL])

;;; ============================================================
;;; GF(3) Conservation with Inverse Möbius Validation
;;; ============================================================

(defn validate-inverse-moebius-conservation
  "Validate GF(3) conservation with inverse Möbius semantics.
   
   All tools are +1 (read without write), so:
   n tools × (+1) = n (mod 3)
   
   For conservation, need n ≡ 0 (mod 3)"
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce trit-add 0 trits)
        bob-alice-computed (compute-bob-alice-trit)]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :operations (mapv :operation tracked-results)
     :count (count tracked-results)
     :needs-for-balance (case (mod (count tracked-results) 3)
                          0 0
                          1 2
                          2 1)
     :bob-minus-alice bob-alice-computed
     :moebius-3 (moebius 3)}))

(defn derive-genesis-bob-alice
  "Derive genesis seeds for Bob-Alice inverse Möbius.
   
   seed_bob ⊕ (μ(3) × seed_alice) = derived seed
   
   μ(3) = -1, so XOR with negated alice seed"
  []
  (let [seed-bob GENESIS-SEED
        seed-alice (bit-xor GENESIS-SEED GAMMA)
        derived (inverse-moebius-derivation seed-bob seed-alice)]
    {:genesis GENESIS-SEED
     :seed-bob seed-bob
     :seed-alice seed-alice
     :mu-3 (moebius 3)
     :derived-seed derived
     :derived-hex (format "0x%X" derived)}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule ToposBobMinusAliceModule
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
   "moebius-accumulator"
   (fn [_setup]
     (atom {:results []
            :genesis (derive-genesis-bob-alice)})))

  (tools/new-tools-agent
   topology
   "InverseMoebiusToolsAgent"
   INVERSE-MOEBIUS-TOOLS)

  (->
    topology
    (aor/new-agent "ToposBobMinusAliceAgent")

    (aor/node
     "execute"
     nil
     (fn execute-fn [agent-node requests]
       (let [model          (aor/get-agent-object agent-node "openai-model")
             tools-agent    (aor/agent-client agent-node "InverseMoebiusToolsAgent")
             moebius-acc    (aor/get-agent-object agent-node "moebius-accumulator")
             results        (atom [])]

         (doseq [request requests]
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools INVERSE-MOEBIUS-TOOLS}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (doseq [tr tool-results]
                   (when (instance? InverseMoebiusResult tr)
                     (swap! moebius-acc update :results conj tr)))
                 (swap! results conj
                   {:request request
                    :tool-calls (mapv #(.name %) tool-calls)
                    :tool-results tool-results}))
               (swap! results conj
                 {:request request
                  :response (.text ai-message)}))))

         (let [acc @moebius-acc
               conservation (validate-inverse-moebius-conservation (:results acc))]
           (aor/result! agent-node
                        {:results @results
                         :inverse-moebius {:genesis (:genesis acc)
                                           :operations-count (count (:results acc))
                                           :conservation conservation}})))))))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn cached-balance
  "Read balance from cache (TRIT: +1)"
  [address & {:keys [network] :or {network "mainnet"}}]
  (aptos-cached-balance-tool {"address" address "network" network}))

(defn historical-view
  "View historical state (TRIT: +1)"
  [function version & {:keys [network type-args args]
                       :or {network "mainnet" type-args [] args []}}]
  (aptos-historical-view-tool {"function" function
                               "version" version
                               "type_arguments" type-args
                               "arguments" args
                               "network" network}))

(defn snapshot-resources
  "Snapshot resources without mutation (TRIT: +1)"
  [address & {:keys [resource-type network]
              :or {resource-type nil network "mainnet"}}]
  (aptos-snapshot-resources-tool {"address" address
                                  "resource_type" resource-type
                                  "network" network}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (println "=== Inverse Möbius: Bob - Alice ===")
  (println)
  (println "μ(3) =" (moebius 3) "  ; 3 is prime")
  (println)
  (println "Bob - Alice = Bob * μ(Alice)")
  (println "In GF(3): (-1) - (+1) = -2 ≡ +1 (mod 3)")
  (println)
  (let [derivation (derive-genesis-bob-alice)]
    (println "Genesis derivation:")
    (println "  Genesis seed:" (format "0x%X" (:genesis derivation)))
    (println "  Seed Bob:" (format "0x%X" (:seed-bob derivation)))
    (println "  Seed Alice:" (format "0x%X" (:seed-alice derivation)))
    (println "  μ(3):" (:mu-3 derivation))
    (println "  Derived (Bob ⊕ μ×Alice):" (:derived-hex derivation)))
  (println)
  (println "All tools have TRIT = +1 (read without write)")
  (println "ζ⁻¹ = μ  (zeta inverse equals Möbius function)")
  
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToposBobMinusAliceModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ToposBobMinusAliceModule))
          agent (aor/agent-client manager "ToposBobMinusAliceAgent")
          requests ["Check cached balance for 0x1"
                    "View historical state of 0x1::coin::balance at version 100"
                    "Snapshot resources for 0x1"]
          result (aor/agent-invoke agent requests)]
      
      (println "\n=== Agent Results ===")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))))
      
      (println "\n=== Inverse Möbius Conservation ===")
      (let [im (:inverse-moebius result)]
        (println "Genesis:" (:genesis im))
        (println "Operations:" (:operations-count im))
        (println "Conservation:" (:conservation im))))))

(comment
  ;; REPL examples
  
  ;; Compute μ(n) for small n
  (map moebius (range 1 13))
  ;; => (1 -1 -1 0 -1 1 -1 0 0 1 -1 0)
  
  ;; μ(3) = -1 (critical for GF(3))
  (moebius 3)
  ;; => -1
  
  ;; Bob - Alice in GF(3)
  (compute-bob-alice-trit)
  ;; => {:bob -1, :alice +1, :bob-minus-alice +1, ...}
  
  ;; Derive genesis seeds
  (derive-genesis-bob-alice)
  
  ;; Read cached balance (TRIT: +1)
  (cached-balance "0x1")
  
  ;; View historical state (TRIT: +1)
  (historical-view "0x1::coin::balance" 12345)
  
  ;; Snapshot resources (TRIT: +1)
  (snapshot-resources "0x1")
  
  ;; Inverse convolution example
  (inverse-convolve identity 12)
  ;; Σ_{d|12} d × μ(12/d) = φ(12) = 4
  
  ;; Run the agent
  (-main))
