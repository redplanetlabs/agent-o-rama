(ns start-ui
  "Start Agent-o-rama UI with demo agents"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.model.openai OpenAiChatModel]))

;; Basic agent module with single node
(aor/defagentmodule DemoAgentModule
  [topology]
  
  ;; Declare OpenAI API key as agent object
  (aor/declare-agent-object topology "openai-api-key" (System/getenv "OPENAI_API_KEY"))
  
  ;; Create a simple echo agent
  (-> topology
      (aor/new-agent "EchoAgent")
      (aor/node
       "echo"
       nil
       (fn [agent-node input]
         (aor/result! agent-node (str "Echo: " input)))))
  
  ;; Create a greeting agent  
  (-> topology
      (aor/new-agent "GreetingAgent")
      (aor/node
       "greet"
       nil
       (fn [agent-node name]
         (aor/result! agent-node (str "Hello, " name "! Welcome to Agent-o-rama."))))))

(defn -main [& args]
  (println "Starting Agent-o-rama UI...")
  (println "Creating in-process cluster...")
  
  (let [ipc (rtest/create-ipc)]
    (println "Launching DemoAgentModule...")
    (rtest/launch-module! ipc DemoAgentModule {:tasks 1 :threads 1})
    
    (println "Starting UI on http://localhost:1974")
    (let [ui (aor/start-ui ipc {:port 1974})]
      
      ;; Test the agents
      (let [manager (aor/agent-manager ipc (rama/get-module-name DemoAgentModule))
            echo-agent (aor/agent-client manager "EchoAgent")
            greeting-agent (aor/agent-client manager "GreetingAgent")]
        
        (println "\n=== Testing Agents ===")
        (println "EchoAgent:" (aor/agent-invoke echo-agent "Hello World"))
        (println "GreetingAgent:" (aor/agent-invoke greeting-agent "Alice"))
        (println "======================\n"))
      
      (println "")
      (println "╔══════════════════════════════════════════════════════════╗")
      (println "║  Agent-o-rama UI is running at: http://localhost:1974    ║")
      (println "║  Press Ctrl+C to stop                                    ║")
      (println "╚══════════════════════════════════════════════════════════╝")
      (println "")
      
      ;; Keep running
      (.addShutdownHook (Runtime/getRuntime)
                        (Thread. (fn []
                                   (println "\nShutting down...")
                                   (.close ui)
                                   (.close ipc))))
      
      ;; Block forever
      (Thread/sleep Long/MAX_VALUE))))
