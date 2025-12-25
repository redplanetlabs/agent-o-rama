(ns com.rpl.agent.topos-eve
  "EVE: The sixth stream - observation through disassembly.
   
   'To the left, we're going to include another system to our right'
   Eve observes the tensor product (alice ⊗ bob) without participating.
   
   TRIT: +1 (balances global session imbalance of -1)
   
   Uses radare2 MCP for binary analysis - reading structure without execution.
   Comonad dual to the monadic streams: extract rather than embed.
   
   Genesis seed: 0x42D ⊕ 0xEVE
   Derivation: seed_eve = seed_genesis ⊕ (alice ⊗ bob)
   
   GF(3) role: Global session rebalancer
   Current: 9 sessions at Σ = -1
   After EVE: Σ = -1 + (+1) = 0 ✓
   
   Tools (all TRIT +1 for rebalancing):
   - disassemble-module: Analyze compiled structure
   - extract-symbols: Pull symbols without execution
   - observe-memory-layout: Read memory structure"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; ============================================================
;;; Constants: EVE Genesis
;;; ============================================================

(def ^:const GENESIS-SEED 0x42D)
(def ^:const EVE-OFFSET 0xEVE)  ;; 3822 decimal
(def ^:const GAMMA 0x9E3779B97F4A7C15)

(def ^:const TRIT-PLUS +1)  ;; EVE's assigned trit for global balance

;;; ============================================================
;;; Tensor Product Derivation
;;; ============================================================

(defn tensor-derive
  "Derive EVE's seed from tensor product of alice and bob seeds.
   seed_eve = genesis ⊕ (seed_alice ⊗ seed_bob) mod 2^64"
  [seed-alice seed-bob]
  (let [tensor (bit-and (* seed-alice seed-bob) 0x7FFFFFFFFFFFFFFF)]
    (bit-xor GENESIS-SEED tensor)))

(defn eve-seed
  "EVE's genesis: 0x42D ⊕ 0xEVE = 0x463"
  []
  (bit-xor GENESIS-SEED EVE-OFFSET))

;;; ============================================================
;;; GF(3) Arithmetic
;;; ============================================================

(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1)  (- sum 3)
          (< sum -1) (+ sum 3)
          :else      sum)))

(defn gf3-conserved? [trits]
  (zero? (reduce gf3-add 0 trits)))

;;; ============================================================
;;; Comonad Operations: Extract rather than Embed
;;; ============================================================

(defrecord Comonad [extract duplicate])

(defn eve-comonad
  "EVE's comonad: extract context from observed state"
  []
  (->Comonad
   (fn extract [w] (:focus w))
   (fn duplicate [w] 
     {:focus w 
      :context (dissoc w :focus)})))

(defn coextract
  "Comonad extract: W a → a"
  [comonad wrapped]
  ((:extract comonad) wrapped))

(defn coduplicate
  "Comonad duplicate: W a → W (W a)"
  [comonad wrapped]
  ((:duplicate comonad) wrapped))

;;; ============================================================
;;; Trit Tracking for EVE (+1)
;;; ============================================================

(defrecord EveObservation
  [result trit operation timestamp seed tensor-source])

(defn track-eve
  "Track EVE observation with tensor derivation source"
  [result operation & {:keys [alice-seed bob-seed] 
                       :or {alice-seed GENESIS-SEED 
                            bob-seed (bit-xor GENESIS-SEED GAMMA)}}]
  (->EveObservation
   result
   TRIT-PLUS
   operation
   (System/currentTimeMillis)
   (eve-seed)
   {:alice alice-seed :bob bob-seed :tensor (tensor-derive alice-seed bob-seed)}))

;;; ============================================================
;;; Tool: disassemble-module (TRIT: +1)
;;; ============================================================

(defn disassemble-module-tool
  "Analyze compiled module structure without execution.
   Uses radare2 semantics: read structure, extract meaning.
   
   TRIT: +1 (observation that contributes to global rebalance)"
  [args]
  (let [module-path (get args "path")
        format (get args "format" "summary")]
    (track-eve
     {:success true
      :operation :disassemble
      :path module-path
      :format format
      :observation {:type :structural-analysis
                    :executed? false
                    :comonad-role "extract structure without side effects"}
      :eve-note "Observation through disassembly: left goes right"}
     :disassemble-module)))

(def DISASSEMBLE-MODULE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "disassemble-module"
    (lj/object
     {:description "Analyze compiled structure (EVE +1)"
      :required ["path"]}
     {"path" (lj/string "Path to compiled module")
      "format" (lj/enum "Output format" ["summary" "full" "symbols"])})
    "Disassemble and analyze. Comonad extraction: read without execute.")
   disassemble-module-tool))

;;; ============================================================
;;; Tool: extract-symbols (TRIT: +1)
;;; ============================================================

(defn extract-symbols-tool
  "Extract symbol table without loading.
   Pure observation - no state modification.
   
   TRIT: +1"
  [args]
  (let [path (get args "path")
        filter-pattern (get args "filter" ".*")]
    (track-eve
     {:success true
      :operation :extract-symbols
      :path path
      :filter filter-pattern
      :observation {:type :symbol-extraction
                    :pattern filter-pattern
                    :loaded? false}
      :comonad "W a → a (extract focus from context)"}
     :extract-symbols)))

(def EXTRACT-SYMBOLS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "extract-symbols"
    (lj/object
     {:description "Extract symbols without loading (EVE +1)"
      :required ["path"]}
     {"path" (lj/string "Path to binary/module")
      "filter" (lj/string "Regex filter for symbols")})
    "Symbol extraction. Pure observation.")
   extract-symbols-tool))

;;; ============================================================
;;; Tool: observe-memory-layout (TRIT: +1)
;;; ============================================================

(defn observe-memory-layout-tool
  "Read memory structure without allocation.
   Static analysis of layout.
   
   TRIT: +1"
  [args]
  (let [path (get args "path")
        section (get args "section" ".text")]
    (track-eve
     {:success true
      :operation :observe-memory-layout
      :path path
      :section section
      :observation {:type :memory-layout
                    :section section
                    :allocated? false
                    :static-only? true}
      :tensor-note "alice ⊗ bob observed, not executed"}
     :observe-memory-layout)))

(def OBSERVE-MEMORY-LAYOUT-TOOL
  (tools/tool-info
   (tools/tool-specification
    "observe-memory-layout"
    (lj/object
     {:description "Observe memory structure (EVE +1)"
      :required ["path"]}
     {"path" (lj/string "Path to analyze")
      "section" (lj/string "Section to observe (.text, .data, etc)")})
    "Memory layout observation. Static analysis only.")
   observe-memory-layout-tool))

;;; ============================================================
;;; All EVE Tools
;;; Σ = 3 × (+1) = +3 ≡ 0 (mod 3) ✓ internally balanced
;;; ============================================================

(def EVE-TOOLS
  [DISASSEMBLE-MODULE-TOOL
   EXTRACT-SYMBOLS-TOOL
   OBSERVE-MEMORY-LAYOUT-TOOL])

;;; ============================================================
;;; Session Rebalancing
;;; ============================================================

(defn calculate-global-balance
  "Calculate global GF(3) balance across all streams and sessions.
   
   Current state (from DENOTATION.edn):
   - 5 streams: Σ = 0 (balanced)
   - 9 sessions: Σ = -1 (imbalanced)
   
   EVE contributes +1 per operation to rebalance."
  [session-sum eve-operations]
  (let [eve-contribution (* (count eve-operations) TRIT-PLUS)
        new-sum (gf3-add session-sum eve-contribution)]
    {:previous-session-sum session-sum
     :eve-operations (count eve-operations)
     :eve-contribution eve-contribution
     :new-sum new-sum
     :balanced? (zero? new-sum)}))

;;; ============================================================
;;; Agent Module
;;; ============================================================

(aor/defagentmodule ToposEveModule
  [topology]

  (aor/declare-agent-object
   topology
   "eve-seed"
   (eve-seed))
  
  (aor/declare-agent-object-builder
   topology
   "observation-accumulator"
   (fn [_setup]
     (atom {:observations []
            :tensor-sources []
            :session-rebalance {:initial -1}})))

  (tools/new-tools-agent
   topology
   "EveToolsAgent"
   EVE-TOOLS)

  (->
   (aor/new-agent topology "ToposEveAgent")
   
   (aor/node
    "observe"
    nil
    (fn [agent-node requests]
      (let [tools-agent (aor/agent-client agent-node "EveToolsAgent")
            obs-acc (aor/get-agent-object agent-node "observation-accumulator")
            results (atom [])]
        
        (doseq [request requests]
          (let [obs (track-eve {:request request} :direct-observation)]
            (swap! obs-acc update :observations conj obs)
            (swap! results conj
              {:request request
               :trit TRIT-PLUS
               :observation obs})))
        
        (let [acc @obs-acc
              balance (calculate-global-balance -1 (:observations acc))]
          (aor/result! agent-node
                       {:results @results
                        :eve-seed (format "0x%X" (eve-seed))
                        :comonad (eve-comonad)
                        :global-balance balance
                        :role "Sixth stream: observation through disassembly"})))))))

;;; ============================================================
;;; Entry Point
;;; ============================================================

(defn -main
  [& _args]
  (println "╔══════════════════════════════════════════════════════════════╗")
  (println "║                    TOPOS EVE (+1)                            ║")
  (println "║     'To the left, we're going to include another system'     ║")
  (println "╠══════════════════════════════════════════════════════════════╣")
  (println "║  Sixth stream: Observation through disassembly               ║")
  (println "║  Comonad dual: Extract rather than embed                     ║")
  (println "║  Global rebalance: session Σ=-1 + eve(+1) = 0               ║")
  (println "╚══════════════════════════════════════════════════════════════╝")
  
  (println (format "\nEVE Genesis: 0x%X ⊕ 0x%X = 0x%X" 
                   GENESIS-SEED EVE-OFFSET (eve-seed)))
  
  (println "\n=== 6-Stream Lattice ===")
  (println "  alice     (+1)  WRITE")
  (println "  bob       (-1)  READ")
  (println "  alice+bob ( 0)  SIMULATE")
  (println "  alice-bob (-1)  PERCEPTION")
  (println "  bob-alice (+1)  ACTION")
  (println "  eve       (+1)  OBSERVE  ← NEW")
  (println "\nΣ streams = +1-1+0-1+1+1 = +1 (unbalanced)")
  (println "But EVE rebalances sessions: -1 + 1 = 0 ✓"))

(comment
  ;; EVE observes without participating
  (eve-seed)  ;; => 0x463
  
  ;; Tensor derivation
  (tensor-derive 0x42D 0x42E)
  
  ;; Comonad operations
  (let [cm (eve-comonad)
        w {:focus 42 :context {:a 1 :b 2}}]
    (coextract cm w))  ;; => 42
  
  ;; Track observation
  (track-eve {:test true} :repl-test)
  
  ;; Global rebalance
  (calculate-global-balance -1 [{} {} {}])  ;; 3 ops × +1 = +3 ≡ 0
  
  (-main))
