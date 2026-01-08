(ns add-queue-items
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.evaluators :as evals])
  (:import [com.rpl.agentorama AgentInvoke]))

(defn add-items-to-queue!
  "Add test items to a human feedback queue.
   
   Usage: (add-items-to-queue! ipc \"Test Queue\" 3)"
  [ipc queue-name n]
  (let [test-manager (aor/agent-manager ipc "com.rpl.agent.e2e-test-agent/E2ETestAgentModule")
        underlying (aor-types/underlying-objects test-manager)
        global-actions-depot (:global-actions-depot underlying)
        test-client (aor/agent-client test-manager "E2ETestAgent")]
    
    (doseq [i (range n)]
      (let [;; Run agent with proper input format for E2ETestAgent
            ;; Use agent-initiate-with-context to get the AgentInvoke object
            ^AgentInvoke inv (aor/agent-initiate-with-context 
                              test-client 
                              {} 
                              {"run-id" (str "queue-test-" i)
                               "output-value" (str "Test output #" i)})
            task-id (.getTaskId inv)
            invoke-id (.getAgentInvokeId inv)
            
            ;; Create FeedbackTarget
            agent-invoke (aor-types/->AgentInvokeImpl task-id invoke-id)
            feedback-target (aor-types/->FeedbackTarget "E2ETestAgent" agent-invoke nil)]
        
        ;; Add to queue
        (evals/add-human-feedback-request!
         global-actions-depot
         queue-name
         feedback-target
         (str "Review request #" i))
        (println "Added item" i "to queue:" queue-name)))
    
    (println "Done! Added" n "items to queue:" queue-name)))

