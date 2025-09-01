(ns com.rpl.agent.tools-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.tools-agent :refer [ToolsAgentModule create-tool-request]]))

(deftest tools-agent-test
  (testing "ToolsAgent example produces expected tool execution behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc ToolsAgentModule {:tasks 2 :threads 2})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name ToolsAgentModule))
            coordinator (aor/agent-client manager "ToolsCoordinator")]
        
        (testing "executes calculator tools correctly"
          (let [requests [(create-tool-request "calculator" {"operation" "add" "a" "10" "b" "5"})
                          (create-tool-request "calculator" {"operation" "multiply" "a" "6" "b" "7"})
                          (create-tool-request "calculator" {"operation" "divide" "a" "20" "b" "4"})]
                result (aor/agent-invoke coordinator requests)]
            
            (is (= "tools-execution-complete" (:action result)))
            (is (= 3 (:requests-count result)))
            (is (= 3 (:results-count result)))
            (is (number? (:processed-at result)))
            
            ;; Results should be strings representing the calculations
            (let [results (:results result)]
              (is (= 3 (count results)))
              (is (some #(= "15.0" %) results)) ; 10 + 5
              (is (some #(= "42.0" %) results)) ; 6 * 7  
              (is (some #(= "5.0" %) results))))) ; 20 / 4
        
        (testing "executes string processing tools correctly"
          (let [requests [(create-tool-request "string-processor" {"text" "hello" "operation" "uppercase"})
                          (create-tool-request "string-processor" {"text" "WORLD" "operation" "lowercase"})
                          (create-tool-request "string-processor" {"text" "reverse" "operation" "reverse"})
                          (create-tool-request "string-processor" {"text" "count" "operation" "length"})]
                result (aor/agent-invoke coordinator requests)]
            
            (is (= 4 (:requests-count result)))
            (is (= 4 (:results-count result)))
            
            (let [results (:results result)]
              (is (some #(= "HELLO" %) results))
              (is (some #(= "world" %) results))
              (is (some #(= "esrever" %) results)) ; "reverse" reversed
              (is (some #(= "5" %) results))))) ; length of "count"
        
        (testing "executes system info tools correctly"
          (let [requests [(create-tool-request "system-info" {"type" "time"})
                          (create-tool-request "system-info" {"type" "memory"})
                          (create-tool-request "system-info" {"type" "java-version"})]
                result (aor/agent-invoke coordinator requests)]
            
            (is (= 3 (:requests-count result)))
            (is (= 3 (:results-count result)))
            
            (let [results (:results result)]
              ;; Time should be a number string (milliseconds)
              (is (some #(re-matches #"\d+" %) results))
              ;; Memory should contain "Total:" and "bytes"
              (is (some #(.contains % "Total:") results))
              (is (some #(.contains % "bytes") results))
              ;; Java version should be a version string
              (is (some #(re-matches #"\d+\.\d+.*" %) results)))))
        
        (testing "handles error cases gracefully"
          (let [requests [(create-tool-request "calculator" {"operation" "divide" "a" "10" "b" "0"}) ; Division by zero
                          (create-tool-request "string-processor" {"text" "test" "operation" "unknown"}) ; Unknown operation
                          (create-tool-request "system-info" {"type" "invalid"})] ; Invalid type
                result (aor/agent-invoke coordinator requests)]
            
            (is (= 3 (:requests-count result)))
            (is (= 3 (:results-count result)))
            
            (let [results (:results result)]
              (is (some #(.contains % "Error") results))
              (is (some #(.contains % "Division by zero") results))
              (is (some #(.contains % "Unknown") results))))))))))