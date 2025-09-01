(ns com.rpl.agent.document-store-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.document-store-agent :refer [DocumentStoreModule]]))

(deftest document-store-agent-test
  (testing "DocumentStoreAgent example produces expected results"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc DocumentStoreModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        DocumentStoreModule))
            agent   (aor/agent-client manager "DocumentStoreAgent")]

        (testing "creates and stores user profile and product data"
          (let [result (aor/agent-invoke
                        agent
                        {:user-id         "test-user"
                         :profile-updates {:name  "Test User"
                                           :email "test@example.com"
                                           :age   25}
                         :product-id      "test-product"
                         :product-updates {:title    "Test Product"
                                           :price    99.99
                                           :category "test"}})]
            (is (= "document-update" (:action result)))
            (is (= "test-user" (:user-id result)))
            (is (= "Test User" (get-in result [:user-profile :name])))
            (is (= "test@example.com" (get-in result [:user-profile :email])))
            (is (= 25 (:user-age result)))
            (is (= "test-product" (:product-id result)))
            (is (= "Test Product" (get-in result [:product-data :title])))
            (is (= 99.99 (:product-price result)))
            (is (= "standard" (:recommendation result)))))

        (testing "updates individual fields independently"
          (let [result (aor/agent-invoke agent
                                         {:user-id         "test-user"
                                          :profile-updates {:age 30}  ; Only
                                                                      ; update
                                                                      ; age
                                          :product-id      "test-product"
                                          :product-updates {:price 150.0}})] ; Only
                                                                             ; update
                                                                             ; price
            (is (= 30 (:user-age result)))
            (is (= "Test User" (get-in result [:user-profile :name]))) ; Name
                                                                       ; unchanged
            (is (= 150.0 (:product-price result)))
            (is (= "Test Product" (get-in result [:product-data :title]))) ; Title
                                                                           ; unchanged
            (is (= "premium" (:recommendation result))))) ; Should be premium
                                                          ; now

      ))))
