(ns com.rpl.agent-o-rama.ui.spel-navigation-test
  "POC: navigation.spec.js tests rewritten in Clojure using spel
   (idiomatic Clojure wrapper for Playwright Java).

   Why this is interesting vs. the current setup:

   vs. Playwright (Node.js):
   - Pure Clojure — no context switch between infra code and test code
   - Can call aor/agent-initiate, wait-for-microbatch-processed-count, etc.
     directly in the test body without a helper server or shared state
   - Allure reporting with embedded Playwright traces out of the box
   - Same server setup (ci-playwright-setup) reused unchanged

   vs. Etaoin:
   - Backed by Playwright Java instead of Selenium — faster, more reliable
   - Chromium/Firefox/WebKit support without Docker containers
   - Semantic selectors: get-by-role, get-by-text, get-by-label
   - Auto-waiting on all actions (no explicit Thread/sleep or wait-visible loops)

   To run locally (server must be running on port 1974):
     lein with-profile +dev,+spel run -m lazytest.main \\
       --namespace com.rpl.agent-o-rama.ui.spel-navigation-test"
  (:require
   [com.blockether.spel.allure :refer [defdescribe describe it expect]]
   [com.blockether.spel.locator :as locator]
   [com.blockether.spel.page :as page]
   [com.blockether.spel.test-fixtures
    :refer [*page* with-browser with-page with-playwright]])
  (:import
   [com.microsoft.playwright.options AriaRole]))

(def ^:private base-url "http://localhost:1974")

(def ^:private e2e-agent-row-name
  "com.rpl.agent.e2e-test-agent/E2ETestAgentModule E2ETestAgent")

;; Browser lifecycle: one Playwright instance → one Browser → fresh Page per test.
;; The application server is started externally (same as playwright.yml).
(defdescribe navigation-tests
  {:context [with-playwright with-browser with-page]}

  (describe "e2e test agent module exists"

    (it "loads the homepage and navigates to an agent detail page"
      (page/set-default-timeout! *page* 30000)
      (page/navigate *page* base-url)
      (expect (re-find #"Agent-o-rama" (page/title *page*)))

      ;; Playwright auto-waits up to the default timeout for the row to appear.
      (let [agent-row (page/get-by-role *page* AriaRole/ROW
                                        {:name e2e-agent-row-name})]
        (locator/click agent-row)
        (expect (re-find #"/agents/.*E2ETestAgentModule" (page/url *page*))))))

  (describe "navigation bar"

    (it "datasets link is visible on agent detail page"
      (page/set-default-timeout! *page* 30000)
      (page/navigate *page* base-url)

      (let [agent-row (page/get-by-role *page* AriaRole/ROW
                                        {:name e2e-agent-row-name})]
        (locator/click agent-row))

      (let [datasets-link (page/get-by-text *page* "Datasets & Experiments")]
        (expect (locator/is-visible? datasets-link))
        (locator/click datasets-link)
        (expect (re-find #"/datasets" (page/url *page*)))))))
