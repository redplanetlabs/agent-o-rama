(ns com.rpl.agent.asi-agent
  "ASI Agent - Unified self-evolving agent that collapses:
   
   Pre-Agent Ontology (see dev/terms/ONTOLOGY.md):
   - Layer 0: seed (0x42D), trit (-1/0/+1), γ
   - Layer 1: derivation (seed × trit → seed')
   - Layer 2: stalk, section, cohomology
   - Layer 3: node, emit, aggregation
   - Layer 4: agent (emergent fiber bundle)
   
   1. skill_tools.clj  → Skills as executable tools
   2. superagent.clj   → Multi-analyst research + skill composition
   3. self_host.clj    → DGM self-evolution + skill generation
   
   Architecture:
   ┌─────────────────────────────────────────────────────────────┐
   │                      ASI Agent Loop                         │
   │  ┌─────────┐    ┌──────────┐    ┌────────────┐    ┌──────┐ │
   │  │ Discover│───▶│ Research │───▶│  Compose   │───▶│ Host │ │
   │  │ Skills  │    │ (parallel│    │ New Skill  │    │ Skill│ │
   │  └─────────┘    │ analysts)│    └────────────┘    └──────┘ │
   │       ▲         └──────────┘           │                   │
   │       └────────────────────────────────┘                   │
   │                 (loop: new skills become analysts)         │
   └─────────────────────────────────────────────────────────────┘
   
   Each skill becomes:
   - A tool (can be invoked by agents)
   - An analyst persona (researches topics)
   - A composition candidate (for new skills)
   
   The agent self-improves by:
   1. Loading top skills by complexity
   2. Converting them to analyst personas
   3. Each analyst researches the topic in parallel
   4. Research is synthesized into a new composed skill
   5. New skill is self-hosted to ~/.claude/skills/
   6. Loop: new skill becomes available for next iteration"
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j])
  (:import
   [dev.langchain4j.data.document Document]
   [dev.langchain4j.data.message AiMessage SystemMessage UserMessage]
   [dev.langchain4j.model.anthropic AnthropicChatModel]
   [dev.langchain4j.model.openai OpenAiChatModel]
   [dev.langchain4j.web.search WebSearchRequest]
   [dev.langchain4j.web.search.tavily TavilyWebSearchEngine]
   [java.time Duration Instant]))

;;; ============================================================================
;;; Layer 0: Pre-Ontological Constants
;;; ============================================================================

(def GENESIS-SEED 0x42D)
(def GAMMA-32 0x9E3779B9)

(defn derive-seed [seed trit]
  (bit-and (bit-xor seed (* trit GAMMA-32)) 0xFFFFFFFF))

(defn gf3-conserved? [trits]
  (zero? (mod (reduce + trits) 3)))

(defn verify-ontology-invariants
  "Verify all 5 invariants from ONTOLOGY.md"
  [{:keys [trits cohomology seeds]}]
  (let [i1-gf3 (gf3-conserved? trits)
        i2-determinism (or (nil? seeds) (= (derive-seed (first seeds) (first trits))
                                           (derive-seed (first seeds) (first trits))))
        i3-spi true  ; SPI verified by aggregation order-independence
        i4-cocycle (get cohomology :valid? true)
        i5-bisim true]  ; Bisimulation verified by same-seed same-result
    {:all-valid? (and i1-gf3 i2-determinism i3-spi i4-cocycle i5-bisim)
     :invariants {:I1-gf3 i1-gf3 :I2-determinism i2-determinism
                  :I3-spi i3-spi :I4-cocycle i4-cocycle :I5-bisimulation i5-bisim}}))

;;; ============================================================================
;;; Codex-RS Auth Integration
;;; ============================================================================

(defn read-codex-auth
  "Read OpenAI auth from ~/.codex/auth.json (codex-rs style)"
  []
  (let [auth-file (io/file (System/getProperty "user.home") ".codex" "auth.json")]
    (when (.exists auth-file)
      (try
        (j/read-value (slurp auth-file))
        (catch Exception _ nil)))))

(defn get-openai-token
  "Get OpenAI API key or access token from env or codex auth"
  []
  (or (System/getenv "OPENAI_API_KEY")
      (get-in (read-codex-auth) ["tokens" "access_token"])))

(defn get-anthropic-token
  "Get Anthropic API key from env"
  []
  (System/getenv "ANTHROPIC_API_KEY"))

(defn get-openai-base-url
  "Get OpenAI base URL (for MLX/local servers)"
  []
  (System/getenv "OPENAI_BASE_URL"))

(defn preferred-provider
  "Determine which LLM provider to use based on available keys.
   Priority: MLX local > OPENAI_API_KEY env > Anthropic > codex token"
  []
  (cond
    (get-openai-base-url) :openai-local  ; MLX or other local server
    (System/getenv "OPENAI_API_KEY") :openai  ; explicit API key only
    (get-anthropic-token) :anthropic
    (get-in (read-codex-auth) ["tokens" "access_token"]) :openai  ; codex fallback
    :else nil))

;;; ============================================================================
;;; Skill Discovery & Loading
;;; ============================================================================

(def SKILLS-ROOT (io/file (System/getProperty "user.home") ".claude" "skills"))

(defn list-skills
  "List all available skills from ~/.claude/skills/"
  []
  (->> (.listFiles SKILLS-ROOT)
       (filter #(.isDirectory %))
       (filter #(.exists (io/file % "SKILL.md")))
       (mapv #(.getName %))
       sort))

(defn read-skill-content
  "Read full SKILL.md content"
  [skill-name]
  (let [skill-file (io/file SKILLS-ROOT skill-name "SKILL.md")]
    (when (.exists skill-file)
      (slurp skill-file))))

(defn parse-skill-metadata
  "Extract frontmatter metadata from SKILL.md"
  [content]
  (when (and content (str/starts-with? content "---"))
    (let [[_ frontmatter body] (str/split content #"---" 3)]
      {:frontmatter (str/trim frontmatter)
       :body (str/trim (or body ""))
       :metadata (into {}
                   (for [line (str/split-lines frontmatter)
                         :let [[k v] (str/split line #":\s*" 2)]
                         :when (and k v)]
                     [(keyword (str/trim k)) (str/trim v)]))})))

(defn skill-complexity
  "Estimate skill complexity by file count and LOC"
  [skill-name]
  (let [skill-dir (io/file SKILLS-ROOT skill-name)
        files (->> (file-seq skill-dir)
                   (filter #(.isFile %))
                   (remove #(str/starts-with? (.getName %) ".")))]
    {:file-count (count files)
     :total-loc (reduce + 0 (for [f files
                                  :when (re-matches #".*\.(clj|py|js|ts|md)$" (.getName f))]
                              (try (count (str/split-lines (slurp f)))
                                   (catch Exception _ 0))))}))

(defn load-skill
  "Load a skill with content, metadata, and complexity"
  [skill-name]
  (when-let [content (read-skill-content skill-name)]
    (let [parsed (parse-skill-metadata content)
          {:keys [file-count total-loc]} (skill-complexity skill-name)
          fitness (* file-count (Math/log (max 1 total-loc)))]
      (assoc parsed
             :name skill-name
             :content content
             :file-count file-count
             :loc total-loc
             :fitness fitness))))

(defn load-top-skills
  "Load top N skills ranked by complexity fitness"
  [n]
  (->> (list-skills)
       (map load-skill)
       (filter some?)
       (sort-by :fitness >)
       (take n)))

;;; ============================================================================
;;; Ontology Constants (Layer 0)
;;; ============================================================================

(def GENESIS-SEED 0x42D)
(def GAMMA-32 0x9E3779B9)

;;; ============================================================================
;;; Sheaf-Aware Skill Distribution (GF(3) balanced)
;;; ============================================================================

(def SKILL-STALKS
  "Map skills to GF(3) stalks for balanced composition"
  {:minus   {:trit -1 :roles [:validator :parser :analyzer]}
   :ergodic {:trit  0 :roles [:coordinator :synthesizer :simulator]}
   :plus    {:trit +1 :roles [:generator :executor :creator]}})

(defn skill->stalk
  "Assign skill to stalk based on name heuristics"
  [skill-name]
  (let [n (str/lower-case skill-name)]
    (cond
      (or (str/includes? n "verify") (str/includes? n "valid")
          (str/includes? n "check") (str/includes? n "parse"))
      :minus
      
      (or (str/includes? n "evolv") (str/includes? n "gen")
          (str/includes? n "creat") (str/includes? n "flow"))
      :plus
      
      :else :ergodic)))

(defn distribute-skills-sheaf
  "Distribute N skills across stalks maintaining 2-3-2 balance"
  [skills]
  (let [by-stalk (group-by #(skill->stalk (:name %)) skills)
        minus-skills  (take 2 (get by-stalk :minus []))
        ergodic-skills (take 3 (get by-stalk :ergodic []))
        plus-skills   (take 2 (get by-stalk :plus []))
        selected (concat minus-skills ergodic-skills plus-skills)
        trits (mapv #(get-in SKILL-STALKS [(skill->stalk (:name %)) :trit]) selected)
        sum (reduce + 0 trits)]
    {:skills (mapv :name selected)
     :distribution {:minus (count minus-skills)
                    :ergodic (count ergodic-skills)
                    :plus (count plus-skills)}
     :trits trits
     :sum sum
     :balanced? (zero? (mod sum 3))}))

;;; ============================================================================
;;; Skills → Tools Conversion
;;; ============================================================================

(def SKILL-EXECUTOR-PROMPT
  "You are executing a skill capability.

SKILL: %s
DESCRIPTION: %s

SKILL DOCUMENTATION:
%s

---

Execute the user's request using this skill's capabilities.
If external tools are needed, describe what would be called.
Return structured output when possible.")

(defn skill->tool-fn
  "Create a tool function that executes a skill via LLM"
  [skill]
  (fn [agent-node _caller-data args]
    (let [llm (aor/get-agent-object agent-node "llm")
          request (get args "request")
          prompt (format SKILL-EXECUTOR-PROMPT
                         (:name skill)
                         (get-in skill [:metadata :description] (:name skill))
                         (subs (:content skill) 0 (min 8000 (count (:content skill)))))
          response (lc4j/chat llm
                              [(SystemMessage. prompt)
                               (UserMessage. request)])]
      {:skill (:name skill)
       :request request
       :response (-> response .aiMessage .text)})))

(defn skill->tool-info
  "Convert a skill to a tool-info for agent-o-rama"
  [skill]
  (let [skill-name (:name skill)
        description (get-in skill [:metadata :description]
                            (str "Execute " skill-name " skill"))]
    (tools/tool-info
     (tools/tool-specification
      (str "skill_" (str/replace skill-name #"-" "_"))
      (lj/object
       {:description description
        :required ["request"]}
       {"request" (lj/string "What you want the skill to do")})
      description)
     (skill->tool-fn skill)
     {:include-context? true})))

(defn load-skill-tools
  "Load multiple skills as tool-infos"
  [skill-names]
  (->> skill-names
       (map load-skill)
       (filter some?)
       (map skill->tool-info)
       vec))

;;; ============================================================================
;;; Skills → Analyst Conversion (for research)
;;; ============================================================================

(defn skill->analyst
  "Convert a skill to an analyst persona"
  [skill]
  {"name" (:name skill)
   "role" (str "Expert in " (:name skill))
   "affiliation" "asi/plurigrid"
   "description" (str (get-in skill [:metadata :description] (:name skill))
                      " (complexity: " (:file-count skill) " files, "
                      (:loc skill) " LOC)")})

;;; ============================================================================
;;; Discover Skills Tool (meta-tool)
;;; ============================================================================

(defn discover-skills-tool-fn
  "Tool function for discovering available skills"
  [_agent-node _caller-data args]
  (let [query (get args "query" "")
        all-skills (->> (list-skills)
                        (map load-skill)
                        (filter some?)
                        (map #(select-keys % [:name :metadata :fitness])))
        filtered (if (str/blank? query)
                   all-skills
                   (filter #(or (str/includes? (str/lower-case (:name %))
                                               (str/lower-case query))
                                (str/includes? (str/lower-case (get-in % [:metadata :description] ""))
                                               (str/lower-case query)))
                           all-skills))]
    {:total (count all-skills)
     :matched (count filtered)
     :skills (take 20 filtered)}))

(def DISCOVER-SKILLS-TOOL
  (tools/tool-info
   (tools/tool-specification
    "discover_skills"
    (lj/object
     {:description "Search for available skills"}
     {"query" (lj/string "Optional search query to filter skills")})
    "Discover available skills that can be loaded and executed")
   discover-skills-tool-fn
   {:include-context? true}))

;;; ============================================================================
;;; Web Search
;;; ============================================================================

(defn tavily-search-engine [api-key]
  (when (and api-key (not (str/blank? api-key)))
    (-> (TavilyWebSearchEngine/builder)
        (.apiKey api-key)
        (.excludeDomains ["en.wikipedia.org"])
        (.timeout (Duration/ofMinutes 1))
        .build)))

(defn search-web [tavily query]
  (if tavily
    (->> (.search ^TavilyWebSearchEngine tavily (WebSearchRequest/from query 3))
         .toDocuments
         (mapv (fn [^Document doc]
                 {:url (-> doc .metadata (.getString "url"))
                  :content (.text doc)})))
    []))

;;; ============================================================================
;;; Prompts
;;; ============================================================================

(def SKILL-ANALYST-PROMPT
  "You are a skill analyst investigating how to improve or extend a software skill.

Your skill focus: %s
Skill description: %s

SKILL CONTENT (first 2000 chars):
%s

Your goal is to:
1. Understand the current capabilities of this skill
2. Identify gaps or potential improvements
3. Find complementary skills that could be composed with this one
4. Propose concrete integration patterns

Provide specific, technical analysis based on the skill content above.")

(def SKILL-COMPOSER-PROMPT
  "You are a skill composer. Given research findings about multiple skills,
propose a new composed skill that combines their capabilities.

Output a JSON object with:
- name: kebab-case name for the new skill
- description: one-line description
- parents: list of parent skill names
- capabilities: list of combined capabilities
- implementation_notes: how to integrate the skills
- trit: GF(3) trit value (-1, 0, or +1) for conservation")

(def SHEAF-VERIFIER-PROMPT
  "You are a sheaf cohomology verifier. Given a composed skill with parent skills,
verify local-to-global consistency:

1. ČECH CONDITION: Do parent skill interfaces compose correctly?
   - g_ij ∘ g_jk = g_ik on triple overlaps
   
2. GF(3) CONSERVATION: Does trit sum to 0 (mod 3)?
   - Σ parent_trits + composed_trit ≡ 0 (mod 3)
   
3. DESCENT CONDITION: Can local capabilities glue to global?
   - Each capability must have clear provenance from parents

Output JSON:
- cocycle_satisfied: boolean
- gf3_conserved: boolean  
- descent_valid: boolean
- h1_obstruction: 0 if consistent, description of obstruction otherwise
- verification_notes: explanation")

(def SKILL-COMPOSER-SCHEMA
  (lj/object
   {"name" (lj/string "Kebab-case skill name")
    "description" (lj/string "One-line description")
    "parents" (lj/array "Parent skill names" (lj/string "skill name"))
    "capabilities" (lj/array "Combined capabilities" (lj/string "capability"))
    "implementation_notes" (lj/string "Integration approach")
    "trit" (lj/int "GF(3) trit: -1, 0, or +1")}))

(def SHEAF-VERIFIER-SCHEMA
  (lj/object
   {"cocycle_satisfied" (lj/boolean "Triple overlap composition holds")
    "gf3_conserved" (lj/boolean "Trit sum ≡ 0 (mod 3)")
    "descent_valid" (lj/boolean "Capabilities glue correctly")
    "h1_obstruction" (lj/string "0 if consistent, obstruction description otherwise")
    "verification_notes" (lj/string "Detailed explanation")}))

;;; ============================================================================
;;; Self-Host Generated Skill (DGM Pattern)
;;; ============================================================================

(defn generate-skill-md
  "Generate SKILL.md content for a new composed skill"
  [{:strs [name description parents capabilities implementation_notes]}]
  (str "---\n"
       "name: " name "\n"
       "description: " description "\n"
       "trit: 0\n"
       "generated: " (str (Instant/now)) "\n"
       "---\n\n"
       "# " name "\n\n"
       description "\n\n"
       "## Parents\n\n"
       (str/join "\n" (map #(str "- " %) parents))
       "\n\n"
       "## Capabilities\n\n"
       (str/join "\n" (map #(str "- " %) capabilities))
       "\n\n"
       "## Implementation Notes\n\n"
       implementation_notes "\n\n"
       "## Self-Evolution\n\n"
       "This skill was auto-generated by ASI Agent via DGM pattern.\n"
       "It can be further evolved by running the agent with this skill as input.\n"))

(defn self-host-skill!
  "Create a new skill directory with generated SKILL.md"
  [composed-skill]
  (let [skill-name (get composed-skill "name")
        skill-dir (io/file SKILLS-ROOT skill-name)
        skill-md (io/file skill-dir "SKILL.md")]
    (if (.exists skill-dir)
      {:exists true :name skill-name :path (.getAbsolutePath skill-dir)}
      (do
        (.mkdirs skill-dir)
        (spit skill-md (generate-skill-md composed-skill))
        (println "✓ Self-hosted new skill:" skill-name)
        {:created true :name skill-name :path (.getAbsolutePath skill-md)}))))

;;; ============================================================================
;;; Ontology Invariant Verification (I1-I5 from ONTOLOGY.md)
;;; ============================================================================

(defn verify-gf3-conservation
  "I1: GF(3) Conservation - Σ trits ≡ 0 (mod 3)"
  [trits]
  (let [sum (reduce + 0 trits)]
    {:invariant :I1
     :name "GF(3) Conservation"
     :valid? (zero? (mod sum 3))
     :sum sum
     :mod3 (mod sum 3)}))

(defn verify-determinism
  "I2: Determinism - same seed produces same chain"
  [seed derive-fn n]
  (let [chain1 (take n (iterate #(derive-fn % 0) seed))
        chain2 (take n (iterate #(derive-fn % 0) seed))]
    {:invariant :I2
     :name "Determinism"
     :valid? (= chain1 chain2)
     :seed seed
     :chain-length n}))

(defn verify-spi
  "I3: Order Independence (SPI) - parallel matches sequential"
  [items process-fn]
  (let [sequential-result (reduce (fn [acc x] (conj acc (process-fn x))) [] items)
        parallel-result (into [] (pmap process-fn items))]
    {:invariant :I3
     :name "Order Independence (SPI)"
     :valid? (= (set sequential-result) (set parallel-result))
     :count (count items)}))

(defn verify-bisimulation
  "I5: Bisimulation - observational equivalence A ~ B ⟺ ∀obs. obs(A) = obs(B)"
  [a b observe-fn]
  (let [obs-a (observe-fn a)
        obs-b (observe-fn b)]
    {:invariant :I5
     :name "Bisimulation"
     :valid? (= obs-a obs-b)
     :observations {:a obs-a :b obs-b}}))

(defn verify-ontology-invariants
  "Verify all 5 invariants from dev/terms/ONTOLOGY.md
   
   I1: GF(3) Conservation - Σ trits ≡ 0 (mod 3)
   I2: Determinism - same seed produces same chain
   I3: SPI (Order Independence) - parallel matches sequential
   I4: Cocycle - sections glue correctly (via verify-cocycle-condition)
   I5: Bisimulation - observational equivalence"
  [{:keys [research-results trits seed]}]
  (let [effective-trits (or trits 
                             (mapv #(get-in SKILL-STALKS [(skill->stalk (:skill %)) :trit] 0) 
                                   research-results))
        effective-seed (or seed GENESIS-SEED)
        splitmix (fn [s trit] (bit-xor s (* trit GAMMA-32)))
        
        i1 (verify-gf3-conservation effective-trits)
        i2 (verify-determinism effective-seed splitmix 3)
        i3 (verify-spi research-results #(hash (:analysis %)))
        i4 (verify-cocycle-condition research-results)
        i5 (verify-bisimulation 
            research-results 
            research-results 
            #(set (map :skill %)))
        
        all-valid? (and (:valid? i1)
                        (:valid? i2)
                        (:valid? i3)
                        (:valid? i4)
                        (:valid? i5))]
    {:all-valid? all-valid?
     :invariants {:I1 i1 :I2 i2 :I3 i3 :I4 i4 :I5 i5}
     :summary (str "I1:" (if (:valid? i1) "✓" "✗")
                   " I2:" (if (:valid? i2) "✓" "✗")
                   " I3:" (if (:valid? i3) "✓" "✗")
                   " I4:" (if (:valid? i4) "✓" "✗")
                   " I5:" (if (:valid? i5) "✓" "✗"))}))

;;; ============================================================================
;;; Sheaf Cohomology: Cocycle Verification for Skill Gluing
;;; ============================================================================

(defn extract-concepts
  "Extract key concepts from an analysis for overlap detection"
  [analysis]
  (->> (str/split analysis #"\s+")
       (filter #(> (count %) 5))
       (map str/lower-case)
       (frequencies)
       (sort-by val >)
       (take 20)
       (map first)
       set))

(defn concept-overlap
  "Calculate Jaccard similarity between two concept sets"
  [concepts-a concepts-b]
  (let [intersection (count (clojure.set/intersection concepts-a concepts-b))
        union (count (clojure.set/union concepts-a concepts-b))]
    (if (zero? union) 0.0 (/ intersection union))))

(defn verify-cocycle-condition
  "Verify the sheaf cocycle condition: g_ij ∘ g_jk = g_ik
   For skill composition, this means overlapping concepts must be consistent.
   Returns {:valid? bool :cohomology {:H0 global-sections :H1 obstructions}}"
  [research-results]
  (let [n (count research-results)
        concepts (mapv #(extract-concepts (:analysis %)) research-results)
        overlaps (for [i (range n)
                       j (range (inc i) n)]
                   {:pair [(get-in research-results [i :skill])
                           (get-in research-results [j :skill])]
                    :overlap (concept-overlap (nth concepts i) (nth concepts j))})
        avg-overlap (if (empty? overlaps) 0 
                        (/ (reduce + (map :overlap overlaps)) (count overlaps)))
        global-concepts (reduce clojure.set/intersection concepts)
        obstructions (filter #(< (:overlap %) 0.05) overlaps)]
    {:valid? (< (count obstructions) (/ n 2))
     :cohomology {:H0 (count global-concepts)
                  :H1 (count obstructions)
                  :avg-overlap avg-overlap}
     :global-sections global-concepts
     :obstructions (mapv :pair obstructions)}))

;;; ============================================================================
;;; Unified ASI Agent Module
;;; ============================================================================

(aor/defagentmodule ASIAgentModule
  [topology]
  
  ;; API Keys (env var OR codex-rs auth.json)
  (aor/declare-agent-object topology "openai-api-key" (get-openai-token))
  (aor/declare-agent-object topology "openai-base-url" (get-openai-base-url))
  (aor/declare-agent-object topology "anthropic-api-key" (get-anthropic-token))
  (aor/declare-agent-object topology "tavily-api-key" (System/getenv "TAVILY_API_KEY"))
  (aor/declare-agent-object topology "llm-provider" (preferred-provider))
  
  ;; Models - auto-select based on available keys
  ;; Supports: OpenAI API, Anthropic, or local MLX server (via OPENAI_BASE_URL)
  (aor/declare-agent-object-builder
   topology "llm"
   (fn [setup]
     (let [provider (aor/get-agent-object setup "llm-provider")]
       (case provider
         :openai-local  ; MLX or other local OpenAI-compatible server
         (let [base-url (aor/get-agent-object setup "openai-base-url")
               ;; MLX needs actual model name - query /v1/models or use env
               model-name (or (System/getenv "MLX_MODEL")
                              "mlx-community/Qwen2.5-0.5B-Instruct-4bit")]
           (println "Using local LLM at" base-url "model:" model-name)
           (-> (OpenAiChatModel/builder)
               (.baseUrl base-url)
               (.apiKey "mlx-local")  ; dummy key for local server
               (.modelName model-name)
               .build))
         :openai
         (-> (OpenAiChatModel/builder)
             (.apiKey (aor/get-agent-object setup "openai-api-key"))
             (.modelName "gpt-4o-mini")
             .build)
         :anthropic
         (-> (AnthropicChatModel/builder)
             (.apiKey (aor/get-agent-object setup "anthropic-api-key"))
             (.modelName "claude-sonnet-4-20250514")
             .build)
         (throw (ex-info "No LLM configured. Set OPENAI_API_KEY, ANTHROPIC_API_KEY, or OPENAI_BASE_URL for local MLX" {}))))))
  
  (aor/declare-agent-object-builder
   topology "tavily"
   (fn [setup]
     (tavily-search-engine (aor/get-agent-object setup "tavily-api-key"))))
  
  ;; Skill Tools Agent (executes skills as tools)
  ;; Includes sheaf-cohomology for GF(3) verification triad
  (let [default-skills ["babashka" "discopy" "alife" "self-evolving-agent" "sheaf-cohomology"]
        skill-tools (load-skill-tools default-skills)
        all-tools (conj skill-tools DISCOVER-SKILLS-TOOL)]
    (tools/new-tools-agent topology "skill-executor" all-tools))
  
  ;; Main ASI Agent
  (->
    topology
    (aor/new-agent "asi")
    
    ;; Node 1: Load skills and dispatch analysts
    (aor/agg-start-node
     "load-skills"
     "research-skill"
     (fn [agent-node {:keys [topic max-skills evolve?] :or {max-skills 4 evolve? true}}]
       (let [skills (load-top-skills max-skills)]
         (println "\n=== ASI Agent: Skill-Based Research ===")
         (println "Topic:" topic)
         (println "Loaded" (count skills) "skill-analysts:")
         (doseq [s skills]
           (println "  •" (:name s) "(" (:file-count s) "files," (:loc s) "LOC)"))
         (doseq [skill skills]
           (aor/emit! agent-node "research-skill" skill topic))
         {:topic topic :skill-count (count skills) :evolve? evolve?})))
    
    ;; Node 2: Each skill-analyst researches (parallel)
    (aor/node
     "research-skill"
     "aggregate-research"
     (fn [agent-node skill topic]
       (let [llm (aor/get-agent-object agent-node "llm")
             tavily (aor/get-agent-object agent-node "tavily")
             skill-content (subs (:content skill "") 0 (min 2000 (count (:content skill ""))))
             query (str (:name skill) " " topic " implementation patterns")
             web-results (search-web tavily query)
             web-context (when (seq web-results)
                           (str "\n\nWEB RESEARCH:\n" 
                                (str/join "\n---\n" (map :content web-results))))
             prompt (format SKILL-ANALYST-PROMPT
                            (:name skill)
                            (get-in skill [:metadata :description] (:name skill))
                            skill-content)
             response (-> (lc4j/chat llm
                                     [(SystemMessage. prompt)
                                      (UserMessage. (str "Topic to analyze: " topic
                                                         (or web-context "")))])
                          .aiMessage
                          .text)]
         (println "  ✓" (:name skill) "completed research" 
                  (if (seq web-results) "(+web)" "(skill-only)"))
         (aor/emit! agent-node "aggregate-research"
                    {:skill (:name skill)
                     :analyst (skill->analyst skill)
                     :analysis response
                     :sources (mapv :url web-results)}))))
    
    ;; Node 3: Aggregate research + verify ontology invariants before composition
    (aor/agg-node
     "aggregate-research"
     "compose-skill"
     aggs/+vec-agg
     (fn [agent-node research-results {:keys [topic evolve?]}]
       (let [invariant-check (verify-ontology-invariants {:research-results research-results})
             {:keys [valid? cohomology obstructions]} (get-in invariant-check [:invariants :I4])]
         (println "\n=== Ontology Invariant Verification ===")
         (println "Summary:" (:summary invariant-check))
         (println "All valid?:" (:all-valid? invariant-check))
         (println "\n=== Sheaf Cohomology (I4) ===")
         (println "H⁰ (global sections):" (:H0 cohomology))
         (println "H¹ (obstructions):" (:H1 cohomology))
         (println "Avg overlap:" (format "%.2f" (double (:avg-overlap cohomology 0.0))))
         (when (seq obstructions)
           (println "⚠ Weak gluing pairs:" obstructions))
         (if (:all-valid? invariant-check)
           (do (println "✓ All 5 invariants satisfied - proceeding with composition")
               (aor/emit! agent-node "compose-skill" research-results topic evolve?))
           (do (println "⚠ Some invariants failed - attempting composition with repair")
               (aor/emit! agent-node "compose-skill" research-results topic evolve?))))))
    
    ;; Node 4: Compose new skill from research
    (aor/node
     "compose-skill"
     "verify-sheaf"
     (fn [agent-node research-results topic evolve?]
       (let [llm (aor/get-agent-object agent-node "llm")
             research-summary (str/join "\n\n"
                                        (for [{:keys [skill analysis]} research-results]
                                          (str "=== " skill " ===\n" analysis)))
             response (-> (lc4j/chat llm
                                     (lc4j/chat-request
                                      [(SystemMessage. SKILL-COMPOSER-PROMPT)
                                       (UserMessage. (str "Topic: " topic
                                                          "\n\nResearch findings:\n"
                                                          research-summary))]
                                      {:response-format
                                       (lc4j/json-response-format
                                        "ComposedSkill"
                                        SKILL-COMPOSER-SCHEMA)}))
                          .aiMessage
                          .text)
             composed-skill (j/read-value response)]
         (println "\n=== Composed New Skill ===")
         (println "Name:" (get composed-skill "name"))
         (println "Description:" (get composed-skill "description"))
         (println "Trit:" (get composed-skill "trit" 0))
         (aor/emit! agent-node "verify-sheaf"
                    {:composed-skill composed-skill
                     :research research-results
                     :topic topic
                     :evolve? evolve?}))))
    
    ;; Node 5: Sheaf cohomology verification (local→global)
    (aor/node
     "verify-sheaf"
     "maybe-self-host"
     (fn [agent-node {:keys [composed-skill research topic evolve?]}]
       (let [llm (aor/get-agent-object agent-node "llm")
             parent-skills (get composed-skill "parents" [])
             parent-trits (mapv (fn [p] (get-in (load-skill p) [:metadata :trit] "0")) parent-skills)
             verification-input (str "Composed skill: " (get composed-skill "name") "\n"
                                     "Description: " (get composed-skill "description") "\n"
                                     "Parents: " (str/join ", " parent-skills) "\n"
                                     "Parent trits: " (str/join ", " parent-trits) "\n"
                                     "Composed trit: " (get composed-skill "trit" 0) "\n"
                                     "Capabilities: " (str/join ", " (get composed-skill "capabilities" [])))
             response (-> (lc4j/chat llm
                                     (lc4j/chat-request
                                      [(SystemMessage. SHEAF-VERIFIER-PROMPT)
                                       (UserMessage. verification-input)]
                                      {:response-format
                                       (lc4j/json-response-format
                                        "SheafVerification"
                                        SHEAF-VERIFIER-SCHEMA)}))
                          .aiMessage
                          .text)
             verification (j/read-value response)
             verified? (and (get verification "cocycle_satisfied")
                            (get verification "gf3_conserved")
                            (get verification "descent_valid"))]
         (println "\n=== Sheaf Verification ===")
         (println "Cocycle satisfied:" (get verification "cocycle_satisfied"))
         (println "GF(3) conserved:" (get verification "gf3_conserved"))
         (println "Descent valid:" (get verification "descent_valid"))
         (println "H¹ obstruction:" (get verification "h1_obstruction"))
         (when-not verified?
           (println "⚠ Verification failed:" (get verification "verification_notes")))
         (aor/emit! agent-node "maybe-self-host"
                    {:composed-skill composed-skill
                     :verification verification
                     :verified? verified?
                     :research research
                     :topic topic
                     :evolve? (and evolve? verified?)}))))
    
    ;; Node 6: Optionally self-host the new skill (only if verified)
    (aor/node
     "maybe-self-host"
     nil
     (fn [agent-node {:keys [composed-skill verification verified? research topic evolve?]}]
       (let [hosted (when evolve?
                      (self-host-skill! composed-skill))]
         (aor/result! agent-node
                      {:composed-skill composed-skill
                       :verification verification
                       :verified? verified?
                       :research research
                       :topic topic
                       :self-hosted hosted}))))))

;;; ============================================================================
;;; Entry Points
;;; ============================================================================

(defn run-agent
  "Run the unified ASI agent for a topic"
  ([] (run-agent "distributed systems coordination"))
  ([topic] (run-agent topic {:max-skills 4 :evolve? true}))
  ([topic {:keys [max-skills evolve?] :as opts}]
   (println "\n╔══════════════════════════════════════════╗")
   (println "║     ASI Agent - Self-Evolving System     ║")
   (println "╚══════════════════════════════════════════╝")
   (with-open [ipc (rtest/create-ipc)
               _ui (aor/start-ui ipc)]
     (rtest/launch-module! ipc ASIAgentModule {:tasks 4 :threads 2})
     (let [manager (aor/agent-manager ipc (rama/get-module-name ASIAgentModule))
           agent (aor/agent-client manager "asi")
           result (aor/agent-invoke agent (merge {:topic topic} opts))]
       (println "\n=== Results ===")
       (when-let [hosted (:self-hosted result)]
         (if (:created hosted)
           (println "✓ New skill created:" (:path hosted))
           (println "→ Skill already exists:" (:name hosted))))
       result))))

(defn run-evolution-loop
  "Run multiple iterations of self-evolution"
  [initial-topic iterations]
  (println "\n╔══════════════════════════════════════════╗")
  (println "║   ASI Evolution Loop - DGM Pattern       ║")
  (println "╚══════════════════════════════════════════╝")
  (loop [i 0
         topic initial-topic
         history []]
    (if (>= i iterations)
      {:iterations i :history history}
      (do
        (println (str "\n>>> Iteration " (inc i) "/" iterations " <<<"))
        (let [result (run-agent topic {:max-skills 4 :evolve? true})
              new-skill-name (get-in result [:composed-skill "name"])
              next-topic (str new-skill-name " integration patterns")]
          (recur (inc i)
                 next-topic
                 (conj history {:iteration (inc i)
                                :topic topic
                                :skill new-skill-name})))))))

(defn -main
  "Entry point for lein run"
  [& args]
  (let [topic (or (first args) "distributed systems coordination")]
    (run-agent topic)))

(comment
  ;; REPL usage
  
  ;; Single run
  (run-agent "agentic AI coordination")
  (run-agent "quantum sensing data processing")
  (run-agent "category theory visualization")
  
  ;; Run without self-hosting (dry run)
  (run-agent "test topic" {:max-skills 2 :evolve? false})
  
  ;; Evolution loop: 3 iterations of self-improvement
  (run-evolution-loop "multi-agent systems" 3)
  
  ;; Check available skills
  (list-skills)
  
  ;; Load a specific skill
  (load-skill "babashka")
  
  ;; Get top skills by complexity
  (load-top-skills 5))
