(ns com.rpl.cluster-retriever-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [meander.epsilon :as m])
  (:import
   [com.rpl.agentorama
    AgentNode]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal]
   [com.rpl.rama.cluster
    ClusterManagerBase]
   [com.rpl.rama
    Depot
    Path
    PState
    QueryTopologyClient]))

(deftest cluster-retriever-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     ;; Plain Rama module exposing a depot, a pstate, and a query topology that
     ;; the agent will reach via the cluster retriever obtained from AgentNode.
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
                  ;; exercises every traced PState arity
                  (vswap! res conj (foreign-select-one (keypath k) p))
                  (vswap! res conj (foreign-select-one (keypath k) p {:pkey k}))
                  (vswap! res conj (foreign-select (keypath k) p))
                  (vswap! res conj (foreign-select (keypath k) p {:pkey k}))
                  (vswap! res conj (foreign-invoke-query q "."))
                  (aor/result! agent-node @res)
                )))
           )
           (aor/define-agents! topology)
         )))
     (bind agent-module-name (get-module-name agent-module))

     (rtest/launch-module! ipc other-module {:tasks 1 :threads 1})
     (launch-module-without-eval-agent! ipc agent-module {:tasks 2 :threads 2})

     (bind agent-manager (aor/agent-manager ipc agent-module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       agent-module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))

     (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo :a))
     (bind res (aor/agent-result foo inv))

     ;; cluster-retriever clients return correct results
     (is (= [1 1 [1] [1] ".!"] res))

     (bind root-invoke-id
       (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                           root-pstate
                           {:pkey task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             task-id
                             [[task-id root-invoke-id]]
                             10000))

     ;; depot append + query are traced as :other; pstate reads as :store-read,
     ;; matching how the same objects are traced via the AgentNode fetch methods
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val [1 1 [1] [1] ".!"] :failure? false}
         :nested-ops
         [{:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?other-module-name
            "name"       "*depot"
            "data"       :a
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "path"       _
            "result"     [1]}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "pkey"       :a
            "path"       _
            "result"     [1]}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "path"       _
            "result"     [1]}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "pkey"       :a
            "path"       _
            "result"     [1]}}
          {:type :other
           :info
           {"op"         "queryTopology"
            "moduleName" ?other-module-name
            "name"       "q"
            "args"       ["."]
            "response"   ".!"}}]
         :input         [:a]}}
       (m/guard
        (and (= ?agent-id agent-invoke-id)
             (= ?agent-task-id task-id)
             (= ?other-module-name other-module-name)))
      ))
    )))

(deftest cluster-retriever-java-pstate-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind other-module
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Long})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+count)})))))
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
                (let [^AgentDeclaredObjectsTaskGlobal dtg
                      (anode/get-declared-objects agent-node)
                      ^ClusterManagerBase retriever (.getClusterRetriever
                                                     ^AgentNode agent-node)
                      ^Depot depot (.clusterDepot retriever
                                                  other-module-name
                                                  "*depot")
                      ^PState p    (.clusterPState retriever
                                                   other-module-name
                                                   "$$p")
                      kpath     (Path/key (into-array Object [k]))
                      res       (volatile! [])]
                  (foreign-append! depot k)
                  ;; direct Java PState methods (every arity)
                  (vswap! res conj (.selectOne p kpath))
                  (vswap! res conj (.selectOne p k kpath))
                  (vswap! res conj (vec (.select p kpath)))
                  (vswap! res conj (vec (.select p k kpath)))
                  ;; the underlying client the retriever wraps comes from the
                  ;; per-task cache, so the same (module, name) is identical
                  (vswap! res conj
                          (identical?
                           (.getForeignPState dtg other-module-name "$$p")
                           (.getForeignPState dtg other-module-name "$$p")))
                  (aor/result! agent-node @res)
                )))
           )
           (aor/define-agents! topology)
         )))
     (bind agent-module-name (get-module-name agent-module))

     (rtest/launch-module! ipc other-module {:tasks 1 :threads 1})
     (launch-module-without-eval-agent! ipc agent-module {:tasks 2 :threads 2})

     (bind agent-manager (aor/agent-manager ipc agent-module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       agent-module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))

     (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo :a))
     (bind res (aor/agent-result foo inv))

     ;; results from every PState arity, plus cached-underlying identity check
     (is (= [1 1 [1] [1] true] res))

     (bind root-invoke-id
       (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                           root-pstate
                           {:pkey task-id}))
     (bind trace
       (foreign-invoke-query traces-query
                             task-id
                             [[task-id root-invoke-id]]
                             10000))

     ;; Java selectOne records "selectOne" with scalar result; select records
     ;; "select" with a list result; both as :store-read
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :agent-task-id ?agent-task-id
         :node          "start"
         :result        {:val [1 1 [1] [1] true] :failure? false}
         :nested-ops
         [{:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?other-module-name
            "name"       "*depot"
            "data"       :a
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :store-read
           :info
           {"op"         "selectOne"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "path"       _
            "result"     1}}
          {:type :store-read
           :info
           {"op"         "selectOne"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "pkey"       :a
            "path"       _
            "result"     1}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "path"       _
            "result"     [1]}}
          {:type :store-read
           :info
           {"op"         "select"
            "moduleName" ?other-module-name
            "name"       "$$p"
            "pkey"       :a
            "path"       _
            "result"     [1]}}]
         :input         [:a]}}
       (m/guard
        (and (= ?agent-id agent-invoke-id)
             (= ?agent-task-id task-id)
             (= ?other-module-name other-module-name)))
      ))
    )))
