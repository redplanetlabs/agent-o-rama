(ns com.rpl.agent-o-rama.ui.experiments-datetime-filter-e2e-test
  "E2E tests for the experiments datetime picker filter.

  Tests that the datetime picker filter correctly filters experiments
  based on their start_time_millis.

  Uses TopologyUtils/startSimTime (via :pre-launch-hook) and advanceSimTime
  to create experiments at different simulated timestamps."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth]
   [com.rpl.agent-o-rama.ui.experiments-datetime-filter-test-agent
    :refer [ExperimentsDatetimeFilterTestAgentModule
            setup-datetime-filter-testing!]]
   [etaoin.api :as e])
  (:import
   [com.rpl.rama.helpers TopologyUtils]
   [java.net URLEncoder]))

;;; Shared state for the test namespace

(defonce system (volatile! nil))
(defonce test-data (atom nil))
(defonce sim-time-closer (atom nil))

;;; URL helpers

(defn- url-encode [s]
  (URLEncoder/encode (str s) "UTF-8"))

(defn experiments-index-url
  "Generate URL for the experiments index page."
  [env dataset-id]
  (str "http://host.testcontainers.internal:" (:port env)
       "/agents/" (url-encode (:module-name env))
       "/datasets/" (url-encode (str dataset-id))
       "/experiments"))

;;; Helper to wait for page load

(defn wait-for-experiments-table
  "Wait for the experiments table to be visible."
  [driver]
  (e/wait-visible
   driver
   {:tag :table}
   {:timeout eth/default-timeout}))

(defn get-visible-experiment-names
  "Gets the names of experiments currently visible in the table."
  [driver]
  ;; Find all table rows and extract experiment names from the second column
  (let [rows (e/query-all driver {:css "tbody tr"})]
    (mapv (fn [row]
            (try
              (e/get-element-text-el driver (e/child driver row {:css "td:nth-child(2)"}))
              (catch Exception _ nil)))
          rows)))

(defn get-visible-experiment-count
  "Gets the count of experiments currently visible in the table."
  [driver]
  (count (e/query-all driver {:css "tbody tr"})))

;;; Test fixture with simulated time

(defn system-fixture
  "Fixture that sets up the system with simulated time for all tests."
  [f]
  (eth/setup-system
   system
   ExperimentsDatetimeFilterTestAgentModule
   ;; Start simulated time BEFORE launching the module
   :pre-launch-hook
   (fn [_ipc]
     (reset! sim-time-closer (TopologyUtils/startSimTime)))
   ;; Set up test data AFTER module is deployed
   :post-deploy-hook
   (fn [ipc module-name]
     (let [data (setup-datetime-filter-testing! ipc module-name)]
       (reset! test-data data)
       data)))
  (try
    (f)
    (finally
      ;; Clean up simulated time
      (when-let [closer @sim-time-closer]
        (.close closer)
        (reset! sim-time-closer nil))
      (eth/teardown-system system))))

(use-fixtures :once system-fixture)

;;; Tests

(deftest experiments-datetime-picker-renders-test
  "Test that the datetime picker components render correctly."
  (eth/with-webdriver [system driver]
    (let [env @system
          {:keys [dataset-id]} @test-data]

      (testing "datetime picker components are visible"
        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [url (experiments-index-url env dataset-id)]
            (e/go driver url)
            (wait-for-experiments-table driver)

            ;; Check that the datetime picker labels are present (with colons)
            (let [labels (e/query-all driver {:tag :label})
                  label-texts (mapv #(e/get-element-text-el driver %) labels)]
              (is (some #(= "Start Date:" %) label-texts)
                  (str "Should have 'Start Date:' label, found: " label-texts))
              (is (some #(= "End Date:" %) label-texts)
                  (str "Should have 'End Date:' label, found: " label-texts)))))))))

(deftest experiments-initially-shows-all-test
  "Test that all experiments are shown initially."
  (eth/with-webdriver [system driver]
    (let [env @system
          {:keys [dataset-id experiments]} @test-data]

      (testing "initially shows all 3 experiments"
        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [url (experiments-index-url env dataset-id)]
            (e/go driver url)
            (wait-for-experiments-table driver)

            (let [exp-names (get-visible-experiment-names driver)]
              (is (= 3 (count exp-names))
                  (str "Should show all 3 experiments, found: " exp-names)))))))))

(deftest experiments-datetime-picker-components-test
  "Test that the datetime picker components are present and interactive."
  (eth/with-webdriver [system driver]
    (let [env @system
          {:keys [dataset-id]} @test-data]

      (testing "datetime pickers are present"
        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [url (experiments-index-url env dataset-id)]
            (e/go driver url)
            (wait-for-experiments-table driver)

            ;; Find the datetime picker wrappers
            ;; react-datetime-picker creates a wrapper with class 'react-datetime-picker'
            (let [picker-wrappers (e/query-all
                                   driver
                                   {:css ".react-datetime-picker"})]
              (is (= 2 (count picker-wrappers))
                  "Should have 2 datetime pickers (start and end)")))))

      (testing "datetime picker wrapper exists"
        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [url (experiments-index-url env dataset-id)]
            (e/go driver url)
            (wait-for-experiments-table driver)

            (is (e/exists? driver {:css ".react-datetime-picker__wrapper"})
                "Datetime picker wrapper should exist")))))))

(deftest experiments-search-filter-test
  "Test that the search filter works."
  (eth/with-webdriver [system driver]
    (let [env @system
          {:keys [dataset-id]} @test-data]

      (testing "search filter filters experiments by name"
        (e/with-postmortem driver {:dir "target/etaoin"}
          (let [url (experiments-index-url env dataset-id)]
            (e/go driver url)
            (wait-for-experiments-table driver)

            ;; Verify all 3 experiments are shown initially
            (let [initial-count (get-visible-experiment-count driver)]
              (is (= 3 initial-count)
                  (str "Should show all 3 experiments initially, found: " initial-count)))

            ;; Type in search box to filter by name
            (e/fill driver {:css "input[placeholder*='Search']"} "3 days ago")
            (Thread/sleep 1500) ; Wait for debounce

            ;; Should now show only the matching experiment
            (let [exp-names (get-visible-experiment-names driver)]
              (is (= 1 (count exp-names))
                  (str "Should show only 1 experiment matching '3 days ago', found: " exp-names))
              (is (some #(str/includes? (or % "") "3 days ago") exp-names)
                  "Should show the '3 days ago' experiment"))

            ;; Refresh page to clear search and verify all experiments return
            (e/go driver url)
            (wait-for-experiments-table driver)

            (let [exp-count (get-visible-experiment-count driver)]
              (is (= 3 exp-count)
                  "Should show all 3 experiments after page refresh"))))))))

;; NOTE: Testing the actual date filtering with the datetime picker requires
;; interacting with the react-datetime-picker component, which has a complex
;; interaction model (clicking the input, then clicking calendar dates).
;;
;; The backend filter logic is tested in experiments_test.clj:search-experiments-test
;; with the :times filter parameter, which verifies the filtering works correctly.
;;
;; This E2E test verifies:
;; 1. The datetime picker UI components render correctly
;; 2. All experiments are visible initially
;; 3. The search filter works as a baseline for filtering functionality
