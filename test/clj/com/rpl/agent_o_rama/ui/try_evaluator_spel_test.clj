(ns com.rpl.agent-o-rama.ui.try-evaluator-spel-test
  "Spel (Playwright) coverage for the Evaluators \"Try Evaluator\" flow: create evaluator,
  client-side JSON validation, successful inline result, and server-side evaluator errors
  surfaced in the UI.

  Playwright JS already exercises similar paths in test/e2e/evaluators.spec.js; this test
  mirrors the critical interactions using com.blockether/spel for eventual migration."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.blockether.spel.core :as spel]
   [com.blockether.spel.locator :as locator]
   [com.blockether.spel.page :as page]
   [com.rpl.agent.e2e-test-agent :refer [E2ETestAgentModule]]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth])
  (:import
   [com.microsoft.playwright.options AriaRole]))

(defn- ok!
  "Spel fns return `com.blockether.anomaly` maps on failure instead of throwing."
  [x]
  (when (and (map? x) (contains? x :anomaly/category))
    (throw (ex-info "Spel anomaly" x)))
  x)

(def ^:private system (volatile! nil))

(deftest try-evaluator-spel-test
  (eth/with-ui-system [system E2ETestAgentModule]
    (let [env @system
          base-url (eth/local-module-base-url env)
          unique-suffix (subs (str (random-uuid)) 0 8)
          eval-name (str "spel-fail-on-output-" unique-suffix)]
      (spel/with-testing-page {:browser-type :chromium
                               :channel "chrome"
                               :headless true}
         [pg]
         (testing "navigate to module"
           (ok! (page/navigate pg base-url))
           ;; SPA: avoid :networkidle (can hang with long-lived connections).
           (ok! (page/wait-for-load-state pg :domcontentloaded))
           (is (str/includes? (ok! (page/url pg)) "/agents/")))

         (testing "open evaluations page"
           ;; Direct navigation is more reliable than clicking nav (same destination as :module/evaluations).
           (ok! (page/navigate pg (str base-url "/evaluations")))
           (ok! (page/wait-for-load-state pg :domcontentloaded))
           (is (str/includes? (ok! (page/url pg)) "/evaluations")))

         (testing "create evaluator from fail-on-output builder"
           (ok! (locator/click (page/get-by-role pg AriaRole/BUTTON {:name "Create Evaluator"})))
           (let [create-modal (page/get-by-role pg AriaRole/DIALOG)]
             (ok! (locator/wait-for create-modal {:state "visible" :timeout 15000}))
             (ok! (locator/click (locator/loc-get-by-text create-modal "fail-on-output")))
             (ok! (locator/wait-for (locator/loc-get-by-label create-modal "Name") {:state "visible" :timeout 10000}))
             (ok! (locator/fill (locator/loc-get-by-label create-modal "Name") eval-name))
             (ok! (locator/fill (locator/loc-get-by-label create-modal "Description") "Spel try-evaluator test"))
             (ok! (locator/fill (locator/loc-get-by-label create-modal "fail_if_contains") "BAD"))
             (ok! (locator/click
                   (locator/loc-filter (locator/loc-get-by-role create-modal AriaRole/BUTTON)
                                       {:has-text "Submit"})))
             (ok! (locator/wait-for create-modal {:state "hidden" :timeout 20000}))))

         (testing "find new evaluator row"
           (let [search (page/get-by-placeholder pg "Search evaluators...")]
             (when (ok! (locator/is-visible? search))
               (ok! (locator/fill search eval-name))
               (Thread/sleep 400)))
           (let [row (locator/loc-filter (page/get-by-role pg AriaRole/ROW) {:has-text eval-name})]
             (ok! (locator/wait-for row {:state "visible" :timeout 15000}))
             (ok! (locator/click row))))

         (testing "Try Evaluator: invalid JSON, success JSON, and propagated error"
           (ok! (locator/wait-for (page/get-by-role pg AriaRole/BUTTON {:name "Try Evaluator"})
                                  {:state "visible" :timeout 15000}))
           (ok! (locator/click (page/get-by-role pg AriaRole/BUTTON {:name "Try Evaluator"})))
           ;; Details modal hides then try modal opens on a short timeout (see evaluators.cljs).
           (Thread/sleep 400)
           ;; RunEvaluatorModal is the only place with this label; more reliable than dialog title matching.
           (ok! (locator/wait-for (page/get-by-label pg "Model Output (JSON)")
                                  {:state "visible" :timeout 30000}))
           (let [out (page/get-by-label pg "Model Output (JSON)")
                 try-modal (locator/loc-locator out "xpath=ancestor::*[@role=\"dialog\"][1]")]
               (ok! (locator/fill out "{not-json"))
               (ok! (locator/wait-for (locator/loc-get-by-text try-modal #"Invalid JSON")
                                      {:state "visible" :timeout 15000}))
               (ok! (locator/fill out "\"ok\""))
               (ok! (locator/click
                     (locator/loc-filter (locator/loc-get-by-role try-modal AriaRole/BUTTON)
                                         {:has-text "Run Evaluator"})))
               (ok! (locator/wait-for (locator/loc-get-by-text try-modal #"\"passed\?\"")
                                      {:state "visible" :timeout 30000}))
               (is (str/includes? (ok! (locator/text-content try-modal)) "\"passed?\": true"))
               (ok! (locator/fill out "\"has BAD substring\""))
               (ok! (locator/click
                     (locator/loc-filter (locator/loc-get-by-role try-modal AriaRole/BUTTON)
                                         {:has-text "Run Evaluator"})))
               (ok! (locator/wait-for (locator/loc-get-by-text try-modal "Intentional evaluator failure")
                                      {:state "visible" :timeout 30000}))
               (ok! (locator/click
                     (locator/loc-filter (locator/loc-get-by-role try-modal AriaRole/BUTTON)
                                         {:has-text "\u00d7"})))))

         (testing "cleanup: delete evaluator"
           (let [row (locator/loc-filter (page/get-by-role pg AriaRole/ROW) {:has-text eval-name})]
             (ok! (locator/wait-for row {:state "visible" :timeout 10000}))
             (ok! (locator/click (locator/loc-get-by-text row "Delete")))
             (ok! (locator/wait-for row {:state "hidden" :timeout 15000}))))))))
