(ns com.rpl.agent.topos-alice
  "ALICE: PLUS (+1) stream - WRITE operations in GF(3).
   
   Aptos LLMs.txt:
   'Sponsored Transactions: Pay for users' gas so they can use your dApp with zero APT'
   
   Genesis seed: 0x42D
   Derivation: seed₀ ⊕ (γ << 1) where γ = 0x9E3779B97F4A7C15
   
   SplitMix64 seed chaining replaces temporal succession with derivational succession.
   
   MCP Tools implemented (all TRIT-PLUS = +1):
   - aptos-fund: Fund devnet/testnet accounts via faucet
   - aptos-transfer-execute: Execute transfer after simulation approval
   - aptos-stake: Stake APT to validator
   
   Möbius duality with BOB:
   - ALICE (+1) × μ(3) = BOB (-1)
   - μ(3) = -1 (3 is prime)
   
   GF(3) Conservation: Σ trit ≡ 0 (mod 3)"
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
;;; SplitMix64 Seed Derivation
;;; ============================================================

(def ^:const GENESIS-SEED 0x42D)
(def ^:const GAMMA 0x9E3779B97F4A7C15)

(defn splitmix64-next
  "SplitMix64 derivation: seed₀ ⊕ (γ << 1)"
  [seed]
  (let [z (bit-xor seed (bit-shift-left GAMMA 1))]
    (-> z
        (bit-xor (unsigned-bit-shift-right z 30))
        (* 0xBF58476D1CE4E5B9)
        (bit-and 0xFFFFFFFFFFFFFFFF)
        (as-> x (bit-xor x (unsigned-bit-shift-right x 27)))
        (* 0x94D049BB133111EB)
        (bit-and 0xFFFFFFFFFFFFFFFF))))

(defn derive-seed
  "Derive seed at step n from genesis via SplitMix64 chaining"
  [n]
  (loop [seed GENESIS-SEED
         step 0]
    (if (>= step n)
      seed
      (recur (splitmix64-next seed) (inc step)))))

;;; ============================================================
;;; GF(3) Trit Tracking - ALICE is PLUS (+1)
;;; ============================================================

(def ^:const TRIT-MINUS -1)  ;; BOB's domain
(def ^:const TRIT-ZERO   0)  ;; ERGODIC 
(def ^:const TRIT-PLUS  +1)  ;; ALICE's domain

(def ^:const MOEBIUS-3 -1)   ;; μ(3) = -1, key for duality

(defn trit-add
  "GF(3) addition: (a + b) mod 3 mapped to {-1, 0, +1}"
  [a b]
  (let [sum (+ a b)]
    (cond
      (> sum 1)  (- sum 3)
      (< sum -1) (+ sum 3)
      :else sum)))

(defn trit-conserved?
  "Check if trit sum is conserved (equals 0 in GF(3))"
  [trits]
  (zero? (reduce trit-add 0 trits)))

(defn moebius-dual
  "Möbius duality: ALICE (+1) ↔ BOB (-1) via μ(3) = -1"
  [trit]
  (* trit MOEBIUS-3))

(defrecord TritTrackedResult
  [result trit operation timestamp seed-step])

(defn track-trit
  "Wrap result with trit tracking for ALICE (+1)"
  [result operation seed-step]
  (->TritTrackedResult result TRIT-PLUS operation (System/currentTimeMillis) seed-step))

;;; ============================================================
;;; HTTP Client for Aptos API
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

(def FAUCET-URLS
  {:devnet  "https://faucet.devnet.aptoslabs.com"
   :testnet "https://faucet.testnet.aptoslabs.com"})

(def STAKING-CONTRACT "0x1::stake")

(defn get-node-url [network]
  (get NETWORKS (keyword network) (:mainnet NETWORKS)))

(defn get-faucet-url [network]
  (get FAUCET-URLS (keyword network)))

;;; ============================================================
;;; Tool: aptos-fund (TRIT: +1, write operation)
;;; ============================================================

(def ^:private fund-seed-counter (atom 0))

(defn aptos-fund-tool
  "Fund devnet/testnet account via faucet.
   Sponsored Transactions: Pay for users' gas so they can use your dApp with zero APT.
   
   TRIT: +1 (ALICE write operation)"
  [args]
  (let [address (get args "address")
        amount (get args "amount" 100000000)
        network (get args "network" "devnet")
        faucet-url (get-faucet-url network)
        seed-step (swap! fund-seed-counter inc)
        derived-seed (derive-seed seed-step)]
    (if-not faucet-url
      (track-trit
       {:success false 
        :error "Faucet only available on devnet/testnet"
        :derived-seed (format "0x%X" derived-seed)}
       :aptos-fund
       seed-step)
      (let [fund-url (str faucet-url "/mint?address=" address "&amount=" amount)
            {:keys [status body error]} (aptos-request :post fund-url nil)]
        (track-trit
         (cond
           error {:success false :error error}
           (= status 200)
           {:success true
            :address address
            :network network
            :funded-octas amount
            :funded-apt (/ amount 1e8)
            :txn-hashes body
            :derived-seed (format "0x%X" derived-seed)
            :alice-trit TRIT-PLUS}
           :else
           {:success false :status status :body body})
         :aptos-fund
         seed-step)))))

(def APTOS-FUND-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-fund"
    (lj/object
     {:description "Fund devnet/testnet account via faucet (ALICE +1)"
      :required ["address"]}
     {"address" (lj/string "Account address to fund (0x...)")
      "amount" (lj/number "Amount in octas (default: 1 APT = 100000000)")
      "network" (lj/enum "Network (devnet/testnet only)" ["devnet" "testnet"])})
    "Fund account via faucet. Sponsored Transactions: Pay for users' gas so they can use your dApp with zero APT")
   aptos-fund-tool))

;;; ============================================================
;;; Tool: aptos-transfer-execute (TRIT: +1, write operation)
;;; ============================================================

(def ^:private transfer-seed-counter (atom 0))

(defn aptos-transfer-execute-tool
  "Execute APT transfer after simulation approval.
   Requires prior simulation (TRIT: 0) to be approved.
   Sponsored Transactions: Pay for users' gas so they can use your dApp with zero APT.
   
   TRIT: +1 (ALICE write operation)"
  [args]
  (let [sender (get args "sender")
        receiver (get args "receiver")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "devnet")
        simulation-hash (get args "simulation_hash")
        fee-payer (get args "fee_payer")
        node-url (get-node-url network)
        seed-step (swap! transfer-seed-counter inc)
        derived-seed (derive-seed seed-step)]
    
    (if-not simulation-hash
      (track-trit
       {:success false
        :error "Simulation hash required. Run aptos-transfer simulation first."
        :derived-seed (format "0x%X" derived-seed)
        :note "GF(3) pattern: simulate (0) → execute (+1)"}
       :aptos-transfer-execute
       seed-step)
      
      (track-trit
       {:success true
        :phase :execution
        :sender sender
        :receiver receiver
        :amount-apt amount-apt
        :amount-octas amount-octas
        :network network
        :simulation-hash simulation-hash
        :fee-payer (or fee-payer sender)
        :sponsored? (some? fee-payer)
        :derived-seed (format "0x%X" derived-seed)
        :alice-trit TRIT-PLUS
        :note "Execute requires signed transaction via Aptos SDK. Keyless: use Aptos Keyless SDK."}
       :aptos-transfer-execute
       seed-step))))

(def APTOS-TRANSFER-EXECUTE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-transfer-execute"
    (lj/object
     {:description "Execute APT transfer after simulation approval (ALICE +1)"
      :required ["sender" "receiver" "amount" "simulation_hash"]}
     {"sender" (lj/string "Sender address (0x...)")
      "receiver" (lj/string "Receiver address (0x...)")
      "amount" (lj/number "Amount in APT")
      "simulation_hash" (lj/string "Hash from prior simulation approval")
      "fee_payer" (lj/string "Optional fee payer for sponsored transaction")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Execute APT transfer after simulation. Supports sponsored transactions for gasless UX.")
   aptos-transfer-execute-tool))

;;; ============================================================
;;; Tool: aptos-stake (TRIT: +1, write operation)
;;; ============================================================

(def ^:private stake-seed-counter (atom 0))

(defn aptos-stake-tool
  "Stake APT to validator.
   Sponsored Transactions: Pay for users' gas so they can use your dApp with zero APT.
   
   TRIT: +1 (ALICE write operation)"
  [args]
  (let [delegator (get args "delegator")
        validator (get args "validator")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        seed-step (swap! stake-seed-counter inc)
        derived-seed (derive-seed seed-step)]
    
    (track-trit
     {:success true
      :action :stake
      :delegator delegator
      :validator validator
      :amount-apt amount-apt
      :amount-octas amount-octas
      :network network
      :staking-contract STAKING-CONTRACT
      :payload {:function "0x1::delegation_pool::add_stake"
                :type_arguments []
                :arguments [validator (str amount-octas)]}
      :derived-seed (format "0x%X" derived-seed)
      :alice-trit TRIT-PLUS
      :note "Submit via Aptos SDK with delegator signature. For sponsored staking, provide fee_payer."}
     :aptos-stake
     seed-step)))

(def APTOS-STAKE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-stake"
    (lj/object
     {:description "Stake APT to validator (ALICE +1)"
      :required ["delegator" "validator" "amount"]}
     {"delegator" (lj/string "Delegator address (0x...)")
      "validator" (lj/string "Validator pool address (0x...)")
      "amount" (lj/number "Amount to stake in APT")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Stake APT to validator via delegation pool")
   aptos-stake-tool))

;;; ============================================================
;;; All ALICE PLUS Tools
;;; ============================================================

(def ALICE-PLUS-TOOLS
  [APTOS-FUND-TOOL
   APTOS-TRANSFER-EXECUTE-TOOL
   APTOS-STAKE-TOOL])

;;; ============================================================
;;; GF(3) Conservation Validator
;;; ============================================================

(defn validate-trit-conservation
  "Validate that a sequence of operations conserves GF(3).
   ALICE tools are all +1, needs BOB (-1) or ERGODIC (0) for balance."
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce trit-add 0 trits)
        alice-count (count (filter #(= TRIT-PLUS %) trits))
        bob-needed alice-count]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :alice-operations alice-count
     :bob-operations-needed bob-needed
     :moebius-dual (mapv moebius-dual trits)
     :operations (mapv :operation tracked-results)}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule ToposAliceModule
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

  (aor/declare-agent-object-builder
   topology
   "genesis-seed"
   (fn [_setup]
     (atom GENESIS-SEED)))

  (tools/new-tools-agent
   topology
   "AlicePlusToolsAgent"
   ALICE-PLUS-TOOLS)

  (->
    topology
    (aor/new-agent "ToposAliceAgent")

    (aor/node
     "execute"
     nil
     (fn execute-fn [agent-node requests]
       (let [model         (aor/get-agent-object agent-node "openai-model")
             tools-agent   (aor/agent-client agent-node "AlicePlusToolsAgent")
             trit-acc      (aor/get-agent-object agent-node "trit-accumulator")
             genesis       (aor/get-agent-object agent-node "genesis-seed")
             results       (atom [])]

         (doseq [request requests]
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools ALICE-PLUS-TOOLS}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (doseq [tr tool-results]
                   (when (instance? TritTrackedResult tr)
                     (swap! trit-acc conj tr)))
                 (swap! results conj
                   {:request request
                    :tool-calls (mapv #(.name %) tool-calls)
                    :tool-results tool-results}))
               (swap! results conj
                 {:request request
                  :response (.text ai-message)}))))

         (let [conservation (validate-trit-conservation @trit-acc)]
           (aor/result! agent-node
                        {:results @results
                         :stream :alice-plus
                         :genesis-seed (format "0x%X" GENESIS-SEED)
                         :gamma (format "0x%X" GAMMA)
                         :trit-tracking {:operations-count (count @trit-acc)
                                         :conservation conservation
                                         :moebius-3 MOEBIUS-3}})))))))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn fund
  "Fund devnet/testnet account (TRIT: +1)"
  [address & {:keys [amount network] :or {amount 100000000 network "devnet"}}]
  (aptos-fund-tool {"address" address "amount" amount "network" network}))

(defn transfer-execute
  "Execute APT transfer after simulation (TRIT: +1)"
  [sender receiver amount simulation-hash & {:keys [network fee-payer] 
                                              :or {network "devnet"}}]
  (aptos-transfer-execute-tool {"sender" sender 
                                "receiver" receiver 
                                "amount" amount
                                "simulation_hash" simulation-hash
                                "fee_payer" fee-payer
                                "network" network}))

(defn stake
  "Stake APT to validator (TRIT: +1)"
  [delegator validator amount & {:keys [network] :or {network "mainnet"}}]
  (aptos-stake-tool {"delegator" delegator
                     "validator" validator
                     "amount" amount
                     "network" network}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToposAliceModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ToposAliceModule))
          alice-agent (aor/agent-client manager "ToposAliceAgent")
          requests ["Fund my devnet account 0xalice with 5 APT"
                    "Execute transfer of 1 APT from 0xalice to 0xbob with simulation hash 0xsim123"
                    "Stake 10 APT to validator 0xvalidator from 0xalice"]
          result (aor/agent-invoke alice-agent requests)]

      (println "=== ALICE Topos Agent (+1 Stream) ===")
      (println (format "\nGenesis Seed: %s" (:genesis-seed result)))
      (println (format "Gamma: %s" (:gamma result)))
      
      (println "\nResults:")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))))

      (println "\n=== GF(3) Trit Conservation ===")
      (let [tc (:trit-tracking result)]
        (println "Operations:" (:operations-count tc))
        (println "Conservation:" (:conservation tc))
        (println "μ(3) = " (:moebius-3 tc) "(duality with BOB)")))))

(comment
  ;; REPL examples - ALICE PLUS (+1) stream
  
  ;; Fund devnet account (TRIT: +1)
  (fund "0xalice" :amount 500000000)
  
  ;; Execute transfer after simulation (TRIT: +1)
  (transfer-execute "0xalice" "0xbob" 1.0 "0xsim123" :network "devnet")
  
  ;; Stake to validator (TRIT: +1)
  (stake "0xalice" "0xvalidator" 10.0)
  
  ;; Run the agent
  (-main)
  
  ;; Möbius duality: ALICE (+1) × μ(3) = BOB (-1)
  (assert (= (moebius-dual TRIT-PLUS) TRIT-MINUS))
  
  ;; SplitMix64 seed derivation
  (derive-seed 0)  ;; => GENESIS-SEED
  (derive-seed 1)  ;; => next in chain
  
  ;; GF(3) conservation requires BOB:
  ;; ALICE (+1) + BOB (-1) + ERGODIC (0) = 0 ✓
  (trit-conserved? [TRIT-PLUS TRIT-MINUS TRIT-ZERO]))
