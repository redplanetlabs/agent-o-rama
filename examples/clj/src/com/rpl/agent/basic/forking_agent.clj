(ns com.rpl.agent.basic.forking-agent
  "Demonstrates agent execution forking and branching patterns.

  Features demonstrated:
  - agent-initiate-fork: Create execution branches from existing invocations
  - agent-fork: Synchronous forking with modified parameters
  - Branching execution paths with different inputs
  - Fork management and result handling"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating forking functionality
(aor/defagentmodule ForkingAgentModule
  [topology]

  (->
    topology
    (aor/new-agent "ForkingAgent")

    ;; Initial processing node
    (aor/node
     "initial-process"
     "calculate"
     (fn [agent-node {:keys [base-value multiplier]}]
       (println (format "Initial processing: %d * %d" base-value multiplier))
       (let [result (* base-value multiplier)]
         (aor/emit! agent-node
                    "calculate"
                    {:original-input  {:base-value base-value
                                       :multiplier multiplier}
                     :processed-value result}))))

    ;; Calculation node that can be forked
    (aor/node
     "calculate"
     "validate"
     (fn [agent-node {:keys [original-input processed-value]}]
       (println (format "Calculating with processed value: %d" processed-value))
       (let [squared (* processed-value processed-value)
             halved  (/ processed-value 2.0)]
         (aor/emit! agent-node
                    "validate"
                    {:original-input  original-input
                     :processed-value processed-value
                     :squared         squared
                     :halved          halved}))))

    ;; Validation node
    (aor/node
     "validate"
     nil
     (fn [agent-node {:keys [original-input processed-value squared halved]}]
       (println
        (format "Validating results: squared=%d, halved=%.1f" squared halved))
       (let [is-valid (and (pos? processed-value)
                           (>= squared processed-value))]
         (aor/result! agent-node
                      {:action          "calculation-complete"
                       :original-input  original-input
                       :processed-value processed-value
                       :squared         squared
                       :halved          halved
                       :valid?          is-valid
                       :completed-at    (System/currentTimeMillis)}))))))

(defn -main
  "Run the forking agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc ForkingAgentModule {:tasks 2 :threads 2})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name ForkingAgentModule))
          agent   (aor/agent-client manager "ForkingAgent")]

      (println "Forking Agent Example:")
      (println "Creating execution branches with different parameters")

      ;; Start base execution
      (println "\n--- Base execution ---")
      (let [base-invoke (aor/agent-initiate agent
                                            {:base-value 5
                                             :multiplier 3})
            base-result (aor/agent-result agent base-invoke)]

        (println "Base result:")
        (println "  Original input:" (:original-input base-result))
        (println "  Processed value:" (:processed-value base-result))
        (println "  Squared:" (:squared base-result))
        (println "  Valid:" (:valid? base-result))

        ;; Fork without modification - re-runs with same data
        (println "\n--- Fork 1: Re-run with same data ---")
        (let [fork1 (aor/agent-fork agent
                                    base-invoke
                                    {})]
          (println "Fork 1 result:")
          (println "  Processed value:" (:processed-value fork1))
          (println "  Squared:" (:squared fork1))
          (println "  Valid:" (:valid? fork1)))

        ;; Fork with async initiation
        (println "\n--- Fork 2: Async fork re-run ---")
        (let [fork2-invoke (aor/agent-initiate-fork agent
                                                    base-invoke
                                                    {})
              fork2-result (aor/agent-result agent fork2-invoke)]
          (println "Fork 2 result:")
          (println "  Processed value:" (:processed-value fork2-result))
          (println "  Squared:" (:squared fork2-result))
          (println "  Valid:" (:valid? fork2-result)))

        ;; Another fork example
        (println "\n--- Fork 3: Another fork re-run ---")
        (let [fork3 (aor/agent-fork agent
                                    base-invoke
                                    {})]
          (println "Fork 3 result:")
          (println "  Processed value:" (:processed-value fork3))
          (println "  Squared:" (:squared fork3))
          (println "  Halved:" (:halved fork3))
          (println "  Valid:" (:valid? fork3))))

      (println "\nNotice how:")
      (println "- Forks create independent execution branches")
      (println "- Forks re-run the agent execution independently")
      (println "- Both sync and async forking are supported"))))
