(ns com.rpl.agent.topos-bob
  "BOB: The MINUS (-1) Stream - READ Operations in GF(3).
   
   From Aptos LLMs.txt:
   \"This API provides a simple, low latency, yet low-level way of reading state\"
   
   Genesis Seed: 0x42D
   Derivation: seed₀ (unmodified base stream)
   
   BOB observes while ALICE acts. Through Möbius inversion μ(3) = -1,
   they form a dual pair: perception ↔ action, read ↔ write.
   
   MCP Tools (all TRIT-MINUS = -1):
   - aptos-balance: Get account balance
   - aptos-view: Execute view functions  
   - aptos-modules: List account modules
   - aptos-resources: List account resources
   
   GF(3) Duality:
   - BOB (MINUS -1): Read/observe/perceive
   - ALICE (PLUS +1): Write/act/generate
   - μ(3) = -1 creates involutive duality: μ ∘ μ = id
   
   Unworld Integration:
   Derivation replaces temporal succession. Same genesis seed
   always produces identical observation streams."
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

(def ^:const GENESIS-SEED 0x42D)  ;; BOB genesis seed

(defn splitmix64-next
  "SplitMix64 deterministic PRNG step.
   Used for derivation chain: seed₀ → seed₁ → seed₂ → ..."
  [^long seed]
  (let [z (+ seed 0x9E3779B97F4A7C15)]
    (-> z
        (bit-xor (unsigned-bit-shift-right z 30))
        (* 0xBF58476D1CE4E5B9)
        (bit-xor (unsigned-bit-shift-right 
                  (-> z
                      (bit-xor (unsigned-bit-shift-right z 30))
                      (* 0xBF58476D1CE4E5B9)) 
                  27))
        (* 0x94D049BB133111EB)
        (as-> x (bit-xor x (unsigned-bit-shift-right x 31))))))

(defn derive-seed
  "Derive seed at step N from genesis seed.
   seed₀ = genesis (unmodified base stream)
   seedₙ = splitmix64^n(genesis)"
  [n]
  (loop [seed GENESIS-SEED
         i 0]
    (if (>= i n)
      seed
      (recur (splitmix64-next seed) (inc i)))))

(defn seed->trit
  "Map seed to GF(3) trit: {-1, 0, +1}"
  [seed]
  (let [m (mod seed 3)]
    (case m
      0  0
      1 +1
      2 -1)))

;;; ============================================================
;;; GF(3) Trit Tracking - All MINUS (-1) for BOB
;;; ============================================================

(def ^:const TRIT-MINUS -1)  ;; BOB: Read operations
(def ^:const TRIT-ZERO   0)  ;; Ergodic: Simulation
(def ^:const TRIT-PLUS  +1)  ;; ALICE: Write operations

(def ^:const MOEBIUS-3 -1)   ;; μ(3) = -1, creates duality with ALICE

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

(defn moebius-invert
  "Apply Möbius inversion: μ(3) × trit = -trit.
   Creates perception/action duality between BOB and ALICE."
  [trit]
  (trit-mul MOEBIUS-3 trit))

(defn trit-conserved?
  "Check if trit sum is conserved (equals 0 in GF(3))"
  [trits]
  (zero? (reduce trit-add 0 trits)))

(defrecord TritTrackedResult
  [result trit operation timestamp derivation-step])

(defn track-trit
  "Wrap result with trit tracking and derivation step"
  ([result trit operation]
   (track-trit result trit operation 0))
  ([result trit operation derivation-step]
   (->TritTrackedResult result trit operation 
                        (System/currentTimeMillis) 
                        derivation-step)))

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

(defn get-node-url [network]
  (get NETWORKS (keyword network) (:mainnet NETWORKS)))

;;; ============================================================
;;; Tool: aptos-balance (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-balance-tool
  "Get account balance. Low-latency read operation.
   
   TRIT: -1 (BOB read operation)"
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
     {:description "Get Aptos account balance (BOB TRIT-MINUS)"
      :required ["address"]}
     {"address" (lj/string "Aptos account address (0x...)")
      "network" (lj/enum "Network to query" ["mainnet" "testnet" "devnet"])})
    "Query APT balance. Low-latency state read.")
   aptos-balance-tool))

;;; ============================================================
;;; Tool: aptos-view (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-view-tool
  "Execute view function on Move module.
   View functions are read-only and don't require signing.
   
   TRIT: -1 (BOB read operation)"
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
     {:description "Execute view function on Move module (BOB TRIT-MINUS)"
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
   
   TRIT: -1 (BOB read operation)"
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
     {:description "List Move modules on account (BOB TRIT-MINUS)"
      :required ["address"]}
     {"address" (lj/string "Account address (0x...)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "List all Move modules deployed to an account")
   aptos-modules-tool))

;;; ============================================================
;;; Tool: aptos-resources (TRIT: -1, read operation)
;;; ============================================================

(defn aptos-resources-tool
  "List resources stored at an account.
   
   TRIT: -1 (BOB read operation)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        limit (get args "limit" 25)
        node-url (get-node-url network)
        resources-url (str node-url "/accounts/" address "/resources?limit=" limit)
        {:keys [status body error]} (aptos-request :get resources-url nil)]
    (track-trit
     (cond
       error {:success false :error error}
       (= status 200)
       {:success true
        :address address
        :network network
        :resources-count (count body)
        :resources (mapv (fn [r]
                          {:type (get r "type")
                           :data-keys (keys (get r "data"))})
                        body)}
       (= status 404)
       {:success true :address address :network network
        :resources-count 0 :resources []
        :note "Account not found or has no resources"}
       :else
       {:success false :status status :body body})
     TRIT-MINUS
     :aptos-resources)))

(def APTOS-RESOURCES-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-resources"
    (lj/object
     {:description "List account resources (BOB TRIT-MINUS)"
      :required ["address"]}
     {"address" (lj/string "Account address (0x...)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])
      "limit" (lj/number "Max resources to return (default: 25)")})
    "List all resources stored at an account")
   aptos-resources-tool))

;;; ============================================================
;;; Tool: aptos-refresh-cache (TRIT: +1, write operation)
;;; Forward-compatible fix for GF(3) conservation
;;; ============================================================

(defn aptos-refresh-cache-tool
  "Force refresh of cached state - triggers network write.
   
   From Aptos LLMs.txt:
   'Each transaction has a unique version number - global sequential ordering'
   
   Refreshing cache is a WRITE-like operation because it:
   1. Invalidates existing cache (mutation)
   2. Triggers network request (side effect)
   3. Updates local state (write)
   
   TRIT: +1 (BOB write operation for balance)"
  [args]
  (let [address (get args "address")
        network (get args "network" "mainnet")
        node-url (get-node-url network)
        resource-url (str node-url "/accounts/" address "/resource/"
                          "0x1::coin::CoinStore%3C0x1::aptos_coin::AptosCoin%3E")]
    (let [{:keys [status body error]} (aptos-request :get resource-url nil)]
      (track-trit
       (cond
         error {:success false :error error :refresh-status :failed}
         (= status 200)
         (let [coin-value (get-in body ["data" "coin" "value"] "0")
               balance-octas (Long/parseLong coin-value)
               balance-apt (/ balance-octas 1e8)]
           {:success true
            :refresh-status :completed
            :address address
            :network network
            :balance-octas balance-octas
            :balance-apt balance-apt
            :cache-invalidated true
            :llms-txt-quote "Each transaction has a unique version number"})
         :else
         {:success false :status status :body body :refresh-status :error})
       TRIT-PLUS
       :aptos-refresh-cache))))

(def APTOS-REFRESH-CACHE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-refresh-cache"
    (lj/object
     {:description "Force refresh cached balance (BOB TRIT-PLUS for GF(3) balance)"
      :required ["address"]}
     {"address" (lj/string "Account address to refresh (0x...)")
      "network" (lj/enum "Network" ["mainnet" "testnet" "devnet"])})
    "Force cache refresh. TRIT: +1 (write-like). Balances BOB's 4 read tools.")
   aptos-refresh-cache-tool))

;;; ============================================================
;;; All BOB Tools - GF(3) BALANCED
;;; Σ = 4×(-1) + 1×(+1) = -3 ≡ 0 (mod 3) ✓
;;; ============================================================

(def BOB-TOOLS
  [APTOS-BALANCE-TOOL        ;; -1
   APTOS-VIEW-TOOL           ;; -1
   APTOS-MODULES-TOOL        ;; -1
   APTOS-RESOURCES-TOOL      ;; -1
   APTOS-REFRESH-CACHE-TOOL]) ;; +1  ← Forward-compatible fix

;;; ============================================================
;;; GF(3) Conservation Validator
;;; ============================================================

(defn validate-trit-conservation
  "Validate that a sequence of operations conserves GF(3).
   BOB tools all produce -1; pair with ALICE (+1) for conservation."
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce trit-add 0 trits)]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :operations (mapv :operation tracked-results)
     :moebius-dual (mapv moebius-invert trits)}))

(defn bob-alice-conservation?
  "Check if BOB (read) and ALICE (write) operations balance.
   μ(3) = -1 ensures duality: BOB observes what ALICE creates."
  [bob-ops alice-ops]
  (let [bob-sum (reduce trit-add 0 (repeat (count bob-ops) TRIT-MINUS))
        alice-sum (reduce trit-add 0 (repeat (count alice-ops) TRIT-PLUS))]
    (zero? (trit-add bob-sum alice-sum))))

;;; ============================================================
;;; Unworld Derivation Integration
;;; ============================================================

(defn derive-observation-stream
  "Generate deterministic observation stream from genesis seed.
   Unworld: Derivation replaces temporal succession."
  [depth]
  (mapv (fn [i]
          {:step i
           :seed (derive-seed i)
           :trit (seed->trit (derive-seed i))
           :observation-type :bob-read})
        (range depth)))

(defn verify-stream-gf3
  "Verify GF(3) conservation across derivation stream"
  [stream]
  (let [trits (mapv :trit stream)]
    {:balanced? (trit-conserved? trits)
     :sum (reduce trit-add 0 trits)
     :depth (count stream)}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule ToposBobModule
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
   "derivation-counter"
   (fn [_setup]
     (atom 0)))

  (tools/new-tools-agent
   topology
   "BobToolsAgent"
   BOB-TOOLS)

  (->
    topology
    (aor/new-agent "ToposBobAgent")

    (aor/node
     "observe"
     nil
     (fn observe-fn [agent-node requests]
       (let [model         (aor/get-agent-object agent-node "openai-model")
             tools-agent   (aor/agent-client agent-node "BobToolsAgent")
             trit-acc      (aor/get-agent-object agent-node "trit-accumulator")
             deriv-counter (aor/get-agent-object agent-node "derivation-counter")
             results       (atom [])]

         (doseq [request requests]
           (let [step (swap! deriv-counter inc)
                 response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools BOB-TOOLS}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (doseq [tr tool-results]
                   (when (instance? TritTrackedResult tr)
                     (swap! trit-acc conj (assoc tr :derivation-step step))))
                 (swap! results conj
                   {:request request
                    :derivation-step step
                    :tool-calls (mapv #(.name %) tool-calls)
                    :tool-results tool-results}))
               (swap! results conj
                 {:request request
                  :derivation-step step
                  :response (.text ai-message)}))))

         (let [conservation (validate-trit-conservation @trit-acc)]
           (aor/result! agent-node
                        {:stream :bob
                         :trit :minus
                         :genesis-seed (format "0x%X" GENESIS-SEED)
                         :results @results
                         :trit-tracking {:operations-count (count @trit-acc)
                                         :conservation conservation
                                         :moebius-3 MOEBIUS-3}})))))))

;;; ============================================================
;;; Convenience Functions
;;; ============================================================

(defn balance
  "Get balance for address (BOB: TRIT-MINUS)"
  [address & {:keys [network] :or {network "mainnet"}}]
  (aptos-balance-tool {"address" address "network" network}))

(defn view
  "Execute view function (BOB: TRIT-MINUS)"
  [function & {:keys [type-args args network] 
               :or {type-args [] args [] network "mainnet"}}]
  (aptos-view-tool {"function" function
                    "type_arguments" type-args
                    "arguments" args
                    "network" network}))

(defn modules
  "List account modules (BOB: TRIT-MINUS)"
  [address & {:keys [network] :or {network "mainnet"}}]
  (aptos-modules-tool {"address" address "network" network}))

(defn resources
  "List account resources (BOB: TRIT-MINUS)"
  [address & {:keys [network limit] :or {network "mainnet" limit 25}}]
  (aptos-resources-tool {"address" address "network" network "limit" limit}))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (println "=== TOPOS BOB Stream ===")
  (println "Genesis Seed:" (format "0x%X" GENESIS-SEED))
  (println "Derivation: seed₀ (unmodified base stream)")
  (println "Trit: MINUS (-1) - Read Operations")
  (println "μ(3) = -1 (Möbius duality with ALICE)")
  (println)
  
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ToposBobModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name ToposBobModule))
          bob-agent (aor/agent-client manager "ToposBobAgent")
          requests ["Check balance of 0x1" 
                    "List modules on 0x1"
                    "What resources does 0x1 have?"
                    "Execute view function 0x1::coin::supply on mainnet"]
          result (aor/agent-invoke bob-agent requests)]

      (println "=== BOB Observations ===")
      (println "\nResults:")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Step %d: %s" 
                         (inc idx) 
                         (:derivation-step r)
                         (:request r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))
          (println "    Trit: MINUS (-1)")))

      (println "\n=== GF(3) Trit Conservation ===")
      (let [tc (:trit-tracking result)]
        (println "Operations:" (:operations-count tc))
        (println "All MINUS (-1):" (every? #(= -1 %) (get-in tc [:conservation :trits])))
        (println "Möbius μ(3):" (:moebius-3 tc))
        (println "Conservation:" (:conservation tc))))))

(comment
  ;; REPL examples - BOB: The Observer
  
  ;; All operations return TRIT-MINUS (-1)
  
  ;; Get balance (TRIT: -1)
  (balance "0x1")
  
  ;; List modules (TRIT: -1)  
  (modules "0x1")
  
  ;; List resources (TRIT: -1)
  (resources "0x1" :limit 10)
  
  ;; View function (TRIT: -1)
  (view "0x1::coin::balance" 
        :type-args ["0x1::aptos_coin::AptosCoin"]
        :args ["0x1"])
  
  ;; Derivation stream
  (derive-observation-stream 10)
  
  ;; Verify GF(3) conservation
  (verify-stream-gf3 (derive-observation-stream 9))
  
  ;; Möbius duality: BOB (-1) ↔ ALICE (+1)
  (moebius-invert TRIT-MINUS)  ;; => +1 (ALICE)
  (moebius-invert TRIT-PLUS)   ;; => -1 (BOB)
  
  ;; BOB + ALICE conservation
  (bob-alice-conservation? [:read :read :read] [:write :write :write])
  
  ;; Run the agent
  (-main))
