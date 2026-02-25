(ns com.rpl.agent-o-rama.ui.handlers.datasets-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.datasets])
  (:import [java.util UUID]))

(def ^:private process-example-source
  #'com.rpl.agent-o-rama.impl.ui.handlers.datasets/process-example-source)

(deftest process-example-source-test
  (testing "no source - passes through unchanged"
    (let [example {:input "data" :reference-output "out"}
          result (process-example-source example)]
      (is (= example result))
      (is (nil? (:source-string result)))
      (is (nil? (:trace-link result)))))

  (testing "HumanSource - adds source-string but no trace-link"
    (let [source (aor-types/->HumanSourceImpl "user" nil)
          result (process-example-source {:input "data" :source source})]
      (is (= "human[user]" (:source-string result)))
      (is (nil? (:trace-link result)))))

  (testing "AgentRunSource - adds source-string and trace-link"
    (let [task-id 42
          invoke-id (UUID/fromString "550e8400-e29b-41d4-a716-446655440000")
          source (aor-types/->AgentRunSourceImpl
                  "com.example/MyModule"
                  "MyAgent"
                  (aor-types/->AgentInvokeImpl task-id invoke-id))
          result (process-example-source {:input "data" :source source})]
      (is (= "agent[com.example/MyModule/MyAgent]" (:source-string result)))
      (is (= {:module-id "com.example/MyModule"
              :agent-name "MyAgent"
              :invoke-id "42-550e8400-e29b-41d4-a716-446655440000"}
             (:trace-link result)))))

  (testing "AgentRunSource - invoke-id string correctly prefixes task-id"
    (let [task-id 12345
          invoke-id (UUID/fromString "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
          source (aor-types/->AgentRunSourceImpl
                  "some.module/SomeModule"
                  "SomeAgent"
                  (aor-types/->AgentInvokeImpl task-id invoke-id))
          result (process-example-source {:input "x" :source source})]
      (is (= "12345-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
             (:invoke-id (:trace-link result)))))))
