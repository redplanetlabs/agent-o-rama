(ns com.rpl.agent.topos-alice-minus-bob
  "Möbius inversion: g(n) = Σ_{d|n} μ(n/d) × f(d)
   Alice - Bob = Alice * μ(Bob) in incidence algebra
   
   ALICE-BOB is Möbius inversion: Alice * μ(Bob) where μ(3) = -1
   In GF(3): (+1) - (-1) = +1 + 1 = +2 ≡ -1 (mod 3)
   
   Genesis seed: 0x42D, derivation via Möbius: seed_alice ⊕ (μ(3) × seed_bob)
   
   Tools implementing 'write without read' (TRIT = -1 in GF(3)):
   - aptos-blind-transfer: Transfer without balance check (dangerous)
   - aptos-force-stake: Stake without validation
   - aptos-emergency-unstake: Emergency unstake
   
   Möbius function: μ(n) = (-1)^k for squarefree n with k prime factors
   μ(n) = 0 if n has squared prime factor
   
   Key values:
   μ(1) = 1, μ(2) = -1, μ(3) = -1, μ(6) = 1, μ(4) = 0
   
   Convolution product: (f * g)(n) = Σ_{d|n} f(d) × g(n/d)
   Möbius inverse: ζ * μ = ε (identity)"
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
   [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers HttpResponse$BodyHandlers]))

;;; ============================================================
;;; GF(3) Trit Tracking - Alice minus Bob
;;; ============================================================

(def ^:const TRIT-MINUS -1)  ;; Alice - Bob computed trit
(def ^:const TRIT-ZERO   0)  ;; Ergodic/balanced
(def ^:const TRIT-PLUS  +1)  ;; Write operations

(def ^:const GENESIS-SEED 0x42D)
(def ^:const GAMMA 0x9E3779B97F4A7C15)
(def ^:const MIX   0xBF58476D1CE4E5B9)

(defn trit-add
  "GF(3) addition: (a + b) mod 3 mapped to {-1, 0, +1}"
  [a b]
  (let [sum (+ a b)]
    (cond
      (> sum 1)  (- sum 3)
      (< sum -1) (+ sum 3)
      :else sum)))

(defn trit-mul
  "GF(3) multiplication"
  [a b]
  (let [prod (* a b)]
    (cond
      (> prod 1)  (- prod 3)
      (< prod -1) (+ prod 3)
      :else prod)))

(defn alice-minus-bob
  "ALICE - BOB in GF(3): Alice * μ(Bob) where μ(3) = -1
   (+1) - (-1) = +1 + 1 = +2 ≡ -1 (mod 3)"
  [alice-trit bob-trit]
  (let [mu-3 -1]
    (trit-add alice-trit (trit-mul mu-3 bob-trit))))

;;; ============================================================
;;; Möbius Function
;;; ============================================================

(defn prime-factors
  "Return prime factors with multiplicities as map"
  [n]
  (loop [n n d 2 factors {}]
    (cond
      (< n 2) factors
      (zero? (mod n d)) (recur (quot n d) d (update factors d (fnil inc 0)))
      (> (* d d) n) (if (> n 1) (assoc factors n 1) factors)
      :else (recur n (inc d) factors))))

(defn moebius
  "Möbius function μ(n):
   μ(1) = 1
   μ(n) = (-1)^k if n = p₁×p₂×...×pₖ (k distinct primes)
   μ(n) = 0 if n has squared prime factor"
  [n]
  (cond
    (= n 1) 1
    :else
    (let [factors (prime-factors n)]
      (if (some #(> % 1) (vals factors))
        0
        (if (even? (count factors)) 1 -1)))))

(defn divisors
  "All divisors of n"
  [n]
  (for [d (range 1 (inc n)) :when (zero? (mod n d))] d))

(defn moebius-invert
  "Apply Möbius inversion: g(n) = Σ_{d|n} μ(n/d) × f(d)"
  [f n]
  (reduce + (for [d (divisors n)]
              (* (moebius (quot n d)) (f d)))))

(defn zeta-sum
  "Zeta function (summatory): f(n) = Σ_{d|n} g(d)"
  [g n]
  (reduce + (for [d (divisors n)] (g d))))

(defn incidence-convolve
  "Convolution product in incidence algebra: (f * g)(n) = Σ_{d|n} f(d) × g(n/d)"
  [f g n]
  (reduce + (for [d (divisors n)]
              (* (f d) (g (quot n d))))))

;;; ============================================================
;;; Derivation Chain (Unworld)
;;; ============================================================

(defn derive-seed
  "Derive next seed: seed_{n+1} = (seed_n ⊕ (trit_n × γ)) × MIX mod 2⁶⁴"
  [seed trit]
  (let [gamma-term (bit-and (* (long trit) GAMMA) 0xFFFFFFFFFFFFFFFF)
        xored (bit-xor seed gamma-term)]
    (bit-and (* xored MIX) 0xFFFFFFFFFFFFFFFF)))

(defn moebius-derive-seed
  "Derive seed via Möbius: seed_alice ⊕ (μ(3) × seed_bob)"
  [seed-alice seed-bob]
  (let [mu-3 (moebius 3)]
    (bit-xor seed-alice (bit-and (* mu-3 seed-bob) 0xFFFFFFFFFFFFFFFF))))

;;; ============================================================
;;; HTTP Client (minimal)
;;; ============================================================

(def ^:private http-client
  (delay (HttpClient/newHttpClient)))

(defn- aptos-request
  "Make HTTP request to Aptos node"
  [method url body]
  (let [builder (-> (HttpRequest/newBuilder)
                    (.uri (URI/create url))
                    (.header "Content-Type" "application/json"))
        request (case method
                  :get (.GET builder)
                  :post (.POST builder (HttpRequest$BodyPublishers/ofString 
                                        (if body (json/write-str body) ""))))]
    (try
      (let [response (.send @http-client (.build request) (HttpResponse$BodyHandlers/ofString))
            status (.statusCode response)
            body-str (.body response)]
        {:status status
         :body (when (seq body-str) (json/read-str body-str))})
      (catch Exception e
        {:error (.getMessage e)}))))

;;; ============================================================
;;; Network Configuration
;;; ============================================================

(def NETWORKS
  {:mainnet "https://fullnode.mainnet.aptoslabs.com/v1"
   :testnet "https://fullnode.testnet.aptoslabs.com/v1"
   :devnet  "https://fullnode.devnet.aptoslabs.com/v1"})

(defn get-node-url [network]
  (get NETWORKS (keyword network) (:mainnet NETWORKS)))

;;; ============================================================
;;; Trit Tracking Record
;;; ============================================================

(defrecord MoebiusTrackedResult
  [result trit operation moebius-value derivation-seed timestamp])

(defn track-moebius
  "Wrap result with Möbius trit tracking"
  [result trit operation & {:keys [moebius-n seed] :or {moebius-n 3 seed GENESIS-SEED}}]
  (->MoebiusTrackedResult 
   result 
   trit 
   operation 
   (moebius moebius-n)
   (derive-seed seed trit)
   (System/currentTimeMillis)))

;;; ============================================================
;;; Tool: aptos-blind-transfer (TRIT: -1, write without read)
;;; ============================================================

(defn aptos-blind-transfer-tool
  "Transfer APT WITHOUT balance check - DANGEROUS.
   
   This is 'write without read' in the Möbius sense:
   Alice - Bob means we proceed without Bob's validation.
   
   μ(3) = -1, so TRIT = -1 for this operation.
   
   WARNING: May fail if insufficient balance."
  [args]
  (let [sender (get args "sender")
        receiver (get args "receiver")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "devnet")
        force? (get args "force" false)
        node-url (get-node-url network)]
    
    (if-not force?
      (track-moebius
       {:success false
        :error "Blind transfer requires force=true acknowledgment"
        :warning "This operation skips balance validation. Set force=true to proceed."
        :moebius-semantics "μ(3) = -1: Write without read"}
       TRIT-MINUS
       :aptos-blind-transfer)
      
      (let [account-url (str node-url "/accounts/" sender)
            {:keys [body error]} (aptos-request :get account-url nil)]
        (if error
          (track-moebius
           {:success false :error error}
           TRIT-MINUS
           :aptos-blind-transfer)
          
          (let [sequence-number (get body "sequence_number" "0")
                payload {:type "entry_function_payload"
                         :function "0x1::aptos_account::transfer"
                         :type_arguments []
                         :arguments [receiver (str amount-octas)]}]
            (track-moebius
             {:success true
              :phase :blind-prepared
              :sender sender
              :receiver receiver
              :amount-apt amount-apt
              :amount-octas amount-octas
              :network network
              :sequence-number sequence-number
              :payload payload
              :warning "Transaction prepared WITHOUT balance check"
              :alice-minus-bob (alice-minus-bob TRIT-PLUS TRIT-PLUS)
              :note "Sign with Aptos SDK to execute. No guarantee of success."}
             TRIT-MINUS
             :aptos-blind-transfer
             :moebius-n 3
             :seed (moebius-derive-seed GENESIS-SEED (hash sender)))))))))

(def APTOS-BLIND-TRANSFER-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-blind-transfer"
    (lj/object
     {:description "Transfer APT without balance check (DANGEROUS)"
      :required ["sender" "receiver" "amount"]}
     {"sender" (lj/string "Sender address (0x...)")
      "receiver" (lj/string "Receiver address (0x...)")
      "amount" (lj/number "Amount in APT")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])
      "force" (lj/boolean "Acknowledge danger (required: true)")})
    "Blind transfer - write without read. μ(3) = -1. DANGEROUS.")
   aptos-blind-transfer-tool))

;;; ============================================================
;;; Tool: aptos-force-stake (TRIT: -1, write without read)
;;; ============================================================

(defn aptos-force-stake-tool
  "Force stake without validation.
   
   Möbius semantics: Skip the 'read' of pool validity.
   μ(3) = -1: Operate blindly.
   
   WARNING: Pool may be invalid, locked, or full."
  [args]
  (let [delegator (get args "delegator")
        pool-address (get args "pool_address")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "mainnet")
        force? (get args "force" false)]
    
    (if-not force?
      (track-moebius
       {:success false
        :error "Force stake requires force=true acknowledgment"
        :warning "This skips pool validation. Set force=true to proceed."
        :moebius-semantics "μ(3) = -1: Stake without read"}
       TRIT-MINUS
       :aptos-force-stake)
      
      (let [payload {:type "entry_function_payload"
                     :function "0x1::delegation_pool::add_stake"
                     :type_arguments []
                     :arguments [pool-address (str amount-octas)]}]
        (track-moebius
         {:success true
          :phase :force-stake-prepared
          :delegator delegator
          :pool-address pool-address
          :amount-apt amount-apt
          :amount-octas amount-octas
          :network network
          :payload payload
          :warning "Stake prepared WITHOUT pool validation"
          :alice-minus-bob (alice-minus-bob TRIT-PLUS TRIT-MINUS)
          :moebius-3 (moebius 3)
          :note "Pool lockup, commission, active status NOT verified."}
         TRIT-MINUS
         :aptos-force-stake
         :moebius-n 3)))))

(def APTOS-FORCE-STAKE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-force-stake"
    (lj/object
     {:description "Force stake without pool validation (DANGEROUS)"
      :required ["delegator" "pool_address" "amount"]}
     {"delegator" (lj/string "Delegator address (0x...)")
      "pool_address" (lj/string "Delegation pool address (0x...)")
      "amount" (lj/number "Amount in APT")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])
      "force" (lj/boolean "Acknowledge danger (required: true)")})
    "Force stake - write without read. μ(3) = -1. DANGEROUS.")
   aptos-force-stake-tool))

;;; ============================================================
;;; Tool: aptos-emergency-unstake (TRIT: -1, write without read)
;;; ============================================================

(defn aptos-emergency-unstake-tool
  "Emergency unstake - bypass lockup checks.
   
   Möbius inversion: g = μ * f
   Normal: Check lockup → validate → unstake
   Emergency: Unstake immediately, accept penalties
   
   μ(3) = -1: The 'minus' operation."
  [args]
  (let [delegator (get args "delegator")
        pool-address (get args "pool_address")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "mainnet")
        emergency? (get args "emergency" false)]
    
    (if-not emergency?
      (track-moebius
       {:success false
        :error "Emergency unstake requires emergency=true acknowledgment"
        :warning "This bypasses lockup. May incur penalties. Set emergency=true."
        :moebius-semantics "g(n) = Σ_{d|n} μ(n/d) × f(d) - inverts lockup sum"}
       TRIT-MINUS
       :aptos-emergency-unstake)
      
      (let [payload {:type "entry_function_payload"
                     :function "0x1::delegation_pool::unlock"
                     :type_arguments []
                     :arguments [pool-address (str amount-octas)]}]
        (track-moebius
         {:success true
          :phase :emergency-unstake-prepared
          :delegator delegator
          :pool-address pool-address
          :amount-apt amount-apt
          :amount-octas amount-octas
          :network network
          :payload payload
          :warning "EMERGENCY: Unstake prepared WITHOUT lockup check"
          :penalty-risk "May forfeit pending rewards or incur slashing"
          :alice-minus-bob (alice-minus-bob TRIT-MINUS TRIT-PLUS)
          :moebius-inversion "ζ * μ = ε (identity via inversion)"
          :note "Lockup period NOT verified. Penalties may apply."}
         TRIT-MINUS
         :aptos-emergency-unstake
         :moebius-n 3)))))

(def APTOS-EMERGENCY-UNSTAKE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-emergency-unstake"
    (lj/object
     {:description "Emergency unstake bypassing lockup (DANGEROUS)"
      :required ["delegator" "pool_address" "amount"]}
     {"delegator" (lj/string "Delegator address (0x...)")
      "pool_address" (lj/string "Delegation pool address (0x...)")
      "amount" (lj/number "Amount in APT")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])
      "emergency" (lj/boolean "Acknowledge emergency (required: true)")})
    "Emergency unstake - bypass lockup. μ(3) = -1. DANGEROUS.")
   aptos-emergency-unstake-tool))

;;; ============================================================
;;; All Topos Alice-Bob Tools
;;; ============================================================

(def TOPOS-ALICE-BOB-TOOLS
  [APTOS-BLIND-TRANSFER-TOOL
   APTOS-FORCE-STAKE-TOOL
   APTOS-EMERGENCY-UNSTAKE-TOOL])

;;; ============================================================
;;; Möbius Conservation Validator
;;; ============================================================

(defn validate-moebius-conservation
  "Validate Möbius properties across operations.
   
   For Alice - Bob:
   Alice (+1) - Bob (-1) = +1 * μ(3) * (-1) = +1 * (-1) * (-1) = +1
   
   In GF(3): (+1) - (-1) = +1 + 1 = +2 ≡ -1 (mod 3)"
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        moebius-vals (mapv :moebius-value tracked-results)
        sum (reduce trit-add 0 trits)
        alice-trit TRIT-PLUS
        bob-trit TRIT-MINUS
        computed-difference (alice-minus-bob alice-trit bob-trit)]
    {:conserved? (= sum computed-difference)
     :trits trits
     :moebius-values moebius-vals
     :sum sum
     :alice-minus-bob computed-difference
     :operations (mapv :operation tracked-results)
     :gf3-interpretation "(+1) - (-1) = +2 ≡ -1 (mod 3)"}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule ToposAliceBobModule
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
            :seed GENESIS-SEED})))

  (tools/new-tools-agent
   topology
   "ToposAliceBobToolsAgent"
   TOPOS-ALICE-BOB-TOOLS)

  (->
    topology
    (aor/new-agent "ToposAliceBobAgent")

    (aor/node
     "execute"
     nil
     (fn execute-fn [agent-node requests]
       (let [model         (aor/get-agent-object agent-node "openai-model")
             tools-agent   (aor/agent-client agent-node "ToposAliceBobToolsAgent")
             moebius-acc   (aor/get-agent-object agent-node "moebius-accumulator")
             results       (atom [])]

         (doseq [request requests]
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools TOPOS-ALICE-BOB-TOOLS}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (doseq [tr tool-results]
                   (when (instance? MoebiusTrackedResult tr)
                     (swap! moebius-acc update :results conj tr)
                     (swap! moebius-acc assoc :seed (:derivation-seed tr))))
                 (swap! results conj
                   {:request request
                    :tool-calls (mapv #(.name %) tool-calls)
                    :tool-results tool-results}))
               (swap! results conj
                 {:request request
                  :response (.text ai-message)}))))

         (let [acc @moebius-acc
               conservation (validate-moebius-conservation (:results acc))]
           (aor/result! agent-node
                        {:results @results
                         :moebius-tracking {:operations-count (count (:results acc))
                                            :current-seed (:seed acc)
                                            :conservation conservation
                                            :mu-3 (moebius 3)}})))))))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn blind-transfer
  "Blind transfer without balance check"
  [sender receiver amount & {:keys [network force?] 
                              :or {network "devnet" force? false}}]
  (aptos-blind-transfer-tool {"sender" sender 
                              "receiver" receiver 
                              "amount" amount
                              "network" network
                              "force" force?}))

(defn force-stake
  "Force stake without pool validation"
  [delegator pool-address amount & {:keys [network force?] 
                                     :or {network "mainnet" force? false}}]
  (aptos-force-stake-tool {"delegator" delegator
                           "pool_address" pool-address
                           "amount" amount
                           "network" network
                           "force" force?}))

(defn emergency-unstake
  "Emergency unstake bypassing lockup"
  [delegator pool-address amount & {:keys [network emergency?] 
                                     :or {network "mainnet" emergency? false}}]
  (aptos-emergency-unstake-tool {"delegator" delegator
                                 "pool_address" pool-address
                                 "amount" amount
                                 "network" network
                                 "emergency" emergency?}))

;;; ============================================================
;;; Möbius Utilities
;;; ============================================================

(defn moebius-table
  "Print Möbius function for n ≤ limit"
  [limit]
  (println "n\tμ(n)\tFactors")
  (println "---\t----\t-------")
  (doseq [n (range 1 (inc limit))]
    (println (format "%d\t%d\t%s" 
                     n 
                     (moebius n) 
                     (str (prime-factors n))))))

(defn verify-inversion
  "Verify Möbius inversion: applying ζ then μ recovers original"
  [f n]
  (let [summed (fn [m] (zeta-sum f m))
        recovered (moebius-invert summed n)]
    {:original (f n)
     :summed (summed n)
     :recovered recovered
     :verified? (= (f n) recovered)}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (println "=== Topos Alice - Bob (Möbius Inversion) ===")
  (println)
  (println "μ(n) = (-1)^k for squarefree n with k prime factors")
  (println "μ(3) =" (moebius 3) "(prime, so -1)")
  (println)
  (println "Alice - Bob = Alice * μ(Bob)")
  (println "In GF(3): (+1) - (-1) = +1 + 1 = +2 ≡ -1 (mod 3)")
  (println)
  (println "Genesis seed: 0x42D")
  (println "Möbius derivation: seed_alice ⊕ (μ(3) × seed_bob)")
  (println)
  
  (println "=== Möbius Table (1-12) ===")
  (moebius-table 12)
  (println)
  
  (println "=== Tool Semantics ===")
  (println "All tools have TRIT = -1 ('write without read')")
  (println "- aptos-blind-transfer: Transfer without balance check")
  (println "- aptos-force-stake: Stake without pool validation")
  (println "- aptos-emergency-unstake: Unstake bypassing lockup")
  (println)
  
  (println "=== Inversion Verification ===")
  (let [f (fn [d] d)
        result (verify-inversion f 6)]
    (println "For f(d) = d, n = 6:")
    (println "  Original f(6) =" (:original result))
    (println "  Summed (ζ * f)(6) =" (:summed result))
    (println "  Recovered (μ * ζ * f)(6) =" (:recovered result))
    (println "  Verified:" (:verified? result))))

(comment
  ;; REPL examples
  
  ;; Möbius function values
  (moebius 1)   ;; => 1
  (moebius 2)   ;; => -1 (prime)
  (moebius 3)   ;; => -1 (prime) ← KEY VALUE
  (moebius 4)   ;; => 0 (has 2²)
  (moebius 6)   ;; => 1 (2 primes: 2×3)
  (moebius 30)  ;; => -1 (3 primes: 2×3×5)
  
  ;; Alice - Bob in GF(3)
  (alice-minus-bob 1 -1)   ;; => -1 (computed)
  (alice-minus-bob 1 1)    ;; => 0
  (alice-minus-bob -1 1)   ;; => 1
  
  ;; Blind operations (require force/emergency flags)
  (blind-transfer "0xalice" "0xbob" 1.0 :force? true)
  (force-stake "0xdelegator" "0xpool" 100.0 :force? true)
  (emergency-unstake "0xdelegator" "0xpool" 50.0 :emergency? true)
  
  ;; Möbius inversion
  (let [f (fn [d] 1)]  ;; constant function
    (moebius-invert f 6))  ;; => 0 for n > 1
  
  ;; Derivation chain
  (derive-seed GENESIS-SEED TRIT-MINUS)
  (moebius-derive-seed GENESIS-SEED 0xBEEF)
  
  ;; Run info
  (-main))
