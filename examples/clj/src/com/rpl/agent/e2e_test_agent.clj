(ns com.rpl.agent.e2e-test-agent
  "A specialized agent module for End-to-End (E2E) testing of the Agent-O-Rama UI.
  
  This agent's behavior is entirely controllable via its input parameters, allowing tests to
  deterministically simulate various scenarios, including:
  - Node failures with successful retries.
  - Agent-level failures by exceeding max retries.
  - Execution paths with very long node names to test UI rendering.
  - Controlled final outputs to test evaluator behavior.

  It has no external dependencies (like API keys) and is designed to replace
  API-dependent agents in the E2E test suite."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.store :as store]
   [clojure.string :as str]))

(defonce E2E_RETRY_STORE "$$e2e-retries")

(defn- check-for-failure!
  "Helper function to deterministically fail a node based on params.
   It uses a PStateStore to track the number of times a node has been entered for a given run,
   allowing simulation of retries."
  [agent-node params current-node-name]
  (let [fail-at-node (get params "fail-at-node")
        retries-before-success (get params "retries-before-success")
        run-id (get params "run-id")]
    ;; Only check for failure if this is the target node
    (when (= fail-at-node current-node-name)
      (let [retry-store (aor/get-store agent-node E2E_RETRY_STORE)
            ;; Get the current attempt count for this specific run
            attempt-count (or (store/get retry-store run-id) 0)]

        (if (< attempt-count retries-before-success)
          (do
            ;; Increment the attempt count in the persistent store
            (store/put! retry-store run-id (inc attempt-count))
            ;; Throw an exception to trigger the agent framework's retry mechanism
            (throw (ex-info (str "Intentional E2E test failure for node '" current-node-name "'. "
                                 "Attempt " (inc attempt-count) " of " retries-before-success ".")
                            {"run-id" run-id
                             "node" current-node-name
                             "attempt" (inc attempt-count)})))
          ;; If we've reached the desired number of retries, we let the execution succeed.
          (println (str "E2E Agent: Node '" current-node-name "' succeeded after " attempt-count " failures.")))))))

(aor/defagentmodule E2ETestAgentModule
  [topology]

  ;; A persistent store to track retry attempts for each test run.
  ;; Using a PStateStore ensures state is maintained correctly across retries,
  ;; even in a distributed environment.
  (aor/declare-key-value-store topology E2E_RETRY_STORE String Long)

  ;; --- Evaluator Builders for Testing ---
  ;; These will be used in the Playwright tests to verify evaluator functionality.

  ;; An evaluator that returns a random float, perfect for testing sorting.
  (aor/declare-evaluator-builder
   topology
   "random-float"
   "Returns a random float between 0 and 1."
   (fn [params]
     (fn [fetcher input ref-output output]
       {"score" (rand)})))

  ;; An evaluator that can be made to fail based on its input.
  (aor/declare-evaluator-builder
   topology
   "fail-on-output"
   "Fails if the output contains a specific substring."
   (fn [params]
     (let [fail-trigger (get params "fail_if_contains")]
       (fn [fetcher input ref-output output]
         (if (str/includes? (str output) fail-trigger)
           (throw (ex-info "Intentional evaluator failure." {"trigger" fail-trigger}))
           {"passed?" true}))))
   {:params {"fail_if_contains" {:description "Substring that causes failure."}}})

  ;; A comparative evaluator for testing that part of the UI.
  (aor/declare-comparative-evaluator-builder
   topology
   "select-longest"
   "Selects the longest string from a list of outputs."
   (fn [params]
     (fn [fetcher input ref-output outputs]
       (let [indexed-outputs (map-indexed vector outputs)
             longest (apply max-key (fn [[idx out]] (count (str out))) indexed-outputs)]
         {"index" (first longest)
          "longest_value" (second longest)}))))

  ;; A comparative evaluator that does NOT return an 'index' key.
  ;; This is to test the 'Evals' column rendering in comparative experiments.
  (aor/declare-comparative-evaluator-builder
   topology
   "random-float-comparative"
   "Returns a random float score for the set of outputs."
   (fn [params]
     (fn [fetcher input ref-output outputs]
       {"random_score" (rand)})))

  ;; A comparative evaluator that returns a random index.
  ;; This is to test the selector evaluator dropdown when multiple selectors exist.
  (aor/declare-comparative-evaluator-builder
   topology
   "select-random"
   "Randomly selects one of the outputs as the winner."
   (fn [params]
     (fn [fetcher input ref-output outputs]
       (let [random-index (rand-int (count outputs))]
         {"index" random-index
          "explanation" (str "Randomly selected output " random-index)}))))

  ;; --- The E2E Test Agent Definition ---
  (-> topology
      (aor/new-agent "E2ETestAgent")

      ;; 1. START: Receives control params and routes to the correct path.
      (aor/node
       "start"
       ["a_very_long_node_name_that_is_designed_specifically_to_test_ui_overflow_rendering_and_text_wrapping_behavior"
        "short_path_node"]
       (fn [agent-node params]
         (let [run-id (get params "run-id")
               retry-store (aor/get-store agent-node E2E_RETRY_STORE)]

           ;; Initialize or reset the retry count for this run
           (when run-id
             (store/put! retry-store run-id 0))

           (check-for-failure! agent-node params "start")

           (if (get params "long-node-names?")
             (aor/emit! agent-node "a_very_long_node_name_that_is_designed_specifically_to_test_ui_overflow_rendering_and_text_wrapping_behavior" params)
             (aor/emit! agent-node "short_path_node" params)))))

      ;; 2a. LONG PATH: A node with a very long name for UI overflow testing.
      (aor/node
       "a_very_long_node_name_that_is_designed_specifically_to_test_ui_overflow_rendering_and_text_wrapping_behavior"
       "processing_node"
       (fn [agent-node params]
         (check-for-failure! agent-node params "a_very_long_node_name_that_is_designed_specifically_to_test_ui_overflow_rendering_and_text_wrapping_behavior")
         (aor/emit! agent-node "processing_node" params)))

      ;; 2b. SHORT PATH: The alternative, short-named path.
      (aor/node
       "short_path_node"
       "processing_node"
       (fn [agent-node params]
         (check-for-failure! agent-node params "short_path_node")
         (aor/emit! agent-node "processing_node" params)))

      ;; 3. PROCESSING: A common node where both paths converge.
      (aor/node
       "processing_node"
       "final_result_node"
       (fn [agent-node params]
         (check-for-failure! agent-node params "processing_node")
         (aor/emit! agent-node "final_result_node" params)))

      ;; 4. FINAL RESULT: Produces the controlled output.
      (aor/node
       "final_result_node"
       nil
       (fn [agent-node params]
         (check-for-failure! agent-node params "final_result_node")
         (aor/result! agent-node (get params "output-value"))))))
