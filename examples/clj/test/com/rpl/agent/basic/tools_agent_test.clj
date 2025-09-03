(ns com.rpl.agent.basic.tools-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.tools-agent :refer [ToolsAgentModule]]))

(deftest tools-agent-test
  (testing "ToolsAgent example with OpenAI model and natural language prompts"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc ToolsAgentModule {:tasks 1 :threads 1})

      (let [manager     (aor/agent-manager
                         ipc
                         (rama/get-module-name ToolsAgentModule))
            coordinator (aor/agent-client manager "ToolsCoordinator")]

        (testing "processes natural language prompts that trigger tool usage"
          (let [prompts ["What is 10 plus 5?"
                         "Calculate 6 times 7"
                         "Convert 'hello' to uppercase"
                         "Reverse the text 'test'"]
                result  (aor/agent-invoke coordinator prompts)]

            (is (= 4 (:prompts-count result)))
            (is (= 4 (count (:results result))))

            ;; Check that each result has the expected structure
            (doseq [prompt-result (:results result)]
              (is (contains? prompt-result :prompt))
              (is (contains? prompt-result :tool-calls))
              ;; Each prompt should either have tool results or a direct
              ;; response
              (is (or (contains? prompt-result :tool-results)
                      (contains? prompt-result :response))))))

        (testing "handles mixed prompts that may or may not trigger tools"
          (let [prompts ["Hello there!" ; Likely no tools
                         "What is 15 divided by 3?" ; Should use calculator
                         "How are you?" ; Likely no tools
                         "Make 'WORLD' lowercase"] ; Should use string processor
                result  (aor/agent-invoke coordinator prompts)]

            (is (= 4 (:prompts-count result)))
            (is (= 4 (count (:results result))))

            ;; At least some results should have tool calls
            (let [tool-call-counts (map #(:tool-calls % 0) (:results result))]
              (is (> (reduce + tool-call-counts) 0)))))

        (testing "handles empty prompt list"
          (let [result (aor/agent-invoke coordinator [])]
            (is (= 0 (:prompts-count result)))
            (is (= 0 (count (:results result))))))

        (testing "handles single prompt"
          (let [result (aor/agent-invoke coordinator ["Calculate 2 plus 2"])]
            (is (= 1 (:prompts-count result)))
            (is (= 1 (count (:results result))))

            (let [prompt-result (first (:results result))]
              (is (= "Calculate 2 plus 2" (:prompt prompt-result))))))))))
