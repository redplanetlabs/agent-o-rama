(ns com.rpl.agent.fail-agent
  "An agent designed to fail a specific number of times to test retry UI."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;; This atom will be used by the agent node to control failures.
;; We def it here so it's accessible in the REPL for inspection if needed.
(defonce FAIL_COUNT (atom 0))

(defn- check!
  "Throws an exception if the FAIL_COUNT atom is greater than 0,
   decrementing it each time."
  []
  (when (> @FAIL_COUNT 0)
    (swap! FAIL_COUNT dec)
    (throw (ex-info (str "Intentional failure. Remaining failures: " @FAIL_COUNT)
                    {:remaining @FAIL_COUNT}))))

(aor/defagentmodule RetryTestModule
  [topology]
  (-> topology
      (aor/new-agent "RetryTestAgent")
      (aor/node
       "start"
       "processing"
       (fn [agent-node initial-fail-count]
         ;; Set the initial failure count when the agent starts
         (reset! FAIL_COUNT initial-fail-count)
         (aor/emit! agent-node "processing" (str "Input with " initial-fail-count " failures."))))

      (aor/node
       "processing"
       "finish"
       (fn [agent-node input-str]
         (check!) ;; This node will fail `initial-fail-count` times
         (aor/emit! agent-node "finish" (str "Processed: " input-str))))

      (aor/node
       "finish"
       nil
       (fn [agent-node final-str]
         (aor/result! agent-node (str "Success! " final-str))))))

;;; Example invocation function
(defn run-agent
  "Runs the RetryTestAgent, configured to fail a specific number of times.
   Example: (run-agent 3) -> The agent will fail 3 times and succeed on the 4th attempt."
  [fail-count]
  (println (str "Running agent, expecting it to fail " fail-count " time(s)..."))
  (with-open [ipc (rtest/create-ipc)
              _   (aor/start-ui ipc)]
    (rtest/launch-module! ipc RetryTestModule {:tasks 4 :threads 2})
    (let [module-name   (rama/get-module-name RetryTestModule)
          agent-manager (aor/agent-manager ipc module-name)
          agent         (aor/agent-client agent-manager "RetryTestAgent")
          invoke        (aor/agent-initiate agent fail-count)
          result        (aor/agent-result agent invoke)]
      (println "\nAgent finished successfully.")
      (println "Result:" result)
      (println "Navigate to the UI to see the invocation trace, including retries and exceptions."))))

(comment
  (run-agent 3))
