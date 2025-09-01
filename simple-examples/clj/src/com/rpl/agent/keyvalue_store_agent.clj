(ns com.rpl.agent.keyvalue-store-agent
  "Demonstrates key-value store operations for persistent agent state.
  
  Features demonstrated:
  - declare-key-value-store: Create a key-value store  
  - get-store: Access stores from agent nodes
  - store/get: Retrieve values from store
  - store/put!: Store values in store
  - store/update!: Update existing values in store
  - Persistent state across agent invocations"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating key-value store usage
(aor/defagentmodule KeyValueStoreModule
  [topology]
  
  ;; Declare a key-value store for counters (String -> Long)
  (aor/declare-key-value-store topology "counters" String Long)
  
  ;; Declare a key-value store for user data (String -> Object)
  (aor/declare-key-value-store topology "user-data" String Object)
  
  (-> topology
      (aor/new-agent "KeyValueStoreAgent")
      
      ;; Node to increment a counter
      (aor/node "increment-counter" "get-user-data"
                (fn [agent-node {:keys [counter-name user-id]}]
                  (let [counters-store (aor/get-store agent-node "counters")]
                    ;; Get current value or default to 0
                    (let [current-count (or (store/get counters-store counter-name) 0)
                          new-count (inc current-count)]
                      ;; Update the counter
                      (store/put! counters-store counter-name new-count)
                      (println (format "Counter '%s' incremented to %d" counter-name new-count))
                      (aor/emit! agent-node "get-user-data" {:counter-name counter-name
                                                             :new-count new-count
                                                             :user-id user-id})))))
      
      ;; Node to manage user data
      (aor/node "get-user-data" "finalize"
                (fn [agent-node {:keys [counter-name new-count user-id]}]
                  (let [user-store (aor/get-store agent-node "user-data")]
                    ;; Get existing user data or create new
                    (let [user-data (or (store/get user-store user-id) 
                                        {:name user-id :interactions []})
                          ;; Update user interaction history
                          updated-data (update user-data :interactions 
                                               conj {:counter counter-name 
                                                     :count new-count 
                                                     :timestamp (System/currentTimeMillis)})]
                      ;; Store updated user data
                      (store/put! user-store user-id updated-data)
                      (println (format "User '%s' data updated" user-id))
                      (aor/emit! agent-node "finalize" {:counter-name counter-name
                                                        :new-count new-count
                                                        :user-data updated-data})))))
      
      ;; Final node to return comprehensive result
      (aor/node "finalize" nil
                (fn [agent-node {:keys [counter-name new-count user-data]}]
                  (let [counters-store (aor/get-store agent-node "counters")
                        user-store (aor/get-store agent-node "user-data")]
                    ;; Demonstrate store querying
                    (let [all-counters (into {} (for [key ["page-views" "api-calls" "errors"]]
                                                  [key (store/get counters-store key)]))
                          total-interactions (count (:interactions user-data))]
                      (let [result {:action "counter-increment"
                                    :counter counter-name
                                    :new-count new-count
                                    :user-data user-data
                                    :total-user-interactions total-interactions
                                    :all-counters all-counters
                                    :processed-at (System/currentTimeMillis)}]
                        (aor/result! agent-node result))))))))

(defn -main
  "Run the key-value store agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc KeyValueStoreModule {:tasks 1 :threads 1})
    
    (let [manager (aor/agent-manager ipc (rama/get-module-name KeyValueStoreModule))
          agent (aor/agent-client manager "KeyValueStoreAgent")]
      
      (println "Key-Value Store Agent Example:")
      
      ;; Multiple invocations to show persistent state
      (println "\n--- First invocation: page-views for alice ---")
      (let [result1 (aor/agent-invoke agent {:counter-name "page-views" :user-id "alice"})]
        (println "Result 1:" (select-keys result1 [:counter :new-count :total-user-interactions])))
      
      (println "\n--- Second invocation: api-calls for alice ---")
      (let [result2 (aor/agent-invoke agent {:counter-name "api-calls" :user-id "alice"})]
        (println "Result 2:" (select-keys result2 [:counter :new-count :total-user-interactions])))
      
      (println "\n--- Third invocation: page-views for bob ---")
      (let [result3 (aor/agent-invoke agent {:counter-name "page-views" :user-id "bob"})]
        (println "Result 3:" (select-keys result3 [:counter :new-count :total-user-interactions])))
      
      (println "\n--- Fourth invocation: page-views for alice again ---")
      (let [result4 (aor/agent-invoke agent {:counter-name "page-views" :user-id "alice"})]
        (println "Result 4:" (select-keys result4 [:counter :new-count :total-user-interactions]))
        (println "All counters:" (:all-counters result4)))
      
      (println "\nNotice how:")
      (println "- Counters persist and increment across invocations")
      (println "- User data accumulates interaction history")
      (println "- Different users maintain separate state"))))