(ns com.rpl.rama-objects-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m]))


(deftest rama-objects-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module1
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
           (str *v "!" :> *res)
         )))
     (bind module1-name (get-module-name module1))
     (bind module2
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Object})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+last :abc)})))
         (<<query-topology topologies
           "q"
           [*v :> *res]
           (|origin)
           (str *v "!!" :> *res))
         (let [topology (aor/agent-topology setup topologies)]
           (aor/declare-key-value-store
            topology
            "$$kv"
            clojure.lang.Keyword
            Object)
           (aor/declare-document-store
            topology
            "$$doc"
            clojure.lang.Keyword
            :a Long)
           (aor/declare-pstate-store
            topology
            "$$pstore"
            {clojure.lang.Keyword (map-schema Long Long {:subindex? true})})
           (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "kv"
              "doc"
              (fn [agent-node k]
                (let [kv (aor/get-store agent-node "$$kv")]
                  (store/update! kv k #(inc (or % 0)))
                  (aor/emit! agent-node "doc" k)
                )))
             (aor/node
              "doc"
              "pstate"
              (fn [agent-node k]
                (let [doc (aor/get-store agent-node "$$doc")]
                  (store/update-document-field! doc k :a #(inc (or % 0)))
                  (aor/emit! agent-node "pstate" k)
                )))
             (aor/node
              "pstate"
              nil
              (fn [agent-node k]
                (let [p (aor/get-store agent-node "$$pstore")]
                  (store/pstate-transform! [(keypath k 0) (nil->val 0) (term inc)]
                                           p
                                           :a)
                  (aor/result! agent-node :done)
                )))
           )
           (aor/define-agents! topology)
         )))
     (bind module2-name (get-module-name module2))

     (bind module3
       (module [setup topologies]
         (declare-depot setup *depot (hash-by identity))
         (let [s (stream-topology topologies "s")]
           (declare-pstate s $$p {clojure.lang.Keyword Object})
           (<<sources s
            (source> *depot :> *k)
             (+compound $$p {*k (aggs/+last :def)})))
         (<<query-topology topologies
           "q"
           [*v :> *res]
           (|origin)
           (str *v "!!!" :> *res))
         (let [topology (aor/agent-topology setup topologies)]
           (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "start"
              nil
              (fn [agent-node k]
                (let [p3        (aor/get-store agent-node "$$p")
                      p1        (aor/get-mirror-store agent-node module1-name "$$p")
                      p2        (aor/get-mirror-store agent-node module2-name "$$p")
                      foo-m2    (aor/mirror-agent-client agent-node module2-name "foo")
                      m2-kv     (aor/get-mirror-store agent-node module2-name "$$kv")
                      m2-doc    (aor/get-mirror-store agent-node module2-name "$$doc")
                      m2-pstore (aor/get-mirror-store agent-node module2-name "$$pstore")
                      depot3    (aor/get-depot agent-node "*depot")
                      depot1    (aor/get-mirror-depot agent-node module1-name "*depot")
                      depot2    (aor/get-mirror-depot agent-node module2-name "*depot")
                      q3        (aor/get-query-topology-client agent-node "q")
                      q1        (aor/get-mirror-query-topology-client agent-node module1-name "q")
                      q2        (aor/get-mirror-query-topology-client agent-node module2-name "q")
                      res       (volatile! [])]
                  (foreign-append! depot1 k)
                  (vswap! res conj (store/pstate-select-one (keypath k) p1))
                  (foreign-append! depot2 k)
                  (vswap! res conj (store/pstate-select-one (keypath k) p2))
                  (foreign-append! depot3 k)
                  (vswap! res conj (store/pstate-select-one (keypath k) p3))
                  (vswap! res conj (foreign-invoke-query q1 "."))
                  (vswap! res conj (foreign-invoke-query q2 "."))
                  (vswap! res conj (foreign-invoke-query q3 "."))
                  (aor/agent-invoke foo-m2 k)
                  (vswap! res conj (h/contains-string? (str (class m2-kv)) "mk_kv_store"))
                  (vswap! res conj (h/contains-string? (str (class m2-doc)) "mk_doc_store"))
                  (vswap! res conj (h/contains-string? (str (class m2-pstore)) "mk_pstate_store"))
                  (vswap! res conj (store/get m2-kv k))
                  (vswap! res conj (store/get m2-doc k))
                  (vswap! res conj (store/pstate-select-one (keypath k 0) m2-pstore))
                  (foreign-append! depot1 :x)
                  (foreign-append! depot1 :y)
                  (foreign-append! depot1 :z)
                  (vswap! res conj (foreign-object-info depot1))
                  (vswap! res conj (foreign-depot-partition-info depot1 0))
                  (vswap! res conj (foreign-depot-read depot1 0 0 3))
                  (foreign-append! depot1 :1 :append-ack)
                  (foreign-append! depot1 :2 nil)
                  (aor/result! agent-node @res)
                ))))
           (aor/define-agents! topology)
         )))
     (bind module3-name (get-module-name module3))

     (rtest/launch-module! ipc module1 {:tasks 1 :threads 1})
     (launch-module-without-eval-agent! ipc module2 {:tasks 2 :threads 2})
     (launch-module-without-eval-agent! ipc module3 {:tasks 2 :threads 2})

     (bind agent-manager (aor/agent-manager ipc module3-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       module3-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))


     (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo :a))
     (bind res (aor/agent-result foo inv))

     (is (= res
            [1 :abc :def ".!" ".!!" ".!!!" true true true 1 {:a 1} 1
             {:name "*depot" :module-name module1-name :num-partitions 1}
             {:start-offset 0 :end-offset 4} [:a :x :y]]
         ))



     (bind root-invoke-id
       (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                           root-pstate
                           {:pkey task-id}))
     (bind res
       (foreign-invoke-query traces-query
                             task-id
                             [[task-id root-invoke-id]]
                             10000))

     (is
      (trace-matches?
       (:invokes-map res)
       {!id1
        {:agent-id      ?agent-id
         :emits         []
         :agent-task-id ?agent-task-id
         :node          "start"
         :nested-ops
         [{:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :a
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :store-read
           :info
           {"moduleName" ?module1-name
            "name"       "$$p"
            "op"         "pstate-select-one"
            "params"     []
            "result"     1}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module2-name
            "name"       "*depot"
            "data"       :a
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :store-read
           :info
           {"moduleName" ?module2-name
            "name"       "$$p"
            "op"         "pstate-select-one"
            "params"     []
            "result"     :abc}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module3-name
            "name"       "*depot"
            "data"       :a
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :store-read
           :info
           {"moduleName" ?module3-name
            "name"       "$$p"
            "op"         "pstate-select-one"
            "params"     []
            "result"     :def}}
          {:type :other
           :info
           {"op"         "queryTopology"
            "moduleName" ?module1-name
            "name"       "q"
            "args"       ["."]
            "response"   ".!"}}
          {:type :other
           :info
           {"op"         "queryTopology"
            "moduleName" ?module2-name
            "name"       "q"
            "args"       ["."]
            "response"   ".!!"}}
          {:type :other
           :info
           {"op"         "queryTopology"
            "moduleName" ?module3-name
            "name"       "q"
            "args"       ["."]
            "response"   ".!!!"}}
          {:type :agent-call
           :info
           {"op"         "initiate"
            "args"       [:a]
            "agent-module-name" ?module2-name
            "agent-name" "foo"}}
          {:type :agent-call
           :info
           {"op"         "nextStep"
            "agent-module-name" ?module2-name
            "agent-name" "foo"
            "result"     {:result :done}}}
          {:type :store-read
           :info
           {"moduleName" ?module2-name
            "name"       "$$kv"
            "op"         "get"
            "params"     [:a]
            "result"     1}}
          {:type :store-read
           :info
           {"moduleName" ?module2-name
            "name"       "$$doc"
            "op"         "get"
            "params"     [:a]
            "result"     {:a 1}}}
          {:type :store-read
           :info
           {"moduleName" ?module2-name
            "name"       "$$pstore"
            "op"         "pstate-select-one"
            "params"     []
            "result"     1}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :x
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :y
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :z
            "ackLevel"   "ack"
            "response"   {}}}
          {:type :other
           :info
           {"op"         "getObjectInfo"
            "moduleName" ?module1-name
            "name"       "*depot"}}
          {:type :other
           :info
           {"op"         "getPartitionInfo"
            "moduleName" ?module1-name
            "name"       "*depot"
            "partitionIndex" 0}}
          {:type :other
           :info
           {"op"             "depotRead"
            "moduleName"     ?module1-name
            "name"           "*depot"
            "partitionIndex" 0
            "startOffset"    0
            "endOffset"      3}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :1
            "ackLevel"   "appendAck"
            "response"   {}}}
          {:type :other
           :info
           {"op"         "depotAppend"
            "moduleName" ?module1-name
            "name"       "*depot"
            "data"       :2
            "ackLevel"   "none"
            "response"   {}}}
         ]
         :input         [:a]
         :metadata      {}}}
       (m/guard
        (and (= ?agent-id agent-invoke-id)
             (= ?agent-task-id task-id)
             (= ?module1-name module1-name)
             (= ?module2-name module2-name)
             (= ?module3-name module3-name)))
      ))

     (bind store-info-query
       (foreign-query ipc module2-name (queries/module-get-store-info-name)))

     (is (= {"$$kv" :kv "$$doc" :doc}
            (foreign-invoke-query store-info-query)))
    )))
