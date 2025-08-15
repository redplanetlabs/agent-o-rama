(ns com.rpl.agent.customer-support-test
  "Tests for AI-powered customer support agent functionality.

   Tests cover all tool functions including flight operations, hotel bookings,
   car rentals, and policy lookups. Uses IPC test functionality for
   integration testing with the real agent system."
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent.customer-support :as cs]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :refer :all]
   [com.rpl.rama.path :refer :all]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-helpers :refer :all])
  (:import
   [dev.langchain4j.data.message
    UserMessage]))

;; Test helpers

(defn with-customer-support-ipc
  "Test helper that sets up IPC environment for customer support tests."
  [test-fn]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module!
     ipc
     cs/CustomerSupportModule
     {:tasks 2 :threads 1})

    (let [module-name (get-module-name cs/CustomerSupportModule)
          agent-manager (aor/agent-manager ipc module-name)
          initializer (aor/agent-client agent-manager "data-initializer")
          agent (aor/agent-client agent-manager "customer-support")]

      ;; Initialize reference data stores before running tests
      (aor/agent-invoke initializer)

      (test-fn agent)
      (rtest/destroy-module! ipc module-name))))

(deftest customer-support-tools-test
  (testing "customer support tools are properly defined"
    (is (vector? cs/CUSTOMER-SUPPORT-TOOLS))
    (is (seq cs/CUSTOMER-SUPPORT-TOOLS))
    (is (every? map? cs/CUSTOMER-SUPPORT-TOOLS))))

;; Tests for individual tool functions using IPC

(deftest fetch-user-flight-information-test
  (testing "flight information retrieval through agent"
    (with-customer-support-ipc
      (fn [agent]
       ;; Test the search flights functionality which doesn't require existing
       ;; bookings
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage.
                        "Search for flights from ZUR to JFK on 2024-03-15")]
                      {:passenger-id "TEST123"})]
          (is (string? result))
          (is (not (str/blank? result)))
          (is (str/includes? (str/lower-case result) "flight")))))))

(deftest search-flights-test
  (testing "flight search through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let
         [result
          (aor/agent-invoke
           agent
           [(UserMessage.
             "I need to find flights from ZUR to JFK departing on March 15, 2024")]
           {:passenger-id "TEST124"})]
          (is (string? result))
          (is (not (str/blank? result)))
         ;; Should mention flights or search results
          (is (or (str/includes? (str/lower-case result) "flight")
                  (str/includes? (str/lower-case result) "search"))))))))

(deftest update-ticket-to-new-flight-test
  (testing "ticket update through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
       ;; First search for flights to establish context
        (let [search-result
              (aor/agent-invoke
               agent
               [(UserMessage.
                 "Can you help me change my ticket T456 to flight LX102?")]
               {:passenger-id "TEST125"})]
          (is (string? search-result))
          (is (not (str/blank? search-result))))))))

(deftest cancel-ticket-test
  (testing "ticket cancellation through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "I need to cancel my ticket number T789")]
                      {:passenger-id "TEST126"})]
          (is (string? result))
          (is (not (str/blank? result)))
         ;; Should respond about cancellation
          (is (str/includes? (str/lower-case result) "cancel")))))))

(deftest search-excursions-test
  (with-customer-support-ipc
    (fn [agent]
      (testing "search for excursions in New York"
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "Find excursions in New York")]
                      {:passenger-id "TEST130"})]
          (is (string? result))
          (is (str/includes? result "excursion")))))))

(deftest book-excursion-test
  (with-customer-support-ipc
    (fn [agent]
      (testing "book an excursion"
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "Book the Statue of Liberty Tour for March 20th")]
                      {:passenger-id "TEST131"})]
          (is (string? result))
          (is (str/includes? result "book")))))))

(deftest update-car-rental-test
  (with-customer-support-ipc
    (fn [agent]
      (testing "update car rental booking"
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "I need to update my car rental booking to different dates")]
                      {:passenger-id "TEST132"})]
          (is (string? result))
          (is (or (str/includes? result "update")
                  (str/includes? result "booking"))))))))

(deftest cancel-car-rental-test
  (with-customer-support-ipc
    (fn [agent]
      (testing "cancel car rental booking"
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "I want to cancel my car rental booking")]
                      {:passenger-id "TEST133"})]
          (is (string? result))
          (is (or (str/includes? result "cancel")
                  (str/includes? result "booking"))))))))

(deftest web-search-test
  (with-customer-support-ipc
    (fn [agent]
      (testing "search for travel information"
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "Search for weather information in New York")]
                      {:passenger-id "TEST134"})]
          (is (string? result))
          (is (or (str/includes? result "search")
                  (str/includes? result "information"))))))))

(deftest search-hotels-test
  (testing "hotel search through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "Can you help me find hotels in New York?")]
                      {:passenger-id "TEST127"})]
          (is (string? result))
          (is (not (str/blank? result)))
          (is (or (str/includes? (str/lower-case result) "hotel")
                  (str/includes? (str/lower-case result) "accommodation"))))))))

(deftest book-hotel-test
  (testing "hotel booking through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result
              (aor/agent-invoke
               agent
               [(UserMessage.
                 "I'd like to book the Grand Hotel from March 15 to March 17")]
               {:passenger-id "TEST128"})]
          (is (string? result))
          (is (not (str/blank? result))))))))

(deftest search-car-rentals-test
  (testing "car rental search through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "I need a car rental in New York")]
                      {:passenger-id "TEST129"})]
          (is (string? result))
          (is (not (str/blank? result)))
          (is (or (str/includes? (str/lower-case result) "car")
                  (str/includes? (str/lower-case result) "rental"))))))))

(deftest book-car-rental-test
  (testing "car rental booking through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result
              (aor/agent-invoke
               agent
               [(UserMessage.
                 "I want to book car rental R001 from March 15 to March 17")]
               {:passenger-id "TEST130"})]
          (is (string? result))
          (is (not (str/blank? result))))))))

(deftest lookup-policy-test
  (testing "policy lookup through agent conversation"
    (with-customer-support-ipc
      (fn [agent]
        (let [result (aor/agent-invoke
                      agent
                      [(UserMessage. "What is your baggage policy?")]
                      {:passenger-id "TEST131"})]
          (is (string? result))
          (is (not (str/blank? result)))
          (is (or (str/includes? (str/lower-case result) "baggage")
                  (str/includes? (str/lower-case result) "policy"))))))))

(deftest customer-support-module-integration-test
  (testing "full agent module execution"
    (when (some? (System/getenv "OPENAI_API_KEY"))
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc
                              cs/CustomerSupportModule
                              {:tasks 4 :threads 2})

        (let [module-name (get-module-name cs/CustomerSupportModule)
              agent-manager (aor/agent-manager ipc module-name)
              initializer (aor/agent-client agent-manager "data-initializer")
              agent (aor/agent-client agent-manager "customer-support")]

          ;; Initialize reference data stores before running tests
          (aor/agent-invoke initializer)

          (testing "flight search interaction"
            (let [result (aor/agent-invoke
                          agent
                          [(UserMessage. "Search for flights from ZUR to JFK")]
                          {:passenger-id "TEST123"})]
              (is (string? result))
              (is (not (str/blank? result)))))

          (testing "policy lookup interaction"
            (let [result (aor/agent-invoke
                          agent
                          [(UserMessage. "What is the baggage policy?")]
                          {:passenger-id "TEST124"})]
              (is (string? result))
              (is (not (str/blank? result))))))))))
