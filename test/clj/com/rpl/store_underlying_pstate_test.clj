(ns com.rpl.store-underlying-pstate-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama
    AgentNode]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]
   [com.rpl.rama.cluster
    ClusterManagerBase]
   [com.rpl.rama
    PState]))

(deftest store-underlying-pstate-and-cluster-retriever-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
           (aor/agentmodule
            [topology]
            (aor/declare-key-value-store
             topology
             "$$kv"
             clojure.lang.Keyword
             Long)
            (aor/declare-document-store
             topology
             "$$doc"
             clojure.lang.Keyword
             :a Long)
            (aor/declare-pstate-store
             topology
             "$$p"
             {clojure.lang.Keyword (map-schema Long Long {:subindex? true})})
            (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "start"
              nil
              (fn [agent-node _]
                (let [^AgentDeclaredObjectsTaskGlobal dtg (anode/get-declared-objects agent-node)
                      module-name (.getThisModuleName dtg)
                      ^ClusterManagerBase retriever (.getClusterRetriever
                                                     ^AgentNode agent-node)
                      kv  (aor/get-store agent-node "$$kv")
                      doc (aor/get-store agent-node "$$doc")
                      p   (aor/get-store agent-node "$$p")]
                  (store/put! kv :a 10)
                  (store/put-document-field! doc :m :a 20)
                  (store/pstate-transform! [:a (keypath 0) (termval 30)]
                                           p
                                           :a)

                  (let [^PState kv-p  (store/get-underlying-pstate kv)
                        ^PState doc-p (store/get-underlying-pstate doc)
                        ^PState p-p   (store/get-underlying-pstate p)
                        ^PState kv-via-retriever
                        (.clusterPState retriever module-name "$$kv")
                        ^PState doc-via-retriever
                        (.clusterPState retriever module-name "$$doc")
                        ^PState p-via-retriever
                        (.clusterPState retriever module-name "$$p")]
                    (aor/result!
                     agent-node
                     {:kv
                      {:traced (store/get kv :a)
                       :underlying (foreign-select-one :a kv-p)
                       :via-retriever (foreign-select-one :a kv-via-retriever)}
                      :doc
                      {:traced (store/get-document-field doc :m :a)
                       :underlying (foreign-select-one [:m :a] doc-p {:pkey :m})
                       :via-retriever (foreign-select-one [:m :a] doc-via-retriever {:pkey :m})}
                      :pstate
                      {:traced (store/pstate-select-one [:a 0] p :a)
                       :underlying (foreign-select-one [:a 0] p-p {:pkey :a})
                       :via-retriever (foreign-select-one [:a 0] p-via-retriever {:pkey :a})}}))))))))
     (bind module-name (get-module-name module))

     (launch-module-without-eval-agent! ipc module {:tasks 2 :threads 2})

     (bind manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client manager "foo"))

     (bind res (aor/agent-invoke foo nil))

     (is (= 10 (get-in res [:kv :traced])))
     (is (= 10 (get-in res [:kv :underlying])))
     (is (= 10 (get-in res [:kv :via-retriever])))

     (is (= 20 (get-in res [:doc :traced])))
     (is (= 20 (get-in res [:doc :underlying])))
     (is (= 20 (get-in res [:doc :via-retriever])))

     (is (= 30 (get-in res [:pstate :traced])))
     (is (= 30 (get-in res [:pstate :underlying])))
     (is (= 30 (get-in res [:pstate :via-retriever]))))))
