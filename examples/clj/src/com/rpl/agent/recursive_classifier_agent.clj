(ns com.rpl.agent.recursive-classifier-agent
  "Recursive question-answering agent with classification and search planning.
  
  This agent demonstrates a complex flow with:
  - Recursive execution (execute-recursively loops back to itself)
  - Classification of questions into different response types
  - Planning and search workflows
  - Multiple converging and diverging paths"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule RecursiveClassifierAgentModule
  [topology]

  (->
   topology
   (aor/new-agent "RecursiveClassifierAgent")

    ;; Entry point: execute-recursively
    ;; execute-recursive -> plan-search
    ;; execute-recursive -> answer
   (aor/node
    "execute-recursively"
    ["plan-search" "answer"]
    (fn [agent-node input]
      (let [{:keys [path iteration] :or {iteration 0}} input
            choice (mod (hash path) 2)]
        (if (= choice 0)
          (aor/emit! agent-node "plan-search"
                    (str path " -> execute-recursively")
                    iteration)
          (aor/emit! agent-node "answer"
                    (str path " -> execute-recursively")
                    iteration)))))

    ;; plan-search -> execute-recursive
   (aor/node
    "plan-search"
    "execute-recursively"
    (fn [agent-node path iteration]
      (if (< iteration 2)
        (aor/emit! agent-node "execute-recursively"
                  {:path (str path " -> plan-search")
                   :iteration (inc iteration)})
        (aor/result! agent-node (str path " -> plan-search [max iterations]")))))

    ;; classify-question -> plan-search
    ;; classify-question -> respond-dataflow
    ;; classify-question -> ask-for-more-info
    ;; classify-question -> respond-general
   (aor/node
    "classify-question"
    ["plan-search" "respond-dataflow" "ask-for-more-info" "respond-general"]
    (fn [agent-node path iteration]
      (let [choice (mod (hash path) 4)]
        (case choice
          0 (aor/emit! agent-node "plan-search"
                      (str path " -> classify-question")
                      iteration)
          1 (aor/emit! agent-node "respond-dataflow"
                      (str path " -> classify-question")
                      iteration)
          2 (aor/emit! agent-node "ask-for-more-info"
                      (str path " -> classify-question")
                      iteration)
          3 (aor/emit! agent-node "respond-general"
                      (str path " -> classify-question")
                      iteration)))))

    ;; ask-for-more-info -> classify-question
   (aor/node
    "ask-for-more-info"
    "classify-question"
    (fn [agent-node path iteration]
      (if (< iteration 2)
        (aor/emit! agent-node "classify-question"
                  (str path " -> ask-for-more-info")
                  (inc iteration))
        (aor/result! agent-node (str path " -> ask-for-more-info [max iterations]")))))

    ;; respond-dataflow (terminal)
   (aor/node
    "respond-dataflow"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> respond-dataflow"))))

    ;; respond-general (terminal)
   (aor/node
    "respond-general"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> respond-general"))))

    ;; answer (terminal)
   (aor/node
    "answer"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> answer"))))))

(defn -main
  "Run the recursive classifier agent with example inputs"
  [& _args]
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc RecursiveClassifierAgentModule {:tasks 4 :threads 2})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name RecursiveClassifierAgentModule))
          agent   (aor/agent-client manager "RecursiveClassifierAgent")]

      (println "Recursive Classifier Agent - Example Runs")
      (println "==========================================\n")

      ;; Example 1: Simple path
      (println "--- Run 1 ---")
      (let [result1 (aor/agent-invoke agent {:path "start" :iteration 0})]
        (println "Result:" result1)
        (println))

      ;; Example 2: Different path
      (println "--- Run 2 ---")
      (let [result2 (aor/agent-invoke agent {:path "begin" :iteration 0})]
        (println "Result:" result2)
        (println))

      ;; Example 3: Another path
      (println "--- Run 3 ---")
      (let [result3 (aor/agent-invoke agent {:path "input" :iteration 0})]
        (println "Result:" result3)
        (println))

      (println "\nAgent execution complete. Check the UI at http://localhost:1974 for traces!"))))
