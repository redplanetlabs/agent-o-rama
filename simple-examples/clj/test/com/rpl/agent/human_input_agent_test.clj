(ns com.rpl.agent.human-input-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.human-input-agent :refer [HumanInputAgentModule]])
  (:import
   [com.rpl.agentorama
    HumanInputRequest]))

(deftest human-input-agent-test
  (testing "HumanInputAgent example handles human input correctly"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc HumanInputAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name HumanInputAgentModule))
            agent (aor/agent-client manager "HumanInputAgent")]
        
        (testing "collects preferences and makes recommendation"
          (let [invoke (aor/agent-initiate agent {:category "laptop"})]
            
            ;; Handle budget input
            (let [step1 (aor/agent-next-step agent invoke)]
              (is (instance? HumanInputRequest step1))
              (is (.contains (:prompt step1) "budget"))
              (aor/provide-human-input agent step1 "800"))
            
            ;; Handle quality input
            (let [step2 (aor/agent-next-step agent invoke)]
              (is (instance? HumanInputRequest step2))
              (is (.contains (:prompt step2) "quality"))
              (aor/provide-human-input agent step2 "premium"))
            
            ;; Handle urgency input
            (let [step3 (aor/agent-next-step agent invoke)]
              (is (instance? HumanInputRequest step3))
              (is (.contains (:prompt step3) "urgent"))
              (aor/provide-human-input agent step3 "medium"))
            
            ;; Handle confirmation input
            (let [step4 (aor/agent-next-step agent invoke)]
              (is (instance? HumanInputRequest step4))
              (is (.contains (:prompt step4) "Recommendation"))
              (aor/provide-human-input agent step4 "y"))
            
            ;; Get final result
            (let [result (aor/agent-result agent invoke)]
              (is (= "laptop" (:category result)))
              (is (= 800.0 (get-in result [:preferences :budget])))
              (is (= :premium (get-in result [:preferences :quality])))
              (is (= :medium (get-in result [:preferences :urgency])))
              (is (= "Mid-range premium option" (:recommendation result)))
              (is (= true (:accepted result)))
              (is (number? (:processed-at result))))))
        
        (testing "handles invalid inputs with defaults"
          (let [invoke (aor/agent-initiate agent {:category "phone"})]
            
            ;; Provide invalid budget (should default to 0.0)
            (let [step1 (aor/agent-next-step agent invoke)]
              (aor/provide-human-input agent step1 "invalid-number"))
            
            ;; Provide invalid quality (should default to :basic)
            (let [step2 (aor/agent-next-step agent invoke)]
              (aor/provide-human-input agent step2 "invalid-quality"))
            
            ;; Provide invalid urgency (should default to :medium)
            (let [step3 (aor/agent-next-step agent invoke)]
              (aor/provide-human-input agent step3 "invalid-urgency"))
            
            ;; Decline recommendation
            (let [step4 (aor/agent-next-step agent invoke)]
              (aor/provide-human-input agent step4 "n"))
            
            (let [result (aor/agent-result agent invoke)]
              (is (= 0.0 (get-in result [:preferences :budget])))
              (is (= :basic (get-in result [:preferences :quality])))
              (is (= :medium (get-in result [:preferences :urgency])))
              (is (= false (:accepted result))))))))))