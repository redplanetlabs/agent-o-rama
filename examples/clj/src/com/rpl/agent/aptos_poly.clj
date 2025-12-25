(ns com.rpl.agent.aptos-poly
  "Polynomial functor agent for Aptos blockchain interaction.
   
   Spivak polynomial functor pattern: P(Y) = Σ_{i∈I} Y^{B_i}
   - Position (I) = Aptos account state (balance, sequence_number)
   - Direction (B) = transaction to apply at each position
   - Lens = agent interaction protocol composing position→direction→new-position
   
   This models agent-blockchain interaction as a polynomial functor where:
   - Forward map: observing current state (covariant)
   - Backward map: proposing transactions (contravariant)"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]))

;;; ============================================================
;;; Polynomial Functor Types
;;; ============================================================

(defrecord AptosPosition
  [address balance sequence-number timestamp])

(defrecord AptosDirection
  [sender receiver amount payload gas-unit-price max-gas-amount])

(defrecord AptosLens
  [position-fn direction-fn compose-fn])

;;; ============================================================
;;; Position: Forward map (covariant observation)
;;; ============================================================

(defn aptos-position-tool
  "Get current Aptos account state. Position in polynomial functor.
   Forward map: () → Position"
  [args]
  (let [address (get args "address")
        node-url (get args "node_url" "https://fullnode.mainnet.aptoslabs.com/v1")]
    (->AptosPosition
     address
     (get args "mock_balance" 1000000)
     (get args "mock_sequence" 0)
     (System/currentTimeMillis))))

(def APTOS-POSITION-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-position"
    (lj/object
     {:description "Parameters for querying Aptos account position"
      :required    ["address"]}
     {"address"       (lj/string "Aptos account address (0x...)")
      "node_url"      (lj/string "Aptos node URL (optional)")
      "mock_balance"  (lj/number "Mock balance for testing")
      "mock_sequence" (lj/number "Mock sequence number for testing")})
    "Query current Aptos account state (position in polynomial functor)")
   aptos-position-tool))

;;; ============================================================
;;; Direction: Backward map (contravariant proposal)
;;; ============================================================

(defn aptos-direction-tool
  "Propose a transaction direction. Contravariant backward map.
   Backward map: Position → Direction"
  [args]
  (let [sender   (get args "sender")
        receiver (get args "receiver")
        amount   (get args "amount")]
    (->AptosDirection
     sender
     receiver
     amount
     {:function "0x1::aptos_coin::transfer"
      :type_arguments []
      :arguments [receiver (str amount)]}
     100
     2000)))

(def APTOS-DIRECTION-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-direction"
    (lj/object
     {:description "Parameters for proposing Aptos transaction direction"
      :required    ["sender" "receiver" "amount"]}
     {"sender"   (lj/string "Sender address (0x...)")
      "receiver" (lj/string "Receiver address (0x...)")
      "amount"   (lj/number "Amount in octas (1 APT = 10^8 octas)")})
    "Propose transaction direction (backward map in polynomial functor)")
   aptos-direction-tool))

;;; ============================================================
;;; Lens: Composition (position→direction→new-position)
;;; ============================================================

(defn aptos-lens-tool
  "Compose position observation with direction proposal to compute new position.
   Lens law: get-put coherence for polynomial functor composition.
   
   P ◁ Q composition: (Σ_i Y^{B_i}) ◁ (Σ_j Y^{C_j}) = Σ_{(i,f)} Y^{Σ_j C_{f(j)}}"
  [args]
  (let [address       (get args "address")
        receiver      (get args "receiver")
        amount        (get args "amount")
        current-pos   (aptos-position-tool {"address" address
                                            "mock_balance" (get args "current_balance" 1000000)
                                            "mock_sequence" (get args "current_sequence" 0)})
        direction     (aptos-direction-tool {"sender" address
                                             "receiver" receiver
                                             "amount" amount})
        new-balance   (- (:balance current-pos) amount)
        new-sequence  (inc (:sequence-number current-pos))
        new-position  (->AptosPosition
                       address
                       new-balance
                       new-sequence
                       (System/currentTimeMillis))]
    {:lens-composition true
     :get {:position current-pos}
     :put {:direction direction}
     :result {:new-position new-position
              :delta {:balance-change (- amount)
                      :sequence-change 1}}
     :polynomial-trace {:forward-map :position
                        :backward-map :direction
                        :composition :lens}}))

(def APTOS-LENS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "aptos-lens"
    (lj/object
     {:description "Parameters for polynomial lens composition"
      :required    ["address" "receiver" "amount"]}
     {"address"          (lj/string "Account address to observe and update")
      "receiver"         (lj/string "Transaction receiver address")
      "amount"           (lj/number "Transfer amount")
      "current_balance"  (lj/number "Current balance (for testing)")
      "current_sequence" (lj/number "Current sequence number (for testing)")})
    "Compose position→direction→new-position via polynomial lens")
   aptos-lens-tool))

;;; ============================================================
;;; Polynomial Functor Combinators
;;; ============================================================

(defn poly-tensor
  "Tensor product of polynomial functors: P ⊗ Q
   Positions multiply, directions add."
  [p q]
  {:positions (* (count (:positions p)) (count (:positions q)))
   :directions (concat (:directions p) (:directions q))
   :composition :tensor})

(defn poly-compose
  "Composition of polynomial functors: P ◁ Q
   Substitution semantics for agent chaining."
  [p q]
  {:positions (:positions q)
   :directions (mapcat (fn [pos] (:directions p)) (:positions q))
   :composition :substitution})

(defn poly-para
  "Para construction for stateful polynomial: Para(P)
   Objects are positions, morphisms are directions."
  [p state]
  {:state state
   :update-fn (fn [dir] (poly-compose p {:positions [state] :directions [dir]}))
   :composition :para})

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule AptosPolyModule
  [topology]

  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [_setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (or (System/getenv "OPENAI_API_KEY") "demo-key"))
         (.modelName "gpt-4o-mini")
         .build)))

  (tools/new-tools-agent
   topology
   "AptosToolsAgent"
   [APTOS-POSITION-TOOL APTOS-DIRECTION-TOOL APTOS-LENS-TOOL])

  (->
    topology
    (aor/new-agent "AptosPolyAgent")

    (aor/node
     "polynomial-interact"
     nil
     (fn polynomial-interact-fn [agent-node requests]
       (let [model       (aor/get-agent-object agent-node "openai-model")
             tools-agent (aor/agent-client agent-node "AptosToolsAgent")
             results     (atom [])]

         (doseq [request requests]
           (let [response   (lc4j/chat model
                                       (lc4j/chat-request
                                        [(UserMessage. (str request))]
                                        {:tools [APTOS-POSITION-TOOL
                                                 APTOS-DIRECTION-TOOL
                                                 APTOS-LENS-TOOL]}))
                 ai-message (.aiMessage response)
                 tool-calls (vec (.toolExecutionRequests ai-message))]

             (if (seq tool-calls)
               (let [tool-results (aor/agent-invoke tools-agent tool-calls)]
                 (swap! results conj
                   {:request      request
                    :functor-type (cond
                                    (some #(str/includes? (.name %) "position") tool-calls) :forward
                                    (some #(str/includes? (.name %) "direction") tool-calls) :backward
                                    (some #(str/includes? (.name %) "lens") tool-calls) :composition
                                    :else :unknown)
                    :tool-results tool-results}))
               (swap! results conj
                 {:request request
                  :response (.text ai-message)}))))

         (aor/result! agent-node
                      {:polynomial-functor "Aptos P(Y) = Σ_pos Y^{tx}"
                       :requests-count (count requests)
                       :results @results}))))))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AptosPolyModule {:tasks 1 :threads 1})
    (let [manager   (aor/agent-manager ipc (rama/get-module-name AptosPolyModule))
          poly-agent (aor/agent-client manager "AptosPolyAgent")
          requests  ["What is the current position for address 0xabcd1234?"
                     "Propose a transfer of 100 APT from 0xabcd1234 to 0xdef5678"
                     "Apply lens composition: observe 0xabcd1234 then transfer 50 APT to 0xef901234"]
          result    (aor/agent-invoke poly-agent requests)]

      (println "=== Polynomial Functor Agent ===")
      (println "Functor:" (:polynomial-functor result))
      (println "Requests processed:" (:requests-count result))
      (println "\nResults:")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (println "    Functor type:" (:functor-type r :direct))
        (when (:tool-results r)
          (println "    Tool results:" (count (:tool-results r))))))))

(comment
  (-main))
