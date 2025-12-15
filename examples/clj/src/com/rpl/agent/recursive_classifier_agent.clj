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
    ;; Routes to either answer or plan-search
   (aor/node
    "execute-recursively"
    ["answer" "plan-search"]
    (fn [agent-node input]
      (let [{:keys [path iteration] :or {iteration 0}} input]
        (if (>= iteration 2)
           ;; Max iterations reached, go to answer
          (aor/emit! agent-node "answer"
                     (str path " -> execute-recursively[final]")
                     iteration)
           ;; Route to plan-search
          (aor/emit! agent-node "plan-search"
                     (str path " -> execute-recursively")
                     iteration)))))

    ;; Answer node: Can terminate only (remove loop for now)
   (aor/node
    "answer"
    nil
    (fn [agent-node path iteration]
      (let [new-path (str path " -> answer")]
        (aor/result! agent-node new-path))))

    ;; Plan search node: Routes to classify-question
   (aor/node
    "plan-search"
    "classify-question"
    (fn [agent-node path iteration]
      (aor/emit! agent-node "classify-question"
                 (str path " -> plan-search")
                 iteration)))

    ;; Classify question node: Routes to one of three response handlers
   (aor/node
    "classify-question"
    ["respond-dataflow" "ask-for-more-info" "respond-general"]
    (fn [agent-node path iteration]
      (let [choice (mod (hash path) 3)]
        (case choice
          0 (aor/emit! agent-node "respond-dataflow"
                       (str path " -> classify-question")
                       iteration)
          1 (aor/emit! agent-node "ask-for-more-info"
                       (str path " -> classify-question")
                       iteration)
          2 (aor/emit! agent-node "respond-general"
                       (str path " -> classify-question")
                       iteration)))))

    ;; Respond with dataflow information (terminal)
   (aor/node
    "respond-dataflow"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> respond-dataflow"))))

    ;; Ask for more information (terminal)
   (aor/node
    "ask-for-more-info"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> ask-for-more-info"))))

    ;; Respond with general information (terminal only for now)
   (aor/node
    "respond-general"
    nil
    (fn [agent-node path iteration]
      (let [new-path (str path " -> respond-general")]
        (aor/result! agent-node new-path))))))

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
