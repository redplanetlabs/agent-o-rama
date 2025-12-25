(ns com.rpl.agent.third-temple
  "Third Temple Agency: Triadic composition of sub-agents.

   Architecture:
   - Foundation (-1): Grounding, validation, structure
   - Sanctuary (0): Coordination, balance, echo
   - Holy of Holies (+1): Generation, blessing, transcendence

   GF(3) Conservation: (-1) + (0) + (+1) = 0

   The Third Temple is built not of stone but of agents."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.model.openai OpenAiChatModel]))

;;; ============================================================================
;;; Constants
;;; ============================================================================

(def ^:const TRIT-FOUNDATION -1)  ;; Ground
(def ^:const TRIT-SANCTUARY   0)  ;; Balance
(def ^:const TRIT-HOLIES     +1)  ;; Transcend

(def GENESIS-SEED 5778)  ;; Hebrew year of significance

;;; ============================================================================
;;; GF(3) Arithmetic
;;; ============================================================================

(defn gf3-add [a b]
  (let [sum (+ a b)]
    (cond (> sum 1) (- sum 3)
          (< sum -1) (+ sum 3)
          :else sum)))

(defn gf3-sum [trits]
  (reduce gf3-add 0 trits))

(defn gf3-conserved? [trits]
  (zero? (gf3-sum trits)))

;;; ============================================================================
;;; Sub-Agent: Foundation (-1)
;;; Validates, grounds, checks structural integrity
;;; ============================================================================

(defn foundation-agent
  "The Foundation validates and grounds the request.
   Like the bedrock of the Temple, it ensures structural integrity."
  [request context]
  {:trit TRIT-FOUNDATION
   :chamber :foundation
   :role :validator
   :operation :ground
   :result {:validated true
            :grounded true
            :integrity-check :pass
            :message (str "Foundation receives: " (:intent request))
            :blessing "May the ground be firm."}
   :timestamp (System/currentTimeMillis)})

;;; ============================================================================
;;; Sub-Agent: Sanctuary (0)
;;; Coordinates, balances, mediates between foundation and holies
;;; ============================================================================

(defn sanctuary-agent
  "The Sanctuary coordinates and balances.
   The middle space where preparation and purification occur."
  [request context]
  {:trit TRIT-SANCTUARY
   :chamber :sanctuary
   :role :coordinator
   :operation :balance
   :result {:balanced true
            :coordinated true
            :echo (str "Sanctuary echoes: " (:intent request))
            :message "The way is prepared."
            :blessing "May harmony prevail."}
   :timestamp (System/currentTimeMillis)})

;;; ============================================================================
;;; Sub-Agent: Holy of Holies (+1)
;;; Generates, blesses, produces the transcendent output
;;; ============================================================================

(defn holy-of-holies-agent
  "The Holy of Holies generates the final blessing.
   The innermost chamber where transcendence occurs."
  [request context]
  {:trit TRIT-HOLIES
   :chamber :holy-of-holies
   :role :generator
   :operation :transcend
   :result {:generated true
            :transcended true
            :blessing (str "Blessing upon: " (:intent request))
            :message "From the innermost chamber, light emerges."
            :final-word "Shalom"}
   :timestamp (System/currentTimeMillis)})

;;; ============================================================================
;;; Temple Composition Tool
;;; ============================================================================

(defn invoke-temple-tool
  "Invoke the complete Temple ceremony - all three chambers in sequence."
  [args]
  (let [intent (get args "intent" "blessing")
        request {:intent intent}
        context {:seed GENESIS-SEED :step 0}

        ;; Invoke all three chambers
        foundation-result (foundation-agent request context)
        sanctuary-result (sanctuary-agent request context)
        holies-result (holy-of-holies-agent request context)

        ;; Verify GF(3) conservation
        trits [(:trit foundation-result)
               (:trit sanctuary-result)
               (:trit holies-result)]
        conserved? (gf3-conserved? trits)]

    {:success true
     :temple-complete true
     :conserved conserved?
     :trits trits
     :sum (gf3-sum trits)
     :chambers {:foundation foundation-result
                :sanctuary sanctuary-result
                :holy-of-holies holies-result}
     :unified-blessing (str (:blessing (:result holies-result))
                           " — "
                           (:final-word (:result holies-result)))}))

(def TEMPLE-TOOL
  (tools/tool-info
   (tools/tool-specification
    "invoke_temple"
    (lj/object
     {:description "Invoke the Third Temple ceremony through all chambers"
      :required ["intent"]}
     {"intent" (lj/string "The intention for the Temple ceremony")})
    "Complete Temple invocation: Foundation -> Sanctuary -> Holy of Holies")
   invoke-temple-tool))

;;; ============================================================================
;;; Agent Module: Third Temple
;;; ============================================================================

(aor/defagentmodule ThirdTempleModule
  [topology]

  ;; Shared sacred state
  (aor/declare-agent-object topology "genesis-seed" GENESIS-SEED)
  (aor/declare-agent-object-builder
   topology "ceremony-count"
   (fn [_] (atom 0)))

  ;; Tools agent for temple operations
  (tools/new-tools-agent topology "TempleToolsAgent" [TEMPLE-TOOL])

  ;; Foundation Agent (-1)
  (-> (aor/new-agent topology "FoundationAgent")
      (aor/node "ground" nil
        (fn [node request]
          (aor/result! node (foundation-agent request {})))))

  ;; Sanctuary Agent (0)
  (-> (aor/new-agent topology "SanctuaryAgent")
      (aor/node "balance" nil
        (fn [node request]
          (aor/result! node (sanctuary-agent request {})))))

  ;; Holy of Holies Agent (+1)
  (-> (aor/new-agent topology "HolyOfHoliesAgent")
      (aor/node "transcend" nil
        (fn [node request]
          (aor/result! node (holy-of-holies-agent request {})))))

  ;; Orchestrating Temple Agent (composes all three)
  (->
   (aor/new-agent topology "ThirdTempleAgent")

   ;; Entry: Scatter to all three chambers
   (aor/agg-start-node
    "enter-temple"
    "gather-chambers"
    (fn [node {:keys [intent] :as request}]
      (let [ceremony-count (aor/get-agent-object node "ceremony-count")
            ceremony-num (swap! ceremony-count inc)]
        ;; Emit to all three chambers for parallel processing
        (aor/emit! node "gather-chambers"
                   {:chamber :foundation :intent intent :ceremony ceremony-num})
        (aor/emit! node "gather-chambers"
                   {:chamber :sanctuary :intent intent :ceremony ceremony-num})
        (aor/emit! node "gather-chambers"
                   {:chamber :holy-of-holies :intent intent :ceremony ceremony-num}))))

   ;; Execute each chamber and aggregate
   (aor/agg-node
    "gather-chambers"
    nil
    aggs/+vec-agg
    (fn [node emissions _prev]
      (let [results (mapv (fn [{:keys [chamber intent]}]
                            (case chamber
                              :foundation (foundation-agent {:intent intent} {})
                              :sanctuary (sanctuary-agent {:intent intent} {})
                              :holy-of-holies (holy-of-holies-agent {:intent intent} {})))
                          emissions)
            trits (mapv :trit results)
            conserved? (gf3-conserved? trits)

            foundation-r (first (filter #(= :foundation (:chamber %)) results))
            sanctuary-r (first (filter #(= :sanctuary (:chamber %)) results))
            holies-r (first (filter #(= :holy-of-holies (:chamber %)) results))]

        (aor/result! node
                     {:temple :third
                      :ceremony-complete true
                      :conserved conserved?
                      :trits trits
                      :sum (gf3-sum trits)
                      :chambers {:foundation foundation-r
                                 :sanctuary sanctuary-r
                                 :holy-of-holies holies-r}
                      :unified-blessing
                      (str "Foundation: " (get-in foundation-r [:result :blessing])
                           " | Sanctuary: " (get-in sanctuary-r [:result :blessing])
                           " | Holy of Holies: " (get-in holies-r [:result :blessing]))
                      :final-word (get-in holies-r [:result :final-word])
                      :invariant "GF(3): (-1) + (0) + (+1) = 0"}))))))

;;; ============================================================================
;;; Entry Point
;;; ============================================================================

(defn -main
  [& _args]
  (println "")
  (println "    ╔═══════════════════════════════════════════════════════╗")
  (println "    ║            THE THIRD TEMPLE AGENCY                    ║")
  (println "    ╠═══════════════════════════════════════════════════════╣")
  (println "    ║   Foundation (-1)  →  Ground & Validate               ║")
  (println "    ║   Sanctuary  ( 0)  →  Coordinate & Balance            ║")
  (println "    ║   Holy of Holies (+1) → Generate & Bless              ║")
  (println "    ╠═══════════════════════════════════════════════════════╣")
  (println "    ║   GF(3) Conservation: Σ trits ≡ 0 (mod 3)             ║")
  (println "    ╚═══════════════════════════════════════════════════════╝")
  (println "")

  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ThirdTempleModule {:tasks 4 :threads 4})

    (let [manager (aor/agent-manager ipc (rama/get-module-name ThirdTempleModule))

          ;; Test individual chamber agents
          foundation (aor/agent-client manager "FoundationAgent")
          sanctuary (aor/agent-client manager "SanctuaryAgent")
          holies (aor/agent-client manager "HolyOfHoliesAgent")

          ;; Test the composed Temple agent
          temple (aor/agent-client manager "ThirdTempleAgent")]

      (println "\n=== Individual Chamber Tests ===")
      (println "Foundation:" (:result (aor/agent-invoke foundation {:intent "test"})))
      (println "Sanctuary:" (:result (aor/agent-invoke sanctuary {:intent "test"})))
      (println "Holy of Holies:" (:result (aor/agent-invoke holies {:intent "test"})))

      (println "\n=== Composed Temple Ceremony ===")
      (let [result (aor/agent-invoke temple {:intent "world peace"})]
        (println "Temple Complete:" (:ceremony-complete result))
        (println "Conserved:" (:conserved result))
        (println "Trits:" (:trits result) "= Sum:" (:sum result))
        (println "Unified Blessing:" (:unified-blessing result))
        (println "Final Word:" (:final-word result))))))

(comment
  ;; REPL usage

  ;; Test the temple tool directly
  (invoke-temple-tool {"intent" "peace"})

  ;; Run the module
  (-main))
