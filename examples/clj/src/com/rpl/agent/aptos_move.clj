(ns com.rpl.agent.aptos-move
  "Move smart contract interaction agent using ReAct loop pattern.
   Implements tools for compiling, publishing, and calling Move modules on Aptos."
  (:require
   [clojure.data.json :as json]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.rama :as rama]
   [com.rpl.rama.path :as path]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.model.openai OpenAiChatModel]))

(def ^:private NETWORK-URLS
  {:devnet  "https://fullnode.devnet.aptoslabs.com/v1"
   :testnet "https://fullnode.testnet.aptoslabs.com/v1"
   :mainnet "https://fullnode.mainnet.aptoslabs.com/v1"})

(defn- run-aptos-cli
  [& args]
  (let [{:keys [exit out err]} (apply sh "aptos" args)]
    (if (zero? exit)
      {:success true :output out}
      {:success false :error err :output out})))

;;; LLMs.txt: "Aptos CLI - Compile, test, publish contracts; accounts & keys; localnet"
;;; TRIT: 0 (compile is simulation/preparation)
(defn- mk-move-compile
  [_opts]
  (fn [agent-node arguments]
    (let [package-dir  (get arguments "package_dir")
          named-addrs  (get arguments "named_addresses" {})
          addr-args    (when (seq named-addrs)
                         ["--named-addresses"
                          (str/join ","
                                    (map (fn [[k v]] (str k "=" v)) named-addrs))])
          result       (apply run-aptos-cli
                              (concat ["move" "compile"
                                       "--package-dir" package-dir
                                       "--save-metadata"]
                                      addr-args))]
      {:trit 0
       :operation :move-compile
       :llms-txt-quote "Aptos CLI - Compile, test, publish contracts"
       :result (if (:success result)
                 (str "✓ Compilation successful for " package-dir "\n" (:output result))
                 (str "✗ Compilation failed: " (:error result) "\n" (:output result)))})))

;;; LLMs.txt: "Deploy Move module to Aptos network"
;;; TRIT: +1 (publish is write operation)
(defn- mk-move-publish
  [_opts]
  (fn [agent-node arguments]
    (let [package-dir   (get arguments "package_dir")
          network       (keyword (get arguments "network" "devnet"))
          profile       (get arguments "profile" "default")
          named-addrs   (get arguments "named_addresses" {})
          url           (get NETWORK-URLS network)
          addr-args     (when (seq named-addrs)
                          ["--named-addresses"
                           (str/join ","
                                     (map (fn [[k v]] (str k "=" v)) named-addrs))])
          result        (apply run-aptos-cli
                               (concat ["move" "publish"
                                        "--package-dir" package-dir
                                        "--url" url
                                        "--profile" profile
                                        "--assume-yes"]
                                       addr-args))
          tx-hash       (when (:success result)
                          (second (re-find #"Transaction hash: (0x[a-f0-9]+)"
                                           (:output result))))]
      {:trit 1
       :operation :move-publish
       :llms-txt-quote "Deploy Move module to Aptos network"
       :tx-hash tx-hash
       :network network
       :result (if (:success result)
                 (str "✓ Published to " (name network) "\n" (:output result))
                 (str "✗ Publish failed: " (:error result) "\n" (:output result)))})))

;;; LLMs.txt: "Execute Move entry function on-chain"
;;; TRIT: +1 (call is write operation)
(defn- mk-move-call
  [_opts]
  (fn [agent-node arguments]
    (let [function-id  (get arguments "function_id")
          type-args    (get arguments "type_args" [])
          args         (get arguments "args" [])
          network      (keyword (get arguments "network" "devnet"))
          profile      (get arguments "profile" "default")
          url          (get NETWORK-URLS network)
          type-strs    (when (seq type-args)
                         ["--type-args" (str/join "," type-args)])
          arg-strs     (when (seq args)
                         ["--args" (str/join " " args)])
          result       (apply run-aptos-cli
                               (concat ["move" "run"
                                        "--function-id" function-id
                                        "--url" url
                                        "--profile" profile
                                        "--assume-yes"]
                                       type-strs
                                       arg-strs))]
      {:trit 1
       :operation :move-call
       :llms-txt-quote "Execute Move entry function on-chain"
       :function-id function-id
       :network network
       :result (if (:success result)
                 (str "✓ Called " function-id "\n" (:output result))
                 (str "✗ Call failed: " (:error result) "\n" (:output result)))})))

;;; LLMs.txt: "Reading state with the View function. View functions do not modify blockchain state"
;;; TRIT: -1 (view is read operation)
(defn- mk-move-view
  [_opts]
  (fn [agent-node arguments]
    (let [function-id  (get arguments "function_id")
          type-args    (get arguments "type_args" [])
          args         (get arguments "args" [])
          network      (keyword (get arguments "network" "devnet"))
          url          (get NETWORK-URLS network)
          type-strs    (when (seq type-args)
                         ["--type-args" (str/join "," type-args)])
          arg-strs     (when (seq args)
                         ["--args" (str/join " " args)])
          result       (apply run-aptos-cli
                               (concat ["move" "view"
                                        "--function-id" function-id
                                        "--url" url]
                                       type-strs
                                       arg-strs))]
      {:trit -1
       :operation :move-view
       :llms-txt-quote "View functions do not modify blockchain state when called from the API"
       :function-id function-id
       :result (if (:success result)
                 (str "View result: " (:output result))
                 (str "✗ View failed: " (:error result)))})))

;;; LLMs.txt: "Module bytecode can be decompiled with Revela or aptos cli"
;;; TRIT: -1 (list is read operation)
(defn- mk-move-list-functions
  [_opts]
  (fn [agent-node arguments]
    (let [account-addr (get arguments "account_address")
          module-name  (get arguments "module_name")
          network      (keyword (get arguments "network" "devnet"))
          url          (get NETWORK-URLS network)
          result       (run-aptos-cli
                        "move" "list"
                        "--account" account-addr
                        "--query" (str "modules::" module-name)
                        "--url" url)]
      {:trit -1
       :operation :move-list-functions
       :llms-txt-quote "Module bytecode can be decompiled with Revela or aptos cli"
       :module-name module-name
       :result (if (:success result)
                 (str "Functions in " module-name ":\n" (:output result))
                 (str "✗ List failed: " (:error result)))})))

;;; LLMs.txt: "On-chain data is stored as: table items, resources or modules (executable)"
;;; TRIT: -1 (get-abi is read operation)
(defn- mk-move-get-abi
  [_opts]
  (fn [agent-node arguments]
    (let [account-addr (get arguments "account_address")
          module-name  (get arguments "module_name")
          network      (keyword (get arguments "network" "devnet"))
          url          (get NETWORK-URLS network)
          result       (run-aptos-cli
                        "account" "list"
                        "--account" account-addr
                        "--query" (str "modules::" module-name)
                        "--url" url)]
      {:trit -1
       :operation :move-get-abi
       :llms-txt-quote "On-chain data is stored as: table items, resources or modules (executable)"
       :module-name module-name
       :result (if (:success result)
                 (str "ABI for " module-name ":\n" (:output result))
                 (str "✗ ABI fetch failed: " (:error result)))})))

;;; LLMs.txt: "Verify contract bytecode matches local source"
;;; TRIT: 0 (verify is validation/simulation, not a write)
(defn- mk-move-verify
  [_opts]
  (fn [agent-node arguments]
    (let [package-dir   (get arguments "package_dir")
          account-addr  (get arguments "account_address")
          module-name   (get arguments "module_name")
          network       (keyword (get arguments "network" "devnet"))
          url           (get NETWORK-URLS network)
          result        (run-aptos-cli
                         "move" "verify-package"
                         "--package-dir" package-dir
                         "--account" account-addr
                         "--module" module-name
                         "--url" url)]
      {:trit 0
       :operation :move-verify
       :llms-txt-quote "Verify contract bytecode matches local source"
       :module-name module-name
       :verified (:success result)
       :result (if (:success result)
                 (str "✓ Bytecode verified for " module-name "\n" (:output result))
                 (str "✗ Verification failed: " (:error result)))})))

;;; GF(3) Tally for MOVE-TOOLS:
;;; compile: 0, publish: +1, call: +1, init: +1, view: -1, list-functions: -1, get-abi: -1, verify: 0
;;; Total: 0 + 1 + 1 + 1 + (-1) + (-1) + (-1) + 0 = 0 ≡ 0 (mod 3) ✓ BALANCED

(def ^:private MOVE-TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "move_compile"
     (lj/object
      {:description "Compile a Move package (TRIT: 0)"
       :required    ["package_dir"]}
      {"package_dir"      (lj/string "Path to the Move package directory")
       "named_addresses"  (lj/object
                           {:description "Named addresses mapping"}
                           {})})
     "Compile Move module source code. LLMs.txt: Aptos CLI - Compile, test, publish contracts")
    (mk-move-compile {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_publish"
     (lj/object
      {:description "Publish Move module to network (TRIT: +1)"
       :required    ["package_dir"]}
      {"package_dir"     (lj/string "Path to compiled Move package")
       "network"         (lj/string "Target network: devnet, testnet, or mainnet")
       "profile"         (lj/string "Aptos CLI profile name")
       "named_addresses" (lj/object
                          {:description "Named addresses mapping"}
                          {})})
     "Deploy Move module. LLMs.txt: Deploy Move module to Aptos network")
    (mk-move-publish {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_call"
     (lj/object
      {:description "Call Move entry function (TRIT: +1)"
       :required    ["function_id"]}
      {"function_id" (lj/string "Function ID: address::module::function")
       "type_args"   (lj/array "Generic type arguments" (lj/string "Type argument"))
       "args"        (lj/array "Function arguments" (lj/string "Argument"))
       "network"     (lj/string "Target network")
       "profile"     (lj/string "Aptos CLI profile")})
     "Execute entry function. LLMs.txt: Execute Move entry function on-chain")
    (mk-move-call {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_view"
     (lj/object
      {:description "Call Move view function (TRIT: -1, read-only)"
       :required    ["function_id"]}
      {"function_id" (lj/string "Function ID: address::module::function")
       "type_args"   (lj/array "Generic type arguments" (lj/string "Type argument"))
       "args"        (lj/array "Function arguments" (lj/string "Argument"))
       "network"     (lj/string "Target network")})
     "Query state. LLMs.txt: View functions do not modify blockchain state")
    (mk-move-view {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_list_functions"
     (lj/object
      {:description "List functions in a module (TRIT: -1)"
       :required    ["account_address" "module_name"]}
      {"account_address" (lj/string "Account address")
       "module_name"     (lj/string "Module name")
       "network"         (lj/string "Target network")})
     "List module functions. LLMs.txt: Module bytecode can be decompiled")
    (mk-move-list-functions {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_get_abi"
     (lj/object
      {:description "Get module ABI (TRIT: -1)"
       :required    ["account_address" "module_name"]}
      {"account_address" (lj/string "Account address")
       "module_name"     (lj/string "Module name")
       "network"         (lj/string "Target network")})
     "Get module ABI. LLMs.txt: On-chain data stored as modules (executable)")
    (mk-move-get-abi {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_verify"
     (lj/object
      {:description "Verify bytecode matches source (TRIT: 0)"
       :required    ["package_dir" "account_address" "module_name"]}
      {"package_dir"      (lj/string "Path to Move package source")
       "account_address"  (lj/string "Account where module is deployed")
       "module_name"      (lj/string "Name of module to verify")
       "network"          (lj/string "Network to verify against")})
     "Verify bytecode. LLMs.txt: Verify contract bytecode matches local source")
    (mk-move-verify {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_init"
     (lj/object
      {:description "Initialize deployed module (TRIT: +1)"
       :required    ["function_id"]}
      {"function_id" (lj/string "Init function ID: address::module::init")
       "network"     (lj/string "Target network")
       "profile"     (lj/string "Aptos CLI profile")})
     "Initialize module after deployment. LLMs.txt: Execute Move entry function on-chain")
    (mk-move-call {})
    {:include-context? true})])

(def ^:private SYSTEM-PROMPT
  "You are an Aptos Move smart contract deployment agent.
   
   Available tools:
   - move_compile: Compile Move package from source
   - move_publish: Deploy compiled module to network
   - move_call: Execute entry functions
   - move_view: Query view functions (read-only)
   - move_verify: Verify deployed bytecode matches source
   
   For multi-step deployments:
   1. First compile the package
   2. Publish to target network
   3. Verify bytecode (optional but recommended)
   4. Call initialization functions if needed
   
   Always check compilation before publishing.
   Default to devnet for testing unless user specifies otherwise.")

;;; Forward-only module using defagentmodule pattern
;;; LLMs.txt: "Aptos CLI - Compile, test, publish contracts; accounts & keys; localnet"
(aor/defagentmodule AptosMoveModule
  [topology]

  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o")
         .build)))

  (tools/new-tools-agent topology "move-tools" MOVE-TOOLS)

  (->
   topology
   (aor/new-agent "AptosMoveAgent")
   (aor/node
    "react"
    "react"
    (fn [agent-node messages]
      (let [openai      (aor/get-agent-object agent-node "openai")
            tools       (aor/agent-client agent-node "move-tools")
            full-msgs   (cons {:role "system" :content SYSTEM-PROMPT} messages)
            response    (lc4j/chat openai (lc4j/chat-request full-msgs {:tools MOVE-TOOLS}))
            ai-message  (.aiMessage response)
            tool-calls  (vec (.toolExecutionRequests ai-message))]
        (if (not-empty tool-calls)
          (let [tool-results  (aor/agent-invoke tools tool-calls)
                next-messages (into (conj messages ai-message) tool-results)]
            (aor/emit! agent-node "react" next-messages))
          (aor/result! agent-node (.text ai-message))))))))

(defn run-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc AptosMoveModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name AptosMoveModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "AptosMoveAgent")
          _             (println "Aptos Move Agent ready.")
          _             (println "Example: 'Deploy the counter module from ./examples/counter to devnet'")
          _             (print "> ")
          _             (flush)
          ^String input (read-line)
          result        (aor/agent-invoke agent [input])]
      (println result))))

(defn deploy-contract
  [package-dir network & {:keys [profile named-addresses]
                          :or   {profile "default" named-addresses {}}}]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AptosMoveModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name AptosMoveModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "AptosMoveAgent")
          prompt        (str "Deploy the Move package at " package-dir
                             " to " (name network)
                             " using profile " profile
                             ". Named addresses: " (pr-str named-addresses)
                             ". First compile, then publish, then verify.")]
      (aor/agent-invoke agent [prompt]))))

;;; Forward-only triadic wrappers (read/simulate/write) for GF(3) balance.

(defn- gf3-add
  [a b]
  (let [sum (+ a b)]
    (cond (> sum 1)  (- sum 3)
          (< sum -1) (+ sum 3)
          :else      sum)))

(defn- validate-move-trit-conservation
  [tracked-results]
  (let [trits (mapv :trit tracked-results)
        sum (reduce gf3-add 0 trits)]
    {:conserved? (zero? sum)
     :trits trits
     :sum sum
     :operations (mapv :operation tracked-results)}))

(defn- mk-move-triad-read
  [_opts]
  (fn [agent-node arguments]
    (case (get arguments "read_op")
      "view" ((mk-move-view {}) agent-node arguments)
      "list_functions" ((mk-move-list-functions {}) agent-node arguments)
      "get_abi" ((mk-move-get-abi {}) agent-node arguments)
      {:trit -1
       :operation :move-read
       :error "Unknown read_op. Use view, list_functions, or get_abi."})))

(defn- mk-move-triad-simulate
  [_opts]
  (fn [agent-node arguments]
    (case (get arguments "simulate_op")
      "compile" ((mk-move-compile {}) agent-node arguments)
      "verify" ((mk-move-verify {}) agent-node arguments)
      {:trit 0
       :operation :move-simulate
       :error "Unknown simulate_op. Use compile or verify."})))

(defn- mk-move-triad-write
  [_opts]
  (fn [agent-node arguments]
    (case (get arguments "write_op")
      "publish" ((mk-move-publish {}) agent-node arguments)
      "call" ((mk-move-call {}) agent-node arguments)
      "init" ((mk-move-call {}) agent-node arguments)
      {:trit 1
       :operation :move-write
       :error "Unknown write_op. Use publish, call, or init."})))

(def ^:private MOVE-TRIAD-TOOLS
  [(tools/tool-info
    (tools/tool-specification
     "move_read"
     (lj/object
      {:description "Read Move state (TRIT: -1)"
       :required    ["read_op"]}
      {"read_op"         (lj/enum "Read op: view | list_functions | get_abi"
                                  ["view" "list_functions" "get_abi"])
       "function_id"     (lj/string "Function ID: address::module::function")
       "type_args"       (lj/array "Generic type arguments" (lj/string "Type argument"))
       "args"            (lj/array "Function arguments" (lj/string "Argument"))
       "account_address" (lj/string "Account address")
       "module_name"     (lj/string "Module name")
       "network"         (lj/string "Target network")})
     "Read state via view/list/get-abi.")
    (mk-move-triad-read {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_simulate"
     (lj/object
      {:description "Simulate/validate Move (TRIT: 0)"
       :required    ["simulate_op"]}
      {"simulate_op"    (lj/enum "Simulate op: compile | verify"
                                 ["compile" "verify"])
       "package_dir"    (lj/string "Path to Move package source")
       "account_address" (lj/string "Account where module is deployed")
       "module_name"    (lj/string "Name of module to verify")
       "named_addresses" (lj/object {:description "Named addresses mapping"} {})
       "network"        (lj/string "Target network")})
     "Simulate via compile/verify.")
    (mk-move-triad-simulate {})
    {:include-context? true})

   (tools/tool-info
    (tools/tool-specification
     "move_write"
     (lj/object
      {:description "Write Move changes (TRIT: +1)"
       :required    ["write_op"]}
      {"write_op"       (lj/enum "Write op: publish | call | init"
                                 ["publish" "call" "init"])
       "package_dir"    (lj/string "Path to compiled Move package")
       "function_id"    (lj/string "Function ID: address::module::function")
       "type_args"      (lj/array "Generic type arguments" (lj/string "Type argument"))
       "args"           (lj/array "Function arguments" (lj/string "Argument"))
       "network"        (lj/string "Target network: devnet, testnet, or mainnet")
       "profile"        (lj/string "Aptos CLI profile name")
       "named_addresses" (lj/object {:description "Named addresses mapping"} {})})
     "Write via publish/call/init.")
    (mk-move-triad-write {})
    {:include-context? true})])

(def ^:private SYSTEM-PROMPT-FORWARD
  "Forward-only Aptos Move agent.

   Use the triad in order: move_read (-1), move_simulate (0), move_write (+1).
   Maintain GF(3) balance per cycle: -1 + 0 + +1 = 0.

   Default to devnet unless specified.")

(aor/defagentmodule AptosMoveForwardModule
  [topology]

  (aor/declare-agent-object
   topology
   "openai-api-key"
   (System/getenv "OPENAI_API_KEY"))

  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o")
         .build)))

  (aor/declare-agent-object-builder
   topology
   "trit-accumulator"
   (fn [_setup]
     (atom [])))

  (tools/new-tools-agent topology "move-triad-tools" MOVE-TRIAD-TOOLS)

  (-> topology
      (aor/new-agent "AptosMoveForwardAgent")
      (aor/node
       "react-forward"
       "react-forward"
       (fn [agent-node messages]
         (let [openai      (aor/get-agent-object agent-node "openai")
               tools       (aor/agent-client agent-node "move-triad-tools")
               trit-acc    (aor/get-agent-object agent-node "trit-accumulator")
               full-msgs   (cons {:role "system" :content SYSTEM-PROMPT-FORWARD} messages)
               response    (lc4j/chat openai (lc4j/chat-request full-msgs {:tools MOVE-TRIAD-TOOLS}))
               ai-message  (.aiMessage response)
               tool-calls  (vec (.toolExecutionRequests ai-message))]
           (if (not-empty tool-calls)
             (let [tool-results  (aor/agent-invoke tools tool-calls)
                   next-messages (into (conj messages ai-message) tool-results)]
               (doseq [tr tool-results]
                 (when (contains? tr :trit)
                   (swap! trit-acc conj tr)))
               (aor/emit! agent-node "react-forward" next-messages))
             (aor/result! agent-node
                          {:response (.text ai-message)
                           :trit-tracking (validate-move-trit-conservation @trit-acc)})))))))

(defn run-forward-agent
  []
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc AptosMoveForwardModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name AptosMoveForwardModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "AptosMoveForwardAgent")
          _             (println "Aptos Move Forward Agent ready.")
          _             (println "Example: 'Read view 0x1::coin::balance, then compile, then publish.'")
          _             (print "> ")
          _             (flush)
          ^String input (read-line)
          result        (aor/agent-invoke agent [input])]
      (println result))))

(comment
  ;; Run the agent
  (run-agent)

  ;; Deploy a contract
  (deploy-contract "./examples/counter" :devnet
                   :profile "default"
                   :named-addresses {"counter_addr" "0x1234..."})
  
  ;; LLMs.txt quotes used in this module:
  ;; - "Aptos CLI - Compile, test, publish contracts; accounts & keys; localnet"
  ;; - "Deploy Move module to Aptos network"
  ;; - "Execute Move entry function on-chain"
  ;; - "View functions do not modify blockchain state"
  ;; - "Module bytecode can be decompiled with Revela or aptos cli"
  ;; - "On-chain data is stored as: table items, resources or modules (executable)"
  ;; - "Verify contract bytecode matches local source"
  )
