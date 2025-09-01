(ns com.rpl.agent.document-store-agent
  "Demonstrates document store operations for structured multi-field data.

  Features demonstrated:
  - declare-document-store: Create a document store with multiple fields
  - get-store: Access document stores from agent nodes
  - store/get-document-field: Retrieve specific field values
  - store/put-document-field!: Store values in specific fields
  - store/update-document-field!: Update specific field values
  - Structured document storage with multiple typed fields"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating document store usage
(aor/defagentmodule DocumentStoreModule
  [topology]

  ;; Declare document store for user profiles
  ;; Key: String (user-id), Fields: name (String), email (String), age (Long), preferences (Object)
  (aor/declare-document-store topology "user-profiles" String
                              "name" String
                              "email" String
                              "age" Long
                              "preferences" Object)

  ;; Declare document store for product catalog
  ;; Key: String (product-id), Fields: title (String), price (Double), category (String), metadata (Object)
  (aor/declare-document-store topology "products" String
                              "title" String
                              "price" Double
                              "category" String
                              "metadata" Object)

  (-> topology
      (aor/new-agent "DocumentStoreAgent")

      ;; Node to create or update user profile
      (aor/node "update-profile" "update-product"
                (fn [agent-node {:keys [user-id profile-updates product-id product-updates]}]
                  (let [profiles-store (aor/get-store agent-node "user-profiles")]
                    ;; Update individual profile fields
                    (when (:name profile-updates)
                      (store/put-document-field! profiles-store user-id "name" (:name profile-updates)))
                    (when (:email profile-updates)
                      (store/put-document-field! profiles-store user-id "email" (:email profile-updates)))
                    (when (:age profile-updates)
                      (store/put-document-field! profiles-store user-id "age" (:age profile-updates)))
                    (when (:preferences profile-updates)
                      (store/put-document-field! profiles-store user-id "preferences" (:preferences profile-updates)))

                    ;; Retrieve updated profile data
                    (let [name (store/get-document-field profiles-store user-id "name")
                          email (store/get-document-field profiles-store user-id "email")
                          age (store/get-document-field profiles-store user-id "age")
                          preferences (store/get-document-field profiles-store user-id "preferences")]

                      (println (format "Updated profile for %s: name=%s, email=%s, age=%s"
                                       user-id name email age))

                      (aor/emit! agent-node "update-product"
                                 {:user-id user-id
                                  :user-profile {:name name :email email :age age :preferences preferences}
                                  :product-id product-id
                                  :product-updates product-updates})))))

      ;; Node to update product information
      (aor/node "update-product" "finalize"
                (fn [agent-node {:keys [user-id user-profile product-id product-updates]}]
                  (let [products-store (aor/get-store agent-node "products")]
                    ;; Update product fields
                    (when (:title product-updates)
                      (store/put-document-field! products-store product-id "title" (:title product-updates)))
                    (when (:price product-updates)
                      (store/put-document-field! products-store product-id "price" (:price product-updates)))
                    (when (:category product-updates)
                      (store/put-document-field! products-store product-id "category" (:category product-updates)))
                    (when (:metadata product-updates)
                      ;; Demonstrate field update with function
                      (store/update-document-field! products-store product-id "metadata"
                                                    (fn [existing]
                                                      (merge (or existing {}) (:metadata product-updates)))))

                    ;; Retrieve product data
                    (let [title (store/get-document-field products-store product-id "title")
                          price (store/get-document-field products-store product-id "price")
                          category (store/get-document-field products-store product-id "category")
                          metadata (store/get-document-field products-store product-id "metadata")]

                      (println (format "Updated product %s: title=%s, price=%.2f, category=%s"
                                       product-id title (or price 0.0) category))

                      (aor/emit! agent-node "finalize"
                                 {:user-id user-id
                                  :user-profile user-profile
                                  :product-id product-id
                                  :product-data {:title title :price price :category category :metadata metadata}})))))

      ;; Final node to return comprehensive result
      (aor/node "finalize" nil
                (fn [agent-node {:keys [user-id user-profile product-id product-data]}]
                  (let [profiles-store (aor/get-store agent-node "user-profiles")
                        products-store (aor/get-store agent-node "products")]

                    ;; Demonstrate querying multiple fields
                    (let [user-age (store/get-document-field profiles-store user-id "age")
                          product-price (store/get-document-field products-store product-id "price")
                          result {:action "document-update"
                                  :user-id user-id
                                  :user-profile user-profile
                                  :user-age user-age
                                  :product-id product-id
                                  :product-data product-data
                                  :product-price product-price
                                  :recommendation (when (and user-age product-price)
                                                   (if (and (>= user-age 25) (>= product-price 100.0))
                                                     "premium"
                                                     "standard"))
                                  :processed-at (System/currentTimeMillis)}]

                      (aor/result! agent-node result))))))))

(defn -main
  "Run the document store agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DocumentStoreModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc (rama/get-module-name DocumentStoreModule))
          agent (aor/agent-client manager "DocumentStoreAgent")]

      (println "Document Store Agent Example:")

      ;; First invocation: Create user and product
      (println "\n--- Creating user profile and product ---")
      (let [result1 (aor/agent-invoke agent
                                      {:user-id "user123"
                                       :profile-updates {:name "Alice Smith"
                                                         :email "alice@example.com"
                                                         :age 28
                                                         :preferences {:theme "dark" :newsletter true}}
                                       :product-id "prod456"
                                       :product-updates {:title "Premium Laptop"
                                                         :price 1299.99
                                                         :category "electronics"
                                                         :metadata {:brand "TechCorp" :warranty "2-year"}}})]
        (println "Result 1:")
        (println "  User:" (select-keys (:user-profile result1) [:name :email :age]))
        (println "  Product:" (select-keys (:product-data result1) [:title :price :category]))
        (println "  Recommendation:" (:recommendation result1)))

      ;; Second invocation: Update specific fields
      (println "\n--- Updating user age and product metadata ---")
      (let [result2 (aor/agent-invoke agent
                                      {:user-id "user123"
                                       :profile-updates {:age 32}  ; Only update age
                                       :product-id "prod456"
                                       :product-updates {:metadata {:specs "16GB RAM, 512GB SSD"}}})] ; Merge metadata
        (println "Result 2:")
        (println "  User age updated to:" (:user-age result2))
        (println "  Product metadata:" (get-in result2 [:product-data :metadata]))
        (println "  Recommendation:" (:recommendation result2)))

      ;; Third invocation: Different user, same product
      (println "\n--- Creating second user for same product ---")
      (let [result3 (aor/agent-invoke agent
                                      {:user-id "user789"
                                       :profile-updates {:name "Bob Jones"
                                                         :email "bob@example.com"
                                                         :age 22
                                                         :preferences {:theme "light"}}
                                       :product-id "prod456"  ; Same product
                                       :product-updates {}})] ; No product updates
        (println "Result 3:")
        (println "  User:" (select-keys (:user-profile result3) [:name :email :age]))
        (println "  Product price:" (:product-price result3))
        (println "  Recommendation:" (:recommendation result3)))

      (println "\nNotice how:")
      (println "- Document fields can be updated independently")
      (println "- Different users can reference the same products")
      (println "- Field updates persist across invocations")
      (println "- Complex field merging is supported"))))
