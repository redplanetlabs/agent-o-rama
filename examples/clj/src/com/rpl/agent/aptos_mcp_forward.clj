;; Forward-only wrapper for balanced Aptos MCP tools.
;; This does not modify the original Aptos MCP module.
(ns com.rpl.agent.aptos-mcp-forward
  "Forward-only Aptos MCP module with GF(3)-balanced toolset."
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.tools :as tools]
   [com.rpl.agent.aptos-mcp :as aptos-mcp]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agent.aptos_mcp TritTrackedResult]
   [dev.langchain4j.data.message UserMessage]
   [dev.langchain4j.model.openai OpenAiChatModel]))

(def ^:private APTOS-MCP-TOOLS-FORWARD
  aptos-mcp/APTOS-MCP-TOOLS-BALANCED)

(aor/defagentmodule AptosMcpForwardModule
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

  (tools/new-tools-agent
   topology
   "AptosMcpToolsAgentForward"
   APTOS-MCP-TOOLS-FORWARD)

  (-> topology
      (aor/new-agent "AptosMcpForwardAgent")
      (aor/node
       "execute"
       nil
       (fn execute-fn [agent-node requests]
         (let [model       (aor/get-agent-object agent-node "openai-model")
               tools-agent (aor/agent-client agent-node "AptosMcpToolsAgentForward")
               trit-acc    (aor/get-agent-object agent-node "trit-accumulator")
               results     (atom [])]
           (doseq [request requests]
             (let [response   (lc4j/chat model
                                         (lc4j/chat-request
                                          [(UserMessage. (str request))]
                                          {:tools APTOS-MCP-TOOLS-FORWARD}))
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
           (let [conservation (aptos-mcp/validate-trit-conservation @trit-acc)]
             (aor/result! agent-node
                          {:results @results
                           :trit-tracking {:operations-count (count @trit-acc)
                                           :conservation conservation}})))))))

(defn -main
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AptosMcpForwardModule {:tasks 1 :threads 1})
    (let [manager (aor/agent-manager ipc (rama/get-module-name AptosMcpForwardModule))
          aptos-agent (aor/agent-client manager "AptosMcpForwardAgent")
          requests ["Check balance of 0x1"
                    "List modules on 0x1"
                    "Simulate transfer of 0.5 APT from 0xabc to 0xdef on devnet"]
          result (aor/agent-invoke aptos-agent requests)]
      (println "=== Aptos MCP Forward Agent ===")
      (println "\nResults:")
      (doseq [[idx r] (map-indexed vector (:results result))]
        (println (format "\n[%d] Request: %s" (inc idx) (:request r)))
        (when (:tool-calls r)
          (println "    Tools:" (str/join ", " (:tool-calls r)))))
      (println "\n=== GF(3) Trit Conservation ===")
      (let [tc (:trit-tracking result)]
        (println "Operations:" (:operations-count tc))
        (println "Conservation:" (:conservation tc))))))
