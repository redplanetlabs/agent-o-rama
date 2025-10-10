(ns com.rpl.agent-o-rama.ui.rules-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.rules :as rules]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.dom])); Load DOM setup before tests

(deftest rules-page-exists
  ;; Tests that the rules-page component exists and is callable
  (testing "rules-page"
    (testing "component exists"
      (is (fn? rules/rules-page)
          "rules-page should be a function component"))))

(deftest process-eval-action-info-test
  ;; Tests the special handling for aor/eval actions with AgentInvokeImpl
  (testing "process-eval-action-info"
    (testing "extracts invoke from info-map for aor/eval actions"
      (let [action-params {"info-map" {"invoke" {:task-id 123 :agent-id "abc"}
                                       "other" "data"}}
            result (rules/process-eval-action-info action-params)]
        (is (= {:task-id 123 :agent-id "abc"} (:eval-invoke result))
            "should extract invoke from info-map")
        (is (= {"other" "data"} (get-in result [:action-params "info-map"]))
            "should remove invoke from info-map in action-params")))

    (testing "returns nil eval-invoke when no invoke in info-map"
      (let [action-params {"info-map" {"other" "data"}}
            result (rules/process-eval-action-info action-params)]
        (is (nil? (:eval-invoke result))
            "should return nil eval-invoke when no invoke present")
        (is (= action-params (:action-params result))
            "should return unchanged action-params")))))

(deftest agent-invoke->url-test
  ;; Tests the URL generation helper for agent invocations
  (testing "agent-invoke->url"
    (testing "generates correct URL for agent invocation"
      (let [url (common/agent-invoke->url "TestModule" "TestAgent" {:task-id 42 :agent-invoke-id "xyz"})]
        (is (= "/agents/TestModule/agent/TestAgent/invocations/42-xyz" url)
            "should generate correct invocation URL")))

    (testing "returns nil when agent-invoke is nil"
      (let [url (common/agent-invoke->url "TestModule" "TestAgent" nil)]
        (is (nil? url)
            "should return nil when agent-invoke is nil")))

    (testing "URL encodes module-id and agent-name"
      (let [url (common/agent-invoke->url "Test Module" "Test Agent" {:task-id 1 :agent-invoke-id "a"})]
        (is (= "/agents/Test%20Module/agent/Test%20Agent/invocations/1-a" url)
            "should URL-encode spaces in module-id and agent-name")))))

(deftest add-rule-form-test
  ;; Tests the Add Rule form configuration
  (testing "add-rule form"
    (testing "has correct initial fields"
      (let [form-spec (get @forms/form-specs :add-rule)
            initial-fields-fn (get-in form-spec [:main :initial-fields])
            initial-fields (initial-fields-fn {})]
        (is (= "" (:rule-name initial-fields))
            "should initialize rule-name to empty string")
        (is (= :success (:status-filter initial-fields))
            "should initialize status-filter to :success by default")
        (is (= 1.0 (:sampling-rate initial-fields))
            "should initialize sampling-rate to 1.0 by default")
        (is (= {:type :and :filters []} (:filter initial-fields))
            "should initialize filter to empty AND filter")))

    (testing "status-filter accepts valid keyword values"
      (let [form-spec (get @forms/form-specs :add-rule)
            initial-fields-fn (get-in form-spec [:main :initial-fields])]
        (is (= :success (:status-filter (initial-fields-fn {})))
            "should default to :success")
        (is (= :all (:status-filter (initial-fields-fn {:status-filter :all})))
            "should accept :all")
        (is (= :failure (:status-filter (initial-fields-fn {:status-filter :failure})))
            "should accept :failure")))

    (testing "has validators for required fields"
      (let [form-spec (get @forms/form-specs :add-rule)
            validators (get-in form-spec [:main :validators])]
        (is (contains? validators :rule-name)
            "should have validator for rule-name")
        (is (contains? validators :status-filter)
            "should have validator for status-filter")
        (is (contains? validators :sampling-rate)
            "should have validator for sampling-rate")))

    (testing "sampling-rate validation"
      (let [form-spec (get @forms/form-specs :add-rule)
            validators (get-in form-spec [:main :validators :sampling-rate])
            [required-validator number-validator range-validator] validators]
        (is (nil? (range-validator 0.0))
            "should accept 0.0 as valid")
        (is (nil? (range-validator 0.5))
            "should accept 0.5 as valid")
        (is (nil? (range-validator 1.0))
            "should accept 1.0 as valid")
        (is (some? (range-validator -0.1))
            "should reject negative values")
        (is (some? (range-validator 1.1))
            "should reject values greater than 1.0")))))

(deftest format-filter-compact-test
  ;; Tests the filter formatting function
  (testing "format-filter-compact"
    (testing "returns nil for empty and() filter"
      (is (nil? (rules/format-filter-compact {:type :and :filters []}))
          "should return nil for empty and() filter"))

    (testing "returns nil for empty and() filter with string keys"
      (is (nil? (rules/format-filter-compact {"type" "and" "filters" []}))
          "should return nil for empty and() filter with string keys"))

    (testing "formats non-empty and() filter"
      (is (= "and(error())"
             (rules/format-filter-compact {:type :and
                                           :filters [{:type :error}]}))
          "should format non-empty and() filter"))

    (testing "formats nested filters correctly"
      (is (= "and(error(), latency(>, 1000))"
             (rules/format-filter-compact
              {:type :and
               :filters [{:type :error}
                         {:type :latency
                          :comparator-spec {:comparator :> :value 1000}}]}))
          "should format nested filters within and()"))

    (testing "formats error filter"
      (is (= "error()" (rules/format-filter-compact {:type :error}))
          "should format error filter"))

    (testing "returns nil for nil input"
      (is (nil? (rules/format-filter-compact nil))
          "should return nil for nil input"))))
