(ns com.rpl.agent.multi-node-agent
  "Demonstrates agent graphs with multiple nodes and inter-node emissions.
  
  Features demonstrated:
  - Agent graph with multiple connected nodes
  - emit!: Send data from one node to another
  - Node chaining and data flow
  - Processing pipeline through graph traversal"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Multi-node agent demonstrating data flow through graph
(aor/defagentmodule MultiNodeAgentModule
  [topology]

  (-> topology
      (aor/new-agent "MultiNodeAgent")

      ;; First node: validate and parse input
      (aor/node "validate" "process"
                (fn [agent-node input]
                  (println "Validate node processing:" input)
                  (cond
                    (number? input)
                    (aor/emit! agent-node "process" {:type :number :value input})

                    (string? input)
                    (aor/emit! agent-node "process" {:type :string :value input})

                    :else
                    (aor/emit! agent-node "process" {:type :other :value input}))))

      ;; Second node: process based on type
      (aor/node "process" "finalize"
                (fn [agent-node {:keys [type value]}]
                  (println "Process node handling type:" type "value:" value)
                  (let [processed (case type
                                    :number (* value 2)
                                    :string (.toUpperCase value)
                                    :other (str "UNKNOWN: " value))]
                    (aor/emit! agent-node "finalize" {:original value
                                                      :processed processed
                                                      :type type}))))

      ;; Final node: format result
      (aor/node "finalize" nil
                (fn [agent-node {:keys [original processed type]}]
                  (println "Finalize node creating result")
                  (let [result {:input original
                                :output processed
                                :transformation (name type)
                                :timestamp (System/currentTimeMillis)}]
                    (aor/result! agent-node result))))))

(defn -main
  "Run the multi-node agent example with various inputs"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MultiNodeAgentModule {:tasks 2 :threads 1})

    (let [manager (aor/agent-manager ipc (rama/get-module-name MultiNodeAgentModule))
          agent (aor/agent-client manager "MultiNodeAgent")]

      (println "Multi-Node Agent Results:")
      (println "\n--- Processing number ---")
      (let [result1 (aor/agent-invoke agent 21)]
        (println "Result:" result1))

      (println "\n--- Processing string ---")
      (let [result2 (aor/agent-invoke agent "hello world")]
        (println "Result:" result2))

      (println "\n--- Processing other ---")
      (let [result3 (aor/agent-invoke agent [:a :b :c])]
        (println "Result:" result3)))))