(ns com.rpl.agent.basic.document-store-agent
  "Demonstrates document store operations for structured multi-field data.

  Features demonstrated:
  - declare-document-store: Create a document store with multiple fields
  - get-store: Access document stores from agent nodes
  - store/get-document-field: Retrieve specific field values
  - store/put-document-field!: Store values in specific fields
  - store/update-document-field!: Update specific field values
  - store/contains-document-field?: Check if fields exist
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
  ;; Key: String (user-id), Fields: name (String), age (Long), preferences (Object)
  (aor/declare-document-store
   topology
   "$$user-profiles" String
   "name" String
   "age" Long
   "preferences" Object)

  (-> (aor/new-agent topology "DocumentStoreAgent")

      ;; Node to create or update user profile
      (aor/node
       "update-profile"
       "read-profile"
       (fn [agent-node {:keys [user-id profile-updates]}]
         (let [profiles-store (aor/get-store agent-node "$$user-profiles")]
           ;; Update individual profile fields
           (when (:name profile-updates)
             (store/put-document-field! profiles-store
                                        user-id
                                        "name"
                                        (:name profile-updates)))

           (when (:age profile-updates)
             (store/put-document-field! profiles-store
                                        user-id
                                        "age"
                                        (:age profile-updates)))
           (when (:preferences profile-updates)
             ;; Demonstrate field update with function
             (store/update-document-field!
              profiles-store
              user-id
              "preferences"
              (fn [existing]
                (merge (or existing {}) (:preferences profile-updates)))))

           (aor/emit! agent-node "read-profile" user-id))))

      ;; Node to read profile and return result
      (aor/node
       "read-profile"
       nil
       (fn [agent-node user-id]
         (let [profiles-store (aor/get-store agent-node "$$user-profiles")
;; Check which fields exist
               has-name? (store/contains-document-field? profiles-store user-id "name")
               has-age? (store/contains-document-field? profiles-store user-id "age")
               has-prefs? (store/contains-document-field? profiles-store user-id "preferences")
               ;; Retrieve all profile fields
               name (when has-name?
                      (store/get-document-field profiles-store user-id "name"))
               age (when has-age?
                     (store/get-document-field profiles-store user-id "age"))
               preferences (when has-prefs?
                             (store/get-document-field profiles-store
                                                       user-id
                                                       "preferences"))]

           (aor/result! agent-node
                        {:user-id user-id
                         :name name
                         :age age
                         :preferences preferences}))))))

(defn -main
  "Run the document store agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DocumentStoreModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name DocumentStoreModule))
          agent (aor/agent-client manager "DocumentStoreAgent")]

      (println "Document Store Agent Example:")

      ;; First invocation: Create user profile
      (println "\n--- Creating user profile ---")
      (let [result1 (aor/agent-invoke
                     agent
                     {:user-id "user123"
                      :profile-updates {:name "Alice Smith"
                                        :age 28
                                        :preferences {:theme "dark"
                                                      :newsletter true}}})]
        (println "Profile created:")
        (println "  Name:" (:name result1))
        (println "  Age:" (:age result1))
        (println "  Preferences:" (:preferences result1)))

      ;; Second invocation: Update specific fields
      (println "\n--- Updating age and preferences ---")
      (let [result2 (aor/agent-invoke
                     agent
                     {:user-id "user123"
                      :profile-updates {:age 29
                                        :preferences {:notifications true}}})]
        (println "Profile updated:")
        (println "  Name:" (:name result2))
        (println "  Age:" (:age result2))
        (println "  Preferences:" (:preferences result2)))

      ;; Third invocation: Different user
      (println "\n--- Creating second user ---")
      (let [result3 (aor/agent-invoke
                     agent
                     {:user-id "user456"
                      :profile-updates {:name "Bob Jones"
                                        :age 35
                                        :preferences {:theme "light"}}})]
        (println "Second profile created:")
        (println "  Name:" (:name result3))
        (println "  Age:" (:age result3))
        (println "  Preferences:" (:preferences result3)))

      (println "\nNotice how:")
      (println "- Document fields can be updated independently")
      (println "- Field updates persist across invocations")
      (println "- Complex field merging is supported with update-document-field!")
      (println "- contains-document-field? checks field existence before retrieval")
      (println "- Multiple users are stored in the same document store"))))
