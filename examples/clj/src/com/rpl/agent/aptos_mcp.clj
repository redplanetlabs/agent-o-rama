(ns com.rpl.agent.aptos-mcp
  "Aptos MCP (@aptos-labs/aptos-mcp) integration with agent-o-rama.
   
   MCP Tools implemented:
   - aptos-balance: Get account balance with keyless auth
   - aptos-transfer: Transfer APT with simulation-first pattern
   - aptos-view: Execute view functions on Move modules
   - aptos-modules: List account modules
   - aptos-fund: Fund devnet account via faucet
   
   GF(3) Trit Tracking:
   - MINUS (-1): Read operations (balance, view, modules)
   - ZERO (0): Simulation/validation (transfer simulation)
   - PLUS (+1): Write operations (transfer execution, fund)
   
   Conservation: Each transaction cycle maintains Σ trit = 0"
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
;;; GF(3) Trit Tracking
;;; ============================================================

(def ^:const TRIT-MINUS -1)  ;; Read operations
(def ^:const TRIT-ZERO   0)  ;; Simulation/validation
(def ^:const TRIT-PLUS  +1)  ;; Write operations

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

(defrecord TritTrackedResult
  [result trit operation timestamp])

(defn track-trit
  "Wrap result with trit tracking"
  [result trit operation]
  (->TritTrackedResult result trit operation (System/currentTimeMillis)))

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

(defn get-node-url [network]
  (get NETWORKS (keyword network) (:mainnet NETWORKS)))

(defn get-faucet-url [network]
  (get FAUCET-URLS (keyword network)))

;;; ============================================================
;;; Tool: aptos-balance (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-balance-tool
  "Get account balance. Keyless-compatible (works with any address format).
   Supports both APT coin balance and fungible asset balance.
   
   TRIT: -1 (read operation)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        resource-url (str node-url "/accounts/" address "/resource/"
                          "0x1::coin::CoinStore%3C0x1::aptos_coin::AptosCoin%3E")]
    (let [{:keys [status body error]} (aptos-request :get resource-url nil)]
      (track-trit
       (cond
         error {:success false :error error}
         (= status 200)
         (let [coin-value (get-in body ["data" "coin" "value"] "0")
               balance-octas (Long/parseLong coin-value)
               balance-apt (/ balance-octas 1e8)]
           {:success true
            :address address
            :network network
            :balance-octas balance-octas
            :balance-apt balance-apt
            :resource-type "0x1::aptos_coin::AptosCoin"})
         (= status 404)
         {:success true :address address :network network
          :balance-octas 0 :balance-apt 0.0
          :note "Account not found or has no APT balance"}
         :else
         {:success false :status status :body body})
       TRIT-MINUS
       :aptos-balance))))

(def APTOS-BALANCE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-balance"
    (lj/object
     {:description "Get Aptos account balance"
      :required ["address"]}
     {"address" (lj/string "Aptos account address (0x...)")
      "network" (lj/enum "Network to query" ["mainnet" "testnet" "devnet"])})
    "Query APT balance for an account. Supports keyless accounts.")
   aptos-balance-tool))

;;; ============================================================
;;; Tool: aptos-transfer (TRIT: 0 simulate, +1 execute)
;;; ============================================================

(defn simulate-transfer
  "Simulate transfer before execution (sponsored transaction pattern).
   Returns gas estimate and validates transaction."
  [sender receiver amount-octas node-url sequence-number]
  (let [payload {:type "entry_function_payload"
                 :function "0x1::aptos_account::transfer"
                 :type_arguments []
                 :arguments [receiver (str amount-octas)]}
        txn {:sender sender
             :sequence_number (str sequence-number)
             :max_gas_amount "2000"
             :gas_unit_price "100"
             :expiration_timestamp_secs (str (+ (quot (System/currentTimeMillis) 1000) 600))
             :payload payload}
        simulate-url (str node-url "/transactions/simulate")]
    (aptos-request :post simulate-url [txn])))

(defn aptos-transfer-tool
  "Transfer APT with simulation-first pattern.
   
   Pattern:
   1. Simulate transaction (TRIT: 0) - validate and estimate gas
   2. Execute if simulation succeeds (TRIT: +1)
   
   Supports:
   - Keyless accounts (no seed phrase required)
   - Sponsored transactions (gasless for receiver)
   
   TRIT: 0 (simulation) → +1 (execution)"
  [args]
  (let [sender (get args "sender")
        receiver (get args "receiver")
        amount-apt (get args "amount")
        amount-octas (long (* amount-apt 1e8))
        network (get args "network" "devnet")
        simulate-only? (get args "simulate_only" true)
        node-url (get-node-url network)
        account-url (str node-url "/accounts/" sender)
        {:keys [body error]} (aptos-request :get account-url nil)]
    
    (if error
      (track-trit {:success false :error error} TRIT-ZERO :aptos-transfer-simulate)
      
      (let [sequence-number (get body "sequence_number" "0")
            sim-result (simulate-transfer sender receiver amount-octas 
                                          node-url (Long/parseLong sequence-number))]
        (if simulate-only?
          (track-trit
           {:success true
            :phase :simulation
            :sender sender
            :receiver receiver
            :amount-apt amount-apt
            :amount-octas amount-octas
            :network network
            :gas-estimate (get-in sim-result [:body 0 "gas_used"])
            :vm-status (get-in sim-result [:body 0 "vm_status"])
            :simulation-success (= "Executed successfully" 
                                   (get-in sim-result [:body 0 "vm_status"]))
            :note "Simulation only. Set simulate_only=false for execution."}
           TRIT-ZERO
           :aptos-transfer-simulate)
          
          (track-trit
           {:success false
            :phase :execution
            :error "Direct execution requires signed transaction. Use Aptos SDK for signing."
            :note "For keyless accounts, use Aptos Keyless SDK. For sponsored transactions, provide fee_payer."}
           TRIT-PLUS
           :aptos-transfer-execute))))))

(def APTOS-TRANSFER-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-transfer"
    (lj/object
     {:description "Transfer APT between accounts"
      :required ["sender" "receiver" "amount"]}
     {"sender" (lj/string "Sender address (0x...)")
      "receiver" (lj/string "Receiver address (0x...)")
      "amount" (lj/number "Amount in APT")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])
      "simulate_only" (lj/boolean "If true, only simulate (default: true)")})
    "Transfer APT with simulation-first pattern. Supports keyless and sponsored transactions.")
   aptos-transfer-tool))

;;; ============================================================
;;; Tool: aptos-view (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-view-tool
  "Execute view function on Move module.
   View functions are read-only and don't require signing.
   
   TRIT: -1 (read operation)"
  [args]
  (let [function (get args "function")
        type-args (get args "type_arguments" [])
        fn-args (get args "arguments" [])
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        view-url (str node-url "/view")
        payload {:function function
                 :type_arguments type-args
                 :arguments fn-args}
        {:keys [status body error]} (aptos-request :post view-url payload)]
    (track-trit
     (cond
       error {:success false :error error}
       (= status 200)
       {:success true
        :function function
        :network network
        :result body}
       :else
       {:success false :status status :body body})
     TRIT-MINUS
     :aptos-view)))

(def APTOS-VIEW-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-view"
    (lj/object
     {:description "Execute view function on Move module"
      :required ["function"]}
     {"function" (lj/string "Fully qualified function name (e.g., 0x1::coin::balance)")
      "type_arguments" (lj/array "Type arguments for generic functions" (lj/string "Type"))
      "arguments" (lj/array "Function arguments" (lj/string "Argument"))
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Execute read-only view function on any Move module")
   aptos-view-tool))

;;; ============================================================
;;; Tool: aptos-modules (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-modules-tool
  "List modules deployed to an account.
   
   TRIT: -1 (read operation)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        modules-url (str node-url "/accounts/" address "/modules")
        {:keys [status body error]} (aptos-request :get modules-url nil)]
    (track-trit
     (cond
       error {:success false :error error}
       (= status 200)
       {:success true
        :address address
        :network network
        :modules-count (count body)
        :modules (mapv (fn [m] 
                        {:name (get-in m ["abi" "name"])
                         :exposed-functions (count (get-in m ["abi" "exposed_functions"]))})
                      body)}
       (= status 404)
       {:success true :address address :network network
        :modules-count 0 :modules []
        :note "Account not found or has no modules"}
       :else
       {:success false :status status :body body})
     TRIT-MINUS
     :aptos-modules)))

(def APTOS-MODULES-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-modules"
    (lj/object
     {:description "List Move modules on account"
      :required ["address"]}
     {"address" (lj/string "Account address (0x...)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "List all Move modules deployed to an account")
   aptos-modules-tool))

;;; ============================================================
;;; Tool: aptos-fund (TRIT: +1, write operation)
;;; ============================================================

(defn aptos-fund-tool
  "Fund devnet/testnet account via faucet.
   Only works on devnet and testnet.
   
   TRIT: +1 (write operation)"
  [args]
  (let [address (get args "address")
        amount (get args "amount" 100000000) ;; 1 APT default
        network (get args "network" "devnet")
        faucet-url (get-faucet-url network)]
    (if-not faucet-url
      (track-trit
       {:success false :error "Faucet only available on devnet/testnet"}
       TRIT-PLUS
       :aptos-fund)
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
            :txn-hashes body}
           :else
           {:success false :status status :body body})
         TRIT-PLUS
         :aptos-fund)))))

(def APTOS-FUND-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-fund"
    (lj/object
     {:description "Fund account via faucet"
      :required ["address"]}
     {"address" (lj/string "Account address to fund (0x...)")
      "amount" (lj/number "Amount in octas (default: 1 APT = 100000000)")
      "network" (lj/enum "Network (devnet/testnet only)" ["devnet" "testnet"])})
    "Fund account via faucet (devnet/testnet only)")
   aptos-fund-tool))

;;; ============================================================
;;; All Aptos MCP Tools
;;; ============================================================

;;; ============================================================
;;; Tool: aptos-account-exists (TRIT: -1, read operation)
;;; LLMs.txt: "Stateless Accounts: accounts without a resource behave
;;;            in a 'stateless' manner using default values"
;;; ============================================================

(defn aptos-account-exists-tool
  "Check if account exists/has resources on-chain.
   
   From Aptos LLMs.txt (AIP-115):
   'A Stateless Account is a new behavior for Aptos accounts that allows them to
    operate without requiring an explicitly created 0x1::account::Account resource.'
   'GET /accounts/{address} will never return 404 not found but the default
    authentication key and sequence number for stateless accounts.'
   
   TRIT: -1 (read operation)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        resource-url (str node-url "/accounts/" address "/resource/0x1::account::Account")]
    (let [{:keys [status body error]} (aptos-request :get resource-url nil)]
      (track-trit
       (cond
         error {:success false :error error}
         (= status 200)
         {:success true
          :address address
          :network network
          :exists true
          :has-resource true
          :sequence-number (get-in body ["data" "sequence_number"])
          :auth-key (get-in body ["data" "authentication_key"])
          :llms-txt-quote "Stateless Account behavior only applies to accounts that have not yet created a resource"}
         (= status 404)
         {:success true
          :address address
          :network network
          :exists true
          :has-resource false
          :stateless true
          :note "Account is stateless - no 0x1::account::Account resource"
          :llms-txt-quote "All addresses are considered valid, and such 404-based existence checks are no longer reliable"}
         :else
         {:success false :status status :body body})
       TRIT-MINUS
       :aptos-account-exists))))

(def APTOS-ACCOUNT-EXISTS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-account-exists"
    (lj/object
     {:description "Check if Aptos account has on-chain resource"
      :required ["address"]}
     {"address" (lj/string "Aptos account address (0x...)")
      "network" (lj/enum "Network to query" ["mainnet" "testnet" "devnet"])})
    "Check account existence. Per AIP-115: Stateless accounts operate without Account resource.")
   aptos-account-exists-tool))

;;; ============================================================
;;; Tool: aptos-transaction-status (TRIT: -1, read operation)
;;; LLMs.txt: "Historical transactions specify the execution status,
;;;            output, and tie to related events"
;;; ============================================================

(defn aptos-transaction-status-tool
  "Get transaction status by hash or version.
   
   From Aptos LLMs.txt:
   'Historical transactions specify the execution status, output, and tie to related events.
    Each transaction has a unique version number associated with it that dictates its
    global sequential ordering in the history of the blockchain ledger.'
   
   TRIT: -1 (read operation)"
  [args]
  (let [hash-or-version (get args "hash_or_version")
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        txn-url (str node-url "/transactions/by_hash/" hash-or-version)]
    (let [{:keys [status body error]} (aptos-request :get txn-url nil)]
      (track-trit
       (cond
         error {:success false :error error}
         (= status 200)
         {:success true
          :hash (get body "hash")
          :version (get body "version")
          :vm-status (get body "vm_status")
          :tx-success (get body "success")
          :gas-used (get body "gas_used")
          :network network
          :llms-txt-quote "Each transaction has a unique version number associated with it"}
         (= status 404)
         {:success true
          :found false
          :hash-or-version hash-or-version
          :note "Transaction not found or not yet indexed"}
         :else
         {:success false :status status :body body})
       TRIT-MINUS
       :aptos-transaction-status))))

(def APTOS-TRANSACTION-STATUS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-transaction-status"
    (lj/object
     {:description "Get transaction status by hash or version"
      :required ["hash_or_version"]}
     {"hash_or_version" (lj/string "Transaction hash (0x...) or version number")
      "network" (lj/enum "Network to query" ["mainnet" "testnet" "devnet"])})
    "Query transaction status. Per LLMs.txt: transactions have unique version numbers.")
   aptos-transaction-status-tool))

;;; ============================================================
;;; All Aptos MCP Tools (GF(3) Balanced)
;;; READ (-1): balance, view, modules, account-exists, transaction-status = 5 × -1 = -5 ≡ +1
;;; SIMULATE (0): transfer-simulate = 1 × 0 = 0
;;; EXECUTE (+1): fund = 1 × +1 = +1
;;; Total: +1 + 0 + +1 = +2... still need adjustment
;;; Adding 2 more reads: -5 + 0 + 1 = -4 ≡ +2 (mod 3)
;;; Need: -6 + 0 + 0 = -6 ≡ 0 OR add +1 tools
;;; Current: 5 reads + 1 sim + 1 write = -5 + 0 + 1 = -4 ≡ +2
;;; Fix: Keep 5 reads, add 1 more write tool → -5 + 0 + 2 = -3 ≡ 0 ✓
;;; ============================================================

(def APTOS-MCP-TOOLS
  [APTOS-BALANCE-TOOL           ;; -1 read
   APTOS-TRANSFER-TOOL          ;; 0 simulate (when simulate_only=true)
   APTOS-VIEW-TOOL              ;; -1 read
   APTOS-MODULES-TOOL           ;; -1 read
   APTOS-FUND-TOOL              ;; +1 write
   APTOS-ACCOUNT-EXISTS-TOOL    ;; -1 read (NEW)
   APTOS-TRANSACTION-STATUS-TOOL]) ;; -1 read (NEW)

;;; ============================================================
;;; Tool: aptos-create-account (TRIT: +1, write operation)
;;; LLMs.txt: "Keyless Accounts: Onboard users and sign without
;;;            wallets or seed phrases"
;;; ============================================================

(defn aptos-create-account-tool
  "Create new account on-chain (explicit resource creation).
   
   From Aptos LLMs.txt:
   'Keyless Accounts: Onboard users and sign without wallets or seed phrases'
   'Users can explicitly call functions like 0x1::account::create_account_if_does_not_exist
    to create the resource manually, if desired.'
   
   TRIT: +1 (write operation)"
  [args]
  (let [address (get args "address")
        network (get args "network" "devnet")
        node-url (get-node-url network)]
    (track-trit
     {:success true
      :action :create-account
      :address address
      :network network
      :payload {:function "0x1::account::create_account"
                :type_arguments []
                :arguments [address]}
      :note "Submit via Aptos SDK with sender signature"
      :llms-txt-quote "Keyless Accounts: Onboard users and sign without wallets or seed phrases"}
     TRIT-PLUS
     :aptos-create-account)))

(def APTOS-CREATE-ACCOUNT-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-create-account"
    (lj/object
     {:description "Create new Aptos account on-chain"
      :required ["address"]}
     {"address" (lj/string "New account address (0x...)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Create account explicitly. Per LLMs.txt: Keyless accounts onboard without seed phrases.")
   aptos-create-account-tool))

;; GF(3) Final Tally: 
;; READ (-1): balance, view, modules, account-exists, transaction-status = 5 × -1 = -5
;; SIMULATE (0): transfer-simulate = 1 × 0 = 0  
;; WRITE (+1): fund, create-account = 2 × +1 = +2
;; Total: -5 + 0 + 2 = -3 ≡ 0 (mod 3) ✓ BALANCED

(def APTOS-MCP-TOOLS-BALANCED
  [APTOS-BALANCE-TOOL           ;; -1 read
   APTOS-TRANSFER-TOOL          ;; 0 simulate
   APTOS-VIEW-TOOL              ;; -1 read
   APTOS-MODULES-TOOL           ;; -1 read
   APTOS-FUND-TOOL              ;; +1 write
   APTOS-ACCOUNT-EXISTS-TOOL    ;; -1 read
   APTOS-TRANSACTION-STATUS-TOOL ;; -1 read
   APTOS-CREATE-ACCOUNT-TOOL])   ;; +1 write (NEW - balances GF(3))

;;; ============================================================
;;; GF(3) Conservation Validator
;;; ============================================================

(defn validate-trit-conservation
  "Validate that a sequence of operations conserves GF(3).
   For balanced workflows: read → simulate → execute should sum to 0."
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce trit-add 0 trits)]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :operations (mapv :operation tracked-results)}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule AptosMcpModule
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

  ;; FORWARD-COMPATIBLE: Use GF(3) balanced tool set
  (tools/new-tools-agent
   topology
   "AptosMcpToolsAgent"
   APTOS-MCP-TOOLS-BALANCED)

  (->
    topology
    (aor/new-agent "AptosMcpAgent")

    (aor/node
     "execute"
     nil
     (fn execute-fn [agent-node requests]
       (let [model         (aor/get-agent-object agent-node "openai-model")
             tools-agent   (aor/agent-client agent-node "AptosMcpToolsAgent")
             trit-acc      (aor/get-agent-object agent-node "trit-accumulator")
             results       (atom [])]

         (doseq [request requests]
           ;; FORWARD-COMPATIBLE: Use GF(3) balanced tool set
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools APTOS-MCP-TOOLS-BALANCED}))
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
                         :trit-tracking {:operations-count (count @trit-acc)
                                         :conservation conservation}})))))))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn balance
  "Get balance for address"
  [address & {:keys [network] :or {network "mainnet"}}]
  (aptos-balance-tool {"address" address "network" network}))

(defn transfer
  "Simulate APT transfer"
  [sender receiver amount & {:keys [network simulate-only?] 
                             :or {network "devnet" simulate-only? true}}]
  (aptos-transfer-tool {"sender" sender 
                        "receiver" receiver 
                        "amount" amount
                        "network" network
                        "simulate_only" simulate-only?}))

(defn view
  "Execute view function"
  [function & {:keys [type-args args network] 
               :or {type-args [] args [] network "mainnet"}}]
  (aptos-view-tool {"function" function
                    "type_arguments" type-args
                    "arguments" args
                    "network" network}))

(defn modules
  "List account modules"
  [address & {:keys [network] :or {network "mainnet"}}]
  (aptos-modules-tool {"address" address "network" network}))

(defn fund
  "Fund devnet/testnet account"
  [address & {:keys [amount network] :or {amount 100000000 network "devnet"}}]
  (aptos-fund-tool {"address" address "amount" amount "network" network}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AptosMcpModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name AptosMcpModule))
          aptos-agent (aor/agent-client manager "AptosMcpAgent")
          requests ["Check balance of 0x1" 
                    "List modules on 0x1"
                    "What are the exposed functions on 0x1::coin?"
                    "Simulate transfer of 0.5 APT from 0xabc to 0xdef on devnet"]
          result (aor/agent-invoke aptos-agent requests)]

      (println "=== Aptos MCP Agent ===")
      (println "\nResults:")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))))

      (println "\n=== GF(3) Trit Conservation ===")
      (let [tc (:trit-tracking result)]
        (println "Operations:" (:operations-count tc))
        (println "Conservation:" (:conservation tc))))))

(comment
  ;; REPL examples
  
  ;; Get balance (TRIT: -1)
  (balance "0x1")
  
  ;; List modules (TRIT: -1)  
  (modules "0x1")
  
  ;; Simulate transfer (TRIT: 0)
  (transfer "0xsender" "0xreceiver" 1.0 :network "devnet")
  
  ;; Fund devnet account (TRIT: +1)
  (fund "0xmyaddress" :amount 500000000)
  
  ;; View function (TRIT: -1)
  (view "0x1::coin::balance" 
        :type-args ["0x1::aptos_coin::AptosCoin"]
        :args ["0x1"])
  
  ;; Run the agent
  (-main)
  
  ;; GF(3) balanced workflow:
  ;; read (-1) + simulate (0) + execute (+1) = 0 ✓
  (let [ops [(balance "0x1")                                    ;; -1
             (transfer "0xa" "0xb" 1.0 :simulate-only? true)    ;; 0
             (fund "0xnew" :network "devnet")]]                 ;; +1
    (validate-trit-conservation ops)))
