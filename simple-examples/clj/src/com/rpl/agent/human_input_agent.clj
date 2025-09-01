(ns com.rpl.agent.human-input-agent
  "Demonstrates human input requests and handling within agent nodes.

  Features demonstrated:
  - get-human-input: Request input from human users
  - agent-next-step: Handle human input requests in execution flow
  - provide-human-input: Supply responses to human input requests
  - Human-in-the-loop agent execution patterns"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama
    HumanInputRequest]))

;;; Agent module demonstrating human input functionality
(aor/defagentmodule HumanInputAgentModule
  [topology]

  (->
    topology
    (aor/new-agent "HumanInputAgent")

    ;; Node that collects user preferences through human input
    (aor/node
     "collect-preferences"
     "make-recommendation"
     (fn [agent-node {:keys [category]}]
       (println (format "Collecting preferences for %s category" category))

       ;; Ask for budget preference
       (let [budget-input (aor/get-human-input
                           agent-node
                           (format "What's your budget for %s? ($): " category))
             budget       (try
                            (Double/parseDouble budget-input)
                            (catch NumberFormatException _
                              0.0))]

         ;; Ask for quality preference
         (let [quality-input (aor/get-human-input
                              agent-node
                              "What quality level do you prefer? (basic/premium): ")
               quality       (if (contains? #{"basic" "premium"} quality-input)
                               (keyword quality-input)
                               :basic)]

           ;; Ask for urgency
           (let [urgency-input (aor/get-human-input
                                agent-node
                                "How urgent is this? (low/medium/high): ")
                 urgency       (if (contains? #{"low" "medium" "high"} urgency-input)
                                 (keyword urgency-input)
                                 :medium)]

             (aor/emit! agent-node
                        "make-recommendation"
                        {:category category
                         :budget   budget
                         :quality  quality
                         :urgency  urgency}))))))

    ;; Node that makes recommendations based on collected preferences
    (aor/node
     "make-recommendation"
     nil
     (fn [agent-node {:keys [category budget quality urgency]}]
       (let [recommendation (cond
                              (and (>= budget 1000) (= quality :premium))
                              "High-end option with excellent features"

                              (and (>= budget 500) (= quality :premium))
                              "Mid-range premium option"

                              (and (< budget 200) (= urgency :high))
                              "Budget option for urgent needs"

                              (= quality :basic)
                              "Standard basic option"

                              :else
                              "Balanced mid-range option")
             
             confirmation (aor/get-human-input
                           agent-node
                           (format "Recommendation: %s\nDo you accept this recommendation? (y/n): "
                                   recommendation))]

         (aor/result! agent-node
                      {:category category
                       :preferences {:budget budget
                                     :quality quality
                                     :urgency urgency}
                       :recommendation recommendation
                       :accepted (= confirmation "y")
                       :processed-at (System/currentTimeMillis)}))))))

(defn -main
  "Run the human input agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc HumanInputAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name HumanInputAgentModule))
          agent   (aor/agent-client manager "HumanInputAgent")]

      (println "Human Input Agent Example:")
      (println "This agent will ask you questions to make personalized recommendations.")
      (println)

      ;; Start agent execution
      (let [invoke (aor/agent-initiate agent {:category "laptop"})]

        ;; Handle human input requests
        (loop []
          (let [step (aor/agent-next-step agent invoke)]
            (if (instance? HumanInputRequest step)
              (do
                (print (:prompt step))
                (flush)
                (let [user-response (read-line)]
                  (aor/provide-human-input agent step user-response)
                  (recur)))
              ;; Agent completed
              (let [result (:result step)]
                (println "\nRecommendation process completed!")
                (println "  Category:" (:category result))
                (println "  Your preferences:")
                (println "    Budget: $" (get-in result [:preferences :budget]))
                (println "    Quality:" (get-in result [:preferences :quality]))
                (println "    Urgency:" (get-in result [:preferences :urgency]))
                (println "  Recommendation:" (:recommendation result))
                (println "  Accepted:" (:accepted result))))))

      (println "\nNotice how:")
      (println "- Agents can request human input during execution")
      (println "- Input validation and defaults are handled gracefully")
      (println "- Multiple input requests can be chained together")
      (println "- Human responses influence the final result"))))