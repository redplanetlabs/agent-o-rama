(ns com.rpl.agent.streaming-test-agent
  "Test agent module for streaming E2E tests.

  Provides a simple agent that manually emits streaming chunks for testing
  the streaming UI without requiring an LLM."
  (:require
   [com.rpl.agent-o-rama :as aor]))

(aor/defagentmodule StreamingTestAgentModule
  [topology]

  (-> topology
      (aor/new-agent "StreamingTestAgent")
      (aor/node
       "stream-node"
       nil
       (fn [agent-node {:strs [chunks delay-ms] :or {delay-ms 1000}}]
         ;; Stream each chunk with a delay between them
         (let [chunks (or chunks ["Hello " "world" "! " "This " "is " "streaming " "text."])]
           (doseq [chunk chunks]
             (aor/stream-chunk! agent-node chunk)
             (Thread/sleep (long delay-ms))))
         (aor/result! agent-node {:status "complete" :chunk-count (count chunks)})))))

