(ns com.rpl.agent.basic.keyvalue-store-agent
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
  (aor/declare-key-value-store topology "$$counters" String Long)

  (->
    (aor/new-agent topology "KeyValueStoreAgent")

    ;; Single node to demonstrate store operations
    (aor/node
     "manage-counter"
     nil
     (fn [agent-node {:keys [counter-name operation value]}]
       (let [counters-store (aor/get-store agent-node "$$counters")]

         (let [result
               (case operation
                 :get
                 (let [current-value (store/get counters-store counter-name)]
                   {:action  :get
                    :counter counter-name
                    :value   current-value})

                 :increment
                 (let [current-value (or (store/get counters-store counter-name)
                                         0)
                       new-value     (inc current-value)]
                   (store/put! counters-store counter-name new-value)
                   {:action         :increment
                    :counter        counter-name
                    :previous-value current-value
                    :new-value      new-value})

                 :set
                 (do
                   (store/put! counters-store counter-name value)
                   {:action  :set
                    :counter counter-name
                    :value   value})

                 :update
                 (let [current-value (or (store/get counters-store counter-name)
                                         0)
                       new-value     (+ current-value value)]
                   (store/update! counters-store
                                  counter-name
                                  (fn [v] (+ (or v 0) value)))
                   {:action         :update
                    :counter        counter-name
                    :previous-value current-value
                    :added-value    value
                    :new-value      new-value}))]

           (println
            (format "Counter '%s' %s: %s"
                    counter-name
                    operation
                    (or (:value result) (:new-value result) "completed")))

           (aor/result! agent-node
                        (assoc result
                         :processed-at (System/currentTimeMillis)))))))))

(defn -main
  "Run the key-value store agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc KeyValueStoreModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name KeyValueStoreModule))
          agent   (aor/agent-client manager "KeyValueStoreAgent")]

      (println "Key-Value Store Agent Example:")

      ;; Demonstrate different counter operations
      (println "\n--- Setting initial counter value ---")
      (let [result1 (aor/agent-invoke agent
                                      {:counter-name "page-views"
                                       :operation    :set
                                       :value        10})]
        (println "Result:" (select-keys result1 [:action :counter :value])))

      (println "\n--- Getting current counter value ---")
      (let [result2 (aor/agent-invoke agent
                                      {:counter-name "page-views"
                                       :operation    :get})]
        (println "Result:" (select-keys result2 [:action :counter :value])))

      (println "\n--- Incrementing counter ---")
      (let [result3 (aor/agent-invoke agent
                                      {:counter-name "page-views"
                                       :operation    :increment})]
        (println "Result:"
                 (select-keys result3
                              [:action :counter :previous-value :new-value])))

      (println "\n--- Updating counter by adding value ---")
      (let [result4 (aor/agent-invoke agent
                                      {:counter-name "page-views"
                                       :operation    :update
                                       :value        5})]
        (println "Result:"
                 (select-keys result4
                              [:action :counter :previous-value :added-value
                               :new-value])))

      (println "\n--- Working with different counter ---")
      (let [result5 (aor/agent-invoke agent
                                      {:counter-name "api-calls"
                                       :operation    :increment})]
        (println "Result:"
                 (select-keys result5
                              [:action :counter :previous-value :new-value])))

      (println "\n--- Final state check ---")
      (let [result6 (aor/agent-invoke agent
                                      {:counter-name "page-views"
                                       :operation    :get})
            result7 (aor/agent-invoke agent
                                      {:counter-name "api-calls"
                                       :operation    :get})]
        (println "page-views final value:" (:value result6))
        (println "api-calls final value:" (:value result7)))

      (println "\nNotice how:")
      (println "- Counter values persist across invocations")
      (println "- Different counters maintain separate state")
      (println
       "- Various store operations (get, put, update) work correctly"))))
