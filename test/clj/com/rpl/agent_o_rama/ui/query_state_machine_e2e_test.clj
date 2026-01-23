(ns com.rpl.agent-o-rama.ui.query-state-machine-e2e-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama.ui.etaoin-test-helpers :as eth]
   [com.rpl.agent-o-rama.ui.feedback-test-agent :refer [FeedbackTestAgentModule]]
   [com.rpl.test-helpers :as th]
   [etaoin.api :as e]))

(defonce system (volatile! nil))

(defn- debug-url
  [env]
  (str "http://host.testcontainers.internal:" (:port env) "/__debug__/queries"))

(defn- fill-number
  [driver data-id value]
  (e/fill driver {:data-id data-id} (str value)))

(defn- text-by-id
  [driver data-id]
  (e/get-element-text-el driver (e/query driver {:data-id data-id})))

(defn- wait-for-text
  [driver data-id expected]
  (when-not (th/condition-attained?*
             #(= expected (text-by-id driver data-id))
             {:max-wait 10000
              :initial-delay 50
              :max-delay 250
              :backoff-factor 1.5})
    (throw (ex-info "Timed out waiting for text"
                    {:data-id data-id :expected expected}))))

(defn- wait-for-int-at-least
  [driver data-id min-value]
  (when-not (th/condition-attained?*
             #(let [text (text-by-id driver data-id)
                    value (try
                            (Long/parseLong text)
                            (catch Exception _ -1))]
                (>= value min-value))
             {:max-wait 15000
              :initial-delay 50
              :max-delay 250
              :backoff-factor 1.5})
    (throw (ex-info "Timed out waiting for value"
                    {:data-id data-id :min-value min-value}))))

(deftest exponential-retry-test
  (eth/with-system [system FeedbackTestAgentModule]
    (eth/with-webdriver [system driver]
      (testing "retries with exponential backoff until success"
        (let [env @system]
          (e/go driver (debug-url env))
          (eth/wait-visible driver "debug-query-page")

          (fill-number driver "debug-delay-ms" 0)
          (fill-number driver "debug-failures-left" 2)
          (fill-number driver "debug-timeout-ms" 1000)
          (fill-number driver "debug-refetch-interval-ms" 0)
          (fill-number driver "debug-retry-base-ms" 100)
          (fill-number driver "debug-retry-max-ms" 1000)
          (fill-number driver "debug-retry-factor" 2)

          (e/click driver {:data-id "debug-start"})
          (wait-for-text driver "query-status" "success")

          (is (= "0" (text-by-id driver "query-retry-count")))
          (is (= "3" (text-by-id driver "query-request-count"))))))))

(deftest long-response-no-overlap-test
  (eth/with-system [system FeedbackTestAgentModule]
    (eth/with-webdriver [system driver]
      (testing "long responses never overlap requests"
        (let [env @system]
          (e/go driver (debug-url env))
          (eth/wait-visible driver "debug-query-page")

          (fill-number driver "debug-delay-ms" 500)
          (fill-number driver "debug-failures-left" 0)
          (fill-number driver "debug-timeout-ms" 2000)
          (fill-number driver "debug-refetch-interval-ms" 100)
          (fill-number driver "debug-retry-base-ms" 200)
          (fill-number driver "debug-retry-max-ms" 2000)
          (fill-number driver "debug-retry-factor" 2)

          (e/click driver {:data-id "debug-start"})
          (wait-for-int-at-least driver "query-request-count" 2)

          (is (= "1" (text-by-id driver "query-max-in-flight"))))))))

(deftest timeout-results-in-error-test
  (eth/with-system [system FeedbackTestAgentModule]
    (eth/with-webdriver [system driver]
      (testing "timeout transitions query to error"
        (let [env @system]
          (e/go driver (debug-url env))
          (eth/wait-visible driver "debug-query-page")

          (fill-number driver "debug-delay-ms" 1500)
          (fill-number driver "debug-failures-left" 0)
          (fill-number driver "debug-timeout-ms" 200)
          (fill-number driver "debug-refetch-interval-ms" 0)
          (fill-number driver "debug-retry-base-ms" 0)
          (fill-number driver "debug-retry-max-ms" 0)
          (fill-number driver "debug-retry-factor" 2)

          (e/click driver {:data-id "debug-start"})
          (wait-for-text driver "query-status" "error")

          (is (= "1" (text-by-id driver "query-retry-count")))))))))
