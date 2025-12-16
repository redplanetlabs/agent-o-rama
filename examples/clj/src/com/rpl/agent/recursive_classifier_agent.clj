(ns com.rpl.agent.recursive-classifier-agent
  "Chat agent with classification, recursive search, and multiple response types"
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule RecursiveClassifierAgentModule
  [topology]

  (->
   topology
   (aor/new-agent "chat-agent")

   ;; classify-question is the entry point (first node)
   ;; Routes to: ask-for-more-info, respond-general, respond-dataflow, plan-search
   (aor/node
    "classify-question"
    ["ask-for-more-info" "respond-general" "respond-dataflow" "plan-search"]
    (fn [agent-node input]
      (let [{:keys [path iteration] :or {path "start" iteration 0}} input
            choice (mod (hash path) 4)]
        (case choice
          0 (aor/emit! agent-node "ask-for-more-info"
                      (str path " -> classify-question")
                      iteration)
          1 (aor/emit! agent-node "respond-general"
                      (str path " -> classify-question")
                      iteration)
          2 (aor/emit! agent-node "respond-dataflow"
                      (str path " -> classify-question")
                      iteration)
          3 (aor/emit! agent-node "plan-search"
                      (str path " -> classify-question")
                      iteration)))))

   ;; plan-search -> execute-recursive-search
   (aor/node
    "plan-search"
    "execute-recursive-search"
    (fn [agent-node path iteration]
      (aor/emit! agent-node "execute-recursive-search"
                (str path " -> plan-search")
                iteration)))

   ;; execute-recursive-search -> plan-search OR answer (loop back or proceed)
   (aor/node
    "execute-recursive-search"
    ["plan-search" "answer"]
    (fn [agent-node path iteration]
      (if (< iteration 2)
        ;; Loop back to plan-search
        (aor/emit! agent-node "plan-search"
                  (str path " -> execute-recursive-search")
                  (inc iteration))
        ;; Proceed to answer
        (aor/emit! agent-node "answer"
                  (str path " -> execute-recursive-search")
                  iteration))))

   ;; answer -> summarize-conversation
   (aor/node
    "answer"
    "summarize-conversation"
    (fn [agent-node path iteration]
      (aor/emit! agent-node "summarize-conversation"
                (str path " -> answer")
                iteration)))

   ;; summarize-conversation (terminal)
   (aor/node
    "summarize-conversation"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> summarize-conversation"))))

   ;; ask-for-more-info -> classify-question (loop back)
   (aor/node
    "ask-for-more-info"
    "classify-question"
    (fn [agent-node path iteration]
      (if (< iteration 2)
        (aor/emit! agent-node "classify-question"
                  {:path (str path " -> ask-for-more-info")
                   :iteration (inc iteration)})
        (aor/result! agent-node (str path " -> ask-for-more-info [max iterations]")))))

   ;; respond-general (terminal)
   (aor/node
    "respond-general"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> respond-general"))))

   ;; respond-dataflow (terminal)
   (aor/node
    "respond-dataflow"
    nil
    (fn [agent-node path iteration]
      (aor/result! agent-node (str path " -> respond-dataflow"))))))

(defn -main
  "Run the chat agent with example inputs"
  [& _args]
  (with-open [ipc (rtest/create-ipc)
              ui (aor/start-ui ipc)]
    (rtest/launch-module! ipc RecursiveClassifierAgentModule {:tasks 4 :threads 2})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name RecursiveClassifierAgentModule))
          agent   (aor/agent-client manager "chat-agent")]

      (println "Chat Agent - Example Runs")
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
