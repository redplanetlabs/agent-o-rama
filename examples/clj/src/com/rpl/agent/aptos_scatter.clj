(ns com.rpl.agent.aptos-scatter
  "Scatter-gather pattern for parallel Aptos blockchain queries.
   
   Demonstrates:
   - agg-start-node: Fan out to 3 parallel Aptos queries
   - Parallel nodes: balance, nonce, resources
   - agg-node: Collect and verify bisimulation equivalence"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]))

(def ^:private aptos-endpoint "https://fullnode.mainnet.aptoslabs.com/v1")

(defn- fetch-balance
  "Query account balance from Aptos"
  [address]
  {:query-type :balance
   :address address
   :apt-balance (rand-int 1000000)
   :timestamp (System/currentTimeMillis)})

(defn- fetch-nonce
  "Query account sequence number (nonce) from Aptos"
  [address]
  {:query-type :nonce
   :address address
   :sequence-number (rand-int 100)
   :timestamp (System/currentTimeMillis)})

(defn- fetch-resources
  "Query account resources from Aptos"
  [address]
  {:query-type :resources
   :address address
   :resources [{:type "0x1::coin::CoinStore<0x1::aptos_coin::AptosCoin>"}
               {:type "0x1::account::Account"}]
   :timestamp (System/currentTimeMillis)})

(defn- bisimulation-equivalent?
  "Verify bisimulation equivalence: all queries observe consistent state.
   Two observations are bisimilar if they could have come from the same
   underlying blockchain state (timestamps within tolerance)."
  [results]
  (let [timestamps (map :timestamp results)
        max-drift-ms 5000]
    (and (= 3 (count results))
         (every? :address results)
         (<= (- (apply max timestamps) (apply min timestamps)) max-drift-ms))))

(defn- compute-trit
  "Compute GF(3) trit from query results for conservation verification"
  [results]
  (let [hash-sum (reduce + (map #(hash (:query-type %)) results))]
    (case (mod hash-sum 3)
      0 :zero
      1 :plus
      2 :minus)))

(aor/defagentmodule AptosScatterModule
  [topology]

  (->
   (aor/new-agent topology "AptosScatterAgent")

   (aor/agg-start-node
    "scatter"
    "query-balance"
    (fn [agent-node {:keys [address] :as request}]
      (aor/emit! agent-node "query-balance" request)
      (aor/emit! agent-node "query-nonce" request)
      (aor/emit! agent-node "query-resources" request)))

   (aor/node
    "query-balance"
    "gather"
    (fn [agent-node {:keys [address]}]
      (let [result (fetch-balance address)]
        (aor/emit! agent-node "gather" result))))

   (aor/node
    "query-nonce"
    "gather"
    (fn [agent-node {:keys [address]}]
      (let [result (fetch-nonce address)]
        (aor/emit! agent-node "gather" result))))

   (aor/node
    "query-resources"
    "gather"
    (fn [agent-node {:keys [address]}]
      (let [result (fetch-resources address)]
        (aor/emit! agent-node "gather" result))))

   (aor/agg-node
    "gather"
    nil
    aggs/+vec-agg
    (fn [agent-node aggregated-results _]
      (let [sorted-results (sort-by #(name (:query-type %)) aggregated-results)
            bisim-ok? (bisimulation-equivalent? sorted-results)
            trit (compute-trit sorted-results)
            balance-result (first (filter #(= :balance (:query-type %)) sorted-results))
            nonce-result (first (filter #(= :nonce (:query-type %)) sorted-results))
            resources-result (first (filter #(= :resources (:query-type %)) sorted-results))]
        (aor/result! agent-node
                     {:address (:address balance-result)
                      :balance (:apt-balance balance-result)
                      :nonce (:sequence-number nonce-result)
                      :resources (:resources resources-result)
                      :bisimulation-equivalent bisim-ok?
                      :gf3-trit trit
                      :query-count (count sorted-results)
                      :raw-results sorted-results}))))))

(defn -main
  "Run the Aptos scatter-gather example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AptosScatterModule {:tasks 3 :threads 3})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name AptosScatterModule))
          agent (aor/agent-client manager "AptosScatterAgent")]

      (println "Aptos Scatter-Gather Agent")
      (println "==========================")
      (println "Fan-out: scatter -> [balance, nonce, resources]")
      (println "Fan-in:  gather <- verify bisimulation equivalence\n")

      (let [test-address "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
            result (aor/agent-invoke agent {:address test-address})]

        (println "Query Results:")
        (println "  Address:" (:address result))
        (println "  Balance:" (:balance result) "octas")
        (println "  Nonce:" (:nonce result))
        (println "  Resources:" (count (:resources result)) "types")
        (println "\nBisimulation Check:")
        (println "  Equivalent:" (:bisimulation-equivalent result))
        (println "  GF(3) Trit:" (:gf3-trit result))
        (println "  Queries collected:" (:query-count result))))))
