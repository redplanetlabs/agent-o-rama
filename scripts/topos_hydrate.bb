#!/usr/bin/env bb
;; Hydrate sparse ~/.topos directories with scaffolding based on inferred purpose

(require '[babashka.fs :as fs]
         '[clojure.string :as str])

(def TOPOS (fs/expand-home "~/.topos"))

;;; ============================================================================
;;; Purpose inference from directory name
;;; ============================================================================

(def TEMPLATES
  {:gay-color
   {:desc "GF(3) deterministic color system"
    :files {"splitmix.clj" 
            "(ns gay.splitmix
  \"SplitMix64 → GF(3) trit generator\")

(defn splitmix64 [seed]
  (let [z (+ seed 0x9e3779b97f4a7c15)
        z (bit-xor z (unsigned-bit-shift-right z 30))
        z (* z 0xbf58476d1ce4e5b9)
        z (bit-xor z (unsigned-bit-shift-right z 27))
        z (* z 0x94d049bb133111eb)
        z (bit-xor z (unsigned-bit-shift-right z 31))]
    z))

(defn seed->trit [seed]
  (mod (splitmix64 seed) 3))

(defn trit->color [trit]
  (case trit
    0 \"#D82626\" ; red
    1 \"#26D826\" ; green  
    2 \"#2626D8\" ; blue
    \"#808080\"))"
            
            "palette.edn"
            "{:trits [0 1 2]
 :colors [\"#D82626\" \"#26D826\" \"#2626D8\"]
 :names [:red :green :blue]
 :gf3-sum 0}"}}
   
   :move-lang
   {:desc "Aptos Move smart contracts"
    :files {"Move.toml"
            "[package]
name = \"gay_move\"
version = \"0.1.0\"

[dependencies]
AptosFramework = { git = \"https://github.com/aptos-labs/aptos-core.git\", subdir = \"aptos-move/framework/aptos-framework\", rev = \"mainnet\" }

[addresses]
gay_move = \"_\""
            
            "sources/.gitkeep" ""}}
   
   :aptos
   {:desc "Aptos blockchain config"
    :files {"config.yaml"
            "profiles:
  default:
    network: mainnet
    private_key_path: ~/.aptos/key
"}}
   
   :localsend
   {:desc "LocalSend P2P transfer protocol"
    :files {"protocol.md"
            "# LocalSend Protocol

## Discovery
- Multicast UDP on port 53317
- JSON announcement with alias, deviceModel, deviceType

## Transfer
- HTTPS on announced port
- POST /api/localsend/v2/prepare-upload
- POST /api/localsend/v2/upload

## State Machine
IDLE → DISCOVERING → PREPARING → TRANSFERRING → COMPLETE
"
            
            "tags.scm"
            ";; Tree-sitter tags for LocalSend Dart code
(class_definition name: (identifier) @name) @definition.class
(method_signature name: (identifier) @name) @definition.method
"}}
   
   :treesitter
   {:desc "Tree-sitter parsers"
    :files {"README.md"
            "# Tree-sitter Parsers

Install parsers:
```bash
tree-sitter init-config
tree-sitter parse file.clj
```
"}}
   
   :nats
   {:desc "NATS messaging telemetry"
    :files {"streams.edn"
            "{:streams
 [{:name \"agent-events\"
   :subjects [\"agent.>\"]}
  {:name \"skill-invocations\"
   :subjects [\"skill.invoke.>\"]}]}"
            
            "nats.conf"
            "port: 4222
jetstream: enabled
"}}
   
   :ruler
   {:desc "Rule-based agent config"
    :files {"AGENTS.md"
            "# Agent Rules

## Standing Instructions
- Use web search when needed
- Create subagents via Task tool

## Skill Preferences
- Prioritize babashka for scripting
- Use tree-sitter for AST analysis
"}}
   
   :skills
   {:desc "Agent skills index"
    :files {"index.edn"
            "{:skills-root \"~/.claude/skills\"
 :top-by-complexity [:alife :slack-gif-creator :theme-factory]
 :categories {:category [\"discopy\" \"acsets\" \"sheaf-cohomology\"]
              :agent [\"self-evolving-agent\" \"godel-machine\"]
              :clojure [\"babashka\" \"cider-clojure\"]}}"}}
   
   :airdrop
   {:desc "Token airdrop scripts"
    :files {"recipients.csv"
            "address,amount,note
0x1234...,100,early-adopter
0x5678...,50,contributor
"
            "distribute.bb"
            "#!/usr/bin/env bb
;; Airdrop distribution script
(require '[babashka.fs :as fs])
(println \"Loading recipients...\")
;; TODO: implement aptos transfer
"}}
   
   :screenshot
   {:desc "Screen captures"
    :files {"metadata.edn"
            "{:format :png
 :timestamp-format \"yyyy-MM-dd_HH-mm-ss\"
 :prefix \"topos_\"}"}}
   
   :bin
   {:desc "Executables"
    :files {"run.sh"
            "#!/bin/bash
# ~/.topos/bin helper scripts
echo \"Available scripts:\"
ls -la ~/.topos/bin/*.bb 2>/dev/null || echo \"No .bb scripts yet\"
"}}
   
   :unknown
   {:desc "Unknown purpose"
    :files {}}})

(defn infer-type [dir-name]
  (cond
    (and (str/includes? dir-name "Gay") (str/includes? dir-name "Move")) :move-lang
    (str/includes? dir-name "Gay") :gay-color
    (str/includes? dir-name "Move") :move-lang
    (str/includes? dir-name "aptos") :aptos
    (str/includes? dir-name "localsend") :localsend
    (str/includes? dir-name "tree-sitter") :treesitter
    (str/includes? dir-name "nats") :nats
    (str/includes? dir-name "ruler") :ruler
    (str/includes? dir-name "skills") :skills
    (str/includes? dir-name "airdrop") :airdrop
    (str/includes? dir-name "screenshot") :screenshot
    (str/includes? dir-name "bin") :bin
    :else :unknown))

(defn hydrate-dir! [dir-path dry-run?]
  (let [dir-name (str (fs/file-name dir-path))
        type-key (infer-type dir-name)
        template (get TEMPLATES type-key)
        files (:files template {})]
    (when (seq files)
      (println (format "\n%s [%s]" dir-name (name type-key)))
      (println (format "  %s" (:desc template)))
      (doseq [[filename content] files]
        (let [target (fs/path dir-path filename)]
          (if (fs/exists? target)
            (println (format "  ✓ %s (exists)" filename))
            (if dry-run?
              (println (format "  → would create: %s" filename))
              (do
                (fs/create-dirs (fs/parent target))
                (spit (str target) content)
                (println (format "  + created: %s" filename))))))))))

(defn -main [& args]
  (let [dry-run? (some #{"--dry-run" "-n"} args)]
    (println "╔═══════════════════════════════════════════╗")
    (println "║   ~/.topos Hydration via babashka.fs      ║")
    (println "╚═══════════════════════════════════════════╝")
    (when dry-run?
      (println "\n[DRY RUN - no files will be created]"))
    
    (let [sparse-dirs (->> (fs/list-dir TOPOS)
                           (filter fs/directory?)
                           (filter #(<= (count (seq (fs/list-dir %))) 3))
                           (sort-by #(str (fs/file-name %))))]
      (doseq [d sparse-dirs]
        (hydrate-dir! d dry-run?))
      
      (println "\n─────────────────────────────────────────────")
      (println (format "Processed %d sparse directories" (count sparse-dirs)))
      (when dry-run?
        (println "Run without --dry-run to create files")))))

(apply -main *command-line-args*)
