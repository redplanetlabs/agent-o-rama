(ns com.rpl.cluster-retriever-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama
    AgentNode]
   [com.rpl.rama.cluster
    ClusterManagerBase]
   [com.rpl.rama
    Depot
    PState
    QueryTopologyClient]))

(deftest cluster-retriever-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind other-module
           (module [setup topologies]
                   (declare-depot setup *depot (hash-by identity))
                   (let [s (stream-topology topologies "s")]
                     (declare-pstate s $$p {clojure.lang.Keyword Long})
                     (<<sources s
                                (source> *depot :> *k)
                                (+compound $$p {*k (aggs/+count)})))
                   (<<query-topology topologies
                                     "q"
                                     [*v :> *res]
                                     (|origin)
                                     (str *v "!" :> *res))))
     (bind other-module-name (get-module-name other-module))

     (bind agent-module
           (module [setup topologies]
                   (let [topology (aor/agent-topology setup topologies)]
                     (->
                      topology
                      (aor/new-agent "foo")
                      (aor/node
                       "start"
                       nil
                       (fn [agent-node k]
                         (let [^ClusterManagerBase retriever (.getClusterRetriever
                                                              ^AgentNode agent-node)
                               ^Depot depot (.clusterDepot retriever
                                                           other-module-name
                                                           "*depot")
                               ^PState p    (.clusterPState retriever
                                                            other-module-name
                                                            "$$p")
                               ^QueryTopologyClient q (.clusterQuery retriever
                                                                     other-module-name
                                                                     "q")
                               res (volatile! [])]
                           (foreign-append! depot k)
                           (vswap! res conj (foreign-select-one (keypath k) p))
                           (vswap! res conj (foreign-select-one (keypath k) p {:pkey k}))
                           (vswap! res conj (foreign-select (keypath k) p))
                           (vswap! res conj (foreign-select (keypath k) p {:pkey k}))
                           (vswap! res conj (foreign-invoke-query q "."))
                           (aor/result! agent-node @res)))))
                     (aor/define-agents! topology))))
     (bind agent-module-name (get-module-name agent-module))

     (rtest/launch-module! ipc other-module {:tasks 1 :threads 1})
     (launch-module-without-eval-agent! ipc agent-module {:tasks 2 :threads 2})

     (bind agent-manager (aor/agent-manager ipc agent-module-name))
     (bind foo (aor/agent-client agent-manager "foo"))

     (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo :a))
     (bind res (aor/agent-result foo inv))

     (is (= [1 1 [1] [1] ".!"] res)))))
